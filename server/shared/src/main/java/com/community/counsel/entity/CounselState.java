package com.community.counsel.entity;

/**
 * 상담 사연의 서버 상태({@code counsel_posts.state}, V274). 공개 상태({@code CounselStatus} WAITING·ANSWERED·PICKED·RISK)는 이 값과 답·고른 답으로 만든다 —
 * {@code CHECKING}·줄 없음 = WAITING, {@code OPEN} = 답 수에 따라 WAITING·ANSWERED(고르면 PICKED), {@code RISK} = RISK.
 */
public enum CounselState {
    /** 낱말 목록은 지났고 AI 분류를 기다린다 */
    CHECKING,
    /** 상담사가 답한다(또는 답을 마쳤다) */
    OPEN,
    /** 위험 사연 — AI 가 답하지 않고 도움 안내만 */
    RISK
}
