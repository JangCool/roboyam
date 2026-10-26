package com.community.pointshop.dto.response;

/**
 * 아바타 하나(카탈로그 차례 — user-web {@code AvatarItem}).
 *
 * @param price      유료만 — 지금 가격(무료·품목 없음은 null)
 * @param itemId     유료만 — 포인트 상점 품목 ID(구매 요청에 쓴다)
 * @param forSale    지금 살 수 있나(유료는 품목 {@code active}, 무료는 true)
 * @param owned      무료는 로그인했으면 true, 유료는 환불하지 않은 보유가 있으면
 * @param refundable 유료이고 안 썼고 환불 기한 안 — 고르면 환불할 수 없어 화면이 한 번 묻는다
 */
public record AvatarItemView(String id, String name, String category, boolean free, Integer price, String itemId, boolean forSale, boolean owned, boolean selected,
                             boolean refundable, String url) {
}
