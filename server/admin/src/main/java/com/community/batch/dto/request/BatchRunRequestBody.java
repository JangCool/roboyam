package com.community.batch.dto.request;

import java.util.List;
import java.util.Map;

/**
 * 지금 실행(파라미터 지정 실행) 요청 — 본문이 없거나 칸을 안 주면 기본(파라미터 없음 · 모든 단계). 검사는 서비스가 한다(칸 경로 {@code params.<이름>} · {@code steps}).
 *
 * @param params 이름 → 값(글자). 빈 값은 보내지 않은 것으로 본다
 * @param steps  돌릴 단계 키 — 안 주면 모두. 주면 하나 이상이고 고를 수 있는 단계만
 */
public record BatchRunRequestBody(Map<String, String> params, List<String> steps) {

    public static BatchRunRequestBody empty() {
        return new BatchRunRequestBody(null, null);
    }

    @Override
    public String toString() {
        return "BatchRunRequestBody[params=" + (params == null ? null : params.keySet()) + ", steps=" + steps + "]"; // 값은 싣지 않는다(회원 ID 같은 대상)
    }
}
