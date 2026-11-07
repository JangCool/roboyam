package com.community.menu.service;

import com.community.commerce.sales.CommerceSalesStore;
import org.springframework.stereotype.Component;

/**
 * 메뉴가 보는 공동구매 판매 스위치 — {@code commerce_sales_settings.groupbuy_open}(DB 의 지금 값, 캐시 없음). 꺼지면 판매 스위치가 필요한 앱 화면 항목
 * ({@code MenuBuiltin#salesGated} — 공동구매 목록·주문 내역)이 상태 {@code SALES_OFF} 로 공개 메뉴에서 빠진다(menu-contract 미확정 2 해소, 2026-11-07).
 * 운영 주체가 개인 단계면 판매를 켤 수 없으므로 늘 꺼짐이다(terms-operator-contract).
 */
@Component
public class MenuSales {

    private final CommerceSalesStore store;

    public MenuSales(CommerceSalesStore store) {
        this.store = store;
    }

    public boolean enabled() {
        return store.groupBuyOpen();
    }
}
