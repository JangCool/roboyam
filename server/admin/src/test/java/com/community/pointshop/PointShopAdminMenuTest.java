package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 관리자 포인트 상점 메뉴(커머스 1-2) — 화면 레지스트리의 「포인트 상점」 묶음 세 줄이 admin-web {@code util-admin-menu.ts} 와 같은 키·주소·권한·아이콘·차례다.
 */
class PointShopAdminMenuTest {

    /** 시험 작업 폴더 = server/admin */
    private static final Path ADMIN_WEB = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils");

    private static final List<String> SCREENS = List.of("pointShopItems", "pointShopPurchases", "pointShopSettings");

    @Test
    void 화면_레지스트리의_포인트_상점_묶음은_포인트_묶음_바로_다음_세_줄이고_pointshop_read() {
        List<AdminMenuNode> normal = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL);
        List<String> sections = normal.stream().map(AdminMenuNode::labelKey).toList();
        assertThat(sections.indexOf("menu.pointShop")).isEqualTo(sections.indexOf("menu.point") + 1);
        AdminMenuNode section = normal.get(sections.indexOf("menu.pointShop"));
        assertThat(section.icon()).isEqualTo("pi pi-shopping-bag");
        assertThat(section.children().stream().map(AdminMenuNode::targetKey).toList()).containsExactlyElementsOf(SCREENS);
        assertThat(AdminScreenRegistry.isGroupLabelKey("menu.pointShop")).isTrue();
        for (String key : SCREENS) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(key).orElseThrow();
            assertThat(screen.permission()).as(key).isEqualTo(Permission.POINTSHOP_READ);
            assertThat(screen.superOnly()).as(key).isFalse();
        }
        assertThat(AdminScreenRegistry.screen("pointShopItems").orElseThrow().path()).isEqualTo("/point-shop/items");
        assertThat(AdminScreenRegistry.screen("pointShopPurchases").orElseThrow().path()).isEqualTo("/point-shop/purchases");
        assertThat(AdminScreenRegistry.screen("pointShopSettings").orElseThrow().path()).isEqualTo("/point-shop/settings");
    }

    @Test
    void 포인트_상점_메뉴_세_줄은_admin_web_사본과_같고_차례도_같다() throws IOException {
        String source = Files.readString(ADMIN_WEB.resolve("util-admin-menu.ts"));
        int last = -1;
        for (String key : SCREENS) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(key).orElseThrow();
            String expected = "{ key: '" + key + "', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                    + screen.permission().code() + "' }";
            assertThat(source).as(key).contains(expected);
            int at = source.indexOf(expected);
            assertThat(at).as(key + " 차례").isGreaterThan(last);
            last = at;
        }
        assertThat(source).containsPattern("key: 'pointShop',\\s*icon: 'pi pi-shopping-bag',\\s*labelKey: 'menu.pointShop',");
        assertThat(source.indexOf("key: 'pointShop'")).isGreaterThan(source.indexOf("key: 'pointPolicy'"));
    }
}
