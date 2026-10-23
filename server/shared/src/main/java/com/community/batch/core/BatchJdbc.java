package com.community.batch.core;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배치 core 의 JDBC 기록기들이 같이 쓰는 작은 도구 — 시각 변환과 트랜잭션 틀.
 *
 * <p>core 가 JPA 가 아니라 JDBC({@code JdbcTemplate} + 앱의 트랜잭션 관리자)로 기록하는 이유: ① 실행 기록·체크포인트는 일과 <b>다른 트랜잭션</b>(시작 줄은 바로 보이고,
 * 일이 롤백돼도 「실패했다」는 남는다) 또는 <b>같은 트랜잭션</b>(덩어리 저장 확정과 단계 건수·체크포인트를 한 번에)을 문장마다 골라야 하고, ② {@code FOR UPDATE SKIP LOCKED}·
 * {@code ON CONFLICT}·부분 유일 색인 위반·어드바이저리 잠금처럼 PostgreSQL 전용 문법을 쓰며, ③ 나중에 배치를 별도 서버({@code server/batch})로 뗄 때 JPA 구성 없이 데이터소스와
 * 트랜잭션 관리자만으로 돌게 하려는 것이다(시험도 같은 이유로 실제 PostgreSQL 데이터소스만으로 돈다). 앱에서는 JPA 트랜잭션 관리자가 같은 연결을 JDBC 에 내주므로
 * 작업의 JPA 일과 이 기록이 한 트랜잭션에 묶인다. 화면의 조회는 JPA 엔티티·QueryDSL 로 한다.
 */
final class BatchJdbc {

    private BatchJdbc() {
    }

    /** DB 시각 정밀도(마이크로초)에 맞춘 지금 */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** 늘 새 트랜잭션 — 부르는 쪽 트랜잭션과 따로 확정된다 */
    static TransactionTemplate requiresNew(PlatformTransactionManager transactions) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /** 읽기 전용 새 트랜잭션 — 덩어리 읽기(JPA 조회도 트랜잭션 안에서 하게) */
    static TransactionTemplate readOnly(PlatformTransactionManager transactions) {
        TransactionTemplate template = requiresNew(transactions);
        template.setReadOnly(true);
        return template;
    }
}
