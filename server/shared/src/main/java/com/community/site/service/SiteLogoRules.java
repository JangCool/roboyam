package com.community.site.service;

import com.community.site.entity.SiteLogoRuleEntity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 로고 보이기 규칙의 기념일 판정 — 순수 함수(상태·DB 없음). docs/features/site-logo-library-contract.md 「정책」, 화면 쪽 같은 규칙은 admin-web {@code UtilSiteLogoRule.matches}.
 * 날짜 글자는 {@code YYYY-MM-DD}(한 번) 또는 {@code MM-DD}(매년 반복 — 2월 29일 허용)이고, 글자 차례가 곧 날짜 차례라 글자로 견준다.
 */
public final class SiteLogoRules {

    private static final Pattern MONTH_DAY = Pattern.compile("\\d{2}-\\d{2}");
    private static final DateTimeFormatter STRICT_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    /** 매년 반복의 월·일이 있는 날인지 볼 때 쓰는 해 — 윤년이라 02-29 가 통과한다 */
    private static final String LEAP_YEAR = "2024-";

    private SiteLogoRules() {
    }

    /**
     * 그 줄이 그날 걸리는가(사용 켬·끔은 보지 않는다).
     *
     * @param holidayNames 그날 쉬는 날 표에 있는 이름들
     */
    public static boolean matches(SiteLogoRuleEntity rule, LocalDate today, Set<String> holidayNames) {
        if (SiteLogoRuleEntity.MODE_HOLIDAY.equals(rule.getMode())) {
            return rule.getHolidayName() != null && holidayNames.contains(rule.getHolidayName());
        }
        String start = rule.getStartDate();
        String end = rule.getEndDate();
        if (start == null || end == null) {
            return false;
        }
        String day = today.format(STRICT_DATE);
        if (rule.isYearly()) {
            String monthDay = day.substring(5);
            // 끝이 시작보다 앞이면 해를 넘기는 기간(12-30 ~ 01-02)
            return start.compareTo(end) <= 0 ? start.compareTo(monthDay) <= 0 && monthDay.compareTo(end) <= 0
                    : monthDay.compareTo(start) >= 0 || monthDay.compareTo(end) <= 0;
        }
        return start.compareTo(day) <= 0 && day.compareTo(end) <= 0;
    }

    /** {@code YYYY-MM-DD} 로 읽히는 실제 날짜인가 */
    public static Optional<LocalDate> date(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text, STRICT_DATE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** {@code MM-DD} 로 읽히는 실제 월·일인가(02-29 허용) */
    public static boolean monthDay(String text) {
        return text != null && MONTH_DAY.matcher(text).matches() && date(LEAP_YEAR + text).isPresent();
    }
}
