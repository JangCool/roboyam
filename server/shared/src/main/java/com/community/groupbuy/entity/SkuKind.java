package com.community.groupbuy.entity;

/** 단품 종류(`group_buy_skus.kind`, 결정 88) — MAIN 본 상품(축 값 조합) / ADDON 추가 구성상품(별개 상품 — 단독 구매 불가, 공구 최대·성사·1인 한도에 세지 않는다) */
public enum SkuKind {
    MAIN, ADDON
}
