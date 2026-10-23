package com.community.batch.core;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 검사를 마친 실행 파라미터(이름 → 글자 값, 이름순). 실행 줄({@code batch_runs.params})에 JSON 으로 남고 「실패 지점부터 다시」·「실패 건만 다시」는 원래 실행의 값을 그대로 쓴다.
 * 값은 {@link BatchParameterValidator} 가 꼴을 확인했으므로 꺼낼 때 다시 실패하지 않는다.
 */
public final class BatchParameters {

    private static final BatchParameters EMPTY = new BatchParameters(Map.of());

    private final Map<String, String> values;

    private BatchParameters(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public static BatchParameters empty() {
        return EMPTY;
    }

    /** 이미 검사한 값(또는 저장된 값)으로 만든다 */
    public static BatchParameters of(Map<String, String> values) {
        return values == null || values.isEmpty() ? EMPTY : new BatchParameters(values);
    }

    public Optional<String> string(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public Optional<LocalDate> date(String name) {
        return string(name).map(LocalDate::parse);
    }

    public Optional<Long> longValue(String name) {
        return string(name).map(Long::parseLong);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** 이름순의 바꿀 수 없는 맵 */
    public Map<String, String> asMap() {
        return values;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BatchParameters parameters && values.equals(parameters.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "BatchParameters" + values.keySet(); // 값은 싣지 않는다(회원 ID 같은 대상이 들어 있을 수 있다)
    }
}
