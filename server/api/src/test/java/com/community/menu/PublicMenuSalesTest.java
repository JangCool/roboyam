package com.community.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.menu.dto.response.MenuItemNodeView;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.service.PublicMenuService;
import com.community.testsupport.SiteOperatorFixture;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공개 메뉴의 공동구매 항목 — 판매 스위치({@code commerce_sales_settings.groupbuy_open})가 꺼지면 {@code salesGated} 앱 화면(공동구매 목록·주문 내역)이 빠진다
 * (menu-contract 미확정 2 해소 2026-11-07, {@code MenuSales}). 운영 주체가 개인 단계면 판매를 켤 수 없으므로 늘 빠진다. 시험 전용 메뉴 그룹을 만들어 본다.
 */
class PublicMenuSalesTest extends CommerceApiTestBase {

    @Autowired
    private PublicMenuService menus;

    private String setKey;

    @BeforeEach
    void setUp() {
        setKey = "sales-" + Long.toString(ids.nextId(), 36);
        jdbc.update("INSERT INTO menu_sets (set_key, name, placement) VALUES (?, '판매 시험', 'CUSTOM')", setKey);
        insertItem(0, "HOME", "ALL");
        insertItem(1, "GROUP_BUYS", "ALL");
        insertItem(2, "GROUP_BUY_ORDERS", "MEMBER");
    }

    @AfterEach
    void restore() {
        jdbc.update("DELETE FROM menu_sets WHERE set_key = ?", setKey);
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        SiteOperatorFixture.clear(jdbc);
    }

    private void insertItem(int order, String key, String audience) {
        jdbc.update("INSERT INTO menu_nodes (id, set_key, kind, sort_order, audience, target_type, target_key) VALUES (?, ?, 'ITEM', ?, ?, 'BUILTIN', ?)", ids.nextId(),
                setKey, order, audience, key);
    }

    private List<String> keys(Long userId) {
        MenuResponse res = menus.menu(setKey, userId);
        return res.nodes().stream().filter(MenuItemNodeView.class::isInstance).map(n -> ((MenuItemNodeView) n).key()).toList();
    }

    @Test
    void 개인_단계_판매_꺼짐이면_공동구매_목록과_주문_내역이_빠진다() {
        SiteOperatorFixture.clear(jdbc);
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");

        assertThat(keys(null)).containsExactly("HOME");
        assertThat(keys(newMember())).as("회원에게도").containsExactly("HOME");
    }

    @Test
    void 판매가_켜지면_보이고_개인사업자여도_스위치를_끄면_다시_빠진다() {
        SiteOperatorFixture.fillBusiness(jdbc);
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        long member = newMember();

        assertThat(keys(null)).as("주문 내역은 회원만").containsExactly("HOME", "GROUP_BUYS");
        assertThat(keys(member)).containsExactly("HOME", "GROUP_BUYS", "GROUP_BUY_ORDERS");

        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        assertThat(keys(member)).containsExactly("HOME");
    }
}
