package com.community.menu.service;

/**
 * 공동구매 판매 스위치 — roboyam 에는 아직 공동구매 기능(과 판매 스위치)이 없어 <b>늘 켜짐</b>이다(docs/features/menu-contract.md 미확정 2).
 * 스위치가 생기면 이 한 곳에서 그 설정을 읽는다. 꺼지면 판매 스위치가 필요한 앱 화면 항목({@code MenuBuiltin#salesGated})이 상태 {@code SALES_OFF} 로 공개 메뉴에서 빠진다.
 */
public final class MenuSales {

    private MenuSales() {
    }

    public static boolean enabled() {
        return true;
    }
}
