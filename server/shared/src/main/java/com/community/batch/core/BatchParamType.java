package com.community.batch.core;

/** 실행 파라미터의 값 꼴 — 값은 모두 글자로 오가고 검사는 {@link BatchParameterValidator} 가 한다 */
public enum BatchParamType {
    /** 달력 날짜 {@code YYYY-MM-DD} */
    DATE,
    /** 1 이상의 정수(ID·건수) */
    LONG,
    /** 글({@value BatchParameterValidator#MAX_STRING}자까지) */
    STRING
}
