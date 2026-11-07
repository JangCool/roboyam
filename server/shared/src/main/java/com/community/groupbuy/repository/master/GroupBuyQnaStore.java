package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyAnswerEntity;
import com.community.groupbuy.entity.GroupBuyAnswerRevisionEntity;
import com.community.groupbuy.entity.GroupBuyQuestionEntity;
import com.community.groupbuy.entity.QGroupBuyAnswerEntity;
import com.community.groupbuy.entity.QGroupBuyAnswerRevisionEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyQuestionEntity;
import com.community.groupbuy.entity.QGroupBuyUgcReportEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 공구 Q&A({@code group_buy_questions}·{@code group_buy_answers}·{@code group_buy_answer_revisions}, 2b-6) — {@code GroupBuyQnaService} 안에 있던 SQL 을 옮겼다.
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). Native SQL 로 남긴 것과 이유: 같은 회원의 질문 쓰기를 줄 세우는 {@link #lockWriter}
 * ({@code pg_advisory_xact_lock} — DB 함수). 행 잠금은 {@link RowLocks}({@code FOR UPDATE} — 전환 전 세기) — 답변·숨김끼리 줄 세운다.
 */
@Repository
public class GroupBuyQnaStore extends GroupBuyStoreSupport {

    private static final QGroupBuyQuestionEntity Q = QGroupBuyQuestionEntity.groupBuyQuestionEntity;
    private static final QGroupBuyAnswerEntity A = QGroupBuyAnswerEntity.groupBuyAnswerEntity;
    private static final QGroupBuyAnswerRevisionEntity REV = QGroupBuyAnswerRevisionEntity.groupBuyAnswerRevisionEntity;
    private static final QGroupBuyUgcReportEntity R = QGroupBuyUgcReportEntity.groupBuyUgcReportEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QOperatorEntity O2 = new QOperatorEntity("updatedByOperator");

    /** 회원이 보는 질문 한 줄 — {@code nickname} 은 탈퇴·삭제한 회원이면 null */
    public record QuestionRow(long id, long userId, String nickname, String body, boolean secret, boolean forcedSecret, String status, boolean hidden, Instant createdAt,
                              Instant updatedAt, String answer, Instant answeredAt, boolean answerEdited) {
    }

    /** 운영자가 보는 질문 한 줄 — {@code title} 공구 제목(비었으면 상품 이름), {@code reportCount} 열린 신고 수 */
    public record AdminQuestionRow(long id, long groupBuyId, String title, int roundNo, long userId, String nickname, boolean gone, String body, boolean secret,
                                   boolean forcedSecret, String status, boolean hidden, boolean deleted, int reportCount, Instant createdAt, Instant answeredAt) {
    }

    /**
     * 관리자 목록 조건 — {@code tab} OPEN·ANSWERED·HIDDEN·ALL, {@code like} 제목·상품 이름·닉네임 ILIKE 말(없으면 null), {@code number} 회원·공구 번호(숫자 찾기 말일 때),
     * {@code overdueBefore} 답변 기한 지남만(이 시각 전에 쓴 답변 대기 — null 이면 거르지 않는다)
     */
    public record AdminFilter(String tab, String like, Long number, Instant overdueBefore) {
    }

    /** 관리자 상세의 나머지 칸 */
    public record QuestionExtra(Instant updatedAt, Instant deletedAt, LocalDate retainUntil, String hiddenReason, Instant hiddenAt, Long hiddenBy, String hiddenByName) {
    }

    /** 답변 — 고친 적이 없으면 {@code updatedBy}·{@code updatedAt} 이 null */
    public record AnswerDetail(String body, Instant answeredAt, Long answeredBy, String answeredByName, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    public record RevisionRow(long id, String body, Instant writtenAt, Long writtenBy, String writtenByName, Instant replacedAt, Long replacedBy, String replacedByName) {
    }

    /** 잠근 질문 */
    public record LockedQuestion(long userId, long groupBuyId, boolean deleted) {
    }

    /** 지금 답변(이력으로 옮길 값) — {@code at}·{@code by} 는 마지막으로 고친(없으면 처음 쓴) 시각·사람 */
    public record CurrentAnswer(String body, Instant at, long by) {
    }

    public GroupBuyQnaStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> memberSelect() {
        return query.select(Q.id, Q.userId, PR.nickname, Q.body, Q.secret, Q.forcedSecret, Q.status, Q.hiddenAt, Q.createdAt, Q.updatedAt, A.body, A.answeredAt, A.updatedAt,
                        U.id, U.status, U.deletedAt)
                .from(Q).leftJoin(A).on(A.questionId.eq(Q.id)).leftJoin(U).on(U.id.eq(Q.userId)).leftJoin(PR).on(PR.userId.eq(Q.userId));
    }

    private static QuestionRow memberRow(Tuple t) {
        boolean gone = GroupBuyRowNames.gone(t.get(U.id), t.get(U.status), t.get(U.deletedAt));
        return new QuestionRow(t.get(Q.id), t.get(Q.userId), gone ? null : t.get(PR.nickname), t.get(Q.body), Boolean.TRUE.equals(t.get(Q.secret)),
                Boolean.TRUE.equals(t.get(Q.forcedSecret)), t.get(Q.status), t.get(Q.hiddenAt) != null, t.get(Q.createdAt), t.get(Q.updatedAt), t.get(A.body),
                t.get(A.answeredAt), t.get(A.updatedAt) != null);
    }

    private static BooleanExpression memberWhere(long groupBuyId, Long onlyUser) {
        BooleanExpression where = Q.groupBuyId.eq(groupBuyId).and(Q.deletedAt.isNull());
        return onlyUser == null ? where : where.and(Q.userId.eq(onlyUser));
    }

    /** 회원 목록의 수 — 지운 질문 빼고({@code onlyUser} 가 있으면 그 회원 것만) */
    public long memberCount(long groupBuyId, Long onlyUser) {
        return n(query.select(Q.count()).from(Q).where(memberWhere(groupBuyId, onlyUser)).fetchOne());
    }

    /** 회원 목록 한 쪽 — 새 글이 위 */
    public List<QuestionRow> memberPage(long groupBuyId, Long onlyUser, long offset, int limit) {
        return memberSelect().where(memberWhere(groupBuyId, onlyUser)).orderBy(Q.createdAt.desc(), Q.id.desc()).offset(offset).limit(limit).fetch().stream()
                .map(GroupBuyQnaStore::memberRow).toList();
    }

    public Optional<QuestionRow> memberRow(long id) {
        return Optional.ofNullable(memberSelect().where(Q.id.eq(id)).fetchFirst()).map(GroupBuyQnaStore::memberRow);
    }

    /** 작성자 본인의 지우지 않은 질문 */
    public Optional<QuestionRow> ownRow(long id, long userId) {
        return Optional.ofNullable(memberSelect().where(Q.id.eq(id), Q.userId.eq(userId), Q.deletedAt.isNull()).fetchFirst()).map(GroupBuyQnaStore::memberRow);
    }

    /** 바로 앞 차수(공개된 것) — (공구 ID, 차수) */
    public Optional<long[]> previousRound(long roundRootId, int roundNo) {
        Tuple t = query.select(G.id, G.roundNo).from(G).where(G.roundRootId.eq(roundRootId), G.roundNo.eq(roundNo), G.publishedAt.isNotNull()).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new long[] {t.get(G.id), t.get(G.roundNo)});
    }

    /** 같은 회원의 질문 쓰기를 줄 세운다(간격 검사) — 트랜잭션 범위. Native SQL 인 이유: DB 함수 {@code pg_advisory_xact_lock} */
    public void lockWriter(long userId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "gbq:" + userId);
    }

    /** 그 회원이 마지막으로 질문을 쓴 시각(지운 것 포함, 없으면 null) */
    public Instant lastAskedAt(long userId) {
        return query.select(Q.createdAt.max()).from(Q).where(Q.userId.eq(userId)).fetchOne();
    }

    public void insertQuestion(long id, long groupBuyId, long userId, String body, boolean secret, boolean forcedSecret, Instant now, LocalDate retainUntil) {
        insert(GroupBuyQuestionEntity.asked(id, groupBuyId, userId, body, secret, forcedSecret, now, retainUntil));
    }

    public void updateBody(long id, String body, boolean secret, boolean forcedSecret, Instant now) {
        query.update(Q).set(Q.body, body).set(Q.secret, secret).set(Q.forcedSecret, forcedSecret).set(Q.updatedAt, now).where(Q.id.eq(id)).execute();
    }

    public void makeSecret(long id) {
        query.update(Q).set(Q.secret, true).where(Q.id.eq(id)).execute();
    }

    public void softDelete(long id, Instant now) {
        query.update(Q).set(Q.deletedAt, now).where(Q.id.eq(id), Q.deletedAt.isNull()).execute();
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> adminSelect() {
        return query.select(Q.id, Q.groupBuyId, G.title, P.name, G.roundNo, Q.userId, PR.nickname, U.id, U.status, U.deletedAt, Q.body, Q.secret, Q.forcedSecret, Q.status,
                        Q.hiddenAt, Q.deletedAt, openReports(), Q.createdAt, A.answeredAt)
                .from(Q).join(G).on(G.id.eq(Q.groupBuyId)).join(P).on(P.id.eq(G.productId)).leftJoin(U).on(U.id.eq(Q.userId)).leftJoin(PR).on(PR.userId.eq(Q.userId))
                .leftJoin(A).on(A.questionId.eq(Q.id));
    }

    private static com.querydsl.core.types.Expression<Long> openReports() {
        return JPAExpressions.select(R.count()).from(R).where(R.targetType.eq("GROUP_BUY_QUESTION"), R.targetId.eq(Q.id), R.status.eq("OPEN"));
    }

    private static AdminQuestionRow adminRow(Tuple t) {
        Long reports = t.get(16, Long.class);
        return new AdminQuestionRow(t.get(Q.id), t.get(Q.groupBuyId), GroupBuyRowNames.title(t.get(G.title), t.get(P.name)), n(t.get(G.roundNo)), t.get(Q.userId),
                t.get(PR.nickname), GroupBuyRowNames.gone(t.get(U.id), t.get(U.status), t.get(U.deletedAt)), t.get(Q.body), Boolean.TRUE.equals(t.get(Q.secret)),
                Boolean.TRUE.equals(t.get(Q.forcedSecret)), t.get(Q.status), t.get(Q.hiddenAt) != null, t.get(Q.deletedAt) != null, reports == null ? 0 : reports.intValue(),
                t.get(Q.createdAt), t.get(A.answeredAt));
    }

    /** 답변 대기(숨기지·지우지 않음) — 대시보드·기한 지남 수와 같은 조건(부분 인덱스 {@code ix_gbq_open}) */
    private static BooleanExpression open() {
        return Q.status.eq("OPEN").and(Q.hiddenAt.isNull()).and(Q.deletedAt.isNull());
    }

    private static BooleanExpression adminWhere(AdminFilter f) {
        BooleanExpression where = switch (f.tab()) {
            case "OPEN", "ANSWERED" -> Q.status.eq(f.tab()).and(Q.hiddenAt.isNull()).and(Q.deletedAt.isNull());
            case "HIDDEN" -> Q.hiddenAt.isNotNull().and(Q.deletedAt.isNull());
            default -> null; // ALL
        };
        if (f.like() != null) {
            BooleanExpression match = GroupBuyRowNames.ilike(G.title, f.like()).or(GroupBuyRowNames.ilike(P.name, f.like())).or(GroupBuyRowNames.ilike(PR.nickname, f.like()));
            if (f.number() != null) {
                match = match.or(Q.userId.eq(f.number())).or(Q.groupBuyId.eq(f.number()));
            }
            where = where == null ? match : where.and(match);
        }
        if (f.overdueBefore() != null) {
            BooleanExpression overdue = open().and(Q.createdAt.lt(f.overdueBefore()));
            where = where == null ? overdue : where.and(overdue);
        }
        return where; // null 이면 거르지 않는다(QueryDSL 은 null 조건을 뺀다)
    }

    /** 관리자 목록의 수(같은 조건 · 같은 조인) */
    public long adminCount(AdminFilter f) {
        return n(query.select(Q.count()).from(Q).join(G).on(G.id.eq(Q.groupBuyId)).join(P).on(P.id.eq(G.productId)).leftJoin(PR).on(PR.userId.eq(Q.userId))
                .where(adminWhere(f)).fetchOne());
    }

    /** 관리자 목록 한 쪽 — {@code oldestFirst} 면 작성 순(답변 대기 탭), 아니면 새 것 먼저 */
    public List<AdminQuestionRow> adminPage(AdminFilter f, boolean oldestFirst, long offset, int limit) {
        JPAQuery<Tuple> q = adminSelect().where(adminWhere(f));
        q = oldestFirst ? q.orderBy(Q.createdAt.asc(), Q.id.asc()) : q.orderBy(Q.createdAt.desc(), Q.id.desc());
        return q.offset(offset).limit(limit).fetch().stream().map(GroupBuyQnaStore::adminRow).toList();
    }

    public Optional<AdminQuestionRow> adminRow(long id) {
        return Optional.ofNullable(adminSelect().where(Q.id.eq(id)).fetchFirst()).map(GroupBuyQnaStore::adminRow);
    }

    /** 지우지 않은 질문의 탭별 수 — OPEN · ANSWERED(숨기지 않은 것의 상태) · HIDDEN(숨긴 것) */
    public Map<String, Long> stateCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("OPEN", 0L);
        counts.put("ANSWERED", 0L);
        query.select(Q.status, Q.count()).from(Q).where(Q.deletedAt.isNull(), Q.hiddenAt.isNull()).groupBy(Q.status).fetch()
                .forEach(t -> counts.put(t.get(0, String.class), n(t.get(1, Long.class))));
        counts.put("HIDDEN", n(query.select(Q.count()).from(Q).where(Q.deletedAt.isNull(), Q.hiddenAt.isNotNull()).fetchOne()));
        return counts;
    }

    /** 답변 기한이 지난 답변 대기 수 — 이 시각 전에 쓴 것 */
    public long overdueCount(Instant before) {
        return n(query.select(Q.count()).from(Q).where(open(), Q.createdAt.lt(before)).fetchOne());
    }

    public Optional<QuestionExtra> extra(long id) {
        Tuple t = query.select(Q.updatedAt, Q.deletedAt, Q.retainUntil, Q.hiddenReason, Q.hiddenAt, Q.hiddenBy, O.operatorName).from(Q).leftJoin(O).on(O.id.eq(Q.hiddenBy))
                .where(Q.id.eq(id)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new QuestionExtra(t.get(Q.updatedAt), t.get(Q.deletedAt), t.get(Q.retainUntil), t.get(Q.hiddenReason),
                t.get(Q.hiddenAt), t.get(Q.hiddenBy), t.get(O.operatorName)));
    }

    public Optional<AnswerDetail> answer(long questionId) {
        Tuple t = query.select(A.body, A.answeredAt, A.answeredBy, O.operatorName, A.updatedAt, A.updatedBy, O2.operatorName).from(A).leftJoin(O).on(O.id.eq(A.answeredBy))
                .leftJoin(O2).on(O2.id.eq(A.updatedBy)).where(A.questionId.eq(questionId)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new AnswerDetail(t.get(A.body), t.get(A.answeredAt), t.get(A.answeredBy), t.get(O.operatorName),
                t.get(A.updatedAt), t.get(A.updatedBy), t.get(O2.operatorName)));
    }

    /** 답변 수정 이력 — 새 것 먼저 */
    public List<RevisionRow> revisions(long questionId) {
        return query.select(REV.id, REV.body, REV.writtenAt, REV.writtenBy, O.operatorName, REV.replacedAt, REV.replacedBy, O2.operatorName).from(REV)
                .leftJoin(O).on(O.id.eq(REV.writtenBy)).leftJoin(O2).on(O2.id.eq(REV.replacedBy)).where(REV.questionId.eq(questionId))
                .orderBy(REV.replacedAt.desc(), REV.id.desc()).fetch().stream()
                .map(t -> new RevisionRow(t.get(REV.id), t.get(REV.body), t.get(REV.writtenAt), t.get(REV.writtenBy), t.get(O.operatorName), t.get(REV.replacedAt),
                        t.get(REV.replacedBy), t.get(O2.operatorName))).toList();
    }

    /** 질문 줄을 잠그고(답변·숨김끼리 줄 세움) 읽는다 */
    public Optional<LockedQuestion> lock(long id) {
        if (!RowLocks.forUpdate(entityManager, GroupBuyQuestionEntity.class, id)) {
            return Optional.empty();
        }
        Tuple t = query.select(Q.userId, Q.groupBuyId, Q.deletedAt).from(Q).where(Q.id.eq(id)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new LockedQuestion(t.get(Q.userId), t.get(Q.groupBuyId), t.get(Q.deletedAt) != null));
    }

    public Optional<CurrentAnswer> currentAnswer(long questionId) {
        Tuple t = query.select(A.body, A.updatedAt, A.answeredAt, A.updatedBy, A.answeredBy).from(A).where(A.questionId.eq(questionId)).fetchFirst();
        if (t == null) {
            return Optional.empty();
        }
        Instant at = t.get(A.updatedAt) != null ? t.get(A.updatedAt) : t.get(A.answeredAt);
        Long by = t.get(A.updatedBy) != null ? t.get(A.updatedBy) : t.get(A.answeredBy);
        return Optional.of(new CurrentAnswer(t.get(A.body), at, n(by)));
    }

    public void insertAnswer(long questionId, String body, long operatorId, Instant now) {
        insert(GroupBuyAnswerEntity.first(questionId, body, operatorId, now));
    }

    public void insertAnswerRevision(long id, long questionId, String body, long writtenBy, Instant writtenAt, long replacedBy, Instant replacedAt) {
        insert(GroupBuyAnswerRevisionEntity.of(id, questionId, body, writtenBy, writtenAt, replacedBy, replacedAt));
    }

    public void updateAnswer(long questionId, String body, long operatorId, Instant now) {
        query.update(A).set(A.body, body).set(A.updatedBy, operatorId).set(A.updatedAt, now).where(A.questionId.eq(questionId)).execute();
    }

    /** 첫 답변 — 상태 ANSWERED, 보관 만료일을 다시 */
    public void markAnswered(long id, LocalDate retainUntil) {
        query.update(Q).set(Q.status, "ANSWERED").set(Q.retainUntil, retainUntil).where(Q.id.eq(id)).execute();
    }

    public void setRetainUntil(long id, LocalDate retainUntil) {
        query.update(Q).set(Q.retainUntil, retainUntil).where(Q.id.eq(id)).execute();
    }

    public void hide(long id, String reason, long operatorId, Instant now) {
        query.update(Q).set(Q.hiddenAt, now).set(Q.hiddenBy, operatorId).set(Q.hiddenReason, reason).where(Q.id.eq(id)).execute();
    }

    public void unhide(long id) {
        query.update(Q).setNull(Q.hiddenAt).setNull(Q.hiddenBy).setNull(Q.hiddenReason).where(Q.id.eq(id)).execute();
    }
}
