package com.community.batch.core;

import java.util.List;

/**
 * 덩어리 하나를 처리한다 — 덩어리째(한 번의 UPDATE·INSERT 묶음) 처리하는 것이 평소 길이다. 실행기가 연 트랜잭션 안에서 불리고, 같은 트랜잭션에서 단계 건수·체크포인트가 한 번 고쳐진다.
 * 예외를 던지면 그 트랜잭션을 되돌리고 같은 덩어리를 한 건씩(건마다 트랜잭션) 다시 불러 실패한 건만 찾아낸다 — 그래서 한 건짜리 목록으로도 같은 일을 해야 한다.
 * 실패 건의 오류 코드를 정하려면 {@link BatchItemException} 을 던진다.
 *
 * @param <T> 대상 하나
 */
@FunctionalInterface
public interface ChunkProcessor<T> {

    /**
     * @return 실제로 처리한 건수(나머지는 「건너뜀」 — 예: 고른 뒤 다른 곳이 이미 바꿔 대상이 아니게 된 건). 0 ~ {@code items.size()}
     */
    long process(List<T> items) throws Exception;
}
