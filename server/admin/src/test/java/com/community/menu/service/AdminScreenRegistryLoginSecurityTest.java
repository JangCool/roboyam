package com.community.menu.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 「설정 › 시스템 › 로그인 보안」 메뉴 — 소셜 로그인 다음(0-4 의 포트원, 사업자 진위확인의 공공데이터포털이 그 사이에 들어왔다), 루트 전용(권한 코드 없음), 주소·아이콘·이름 키 (commerce-foundation-contract 「0-3」 화면) */
class AdminScreenRegistryLoginSecurityTest {

    @Test
    void 로그인_보안_화면은_루트_전용이고_권한_코드가_없다() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("loginSecurity").orElseThrow();

        assertThat(screen.path()).isEqualTo("/settings/login-security");
        assertThat(screen.labelKey()).isEqualTo("menu.loginSecurity");
        assertThat(screen.icon()).isEqualTo("pi pi-lock");
        assertThat(screen.superOnly()).isTrue();
        assertThat(screen.permission()).isNull();
        assertThat(AdminScreenRegistry.screen("socialLogin").orElseThrow().superOnly()).isFalse();
    }

    @Test
    void 설정_운영자와_권한_묶음의_맨_끝이다() {
        assertThat(AdminMenuDefaults.children(AdminMenuDefaults.group(AdminScreenRegistry.SETTINGS, "menu.operatorsAndPermissions")))
                .containsExactly("operators", "menu.roles", "teams", "menusAdmin", "loginSecurity");
    }
}
