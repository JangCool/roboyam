package com.community.menu.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.menu.dto.AdminMenuNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 「설정 › 시스템 › 로그인 보안」 메뉴 — 소셜 로그인 다음(0-4 의 포트원이 그 사이에 들어왔다), 루트 전용(권한 코드 없음), 주소·아이콘·이름 키 (commerce-foundation-contract 「0-3」 화면) */
class AdminScreenRegistryLoginSecurityTest {

    @Test
    void 로그인_보안_화면은_루트_전용이고_권한_코드가_없다() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("loginSecurity").orElseThrow();

        assertThat(screen.path()).isEqualTo("/settings/login-security");
        assertThat(screen.labelKey()).isEqualTo("menu.loginSecurity");
        assertThat(screen.icon()).isEqualTo("pi pi-shield");
        assertThat(screen.superOnly()).isTrue();
        assertThat(screen.permission()).isNull();
        assertThat(AdminScreenRegistry.screen("socialLogin").orElseThrow().superOnly()).isFalse();
    }

    @Test
    void 설정_시스템_묶음에서_소셜_로그인과_포트원_다음이다() {
        AdminMenuNode system = AdminScreenRegistry.defaults().get(AdminScreenRegistry.SETTINGS).stream()
                .filter(node -> "menu.system".equals(node.labelKey())).findFirst().orElseThrow();
        List<String> keys = system.children().stream().map(AdminMenuNode::targetKey).toList();

        assertThat(keys).containsSubsequence("socialLogin", "portone", "loginSecurity");
        assertThat(keys.indexOf("loginSecurity")).isEqualTo(keys.indexOf("portone") + 1);
    }
}
