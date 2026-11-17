package com.community.trial.repository.master;

import com.community.ad.entity.QAdAdvertiserEntity;
import com.community.post.entity.QPostEntity;
import com.community.trial.entity.QTrialApplicationEntity;
import com.community.trial.entity.QTrialEntity;
import com.community.trial.entity.QTrialParticipantEntity;
import com.community.trial.entity.QTrialStockReceiptEntity;
import com.community.trial.entity.QTrialDamageReportEntity;
import com.community.trial.entity.QTrialPhotoEntity;
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


    /** 붙은 사진 한 줄의 메타(바이트 없이 — 상세·내 체험단 응답이 사진 바이트를 메모리에 올리지 않게, V243) */
    public record PhotoMeta(long id, com.community.trial.entity.TrialPhotoKind kind, Long participantId, Long damageReportId, Instant createdAt, boolean purged) {

        public static PhotoMeta of(com.community.trial.entity.TrialPhotoEntity p) {
            return new PhotoMeta(p.getId(), p.getKind(), p.getParticipantId(), p.getDamageReportId(), p.getCreatedAt(), p.purged());
        }
    }

    /** 모집의 붙은 사진 메타(ID 차례) */
    public List<PhotoMeta> attachedPhotos(long trialId) {
        QTrialPhotoEntity PH = QTrialPhotoEntity.trialPhotoEntity;
        return query.select(PH.id, PH.kind, PH.participantId, PH.damageReportId, PH.createdAt, PH.purgedAt).from(PH).where(PH.trialId.eq(trialId), PH.attachedAt.isNotNull())
                .orderBy(PH.id.asc()).fetch().stream().map(r -> new PhotoMeta(r.get(PH.id), r.get(PH.kind), r.get(PH.participantId), r.get(PH.damageReportId),
                        r.get(PH.createdAt), r.get(PH.purgedAt) != null)).toList();
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

    /**
     * 반납 독촉·미반납 판정을 할 때가 된 줄 한 쪽(M4-3 — 때가 된 것만 질의에서 거르고 ID 커서로 끝까지): 대여형에서 받았는데 반납·미반납 확정이 없고, 첫 독촉 전이면 기한 + 첫 독촉 일수,
     * 두 번째 독촉 전이면 기한 + 두 번째 일수, 아니면 기한 + 미반납 일수가 지났다. {@code afterId} 보다 큰 ID 차례.
     */
    public List<TrialParticipantEntity> returnOpen(Instant now, int firstDays, int secondDays, int unreturnedDays, long afterId, int limit) {
        BooleanExpression due = P.returnRemindFirstAt.isNull().and(P.returnDueAt.loe(now.minus(java.time.Duration.ofDays(firstDays))))
                .or(P.returnRemindSecondAt.isNull().and(P.returnDueAt.loe(now.minus(java.time.Duration.ofDays(secondDays)))))
                .or(P.returnDueAt.loe(now.minus(java.time.Duration.ofDays(unreturnedDays))));
        return query.selectFrom(P).join(T).on(T.id.eq(P.trialId))
                .where(T.form.eq(TrialForm.LEND), P.receivedAt.isNotNull(), P.returnedAt.isNull(), P.unreturnedAt.isNull(), P.returnDueAt.isNotNull(), due, P.id.gt(afterId))
                .orderBy(P.id.asc()).limit(limit).fetch();
    }

    /** 입고 보관 기한이 된(반송·알림 전) 입고 줄 */
    public List<TrialStockReceiptEntity> stockKeepDue(Instant receivedBefore) {
        return query.selectFrom(S).where(S.receivedAt.loe(receivedBefore), S.returnShippedAt.isNull(), S.keepNotifiedAt.isNull()).limit(500).fetch();
    }

    /**
     * 주소 사본이 남은 신청 중 파기를 따져 볼 줄 한 쪽(ID 만 — M4-3: 아직 때가 안 된 줄이 앞을 막지 않게 ID 커서로 끝까지) — 신청 대기(모집 진행 중)가 아닌 것, 또는 모집이 취소·끝난 것.
     * {@code afterId} 보다 큰 ID 차례.
     */
    public List<Long> purgeCandidates(long afterId, int limit) {
        BooleanExpression trialDone = JPAExpressions.selectOne().from(T).where(T.id.eq(A.trialId), T.status.in(TrialStatus.CANCELLED, TrialStatus.CLOSED)).exists();
        return query.select(A.id).from(A).where(A.addressPurgedAt.isNull(), A.status.ne(TrialApplicationStatus.APPLIED).or(trialDone), A.id.gt(afterId))
                .orderBy(A.id.asc()).limit(limit).fetch();
    }

    /**
     * 보관 기한이 지난 체험단 사진 한 쪽(점검 D3 — ID 차례, {@code afterId} 커서): 붙은 사진 중 아직 지우지 않았고, 모집이 끝났거나(CLOSED — 끝난 때) 취소됐는데(취소한 때) 그때가
     * {@code cutoff} 이전이고, 그 모집에 판정 안 된 파손 신고가 없으며 판정한 파손 신고도 판정 때가 {@code cutoff} 이전인 것.
     */
    public List<Long> photoPurgeCandidates(Instant cutoff, long afterId, int limit) {
        QTrialPhotoEntity PH = QTrialPhotoEntity.trialPhotoEntity;
        QTrialDamageReportEntity D = QTrialDamageReportEntity.trialDamageReportEntity;
        BooleanExpression ended = JPAExpressions.selectOne().from(T).where(T.id.eq(PH.trialId),
                T.status.eq(TrialStatus.CLOSED).and(T.closedAt.loe(cutoff)).or(T.status.eq(TrialStatus.CANCELLED).and(T.cancelledAt.loe(cutoff)))).exists();
        BooleanExpression damageOpen = JPAExpressions.selectOne().from(D).where(D.trialId.eq(PH.trialId),
                D.status.ne(com.community.trial.entity.TrialDamageStatus.DECIDED).or(D.decidedAt.gt(cutoff))).exists();
        return query.select(PH.id).from(PH).where(PH.purgedAt.isNull(), PH.attachedAt.isNotNull(), ended, damageOpen.not(), PH.id.gt(afterId)).orderBy(PH.id.asc())
                .limit(limit).fetch();
    }

    /** 진행 중인 모집 중 모든 뽑힌 줄이 판정(약속 지킴 여부)된 모집 — 자동으로 끝낸다. 운영자가 취소한 줄(V242 — 끝내 보내지 않는 줄)은 판정에서 뺀다 */
    public List<Long> runningAllJudged() {
        BooleanExpression open = JPAExpressions.selectOne().from(P).where(P.trialId.eq(T.id), P.cancelledAt.isNull(), P.kept.isNull()).exists();
        BooleanExpression any = JPAExpressions.selectOne().from(P).where(P.trialId.eq(T.id), P.cancelledAt.isNull()).exists();
        return query.select(T.id).from(T).where(T.status.eq(TrialStatus.RUNNING), any, open.not()).fetch();
    }

    // ── 결제 연결(V242 — trial-group-contract 「유료 결제 연결」) ─────────────────────

    /** 모집의 광고 신청 건 ID(엔티티를 영속성 문맥에 올리지 않는 칸 읽기 — 신청 건 행을 먼저 잠그려고) */
    public java.util.Optional<Long> bizApplicationIdOf(long trialId) {
        return java.util.Optional.ofNullable(query.select(T.bizApplicationId).from(T).where(T.id.eq(trialId)).fetchFirst());
    }

    /** 뽑힌 줄의 모집 ID(칸 읽기) */
    public java.util.Optional<Long> trialIdOfParticipant(long participantId) {
        return java.util.Optional.ofNullable(query.select(P.trialId).from(P).where(P.id.eq(participantId)).fetchFirst());
    }

    /** 신청 건 ID → 모집 ID(칸 읽기) */
    public java.util.Optional<Long> trialIdOfBizApplication(long bizApplicationId) {
        return java.util.Optional.ofNullable(query.select(T.id).from(T).where(T.bizApplicationId.eq(bizApplicationId)).fetchFirst());
    }

    /** 사업자 신원에 지금 이어진 광고주의 모집(그 상태들만) — ID 차례 */
    public List<Long> byBusinessProfile(long businessProfileId, Collection<TrialStatus> statuses) {
        QAdAdvertiserEntity adv = QAdAdvertiserEntity.adAdvertiserEntity;
        return query.select(T.id).from(T).join(adv).on(adv.id.eq(T.advertiserId))
                .where(adv.businessProfileId.eq(businessProfileId), T.status.in(statuses)).orderBy(T.id.asc()).fetch();
    }

    /** 취소하지 않은 뽑힌 줄 중 무엇이든 시작된 줄(송장·받음·사용기 연결)이 있나 */
    public boolean anyStarted(long trialId) {
        return query.selectOne().from(P).where(P.trialId.eq(trialId), P.cancelledAt.isNull(),
                P.shippedAt.isNotNull().or(P.receivedAt.isNotNull()).or(P.reviewPostId.isNotNull())).fetchFirst() != null;
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

    /** 반납 확인 대기 수(확인할 일 대기 수 — 질의 한 번, 점검 L8) — {@link #returnChecks} 대기와 같은 조건 */
    public long countReturnChecksPending(Instant now) {
        BooleanExpression lend = JPAExpressions.selectOne().from(T).where(T.id.eq(P.trialId), T.form.eq(TrialForm.LEND)).exists();
        Long n = query.select(P.id.count()).from(P).where(lend, P.receivedAt.isNotNull(), P.returnedAt.isNull(), P.unreturnedAt.isNull(), P.returnDueAt.loe(now))
                .fetchOne();
        return n == null ? 0 : n;
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
