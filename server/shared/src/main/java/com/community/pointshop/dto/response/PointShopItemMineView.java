package com.community.pointshop.dto.response;

import java.time.Instant;

/**
 * 이 품목에 대한 내 보유 상태(user-web {@code PointShopItemMine}).
 *
 * @param owned        영구 아이템을 가졌다(환불하지 않은 것) — 다시 살 수 없다
 * @param count        안 쓴 소모형 장수(변경권)
 * @param periodEndsAt 기간제 — 지금 가진 기간의 가장 늦은 끝(또 사면 여기서 이어 붙는다). 없으면 null
 */
public record PointShopItemMineView(boolean owned, int count, Instant periodEndsAt) {

    public static final PointShopItemMineView NONE = new PointShopItemMineView(false, 0, null);
}
