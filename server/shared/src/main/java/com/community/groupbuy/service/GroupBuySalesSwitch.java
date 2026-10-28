package com.community.groupbuy.service;

/**
 * 공구 판매 스위치(commerce.md 4절·20절) — 꺼져 있으면 새 주문을 받지 않는다(403 {@code GROUP_BUY_SALES_CLOSED}).
 * 스위치·체크리스트·사업자 정보는 2b-4(V104 — {@code commerce.sales}), 구현은 {@link DefaultGroupBuySalesSwitch}(DB 의 지금 값).
 */
public interface GroupBuySalesSwitch {

    boolean groupBuyOpen();
}
