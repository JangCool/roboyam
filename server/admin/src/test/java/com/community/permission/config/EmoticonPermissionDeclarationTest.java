package com.community.permission.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.emoticon.controller.EmoticonAdminController;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * 관리자 로보티콘 API 의 입구 선언(emoticon-contract S1) — 모든 핸들러가 권한 선언(인터셉터 판정 PERMISSION)을 가지고 계약과 같다:
 * 조회 {@code emoticon.read}, 승인·반려·판매 중단·다시 판매·가격 바꾸기(S2)·운영 팩·그림 올리기 {@code emoticon.review}, 설정 저장 {@code emoticon.settings},
 * 작가 정산 계좌 원문 보기 {@code emoticon.payout.pii}(S2).
 */
class EmoticonPermissionDeclarationTest {

    @Test
    void 모든_핸들러가_권한을_선언하고_계약과_같다() {
        assertThat(EmoticonAdminController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/admin/emoticons");
        EmoticonAdminController instance = new EmoticonAdminController(null, null, null, null);
        Map<String, List<Permission>> declared = new LinkedHashMap<>();
        for (Method method : EmoticonAdminController.class.getDeclaredMethods()) {
            String path = method.isAnnotationPresent(GetMapping.class) ? "GET " + method.getAnnotation(GetMapping.class).value()[0]
                    : method.isAnnotationPresent(PostMapping.class) ? "POST " + method.getAnnotation(PostMapping.class).value()[0]
                    : method.isAnnotationPresent(PutMapping.class) ? "PUT " + method.getAnnotation(PutMapping.class).value()[0] : null;
            if (path == null) {
                continue;
            }
            assertThat(AdminAccessInterceptor.kindOf(new HandlerMethod(instance, method))).as(path).isEqualTo(AdminAccessInterceptor.Kind.PERMISSION);
            RequirePermission required = method.getAnnotation(RequirePermission.class);
            assertThat(required.anyOf()).as(path).isEmpty();
            declared.put(path, Arrays.asList(required.value()));
        }
        assertThat(declared).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("GET /reviews", List.of(Permission.EMOTICON_READ)),
                Map.entry("GET /reviews/{id}", List.of(Permission.EMOTICON_READ)),
                Map.entry("POST /reviews/{id}/approve", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("POST /reviews/{id}/reject", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("GET /packs", List.of(Permission.EMOTICON_READ)),
                Map.entry("GET /packs/{id}", List.of(Permission.EMOTICON_READ)),
                Map.entry("POST /packs/{id}/suspend", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("POST /packs/{id}/resume", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("POST /packs/{id}/price", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("POST /creators/{memberNo}/payout-account/reveal", List.of(Permission.EMOTICON_PAYOUT_PII)),
                Map.entry("POST /packs", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("PUT /packs/{id}", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("POST /uploads", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("POST /uploads/cover", List.of(Permission.EMOTICON_REVIEW)),
                Map.entry("GET /settings", List.of(Permission.EMOTICON_READ)),
                Map.entry("PUT /settings", List.of(Permission.EMOTICON_SETTINGS)),
                Map.entry("GET /creators/{memberNo}", List.of(Permission.EMOTICON_READ))));
    }

    /** S3 — 판매 내역·예외 환불은 emoticon.sales 하나 */
    @Test
    void 판매_내역_입구는_모두_emoticon_sales() {
        assertThat(com.community.emoticon.controller.EmoticonSalesAdminController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/admin/emoticons/sales");
        com.community.emoticon.controller.EmoticonSalesAdminController instance = new com.community.emoticon.controller.EmoticonSalesAdminController(null);
        Map<String, List<Permission>> declared = new LinkedHashMap<>();
        for (Method method : com.community.emoticon.controller.EmoticonSalesAdminController.class.getDeclaredMethods()) {
            String path = method.isAnnotationPresent(GetMapping.class) ? "GET " + String.join("", method.getAnnotation(GetMapping.class).value())
                    : method.isAnnotationPresent(PostMapping.class) ? "POST " + method.getAnnotation(PostMapping.class).value()[0] : null;
            if (path == null) {
                continue;
            }
            assertThat(AdminAccessInterceptor.kindOf(new HandlerMethod(instance, method))).as(path).isEqualTo(AdminAccessInterceptor.Kind.PERMISSION);
            RequirePermission required = method.getAnnotation(RequirePermission.class);
            assertThat(required.anyOf()).as(path).isEmpty();
            declared.put(path, Arrays.asList(required.value()));
        }
        assertThat(declared).containsExactlyInAnyOrderEntriesOf(Map.of("GET ", List.of(Permission.EMOTICON_SALES), "POST /{id}/refund", List.of(Permission.EMOTICON_SALES)));
    }

    /** S4 — 작가 정산은 emoticon.settlement, 정산 줄의 계좌 원문 보기만 emoticon.payout.pii */
    @Test
    void 작가_정산_입구는_emoticon_settlement_계좌_원문_보기는_payout_pii() {
        Class<?> type = com.community.emoticon.controller.EmoticonSettlementAdminController.class;
        assertThat(type.getAnnotation(RequestMapping.class).value()).containsExactly("/api/admin/emoticons/settlements");
        com.community.emoticon.controller.EmoticonSettlementAdminController instance = new com.community.emoticon.controller.EmoticonSettlementAdminController(null);
        Map<String, List<Permission>> declared = new LinkedHashMap<>();
        for (Method method : type.getDeclaredMethods()) {
            String path = method.isAnnotationPresent(GetMapping.class) ? "GET " + String.join("", method.getAnnotation(GetMapping.class).value())
                    : method.isAnnotationPresent(PostMapping.class) ? "POST " + method.getAnnotation(PostMapping.class).value()[0] : null;
            if (path == null) {
                continue;
            }
            assertThat(AdminAccessInterceptor.kindOf(new HandlerMethod(instance, method))).as(path).isEqualTo(AdminAccessInterceptor.Kind.PERMISSION);
            RequirePermission required = method.getAnnotation(RequirePermission.class);
            assertThat(required.anyOf()).as(path).isEmpty();
            declared.put(path, Arrays.asList(required.value()));
        }
        assertThat(declared).containsExactlyInAnyOrderEntriesOf(Map.of(
                "GET ", List.of(Permission.EMOTICON_SETTLEMENT),
                "GET /{id}", List.of(Permission.EMOTICON_SETTLEMENT),
                "POST /confirm", List.of(Permission.EMOTICON_SETTLEMENT),
                "POST /{id}/hold", List.of(Permission.EMOTICON_SETTLEMENT),
                "POST /{id}/unhold", List.of(Permission.EMOTICON_SETTLEMENT),
                "POST /{id}/paid", List.of(Permission.EMOTICON_SETTLEMENT),
                // 탈퇴 작가 보류의 포기 처리(V148) — 정산 처리와 같은 권한
                "POST /{id}/waive", List.of(Permission.EMOTICON_SETTLEMENT),
                "POST /{id}/account/reveal", List.of(Permission.EMOTICON_PAYOUT_PII)));
    }
}
