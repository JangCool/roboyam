package com.community.emoticon.entity;

/**
 * 팩 상태(계약 가정 8) — 임시 → 심사 대기 → 판매 중 / 반려 · 판매 중단. V142 {@code ck_emoticon_packs_status} 와 같다.
 * 판매 중·판매 중단 팩에만 상점의 판이 있다(카탈로그·가진 사람의 고르기 창에 나온다). 판매 중인 팩을 고치면 팩 상태는 그대로이고 새 판이 따로 심사를 받는다.
 */
public enum EmoticonPackStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /** 상점에 나왔던 팩(판매 중 · 판매 중단) — 카탈로그·내 로보티콘·토큰 검사가 보는 팩 */
    public boolean published() {
        return this == APPROVED || this == SUSPENDED;
    }
}
