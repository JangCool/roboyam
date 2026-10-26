package com.community.permission.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.pointshop.controller.PointShopAdminController;
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
 * 관리자 포인트 상점 API 의 입구 선언(커머스 1-2 — commerce-stage1-contract 「1-2 화면 목업」 관리자 API) — 모든 핸들러가 권한 선언(인터셉터 판정 PERMISSION)을 가지고 계약과 같다:
 * 조회 {@code pointshop.read}, 품목·아바타 묶음·설정 저장 {@code pointshop.manage}, 예외 환불·환불 신청 수락·거절 {@code pointshop.refund}.
 */
class PointShopPermissionDeclarationTest {

    @Test
    void 모든_핸들러가_권한을_선언하고_계약과_같다() {
        assertThat(PointShopAdminController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/admin/point-shop");
        PointShopAdminController instance = new PointShopAdminController(null);
        Map<String, List<Permission>> declared = new LinkedHashMap<>();
        for (Method method : PointShopAdminController.class.getDeclaredMethods()) {
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
                Map.entry("GET /items", List.of(Permission.POINTSHOP_READ)),
                Map.entry("GET /items/{id}", List.of(Permission.POINTSHOP_READ)),
                Map.entry("POST /items", List.of(Permission.POINTSHOP_MANAGE)),
                Map.entry("PUT /items/{id}", List.of(Permission.POINTSHOP_MANAGE)),
                Map.entry("GET /avatars/summary", List.of(Permission.POINTSHOP_READ)),
                Map.entry("PUT /avatars/price", List.of(Permission.POINTSHOP_MANAGE)),
                Map.entry("POST /avatars/sale", List.of(Permission.POINTSHOP_MANAGE)),
                Map.entry("GET /purchases", List.of(Permission.POINTSHOP_READ)),
                Map.entry("POST /purchases/{id}/refund", List.of(Permission.POINTSHOP_REFUND)),
                Map.entry("POST /refund-requests/{id}/approve", List.of(Permission.POINTSHOP_REFUND)),
                Map.entry("POST /refund-requests/{id}/reject", List.of(Permission.POINTSHOP_REFUND)),
                Map.entry("GET /settings", List.of(Permission.POINTSHOP_READ)),
                Map.entry("PUT /settings", List.of(Permission.POINTSHOP_MANAGE))));
    }
}
