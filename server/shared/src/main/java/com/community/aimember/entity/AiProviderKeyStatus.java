package com.community.aimember.entity;

import com.community.aimember.llm.AiLlmException;

/**
 * 제공사 키 상태 — {@code ai_provider_keys.status}(shared V189). {@link #OK} 가 아니면 그 제공사의 AI 계정은 쉰다(「키 없음」과 같은 방식 — 생성 호출을 하지 않는다).
 * 실행기가 제공사의 거절(결제·크레딧 부족 · 키 오류)을 보고 바꾸고, 관리자가 키를 다시 저장하면 {@link #OK} 로 돌아온다.
 */
public enum AiProviderKeyStatus {
    OK,
    /** 결제·크레딧 부족 — {@link AiLlmException#BILLING_REQUIRED} */
    BILLING_REQUIRED,
    /** 키 오류(401·403 등) — {@link AiLlmException#KEY_INVALID} */
    KEY_INVALID;

    /** 실패 까닭 코드 → 쉬게 하는 상태(쉬게 하지 않는 까닭이면 null) */
    public static AiProviderKeyStatus ofFailure(String reason) {
        if (AiLlmException.BILLING_REQUIRED.equals(reason)) {
            return BILLING_REQUIRED;
        }
        if (AiLlmException.KEY_INVALID.equals(reason)) {
            return KEY_INVALID;
        }
        return null;
    }
}
