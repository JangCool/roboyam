package com.community.groupbuy.service;

import org.springframework.stereotype.Component;

/**
 * 판매 스위치가 아직 없을 때(2b-2)의 자리 — 늘 열림. 회원 주문 API 는 2b-4 에서 판매 스위치와 함께 열리므로 이 기본값으로 주문이 들어올 길은 없다.
 */
@Component
public class DefaultGroupBuySalesSwitch implements GroupBuySalesSwitch {

    @Override
    public boolean groupBuyOpen() {
        return true;
    }
}
