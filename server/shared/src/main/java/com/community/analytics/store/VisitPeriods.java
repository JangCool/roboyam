package com.community.analytics.store;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * 주·월 순방문자(site-analytics-contract 「3단계」 결정 ④)의 달력 기간 — 한국 날짜, 주는 월요일 시작(PostgreSQL {@code date_trunc('week')} 와 같다), 달은 1일.
 * 쓰는 쪽(api 몰아쓰기)과 읽는 쪽(admin 순방문자·월별 보기)이 같은 규칙을 쓴다. 숫자 표({@code analytics_unique_counts}, analytics V8)의 대상·종류 값도 여기에 둔다.
 */
public final class VisitPeriods {

    /** 숫자 표 대상 — 방문자(브라우저) */
    public static final String VISITOR = "VISITOR";
    /** 숫자 표 대상 — 회원(로그인한 회원 번호) */
    public static final String MEMBER = "MEMBER";
    /** 숫자 표 종류 — 주(월요일 시작) */
    public static final String WEEK = "WEEK";
    /** 숫자 표 종류 — 달 */
    public static final String MONTH = "MONTH";

    private VisitPeriods() {
    }

    /** 그 날이 든 주의 월요일 */
    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 그 날이 든 달의 1일 */
    public static LocalDate monthStart(LocalDate day) {
        return day.withDayOfMonth(1);
    }

    /**
     * 방문자 해시(64자 16진 HMAC — {@code BlindIndex})의 앞 16바이트를 uuid 로 — PostgreSQL {@code substr(visitor_hash, 1, 32)::uuid} 와 같은 값(삭제 요청 크론이 그렇게 바꿔 지운다).
     * 16진 32자보다 짧거나 16진이 아니면 null.
     */
    public static UUID visitorId(String visitorHash) {
        if (visitorHash == null || visitorHash.length() < 32) {
            return null;
        }
        try {
            long msb = Long.parseUnsignedLong(visitorHash.substring(0, 16), 16);
            long lsb = Long.parseUnsignedLong(visitorHash.substring(16, 32), 16);
            return new UUID(msb, lsb);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
