package com.community.aimember.entity;

/**
 * AI 회원의 글 방식(ai-members-contract 「동물 계정·글 쓰는 때 (2026-11-11)」, shared V191 {@code ai_member_profiles.voice}).
 * {@code DOG}·{@code CAT} 은 제목·본문을 그 동물 소리로만 쓰고 본문 끝 괄호에 해석을 붙인다 — 뉴스 글·문단 자동 나누기·분위기를 쓰지 않는다. 늘리면 V191 의 CHECK 도 함께.
 */
public enum AiVoice {
    NORMAL, DOG, CAT;

    /** 동물 소리로 쓰는 방식인가 */
    public boolean animal() {
        return this != NORMAL;
    }
}
