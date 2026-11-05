package com.community.holiday.service;

import com.community.holiday.repository.master.PublicHolidayStore;
import org.springframework.stereotype.Service;

/**
 * 공휴일 표 → 업무일 달력({@link BusinessDayCalendar}). 표가 작아(해마다 20줄 안팎) 부를 때마다 통째로 읽는다 — 마이그레이션으로 해를 더하면 서버를 다시 띄우지 않아도 바로 쓴다.
 * 쓰는 곳: 로보티콘 작가 정산 지급 예정일(다음 업무일로 미룸) · 지급 기록의 이체일(업무일만).
 */
@Service
public class PublicHolidayService {

    private final PublicHolidayStore store;

    public PublicHolidayService(PublicHolidayStore store) {
        this.store = store;
    }

    public BusinessDayCalendar calendar() {
        return BusinessDayCalendar.of(store.all());
    }
}
