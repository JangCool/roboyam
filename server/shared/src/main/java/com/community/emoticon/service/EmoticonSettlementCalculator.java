package com.community.emoticon.service;

import com.community.emoticon.entity.EmoticonSettlementStatus;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonPayoutStore.PayoutRow;
import com.community.emoticon.repository.master.EmoticonSettingsStore.SettingsRow;
import com.community.emoticon.repository.master.EmoticonSettlementStore;
import com.community.emoticon.repository.master.EmoticonSettlementStore.ActivityRow;
import com.community.emoticon.repository.master.EmoticonSettlementStore.Frozen;
import com.community.emoticon.repository.master.EmoticonSettlementStore.SettlementRow;
import com.community.holiday.service.BusinessDayCalendar;
import com.community.holiday.service.PublicHolidayService;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 로보티콘 작가 월 정산의 <b>계산 한 곳</b>(emoticon-contract S4). 예정(굳히지 않은) 달의 금액은 저장하지 않고 조회할 때 구매 기록에서 계산한다 — 굳힌(확정한) 달은 저장한 값을 그대로 쓴다.
 *
 * <ul>
 *   <li><b>들어갈 달</b>: 아직 정산에 들어가지 않은 판매는 결제 달(서비스 시간대 KST). 아직 빼지 않은 환불은 그 판매가 아직 정산에 들어가지 않았으면 판매 달(예정 ~ 확정 사이 환불은 그 달에서),
 *       이미 확정된 줄에 들어갔으면 <b>환불한 달</b>(확정 뒤 환불은 환불한 달에서 뺀다 — 계약 「화면 확정」). 들어갈 달이 이미 굳었으면(확정 뒤 늦게 들어온 결제 등) 다음에 열린 달로 넘긴다.</li>
 *   <li><b>이월</b>: 달마다 합 = 작가 몫 + 앞 달의 넘김. 최소 지급액(설정) 미만이거나 0 이하면 지급하지 않고 합 전부를 다음 달로 넘긴다 — 음수(확정 뒤 환불이 그 달 판매보다 큼)도 넘겨 다음 달에서 뺀다.
 *       넘어온 금액만 있는 달도 줄이 생긴다(목업과 같다). 계산은 {@link EmoticonSettlementMath#month}. <b>탈퇴한 작가</b>는 최소 지급액이 없다({@link Rules#forCreator} — 후속 결정 6).</li>
 *   <li><b>예정 달의 값</b>: 수수료율·최소 지급액·정산일은 지금 설정, 작가 구분은 지금 정산 정보(없으면 개인으로 어림 — 원천징수를 떼고 보인다). 지급 예정일 = 다음 달 정산일(토·일·공휴일이면 다음 업무일 — {@link Rules#payDate}).
 *       확정 가능 시각 = max(다음 달 1일 0시 KST, 그 달 판매들의 셀프 환불 기한 {@code refund_until} 최댓값).</li>
 * </ul>
 */
@Component
public class EmoticonSettlementCalculator {

    /** 한 작가의 한 달 정산 — 굳힌 달은 저장한 값, 예정 달은 계산한 값. {@code sales}·{@code refunds} 는 예정 달에 들어가는 구매(굳힌 달은 비어 있다) */
    public record Line(long creatorId, LocalDate month, SettlementRow row, EmoticonSettlementStatus status, boolean frozen, int salesCount, long salesAmount,
                       int refundCount, long refundAmount, long vat, long base, int feeRatePercent, long fee, long share, long carriedIn, long withholding, long payout,
                       long carriedOut, String payeeType, LocalDate payDate, Instant closesAt, List<ActivityRow> sales, List<ActivityRow> refunds) {

        /** 확정할 때 굳힐 값(작가 구분은 그때의 정산 정보) */
        public Frozen freeze() {
            return new Frozen(salesCount, salesAmount, refundCount, refundAmount, vat, base, feeRatePercent, fee, share, carriedIn, withholding, payout, carriedOut,
                    payeeType, payDate, closesAt);
        }
    }

    /** 계산에 쓰는 설정 값 + 업무일 달력(지급 예정일을 미룬다 — 후속 결정 4) */
    public record Rules(int feeRatePercent, int payoutMinimum, int settlementDay, int refundDays, BusinessDayCalendar calendar) {

        public static Rules of(SettingsRow s, BusinessDayCalendar calendar) {
            return new Rules(s.feeRatePercent(), s.payoutMinimum(), s.settlementDay(), s.refundDays(), calendar);
        }

        /**
         * 그 판매 달의 지급 예정일 = 다음 달 정산일(설정, 기본 10일) — 토·일·공휴일이면 <b>다음 업무일</b>(사용자 결정 2026-11-05 「평일 업무시간만 처리 가능」 — 날짜 기준만,
         * 시각은 보지 않는다). 확정할 때 이 값을 굳힌다(확정 뒤 공휴일 표를 고쳐도 굳힌 줄은 그대로).
         */
        public LocalDate payDate(LocalDate month) {
            return calendar.onOrAfter(month.plusMonths(1).withDayOfMonth(settlementDay));
        }

        /**
         * 그 작가에게 쓸 규칙 — 탈퇴한 작가는 최소 지급액이 없다(후속 결정 6 「남은 정산은 한 번에 지급」): 아직 굳히지 않은 달의 합이 0 보다 크면 넘긴 금액까지 모두 지급 대상이고
         * 이월하지 않는다. 0 이하(음수 = 확정 뒤 환불로 받을 돈)는 지금처럼 넘긴다(끝난 것으로 본다 — 파기 배치 조건 ③). 이미 굳힌 달은 그대로.
         */
        public Rules forCreator(boolean withdrawn) {
            return withdrawn ? new Rules(feeRatePercent, 0, settlementDay, refundDays, calendar) : this;
        }
    }

    /** 한 작가의 계산 결과 — 달 오래된 것 먼저 · 지금 정산 정보(없으면 null) */
    public record CreatorLines(List<Line> lines, PayoutRow payee) {

        public Line at(LocalDate month) {
            return lines.stream().filter(l -> l.month().equals(month)).findFirst().orElse(null);
        }
    }

    private final EmoticonSettlementStore store;
    private final EmoticonPayoutStore payouts;
    private final EmoticonSettingsService settings;
    private final PublicHolidayService holidays;

    public EmoticonSettlementCalculator(EmoticonSettlementStore store, EmoticonPayoutStore payouts, EmoticonSettingsService settings, PublicHolidayService holidays) {
        this.store = store;
        this.payouts = payouts;
        this.settings = settings;
        this.holidays = holidays;
    }

    /** 판매·환불 시각이 든 정산 달(서비스 시간대의 그달 1일) */
    public static LocalDate monthOf(Instant at) {
        return ServiceTime.dateOf(at).withDayOfMonth(1);
    }

    /** 그 달 다음 달 1일 0시(KST) */
    public static Instant monthEnd(LocalDate month) {
        return month.plusMonths(1).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    public Rules rules() {
        return Rules.of(settings.current(), holidays.calendar());
    }

    /** 업무일 달력(지급 기록의 이체일 검사) */
    public BusinessDayCalendar calendar() {
        return holidays.calendar();
    }

    /**
     * 한 작가의 처음 달부터 {@code end} 까지(굳힌 달 포함). {@code lock} 이면 아직 정산에 들어가지 않은 구매 행을 잠근다(확정 — 부르는 쪽이 {@code lockCreator} 를 먼저 잡는다).
     */
    public CreatorLines creator(long creatorId, LocalDate end, boolean lock) {
        Map<LocalDate, SettlementRow> rows = store.byCreator(creatorId).stream().collect(Collectors.toMap(SettlementRow::month, Function.identity(), (a, b) -> a, TreeMap::new));
        List<ActivityRow> activity = store.unsettled(creatorId, lock);
        PayoutRow payee = payouts.find(creatorId).orElse(null);
        Rules rules = rules().forCreator(store.withdrawn(List.of(creatorId)).contains(creatorId));
        return new CreatorLines(walk(creatorId, rows, activity, 0, null, end, rules, payee == null ? null : payee.payeeType()), payee);
    }

    /**
     * 한 작가의 달마다 정산을 차례로 계산한다.
     *
     * @param persisted    저장된 줄(달 → 줄) — 굳힌 줄은 그대로 쓰고 이월을 이어 받는다
     * @param activity     아직 정산에 들어가지 않은 판매·환불
     * @param initialCarry {@code start} 앞에서 넘어온 금액(앞의 마지막 굳힌 줄의 넘김)
     * @param start        계산을 시작할 달(null 이면 저장된 줄·입력의 가장 이른 달). 이보다 앞 달에 들어갈 입력은 시작 달로 넘긴다(앞 달이 모두 굳었다)
     * @param end          마지막 달(이 뒤의 입력은 보지 않는다)
     * @param payeeType    지금 작가 구분({@code INDIVIDUAL}·{@code BUSINESS}, 없으면 null — 개인으로 어림)
     */
    public static List<Line> walk(long creatorId, Map<LocalDate, SettlementRow> persisted, List<ActivityRow> activity, long initialCarry, LocalDate start, LocalDate end,
                                  Rules rules, String payeeType) {
        TreeMap<LocalDate, List<ActivityRow>> sales = new TreeMap<>();
        TreeMap<LocalDate, List<ActivityRow>> refunds = new TreeMap<>();
        for (ActivityRow a : activity) {
            if (a.paidAt() != null && a.settlementMonth() == null) {
                sales.computeIfAbsent(monthOf(a.paidAt()), k -> new ArrayList<>()).add(a);
            }
            if (a.refundedAt() != null && a.refundSettlementMonth() == null) {
                LocalDate natural = a.settlementMonth() == null ? monthOf(a.paidAt()) : monthOf(a.refundedAt());
                refunds.computeIfAbsent(natural, k -> new ArrayList<>()).add(a);
            }
        }
        LocalDate begin = start;
        if (begin == null) {
            begin = min(min(persisted.keySet().stream().min(Comparator.naturalOrder()).orElse(null), sales.isEmpty() ? null : sales.firstKey()),
                    refunds.isEmpty() ? null : refunds.firstKey());
        }
        List<Line> out = new ArrayList<>();
        if (begin == null || begin.isAfter(end)) {
            return out;
        }
        // 시작 달보다 앞 달에 들어갈 입력(앞 달이 모두 굳었다) — 시작 달로 넘긴다
        List<ActivityRow> pendingSales = new ArrayList<>(drainBefore(sales, begin));
        List<ActivityRow> pendingRefunds = new ArrayList<>(drainBefore(refunds, begin));
        long carry = initialCarry;
        for (LocalDate m = begin; !m.isAfter(end); m = m.plusMonths(1)) {
            SettlementRow row = persisted.get(m);
            List<ActivityRow> monthSales = sales.getOrDefault(m, List.of());
            List<ActivityRow> monthRefunds = refunds.getOrDefault(m, List.of());
            if (row != null && row.frozen() != null) {
                // 굳힌 달 — 저장한 값. 이 달에 들어갈 입력(늦게 들어온 결제 등)은 다음에 열린 달로 넘긴다
                pendingSales.addAll(monthSales);
                pendingRefunds.addAll(monthRefunds);
                Frozen f = row.frozen();
                out.add(new Line(creatorId, m, row, row.status(), true, f.salesCount(), f.salesAmount(), f.refundCount(), f.refundAmount(), f.vat(), f.base(),
                        f.feeRatePercent(), f.fee(), f.share(), f.carriedIn(), f.withholding(), f.payout(), f.carriedOut(), f.payeeType(), f.payDate(), f.closesAt(),
                        List.of(), List.of()));
                carry = f.carriedOut();
                continue;
            }
            List<ActivityRow> s = new ArrayList<>(pendingSales);
            s.addAll(monthSales);
            List<ActivityRow> r = new ArrayList<>(pendingRefunds);
            r.addAll(monthRefunds);
            pendingSales.clear();
            pendingRefunds.clear();
            if (s.isEmpty() && r.isEmpty() && carry == 0 && row == null) {
                continue;
            }
            s.sort(Comparator.comparing(ActivityRow::paidAt).thenComparing(ActivityRow::purchaseId));
            r.sort(Comparator.comparing(ActivityRow::refundedAt).thenComparing(ActivityRow::purchaseId));
            long salesAmount = s.stream().mapToLong(ActivityRow::amount).sum();
            long refundAmount = r.stream().mapToLong(ActivityRow::amount).sum();
            EmoticonSettlementMath.Month calc = EmoticonSettlementMath.month(salesAmount, refundAmount, rules.feeRatePercent(), carry, !"BUSINESS".equals(payeeType),
                    rules.payoutMinimum());
            Instant closesAt = monthEnd(m);
            for (ActivityRow a : s) {
                if (a.refundUntil() != null && a.refundUntil().isAfter(closesAt)) {
                    closesAt = a.refundUntil();
                }
            }
            out.add(new Line(creatorId, m, row, row == null ? EmoticonSettlementStatus.ESTIMATED : row.status(), false, s.size(), salesAmount, r.size(), refundAmount,
                    calc.vat(), calc.base(), rules.feeRatePercent(), calc.fee(), calc.share(), calc.carriedIn(), calc.withholding(), calc.payout(), calc.carriedOut(), payeeType,
                    rules.payDate(m), closesAt, List.copyOf(s), List.copyOf(r)));
            carry = calc.carriedOut();
        }
        return out;
    }

    private static List<ActivityRow> drainBefore(TreeMap<LocalDate, List<ActivityRow>> byMonth, LocalDate begin) {
        List<ActivityRow> out = new ArrayList<>();
        var head = byMonth.headMap(begin, false);
        head.values().forEach(out::addAll);
        head.clear();
        return out;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }
}
