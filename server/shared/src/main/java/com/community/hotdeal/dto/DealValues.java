package com.community.hotdeal.dto;

import java.time.Instant;

/**
 * 검사를 마친 딜 칸(저장할 값) — 글쓰기·고치기가 만들어 {@code PostDealEntity} 에 넘긴다. 금액은 원 정수, 배송비 0 무료 · null 모름.
 *
 * @param imageSource OG · UPLOAD · null(이미지 없음)
 * @param ogImageId   OG 사본({@code deal_og_images.id}) — OG 일 때만
 * @param imageId     올린 글 이미지({@code files.id}) — UPLOAD 일 때만
 * @param imageUrl    우리 서버 주소
 */
public record DealValues(String storeName, String productUrl, Long normalPrice, long dealPrice, Long shippingFee, String couponNote, String priceBasisKind,
                         boolean dutyIncluded, String priceBasis, Instant endsAt, String stockState, boolean affiliate, String imageSource, Long ogImageId,
                         Long imageId, String imageUrl) {
}
