package com.community.batch.core;

import com.community.batch.entity.BatchSource;
import java.util.regex.Pattern;

/**
 * 배치 하나의 정체 — 키와 어디서 도는가. 운영 배치는 모두 {@link BatchCatalog} 의 상수이고, 실행기·요청 처리기는 이 인터페이스만 본다
 * (그래서 시험은 카탈로그를 고치지 않고 자기 키의 작업으로 실행기를 돌릴 수 있다 — {@link BatchRegistry#forJobs}).
 */
public interface BatchDefinition {

    /** 배치 키 — 소문자·숫자·하이픈. 한 번 정하면 바꾸지 않는다(설정 행·실행 로그·요청·화면이 이 값으로 가리킨다) */
    Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{0,39}");

    String key();

    BatchSource source();

    default boolean isJava() {
        return source() == BatchSource.JAVA;
    }

    /**
     * DB 배치가 부르는 저장 프로시저 이름 — {@code batch_} + 키의 하이픈을 밑줄로 바꾼 것(예: {@code audit-log-retention} → {@code batch_audit_log_retention}).
     * 키 규칙을 지키므로 SQL 식별자로 그대로 쓸 수 있다. Java 배치에는 없다.
     */
    default String procedureName() {
        if (source() != BatchSource.POSTGRESQL) {
            throw new IllegalStateException("DB 배치가 아니다: " + key());
        }
        if (!KEY.matcher(key()).matches()) {
            throw new IllegalStateException("배치 키 규칙에 맞지 않는다: " + key());
        }
        return "batch_" + key().replace('-', '_');
    }
}
