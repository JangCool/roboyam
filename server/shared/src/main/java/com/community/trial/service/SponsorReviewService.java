package com.community.trial.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.PurchaseSource;
import com.community.trial.entity.QTrialSponsorReviewEntity;
import com.community.trial.entity.SponsorReviewStatus;
import com.community.trial.entity.TrialSponsorReviewEntity;
import com.community.trial.exception.TrialException;
import com.community.trial.repository.master.TrialSponsorReviewRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협찬 숨김 신고 확인(trial-group-contract 「협찬 숨김」·「구현 결정」 6·7) — 신고 사유 {@code SPONSOR_HIDDEN} 이 들어오면 글마다 확인 줄을 대기로 두고(신고 트랜잭션 안), 운영자가
 * 확인하면 글의 구입 경로를 SPONSORED 로 바꿔 협찬 표시를 강제로 붙인다(체험단 연결 글은 이미 표시가 있어 그대로 — 글쓴이는 고치기로 되돌릴 수 없다, 점검 D2). 무시하면 그대로. 감사 기록.
 * 확인에 곁들인 회원 제재·그 글의 신고 사건 함께 닫기는 관리자 서버 {@code SponsorHiddenActionService}, 신고 관리에서 사건을 닫으면 {@link #closeFromReport}.
 */
@Service
public class SponsorReviewService {

    private static final QTrialSponsorReviewEntity R = QTrialSponsorReviewEntity.trialSponsorReviewEntity;

    private final TrialSponsorReviewRepository reviews;
    private final PostRepository posts;
    private final AuditLogService audit;
    private final JPAQueryFactory query;
    private final Clock clock;

    public SponsorReviewService(TrialSponsorReviewRepository reviews, PostRepository posts, AuditLogService audit, JPAQueryFactory query, ObjectProvider<Clock> clocks) {
        this.reviews = reviews;
        this.posts = posts;
        this.audit = audit;
        this.query = query;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 신고 한 건(신고 트랜잭션 안) — 줄이 없으면 대기로 만들고, 있으면 수를 올린다(무시한 줄은 다시 대기) */
    @Transactional
    public void reported(long postId) {
        Instant now = now();
        TrialSponsorReviewEntity row = reviews.findWithLockByPostId(postId).orElse(null);
        if (row != null) {
            row.reported(now);
            return;
        }
        try {
            reviews.persist(TrialSponsorReviewEntity.open(postId, now));
            reviews.flush();
        } catch (DuplicateKeyException e) {
            reviews.findWithLockByPostId(postId).ifPresent(r -> r.reported(now)); // 동시에 다른 신고가 먼저 만들었다
        }
    }

    /** 확인 — 글을 협찬으로 바꾼다 */
    @Transactional
    public void confirm(long operatorId, String ip, String postIdText, String note) {
        TrialSponsorReviewEntity row = lockPending(postIdText);
        PostEntity post = posts.findWithLockById(row.getPostId()).orElseThrow(TrialException::sponsorReviewNotFound);
        String before = post.getPurchaseSource();
        if (post.getTrialParticipantId() == null) {
            post.changePurchaseSource(PurchaseSource.SPONSORED.name());
        }
        row.decide(SponsorReviewStatus.CONFIRMED, operatorId, clean(note), now());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.SPONSOR_HIDDEN_CONFIRM, AuditResult.SUCCESS).target("POST", Long.toString(post.getId()))
                .with("from", before == null ? "NONE" : before).with("to", post.getPurchaseSource() == null ? "NONE" : post.getPurchaseSource()));
    }

    @Transactional
    public void dismiss(long operatorId, String ip, String postIdText, String note) {
        TrialSponsorReviewEntity row = lockPending(postIdText);
        row.decide(SponsorReviewStatus.DISMISSED, operatorId, clean(note), now());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.SPONSOR_HIDDEN_DISMISS, AuditResult.SUCCESS).target("POST", Long.toString(row.getPostId())));
    }

    /**
     * 신고 관리에서 그 글의 신고 사건을 닫았다(점검 D2 — 사건 처리 트랜잭션 안) — 대기 확인 줄이 있으면 함께 닫는다: {@code confirmed}(사유 코드 SPONSOR_HIDDEN 으로 조치)면 협찬으로 바꾸고
     * 확인, 아니면(무시·다른 사유 조치) 무시. 대기 줄이 없으면 아무것도 하지 않는다. 감사 덧붙임에 사건 ID. 닫았으면 true.
     */
    @Transactional
    public boolean closeFromReport(long postId, boolean confirmed, long operatorId, String ip, long reportCaseId) {
        TrialSponsorReviewEntity row = reviews.findWithLockByPostId(postId).filter(r -> r.getStatus() == SponsorReviewStatus.PENDING).orElse(null);
        if (row == null) {
            return false;
        }
        Instant now = now();
        if (!confirmed) {
            row.decide(SponsorReviewStatus.DISMISSED, operatorId, null, now);
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.SPONSOR_HIDDEN_DISMISS, AuditResult.SUCCESS).target("POST", Long.toString(postId))
                    .with("reportCaseId", Long.toString(reportCaseId)));
            return true;
        }
        PostEntity post = posts.findWithLockById(postId).orElse(null);
        String before = post == null ? null : post.getPurchaseSource();
        if (post != null && post.getTrialParticipantId() == null) {
            post.changePurchaseSource(PurchaseSource.SPONSORED.name());
        }
        row.decide(SponsorReviewStatus.CONFIRMED, operatorId, null, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.SPONSOR_HIDDEN_CONFIRM, AuditResult.SUCCESS).target("POST", Long.toString(postId))
                .with("from", before == null ? "NONE" : before).with("to", post == null || post.getPurchaseSource() == null ? "NONE" : post.getPurchaseSource())
                .with("reportCaseId", Long.toString(reportCaseId)));
        return true;
    }

    /** 협찬 숨김 확인할 일의 글 ID(글자 → 숫자, 틀리면 404 {@code SPONSOR_REVIEW_NOT_FOUND}) */
    public static long postId(String postIdText) {
        return TrialService.id(postIdText, TrialException::sponsorReviewNotFound);
    }

    private TrialSponsorReviewEntity lockPending(String postIdText) {
        TrialSponsorReviewEntity row = reviews.findWithLockByPostId(TrialService.id(postIdText, TrialException::sponsorReviewNotFound))
                .orElseThrow(TrialException::sponsorReviewNotFound);
        if (row.getStatus() != SponsorReviewStatus.PENDING) {
            throw TrialException.sponsorReviewClosed();
        }
        return row;
    }

    private static String clean(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String v = note.strip();
        if (v.length() > 300) {
            throw ValidationException.of("note", new MessageRef("trial.text.length", "300"));
        }
        return v;
    }

    /** 목록 — 대기(최근 신고 차례) · 처리됨(최근 처리 차례) */
    @Transactional(readOnly = true)
    public List<TrialSponsorReviewEntity> list(boolean pending, int limit) {
        return pending ? query.selectFrom(R).where(R.status.eq(SponsorReviewStatus.PENDING)).orderBy(R.lastReportedAt.desc()).limit(limit).fetch()
                : query.selectFrom(R).where(R.status.ne(SponsorReviewStatus.PENDING)).orderBy(R.decidedAt.desc()).limit(limit).fetch();
    }

    /** 보이는 모양 한 줄(확인할 일에서 씀) — 글 제목은 부르는 쪽이 붙인다 */
    public static TrialViews.Ref ref(TrialSponsorReviewEntity row, String title) {
        return new TrialViews.Ref(Long.toString(row.getPostId()), title);
    }
}
