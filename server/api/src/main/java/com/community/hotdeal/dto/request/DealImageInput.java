package com.community.hotdeal.dto.request;

/**
 * 딜 이미지(user-web {@code DealImage}) — {@code OG} 이면 {@code url} 이 우리 OG 사본 주소여야 하고, {@code UPLOAD} 이면 {@code imageId} 가 글쓴이가 올린 글 이미지여야 한다.
 * {@code originUrl} 은 받아도 보지 않는다(서버가 사본 표에서 안다).
 */
public record DealImageInput(String url, String source, String imageId, String originUrl) {
}