package com.community.withdrawal.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.exception.OperatorAccountLinkedException;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.dto.WithdrawalApplication;
import com.community.withdrawal.dto.WithdrawalApplication.GroupPlan;
import com.community.withdrawal.dto.WithdrawalOutcome;
import com.community.withdrawal.dto.WithdrawalPending;
import com.community.withdrawal.entity.WithdrawalSource;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.withdrawal.exception.MemberAlreadyWithdrawnException;
import com.community.withdrawal.exception.WithdrawalAlreadyScheduledException;
import com.community.withdrawal.exception.WithdrawalNotMemberRequestedException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 탈퇴 신청·취소의 DB 일 — 메서드마다 한 트랜잭션. 회원 본인 신청(api {@code WithdrawalService} 가 입력 검사·본인 확인을 트랜잭션 밖에서 끝낸 뒤)과 운영자 강제 탈퇴·대리 취소
 * (admin {@code MemberWithdrawalAdminService})가 <b>같은 코드</b>를 쓴다(member-withdrawal-contract 「관리자」). 회원 행을 잠가 같은 회원의 동시 요청을 줄 세운다.
 *
 * <ul>
 *   <li>신청: 회원 잠금(진행 중 409, 이미 탈퇴 — 회원이면 401, 운영자면 409 {@code MEMBER_ALREADY_WITHDRAWN} · 운영자 회원 계정으로 이어짐 409 {@code OPERATOR_ACCOUNT_LINKED}) → 남은 일 셈 → 신청 줄(모임장 처리 선택은 저장만) + WITHDRAWING
 *       + 감사(회원 {@code MEMBER_WITHDRAW_REQUEST} · 운영자 {@code MEMBER_WITHDRAW_FORCE}) → 남은 일이 없으면 <b>같은 트랜잭션</b>에서 완료(익명화)까지.</li>
 *   <li>취소: 진행 중일 때만(아니면 409) — 신청 전 상태로 되돌린다. 운영자 대리 취소는 회원이 신청한 것만(강제 탈퇴는 409 {@code WITHDRAWAL_NOT_MEMBER_REQUESTED}).
 *       감사는 둘 다 {@code MEMBER_WITHDRAW_CANCEL}, 운영자 것은 행위자가 운영자이고 {@code requestChannel}·{@code memo} 가 붙는다.</li>
 * </ul>
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
    private final OperatorMemberAccountService operatorAccounts;

    public WithdrawalRequestWriter(WithdrawalQueries queries, WithdrawalEraseStore erase, MemberWithdrawalStore withdrawals, WithdrawalPendingResolver pending,
                                   WithdrawalRejoinPolicy rejoin, WithdrawalCompletionService completion, WithdrawalMailSender mail,
                                   AuditLogService audit, SnowflakeIdGenerator ids, WithdrawalProperties properties, ObjectProvider<Clock> clocks,
                                   OperatorMemberAccountService operatorAccounts) {
        this.operatorAccounts = operatorAccounts;
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
     * 신청 처리 — 회원 잠금 → 남은 일 셈 → 신청 줄 + WITHDRAWING + 감사 → 남은 일이 없으면 같은 트랜잭션에서 완료(익명화)까지.
     *
     * @throws WithdrawalAlreadyScheduledException 이미 진행 중(409)
     * @throws MemberAlreadyWithdrawnException     운영자 강제 탈퇴인데 이미 탈퇴한 회원(409). 회원 본인 신청이면 401 {@code UNAUTHENTICATED}
     */
    @Transactional
    public WithdrawalOutcome apply(WithdrawalApplication app) {
        boolean byOperator = app.source() == WithdrawalSource.OPERATOR;
        long userId = app.userId();
        String status = queries.lockStatus(userId).orElseThrow(() -> byOperator ? new MemberAlreadyWithdrawnException() : new CommonException(ErrorCode.UNAUTHENTICATED));
        if ("WITHDRAWING".equals(status)) {
            throw new WithdrawalAlreadyScheduledException();
        }
        if ("WITHDRAWN".equals(status)) {
            throw byOperator ? new MemberAlreadyWithdrawnException() : new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        // 운영자 회원 계정으로 이어진 동안은 탈퇴할 수 없다(operator-member-account-contract 정책 12) — 회원 행을 잠근 뒤라 연결(같은 잠금)과 줄을 선다
        if (operatorAccounts.isLinked(userId)) {
            throw byOperator ? OperatorAccountLinkedException.byOperator() : OperatorAccountLinkedException.byMember();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long paidPoints = queries.losses(userId, ServiceTime.dateOf(now), now).paidPoints();
        List<WithdrawalPending> remaining = pending.all(userId, app.choices(), paidPoints, now);
        Instant expected = WithdrawalPendingResolver.expectedCompleteAt(remaining).orElse(null);
        boolean hasPaymentHistory = queries.lastTransactionAt(userId).isPresent();
        WithdrawalRejoinPolicy.Rejoin rj = rejoin.of(userId, status, now);
        long withdrawalId = ids.nextId();
        WithdrawalApplication.Force force = app.force();
        withdrawals.insert(new MemberWithdrawalStore.NewRow(withdrawalId, userId, WithdrawalStatus.SCHEDULED, app.source(), status, app.reason(), app.reasonNote(), app.choices(),
                JSON.writeValueAsString(plansJson(app.groupPlans())), app.consent().paidPoints(), app.consent().refundEstimate(), app.consent().bizForfeit(),
                app.consent().consented() ? now : null, JSON.writeValueAsString(pendingJson(remaining)), remaining.size(), expected, rj.until(), hasPaymentHistory, now,
                force == null ? null : new MemberWithdrawalStore.Force(force.reasonKind(), force.memo(), force.operatorId()), app.consent().emoticonEarnings()));
        erase.markWithdrawing(userId, now);

        String result = remaining.isEmpty() ? "COMPLETED" : "SCHEDULED";
        AuditEvent event = byOperator
                ? AuditEvent.byOperator(force.operatorId(), app.ip(), AuditActions.MEMBER_WITHDRAW_FORCE, AuditResult.SUCCESS).with("reasonKind", force.reasonKind())
                        .with("memo", force.memo())
                : AuditEvent.byMember(userId, app.ip(), AuditActions.MEMBER_WITHDRAW_REQUEST, AuditResult.SUCCESS).with("reason", app.reason() == null ? "" : app.reason());
        audit.record(event.target("MEMBER", Long.toString(userId)).with("withdrawalId", Long.toString(withdrawalId)).with("result", result).with("pendingCount", remaining.size())
                .with("paidPointChoice", app.choices().paidPointChoice()).with("deliveredChoice", app.choices().deliveredChoice()).with("bizChoice", app.choices().bizChoice())
                .with("groups", app.groupPlans().size())
                .with("forfeitEmoticonEarnings", app.consent().emoticonEarnings() == null ? 0L : app.consent().emoticonEarnings()));

        if (remaining.isEmpty()) {
            WithdrawalCompletionService.Actor actor = byOperator ? WithdrawalCompletionService.Actor.operator(force.operatorId(), app.ip())
                    : WithdrawalCompletionService.Actor.member(userId, app.ip());
            WithdrawalCompletionService.Completed done = completion.complete(userId, actor).orElseThrow(() -> new IllegalStateException("탈퇴를 완료하지 못했다"));
            log.info("회원 탈퇴 — 즉시 완료 userId={} withdrawalId={} source={}", userId, withdrawalId, app.source());
            return new WithdrawalOutcome(withdrawalId, WithdrawalStatus.COMPLETED, List.of(), null, done.rejoinUntil(), done.hasPaymentHistory(), done.retentionYears(),
                    done.supportEmail(), done.completedAt());
        }
        log.info("회원 탈퇴 — 예약 userId={} withdrawalId={} source={} 남은 일={}건", userId, withdrawalId, app.source(), remaining.size());
        return new WithdrawalOutcome(withdrawalId, WithdrawalStatus.SCHEDULED, remaining, expected, rj.until(), hasPaymentHistory, properties.contactRetentionYears(),
                mail.supportEmail(), null);
    }

    /**
     * 회원 본인 취소 — 진행 중일 때만. 신청 전 상태로 되돌린다(모임장 처리는 완료 때 하므로 소모임은 그대로다).
     *
     * @throws WithdrawalNotScheduledException 진행 중이 아닐 때(409)
     */
    @Transactional
    public void cancel(long userId, String ip) {
        MemberWithdrawalStore.Row row = lockForCancel(userId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        erase.restoreStatus(userId, row.priorStatus(), now);
        withdrawals.cancel(row.id(), now);
        audit.record(AuditEvent.byMember(userId, ip, AuditActions.MEMBER_WITHDRAW_CANCEL, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("withdrawalId", Long.toString(row.id())));
        log.info("회원 탈퇴를 취소했다 userId={} withdrawalId={}", userId, row.id());
    }

    /**
     * 운영자 대리 취소(회원이 고객센터·전화·이메일로 요청) — 회원이 신청한 진행 중 탈퇴만. 결과는 회원 본인 취소와 같다.
     *
     * @throws WithdrawalNotScheduledException      진행 중이 아닐 때(409)
     * @throws WithdrawalNotMemberRequestedException 운영자 강제 탈퇴일 때(409)
     */
    @Transactional
    public void cancelByOperator(long userId, long operatorId, String requestChannel, String memo, String ip) {
        MemberWithdrawalStore.Row row = lockForCancel(userId);
        if (row.source() != WithdrawalSource.MEMBER) {
            throw new WithdrawalNotMemberRequestedException();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        erase.restoreStatus(userId, row.priorStatus(), now);
        withdrawals.cancelByOperator(row.id(), now, new MemberWithdrawalStore.OperatorCancel(requestChannel, memo, operatorId));
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.MEMBER_WITHDRAW_CANCEL, AuditResult.SUCCESS).target("MEMBER", Long.toString(userId))
                .with("withdrawalId", Long.toString(row.id())).with("onBehalf", true).with("requestChannel", requestChannel).with("memo", memo));
        log.info("운영자가 회원 탈퇴를 대리 취소했다 userId={} withdrawalId={} operatorId={} channel={}", userId, row.id(), operatorId, requestChannel);
    }

    private MemberWithdrawalStore.Row lockForCancel(long userId) {
        String status = queries.lockStatus(userId).orElseThrow(WithdrawalNotScheduledException::new);
        if (!"WITHDRAWING".equals(status)) {
            throw new WithdrawalNotScheduledException();
        }
        return withdrawals.lockScheduled(userId).orElseThrow(WithdrawalNotScheduledException::new);
    }

    /** 남은 일 스냅샷({@code pending_snapshot}) — {@link WithdrawalPending} 과 같은 칸 */
    static List<Map<String, Object>> pendingJson(List<WithdrawalPending> pending) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WithdrawalPending p : pending) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.code().name());
            m.put("label", p.label());
            m.put("link", p.link());
            m.put("refId", p.refId());
            m.put("waitUntil", p.waitUntil() == null ? null : p.waitUntil().toString());
            out.add(m);
        }
        return out;
    }

    /** 소모임 선택 저장 모양({@code group_choices}) — {@code [{groupId, action, toMemberId}]}, 완료 때 {@code WithdrawalCompletionService#groupPlans} 가 읽는다 */
    static List<Map<String, Object>> plansJson(Map<Long, GroupPlan> plans) {
        List<Map<String, Object>> out = new ArrayList<>();
        plans.forEach((groupId, plan) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("groupId", Long.toString(groupId));
            m.put("action", plan.action());
            m.put("toMemberId", plan.toMemberId() == null ? null : Long.toString(plan.toMemberId()));
            out.add(m);
        });
        return out;
    }
}
