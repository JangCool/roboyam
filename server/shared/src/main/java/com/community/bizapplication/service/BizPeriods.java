package com.community.bizapplication.service;

import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 기간 변환 — 서버는 반열림 {@code [시작, 끝)} 순간(분 단위)으로 저장한다. 공개 화면은 한국 날짜 {@code startDate}·{@code endDate}(끝 포함)로 주고받는다:
 * {@code [startDate 00:00 KST, endDate + 1 00:00 KST)}.
 */
public final class BizPeriods {

    /** 기간 — 반열림 */
    public record Period(Instant start, Instant end) {

        public boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }
    }

    private BizPeriods() {
    }

    /** 한국 날짜 0시 */
    public static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 끝 포함 날짜 범위 → 반열림 기간 */
    public static Period ofDates(LocalDate startDate, LocalDate endDateInclusive) {
        return new Period(startOf(startDate), startOf(endDateInclusive.plusDays(1)));
    }

    /** 시작 날짜(한국) */
    public static LocalDate startDate(Instant start) {
        return ServiceTime.dateOf(start);
    }

    /** 끝 날짜(한국, 끝 포함) — 끝 바로 앞 순간의 날짜 */
    public static LocalDate endDate(Instant end) {
        return ServiceTime.dateOf(end.minusNanos(1));
    }

    /** 알림 문구용 한국 시각 {@code yyyy-MM-dd HH:mm} */
    public static String kstMinute(Instant at) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ServiceTime.ZONE).format(at);
    }

    /** 분 단위인가(초·소수 0) */
    public static boolean minuteAligned(Instant at) {
        return at.getEpochSecond() % 60 == 0 && at.getNano() == 0;
    }

    /**
     * 시작이 지난 신청을 「같은 길이로 뒤로 옮기기」(F6) — 다음 날(한국) 0시부터 같은 길이. 화면이 이 값을 보낸다(서버는 그대로는 승인하지 않는다 — 400 {@code fields.period}).
     */
    public static Period shiftedForward(Period period, Instant now) {
        Instant start = startOf(ServiceTime.dateOf(now).plusDays(1));
        return new Period(start, start.plus(java.time.Duration.between(period.start(), period.end())));
    }
}
