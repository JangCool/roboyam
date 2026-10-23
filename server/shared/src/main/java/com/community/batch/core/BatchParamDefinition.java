package com.community.batch.core;

import java.util.regex.Pattern;

/**
 * 작업이 받을 수 있는 실행 파라미터 하나(예: 대사 기준일, 특정 회원). 형식·필수 검사는 공통({@link BatchParameterValidator})이고 작업별 추가 검사는 {@link BatchJob#validate}.
 *
 * @param name     키 — 영문 소문자로 시작하는 영문·숫자(낙타 표기). 요청의 {@code params} 이름이자 오류 칸 {@code fields.params.<이름>}, 이름 문구는 번들
 *                 {@code batch.job.<배치 키>.param.<이름>}(안내 {@code .help})
 * @param type     값 꼴
 * @param required 꼭 있어야 하나
 */
public record BatchParamDefinition(String name, BatchParamType type, boolean required) {

    private static final Pattern NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,39}");

    public BatchParamDefinition {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("파라미터 이름 규칙(영문 소문자로 시작하는 영문·숫자 40자)에 맞지 않는다: " + name);
        }
        if (type == null) {
            throw new IllegalArgumentException("파라미터 꼴이 없다: " + name);
        }
    }
}
