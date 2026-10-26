package com.community.permission.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.portone.controller.PortOneSettingsController;
import com.community.restriction.controller.MemberRestrictionController;
import com.community.withdrawal.controller.WithdrawnContactController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * 커머스 0-4 관리자 API 의 입구 선언(계약 「0-4」 API 표) — 모든 핸들러가 권한 선언({@code @RequirePermission}, 인터셉터 판정 PERMISSION)을 가지고 그 권한이 계약과 같다.
 * 선언이 없는 핸들러는 인터셉터가 거절한다(판정 NONE → 403) — 같은 판정 함수를 쓴다.
 */
class Commerce04PermissionDeclarationTest {

    private static Map<String, List<Permission>> declared(Class<?> controller, Object instance) {
        Map<String, List<Permission>> result = new LinkedHashMap<>();
        for (Method method : controller.getDeclaredMethods()) {
            boolean handler = method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class)
                    || method.isAnnotationPresent(DeleteMapping.class);
            if (!handler) {
                continue;
            }
            assertThat(AdminAccessInterceptor.kindOf(new HandlerMethod(instance, method))).as(method.getName()).isEqualTo(AdminAccessInterceptor.Kind.PERMISSION);
            RequirePermission required = method.getAnnotation(RequirePermission.class);
            assertThat(required.anyOf()).as(method.getName()).isEmpty();
            result.put(method.getName(), Arrays.asList(required.value()));
        }
        return result;
    }

    @Test
    void 기능_제한은_조회_member_read_걸기와_해제_sanction_manage() {
        assertThat(declared(MemberRestrictionController.class, new MemberRestrictionController(null))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "list", List.of(Permission.MEMBER_READ), "grant", List.of(Permission.SANCTION_MANAGE), "lift", List.of(Permission.SANCTION_MANAGE)));
    }

    @Test
    void 탈퇴_회원_연락처는_상태_member_read_열람_member_withdrawn_contact() {
        assertThat(declared(WithdrawnContactController.class, new WithdrawnContactController(null))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "view", List.of(Permission.MEMBER_READ), "reveal", List.of(Permission.MEMBER_WITHDRAWN_CONTACT)));
    }

    @Test
    void 포트원_설정은_조회_site_read_저장과_지우기_site_secret() {
        assertThat(declared(PortOneSettingsController.class, new PortOneSettingsController(null))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "view", List.of(Permission.SITE_READ), "saveIdentity", List.of(Permission.SITE_SECRET), "deleteField", List.of(Permission.SITE_SECRET)));
    }

    static class Undeclared {
        public void handler() {
        }
    }

    @Test
    void 선언이_없으면_거절_판정이다() throws Exception {
        assertThat(AdminAccessInterceptor.kindOf(new HandlerMethod(new Undeclared(), Undeclared.class.getMethod("handler")))).isEqualTo(AdminAccessInterceptor.Kind.NONE);
    }
}
