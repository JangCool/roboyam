package com.community.batch.core;

import java.util.regex.Pattern;

/**
 * 작업의 단계 하나(작업이 정한 차례대로). 한 단계짜리 작업도 단계가 하나 있다({@link #main()}).
 *
 * @param name       키 — 소문자·숫자·하이픈. 요청의 {@code steps} 값이고 이름 문구는 번들 {@code batch.job.<배치 키>.step.<키>}
 * @param selectable 이 단계만 골라 돌릴 수 있나. false 인 단계는 고를 수 없고 단계를 골라 실행해도 <b>늘 돈다</b>(확정 규칙 2)
 */
public record BatchStepDefinition(String name, boolean selectable) {

    /** 기본 단계 이름 */
    public static final String MAIN = "main";

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,39}");

    public BatchStepDefinition {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("단계 이름 규칙(소문자·숫자·하이픈 40자)에 맞지 않는다: " + name);
        }
    }

    /** 한 단계짜리 작업의 단계 — 고를 수 없다 */
    public static BatchStepDefinition main() {
        return new BatchStepDefinition(MAIN, false);
    }
}
