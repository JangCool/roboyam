package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonReviewEntity;
import com.community.emoticon.entity.EmoticonReviewKind;
import com.community.emoticon.entity.EmoticonReviewStatus;
import com.community.emoticon.entity.QEmoticonReviewEntity;
import com.community.operator.entity.QOperatorEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 심사 건({@code emoticon_reviews}, V142) 읽기·쓰기. QueryDSL 이다({@link EmoticonStoreSupport}) — 심사 건 행 잠금은 심사 건 표만 고르는
 * {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE} — 심사 건을 FK 로 가리키는 가격 기록은 그 건을 먼저 잠근 승인이 넣는다).
 * Native SQL 로 남긴 것과 이유: 작가 단위 줄 세우기({@code pg_advisory_xact_lock} — 잠글 행이 없는 「작가」 단위), 작가 탈퇴의 대기 건 닫기(하위 질의 {@code FOR UPDATE} 로
 * ID 차례로 잠그고 바꾼 줄을 {@code RETURNING} 으로 — 닫은 건과 알린 건이 같은 문장이어야 한다).
 */
@Repository
public class EmoticonReviewStore extends EmoticonStoreSupport {

    /** 심사 건 한 줄(처리한 운영자 이름 포함 — 없으면 null) */
    public record ReviewRow(long id, long packId, long revisionId, Long previousRevisionId, long creatorId, EmoticonReviewKind kind, EmoticonReviewStatus status,
                            Instant submittedAt, Instant agreeOwnershipAt, Instant agreeLicenseAt, Instant agreePolicyAt, String consentVersion, Instant decidedAt,
                            Long decidedBy, String decidedByName, String reason, Integer approvedPrice, String systemCause) {
    }

    /** 작가 탈퇴로 닫은 대기 건 한 줄(V147 — 후속 결정 6) */
    public record ClosedRow(long id, long packId, EmoticonReviewKind kind) {
    }

    /** 시스템이 닫은 까닭 — 작가 탈퇴(V147 {@code system_cause}) */
    public static final String CAUSE_CREATOR_WITHDRAWN = "CREATOR_WITHDRAWN";

    private static final QEmoticonReviewEntity R = QEmoticonReviewEntity.emoticonReviewEntity;
    private static final QEmoticonReviewEntity R2 = new QEmoticonReviewEntity("r2");
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    public EmoticonReviewStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    public void insert(long id, long packId, long revisionId, Long previousRevisionId, long creatorId, EmoticonReviewKind kind, Instant now, String consentVersion) {
        insert(EmoticonReviewEntity.pending(id, packId, revisionId, previousRevisionId, creatorId, kind, now, consentVersion));
    }

    private JPAQuery<Tuple> rows() {
        return query.select(R.id, R.packId, R.revisionId, R.previousRevisionId, R.creatorId, R.kind, R.status, R.submittedAt, R.agreeOwnershipAt, R.agreeLicenseAt,
                R.agreePolicyAt, R.consentVersion, R.decidedAt, R.decidedBy, O.operatorName, R.reason, R.approvedPrice, R.systemCause).from(R).leftJoin(O)
                .on(O.id.eq(R.decidedBy));
    }

    public Optional<ReviewRow> find(long id) {
        return Optional.ofNullable(rows().where(R.id.eq(id)).fetchFirst()).map(EmoticonReviewStore::rowOf);
    }

    /** 처리하기 전에 — 심사 건 행을 잠근다(같은 건을 둘이 동시에 처리하지 못하게). 잠금은 심사 건 표만 고르는 문장, 칸은 그 뒤에 읽는다 */
    public Optional<ReviewRow> lock(long id) {
        if (query.select(R.id).from(R).where(R.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() == null) {
            return Optional.empty();
        }
        return find(id);
    }

    /** 처리 — 대기 건만(바뀐 줄 수) */
    public int decide(long id, EmoticonReviewStatus status, long operatorId, String reason, Integer approvedPrice, Instant now) {
        return (int) query.update(R).set(R.status, status).set(R.decidedAt, now).set(R.decidedBy, operatorId).set(R.reason, reason).set(R.approvedPrice, approvedPrice)
                .where(R.id.eq(id), R.status.eq(EmoticonReviewStatus.PENDING)).execute();
    }

    /**
     * 작가 탈퇴 — 그 작가의 대기 건을 모두 반려로 닫는다(처리한 운영자 없음 · {@code system_cause = CREATOR_WITHDRAWN} · 사유 {@code reason}). 심사 건 행이 먼저 잠긴다(승인·반려와 같은
     * 차례 — 심사 건 → 팩). 닫은 건.
     *
     * <p>Native SQL 인 이유: 하위 질의 {@code ORDER BY id FOR UPDATE} 로 ID 차례로 잠그고, 바꾼 줄을 {@code RETURNING} 으로 돌려받는다 — 닫은 건과 부르는 쪽이 알리는 건이 같은 문장의 결과여야 한다.
     */
    public List<ClosedRow> closeForCreatorWithdrawal(long creatorId, String reason, Instant now) {
        return jdbc.query("UPDATE emoticon_reviews SET status = 'REJECTED', decided_at = ?, decided_by = NULL, reason = ?, approved_price = NULL, system_cause = ? "
                        + "WHERE id IN (SELECT id FROM emoticon_reviews WHERE creator_id = ? AND status = 'PENDING' ORDER BY id FOR UPDATE) AND status = 'PENDING' "
                        + "RETURNING id, pack_id, kind", (rs, i) -> new ClosedRow(rs.getLong(1), rs.getLong(2), EmoticonReviewKind.valueOf(rs.getString(3))),
                Timestamp.from(now), reason, CAUSE_CREATOR_WITHDRAWN, creatorId);
    }

    /** 그 팩의 대기 건 */
    public Optional<ReviewRow> pendingOf(long packId) {
        return Optional.ofNullable(rows().where(R.packId.eq(packId), R.status.eq(EmoticonReviewStatus.PENDING)).fetchFirst()).map(EmoticonReviewStore::rowOf);
    }

    /**
     * 팩마다 마지막 심사 건 — (신청 시각, ID) 가 가장 큰 건: 더 늦은 건이 없는 건(예전 {@code DISTINCT ON … ORDER BY submitted_at DESC, id DESC} 와 같은 건, 팩 ID 차례).
     */
    public Map<Long, ReviewRow> latestOf(Collection<Long> packIds) {
        Map<Long, ReviewRow> out = new LinkedHashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        for (Tuple t : rows().where(R.packId.in(packIds), JPAExpressions.selectOne().from(R2)
                .where(R2.packId.eq(R.packId), R2.submittedAt.gt(R.submittedAt).or(R2.submittedAt.eq(R.submittedAt).and(R2.id.gt(R.id)))).notExists())
                .orderBy(R.packId.asc()).fetch()) {
            ReviewRow row = rowOf(t);
            out.put(row.packId(), row);
        }
        return out;
    }

    /** 작가의 대기 건 수(처음 신청 + 새 판) */
    public int pendingCount(long creatorId) {
        return (int) n(query.select(R.count()).from(R).where(R.creatorId.eq(creatorId), R.status.eq(EmoticonReviewStatus.PENDING)).fetchFirst());
    }

    /**
     * 같은 작가의 심사 요청을 줄 세운다 — 대기 건 수(상한)를 세고 새 건을 넣는 사이에 다른 요청이 끼지 못하게(트랜잭션 끝까지).
     * Native SQL 인 이유: {@code pg_advisory_xact_lock} — 표 행이 없는 「작가」 단위라서.
     */
    public void lockCreator(long creatorId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('emoticon-review:' || ?::text, 0))", rs -> {
        }, creatorId);
    }

    /** 목록 — 조건은 부르는 쪽이 만든다(심사 대기·새 판은 오래 기다린 것이 위, 반려·전체는 최근이 위) */
    public List<ReviewRow> list(EmoticonReviewStatus status, EmoticonReviewKind kind, boolean oldestFirst) {
        List<BooleanExpression> where = new ArrayList<>();
        if (status != null) {
            where.add(R.status.eq(status));
        }
        if (kind != null) {
            where.add(R.kind.eq(kind));
        }
        JPAQuery<Tuple> q = rows().where(where.toArray(BooleanExpression[]::new));
        q = oldestFirst ? q.orderBy(R.submittedAt.asc(), R.id.asc()) : q.orderBy(R.submittedAt.desc(), R.id.desc());
        return q.limit(500).fetch().stream().map(EmoticonReviewStore::rowOf).collect(Collectors.toList());
    }

    /** 탭 숫자 — 처음 신청 대기 · 새 판 대기 · 반려(상태 · 종류로 세어 탭으로 모은다) */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("PENDING", 0);
        out.put("UPDATE", 0);
        out.put("REJECTED", 0);
        for (Tuple t : query.select(R.status, R.kind, R.count()).from(R).where(R.status.in(EmoticonReviewStatus.PENDING, EmoticonReviewStatus.REJECTED))
                .groupBy(R.status, R.kind).fetch()) {
            String tab = t.get(R.status) == EmoticonReviewStatus.REJECTED ? "REJECTED" : t.get(R.kind) == EmoticonReviewKind.UPDATE ? "UPDATE" : "PENDING";
            out.merge(tab, (int) n(t.get(R.count())), Integer::sum);
        }
        return out;
    }

    private static ReviewRow rowOf(Tuple t) {
        return new ReviewRow(t.get(R.id), t.get(R.packId), t.get(R.revisionId), t.get(R.previousRevisionId), t.get(R.creatorId), t.get(R.kind), t.get(R.status),
                t.get(R.submittedAt), t.get(R.agreeOwnershipAt), t.get(R.agreeLicenseAt), t.get(R.agreePolicyAt), t.get(R.consentVersion), t.get(R.decidedAt),
                t.get(R.decidedBy), t.get(O.operatorName), t.get(R.reason), t.get(R.approvedPrice), t.get(R.systemCause));
    }
}
