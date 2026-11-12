package com.community.dashboard.repository.master;

import com.community.dashboard.entity.QOperatorDashboardEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 운영자 대시보드 구성 표(admin 스트림 V5 — {@code operator_dashboards}, 엔티티 {@code OperatorDashboardEntity}) 읽기·쓰기.
 * 읽기는 QueryDSL(칸을 골라 읽는다 — jsonb 는 PostgreSQL 이 정규화한 글자 그대로), 저장만 네이티브 upsert 다. 트랜잭션은 부르는 서비스의 것을 따른다.
 */
@Repository
public class DashboardLayoutStore extends IrobitRepositoryQDSL {

    private static final QOperatorDashboardEntity DASHBOARD = QOperatorDashboardEntity.operatorDashboardEntity;

    /** 저장된 한 행 — {@code gadgets} 는 JSON 배열 문자열 */
    public record Row(int version, int columns, String gadgets) {
    }

    private final EntityManager entityManager;

    public DashboardLayoutStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    public Optional<Row> find(long operatorId) {
        Tuple t = query.select(DASHBOARD.version, DASHBOARD.columns, DASHBOARD.gadgets).from(DASHBOARD).where(DASHBOARD.operatorId.eq(operatorId)).fetchOne();
        return t == null ? Optional.empty() : Optional.of(new Row(t.get(DASHBOARD.version), t.get(DASHBOARD.columns), t.get(DASHBOARD.gadgets)));
    }

    /**
     * 구성을 저장한다(없으면 넣고 있으면 바꾼다).
     *
     * <p>Native SQL 인 이유: {@code INSERT ... ON CONFLICT DO UPDATE} 는 JPQL·QueryDSL 에 대응 문법이 없다. 「찾아보고 없으면 넣는다」로 바꾸면 같은 운영자의
     * 동시 첫 저장이 {@code duplicate key} 로 깨진다. jsonb 칸은 글자를 {@code CAST(? AS jsonb)} 로 넘긴다.
     */
    public void save(long operatorId, int version, int columns, String gadgetsJson, Instant now) {
        entityManager.createNativeQuery("INSERT INTO operator_dashboards (operator_id, version, columns, gadgets, updated_at) VALUES (?1, ?2, ?3, CAST(?4 AS jsonb), ?5) "
                        + "ON CONFLICT (operator_id) DO UPDATE SET version = EXCLUDED.version, columns = EXCLUDED.columns, gadgets = EXCLUDED.gadgets, "
                        + "updated_at = EXCLUDED.updated_at")
                .setParameter(1, operatorId)
                .setParameter(2, version)
                .setParameter(3, columns)
                .setParameter(4, gadgetsJson)
                .setParameter(5, now.truncatedTo(ChronoUnit.MICROS)) // DB 는 마이크로초 — 반올림되지 않게 먼저 자른다
                .executeUpdate();
    }
}
