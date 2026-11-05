package com.community.holiday.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 업무일 달력(값 객체) — 업무일 = 토·일이 아니고 공휴일 표({@code public_holidays})에 없는 날. 날짜는 한국 날짜(서비스 시간대)다. 시각(업무시간)은 보지 않는다.
 *
 * <p>공휴일 표에 그 해가 없으면(시드를 넣지 않은 해) 주말만 거른다 — 그 해를 처음 물을 때 한 번 WARN 을 남긴다(해마다 다음 해 공휴일을 마이그레이션으로 더해야 한다).
 * 공구의 영업일({@code GroupBuyBusinessDays} — 관리자가 넣는 공구 설정 휴일 목록)과는 따로다(emoticon-contract 「후속 결정 반영(2026-11-05)」 미확정).
 *
 * @param holidays 공휴일(대체공휴일·선거일 포함)
 */
public record BusinessDayCalendar(Set<LocalDate> holidays) {

    private static final Logger log = LoggerFactory.getLogger(BusinessDayCalendar.class);
    /** 공휴일이 없는 해를 이미 알렸다(같은 서버에서 한 번) */
    private static final Set<Integer> WARNED = ConcurrentHashMap.newKeySet();

    public BusinessDayCalendar {
        holidays = Set.copyOf(holidays);
    }

    public static BusinessDayCalendar of(Collection<LocalDate> holidays) {
        return new BusinessDayCalendar(new TreeSet<>(holidays));
    }

    /** 주말만 거르는 달력(공휴일 없음) */
    public static BusinessDayCalendar weekendsOnly() {
        return new BusinessDayCalendar(Set.of());
    }

    /** 공휴일 표에 그 해의 공휴일이 하나라도 있는가 */
    public boolean covers(int year) {
        return holidays.stream().anyMatch(d -> d.getYear() == year);
    }

    public boolean businessDay(LocalDate day) {
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        if (!holidays.isEmpty() && !covers(day.getYear()) && WARNED.add(day.getYear())) {
            log.warn("공휴일 표에 {}년이 없다 — 주말만 거른다(public_holidays 에 그 해를 더해야 한다)", day.getYear());
        }
        return !holidays.contains(day);
    }

    /** 그날이 업무일이면 그날, 아니면 다음 업무일 */
    public LocalDate onOrAfter(LocalDate day) {
        LocalDate d = day;
        while (!businessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }
}
