package com.community.permission.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.point.controller.PointAdminController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * 관리자 포인트 API 의 입구 선언(커머스 1-1 — commerce-stage1-contract 「1-1」 API) — 모든 핸들러가 권한 선언(인터셉터 판정 PERMISSION)을 가지고 계약과 같다.
 * 회원 묶음 {@code GET /members/{id}/point-lots} 는 {@code point.read}, 조정은 입구 {@code point.adjust}(큰 금액의 {@code point.adjust.large} 는 서비스가 금액을 보고 판정한다).
 */
class PointPermissionDeclarationTest {

    @Test
    void 포인트_API_는_원장_묶음_정책_조회_point_read_후보와_조정_point_adjust_정책_저장_point_settings() {
        PointAdminController instance = new PointAdminController(null, null, null, null);
        Map<String, List<Permission>> declared = new LinkedHashMap<>();
        Map<String, String> paths = new LinkedHashMap<>();
        for (Method method : PointAdminController.class.getDeclaredMethods()) {
            String path = method.isAnnotationPresent(GetMapping.class) ? method.getAnnotation(GetMapping.class).value()[0]
                    : method.isAnnotationPresent(PostMapping.class) ? method.getAnnotation(PostMapping.class).value()[0]
                    : method.isAnnotationPresent(PutMapping.class) ? method.getAnnotation(PutMapping.class).value()[0] : null;
            if (path == null) {
                continue;
            }
            assertThat(AdminAccessInterceptor.kindOf(new HandlerMethod(instance, method))).as(method.getName()).isEqualTo(AdminAccessInterceptor.Kind.PERMISSION);
            RequirePermission required = method.getAnnotation(RequirePermission.class);
            assertThat(required.anyOf()).as(method.getName()).isEmpty();
            declared.put(method.getName(), Arrays.asList(required.value()));
            paths.put(method.getName(), path);
        }
        assertThat(declared).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ledger", List.of(Permission.POINT_READ),
                "candidates", List.of(Permission.POINT_ADJUST),
                "adjust", List.of(Permission.POINT_ADJUST),
                "memberLots", List.of(Permission.POINT_READ),
                "loadSettings", List.of(Permission.POINT_READ),
                "saveSettings", List.of(Permission.POINT_SETTINGS)));
        assertThat(paths.get("memberLots")).isEqualTo("/members/{id}/point-lots");
        assertThat(Permission.POINT_ADJUST_LARGE.featureReady()).as("고액 조정은 1-1 에서 기능이 생겼다").isTrue();
    }
}
