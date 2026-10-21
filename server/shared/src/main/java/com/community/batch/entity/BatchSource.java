package com.community.batch.entity;

/** 배치가 어디서 도는가. 화면에 나가는 값 이름은 {@code JAVA}·{@code POSTGRESQL} 이다 */
public enum BatchSource {
    /** 애플리케이션 스케줄러(관리자 서버) */
    JAVA,
    /** DB — pg_cron 이 저장 프로시저를 부른다(「지금 실행」은 앱이 같은 프로시저를 CALL 한다) */
    POSTGRESQL
}
