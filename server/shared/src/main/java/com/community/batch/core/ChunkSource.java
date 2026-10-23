package com.community.batch.core;

import java.util.List;

/**
 * 덩어리 처리 도우미({@link BatchStep#chunks})가 읽는 쪽 — 작업이 준다. 대상은 <b>가벼운 값</b>(ID·레코드)이 좋다: 읽기는 처리 트랜잭션 밖에서 하고, 처리 쪽이 필요하면
 * 잠가 다시 읽는다(덩어리가 실패하면 같은 값으로 한 건씩 다시 처리하므로).
 *
 * @param <T> 대상 하나
 */
public interface ChunkSource<T> {

    /** 대상 종류 코드(대문자·숫자·밑줄, 예 {@code POINT_LOT}) — 실패 건에 남고 이름 문구는 번들 {@code batch.target.<코드>} */
    String targetType();

    /**
     * 정렬 키가 {@code afterKey} 보다 큰 대상을 정렬 키 오름차순으로 {@code size} 개까지. {@code afterKey} 가 null 이면 처음부터.
     * 「그날 몫」이 아니라 「아직 처리 안 한 것 전부」를 고른다(15-3 규칙 ②).
     */
    List<T> next(String afterKey, int size);

    /** 「실패 건만 다시」 — 대상 ID 로 다시 읽는다. 없어진 대상은 결과에서 빠진다(실행기가 건너뜀으로 세고 실패 줄을 해결 표시한다) */
    List<T> byIds(List<String> targetIds);

    /** 정렬 키(체크포인트로 남는다 — 200자까지). 키 순서는 {@link #next} 의 순서와 같아야 한다 */
    String keyOf(T item);

    /** 대상 ID(실패 건에 남는다 — 100자까지, 개인정보를 넣지 않는다) */
    String targetIdOf(T item);
}
