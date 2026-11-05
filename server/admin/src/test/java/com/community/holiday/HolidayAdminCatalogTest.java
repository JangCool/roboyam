package com.community.holiday;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
import com.community.batch.core.BatchCatalog;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 휴일 관리가 서버 카탈로그에 더한 줄(사용자 지시 2026-11-05) — 메뉴 「설정 › 시스템 › 휴일 관리」(로그인 보안 바로 다음, {@code site.read} — 새 권한 없음), 메뉴 줄이 admin-web 사본과 같다,
 * 감사 대상 {@code SITE_HOLIDAY}·행위 다섯(일반 분류), 배치 {@code holiday-sync}(Java, 매월 1일, 기본 켬).
 */
class HolidayAdminCatalogTest {

    /** 시험 작업 폴더 = server/admin */
    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");

    @Test
    void 휴일_관리_화면은_설정_시스템에서_로그인_보안_바로_다음이고_site_read() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("holidays").orElseThrow();
        assertThat(screen.path()).isEqualTo("/settings/holidays");
        assertThat(screen.labelKey()).isEqualTo("menu.holidays");
        assertThat(screen.icon()).isEqualTo("pi pi-calendar");
        assertThat(screen.permission()).isEqualTo(Permission.SITE_READ);
        assertThat(screen.superOnly()).isFalse();

        AdminMenuNode system = AdminScreenRegistry.defaults().get(AdminScreenRegistry.SETTINGS).stream()
                .filter(node -> "menu.system".equals(node.labelKey())).findFirst().orElseThrow();
        List<String> keys = system.children().stream().map(AdminMenuNode::targetKey).toList();
        assertThat(keys.indexOf("holidays")).isEqualTo(keys.indexOf("loginSecurity") + 1);
    }

    @Test
    void 휴일_관리_메뉴_줄은_admin_web_사본과_같다() throws IOException {
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("holidays").orElseThrow();
        String expected = "{ key: 'holidays', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                + screen.permission().code() + "' }";
        assertThat(source).contains(expected);
        int loginSecurity = source.indexOf("key: 'loginSecurity'");
        int holidays = source.indexOf("key: 'holidays'");
        int addressSearch = source.indexOf("key: 'addressSearch'");
        assertThat(loginSecurity).isPositive().isLessThan(holidays);
        assertThat(holidays).isLessThan(addressSearch);
    }

    @Test
    void 감사_대상과_행위가_있고_일반_분류_배치는_매월_1일_Java() {
        assertThat(AuditLabels.TARGET_TYPES).contains("SITE_HOLIDAY", "PUBLIC_DATA_SETTINGS");
        for (String action : List.of(AuditActions.SITE_HOLIDAY_CREATE, AuditActions.SITE_HOLIDAY_DELETE, AuditActions.SITE_HOLIDAY_SYNC, AuditActions.PUBLIC_DATA_ENDPOINT_UPDATE,
                AuditActions.PUBLIC_DATA_ENDPOINT_RESET)) {
            assertThat(AuditRetention.declared(action)).as(action).contains(AuditRetentionClass.GENERAL);
        }
        assertThat(BatchCatalog.ofKey("holiday-sync")).isEqualTo(BatchCatalog.HOLIDAY_SYNC);
        assertThat(BatchCatalog.HOLIDAY_SYNC.isJava()).isTrue();
        assertThat(BatchCatalog.HOLIDAY_SYNC.defaultCron()).isEqualTo("0 15 3 1 * *");
        assertThat(BatchCatalog.HOLIDAY_SYNC.defaultEnabled()).isTrue();
    }
}
