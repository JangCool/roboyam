package com.community.aimember.entity;

/** AI 회원의 제공사(ai-members-contract 정책 2 — 코파일럿은 뺀다). 열거형이라 나중에 늘릴 수 있다(늘리면 V183 의 CHECK 도 함께) */
public enum AiProvider {
    OPENAI, GOOGLE, ANTHROPIC
}
