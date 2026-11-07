package com.community.testsupport;

import com.community.audit.entity.QAuditLogEntity;
import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationContext;

/**
 * 시험의 감사 기록({@code audit_logs}) 확인 — QueryDSL. 조건을 이어 붙이고 {@link Criteria#count}·{@link Criteria#latest}·{@link Criteria#lastById}·{@link Criteria#list} 로 읽는다.
 *
 * <pre>{@code
 * audits.where().actor(operatorId).action("BOARD_UPDATE").latest().orElseThrow().detail();
 * audits.where().targetType("MEMBER").target(userId).action(AuditActions.MEMBER_WITHDRAW).count();
 * }</pre>
 */
public final class AuditLogQuery {

    private static final QAuditLogEntity AUDIT = QAuditLogEntity.auditLogEntity;

    /** 감사 한 줄 — 칸 이름은 표와 같다({@code detail} 은 저장된 글자 그대로) */
    public record AuditRow(Long id, Instant occurredAt, String actorType, Long actorId, String action, String result, String targetType, String targetId,
                           String ip, String requestId, String detail, String retentionClass) {

        /** 표의 칸 이름(snake_case)을 열쇠로 한 맵 — 예전 {@code queryForMap} 결과를 받던 단정이 그대로 읽는다(빈 칸은 null 값) */
        public Map<String, Object> asMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("occurred_at", occurredAt);
            row.put("actor_type", actorType);
            row.put("actor_id", actorId);
            row.put("action", action);
            row.put("result", result);
            row.put("target_type", targetType);
            row.put("target_id", targetId);
            row.put("ip", ip);
            row.put("request_id", requestId);
            row.put("detail", detail);
            row.put("retention_class", retentionClass);
            return row;
        }
    }

    private final FixtureTx tx;
    private final JPAQueryFactory query;

    public AuditLogQuery(ApplicationContext context) {
        this.tx = new FixtureTx(context);
        this.query = context.getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
    }

    public Criteria where() {
        return new Criteria();
    }

    public final class Criteria {

        private final BooleanBuilder where = new BooleanBuilder();

        private Criteria() {
        }

        public Criteria actor(long actorId) {
            where.and(AUDIT.actorId.eq(actorId));
            return this;
        }

        /** 행위 — 여럿이면 그중 하나 */
        public Criteria action(String... actions) {
            where.and(actions.length == 1 ? AUDIT.action.eq(actions[0]) : AUDIT.action.in(actions));
            return this;
        }

        public Criteria targetType(String targetType) {
            where.and(AUDIT.targetType.eq(targetType));
            return this;
        }

        /** 대상 ID — 여럿이면 그중 하나 */
        public Criteria target(String... targetIds) {
            where.and(targetIds.length == 1 ? AUDIT.targetId.eq(targetIds[0]) : AUDIT.targetId.in(targetIds));
            return this;
        }

        public Criteria target(long targetId) {
            return target(Long.toString(targetId));
        }

        /** 결과(SUCCESS·DENIED·FAILURE) */
        public Criteria result(String result) {
            where.and(AUDIT.result.eq(result));
            return this;
        }

        public Criteria retentionClass(String retentionClass) {
            where.and(AUDIT.retentionClass.eq(retentionClass));
            return this;
        }

        /** {@code detail} 이 이 글자를 품는다 */
        public Criteria detailContains(String text) {
            where.and(AUDIT.detail.contains(text));
            return this;
        }

        /** 조건에 맞는 또 하나의 묶음 — 이 조건 OR 저 조건 */
        public Criteria actorOrTarget(long actorId, String targetId) {
            where.and(AUDIT.actorId.eq(actorId).or(AUDIT.targetId.eq(targetId)));
            return this;
        }

        public int count() {
            Long count = tx.call(() -> query.select(AUDIT.count()).from(AUDIT).where(where).fetchOne());
            return count == null ? 0 : count.intValue();
        }

        /** 가장 최근 한 줄(일어난 시각 → ID 내림차순) */
        public Optional<AuditRow> latest() {
            return Optional.ofNullable(tx.call(() -> rows().orderBy(AUDIT.occurredAt.desc(), AUDIT.id.desc()).fetchFirst()));
        }

        /** ID 가 가장 큰 한 줄 */
        public Optional<AuditRow> lastById() {
            return Optional.ofNullable(tx.call(() -> rows().orderBy(AUDIT.id.desc()).fetchFirst()));
        }

        /** 맞는 줄이 꼭 하나다(없거나 둘 이상이면 실패) — 예전 {@code queryForObject}·{@code queryForMap} 자리 */
        public AuditRow single() {
            List<AuditRow> rows = tx.call(() -> rows().limit(2).fetch());
            if (rows.size() != 1) {
                throw new IllegalStateException("감사 기록이 하나가 아니다: " + rows.size());
            }
            return rows.get(0);
        }

        /** 맞는 줄 전부(ID 오름차순) */
        public List<AuditRow> list() {
            return tx.call(() -> rows().orderBy(AUDIT.id.asc()).fetch());
        }

        private JPAQuery<AuditRow> rows() {
            return query.select(Projections.constructor(AuditRow.class, AUDIT.id, AUDIT.occurredAt, AUDIT.actorType, AUDIT.actorId, AUDIT.action, AUDIT.result,
                    AUDIT.targetType, AUDIT.targetId, AUDIT.ip, AUDIT.requestId, AUDIT.detail, AUDIT.retentionClass)).from(AUDIT).where(where);
        }
    }
}
