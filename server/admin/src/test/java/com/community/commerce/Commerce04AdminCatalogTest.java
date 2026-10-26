package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.service.AuditLabels;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionType;
import com.community.permission.entity.PermissionUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 커머스 0-4 가 서버 카탈로그에 더한 줄 — 권한 {@code member.withdrawn.contact}(세션 정리 결정 12·18), 메뉴 「설정 › 시스템 › 포트원」, 감사 대상 {@code PORTONE_SETTINGS} —
 * 그리고 서버 원천과 admin-web 사본({@code util-permission-catalog.ts}·{@code util-admin-menu.ts})이 같다(어긋나면 화면이 모르는 코드를 받는다).
 */
class Commerce04AdminCatalogTest {

    /** 시험 작업 폴더 = server/admin */
    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");

    private static final Pattern PERMISSION_LINE = Pattern.compile(
            "\\{ code: '([^']+)', unit: '([A-Z]+)', type: '([A-Za-z]+)', nameKey: '[^']+', descKey: '[^']+', featureReady: (true|false) }");

    @Test
    void 탈퇴_회원_연락처_권한은_회원_조회_유형의_민감_조치이고_화면이_있다_member_pii_바로_다음() {
        Permission permission = Permission.ofCode("member.withdrawn.contact").orElseThrow();
        assertThat(permission).isEqualTo(Permission.MEMBER_WITHDRAWN_CONTACT);
        assertThat(permission.type()).isEqualTo(PermissionType.MEMBER_VIEW);
        assertThat(permission.unit()).isEqualTo(PermissionUnit.SENSITIVE);
        assertThat(permission.featureReady()).isTrue();
        assertThat(permission.ordinal()).isEqualTo(Permission.MEMBER_PII.ordinal() + 1);
        assertThat(Permission.candidatesOf(PermissionType.MEMBER_VIEW)).containsExactly(Permission.MEMBER_READ, Permission.MEMBER_PII, Permission.MEMBER_WITHDRAWN_CONTACT);
    }

    @Test
    void 권한_카탈로그는_admin_web_사본과_같다_차례_단위_유형_화면_여부까지() throws IOException {
        String source = Files.readString(ADMIN_WEB.resolve("util-permission-catalog.ts"));
        List<String> web = new ArrayList<>();
        Matcher matcher = PERMISSION_LINE.matcher(source);
        while (matcher.find()) {
            web.add(matcher.group(1) + "|" + matcher.group(2) + "|" + matcher.group(3) + "|" + matcher.group(4));
        }
        List<String> server = Arrays.stream(Permission.values()).map(p -> p.code() + "|" + p.unit().name() + "|" + p.type().id() + "|" + p.featureReady()).toList();

        assertThat(web).containsExactlyElementsOf(server);
    }

    @Test
    void 포트원_화면은_설정_시스템에서_소셜_로그인_바로_다음이고_site_read() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("portone").orElseThrow();
        assertThat(screen.path()).isEqualTo("/settings/portone");
        assertThat(screen.labelKey()).isEqualTo("menu.portone");
        assertThat(screen.icon()).isEqualTo("pi pi-credit-card");
        assertThat(screen.permission()).isEqualTo(Permission.SITE_READ);
        assertThat(screen.superOnly()).isFalse();

        AdminMenuNode system = AdminScreenRegistry.defaults().get(AdminScreenRegistry.SETTINGS).stream()
                .filter(node -> "menu.system".equals(node.labelKey())).findFirst().orElseThrow();
        List<String> keys = system.children().stream().map(AdminMenuNode::targetKey).toList();
        assertThat(keys.indexOf("portone")).isEqualTo(keys.indexOf("socialLogin") + 1);
    }

    @Test
    void 포트원_메뉴_줄은_admin_web_사본과_같다() throws IOException {
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("portone").orElseThrow();
        String expected = "{ key: 'portone', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                + screen.permission().code() + "' }";
        assertThat(source).contains(expected);
        // 같은 차례: 소셜 로그인 → 포트원 → 로그인 보안
        int social = source.indexOf("key: 'socialLogin'");
        int portone = source.indexOf("key: 'portone'");
        int loginSecurity = source.indexOf("key: 'loginSecurity'");
        assertThat(social).isPositive().isLessThan(portone);
        assertThat(portone).isLessThan(loginSecurity);
    }

    @Test
    void 감사_대상_종류에_포트원_설정이_있다() {
        assertThat(AuditLabels.TARGET_TYPES).contains("PORTONE_SETTINGS");
    }
}
