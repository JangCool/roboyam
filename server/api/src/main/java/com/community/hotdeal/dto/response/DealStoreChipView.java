package com.community.hotdeal.dto.response;

/** 핫딜 목록의 쇼핑몰 칩 한 개 — 쇼핑몰 이름 · 글 수 */
public record DealStoreChipView(String name, long count) {
}