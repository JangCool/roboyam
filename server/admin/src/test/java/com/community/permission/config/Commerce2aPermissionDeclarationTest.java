package com.community.permission.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.payment.controller.PaymentAdminController;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionUnit;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * 관리자 결제 API 의 입구 선언(커머스 2a-3 — commerce-payment-contract 결정 12) — 모든 핸들러가 권한 선언(인터셉터 판정 PERMISSION)을 가지고 계약과 같다:
 * 조회(결제 주문·상세·웹훅 수신 내역·결제 이상·대사 요약·이슈) {@code commerce.payment.read}, 처리(포트원에서 다시 확인·다시 시도·수동 처리 완료·이슈 처리·이 날 다시 대사)
 * {@code commerce.reconcile.manage}. 두 권한은 2a-3 에서 기능이 생겼다(featureReady).
 */
class Commerce2aPermissionDeclarationTest {

    @Test
    void 모든_핸들러가_권한을_선언하고_계약과_같다() {
        assertThat(PaymentAdminController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/admin");
        PaymentAdminController instance = new PaymentAdminController(null);
        Map<String, List<Permission>> declared = new LinkedHashMap<>();
        for (Method method : PaymentAdminController.class.getDeclaredMethods()) {
            String path = method.isAnnotationPresent(GetMapping.class) ? "GET " + method.getAnnotation(GetMapping.class).value()[0]
                    : method.isAnnotationPresent(PostMapping.class) ? "POST " + method.getAnnotation(PostMapping.class).value()[0] : null;
            if (path == null) {
                continue;
            }
            assertThat(AdminAccessInterceptor.kindOf(new HandlerMethod(instance, method))).as(path).isEqualTo(AdminAccessInterceptor.Kind.PERMISSION);
            RequirePermission required = method.getAnnotation(RequirePermission.class);
            assertThat(required.anyOf()).as(path).isEmpty();
            declared.put(path, Arrays.asList(required.value()));
        }
        assertThat(declared).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("GET /payments", List.of(Permission.COMMERCE_PAYMENT_READ)),
                Map.entry("GET /payments/{id}", List.of(Permission.COMMERCE_PAYMENT_READ)),
                Map.entry("POST /payments/{id}/recheck", List.of(Permission.COMMERCE_RECONCILE_MANAGE)),
                Map.entry("GET /payment-webhooks", List.of(Permission.COMMERCE_PAYMENT_READ)),
                Map.entry("GET /payment-anomalies", List.of(Permission.COMMERCE_PAYMENT_READ)),
                Map.entry("POST /payment-cancels/{id}/retry", List.of(Permission.COMMERCE_RECONCILE_MANAGE)),
                Map.entry("POST /payment-cancels/{id}/manual", List.of(Permission.COMMERCE_RECONCILE_MANAGE)),
                Map.entry("GET /payment-reconciliation", List.of(Permission.COMMERCE_PAYMENT_READ)),
                Map.entry("GET /payment-reconciliation/issues", List.of(Permission.COMMERCE_PAYMENT_READ)),
                Map.entry("POST /payment-reconciliation/issues/{id}/resolve", List.of(Permission.COMMERCE_RECONCILE_MANAGE)),
                Map.entry("POST /payment-reconciliation/days/{day}/rerun", List.of(Permission.COMMERCE_RECONCILE_MANAGE))));
    }

    @Test
    void 두_권한은_기능이_생겼고_처리_권한은_민감_단위다() {
        assertThat(Permission.COMMERCE_PAYMENT_READ.featureReady()).isTrue();
        assertThat(Permission.COMMERCE_RECONCILE_MANAGE.featureReady()).isTrue();
        assertThat(Permission.COMMERCE_PAYMENT_READ.unit()).isEqualTo(PermissionUnit.READ);
        assertThat(Permission.COMMERCE_RECONCILE_MANAGE.unit()).isEqualTo(PermissionUnit.SENSITIVE);
    }
}
