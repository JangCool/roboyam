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
 * 쉬는 날 판정 하나(값 객체) — <b>사이트 전체가 이것만 쓴다</b>(사용자 지시 2026-11-05). 업무일(영업일) = 토·일이 아니고 사이트 쉬는 날 표({@code site_holidays} —
 * 공공데이터 공휴일 · 기본값 · 회사 휴무일 모두)에 없는 날. 날짜는 한국 날짜(서비스 시간대)다. 시각(업무시간)은 보지 않는다.
 * 쓰는 곳: 공동구매 영업일({@code GroupBuyBusinessDays} — 처리 기한·공급 불가 선택 기한·발송 기한·문의 답변 기한) · 로보티콘 정산 지급일·이체일.
 *
 * <p>표에 그 해의 날이 하나도 없으면(공공데이터를 아직 받지 못한 해) 주말만 거른다 — 그 해를 처음 물을 때 한 번 WARN 을 남긴다(공휴일은 매월 1일 배치 {@code holiday-sync} 가 받는다).
 *
 * @param holidays 쉬는 날(공휴일·대체공휴일·선거일·회사 휴무일)
 */
public record BusinessDayCalendar(Set<LocalDate> holidays) {

    private static final Logger log = LoggerFactory.getLogger(BusinessDayCalendar.class);
    /** 쉬는 날이 없는 해를 이미 알렸다(같은 서버에서 한 번) */
    private static final Set<Integer> WARNED = ConcurrentHashMap.newKeySet();

    public BusinessDayCalendar {
        holidays = Set.copyOf(holidays);
    }

    public static BusinessDayCalendar of(Collection<LocalDate> holidays) {
        return new BusinessDayCalendar(new TreeSet<>(holidays));
    }

    /** 주말만 거르는 달력(쉬는 날 없음) */
    public static BusinessDayCalendar weekendsOnly() {
        return new BusinessDayCalendar(Set.of());
    }

    /** 표에 그 해의 쉬는 날이 하나라도 있는가 */
    public boolean covers(int year) {
        return holidays.stream().anyMatch(d -> d.getYear() == year);
    }

    public boolean businessDay(LocalDate day) {
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        if (!holidays.isEmpty() && !covers(day.getYear()) && WARNED.add(day.getYear())) {
            log.warn("쉬는 날 표에 {}년이 없다 — 주말만 거른다(공공데이터 공휴일 받기 holiday-sync 를 확인)", day.getYear());
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
