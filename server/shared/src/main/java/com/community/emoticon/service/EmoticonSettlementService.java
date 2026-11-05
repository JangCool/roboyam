package com.community.emoticon.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.dto.request.EmoticonSettlementConfirmRequest;
import com.community.emoticon.dto.request.EmoticonSettlementPaidRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonSettlementView;
import com.community.emoticon.entity.EmoticonSettlementStatus;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonSettlementNotFoundException;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonPayoutStore.PayoutRow;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberRef;
import com.community.emoticon.repository.master.EmoticonSettlementStore;
import com.community.emoticon.repository.master.EmoticonSettlementStore.ActivityRow;
import com.community.emoticon.repository.master.EmoticonSettlementStore.Frozen;
import com.community.emoticon.repository.master.EmoticonSettlementStore.SettlementRow;
import com.community.emoticon.service.EmoticonSettlementCalculator.CreatorLines;
import com.community.emoticon.service.EmoticonSettlementCalculator.Line;
import com.community.emoticon.service.EmoticonSettlementCalculator.Rules;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로보티콘 작가 월 정산(emoticon-contract S4 — 관리자 {@code /robotikon/settlements} 권한 {@code emoticon.settlement}, 작가 스튜디오 정산 내역). 계산은 {@link EmoticonSettlementCalculator} 한 곳.
 *
 * <ul>
 *   <li><b>목록</b>({@link #list}): 그 판매 달의 작가별 줄 — 판매·환불이 들어가거나 앞 달에서 넘어온 금액이 있는 작가. 달을 안 주거나 고를 수 있는 달 밖이면 「지급을 앞둔 달」(이번 달의 앞 달,
 *       없으면 이번 달). 고를 수 있는 달 = 작가 팩 첫 판매 달 ~ 이번 달(최근이 앞). 정렬 = 지급액 큰 것 → 작가 이름.</li>
 *   <li><b>확정</b>({@link #confirm} — 여러 줄): 줄마다 작가 정산을 줄 세우고(권고 잠금) 아직 정산에 들어가지 않은 구매 행을 잠근 뒤 다시 계산해, 맞지 않으면 건너뛴다 —
 *       없음 {@code NOT_FOUND} → 예정 아님 {@code NOT_ESTIMATED} → 확정 가능 시각 전 {@code NOT_CLOSED}(그 달 판매의 셀프 환불 기한이 모두 지나야) → 앞 달이 아직 굳지 않음
 *       {@code PREVIOUS_OPEN}(이월이 굳어야 하므로 달 차례대로) → 정산 정보 없음 {@code PAYOUT_ACCOUNT_MISSING}(원천징수 판단이 없어 막는다 — 보류로 두지 않는다).
 *       확정하면 금액·수수료율·작가 구분·지급일을 굳히고, 그 줄에 들어간 구매에 정산 달을 찍고, 처리 기록 · 감사 {@code EMOTICON_SETTLEMENT_CONFIRM}.</li>
 *   <li><b>보류</b>({@link #hold} — 사유 5~200자): 예정·확정만({@code 409 EMOTICON_SETTLEMENT_NOT_HOLDABLE}). 굳힌 줄은 굳힌 그대로 보류된다. <b>보류 풀기</b>({@link #unhold}):
 *       보류 전 상태로(굳힌 줄 = 확정, 아니면 예정 — 목업은 늘 예정으로 되돌려 다시 계산했지만, 굳힌 금액을 풀면 이미 다른 달에 뺀 확정 뒤 환불이 두 번 빠질 수 있어 바꿨다).
 *       보류 아님 {@code 409 EMOTICON_SETTLEMENT_NOT_HELD}.</li>
 *   <li><b>지급 완료 기록</b>({@link #markPaid}): 이체한 날(오늘 이전 {@code YYYY-MM-DD} · 업무일 — 토·일·공휴일이면 {@code fields.transferDate}) · 메모 200자 → 확정만({@code 409 EMOTICON_SETTLEMENT_NOT_CONFIRMED}) → 지급액 0
 *       {@code 409 EMOTICON_SETTLEMENT_NOTHING_TO_PAY}. 실제 송금은 사이트 밖.</li>
 *   <li><b>계좌 원문 보기</b>({@link #revealAccount} — 권한 {@code emoticon.payout.pii}): 사유 → 줄 404 → S2 {@code EmoticonPayoutService.reveal}(계좌 404 · 감사) → 처리 기록 {@code ACCOUNT_VIEWED}.</li>
 *   <li><b>작가</b>({@link #studio}): 내 정산 내역 — 처음 달 ~ 이번 달, 최근 달 먼저(예정 달 포함).</li>
 * </ul>
 * 정산 확정·지급 완료의 작가 알림은 만들지 않았다(계약·두 앱 목업에 그 알림이 없다).
 */
@Service
public class EmoticonSettlementService {

    public static final String TARGET = "EMOTICON_SETTLEMENT";
    static final int CONFIRM_MAX = 200;
    static final int MEMO_MAX = 200;
    private static final Pattern ID = Pattern.compile("^(\\d{4})(\\d{2})-(\\d{1,19})$");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Pattern MONTH_TEXT = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern DATE_TEXT = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** 정산 줄 주소 — 작가 · 판매 달 */
    record Key(long creatorId, LocalDate month) {

        String id() {
            return EmoticonSettlementService.id(creatorId, month);
        }
    }

    private final EmoticonSettlementStore store;
    private final EmoticonSettlementCalculator calculator;
    private final EmoticonPayoutStore payouts;
    private final EmoticonPayoutService payoutService;
    private final EmoticonReadStore reads;
    private final EmoticonPackStore packs;
    private final EmoticonContent content;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public EmoticonSettlementService(EmoticonSettlementStore store, EmoticonSettlementCalculator calculator, EmoticonPayoutStore payouts, EmoticonPayoutService payoutService,
                                     EmoticonReadStore reads, EmoticonPackStore packs, EmoticonContent content, AuditLogService audit, SnowflakeIdGenerator ids,
                                     ObjectProvider<Clock> clocks) {
        this.store = store;
        this.calculator = calculator;
        this.payouts = payouts;
        this.payoutService = payoutService;
        this.reads = reads;
        this.packs = packs;
        this.content = content;
        this.audit = audit;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 정산 줄 ID — {@code yyyyMM-작가회원번호} */
    public static String id(long creatorId, LocalDate month) {
        return String.format("%04d%02d-%d", month.getYear(), month.getMonthValue(), creatorId);
    }

    // ── 관리자 읽기 ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EmoticonSettlementView.AdminList list(String monthText) {
        LocalDate current = EmoticonSettlementCalculator.monthOf(clock.instant());
        List<String> months = months(current);
        LocalDate picked = parseMonth(monthText);
        if (picked == null || !months.contains(picked.format(MONTH))) {
            picked = LocalDate.parse((months.size() > 1 ? months.get(1) : months.get(0)) + "-01");
        }
        Rules rules = calculator.rules();
        List<Line> lines = monthLines(picked, rules);
        Map<Long, PayoutRow> payees = payouts.findAll(lines.stream().map(Line::creatorId).collect(Collectors.toSet()));
        Map<Long, MemberRef> members = reads.members(lines.stream().map(Line::creatorId).collect(Collectors.toSet()));
        List<EmoticonSettlementView.AdminRow> items = lines.stream().map(l -> adminRow(l, payees.get(l.creatorId()), members.get(l.creatorId())))
                .sorted(Comparator.comparingLong(EmoticonSettlementView.AdminRow::payoutAmount).reversed()
                        .thenComparing(r -> r.creator().nickname() == null ? "￿" : r.creator().nickname()))
                .toList();
        EmoticonSettlementView.Totals totals = new EmoticonSettlementView.Totals(sum(items, EmoticonSettlementView.AdminRow::salesAmount),
                sum(items, EmoticonSettlementView.AdminRow::refundAmount), sum(items, EmoticonSettlementView.AdminRow::vatAmount),
                sum(items, EmoticonSettlementView.AdminRow::feeAmount), sum(items, EmoticonSettlementView.AdminRow::withholdingAmount),
                sum(items, EmoticonSettlementView.AdminRow::payoutAmount), sum(items, EmoticonSettlementView.AdminRow::carriedOut));
        return new EmoticonSettlementView.AdminList(picked.format(MONTH), items, totals, months,
                new EmoticonSettlementView.Rules(rules.feeRatePercent(), rules.payoutMinimum(), rules.refundDays(), rules.settlementDay()));
    }

    @Transactional(readOnly = true)
    public EmoticonSettlementView.AdminDetail detail(String idText) {
        Key key = key(idText);
        CreatorLines c = calculator.creator(key.creatorId(), key.month(), false);
        return detail(key, c);
    }

    // ── 관리자 처리 ─────────────────────────────────────────────────────────

    @Transactional
    public EmoticonSettlementView.BulkResult confirm(long operatorId, String ip, EmoticonSettlementConfirmRequest body) {
        List<String> raw = body == null || body.ids() == null ? List.of() : body.ids();
        if (raw.isEmpty() || raw.size() > CONFIRM_MAX) {
            throw ValidationException.of("ids", new MessageRef("emoticon.settlement.ids", CONFIRM_MAX));
        }
        List<EmoticonSettlementView.Skipped> skipped = new ArrayList<>();
        Map<String, Key> keys = new LinkedHashMap<>();
        for (String text : new LinkedHashSet<>(raw)) {
            Key key = parseKey(text);
            if (key == null) {
                skipped.add(new EmoticonSettlementView.Skipped(text, "NOT_FOUND"));
            } else {
                keys.put(key.id(), key);
            }
        }
        // 작가 → 달 차례(같은 작가의 두 달을 함께 확정하면 앞 달이 먼저 굳는다 · 잠금 차례도 한 가지)
        List<Key> ordered = keys.values().stream().sorted(Comparator.comparingLong(Key::creatorId).thenComparing(Key::month)).toList();
        int confirmed = 0;
        Instant now = clock.instant();
        for (Key key : ordered) {
            store.lockCreator(key.creatorId());
            CreatorLines c = calculator.creator(key.creatorId(), key.month(), true);
            Line line = c.at(key.month());
            String reason = confirmBlock(c, line, now);
            if (reason != null) {
                skipped.add(new EmoticonSettlementView.Skipped(key.id(), reason));
                continue;
            }
            // 작가 구분은 지금 정산 정보 — 계산도 그 구분으로 했다
            Frozen frozen = line.freeze();
            if (!store.confirm(key.creatorId(), key.month(), frozen, operatorId, now)) {
                skipped.add(new EmoticonSettlementView.Skipped(key.id(), "NOT_ESTIMATED"));
                continue;
            }
            store.markPurchases(key.creatorId(), key.month(), line.sales().stream().map(ActivityRow::purchaseId).toList(),
                    line.refunds().stream().map(ActivityRow::purchaseId).toList());
            store.insertEvent(ids.nextId(), key.creatorId(), key.month(), "CONFIRMED", now, operatorId, null);
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_SETTLEMENT_CONFIRM, AuditResult.SUCCESS).target(TARGET, key.id())
                    .with("creator", Long.toString(key.creatorId())).with("month", key.month().format(MONTH)).with("salesAmount", frozen.salesAmount())
                    .with("refundAmount", frozen.refundAmount()).with("shareAmount", frozen.share()).with("carriedIn", frozen.carriedIn())
                    .with("withholdingAmount", frozen.withholding()).with("payoutAmount", frozen.payout()).with("carriedOut", frozen.carriedOut())
                    .with("payeeType", frozen.payeeType()).with("feeRatePercent", frozen.feeRatePercent()));
            confirmed++;
        }
        return new EmoticonSettlementView.BulkResult(confirmed, skipped.size(), skipped);
    }

    @Transactional
    public EmoticonSettlementView.AdminDetail hold(long operatorId, String ip, String idText, String reasonText) {
        String reason = EmoticonPackAdminService.reason(reasonText);
        Key key = key(idText);
        store.lockCreator(key.creatorId());
        CreatorLines c = linesOrThrow(key);
        Line line = c.at(key.month());
        if (line.status() != EmoticonSettlementStatus.ESTIMATED && line.status() != EmoticonSettlementStatus.CONFIRMED) {
            throw new EmoticonConflictException(EmoticonConflictException.SETTLEMENT_NOT_HOLDABLE);
        }
        Instant now = clock.instant();
        store.insertIfAbsent(key.creatorId(), key.month(), now);
        if (!store.hold(key.creatorId(), key.month(), reason, operatorId, now)) {
            throw new EmoticonConflictException(EmoticonConflictException.SETTLEMENT_NOT_HOLDABLE);
        }
        store.insertEvent(ids.nextId(), key.creatorId(), key.month(), "HELD", now, operatorId, reason);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_SETTLEMENT_HOLD, AuditResult.SUCCESS).target(TARGET, key.id())
                .with("creator", Long.toString(key.creatorId())).with("month", key.month().format(MONTH)).with("from", line.status().name()).with("reason", reason));
        return detail(key, calculator.creator(key.creatorId(), key.month(), false));
    }

    @Transactional
    public EmoticonSettlementView.AdminDetail unhold(long operatorId, String ip, String idText) {
        Key key = key(idText);
        store.lockCreator(key.creatorId());
        CreatorLines c = linesOrThrow(key);
        if (c.at(key.month()).status() != EmoticonSettlementStatus.HELD || !store.unhold(key.creatorId(), key.month(), clock.instant())) {
            throw new EmoticonConflictException(EmoticonConflictException.SETTLEMENT_NOT_HELD);
        }
        Instant now = clock.instant();
        store.insertEvent(ids.nextId(), key.creatorId(), key.month(), "UNHELD", now, operatorId, null);
        CreatorLines after = calculator.creator(key.creatorId(), key.month(), false);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_SETTLEMENT_UNHOLD, AuditResult.SUCCESS).target(TARGET, key.id())
                .with("creator", Long.toString(key.creatorId())).with("month", key.month().format(MONTH)).with("to", after.at(key.month()).status().name()));
        return detail(key, after);
    }

    @Transactional
    public EmoticonSettlementView.AdminDetail markPaid(long operatorId, String ip, String idText, EmoticonSettlementPaidRequest body) {
        EmoticonSettlementPaidRequest b = body == null ? new EmoticonSettlementPaidRequest(null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        LocalDate transferDate = null;
        if (b.transferDate() == null || !DATE_TEXT.matcher(b.transferDate().trim()).matches()) {
            fields.put("transferDate", new MessageRef("emoticon.settlement.transferDate"));
        } else {
            try {
                transferDate = LocalDate.parse(b.transferDate().trim());
                if (transferDate.isAfter(ServiceTime.today(clock))) {
                    fields.put("transferDate", new MessageRef("emoticon.settlement.transferDate.future"));
                } else if (!calculator.calendar().businessDay(transferDate)) {
                    // 지급은 평일 업무일에만(사용자 결정 2026-11-05) — 토·일·공휴일 이체일은 받지 않는다(날짜 기준만, 시각은 보지 않는다)
                    fields.put("transferDate", new MessageRef("emoticon.settlement.transferDate.notBusinessDay"));
                }
            } catch (DateTimeParseException e) {
                fields.put("transferDate", new MessageRef("emoticon.settlement.transferDate"));
            }
        }
        String memo = b.memo() == null ? null : b.memo().strip();
        if (memo != null && memo.codePointCount(0, memo.length()) > MEMO_MAX) {
            fields.put("memo", new MessageRef("emoticon.settlement.memo", MEMO_MAX));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        String savedMemo = memo == null || memo.isEmpty() ? null : memo;
        Key key = key(idText);
        store.lockCreator(key.creatorId());
        Line line = linesOrThrow(key).at(key.month());
        if (line.status() != EmoticonSettlementStatus.CONFIRMED) {
            throw new EmoticonConflictException(EmoticonConflictException.SETTLEMENT_NOT_CONFIRMED);
        }
        if (line.payout() <= 0) {
            throw new EmoticonConflictException(EmoticonConflictException.SETTLEMENT_NOTHING_TO_PAY);
        }
        Instant now = clock.instant();
        if (!store.markPaid(key.creatorId(), key.month(), transferDate, savedMemo, operatorId, now)) {
            throw new EmoticonConflictException(EmoticonConflictException.SETTLEMENT_NOT_CONFIRMED);
        }
        store.insertEvent(ids.nextId(), key.creatorId(), key.month(), "PAID", now, operatorId, savedMemo);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_SETTLEMENT_PAID, AuditResult.SUCCESS).target(TARGET, key.id())
                .with("creator", Long.toString(key.creatorId())).with("month", key.month().format(MONTH)).with("payoutAmount", line.payout())
                .with("transferDate", transferDate.toString()).with("memo", savedMemo == null ? "" : savedMemo));
        return detail(key, calculator.creator(key.creatorId(), key.month(), false));
    }

    /** 정산 줄의 계좌 원문 보기 — 사유 → 줄 404 → 계좌 404 · 감사(S2 서비스) → 처리 기록 {@code ACCOUNT_VIEWED} */
    @Transactional
    public EmoticonAdminView.PayoutAccountReveal revealAccount(long operatorId, String ip, String idText, String reasonText) {
        String reason = EmoticonPackAdminService.reason(reasonText);
        Key key = key(idText);
        store.lockCreator(key.creatorId());
        linesOrThrow(key);
        EmoticonAdminView.PayoutAccountReveal revealed = payoutService.reveal(operatorId, ip, key.creatorId(), reason, key.id());
        Instant now = clock.instant();
        store.insertIfAbsent(key.creatorId(), key.month(), now);
        store.insertEvent(ids.nextId(), key.creatorId(), key.month(), "ACCOUNT_VIEWED", now, operatorId, reason);
        return revealed;
    }

    // ── 작가 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EmoticonSettlementView.StudioList studio(long creatorId) {
        LocalDate current = EmoticonSettlementCalculator.monthOf(clock.instant());
        List<Line> lines = new ArrayList<>(calculator.creator(creatorId, current, false).lines());
        java.util.Collections.reverse(lines);
        return new EmoticonSettlementView.StudioList(lines.stream().map(l -> new EmoticonSettlementView.StudioRow(id(l.creatorId(), l.month()), l.month().format(MONTH),
                l.salesCount(), l.salesAmount(), l.refundAmount(), l.vat(), l.base(), l.fee(), l.share(), l.withholding(), l.payout(), l.carriedIn(), l.carriedOut() != 0,
                l.carriedOut(), l.status().name(), l.row() == null ? null : l.row().holdReason(), l.carriedOut() != 0 ? null : l.payDate().toString())).toList());
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 확정을 막는 까닭(없으면 null) */
    private static String confirmBlock(CreatorLines c, Line line, Instant now) {
        if (line == null) {
            return "NOT_FOUND";
        }
        if (line.status() != EmoticonSettlementStatus.ESTIMATED) {
            return "NOT_ESTIMATED";
        }
        if (now.isBefore(line.closesAt())) {
            return "NOT_CLOSED";
        }
        boolean previousOpen = c.lines().stream().anyMatch(l -> l.month().isBefore(line.month()) && !l.frozen());
        if (previousOpen) {
            return "PREVIOUS_OPEN";
        }
        if (c.payee() == null) {
            return "PAYOUT_ACCOUNT_MISSING";
        }
        return null;
    }

    private CreatorLines linesOrThrow(Key key) {
        CreatorLines c = calculator.creator(key.creatorId(), key.month(), false);
        if (c.at(key.month()) == null) {
            throw new EmoticonSettlementNotFoundException();
        }
        return c;
    }

    /** 그 달의 모든 작가 줄 — 앞의 마지막 굳힌 줄부터 이어 계산한다 */
    private List<Line> monthLines(LocalDate month, Rules rules) {
        Map<Long, SettlementRow> bases = store.latestFrozenBefore(month);
        Map<Long, TreeMap<LocalDate, SettlementRow>> open = new HashMap<>();
        for (SettlementRow r : store.openUpTo(month)) {
            open.computeIfAbsent(r.creatorId(), k -> new TreeMap<>()).put(r.month(), r);
        }
        Map<Long, List<ActivityRow>> activity = store.unsettled().stream().collect(Collectors.groupingBy(ActivityRow::creatorId));
        Set<Long> candidates = new HashSet<>(open.keySet());
        candidates.addAll(activity.keySet());
        bases.forEach((creator, row) -> {
            if (row.frozen().carriedOut() != 0) {
                candidates.add(creator);
            }
        });
        Map<Long, PayoutRow> payees = payouts.findAll(candidates);
        List<Line> out = new ArrayList<>();
        for (long creator : candidates) {
            SettlementRow base = bases.get(creator);
            PayoutRow payee = payees.get(creator);
            List<Line> lines = EmoticonSettlementCalculator.walk(creator, open.getOrDefault(creator, new TreeMap<>()), activity.getOrDefault(creator, List.of()),
                    base == null ? 0 : base.frozen().carriedOut(), base == null ? null : base.month().plusMonths(1), month, rules,
                    payee == null ? null : payee.payeeType());
            lines.stream().filter(l -> l.month().equals(month)).findFirst().ifPresent(out::add);
        }
        return out;
    }

    /** 고를 수 있는 달 — 첫 정산 달 ~ 이번 달(최근이 앞) */
    private List<String> months(LocalDate current) {
        LocalDate first = store.firstMonth();
        List<String> out = new ArrayList<>();
        LocalDate from = first == null || first.isAfter(current) ? current : first;
        for (LocalDate m = current; !m.isBefore(from); m = m.minusMonths(1)) {
            out.add(m.format(MONTH));
        }
        return out;
    }

    private EmoticonSettlementView.AdminDetail detail(Key key, CreatorLines c) {
        Line line = c.at(key.month());
        if (line == null) {
            throw new EmoticonSettlementNotFoundException();
        }
        MemberRef member = reads.members(List.of(key.creatorId())).get(key.creatorId());
        EmoticonSettlementView.AdminRow row = adminRow(line, c.payee(), member);
        List<ActivityRow> sales;
        List<ActivityRow> refunds;
        if (line.frozen()) {
            List<ActivityRow> settled = store.settled(key.creatorId(), key.month());
            sales = settled.stream().filter(a -> key.month().equals(a.settlementMonth())).toList();
            refunds = settled.stream().filter(a -> key.month().equals(a.refundSettlementMonth())).toList();
        } else {
            sales = line.sales();
            refunds = line.refunds();
        }
        List<EmoticonSettlementView.Event> events = new ArrayList<>();
        Instant createdAt = firstActivity(sales, refunds);
        events.add(new EmoticonSettlementView.Event("CREATED", createdAt == null ? key.month().atStartOfDay(ServiceTime.ZONE).toInstant() : createdAt, null, null));
        store.events(key.creatorId(), key.month()).forEach(e -> events.add(new EmoticonSettlementView.Event(e.action(), e.at(), e.byName(), e.note())));
        java.util.Collections.reverse(events);
        return EmoticonSettlementView.AdminDetail.of(row, packLines(sales, refunds), events);
    }

    private static Instant firstActivity(List<ActivityRow> sales, List<ActivityRow> refunds) {
        Instant first = null;
        for (ActivityRow a : sales) {
            first = first == null || a.paidAt().isBefore(first) ? a.paidAt() : first;
        }
        for (ActivityRow a : refunds) {
            first = first == null || a.refundedAt().isBefore(first) ? a.refundedAt() : first;
        }
        return first;
    }

    private List<EmoticonSettlementView.PackLine> packLines(List<ActivityRow> sales, List<ActivityRow> refunds) {
        record Acc(long packId, String[] name, int[] price, int[] counts, long[] amounts) {
        }
        Map<Long, Acc> byPack = new LinkedHashMap<>();
        for (ActivityRow a : sales.stream().sorted(Comparator.comparing(ActivityRow::paidAt)).toList()) {
            Acc acc = byPack.computeIfAbsent(a.packId(), k -> new Acc(k, new String[] {a.packName()}, new int[] {a.amount()}, new int[2], new long[2]));
            acc.name()[0] = a.packName();
            acc.price()[0] = a.amount();
            acc.counts()[0]++;
            acc.amounts()[0] += a.amount();
        }
        for (ActivityRow a : refunds) {
            Acc acc = byPack.computeIfAbsent(a.packId(), k -> new Acc(k, new String[] {a.packName()}, new int[] {a.amount()}, new int[2], new long[2]));
            acc.counts()[1]++;
            acc.amounts()[1] += a.amount();
        }
        if (byPack.isEmpty()) {
            return List.of();
        }
        Map<Long, PackRow> packById = packs.byIds(byPack.keySet()).stream().collect(Collectors.toMap(PackRow::id, p -> p));
        Map<Long, EmoticonContent.Loaded> lives = content.load(packById.values().stream().map(PackRow::liveRevisionId).filter(java.util.Objects::nonNull).toList());
        return byPack.values().stream().map(acc -> {
            PackRow p = packById.get(acc.packId());
            String cover = p == null || p.liveRevisionId() == null ? null : content.coverUrl(lives.get(p.liveRevisionId()));
            return new EmoticonSettlementView.PackLine(Long.toString(acc.packId()), acc.name()[0], cover, acc.price()[0], acc.counts()[0], acc.amounts()[0], acc.counts()[1],
                    acc.amounts()[1]);
        }).toList();
    }

    private static EmoticonSettlementView.AdminRow adminRow(Line l, PayoutRow payee, MemberRef member) {
        SettlementRow row = l.row();
        EmoticonSettlementView.AccountMasked account = payee == null ? null
                : new EmoticonSettlementView.AccountMasked(payee.bankCode(), "****" + payee.accountLast4(), payee.holderMasked());
        EmoticonSettlementView.Hold hold = row == null || row.holdReason() == null ? null
                : new EmoticonSettlementView.Hold(row.holdReason(), row.heldAt(), row.heldByName());
        EmoticonSettlementView.Paid paid = row == null || row.paidAt() == null ? null
                : new EmoticonSettlementView.Paid(row.transferDate().toString(), row.paidMemo(), row.paidAt(), row.paidByName());
        return new EmoticonSettlementView.AdminRow(id(l.creatorId(), l.month()), l.month().format(MONTH),
                new EmoticonAdminView.CreatorRef(Long.toString(l.creatorId()), member == null ? null : member.nickname(), member == null || member.withdrawn()),
                l.payeeType(), payee == null ? null : payee.businessNumberMasked(), l.salesCount(), l.salesAmount(), l.refundCount(), l.refundAmount(), l.vat(), l.base(),
                l.feeRatePercent(), l.fee(), l.share(), l.withholding(), l.carriedIn(), l.payout(), l.carriedOut(), l.status().name(), l.payDate().toString(), l.closesAt(),
                row == null ? null : row.confirmedAt(), account, hold, paid);
    }

    private static long sum(List<EmoticonSettlementView.AdminRow> rows, java.util.function.ToLongFunction<EmoticonSettlementView.AdminRow> f) {
        return rows.stream().mapToLong(f).sum();
    }

    private static Key key(String text) {
        Key key = parseKey(text);
        if (key == null) {
            throw new EmoticonSettlementNotFoundException();
        }
        return key;
    }

    static Key parseKey(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = ID.matcher(text.trim());
        if (!m.matches()) {
            return null;
        }
        int month = Integer.parseInt(m.group(2));
        long creator;
        try {
            creator = Long.parseLong(m.group(3));
        } catch (NumberFormatException e) {
            return null;
        }
        if (month < 1 || month > 12) {
            return null;
        }
        return new Key(creator, LocalDate.of(Integer.parseInt(m.group(1)), month, 1));
    }

    private static LocalDate parseMonth(String text) {
        if (text == null || !MONTH_TEXT.matcher(text.trim()).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim() + "-01");
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
