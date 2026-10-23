package com.community.batch.core;

import com.community.zconfiguration.message.MessageRef;

/**
 * 배치 한 번의 결과 요약. 상태(OK·SKIP)와 건수는 실행기가 단계가 센 값으로 정한다 — 주 단계의 처리 건수와 실패 건수가 모두 0 이면 「건너뜀」(SKIP), 아니면 「성공」(OK).
 * 실패는 예외로 알린다.
 *
 * @param message 결과 요약(문구 키 + 인자) — 번들에 ko·en 으로 둔다. 인자에는 건수 같은 규칙 값만 싣고 사용자가 입력한 값은 싣지 않는다.
 *                할 일이 없었을 때(건수 0)는 그에 맞는 문구({@code ….none})를 준다
 */
public record BatchResult(MessageRef message) {

    public BatchResult {
        if (message == null) {
            throw new IllegalArgumentException("결과 요약 문구가 없다");
        }
    }

    public static BatchResult of(MessageRef message) {
        return new BatchResult(message);
    }
}
