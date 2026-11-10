package com.community.aimember.llm;

import com.community.aimember.entity.AiProvider;

/**
 * 생성 요청 한 번 — 시스템 지시(페르소나·규칙)와 사용자 메시지(할 일 + 인용한 글). 회원 닉네임은 넣지 않는다(법무 메모 「개인정보」).
 *
 * @param maxTokens 최대 출력 토큰
 */
public record AiLlmRequest(AiProvider provider, String model, String system, String user, int maxTokens) {

    /** 프롬프트는 로그에 싣지 않는다 */
    @Override
    public String toString() {
        return "AiLlmRequest[provider=" + provider + ", model=" + model + "]";
    }
}
