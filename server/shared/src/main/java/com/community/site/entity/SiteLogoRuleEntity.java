package com.community.site.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로고 보이기 규칙의 기념일 한 줄 — V249 {@code site_logo_rules}, docs/features/site-logo-library-contract.md. {@code sortOrder} 가 작은 줄이 먼저다(위 줄이 이긴다).
 * 날짜 정하는 법은 둘 — {@link #MODE_DATES}(시작~끝, 끝 날 포함. {@code yearly} 면 월·일 {@code MM-DD} 이고 끝이 시작보다 앞이면 해를 넘기는 기간, 아니면 {@code YYYY-MM-DD})
 * · {@link #MODE_HOLIDAY}(쉬는 날 표 {@code site_holidays} 의 이름 — 그 이름의 날마다). 오늘 걸리는지는 {@code SiteLogoRules.matches} 가 본다.
 * 규칙 저장은 줄 전체를 갈아 끼운다(행을 고치지 않는다).
 */
@Entity
@Table(name = "site_logo_rules")
public class SiteLogoRuleEntity {

    public static final String MODE_DATES = "DATES";
    public static final String MODE_HOLIDAY = "HOLIDAY";

    /** 이름 길이 한도 */
    public static final int NAME_MAX = 30;
    /** 쉬는 날 이름 길이 한도 — {@code site_holidays.name} 과 같다 */
    public static final int HOLIDAY_NAME_MAX = 40;

    @Id
    private Long id;

    private int sortOrder;
    private String name;
    private boolean enabled;
    private String mode;
    private String startDate;
    private String endDate;
    private boolean yearly;
    private String holidayName;
    private Long bundleId;
    private Instant createdAt;

    protected SiteLogoRuleEntity() {
    }

    /** 날짜 직접 — {@code yearly} 면 {@code MM-DD}, 아니면 {@code YYYY-MM-DD}(모양 검사는 부르는 쪽) */
    public static SiteLogoRuleEntity dates(long id, int sortOrder, String name, boolean enabled, String startDate, String endDate, boolean yearly, long bundleId,
                                           Instant now) {
        SiteLogoRuleEntity rule = base(id, sortOrder, name, enabled, MODE_DATES, bundleId, now);
        rule.startDate = startDate;
        rule.endDate = endDate;
        rule.yearly = yearly;
        return rule;
    }

    /** 쉬는 날 표의 이름 */
    public static SiteLogoRuleEntity holiday(long id, int sortOrder, String name, boolean enabled, String holidayName, long bundleId, Instant now) {
        SiteLogoRuleEntity rule = base(id, sortOrder, name, enabled, MODE_HOLIDAY, bundleId, now);
        rule.holidayName = holidayName;
        return rule;
    }

    private static SiteLogoRuleEntity base(long id, int sortOrder, String name, boolean enabled, String mode, long bundleId, Instant now) {
        SiteLogoRuleEntity rule = new SiteLogoRuleEntity();
        rule.id = id;
        rule.sortOrder = sortOrder;
        rule.name = name;
        rule.enabled = enabled;
        rule.mode = mode;
        rule.bundleId = bundleId;
        rule.createdAt = now;
        return rule;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMode() {
        return mode;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public boolean isYearly() {
        return yearly;
    }

    public String getHolidayName() {
        return holidayName;
    }

    public Long getBundleId() {
        return bundleId;
    }
}
