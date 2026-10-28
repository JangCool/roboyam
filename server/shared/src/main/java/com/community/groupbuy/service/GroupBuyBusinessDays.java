package com.community.groupbuy.service;

import com.community.zconfiguration.time.ServiceTime;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;

/**
 * 영업일(계약 결정 8) — 주말 + 관리자가 넣는 휴일 목록(공구 설정 {@code holidays}). 날짜는 한국 날짜. 처리 기한 3영업일(10-8 · P35)·공급 불가 선택 기한(결정 53)이 같이 쓴다.
 */
public final class GroupBuyBusinessDays {

    /** 처리 기한 단계 — 2영업일 경고, 3영업일 심각(commerce.md 10-8 「처리 기한 3영업일」·19절) */
    public enum Level {
        NONE, WARNING, CRITICAL
    }

    private GroupBuyBusinessDays() {
    }

    public static boolean businessDay(LocalDate day, Collection<LocalDate> holidays) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(day);
    }

    /** {@code from} 다음 영업일부터 세어 {@code n} 번째 영업일 */
    public static LocalDate plus(LocalDate from, int n, Collection<LocalDate> holidays) {
        LocalDate day = from;
        int counted = 0;
        while (counted < n) {
            day = day.plusDays(1);
            if (businessDay(day, holidays)) {
                counted++;
            }
        }
        return day;
    }

    /** {@code from} 다음 날부터 {@code to} 까지(양 끝 중 뒤만 포함)의 영업일 수 */
    public static int elapsed(LocalDate from, LocalDate to, Collection<LocalDate> holidays) {
        int n = 0;
        for (LocalDate day = from.plusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            if (businessDay(day, holidays)) {
                n++;
            }
        }
        return n;
    }

    /** 시작 시각(한국 날짜)부터 {@code n} 영업일째 날이 끝나는 시각(다음 날 0시 KST) */
    public static Instant endOfBusinessDays(Instant start, int n, Collection<LocalDate> holidays) {
        return plus(ServiceTime.dateOf(start), n, holidays).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 처리 기한 단계 — 시작(취소 요청한 날 · 반품은 돌려받은 날)부터 지난 영업일 2 이상 경고, 3 이상 심각 */
    public static Level deadlineLevel(Instant start, Instant now, Collection<LocalDate> holidays) {
        int days = elapsed(ServiceTime.dateOf(start), ServiceTime.dateOf(now), holidays);
        return days >= 3 ? Level.CRITICAL : days >= 2 ? Level.WARNING : Level.NONE;
    }
}
