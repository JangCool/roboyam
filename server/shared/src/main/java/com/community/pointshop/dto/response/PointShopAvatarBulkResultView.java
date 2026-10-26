package com.community.pointshop.dto.response;

import com.community.pointshop.dto.request.PointShopAvatarScope;

/**
 * 아바타 묶음 가격·판매 변경 결과.
 *
 * @param matched 범위에 든 아바타 품목 수
 * @param changed 값이 실제로 바뀐 줄 수(같은 값이면 0)
 * @param price   가격 요청이면 새 가격(판매 요청이면 null)
 * @param active  판매 요청이면 새 값(가격 요청이면 null)
 */
public record PointShopAvatarBulkResultView(PointShopAvatarScope scope, int matched, int changed, Integer price, Boolean active) {
}
