package com.community.batch.job;

import com.community.zconfiguration.message.MessageRef;

/**
 * 배치 한 번의 결과. 처리 건수가 0 이면 실행 로그는 「건너뜀」(SKIP)이고 1 이상이면 「성공」(OK)이다 — 실패는 예외로 알린다.
 *
 * @param affected 처리한 건수(0 이상)
 * @param message  결과 요약(문구 키 + 인자) — 번들에 ko·en 으로 둔다. 인자에는 건수 같은 규칙 값만 싣고 사용자가 입력한 값은 싣지 않는다
 */
public record BatchResult(long affected, MessageRef message) {

    public BatchResult {
        if (affected < 0) {
            throw new IllegalArgumentException("처리 건수는 0 이상이다");
        }
        if (message == null) {
            throw new IllegalArgumentException("결과 요약 문구가 없다");
        }
    }

    public static BatchResult of(long affected, MessageRef message) {
        return new BatchResult(affected, message);
    }
}
