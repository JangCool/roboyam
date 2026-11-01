package com.community.hotdeal.dto.response;

import java.time.Instant;

/** 같은 딜 한 줄(user-web {@code DealSameRef}) — 공개·진행 중인 같은 딜 키의 글. 배송비 0 무료 · null 모름 */
public record DealSameRefView(String postId, String title, long dealPrice, Long shippingFee, Instant createdAt) {
}