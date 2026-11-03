package com.community.withdrawal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.dto.WithdrawalChoices;
import com.community.withdrawal.dto.WithdrawalPending;
import com.community.withdrawal.dto.response.WithdrawalResult;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.withdrawal.exception.WithdrawalAlreadyScheduledException;
import com.community.withdrawal.exception.WithdrawalNotScheduledException;
import com.community.withdrawal.repository.master.MemberWithdrawalStore;
import com.community.withdrawal.repository.master.WithdrawalEraseStore;
import com.community.withdrawal.repository.master.WithdrawalQueries;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 탈퇴 신청·취소의 DB 일 — 메서드마다 한 트랜잭션({@link WithdrawalService} 가 입력 검사·본인 확인을 트랜잭션 밖에서 끝낸 뒤 부른다). 회원 행을 잠가 같은 회원의 동시 요청을 줄 세운다.
 */
@Service
public class WithdrawalRequestWriter {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalRequestWriter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WithdrawalQueries queries;
    private final WithdrawalEraseStore erase;
    private final MemberWithdrawalStore withdrawals;
    private final WithdrawalPendingResolver pending;

    private final WithdrawalRejoinPolicy rejoin;
    private final WithdrawalCompletionService completion;
    private final WithdrawalMailSender mail;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final WithdrawalProperties properties;
    private final Clock clock;

    public WithdrawalRequestWriter(WithdrawalQueries queries, WithdrawalEraseStore erase, MemberWithdrawalStore withdrawals, WithdrawalPendingResolver pending,
                                   WithdrawalRejoinPolicy rejoin, WithdrawalCompletionService completion, WithdrawalMailSender mail,
                                   AuditLogService audit, SnowflakeIdGenerator ids, WithdrawalProperties properties, ObjectProvider<Clock> clocks) {
        this.queries = queries;
        this.erase = erase;
        this.withdrawals = withdrawals;
        this.pending = pending;
        this.rejoin = rejoin;
        this.completion = completion;
        this.mail = mail;
        this.audit = audit;
        this.ids = ids;
        this.properties = properties;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 신청 처리 — 회원 잠금(진행 중 409) → 남은 일 셈 → 신청 줄(모임장 처리 선택은 저장만 — 실제 넘기기·해산은 완료 때) + WITHDRAWING + 감사 → 남은 일이 없으면 <b>같은 트랜잭션</b>에서 완료(익명화)까지.
     *
     * @throws WithdrawalAlreadyScheduledException 이미 진행 중(409)
     */
    @Transactional
    public WithdrawalResult apply(long userId, String reason, String note, WithdrawalChoices choices, Map<Long, WithdrawalService.GroupPlan> plans,
                                  WithdrawalService.Consent consent, String ip) {
        String status = queries.lockStatus(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if ("WITHDRAWING".equals(status)) {
            throw new WithdrawalAlreadyScheduledException();
        }
        if ("WITHDRAWN".equals(status)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long paidPoints = queries.losses(userId, ServiceTime.dateOf(now), now).paidPoints();
        List<WithdrawalPending> remaining = pending.all(userId, choices, paidPoints, now);
        Instant expected = WithdrawalPendingResolver.expectedCompleteAt(remaining).orElse(null);
        boolean hasPaymentHistory = queries.lastTransactionAt(userId).isPresent();
        WithdrawalRejoinPolicy.Rejoin rj = rejoin.of(userId, status, now);
        long withdrawalId = ids.nextId();
        withdrawals.insert(new MemberWithdrawalStore.NewRow(withdrawalId, userId, WithdrawalStatus.SCHEDULED, status, reason, note, choices,
                JSON.writeValueAsString(WithdrawalService.plansJson(plans)), consent.paidPoints(), consent.refundEstimate(), consent.bizForfeit(),
                consent.consented() ? now : null, JSON.writeValueAsString(WithdrawalService.pendingJson(remaining)), remaining.size(), expected, rj.until(),
                hasPaymentHistory, now));
        erase.markWithdrawing(userId, now);
        audit.record(AuditEvent.byMember(userId, ip, AuditActions.MEMBER_WITHDRAW_REQUEST, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("withdrawalId", Long.toString(withdrawalId)).with("result", remaining.isEmpty() ? "COMPLETED" : "SCHEDULED").with("pendingCount", remaining.size())
                .with("reason", reason == null ? "" : reason).with("paidPointChoice", choices.paidPointChoice()).with("deliveredChoice", choices.deliveredChoice())
                .with("bizChoice", choices.bizChoice()).with("groups", plans.size()));
        if (remaining.isEmpty()) {
            WithdrawalCompletionService.Completed done = completion.complete(userId, true, ip).orElseThrow(() -> new IllegalStateException("탈퇴를 완료하지 못했다"));
            log.info("회원 탈퇴 — 즉시 완료 userId={} withdrawalId={}", userId, withdrawalId);
            return new WithdrawalResult("COMPLETED", List.of(), null, done.rejoinUntil(), done.hasPaymentHistory(), done.retentionYears(), done.supportEmail());
        }
        log.info("회원 탈퇴 — 예약 userId={} withdrawalId={} 남은 일={}건", userId, withdrawalId, remaining.size());
        return new WithdrawalResult("SCHEDULED", WithdrawalService.items(remaining), expected, rj.until(), hasPaymentHistory, properties.contactRetentionYears(),
                mail.supportEmail());
    }

    /**
     * 취소 — 진행 중일 때만. 신청 전 상태로 되돌린다(모임장 처리는 완료 때 하므로 소모임은 그대로다).
     *
     * @throws WithdrawalNotScheduledException 진행 중이 아닐 때(409)
     */
    @Transactional
    public void cancel(long userId, String ip) {
        String status = queries.lockStatus(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (!"WITHDRAWING".equals(status)) {
            throw new WithdrawalNotScheduledException();
        }
        MemberWithdrawalStore.Row row = withdrawals.lockScheduled(userId).orElseThrow(WithdrawalNotScheduledException::new);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        erase.restoreStatus(userId, row.priorStatus(), now);
        withdrawals.cancel(row.id(), now);
        audit.record(AuditEvent.byMember(userId, ip, AuditActions.MEMBER_WITHDRAW_CANCEL, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("withdrawalId", Long.toString(row.id())));
        log.info("회원 탈퇴를 취소했다 userId={} withdrawalId={}", userId, row.id());
    }
}
