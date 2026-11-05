package com.community.holiday.dto;

import com.community.holiday.entity.HolidaySource;
import com.community.holiday.repository.master.SiteHolidayStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 휴일 관리 화면(설정 › 시스템 › 휴일 관리 {@code /settings/holidays}) 응답 — admin-web {@code useHolidayApi.types.ts}.
 */
public final class HolidayAdminView {

    private HolidayAdminView() {
    }

    /**
     * 그 해 화면.
     *
     * @param rows          그 해의 쉬는 날(날짜 차례 — 같은 날은 공휴일 먼저)
     * @param sync          마지막 받기(없으면 null)
     * @param keyConfigured 공공데이터 인증키가 있는가(없으면 공공데이터 설정 안내)
     */
    public record Year(int year, List<Row> rows, Sync sync, boolean keyConfigured) {
    }

    /** 한 줄 — {@code id} 는 글자(JS 숫자 범위와 무관하게) */
    public record Row(String id, LocalDate date, String name, HolidaySource source, boolean substitute, String createdBy, Instant createdAt) {

        public static Row of(SiteHolidayStore.Row r) {
            return new Row(String.valueOf(r.id()), r.date(), r.name(), r.source(), r.substitute(), r.createdByName(), r.createdAt());
        }
    }

    /** 받기 결과 — outcome OK · PARTIAL · NO_KEY · FAILED */
    public record Sync(Instant ranAt, String outcome, List<Integer> appliedYears, List<Integer> failedYears, int changed, String ranBy) {

        public static Sync of(SiteHolidayStore.SyncRow s) {
            return s == null ? null : new Sync(s.ranAt(), s.outcome(), s.appliedYears(), s.failedYears(), s.changed(), s.ranByName());
        }
    }

    /** 회사 휴무일 더하기 본문 */
    public record CreateRequest(String date, String name) {
    }
}
