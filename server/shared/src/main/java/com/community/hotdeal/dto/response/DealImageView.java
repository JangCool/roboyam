package com.community.hotdeal.dto.response;

/**
 * 딜 이미지(user-web {@code DealImage}) — {@code url} 은 늘 우리 서버 주소(D1).
 *
 * @param imageId   올린 글 이미지 ID(UPLOAD 일 때만)
 * @param originUrl OG 사본의 원본 주소 — 공개 응답은 늘 null
 */
public record DealImageView(String url, String source, String imageId, String originUrl) {
}