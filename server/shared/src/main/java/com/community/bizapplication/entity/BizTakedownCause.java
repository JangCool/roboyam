package com.community.bizapplication.entity;

/**
 * 내림 까닭 — 사업자 정지(H2) · 긴급 내림(운영자 — 광고 일정표 세션) · 광고주 요청 일시 정지·중단(V127 — biz-pause-contract). 관리자 [되돌리기](보상 선택)는
 * {@link #EMERGENCY} 만, 정지는 [다시 켜기], 중단은 되돌리기 없음.
 */
public enum BizTakedownCause {
    SUSPENDED, EMERGENCY,
    /** 광고주가 잠깐 멈춤 — [다시 켜기] → 예약(끝 시각 그대로 · 보상 없음) */
    ADVERTISER_PAUSE,
    /** 광고주가 끝냄 — 되돌리기 없음, 일할 환불({@code STOP_PRORATED}) 심사 */
    ADVERTISER_STOP;

    /** 광고주 요청 내림(정지·중단) */
    public boolean advertiser() {
        return this == ADVERTISER_PAUSE || this == ADVERTISER_STOP;
    }
}
