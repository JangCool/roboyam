package com.community.batch.job;

import com.community.batch.catalog.BatchCatalog;

/**
 * 배치 하나의 실제 일. 구현은 스프링 빈이고 {@link #definition()} 으로 카탈로그의 어느 줄인지 밝힌다(카탈로그 한 줄에 구현 하나).
 *
 * <p>실행기({@code BatchRunner})가 잠금·로그·실패 처리를 맡으므로 구현은 일만 한다 — 트랜잭션은 구현이 부르는 서비스가 연다(여기서 감싸지 않는다).
 * 실패는 예외를 던져 알린다(실행 로그에 사유가 남는다). 예외 문구에 비밀번호·토큰·개인정보를 싣지 않는다.
 */
public interface BatchJob {

    /** 카탈로그의 어느 배치인가 */
    BatchCatalog definition();

    /** 일을 하고 결과(처리 건수·요약)를 돌려준다 */
    BatchResult execute() throws Exception;
}
