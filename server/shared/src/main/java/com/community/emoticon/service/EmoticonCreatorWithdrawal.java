package com.community.emoticon.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.EmoticonReviewKind;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.StoppedRow;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonReviewStore;
import com.community.emoticon.repository.master.EmoticonReviewStore.ClosedRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때의 로보티콘 작가 팩(후속 결정 6 — 사용자 결정 2026-11-05 「탈퇴 작가의 팩은 새 판매만 멈춰」). 탈퇴 <b>완료</b> 트랜잭션 안에서
 * {@code WithdrawalCompletionService} 가 부른다({@code DealWithdrawal}·{@code BusinessWithdrawal} 과 같은 자리 — 회원 신청 즉시 완료 · 배치 자동 완료 · 운영자 강제 탈퇴 모두 같은 길).
 * 탈퇴 진행 중(WITHDRAWING)에는 팩을 건드리지 않는다 — 진행 중에 취소하면 그대로 판매 중이다(모임장 처리와 같은 까닭).
 *
 * <ol>
 *   <li><b>대기 심사 건</b>을 반려로 닫는다(처리한 운영자 없음 · {@code system_cause CREATOR_WITHDRAWN} · 사유 {@link #REVIEW_REASON}). 처음 신청이면 팩도 반려(상점에 나오지 않는다),
 *       새 판이면 상점 판 그대로. 심사 건 → 팩 차례로 잠근다(승인·반려와 같은 차례).</li>
 *   <li><b>상점에 나온 팩</b>(판매 중 · 운영자 판매 중단)을 작가 탈퇴 판매 중단({@code suspend_kind CREATOR_WITHDRAWN}, 사유 {@link #PACK_REASON}, 멈춘 운영자 없음)으로 바꾼다 —
 *       새로 사기·받기는 판매 중단과 같이 {@code 409 EMOTICON_PACK_SUSPENDED}, 이미 가진 사람은 계속 쓴다(카탈로그에 남는다 · 셀프 환불 그대로), 결제창에 있던 구매의 완료는
 *       처리기의 「판매 중 아님」 거절 → 자동 취소. 다시 판매·가격 바꾸기·새 판 승인은 {@code 409 EMOTICON_PACK_CREATOR_WITHDRAWN}(DB 트리거가 마지막 그물).</li>
 *   <li>감사(시스템): 팩마다 {@code EMOTICON_PACK_CREATOR_WITHDRAWN} · 닫은 심사 건마다 {@code EMOTICON_REVIEW_REJECT}({@code cause CREATOR_WITHDRAWN}). 회원 알림은 보내지 않는다
 *       (탈퇴한 사람 — 알림 행도 탈퇴 정리에서 지운다).</li>
 * </ol>
 * 남은 정산은 {@link EmoticonSettlementCalculator} 가 탈퇴 작가의 최소 지급액을 없애 다음 확정 때 모두 지급 대상으로 만든다({@link #withdraw} 는 정산을 건드리지 않는다).
 *
 * <p><b>정산 정보 없이 수익이 남은 작가</b>(탈퇴 작가 미확정 정리 2026-11-05, V148): 탈퇴 화면의 작가 요약({@link #summary}) · 탈퇴 완료 트랜잭션의 정산 정리({@link #closeSettlement} —
 * 회원 동의면 포기, 동의가 없으면(운영자 강제 탈퇴 포함) 시스템 보류 — {@link EmoticonSettlementService#closeForWithdrawal}).
 */
@Service
public class EmoticonCreatorWithdrawal {

    private static final Logger log = LoggerFactory.getLogger(EmoticonCreatorWithdrawal.class);

    /** 작가 탈퇴로 멈춘 팩의 판매 중단 사유(DB 칸이 5~200자를 요구한다 — 화면은 {@code suspendKind} 로 문구를 고른다) */
    public static final String PACK_REASON = "작가 탈퇴로 새 판매를 멈췄어요(시스템 처리).";
    /** 작가 탈퇴로 닫은 심사 건의 반려 사유(화면은 {@code decision.cause} 로 문구를 고른다) */
    public static final String REVIEW_REASON = "작가 탈퇴로 심사를 닫았어요(시스템 처리).";

    /** 결과 — 멈춘 팩 수 · 닫은 심사 건 수 */
    public record Result(int stoppedPacks, int closedReviews) {
    }

    /**
     * 탈퇴 화면의 작가 요약 — 작가(만든 팩이 하나라도 있거나 정산 정보를 등록함)가 아니면 {@link #summary} 가 비어 있다.
     *
     * @param onSalePacks             판매 중인 내 팩 수(탈퇴하면 새 판매가 멈춘다)
     * @param pendingReviews          심사 대기 건 수(탈퇴하면 반려로 닫힌다)
     * @param unpaidEarnings          아직 지급되지 않은 수익(원천징수 전, 원 — 예정·확정·보류 + 넘긴 금액, 양수만 · 탈퇴 작가 규칙 = 최소 지급액 없음)
     * @param payoutAccountRegistered 정산 정보 등록 여부
     */
    public record Summary(int onSalePacks, int pendingReviews, long unpaidEarnings, boolean payoutAccountRegistered) {

        /** 탈퇴 전에 정할 것이 있다 — 지급할 수익이 있는데 정산 정보가 없다(등록하거나 포기에 동의해야 탈퇴가 된다) */
        public boolean decisionRequired() {
            return unpaidEarnings > 0 && !payoutAccountRegistered;
        }
    }

    private final EmoticonPackStore packs;
    private final EmoticonReviewStore reviews;
    private final EmoticonPayoutStore payouts;
    private final EmoticonSettlementService settlements;
    private final AuditLogService audit;
    private final Clock clock;

    public EmoticonCreatorWithdrawal(EmoticonPackStore packs, EmoticonReviewStore reviews, EmoticonPayoutStore payouts, EmoticonSettlementService settlements,
                                     AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.packs = packs;
        this.reviews = reviews;
        this.payouts = payouts;
        this.settlements = settlements;
        this.audit = audit;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 탈퇴 화면·탈퇴 신청의 작가 요약 — 작가가 아니면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<Summary> summary(long userId) {
        List<EmoticonPackStore.PackRow> mine = packs.byCreator(userId);
        boolean registered = payouts.registered(userId);
        if (mine.isEmpty() && !registered) {
            return Optional.empty();
        }
        int onSale = (int) mine.stream().filter(p -> p.status() == EmoticonPackStatus.APPROVED).count();
        return Optional.of(new Summary(onSale, reviews.pendingCount(userId), settlements.unpaidForWithdrawal(userId), registered));
    }

    /**
     * 탈퇴 완료 트랜잭션의 정산 정리({@link EmoticonSettlementService#closeForWithdrawal}) — 남은 수익이 없거나 정산 정보가 있으면 그대로, 정산 정보 없이 남았으면 회원 동의(금액 이하)면 포기 ·
     * 아니면 시스템 보류. 작가가 아니면 할 일이 없다.
     */
    @Transactional
    public EmoticonSettlementService.WithdrawalClosure closeSettlement(long creatorId, Long consentedAmount, Instant consentedAt) {
        EmoticonSettlementService.WithdrawalClosure closure = settlements.closeForWithdrawal(creatorId, consentedAmount, consentedAt);
        if (!"NONE".equals(closure.outcome())) {
            log.info("탈퇴 작가의 로보티콘 정산을 정리했다 creatorId={} 결과={} 금액={}", creatorId, closure.outcome(), closure.amount());
        }
        return closure;
    }

    @Transactional
    public Result withdraw(long creatorId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<ClosedRow> closed = reviews.closeForCreatorWithdrawal(creatorId, REVIEW_REASON, now);
        for (ClosedRow r : closed) {
            if (r.kind() == EmoticonReviewKind.FIRST) {
                packs.lock(r.packId()).filter(p -> p.status() == EmoticonPackStatus.PENDING).ifPresent(p -> packs.setStatus(p.id(), EmoticonPackStatus.REJECTED, now));
            } else {
                packs.touch(r.packId(), now);
            }
            audit.record(system(AuditActions.EMOTICON_REVIEW_REJECT, EmoticonReviewService.TARGET, r.id()).with("packId", Long.toString(r.packId()))
                    .with("kind", r.kind().name()).with("reason", REVIEW_REASON).with("cause", EmoticonReviewStore.CAUSE_CREATOR_WITHDRAWN));
        }
        List<StoppedRow> stopped = packs.stopForCreatorWithdrawal(creatorId, PACK_REASON, now);
        for (StoppedRow s : stopped) {
            AuditEvent event = system(AuditActions.EMOTICON_PACK_CREATOR_WITHDRAWN, EmoticonPackAdminService.TARGET, s.id()).with("creator", Long.toString(creatorId))
                    .with("from", s.previousStatus().name());
            if (s.previousKind() != null) {
                event = event.with("fromKind", s.previousKind().name()).with("fromReason", s.previousReason());
            }
            audit.record(event);
        }
        if (!closed.isEmpty() || !stopped.isEmpty()) {
            log.info("탈퇴 작가의 로보티콘 팩 판매를 멈췄다 creatorId={} 멈춘팩={} 닫은심사={}", creatorId, stopped.size(), closed.size());
        }
        return new Result(stopped.size(), closed.size());
    }

    private static AuditEvent system(String action, String targetType, long targetId) {
        return new AuditEvent(AuditActorType.SYSTEM, null, action, AuditResult.SUCCESS, targetType, Long.toString(targetId), null, Map.of());
    }
}
