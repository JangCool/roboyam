package com.community.trial.repository.master;

import com.community.post.entity.QPostEntity;
import com.community.trial.entity.QTrialApplicationEntity;
import com.community.trial.entity.QTrialEntity;
import com.community.trial.entity.QTrialParticipantEntity;
import com.community.trial.entity.QTrialStockReceiptEntity;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialForm;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialStatus;
import com.community.trial.entity.TrialStockReceiptEntity;
import com.community.trial.entity.TrialApplicationEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 체험단 묶음 읽기(trial-group-contract) — 목록·신청 수·신청자 숫자(사용기 수·평균 추천·약속 지킨 비율)·끝난 모집의 사용기·확인할 일·하루 배치 후보. 모두 QueryDSL.
 * 트랜잭션은 부르는 서비스가 연다. 시각 판정은 서비스가 넘기는 {@code now}(시험이 시계를 옮긴다).
 */
@Repository
public class TrialQueries extends IrobitRepositoryQDSL {

    private static final QTrialEntity T = QTrialEntity.trialEntity;
    private static final QTrialApplicationEntity A = QTrialApplicationEntity.trialApplicationEntity;
    private static final QTrialParticipantEntity P = QTrialParticipantEntity.trialParticipantEntity;
    private static final QTrialStockReceiptEntity S = QTrialStockReceiptEntity.trialStockReceiptEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;

    public TrialQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 사용기 수·평균 추천(그 게시판 지우지 않은 글) · 약속 지킴(지킨 수·판정 수) */
    public record Stats(long reviewPosts, double averageLikes, long kept, long judged) {
    }

    // ── 모집 ────────────────────────────────────────────────────────────

    /** 관리자 목록 — 상태(null 이면 전부), 최근 만든 차례 500건까지 */
    public List<TrialEntity> list(TrialStatus status) {
        return query.selectFrom(T).where(status == null ? null : T.status.eq(status)).orderBy(T.createdAt.desc(), T.id.desc()).limit(500).fetch();
    }

    /** 공개 목록 — 상태 묶음, 신청 시작이 늦은 차례 200건까지 */
    public List<TrialEntity> byStatuses(Collection<TrialStatus> statuses) {
        return query.selectFrom(T).where(T.status.in(statuses)).orderBy(T.applyStartAt.desc(), T.id.desc()).limit(200).fetch();
    }

    /** 광고주의 모집(초안 빼고) — 최근 차례 */
    public List<TrialEntity> byAdvertiser(long advertiserId) {
        return query.selectFrom(T).where(T.advertiserId.eq(advertiserId), T.status.ne(TrialStatus.DRAFT)).orderBy(T.createdAt.desc(), T.id.desc()).limit(200).fetch();
    }

    /** 모집마다 살아 있는(취소 아닌) 신청 수 */
    public Map<Long, Long> applicantCounts(Collection<Long> trialIds) {
        Map<Long, Long> out = new HashMap<>();
        if (trialIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(A.trialId, A.id.count()).from(A).where(A.trialId.in(trialIds), A.status.ne(TrialApplicationStatus.CANCELLED)).groupBy(A.trialId)
                .fetch()) {
            out.put(t.get(A.trialId), t.get(A.id.count()));
        }
        return out;
    }

    /** 신청자 숫자 — 사용기 게시판 글 수·평균 추천(한 질의) + 체험단 약속 지킴(한 질의) */
    public Map<Long, Stats> stats(Collection<Long> userIds, long reviewBoardId) {
        Map<Long, Stats> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        Map<Long, long[]> posts = new HashMap<>();
        Map<Long, Double> likes = new HashMap<>();
        for (Tuple t : query.select(POST.authorId, POST.id.count(), POST.likeCount.avg()).from(POST)
                .where(POST.authorId.in(userIds), POST.boardId.eq(reviewBoardId), POST.deletedAt.isNull()).groupBy(POST.authorId).fetch()) {
            posts.put(t.get(POST.authorId), new long[] {t.get(POST.id.count())});
            Double avg = t.get(POST.likeCount.avg());
            likes.put(t.get(POST.authorId), avg == null ? 0 : avg);
        }
        NumberExpression<Long> kept = new CaseBuilder().when(P.kept.isTrue()).then(1L).otherwise(0L).sum();
        NumberExpression<Long> judged = new CaseBuilder().when(P.kept.isNotNull()).then(1L).otherwise(0L).sum();
        Map<Long, long[]> promise = new HashMap<>();
        for (Tuple t : query.select(P.userId, kept, judged).from(P).where(P.userId.in(userIds)).groupBy(P.userId).fetch()) {
            promise.put(t.get(P.userId), new long[] {nz(t.get(kept)), nz(t.get(judged))});
        }
        for (Long id : userIds) {
            long[] p = posts.getOrDefault(id, new long[] {0});
            long[] k = promise.getOrDefault(id, new long[] {0, 0});
            out.put(id, new Stats(p[0], Math.round(likes.getOrDefault(id, 0.0) * 10) / 10.0, k[0], k[1]));
        }
        return out;
    }

    /** 끝난 모집의 사용기 — 연결된 글 중 살아 있는(지우지 않고 숨기지 않은) 글, 쓴 차례 */
    public List<Tuple> reviews(long trialId, Instant now) {
        return query.select(POST.id, POST.title, POST.boardId, POST.authorId, POST.createdAt).from(P).join(POST).on(POST.id.eq(P.reviewPostId))
                .where(P.trialId.eq(trialId), POST.deletedAt.isNull(), POST.hiddenAt.isNull().or(POST.hiddenUntil.isNotNull().and(POST.hiddenUntil.lt(now))))
                .orderBy(POST.createdAt.asc(), POST.id.asc()).fetch();
    }

    // ── 하루 배치 후보 ──────────────────────────────────────────────────────

    /** 신청 마감이 지난 모집 중(RECRUITING) */
    public List<Long> recruitingEnded(Instant now) {
        return query.select(T.id).from(T).where(T.status.eq(TrialStatus.RECRUITING), T.applyEndAt.lt(now)).fetch();
    }

    /** 송장 뒤 받음을 누르지 않은 지 그 시각이 지난 줄(자동 받음) */
    public List<Long> autoReceiveDue(Instant shippedBefore) {
        return query.select(P.id).from(P).where(P.shippedAt.loe(shippedBefore), P.receivedAt.isNull()).orderBy(P.id.asc()).limit(1000).fetch();
    }

    /** 사용기 기한이 지났는데 연결 글이 없고 아직 판정하지 않은 줄 */
    public List<Long> reviewLateDue(Instant now) {
        return query.select(P.id).from(P).where(P.receivedAt.isNotNull(), P.reviewPostId.isNull(), P.reviewDueAt.lt(now), P.reviewLateAt.isNull(), P.unreturnedAt.isNull())
                .orderBy(P.id.asc()).limit(1000).fetch();
    }

    /** 대여형에서 받았는데 반납·미반납 확정이 없는 줄(반납 독촉·미반납 판정 대상) */
    public List<TrialParticipantEntity> returnOpen() {
        return query.selectFrom(P).join(T).on(T.id.eq(P.trialId))
                .where(T.form.eq(TrialForm.LEND), P.receivedAt.isNotNull(), P.returnedAt.isNull(), P.unreturnedAt.isNull(), P.returnDueAt.isNotNull())
                .orderBy(P.id.asc()).limit(1000).fetch();
    }

    /** 입고 보관 기한이 된(반송·알림 전) 입고 줄 */
    public List<TrialStockReceiptEntity> stockKeepDue(Instant receivedBefore) {
        return query.selectFrom(S).where(S.receivedAt.loe(receivedBefore), S.returnShippedAt.isNull(), S.keepNotifiedAt.isNull()).limit(500).fetch();
    }

    /** 주소 사본이 남은 신청 중 파기를 따져 볼 줄 — 신청 대기(모집 진행 중)가 아닌 것, 또는 모집이 취소·끝난 것 */
    public List<TrialApplicationEntity> purgeCandidates() {
        BooleanExpression trialDone = JPAExpressions.selectOne().from(T).where(T.id.eq(A.trialId), T.status.in(TrialStatus.CANCELLED, TrialStatus.CLOSED)).exists();
        return query.selectFrom(A).where(A.addressPurgedAt.isNull(), A.status.ne(TrialApplicationStatus.APPLIED).or(trialDone)).orderBy(A.id.asc()).limit(1000).fetch();
    }

    /** 진행 중인 모집 중 모든 뽑힌 줄이 판정(약속 지킴 여부)된 모집 — 자동으로 끝낸다 */
    public List<Long> runningAllJudged() {
        BooleanExpression open = JPAExpressions.selectOne().from(P).where(P.trialId.eq(T.id), P.kept.isNull()).exists();
        BooleanExpression any = JPAExpressions.selectOne().from(P).where(P.trialId.eq(T.id)).exists();
        return query.select(T.id).from(T).where(T.status.eq(TrialStatus.RUNNING), any, open.not()).fetch();
    }

    // ── 확인할 일 ───────────────────────────────────────────────────────────

    /** 반납 확인 — 대여형에서 받았고 반납 기한이 지났는데 반납·미반납 확정이 없는 줄(대기) · 반납 받은 줄(처리됨, 최근 차례) */
    public List<TrialParticipantEntity> returnChecks(boolean pending, Instant now, int limit) {
        BooleanExpression lend = JPAExpressions.selectOne().from(T).where(T.id.eq(P.trialId), T.form.eq(TrialForm.LEND)).exists();
        if (pending) {
            return query.selectFrom(P).where(lend, P.receivedAt.isNotNull(), P.returnedAt.isNull(), P.unreturnedAt.isNull(), P.returnDueAt.loe(now))
                    .orderBy(P.returnDueAt.asc(), P.id.asc()).limit(limit).fetch();
        }
        return query.selectFrom(P).where(lend, P.returnedAt.isNotNull().or(P.unreturnedAt.isNotNull())).orderBy(P.id.desc()).limit(limit).fetch();
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
