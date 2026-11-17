package com.community.title.repository.master;

import com.community.aimember.entity.QAiMemberProfileEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.operator.entity.QOperatorMemberAccountEntity;
import com.community.point.entity.QPointAttendanceEntity;
import com.community.point.entity.QPointPopularRewardEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.entity.QPostFeatureEntity;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.entity.QMemberTitleEntity;
import com.community.title.entity.QTitleApplicationEntity;
import com.community.title.entity.TitleApplicationEntity;
import com.community.title.entity.TitleApplicationStatus;
import com.community.title.entity.TitleTier;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 회원 칭호의 묶음 읽기(member-title-contract) — 작성자 줄의 회원 상태·마지막 활동, 보유자 수·비율, 조건 수치(글·인기글 일수·출석·신고 숨김), 자동 판정 배치의 후보(회원 ID 차례 덩어리),
 * 명예의 전당·주고 뺀 기록·신청 목록·이달의 작가 후보. 모두 QueryDSL(집계는 group by·having). 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class TitleQueries extends IrobitRepositoryQDSL {

    private static final QMemberTitleEntity M = QMemberTitleEntity.memberTitleEntity;
    private static final QTitleApplicationEntity A = QTitleApplicationEntity.titleApplicationEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;
    private static final QPostEntity P = QPostEntity.postEntity;
    private static final QCommentEntity C = QCommentEntity.commentEntity;
    private static final QPostFeatureEntity F = QPostFeatureEntity.postFeatureEntity;
    private static final QPointPopularRewardEntity R = QPointPopularRewardEntity.pointPopularRewardEntity;
    private static final QPointAttendanceEntity AT = QPointAttendanceEntity.pointAttendanceEntity;
    private static final QAiMemberProfileEntity AI = QAiMemberProfileEntity.aiMemberProfileEntity;
    private static final QOperatorMemberAccountEntity OPS = QOperatorMemberAccountEntity.operatorMemberAccountEntity;
    private static final com.community.trial.entity.QPurchaseProofEntity PP = com.community.trial.entity.QPurchaseProofEntity.purchaseProofEntity;
    private static final com.community.trial.entity.QTrialParticipantEntity TP = com.community.trial.entity.QTrialParticipantEntity.trialParticipantEntity;

    /** 신고로 숨겨진 글의 숨김 사유(V80·V127 — 운영 숨김 STAFF·판매 정지는 신고가 아니다) */
    public static final List<String> REPORT_HIDDEN_REASONS = List.of("REPORTED", "ILLEGAL", "RIGHTS");

    public TitleQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 덩어리 후보 한 명 — 회원 ID 와 센 수 */
    public record Counted(long userId, long count) {
    }

    // ── 회원 ─────────────────────────────────────────────────────────────

    /** AI 회원·운영팀 계정이 아닌 회원 — 이 회원 칸에 붙여 「칭호를 받을 수 있는 회원」으로 거른다 */
    private static BooleanExpression notExcluded(com.querydsl.core.types.dsl.NumberPath<Long> userId) {
        return JPAExpressions.selectOne().from(AI).where(AI.userId.eq(userId)).notExists()
                .and(JPAExpressions.selectOne().from(OPS).where(OPS.userId.eq(userId)).notExists());
    }

    /** AI 회원이거나 운영팀 계정인가(늘 제외 — 계약 「규칙」) */
    public boolean excluded(long userId) {
        return query.selectOne().from(AI).where(AI.userId.eq(userId)).fetchFirst() != null
                || query.selectOne().from(OPS).where(OPS.userId.eq(userId)).fetchFirst() != null;
    }

    /** 회원들의 상태(없는 회원은 빠진다) — 한 질의 */
    public Map<Long, UserStatus> statuses(Collection<Long> userIds) {
        Map<Long, UserStatus> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(U.id, U.status).from(U).where(U.id.in(userIds)).fetch()) {
            out.put(t.get(U.id), t.get(U.status));
        }
        return out;
    }

    /** 회원들의 마지막 글·댓글 시각(휴면 판정) — 글 한 질의 + 댓글 한 질의(작성자 색인), 둘 중 늦은 것 */
    public Map<Long, Instant> lastActivity(Collection<Long> userIds) {
        Map<Long, Instant> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(P.authorId, P.createdAt.max()).from(P).where(P.authorId.in(userIds)).groupBy(P.authorId).fetch()) {
            out.merge(t.get(P.authorId), t.get(P.createdAt.max()), (a, b) -> a.isAfter(b) ? a : b);
        }
        for (Tuple t : query.select(C.authorId, C.createdAt.max()).from(C).where(C.authorId.in(userIds)).groupBy(C.authorId).fetch()) {
            out.merge(t.get(C.authorId), t.get(C.createdAt.max()), (a, b) -> a.isAfter(b) ? a : b);
        }
        return out;
    }

    /** 보유 비율의 분모 — 정상(ACTIVE) 회원 중 AI 회원·운영팀 계정이 아닌 회원 수 */
    public long memberBase() {
        Long n = query.select(U.id.count()).from(U).where(U.status.eq(UserStatus.ACTIVE), notExcluded(U.id)).fetchOne();
        return n == null ? 0 : n;
    }

    // ── 받은 칭호 ──────────────────────────────────────────────────────────

    /** 회원들의 살아 있는 받은 줄 — 작성자 줄·프로필(회원 묶음 IN 한 질의) */
    public List<MemberTitleEntity> activeOf(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(M).where(M.userId.in(userIds), M.revokedAt.isNull()).orderBy(M.grantedAt.asc(), M.id.asc()).fetch();
    }

    /** 칭호별 보유 회원 수(탈퇴 회원 빼고, 같은 회원은 한 번) */
    public Map<Long, Long> holders() {
        Map<Long, Long> out = new HashMap<>();
        NumberExpression<Long> n = M.userId.countDistinct();
        for (Tuple t : query.select(M.titleId, n).from(M).where(M.revokedAt.isNull(), holderAlive()).groupBy(M.titleId).fetch()) {
            out.put(t.get(M.titleId), t.get(n));
        }
        return out;
    }

    /** 칭호·단계별 보유 회원 수 */
    public Map<Long, Map<TitleTier, Long>> tierHolders() {
        Map<Long, Map<TitleTier, Long>> out = new HashMap<>();
        NumberExpression<Long> n = M.userId.countDistinct();
        for (Tuple t : query.select(M.titleId, M.tier, n).from(M).where(M.revokedAt.isNull(), M.tier.isNotNull(), holderAlive()).groupBy(M.titleId, M.tier).fetch()) {
            out.computeIfAbsent(t.get(M.titleId), k -> new HashMap<>()).put(t.get(M.tier), t.get(n));
        }
        return out;
    }

    private static BooleanExpression holderAlive() {
        return JPAExpressions.selectOne().from(U).where(U.id.eq(M.userId), U.status.ne(UserStatus.WITHDRAWN)).exists();
    }

    /** 명예의 전당 — 그 칭호들의 살아 있는 줄 중 정상(ACTIVE) 회원 것, 기간 최신 순·받은 차례 */
    public List<MemberTitleEntity> hallOf(Collection<Long> titleIds) {
        if (titleIds.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(M).where(M.titleId.in(titleIds), M.revokedAt.isNull(),
                        JPAExpressions.selectOne().from(U).where(U.id.eq(M.userId), U.status.eq(UserStatus.ACTIVE)).exists())
                .orderBy(M.period.desc(), M.grantedAt.asc(), M.id.asc()).fetch();
    }

    /** 주고 뺀 기록 — 칭호·회원으로 거르고 마지막 사건(회수 또는 수여) 최신 순 */
    public List<MemberTitleEntity> grantLog(Long titleId, Long userId, int page, int size) {
        return query.selectFrom(M).where(logFilter(titleId, userId)).orderBy(M.revokedAt.coalesce(M.grantedAt).desc(), M.id.desc())
                .offset((long) page * size).limit(size).fetch();
    }

    public long grantLogCount(Long titleId, Long userId) {
        Long n = query.select(M.id.count()).from(M).where(logFilter(titleId, userId)).fetchOne();
        return n == null ? 0 : n;
    }

    private static BooleanBuilder logFilter(Long titleId, Long userId) {
        BooleanBuilder where = new BooleanBuilder();
        if (titleId != null) {
            where.and(M.titleId.eq(titleId));
        }
        if (userId != null) {
            where.and(M.userId.eq(userId));
        }
        return where;
    }

    /** 회원 하나의 받은 줄 전부(회수 포함) — 관리자 회원 칭호 */
    public List<MemberTitleEntity> allOf(long userId) {
        return query.selectFrom(M).where(M.userId.eq(userId)).orderBy(M.grantedAt.desc(), M.id.desc()).fetch();
    }

    // ── 조건 수치 ──────────────────────────────────────────────────────────

    /** 지우지 않은 글 수 — 게시판({@code boardId} null 이면 모든 게시판) */
    public long posts(long userId, Long boardId) {
        BooleanBuilder where = new BooleanBuilder(P.authorId.eq(userId)).and(P.deletedAt.isNull());
        if (boardId != null) {
            where.and(P.boardId.eq(boardId));
        }
        Long n = query.select(P.id.count()).from(P).where(where).fetchOne();
        return n == null ? 0 : n;
    }

    /** 승인된 사용기 구매 인증 수(V241 — 리뷰어 칭호 조건 「구매 인증 1편 이상」) */
    public long purchaseProofs(long userId) {
        Long n = query.select(PP.id.count()).from(PP).where(PP.userId.eq(userId), PP.status.eq(com.community.trial.entity.PurchaseProofStatus.APPROVED)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 체험단 약속 지킨 횟수(V241 — 리뷰어 단계: 기한 안 연결 사용기 + 대여형이면 반납까지) */
    public long trialKept(long userId) {
        Long n = query.select(TP.id.count()).from(TP).where(TP.userId.eq(userId), TP.kept.isTrue()).fetchOne();
        return n == null ? 0 : n;
    }

    /** 체험단 약속 지킴이 {@code min} 이상인 회원(자동 칭호가 TRIAL_KEPT 단계를 쓸 때) */
    public List<Counted> trialKeptCandidates(long afterUserId, long min, int limit) {
        NumberExpression<Long> n = TP.id.count();
        return query.select(TP.userId, n).from(TP).where(TP.kept.isTrue(), candidate(TP.userId, afterUserId)).groupBy(TP.userId).having(n.goe(min))
                .orderBy(TP.userId.asc()).limit(limit).fetch().stream().map(t -> new Counted(t.get(TP.userId), t.get(n))).toList();
    }

    /** 하루 인기글 상위에 든 날 수({@code point_popular_rewards} — 인기글 보상과 같은 뽑기) */
    public long popularDays(long userId) {
        Long n = query.select(R.rewardDay.countDistinct()).from(R).where(R.authorId.eq(userId)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 출석한 날 수 */
    public long attendanceDays(long userId) {
        Long n = query.select(AT.id.count()).from(AT).where(AT.userId.eq(userId)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 신고로 숨겨진(지우지 않은) 글이 지금 있나 */
    public boolean hasReportHidden(long userId) {
        return query.selectOne().from(P).where(P.authorId.eq(userId), P.deletedAt.isNull(), P.hiddenAt.isNotNull(), P.hiddenReason.in(REPORT_HIDDEN_REASONS))
                .fetchFirst() != null;
    }

    // ── 자동 판정 후보(회원 ID 차례 덩어리 — AI·운영팀 계정·탈퇴 회원 제외) ─────────────────

    private BooleanExpression candidate(com.querydsl.core.types.dsl.NumberPath<Long> userId, long afterUserId) {
        return userId.gt(afterUserId).and(notExcluded(userId))
                .and(JPAExpressions.selectOne().from(U).where(U.id.eq(userId), U.status.notIn(UserStatus.WITHDRAWN, UserStatus.WITHDRAWING)).exists());
    }

    /** 인기글 진입 일수가 {@code min} 이상인 회원 */
    public List<Counted> popularDayCandidates(long afterUserId, long min, int limit) {
        NumberExpression<Long> n = R.rewardDay.countDistinct();
        return query.select(R.authorId, n).from(R).where(R.authorId.isNotNull(), candidate(R.authorId, afterUserId)).groupBy(R.authorId).having(n.goe(min))
                .orderBy(R.authorId.asc()).limit(limit).fetch().stream().map(t -> new Counted(t.get(R.authorId), t.get(n))).toList();
    }

    /** 출석 일수가 {@code min} 이상인 회원 */
    public List<Counted> attendanceCandidates(long afterUserId, long min, int limit) {
        NumberExpression<Long> n = AT.id.count();
        return query.select(AT.userId, n).from(AT).where(candidate(AT.userId, afterUserId)).groupBy(AT.userId).having(n.goe(min))
                .orderBy(AT.userId.asc()).limit(limit).fetch().stream().map(t -> new Counted(t.get(AT.userId), t.get(n))).toList();
    }

    /** 게시판({@code boardId} null 이면 모든 게시판)에 지우지 않은 글이 {@code min} 편 이상인 회원 */
    public List<Counted> postCandidates(Long boardId, long afterUserId, long min, int limit) {
        BooleanBuilder where = new BooleanBuilder(P.authorId.isNotNull()).and(P.deletedAt.isNull()).and(candidate(P.authorId, afterUserId));
        if (boardId != null) {
            where.and(P.boardId.eq(boardId));
        }
        NumberExpression<Long> n = P.id.count();
        return query.select(P.authorId, n).from(P).where(where).groupBy(P.authorId).having(n.goe(min))
                .orderBy(P.authorId.asc()).limit(limit).fetch().stream().map(t -> new Counted(t.get(P.authorId), t.get(n))).toList();
    }

    /** 등급 키가 이 중 하나인 회원(세는 조건이 없는 칭호 — 등급 조건만) */
    public List<Counted> gradeCandidates(Collection<String> gradeKeys, long afterUserId, int limit) {
        if (gradeKeys.isEmpty()) {
            return List.of();
        }
        return query.select(PR.userId).from(PR).where(PR.gradeKey.in(gradeKeys), candidate(PR.userId, afterUserId)).orderBy(PR.userId.asc()).limit(limit).fetch()
                .stream().map(id -> new Counted(id, 0)).toList();
    }

    /** 그 칭호(이달의 작가)의 살아 있는 줄이 {@code min} 개 이상인 회원 — 명예 작가 소급 */
    public List<Counted> titleCountCandidates(long titleId, long afterUserId, long min, int limit) {
        NumberExpression<Long> n = M.id.count();
        return query.select(M.userId, n).from(M).where(M.titleId.eq(titleId), M.revokedAt.isNull(), candidate(M.userId, afterUserId)).groupBy(M.userId).having(n.goe(min))
                .orderBy(M.userId.asc()).limit(limit).fetch().stream().map(t -> new Counted(t.get(M.userId), t.get(n))).toList();
    }

    // ── 신청 ─────────────────────────────────────────────────────────────

    /** 신청 목록 — 상태·칭호로 거르고 최신 순({@code limit} 개). {@code titleIds} 가 비어 있지 않으면 그 칭호들만(게시판 매니저) */
    public List<TitleApplicationEntity> applications(TitleApplicationStatus status, Collection<Long> titleIds, int limit) {
        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(A.status.eq(status));
        }
        if (titleIds != null) {
            if (titleIds.isEmpty()) {
                return List.of();
            }
            where.and(A.titleId.in(titleIds));
        }
        return query.selectFrom(A).where(where).orderBy(A.createdAt.desc(), A.id.desc()).limit(limit).fetch();
    }

    // ── 이달의 작가 후보 ─────────────────────────────────────────────────────

    /** 그 달에 우수 글로 뽑힌(지금도 살아 있는 선정) 회원 글 — 글 ID → 글쓴이 */
    public Map<Long, Long> featuredPosts(Instant from, Instant to) {
        Map<Long, Long> out = new HashMap<>();
        for (Tuple t : query.select(F.postId, F.authorId).from(F)
                .where(F.featuredAt.goe(from), F.featuredAt.lt(to), F.unfeaturedAt.isNull(), F.authorId.isNotNull()).orderBy(F.featuredAt.asc()).fetch()) {
            out.put(t.get(F.postId), t.get(F.authorId));
        }
        return out;
    }

    /** 글 제목 — 글 ID → 제목(지운 글도) */
    public Map<Long, String> postTitles(Collection<Long> postIds) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(P.id, P.title).from(P).where(P.id.in(postIds)).fetch()) {
            out.put(t.get(P.id), t.get(P.title));
        }
        return out;
    }

    /** 이 회원들 중 AI 회원·운영팀 계정 */
    public Set<Long> excludedAmong(Collection<Long> userIds) {
        Set<Long> out = new HashSet<>();
        if (userIds.isEmpty()) {
            return out;
        }
        out.addAll(query.select(AI.userId).from(AI).where(AI.userId.in(userIds)).fetch());
        out.addAll(query.select(OPS.userId).from(OPS).where(OPS.userId.in(userIds)).fetch());
        return out;
    }
}
