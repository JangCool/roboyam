package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.controller.AuditLogController;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.request.AuditRetentionSaveRequest;
import com.community.audit.dto.response.AuditRetentionView;
import com.community.audit.service.AuditRetentionService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.zconfiguration.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 감사 보관 기간 화면 API(docs/domain/commerce.md 13-4 「기간은 관리자 화면에서 바꾸되 하한보다 짧게는 못 줄인다. 바꾸면 감사」, commerce-foundation-contract 「0-5」 —
 * 바꾸기는 최고 관리자만). 실제 관리자 앱 문맥 + PostgreSQL. 설정값은 시험 끝에 기본값으로 되돌린다.
 */
class AuditRetentionAdminTest extends CommerceAdminTestBase {

    @Autowired
    private AuditRetentionService service;

    @AfterEach
    void restore() {
        jdbc.update("UPDATE batch_params SET param_value = 365 WHERE job_key = 'audit-log-retention' AND param_key = 'retention_days'");
        jdbc.update("UPDATE batch_params SET param_value = 730 WHERE job_key = 'audit-log-retention' AND param_key = 'personal_data_retention_days'");
        jdbc.update("UPDATE batch_params SET param_value = 1825 WHERE job_key = 'audit-log-retention' AND param_key = 'money_retention_days'");
    }

    private static AuditRetentionSaveRequest body(Object general, Object pii, Object money) {
        Map<String, Object> days = new LinkedHashMap<>();
        days.put("GENERAL", general);
        days.put("PERSONAL_DATA", pii);
        days.put("MONEY", money);
        return new AuditRetentionSaveRequest(days);
    }

    private long param(String key) {
        return jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = 'audit-log-retention' AND param_key = ?", Long.class, key);
    }

    @Test
    void 조회는_분류_셋을_정해진_차례로_기본값_하한_상한과_함께_주고_루트만_바꿀_수_있다고_알린다() {
        AuditRetentionView asOperator = service.view(actor(newOperator()));
        assertThat(asOperator.items()).extracting(AuditRetentionView.Item::category).containsExactly("GENERAL", "PERSONAL_DATA", "MONEY");
        assertThat(asOperator.items()).extracting(AuditRetentionView.Item::days).containsExactly(365L, 730L, 1825L);
        assertThat(asOperator.items()).extracting(AuditRetentionView.Item::minDays).containsExactly(1, 365, 1825);
        assertThat(asOperator.items()).extracting(AuditRetentionView.Item::maxDays).containsExactly(3650, 3650, 3650);
        assertThat(asOperator.canEdit()).isFalse();
        assertThat(service.view(actor(rootId())).canEdit()).isTrue();
    }

    @Test
    void 루트가_아니면_403_과_거부_감사() {
        long operator = newOperator();
        assertThatThrownBy(() -> service.save(actor(operator), body(400, 800, 2000))).isInstanceOf(PermissionDeniedException.class);
        assertThat(param("retention_days")).isEqualTo(365);
        assertThat(auditCount(operator, AuditActions.PERMISSION_DENIED)).isEqualTo(1);
    }

    @Test
    void 하한보다_짧거나_상한을_넘거나_정수가_아니면_400_그_분류만_표시하고_저장하지_않는다() {
        long root = rootId();
        assertThatThrownBy(() -> service.save(actor(root), body(0, 364, 1824)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsOnlyKeys("days.GENERAL", "days.PERSONAL_DATA", "days.MONEY"));
        assertThatThrownBy(() -> service.save(actor(root), body(365, 730, 3651)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsOnlyKeys("days.MONEY"));
        assertThatThrownBy(() -> service.save(actor(root), body(365.5, "730", null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFields()).containsOnlyKeys("days.GENERAL", "days.PERSONAL_DATA", "days.MONEY"));
        assertThat(param("personal_data_retention_days")).isEqualTo(730);
        assertThat(param("money_retention_days")).isEqualTo(1825);
    }

    @Test
    void 루트가_저장하면_배치_설정값을_바꾸고_바뀐_분류만_감사에_전후_감사_줄은_돈_관련_5년_분류() {
        long root = rootId();
        AuditRetentionView saved = service.save(actor(root), body(180, 730, 2555));

        assertThat(param("retention_days")).isEqualTo(180);
        assertThat(param("personal_data_retention_days")).isEqualTo(730);
        assertThat(param("money_retention_days")).isEqualTo(2555);
        assertThat(saved.items()).extracting(AuditRetentionView.Item::days).containsExactly(180L, 730L, 2555L);
        assertThat(saved.updatedBy()).isEqualTo(jdbc.queryForObject("SELECT operator_name FROM operators WHERE id = ?", String.class, root));
        assertThat(saved.updatedAt()).isNotNull();

        Map<String, Object> audit = lastAudit(root, AuditActions.AUDIT_RETENTION_UPDATE);
        assertThat(audit.get("retention_class")).isEqualTo("MONEY");
        assertThat(audit.get("target_type")).isEqualTo("AUDIT_RETENTION");
        assertThat((String) audit.get("detail")).contains("\"before\":{\"GENERAL\":365,\"MONEY\":1825}").contains("\"after\":{\"GENERAL\":180,\"MONEY\":2555}")
                .doesNotContain("PERSONAL_DATA");
    }

    @Test
    void 저장된_값이_하한보다_짧으면_배치처럼_기본값을_보인다() {
        jdbc.update("UPDATE batch_params SET param_value = 10 WHERE job_key = 'audit-log-retention' AND param_key = 'money_retention_days'");
        AuditRetentionView view = service.view(actor(newOperator()));
        assertThat(view.items().get(2).days()).isEqualTo(1825);
    }

    @Test
    void 조회는_audit_read_저장은_로그인만_선언하고_서비스가_루트를_본다() throws Exception {
        RequirePermission read = AuditLogController.class.getMethod("retention", com.community.permission.dto.AdminActor.class).getAnnotation(RequirePermission.class);
        assertThat(read.value()).containsExactly(Permission.AUDIT_READ);
        var save = AuditLogController.class.getMethod("saveRetention", com.community.permission.dto.AdminActor.class, AuditRetentionSaveRequest.class);
        assertThat(save.isAnnotationPresent(AdminAuthenticated.class)).isTrue();
        assertThat(save.isAnnotationPresent(RequirePermission.class)).as("권한 코드로 위임하지 않는다").isFalse();
    }
}
