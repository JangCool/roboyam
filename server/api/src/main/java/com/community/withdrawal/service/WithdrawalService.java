package com.community.withdrawal.service;

import com.community.account.service.CurrentPasswordVerifier;
import com.community.group.entity.GroupEntity;
import com.community.group.service.GroupPeople;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.dto.WithdrawalChoices;
import com.community.withdrawal.dto.WithdrawalPending;
import com.community.withdrawal.dto.request.WithdrawalRequest;
import com.community.withdrawal.dto.response.WithdrawalResult;
import com.community.withdrawal.dto.response.WithdrawalView;
import com.community.withdrawal.exception.WithdrawalAlreadyScheduledException;
import com.community.withdrawal.exception.WithdrawalNotScheduledException;
import com.community.withdrawal.exception.WithdrawalReauthRequiredException;
import com.community.withdrawal.repository.master.MemberWithdrawalStore;
import com.community.withdrawal.repository.master.WithdrawalQueries;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 API(docs/features/member-withdrawal-contract.md, commerce.md 17-1 D1~D6) — 화면({@code GET}) · 신청({@code POST}) · 취소({@code DELETE}).
 *
 * <ul>
 *   <li><b>신청</b>: 입력 검사(400 {@code fields.reason|reasonNote|groupChoices|password|forfeitConsent}) → 본인 확인(비밀번호 계정은 비밀번호 — 비밀번호 변경과 같은 횟수·잠금,
 *       소셜 전용은 최근 {@code reauth-minutes} 안 로그인) → 트랜잭션: 회원 잠금(진행 중이면 409) → 모임장 처리(D6) → 남은 일 셈 → 신청 줄 + WITHDRAWING + 감사 → 남은 일이 없으면
 *       그 자리에서 완료({@link WithdrawalCompletionService} — 배치와 같은 코드), 있으면 SCHEDULED.</li>
 *   <li>포기 동의 금액은 서버 계산과 같아야 한다(다르면 400 {@code fields.forfeitConsent} — 금액이 바뀌었으니 다시 보라).</li>
 *   <li><b>취소</b>: 진행 중일 때만(아니면 409) — 신청 전 상태로 되돌린다. 모임장 처리는 완료 때 하므로 소모임은 그대로다.</li>
 * </ul>
 * 비밀번호 확인(느린 해시)은 트랜잭션 밖이다.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    public static final int REASON_NOTE_MAX = 200;
    /** 충전 환불 수수료율(9-3 — 충전 7일이 지난 묶음의 10%, 사유는 청약철회 기간 경과) */
    public static final int FEE_RATE_PERCENT = 10;
    public static final int FEE_AFTER_DAYS = 7;
    static final Set<String> REASONS = Set.of("NOT_USING", "PRIVACY", "INCONVENIENT", "OTHER_SERVICE", "REJOIN", "OTHER");
    static final Set<String> GROUP_ACTIONS = Set.of("AUTO", "TRANSFER", "DISSOLVE");

    private final WithdrawalQueries queries;
    private final MemberWithdrawalStore withdrawals;
    private final WithdrawalPendingResolver pending;
    private final WithdrawalBusinessInfo business;
    private final WithdrawalGroupHandler groups;
    private final WithdrawalRejoinPolicy rejoin;
    private final WithdrawalRequestWriter writer;
    private final WithdrawalMailSender mail;
    private final GroupPeople people;
    private final UserRepository users;
    private final CurrentPasswordVerifier passwords;
    private final WithdrawalProperties properties;
    private final Clock clock;

    public WithdrawalService(WithdrawalQueries queries, MemberWithdrawalStore withdrawals, WithdrawalPendingResolver pending,
                             WithdrawalBusinessInfo business, WithdrawalGroupHandler groups, WithdrawalRejoinPolicy rejoin, WithdrawalRequestWriter writer,
                             WithdrawalMailSender mail, GroupPeople people, UserRepository users, CurrentPasswordVerifier passwords,
                             WithdrawalProperties properties, ObjectProvider<Clock> clocks) {
        this.queries = queries;
        this.withdrawals = withdrawals;
        this.pending = pending;
        this.business = business;
        this.groups = groups;
        this.rejoin = rejoin;
        this.writer = writer;
        this.mail = mail;
        this.people = people;
        this.users = users;
        this.passwords = passwords;
        this.properties = properties;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 화면 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WithdrawalView view(long userId) {
        WithdrawalQueries.Member member = requireMember(userId);
        Instant now = clock.instant();
        WithdrawalQueries.Losses losses = queries.losses(userId, ServiceTime.dateOf(now), now);
        int[] counts = queries.contentCounts(userId);
        boolean hasPaymentHistory = queries.lastTransactionAt(userId).isPresent();
        WithdrawalBusinessInfo.Info biz = business.of(userId, now);
        Optional<MemberWithdrawalStore.Row> scheduled = "WITHDRAWING".equals(member.status()) ? withdrawals.findScheduled(userId) : Optional.empty();
        String priorStatus = scheduled.map(MemberWithdrawalStore.Row::priorStatus).orElse(member.status());
        WithdrawalRejoinPolicy.Rejoin rj = rejoin.of(userId, priorStatus, now);

        WithdrawalView.Scheduled scheduledView = null;
        if (scheduled.isPresent()) {
            List<WithdrawalPending> remaining = pending.all(userId, scheduled.get().choices(), losses.paidPoints(), now);
            scheduledView = new WithdrawalView.Scheduled(scheduled.get().requestedAt(), WithdrawalPendingResolver.expectedCompleteAt(remaining).orElse(null), items(remaining));
        }
        return new WithdrawalView(scheduled.isPresent() ? "SCHEDULED" : "NONE",
                new WithdrawalView.Losses(losses.freePoints(), losses.items(), losses.refundableItems(), losses.gradeName(), losses.attendanceStreak()),
                money(userId, losses.paidPoints(), now),
                new WithdrawalView.Remains(counts[0], counts[1], hasPaymentHistory, properties.contactRetentionYears()),
                items(pending.fixed(userId)),
                queries.deliveredWithin(userId, WithdrawalPendingResolver.RETURN_WINDOW_DAYS, now).stream().map(o -> new WithdrawalView.DeliveredOrder(o.orderNo(),
                        "/me/orders/" + o.orderNo(), o.deliveredAt(), o.deliveredAt().plus(Duration.ofDays(WithdrawalPendingResolver.RETURN_WINDOW_DAYS)))).toList(),
                biz.hasRunning() ? new WithdrawalView.Business(biz.running().stream().map(r -> new WithdrawalView.BizRunning(Long.toString(r.id()), r.kind(), r.title(),
                        "/business/applications/" + r.id(), r.endsAt(), r.forfeitAmount())).toList(), biz.forfeitTotal(), biz.refundWaitingTotal()) : null,
                groupsLed(userId),
                new WithdrawalView.Rejoin(rj.until(), rj.extendedByRestriction(), rj.restrictionEndsAt(), rj.restrictionReason()),
                new WithdrawalView.Verification(member.hasPassword() ? "PASSWORD" : "RECENT_LOGIN", recentLoginOk(member, now)),
                mail.supportEmail(), scheduledView);
    }

    private WithdrawalView.Money money(long userId, long paidPoints, Instant now) {
        if (paidPoints <= 0) {
            return null;
        }
        long feeBase = queries.paidLotsOlderThan(userId, FEE_AFTER_DAYS, now);
        long fee = Math.floorDiv(feeBase * FEE_RATE_PERCENT, 100);
        return new WithdrawalView.Money(paidPoints, paidPoints - fee, fee, feeBase, FEE_RATE_PERCENT);
    }

    private List<WithdrawalView.GroupLed> groupsLed(long userId) {
        List<WithdrawalView.GroupLed> out = new ArrayList<>();
        for (GroupEntity group : groups.led(userId)) {
            List<WithdrawalGroupHandler.Candidate> candidates = groups.candidates(group, userId);
            GroupPeople.Members persons = people.members(candidates.stream().map(WithdrawalGroupHandler.Candidate::userId).toList(), false);
            List<WithdrawalView.GroupCandidate> views = candidates.stream()
                    .map(c -> new WithdrawalView.GroupCandidate(Long.toString(c.userId()), persons.nickname(c.userId()), c.role().name())).toList();
            out.add(new WithdrawalView.GroupLed(Long.toString(group.getId()), group.getName(), "/groups/" + group.getId(), group.getMemberCount(), views,
                    views.isEmpty() ? null : views.get(0)));
        }
        return out;
    }

    private boolean recentLoginOk(WithdrawalQueries.Member member, Instant now) {
        return member.lastLoginAt() != null && !member.lastLoginAt().plus(Duration.ofMinutes(properties.reauthMinutes())).isBefore(now);
    }

    // ── 신청 ─────────────────────────────────────────────────────────────────

    /**
     * 탈퇴 신청 — 검사·본인 확인은 트랜잭션 밖, 처리는 {@link #apply}.
     *
     * @throws ValidationException                 입력 위반
     * @throws WithdrawalReauthRequiredException   소셜 전용 회원이 최근에 로그인하지 않았을 때(401)
     * @throws com.community.account.exception.AccountCurrentPasswordWrongException 비밀번호가 틀렸을 때(401)
     * @throws com.community.auth.exception.AuthLoginLockedException             비밀번호를 여러 번 틀려 잠겼을 때(429)
     * @throws WithdrawalAlreadyScheduledException 이미 진행 중(409)
     */
    public WithdrawalResult request(long userId, WithdrawalRequest body, String ip) {
        WithdrawalRequest r = body == null ? new WithdrawalRequest(null, null, null, null, null, null, null, null) : body;
        UserEntity user = users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        WithdrawalQueries.Member member = requireMember(userId);
        if ("WITHDRAWING".equals(member.status())) {
            throw new WithdrawalAlreadyScheduledException();
        }
        Instant now = clock.instant();
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String reason = r.reason() == null || r.reason().isBlank() ? null : r.reason().strip();
        if (reason != null && !REASONS.contains(reason)) {
            errors.put("reason", new MessageRef("withdrawal.reason.invalid"));
        }
        String note = normalizeNote(r.reasonNote());
        if (note != null && note.codePointCount(0, note.length()) > REASON_NOTE_MAX) {
            errors.put("reasonNote", new MessageRef("withdrawal.reasonNote.max", REASON_NOTE_MAX));
        }
        List<GroupEntity> led = groups.led(userId);
        Map<Long, GroupPlan> plans = groupPlans(led, r.groupChoices(), userId, errors);
        if (user.hasPassword() && (r.password() == null || r.password().isEmpty())) {
            errors.put("password", new MessageRef("withdrawal.password.required"));
        }
        WithdrawalChoices choices = new WithdrawalChoices(r.paidPointChoice(), r.deliveredChoice(), r.bizChoice());
        WithdrawalQueries.Losses losses = queries.losses(userId, ServiceTime.dateOf(now), now);
        WithdrawalBusinessInfo.Info biz = business.of(userId, now);
        Consent consent = consent(r, choices, losses.paidPoints(), money(userId, losses.paidPoints(), now), biz, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // 본인 확인 — 비밀번호 계정은 비밀번호(비밀번호 변경과 같은 횟수·잠금), 소셜 전용은 최근 로그인
        if (user.hasPassword()) {
            passwords.verify(user, r.password());
        } else if (!recentLoginOk(member, now)) {
            throw new WithdrawalReauthRequiredException();
        }
        return writer.apply(userId, reason, note, choices, plans, consent, ip);
    }

    // ── 취소 ─────────────────────────────────────────────────────────────────

    /** @throws WithdrawalNotScheduledException 진행 중이 아닐 때(409) */
    public void cancel(long userId, String ip) {
        writer.cancel(userId, ip);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 소모임 하나의 처리 */
    public record GroupPlan(String action, Long toMemberId) {
    }

    /** 포기 동의 — 검사한 금액(동의가 필요 없으면 null) */
    public record Consent(Long paidPoints, Long refundEstimate, Long bizForfeit) {

        public boolean consented() {
            return paidPoints != null || bizForfeit != null;
        }
    }

    private WithdrawalQueries.Member requireMember(long userId) {
        return queries.member(userId).filter(m -> !"WITHDRAWN".equals(m.status())).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }

    /** 모임마다 처리 계획 — 빠진 모임은 AUTO. 모르는 모임·동작·후보는 {@code groupChoices} 위반 */
    private Map<Long, GroupPlan> groupPlans(List<GroupEntity> led, List<WithdrawalRequest.GroupChoice> choices, long me, Map<String, MessageRef> errors) {
        Map<Long, GroupPlan> plans = new LinkedHashMap<>();
        led.forEach(g -> plans.put(g.getId(), new GroupPlan("AUTO", null)));
        if (choices == null) {
            return plans;
        }
        for (WithdrawalRequest.GroupChoice choice : choices) {
            Long groupId = parseLong(choice == null ? null : choice.groupId());
            if (groupId == null || !plans.containsKey(groupId) || choice.action() == null || !GROUP_ACTIONS.contains(choice.action())) {
                errors.put("groupChoices", new MessageRef("withdrawal.groupChoices.invalid"));
                return plans;
            }
            if ("TRANSFER".equals(choice.action())) {
                Long target = parseLong(choice.toMemberId());
                GroupEntity group = led.stream().filter(g -> g.getId().equals(groupId)).findFirst().orElseThrow();
                if (target == null || groups.candidates(group, me).stream().noneMatch(c -> c.userId() == target)) {
                    errors.put("groupChoices", new MessageRef("withdrawal.groupChoices.candidate"));
                    return plans;
                }
                plans.put(groupId, new GroupPlan("TRANSFER", target));
            } else {
                plans.put(groupId, new GroupPlan(choice.action(), null));
            }
        }
        return plans;
    }

    /** 포기 동의 금액 검사 — 포기를 골랐고 포기할 것이 있으면 금액이 서버 계산과 같아야 한다 */
    private static Consent consent(WithdrawalRequest r, WithdrawalChoices choices, long paidPoints, WithdrawalView.Money money, WithdrawalBusinessInfo.Info biz,
                                   Map<String, MessageRef> errors) {
        WithdrawalRequest.ForfeitConsent c = r.forfeitConsent();
        Long consentPaid = null;
        Long consentRefund = null;
        Long consentBiz = null;
        if (choices.forfeitPaidPoints() && paidPoints > 0) {
            if (c == null || !Objects.equals(c.paidPoints(), paidPoints) || !Objects.equals(c.refundEstimate(), money.refundEstimate())) {
                errors.put("forfeitConsent", new MessageRef("withdrawal.forfeitConsent.mismatch"));
            }
            consentPaid = paidPoints;
            consentRefund = money.refundEstimate();
        }
        if (choices.forfeitBiz() && biz.hasRunning()) {
            if (c == null || !Objects.equals(c.bizForfeit(), biz.forfeitTotal())) {
                errors.put("forfeitConsent", new MessageRef("withdrawal.forfeitConsent.mismatch"));
            }
            consentBiz = biz.forfeitTotal();
        }
        return new Consent(consentPaid, consentRefund, consentBiz);
    }

    private static String normalizeNote(String raw) {
        if (raw == null) {
            return null;
        }
        String note = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        return note.isEmpty() ? null : note;
    }

    private static Long parseLong(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,19}")) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static List<WithdrawalView.PendingItem> items(List<WithdrawalPending> pending) {
        return pending.stream().map(p -> new WithdrawalView.PendingItem(p.code().name(), p.label(), p.link(), p.waitUntil())).toList();
    }

    static List<Map<String, Object>> pendingJson(List<WithdrawalPending> pending) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WithdrawalPending p : pending) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.code().name());
            m.put("label", p.label());
            m.put("link", p.link());
            m.put("waitUntil", p.waitUntil() == null ? null : p.waitUntil().toString());
            out.add(m);
        }
        return out;
    }

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
