package com.community.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLabels;
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
 * 공공데이터포털 설정이 서버 카탈로그에 더한 줄 — 메뉴 「설정 › 시스템 › 공공데이터포털」(포트원 바로 다음, {@code site.read}), 감사 대상 {@code PUBLIC_DATA_SETTINGS}·행위 둘(일반 분류) —
 * 그리고 메뉴 줄이 admin-web 사본({@code util-admin-menu.ts})과 같다.
 */
class PublicDataAdminCatalogTest {

    /** 시험 작업 폴더 = server/admin */
    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");

    @Test
    void 공공데이터포털_화면은_설정_시스템에서_포트원_바로_다음이고_site_read() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("publicData").orElseThrow();
        assertThat(screen.path()).isEqualTo("/settings/public-data");
        assertThat(screen.labelKey()).isEqualTo("menu.publicData");
        assertThat(screen.icon()).isEqualTo("pi pi-building-columns");
        assertThat(screen.permission()).isEqualTo(Permission.SITE_READ);
        assertThat(screen.superOnly()).isFalse();

        AdminMenuNode system = AdminScreenRegistry.defaults().get(AdminScreenRegistry.SETTINGS).stream()
                .filter(node -> "menu.system".equals(node.labelKey())).findFirst().orElseThrow();
        List<String> keys = system.children().stream().map(AdminMenuNode::targetKey).toList();
        assertThat(keys.indexOf("publicData")).isEqualTo(keys.indexOf("portone") + 1);
    }

    @Test
    void 공공데이터포털_메뉴_줄은_admin_web_사본과_같다() throws IOException {
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("publicData").orElseThrow();
        String expected = "{ key: 'publicData', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                + screen.permission().code() + "' }";
        assertThat(source).contains(expected);
        int portone = source.indexOf("key: 'portone'");
        int publicData = source.indexOf("key: 'publicData'");
        int loginSecurity = source.indexOf("key: 'loginSecurity'");
        assertThat(portone).isPositive().isLessThan(publicData);
        assertThat(publicData).isLessThan(loginSecurity);
    }

    @Test
    void 감사_대상과_행위가_있고_일반_분류() {
        assertThat(AuditLabels.TARGET_TYPES).contains("PUBLIC_DATA_SETTINGS");
        assertThat(AuditRetention.classify(AuditActions.PUBLIC_DATA_SETTINGS_UPDATE)).isEqualTo(AuditRetentionClass.GENERAL);
        assertThat(AuditRetention.classify(AuditActions.PUBLIC_DATA_SETTINGS_DELETE)).isEqualTo(AuditRetentionClass.GENERAL);
    }
}
