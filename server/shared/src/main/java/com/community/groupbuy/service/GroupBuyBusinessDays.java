package com.community.groupbuy.service;

import com.community.holiday.service.BusinessDayCalendar;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;

/**
 * 영업일(계약 결정 8) — <b>쉬는 날 = 사이트 휴일 표 하나</b>(사용자 지시 2026-11-05): 토·일 + {@code site_holidays} 의 모든 날(공공데이터 공휴일·기본값·회사 휴무일). 판정은
 * {@link BusinessDayCalendar} 하나가 하고 여기는 공구의 셈(n 영업일째 · 지난 영업일 수 · 처리 기한 단계)만 한다. 날짜는 한국 날짜.
 * 처리 기한 3영업일(10-8 · P35)·공급 불가 선택 기한(결정 53)·발송 기한·문의 답변 기한이 같이 쓴다. 달력은 공구 설정 읽기({@code GroupBuyStore#settings})가 함께 준다.
 *
 * <p>이미 정해 저장한 기한(공급 불가 선택 기한 등)은 휴일이 바뀌어도 다시 계산하지 않는다 — 아직 정해지지 않은 것만 지금 표를 본다.
 * {@code Collection<LocalDate>} 를 받는 판은 시험·옛 부르는 쪽을 위한 같은 셈이다(그 날들로 달력을 만든다).
 */
public final class GroupBuyBusinessDays {

    /** 처리 기한 단계 — 2영업일 경고, 3영업일 심각(commerce.md 10-8 「처리 기한 3영업일」·19절) */
    public enum Level {
        NONE, WARNING, CRITICAL
    }

    private GroupBuyBusinessDays() {
    }

    public static boolean businessDay(LocalDate day, BusinessDayCalendar calendar) {
        return calendar.businessDay(day);
    }

    /** {@code from} 다음 영업일부터 세어 {@code n} 번째 영업일 */
    public static LocalDate plus(LocalDate from, int n, BusinessDayCalendar calendar) {
        LocalDate day = from;
        int counted = 0;
        while (counted < n) {
            day = day.plusDays(1);
            if (calendar.businessDay(day)) {
                counted++;
            }
        }
        return day;
    }

    /** {@code from} 다음 날부터 {@code to} 까지(양 끝 중 뒤만 포함)의 영업일 수 */
    public static int elapsed(LocalDate from, LocalDate to, BusinessDayCalendar calendar) {
        int n = 0;
        for (LocalDate day = from.plusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            if (calendar.businessDay(day)) {
                n++;
            }
        }
        return n;
    }

    /**
     * {@link #elapsed}{@code (d, today) >= n} 인 날 {@code d} 의 경계 — 그런 {@code d} 는 모두 돌려준 날보다 앞이고, 앞인 날은 모두 그렇다({@code n >= 1}). {@code today} 부터
     * 거꾸로 세어 {@code n} 번째 영업일이다(지난 영업일 수는 날이 뒤로 갈수록 줄기만 하므로). 목록이 「기한 지남」을 DB 에서 고르는 시각 경계({@code 작성 시각 < 이 날 0시 KST})로 쓴다.
     */
    public static LocalDate elapsedCutoff(LocalDate today, int n, BusinessDayCalendar calendar) {
        int counted = 0;
        LocalDate day = today;
        for (int guard = 0; guard < 3_660; guard++, day = day.minusDays(1)) {
            if (calendar.businessDay(day) && ++counted >= n) {
                return day;
            }
        }
        return day; // 10년 내내 쉬는 날인 달력은 없다 — 그래도 끝나게
    }

    /** 시작 시각(한국 날짜)부터 {@code n} 영업일째 날이 끝나는 시각(다음 날 0시 KST) */
    public static Instant endOfBusinessDays(Instant start, int n, BusinessDayCalendar calendar) {
        return plus(ServiceTime.dateOf(start), n, calendar).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 처리 기한 단계 — 시작(취소 요청한 날 · 반품은 돌려받은 날)부터 지난 영업일 2 이상 경고, 3 이상 심각 */
    public static Level deadlineLevel(Instant start, Instant now, BusinessDayCalendar calendar) {
        int days = elapsed(ServiceTime.dateOf(start), ServiceTime.dateOf(now), calendar);
        return days >= 3 ? Level.CRITICAL : days >= 2 ? Level.WARNING : Level.NONE;
    }

    // ── 날 목록 판(같은 셈) ──

    public static boolean businessDay(LocalDate day, Collection<LocalDate> holidays) {
        return businessDay(day, BusinessDayCalendar.of(holidays));
    }

    public static LocalDate plus(LocalDate from, int n, Collection<LocalDate> holidays) {
        return plus(from, n, BusinessDayCalendar.of(holidays));
    }

    public static int elapsed(LocalDate from, LocalDate to, Collection<LocalDate> holidays) {
        return elapsed(from, to, BusinessDayCalendar.of(holidays));
    }

    public static Instant endOfBusinessDays(Instant start, int n, Collection<LocalDate> holidays) {
        return endOfBusinessDays(start, n, BusinessDayCalendar.of(holidays));
    }

    public static Level deadlineLevel(Instant start, Instant now, Collection<LocalDate> holidays) {
        return deadlineLevel(start, now, BusinessDayCalendar.of(holidays));
    }
}
