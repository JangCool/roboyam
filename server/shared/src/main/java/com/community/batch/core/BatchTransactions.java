package com.community.batch.core;

import com.community.zconfiguration.jpa.UniqueViolations;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배치 core 의 기록기들(실행 기록·실패 건·요청 표)이 같이 쓰는 작은 도구 — 시각과 트랜잭션 틀.
 *
 * <p>기록은 JPA·QueryDSL 로 하고 <b>문장마다 트랜잭션을 고른다</b>(세션 정리 결정 2026-11-07 — commerce-foundation-contract.md 「0-2 구현 결과」): 실행 기록·체크포인트·실패 건은 일과
 * <b>다른 트랜잭션</b>({@link #requiresNew} — 시작 줄은 바로 보이고, 일이 롤백돼도 「실패했다」는 남는다) 또는 <b>부르는 쪽 트랜잭션</b>({@link #required} — 덩어리 저장 확정과 단계
 * 건수·체크포인트를 한 번에, 감사 기록과 요청 줄을 한 번에. 트랜잭션 밖에서 부르면 그 문장만의 트랜잭션 — JDBC 자동 확정 때와 같다). 앱의 트랜잭션 관리자는 JPA 라 작업의 JDBC 일도
 * 같은 연결을 쓴다. PostgreSQL 전용 문법이 핵심인 문장만 native 로 남긴다: 「없을 때만 넣기」·하트비트({@code ON CONFLICT}), DB 함수 {@code batch_param()}.
 * {@code SKIP LOCKED} 는 엔티티 잠금({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE}) + 힌트 {@code jakarta.persistence.lock.timeout = -2} 다.
 */
final class BatchTransactions {

    /** 잠긴 줄은 기다리지 않고 건너뛴다({@code SKIP LOCKED}) */
    static final String LOCK_TIMEOUT = "jakarta.persistence.lock.timeout";
    static final int SKIP_LOCKED = -2;

    private BatchTransactions() {
    }

    /** DB 시각 정밀도(마이크로초)에 맞춘 지금 */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** native 인자 — {@code timestamptz} 로 보낸다 */
    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** 늘 새 트랜잭션 — 부르는 쪽 트랜잭션과 따로 확정된다 */
    static TransactionTemplate requiresNew(PlatformTransactionManager transactions) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /** 부르는 쪽 트랜잭션에 참여한다(없으면 그 문장만의 새 트랜잭션) */
    static TransactionTemplate required(PlatformTransactionManager transactions) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }

    /** 읽기 전용 새 트랜잭션 — 덩어리 읽기(JPA 조회도 트랜잭션 안에서 하게) */
    static TransactionTemplate readOnly(PlatformTransactionManager transactions) {
        TransactionTemplate template = requiresNew(transactions);
        template.setReadOnly(true);
        return template;
    }

    /** 유일 색인 위반({@code 23505})인가 — 원인을 따라가 본다(다른 무결성 위반은 아니다) */
    static boolean uniqueViolation(Throwable error) {
        return UniqueViolations.is(error);
    }
}
