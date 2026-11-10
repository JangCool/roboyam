package com.community.aimember.llm;

/** 생성 결과 — 글자와 토큰 수(입력·출력, 생각 토큰은 출력에 든다) */
public record AiLlmResult(String text, int inputTokens, int outputTokens) {

    @Override
    public String toString() {
        return "AiLlmResult[in=" + inputTokens + ", out=" + outputTokens + "]";
    }
}
