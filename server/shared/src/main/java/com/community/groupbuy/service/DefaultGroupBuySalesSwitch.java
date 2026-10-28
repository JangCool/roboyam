package com.community.groupbuy.service;

import com.community.commerce.sales.CommerceSalesStore;
import org.springframework.stereotype.Component;

/**
 * 판매 스위치의 실제 구현(2b-4, V104) — {@code commerce_sales_settings.groupbuy_open} 을 <b>부른 트랜잭션 안에서</b> 읽는다(결정 19 — 캐시 없음).
 * 설치 직후 꺼짐, local 은 시드가 켠다(commerce.md 20절).
 */
@Component
public class DefaultGroupBuySalesSwitch implements GroupBuySalesSwitch {

    private final CommerceSalesStore store;

    public DefaultGroupBuySalesSwitch(CommerceSalesStore store) {
        this.store = store;
    }

    @Override
    public boolean groupBuyOpen() {
        return store.groupBuyOpen();
    }
}
