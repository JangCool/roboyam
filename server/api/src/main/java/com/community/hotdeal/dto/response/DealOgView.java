package com.community.hotdeal.dto.response;

/** 링크 OG 결과 — {@code status} OK · NONE · FAILED · OFF, {@code imageUrl} 은 우리 서버 사본(D1) */
public record DealOgView(String status, String imageUrl, String title, String storeName) {
}