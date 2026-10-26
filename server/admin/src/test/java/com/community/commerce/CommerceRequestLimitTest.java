package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.commerce.controller.CommerceSettingsController;
import com.community.commerce.dto.request.RequestLimitsSaveRequest;
import com.community.commerce.dto.response.RequestLimitsView;
import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.service.RequestRateLimits;
import com.community.commerce.service.CommerceRequestLimitService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.ValidationException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 커머스 설정 › 요청 제한(docs/domain/commerce.md 12-4, commerce-foundation-contract 「0-5」) — 실제 관리자 앱 문맥 + PostgreSQL.
 * 한도 표는 시험이 같이 쓰므로 끝에 기본값(저장 기록 없음)으로 되돌린다.
 */
class CommerceRequestLimitTest extends CommerceAdminTestBase {

    @Autowired
    private CommerceRequestLimitService service;
    @Autowired
    private RequestRateLimits copy;

    @AfterEach
    void restore() {
        for (RequestAction action : RequestAction.values()) {
            jdbc.update("UPDATE request_rate_limits SET limit_per_minute = ?, updated_at = NULL, updated_by = NULL WHERE action = ?", action.defaultLimit(), action.name());
        }
        copy.invalidate();
    }

    private static RequestLimitsSaveRequest body(Object groupbuy, Object charge, Object shop, Object refund) {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("GROUPBUY_JOIN", groupbuy);
        limits.put("POINT_CHARGE", charge);
        limits.put("SHOP_PURCHASE", shop);
        limits.put("REFUND_REQUEST", refund);
        return new RequestLimitsSaveRequest(limits);
    }

    @Test
    void 조회는_네_종류를_정해진_차례로_기본값과_함께_준다() {
        RequestLimitsView view = service.load();
        assertThat(view.items()).extracting(RequestLimitsView.Item::action).containsExactly("GROUPBUY_JOIN", "POINT_CHARGE", "SHOP_PURCHASE", "REFUND_REQUEST");
        assertThat(view.items()).extracting(RequestLimitsView.Item::limitPerMinute).containsExactly(5, 3, 10, 3);
        assertThat(view.items()).extracting(RequestLimitsView.Item::defaultLimit).containsExactly(5, 3, 10, 3);
        assertThat(view.updatedAt()).isNull();
        assertThat(view.updatedBy()).isNull();
    }

    @Test
    void 저장하면_바뀐_종류만_쓰고_감사에_전후를_남기고_이_서버의_사본은_바로_새_값() {
        long operator = newOperator();
        assertThat(copy.limitOf(RequestAction.SHOP_PURCHASE)).isEqualTo(10);

        RequestLimitsView saved = service.save(actor(operator), body(5, 0, 20, 3));

        assertThat(saved.items()).extracting(RequestLimitsView.Item::limitPerMinute).containsExactly(5, 0, 20, 3);
        assertThat(saved.updatedAt()).isNotNull();
        assertThat(saved.updatedBy()).isEqualTo(jdbc.queryForObject("SELECT operator_name FROM operators WHERE id = ?", String.class, operator));
        assertThat(jdbc.queryForObject("SELECT updated_by FROM request_rate_limits WHERE action = 'GROUPBUY_JOIN'", Long.class)).as("안 바뀐 줄은 그대로").isNull();

        Map<String, Object> audit = lastAudit(operator, AuditActions.COMMERCE_REQUEST_LIMIT_UPDATE);
        assertThat(audit.get("target_type")).isEqualTo("COMMERCE_SETTINGS");
        assertThat(audit.get("target_id")).isEqualTo("request-limits");
        assertThat((String) audit.get("detail")).contains("\"before\":{\"POINT_CHARGE\":3,\"SHOP_PURCHASE\":10}")
                .contains("\"after\":{\"POINT_CHARGE\":0,\"SHOP_PURCHASE\":20}").doesNotContain("GROUPBUY_JOIN");
        assertThat(audit.get("retention_class")).isEqualTo("GENERAL");

        assertThat(copy.limitOf(RequestAction.SHOP_PURCHASE)).as("커밋 뒤 사본을 비웠다").isEqualTo(20);
        assertThat(copy.limitOf(RequestAction.POINT_CHARGE)).isZero();
    }

    @Test
    void 바뀐_것이_없으면_쓰지도_감사하지도_않는다() {
        long operator = newOperator();
        service.save(actor(operator), body(5, 3, 10, 3));
        assertThat(auditCount(operator, AuditActions.COMMERCE_REQUEST_LIMIT_UPDATE)).isZero();
        assertThat(service.load().updatedAt()).isNull();
    }

    @Test
    void 정수_0_에서_100_밖이거나_빠지면_400_그_칸만_표시하고_아무것도_저장하지_않는다() {
        long operator = newOperator();
        assertThatThrownBy(() -> service.save(actor(operator), body(101, -1, 1.5, "abc")))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsOnlyKeys("limits.GROUPBUY_JOIN", "limits.POINT_CHARGE",
                        "limits.SHOP_PURCHASE", "limits.REFUND_REQUEST"));
        assertThatThrownBy(() -> service.save(actor(operator), body(0, 100, 10, null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsOnlyKeys("limits.REFUND_REQUEST"));
        assertThatThrownBy(() -> service.save(actor(operator), null)).isInstanceOf(ValidationException.class);
        assertThat(service.load().items()).extracting(RequestLimitsView.Item::limitPerMinute).containsExactly(5, 3, 10, 3);
        assertThat(auditCount(operator, AuditActions.COMMERCE_REQUEST_LIMIT_UPDATE)).isZero();
    }

    @Test
    void 모르는_요청_종류는_400() {
        Map<String, Object> limits = new HashMap<>(body(5, 3, 10, 3).limits());
        limits.put("PAYOUT", 1);
        assertThatThrownBy(() -> service.save(actor(newOperator()), new RequestLimitsSaveRequest(limits)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsOnlyKeys("limits"));
    }

    @Test
    void 조회와_저장_모두_commerce_settings_권한() throws Exception {
        for (Method method : CommerceSettingsController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PutMapping.class)) {
                RequirePermission required = method.getAnnotation(RequirePermission.class);
                assertThat(required).as(method.getName()).isNotNull();
                assertThat(required.value()).containsExactly(Permission.COMMERCE_SETTINGS);
            }
        }
    }
}
