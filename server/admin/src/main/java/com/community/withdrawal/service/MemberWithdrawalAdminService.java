package com.community.withdrawal.service;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.emoticon.service.EmoticonCreatorWithdrawal;
import com.community.group.entity.GroupEntity;
import com.community.member.service.MemberLookup;
import com.community.permission.dto.AdminActor;
import com.community.withdrawal.config.WithdrawalProperties;
import com.community.withdrawal.dto.MemberWithdrawalAdminRequest;
import com.community.withdrawal.dto.MemberWithdrawalAdminView;
import com.community.withdrawal.dto.MemberWithdrawalAdminView.Detail;
import com.community.withdrawal.dto.MemberWithdrawalAdminView.Event;
import com.community.withdrawal.dto.MemberWithdrawalAdminView.PendingItem;
import com.community.withdrawal.dto.MemberWithdrawalAdminView.Row;
import com.community.withdrawal.dto.WithdrawalApplication;
import com.community.withdrawal.dto.WithdrawalApplication.GroupPlan;
import com.community.withdrawal.dto.WithdrawalChoices;
import com.community.withdrawal.dto.WithdrawalOutcome;
import com.community.withdrawal.dto.WithdrawalPending;
import com.community.withdrawal.entity.WithdrawalPendingCode;
import com.community.withdrawal.entity.WithdrawalSource;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.withdrawal.exception.MemberAlreadyWithdrawnException;
import com.community.withdrawal.exception.WithdrawalAlreadyScheduledException;
import com.community.withdrawal.exception.WithdrawalNotFoundException;
import com.community.withdrawal.exception.WithdrawalNotMemberRequestedException;
import com.community.withdrawal.exception.WithdrawalNotScheduledException;
import com.community.withdrawal.repository.master.MemberWithdrawalAdminStore;
import com.community.withdrawal.repository.master.MemberWithdrawalAdminStore.Criteria;
import com.community.withdrawal.repository.master.MemberWithdrawalAdminStore.Listed;
import com.community.withdrawal.repository.master.MemberWithdrawalStore;
import com.community.withdrawal.repository.master.WithdrawalQueries;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 탈퇴 관리(admin-web {@code /members/withdrawals} · 회원 상세 탈퇴 블록, API {@code /api/admin/member-withdrawals} · {@code /api/admin/members/{id}/withdrawal}, member-withdrawal-contract 「관리자」).
 *
 * <ul>
 *   <li><b>읽기</b>: 목록(상태 탭·회원 번호·신청일·완료일 기간·정렬·쪽) · 요약 4칸(서비스 하루 기준) · 사유 분포(신청일 기간, 상태 무관) · 상세(진행 중 건은 남은 일을 지금 다시 센다) ·
 *       회원별 최근 기록. 완료 건의 닉네임은 null(익명화). 처리 이력은 신청 줄의 칸(신청·강제·진행 안내·완료·취소·대리 취소 시각과 운영자)에서 조립한다 — 별도 표 없음.</li>
 *   <li><b>강제 탈퇴</b>: 사유 종류 + 운영 메모 5~500자 → shared {@link WithdrawalRequestWriter} 를 회원 탈퇴와 같은 코드로 부른다(선택값은 회원에게 유리한 기본값으로 고정, 본인 확인 없음,
 *       모임장 소모임은 모두 자동, 남은 일이 있으면 운영자도 건너뛰지 못하고 진행 중). 완료면 완료 트랜잭션이 회원 세션을 모두 끊는다. 감사 {@code MEMBER_WITHDRAW_FORCE}.</li>
 *   <li><b>대리 취소</b>: 회원이 신청한 진행 중 탈퇴만(강제 건 409 {@code WITHDRAWAL_NOT_MEMBER_REQUESTED}, 끝난 건 409 {@code WITHDRAWAL_NOT_SCHEDULED}) — 결과는 회원 본인 취소와 같다.
 *       감사 {@code MEMBER_WITHDRAW_CANCEL}(행위자 운영자 · 경로 · 메모).</li>
 * </ul>
 * 권한(보기 {@code member.read} · 강제 탈퇴·대리 취소·미리보기 {@code member.withdraw})은 컨트롤러 선언이 정한다.
 */
@Service
public class MemberWithdrawalAdminService {

    private static final Logger log = LoggerFactory.getLogger(MemberWithdrawalAdminService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static final int MEMO_MIN = 5;
    public static final int MEMO_MAX = 500;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;
    static final Set<String> FORCE_REASON_KINDS = Set.of("UNDER_14", "POLICY_VIOLATION", "ON_BEHALF", "OTHER");
    static final Set<String> CANCEL_CHANNELS = Set.of("CS_INQUIRY", "PHONE", "EMAIL", "OTHER");
    static final List<String> REASONS = List.of("NOT_USING", "PRIVACY", "INCONVENIENT", "OTHER_SERVICE", "REJOIN", "OTHER");

    private final MemberWithdrawalAdminStore store;
    private final MemberWithdrawalStore withdrawals;
    private final WithdrawalQueries queries;
    private final WithdrawalPendingResolver pending;
    private final WithdrawalBusinessInfo business;
    private final WithdrawalGroupHandler groups;
    private final WithdrawalRejoinPolicy rejoin;
    private final WithdrawalRequestWriter writer;
    private final MemberLookup members;
    private final WithdrawalProperties properties;
    private final EmoticonCreatorWithdrawal emoticons;
    private final Clock clock;

    public MemberWithdrawalAdminService(MemberWithdrawalAdminStore store, MemberWithdrawalStore withdrawals, WithdrawalQueries queries, WithdrawalPendingResolver pending,
                                        WithdrawalBusinessInfo business, WithdrawalGroupHandler groups, WithdrawalRejoinPolicy rejoin, WithdrawalRequestWriter writer,
                                        MemberLookup members, WithdrawalProperties properties, EmoticonCreatorWithdrawal emoticons, ObjectProvider<Clock> clocks) {
        this.emoticons = emoticons;
        this.store = store;
        this.withdrawals = withdrawals;
        this.queries = queries;
        this.pending = pending;
        this.business = business;
        this.groups = groups;
        this.rejoin = rejoin;
        this.writer = writer;
        this.members = members;
        this.properties = properties;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 목록·요약·분포 ────────────────────────────────────────────────────────

    /**
     * 목록 — 상태 {@code SCHEDULED·COMPLETED·CANCELED} · 회원 번호(정확히) · 신청일·완료일 기간(UTC 순간, [from, to)) · 정렬 {@code <칸>,<asc|desc>}(기본 {@code requestedAt,desc}) ·
     * 쪽 0부터 · 크기 1~100(기본 20). 틀리면 400 {@code fields.<칸>}.
     */
    @Transactional(readOnly = true)
    public MemberWithdrawalAdminView.Page list(String status, String memberNo, String requestedFrom, String requestedTo, String completedFrom, String completedTo, String sort,
                                               String page, String size) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        WithdrawalStatus st = blank(status) ? null : parse(errors, "status", status, s -> WithdrawalStatus.valueOf(s.strip()));
        Long userId = blank(memberNo) ? null : parse(errors, "memberNo", memberNo, s -> Long.parseLong(s.strip()));
        Instant rf = instant(errors, "requestedFrom", requestedFrom);
        Instant rt = instant(errors, "requestedTo", requestedTo);
        Instant cf = instant(errors, "completedFrom", completedFrom);
        Instant ct = instant(errors, "completedTo", completedTo);
        if (rf != null && rt != null && rt.isBefore(rf)) {
            errors.put("requestedTo", new MessageRef("withdrawalAdmin.query.reversed"));
        }
        if (cf != null && ct != null && ct.isBefore(cf)) {
            errors.put("completedTo", new MessageRef("withdrawalAdmin.query.reversed"));
        }
        String[] sorting = sort(errors, sort);
        int pageNo = number(errors, "page", page, 0, 0, Integer.MAX_VALUE);
        int pageSize = number(errors, "size", size, DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Criteria criteria = new Criteria(st, userId, rf, rt, cf, ct, sorting[0], "asc".equals(sorting[1]), pageNo, pageSize);
        List<Row> rows = store.search(criteria).stream().map(this::row).toList();
        return new MemberWithdrawalAdminView.Page(rows, store.count(criteria), pageNo, pageSize);
    }

    /** 요약 4칸 — 오늘·이번 달은 서비스 시간대(한국) 하루 기준, 10일은 {@code progress-notice-days} */
    @Transactional(readOnly = true)
    public MemberWithdrawalAdminView.Summary summary() {
        Instant now = clock.instant();
        LocalDate today = ServiceTime.dateOf(now);
        long[] counts = store.summary(DashboardGadgetQuery.startOf(today), DashboardGadgetQuery.startOf(today.withDayOfMonth(1)),
                now.minus(Duration.ofDays(properties.progressNoticeDays())));
        return new MemberWithdrawalAdminView.Summary(counts[0], counts[1], counts[2], counts[3]);
    }

    /** 사유 분포 — 6종 차례 + 고르지 않음(null) 마지막, 0건도 싣는다. 신청일 기간([from, to), 상태 무관) */
    @Transactional(readOnly = true)
    public MemberWithdrawalAdminView.ReasonStats reasons(String requestedFrom, String requestedTo) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        Instant from = instant(errors, "requestedFrom", requestedFrom);
        Instant to = instant(errors, "requestedTo", requestedTo);
        if (from != null && to != null && to.isBefore(from)) {
            errors.put("requestedTo", new MessageRef("withdrawalAdmin.query.reversed"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Map<String, Long> counts = store.reasonCounts(from, to);
        List<MemberWithdrawalAdminView.ReasonCount> items = new ArrayList<>();
        long total = 0;
        for (String reason : REASONS) {
            long n = counts.getOrDefault(reason, 0L);
            total += n;
            items.add(new MemberWithdrawalAdminView.ReasonCount(reason, n));
        }
        long none = counts.getOrDefault(null, 0L);
        total += none;
        items.add(new MemberWithdrawalAdminView.ReasonCount(null, none));
        return new MemberWithdrawalAdminView.ReasonStats(total, items);
    }

    // ── 상세 ─────────────────────────────────────────────────────────────────

    /** @throws WithdrawalNotFoundException 없는 기록(번호 형식이 틀려도 같다) */
    @Transactional(readOnly = true)
    public Detail detail(String id) {
        Long parsed = parseId(id);
        Listed listed = parsed == null ? null : store.find(parsed);
        if (listed == null) {
            throw new WithdrawalNotFoundException();
        }
        return detail(listed);
    }

    /**
     * 그 회원의 가장 최근 탈퇴 기록(회원 상세 서랍의 탈퇴 블록).
     *
     * @throws com.community.member.exception.MemberNotFoundException 없는 회원(404 {@code MEMBER_NOT_FOUND})
     * @throws WithdrawalNotFoundException                            신청한 적 없음(404 {@code WITHDRAWAL_NOT_FOUND})
     */
    @Transactional(readOnly = true)
    public Detail forMember(String memberId) {
        MemberLookup.Member member = members.require(memberId);
        Listed listed = store.latestOf(member.id());
        if (listed == null) {
            throw new WithdrawalNotFoundException();
        }
        return detail(listed);
    }

    private Detail detail(Listed listed) {
        MemberWithdrawalStore.Row r = listed.row();
        Instant now = clock.instant();
        List<WithdrawalPending> pendingNow = r.scheduled() ? pending.all(r.userId(), r.choices(), queries.losses(r.userId(), ServiceTime.dateOf(now), now).paidPoints(), now)
                : snapshot(r.pendingJson());
        Instant expected = r.scheduled() ? WithdrawalPendingResolver.expectedCompleteAt(pendingNow).orElse(null) : r.expectedCompleteAt();
        Row base = row(r, listed.nickname(), pendingNow, expected);

        Set<Long> operatorIds = new LinkedHashSet<>();
        if (r.forceOperatorId() != null) {
            operatorIds.add(r.forceOperatorId());
        }
        if (r.cancelOperatorId() != null) {
            operatorIds.add(r.cancelOperatorId());
        }
        Map<Long, String> operators = store.operatorNames(operatorIds);
        MemberWithdrawalAdminView.Force force = r.source() == WithdrawalSource.OPERATOR
                ? new MemberWithdrawalAdminView.Force(r.forceReasonKind(), r.forceMemo(), operators.get(r.forceOperatorId())) : null;
        MemberWithdrawalAdminView.Cancel cancel = r.cancelOperatorId() != null
                ? new MemberWithdrawalAdminView.Cancel(r.cancelChannel(), r.cancelMemo(), operators.get(r.cancelOperatorId())) : null;
        MemberWithdrawalAdminView.Forfeit forfeit = new MemberWithdrawalAdminView.Forfeit(nz(r.forfeitPaidPoints()), nz(r.forfeitRefundEstimate()), nz(r.forfeitBizAmount()),
                r.forfeitConsentedAt(), nz(r.forfeitEmoticonEarnings()));
        return new Detail(base.id(), base.memberNo(), base.nickname(), base.status(), base.source(), base.requestedAt(), base.pendingCounts(), base.pendingCount(),
                base.expectedCompleteAt(), base.progressNotifiedAt(), base.hasPaymentHistory(), base.completedAt(), base.canceledAt(), r.priorStatus(), r.reason(), r.reasonNote(),
                choices(r), forfeit, items(pendingNow), r.status() == WithdrawalStatus.COMPLETED ? r.rejoinUntil() : null, force, cancel, events(r, force, cancel));
    }

    /** 처리 이력 — 신청 줄의 칸에서 시각 차례로 */
    private static List<Event> events(MemberWithdrawalStore.Row r, MemberWithdrawalAdminView.Force force, MemberWithdrawalAdminView.Cancel cancel) {
        List<Event> out = new ArrayList<>();
        if (force != null) {
            out.add(new Event("FORCED", r.requestedAt(), force.operatorName(), force.memo()));
        } else {
            out.add(new Event("REQUESTED", r.requestedAt(), null, null));
        }
        if (r.progressNotifiedAt() != null) {
            out.add(new Event("PROGRESS_MAILED", r.progressNotifiedAt(), null, null));
        }
        if (r.completedAt() != null) {
            out.add(new Event("COMPLETED", r.completedAt(), null, null));
        }
        if (r.canceledAt() != null) {
            out.add(cancel != null ? new Event("OPERATOR_CANCELED", r.canceledAt(), cancel.operatorName(), cancel.memo()) : new Event("CANCELED", r.canceledAt(), null, null));
        }
        out.sort((a, b) -> a.at().compareTo(b.at()));
        return out;
    }

    private MemberWithdrawalAdminView.Choices choices(MemberWithdrawalStore.Row r) {
        List<Map<String, Object>> plans = read(r.groupChoicesJson());
        Set<Long> groupIds = new LinkedHashSet<>();
        Set<Long> targets = new LinkedHashSet<>();
        for (Map<String, Object> plan : plans) {
            Long groupId = toLong(plan.get("groupId"));
            Long target = toLong(plan.get("toMemberId"));
            if (groupId != null) {
                groupIds.add(groupId);
            }
            if (target != null) {
                targets.add(target);
            }
        }
        Map<Long, String> groupNames = store.groupNames(groupIds);
        Map<Long, String> nicknames = store.nicknames(targets);
        List<MemberWithdrawalAdminView.GroupChoice> out = new ArrayList<>();
        for (Map<String, Object> plan : plans) {
            Long groupId = toLong(plan.get("groupId"));
            if (groupId == null) {
                continue;
            }
            String action = String.valueOf(plan.get("action"));
            String choice = "TRANSFER".equals(action) ? "TRANSFER" : "DISSOLVE".equals(action) ? "DISBAND" : "AUTO";
            Long target = toLong(plan.get("toMemberId"));
            out.add(new MemberWithdrawalAdminView.GroupChoice(Long.toString(groupId), groupNames.get(groupId), choice,
                    "TRANSFER".equals(choice) && target != null ? nicknames.get(target) : null));
        }
        // 저장된 JSON 의 차례는 신청 경로마다 다를 수 있다 — 화면이 늘 같은 차례로 그리게 모임 번호(만든 차례) 순
        out.sort(java.util.Comparator.comparingLong(g -> Long.parseLong(g.groupId())));
        WithdrawalChoices c = r.choices();
        return new MemberWithdrawalAdminView.Choices(c.paidPointChoice(), c.deliveredChoice(), c.bizChoice(), out);
    }

    // ── 강제 탈퇴 ───────────────────────────────────────────────────────────────

    /**
     * 강제 탈퇴 미리보기 — 선택값 기본으로 센 남은 일·돈·모임장 소모임·재가입 가능일.
     *
     * @throws com.community.member.exception.MemberNotFoundException 없는 회원(404)
     * @throws MemberAlreadyWithdrawnException                        이미 탈퇴(409)
     * @throws WithdrawalAlreadyScheduledException                    이미 진행 중(409)
     */
    @Transactional(readOnly = true)
    public MemberWithdrawalAdminView.Preview preview(String memberId) {
        WithdrawalQueries.Member member = activeMember(memberId);
        Instant now = clock.instant();
        WithdrawalQueries.Losses losses = queries.losses(member.id(), ServiceTime.dateOf(now), now);
        List<WithdrawalPending> remaining = pending.all(member.id(), WithdrawalChoices.DEFAULT, losses.paidPoints(), now);
        List<MemberWithdrawalAdminView.OwnedGroup> owned = groups.led(member.id()).stream()
                .map(g -> new MemberWithdrawalAdminView.OwnedGroup(Long.toString(g.getId()), g.getName())).toList();
        return new MemberWithdrawalAdminView.Preview(Long.toString(member.id()), member.nickname(), items(remaining), losses.freePoints(), losses.paidPoints(),
                business.of(member.id(), now).running().size(), owned, queries.lastTransactionAt(member.id()).isPresent(), rejoin.of(member.id(), member.status(), now).until(),
                emoticons.summary(member.id()).map(s -> new MemberWithdrawalAdminView.EmoticonCreator(s.onSalePacks(), s.pendingReviews(), s.unpaidEarnings(),
                        s.payoutAccountRegistered())).orElse(null));
    }

    /**
     * 강제 탈퇴 — 검사(400 {@code fields.reasonKind|memo}) → 회원 확인(404 · 409) → 회원 탈퇴와 같은 처리({@link WithdrawalRequestWriter#apply}, 선택값 고정 · 모임 모두 자동).
     * 즉시 완료면 완료 트랜잭션이 회원 세션을 모두 끊는다. 진행 중이면 회원 로그인은 유지된다(남은 일 처리·취소 요청은 할 수 있어야 한다).
     */
    public MemberWithdrawalAdminView.ForceResult force(AdminActor actor, String memberId, MemberWithdrawalAdminRequest.Force body) {
        MemberWithdrawalAdminRequest.Force r = body == null ? new MemberWithdrawalAdminRequest.Force(null, null) : body;
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String kind = r.reasonKind() == null ? null : r.reasonKind().strip();
        if (kind == null || !FORCE_REASON_KINDS.contains(kind)) {
            errors.put("reasonKind", new MessageRef("withdrawalAdmin.reasonKind.invalid"));
        }
        String memo = memo(errors, r.memo());
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        WithdrawalQueries.Member member = activeMember(memberId);
        Map<Long, GroupPlan> plans = new LinkedHashMap<>();
        for (GroupEntity group : groups.led(member.id())) {
            plans.put(group.getId(), GroupPlan.AUTO);
        }
        WithdrawalOutcome outcome = writer.apply(WithdrawalApplication.byOperator(member.id(), plans, new WithdrawalApplication.Force(actor.operatorId(), kind, memo), actor.ip()));
        log.info("운영자가 회원을 강제 탈퇴했다 operatorId={} userId={} withdrawalId={} result={} reasonKind={}", actor.operatorId(), member.id(), outcome.withdrawalId(),
                outcome.status(), kind);
        return new MemberWithdrawalAdminView.ForceResult(Long.toString(outcome.withdrawalId()), outcome.status().name(), outcome.pending().size(), outcome.expectedCompleteAt(),
                outcome.completedAt());
    }

    // ── 대리 취소 ───────────────────────────────────────────────────────────────

    /**
     * 운영자 대리 취소 — 검사(400 {@code fields.requestChannel|memo|memberConfirmed}) → 기록 확인(404 · 끝난 건 409 · 강제 건 409) → 회원 본인 취소와 같은 처리. 바뀐 상세를 돌려준다.
     */
    public Detail cancel(AdminActor actor, String id, MemberWithdrawalAdminRequest.Cancel body) {
        MemberWithdrawalAdminRequest.Cancel r = body == null ? new MemberWithdrawalAdminRequest.Cancel(null, null, null) : body;
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        String channel = r.requestChannel() == null ? null : r.requestChannel().strip();
        if (channel == null || !CANCEL_CHANNELS.contains(channel)) {
            errors.put("requestChannel", new MessageRef("withdrawalAdmin.requestChannel.invalid"));
        }
        String memo = memo(errors, r.memo());
        if (!Boolean.TRUE.equals(r.memberConfirmed())) {
            errors.put("memberConfirmed", new MessageRef("withdrawalAdmin.memberConfirmed.required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Long parsed = parseId(id);
        MemberWithdrawalStore.Row row = parsed == null ? null : withdrawals.find(parsed).orElse(null);
        if (row == null) {
            throw new WithdrawalNotFoundException();
        }
        if (!row.scheduled()) {
            throw new WithdrawalNotScheduledException();
        }
        if (row.source() != WithdrawalSource.MEMBER) {
            throw new WithdrawalNotMemberRequestedException();
        }
        writer.cancelByOperator(row.userId(), actor.operatorId(), channel, memo, actor.ip());
        return detail(id);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 있는 회원이고 탈퇴 완료·진행 중이 아니어야 한다 */
    private WithdrawalQueries.Member activeMember(String memberId) {
        MemberLookup.Member found = members.require(memberId);
        if (found.withdrawn()) {
            throw new MemberAlreadyWithdrawnException();
        }
        WithdrawalQueries.Member member = queries.member(found.id()).orElseThrow(MemberAlreadyWithdrawnException::new);
        if ("WITHDRAWN".equals(member.status())) {
            throw new MemberAlreadyWithdrawnException();
        }
        if ("WITHDRAWING".equals(member.status())) {
            throw new WithdrawalAlreadyScheduledException();
        }
        return member;
    }

    private Row row(Listed listed) {
        MemberWithdrawalStore.Row r = listed.row();
        return row(r, listed.nickname(), snapshot(r.pendingJson()), r.expectedCompleteAt());
    }

    private static Row row(MemberWithdrawalStore.Row r, String nickname, List<WithdrawalPending> pendingItems, Instant expected) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WithdrawalPending p : pendingItems) {
            counts.merge(p.code().name(), 1, Integer::sum);
        }
        boolean completed = r.status() == WithdrawalStatus.COMPLETED;
        return new Row(Long.toString(r.id()), Long.toString(r.userId()), completed ? null : nickname, r.status().name(), r.source().name(), r.requestedAt(), counts,
                pendingItems.size(), expected, r.progressNotifiedAt(), Boolean.TRUE.equals(r.hasPaymentHistory()), r.completedAt(), r.canceledAt());
    }

    private static List<PendingItem> items(List<WithdrawalPending> pendingItems) {
        return pendingItems.stream().map(p -> new PendingItem(p.code().name(), p.label(), p.refId(), p.waitUntil())).toList();
    }

    /** 저장한 남은 일 스냅샷 → 목록(모르는 종류·깨진 줄은 건너뛴다) */
    static List<WithdrawalPending> snapshot(String json) {
        List<WithdrawalPending> out = new ArrayList<>();
        for (Map<String, Object> item : read(json)) {
            WithdrawalPendingCode code;
            try {
                code = WithdrawalPendingCode.valueOf(String.valueOf(item.get("code")));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Object until = item.get("waitUntil");
            Instant waitUntil = null;
            if (until != null) {
                try {
                    waitUntil = Instant.parse(until.toString());
                } catch (DateTimeParseException e) {
                    waitUntil = null;
                }
            }
            Object label = item.get("label");
            Object link = item.get("link");
            Object refId = item.get("refId");
            out.add(new WithdrawalPending(code, label == null ? "" : label.toString(), link == null ? null : link.toString(), refId == null ? null : refId.toString(), waitUntil));
        }
        return out;
    }

    private static List<Map<String, Object>> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (RuntimeException e) {
            log.warn("탈퇴 신청의 JSON 칸을 읽지 못했다 원인={}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static String memo(Map<String, MessageRef> errors, String raw) {
        String memo = raw == null ? null : Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        int length = memo == null ? 0 : memo.codePointCount(0, memo.length());
        if (length < MEMO_MIN || length > MEMO_MAX) {
            errors.put("memo", new MessageRef("withdrawalAdmin.memo.length", MEMO_MIN, MEMO_MAX));
        }
        return memo;
    }

    private static String[] sort(Map<String, MessageRef> errors, String sort) {
        if (blank(sort)) {
            return new String[] {"requested_at", "desc"};
        }
        String[] parts = sort.strip().split(",");
        String column = MemberWithdrawalAdminStore.SORTS.get(parts[0].strip());
        String dir = parts.length > 1 ? parts[1].strip().toLowerCase(java.util.Locale.ROOT) : "desc";
        if (column == null || parts.length > 2 || !(dir.equals("asc") || dir.equals("desc"))) {
            errors.put("sort", new MessageRef("withdrawalAdmin.query.invalid"));
            return new String[] {"requested_at", "desc"};
        }
        return new String[] {column, dir};
    }

    private static Instant instant(Map<String, MessageRef> errors, String field, String value) {
        return blank(value) ? null : parse(errors, field, value, s -> Instant.parse(s.strip()));
    }

    private static int number(Map<String, MessageRef> errors, String field, String value, int fallback, int min, int max) {
        if (blank(value)) {
            return fallback;
        }
        Integer n = parse(errors, field, value, s -> Integer.parseInt(s.strip()));
        if (n == null) {
            return fallback;
        }
        if (n < min || n > max) {
            errors.put(field, new MessageRef("withdrawalAdmin.query.invalid"));
            return fallback;
        }
        return n;
    }

    private static <T> T parse(Map<String, MessageRef> errors, String field, String value, java.util.function.Function<String, T> parser) {
        try {
            return parser.apply(value);
        } catch (RuntimeException e) {
            errors.put(field, new MessageRef("withdrawalAdmin.query.invalid"));
            return null;
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 19 || !raw.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
