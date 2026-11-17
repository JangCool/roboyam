package com.community.title.service;

import java.util.List;
import java.util.Set;

/**
 * 기본 칭호 7개의 고정 키(V240 이 넣는다 — member-title-contract 「기본 칭호」)와 그림·칩 값 목록. 작가 칭호(이달의·올해의·명예)는 키로 특별 규칙이 붙는다:
 * 이달의 작가 = 기간 {@code YYYY-MM}·한 달 최대 인원, 올해의 작가 = 기간 {@code YYYY}·그해 이달의 작가만, 명예 작가 = 이달의 작가 수여 때 바로(배치를 기다리지 않는다).
 */
public final class BuiltinTitles {

    public static final String MONTHLY_WRITER = "monthly-writer";
    public static final String YEARLY_WRITER = "yearly-writer";
    public static final String HONORARY_WRITER = "honorary-writer";
    public static final String REVIEWER = "reviewer";
    public static final String REPORTER = "reporter";
    public static final String POPULAR = "popular";
    public static final String ATTENDANCE = "attendance-100";

    /** 대표 칭호 기본값의 차례 — 가진 작가 칭호 중 가장 높은 것(명예 작가 > 올해의 작가 > 이달의 작가) */
    public static final List<String> WRITER_RANK = List.of(HONORARY_WRITER, YEARLY_WRITER, MONTHLY_WRITER);

    /** 화면 도트 그림 키(계약 「아이콘」) */
    public static final Set<String> ICONS = Set.of("pen", "trophy", "wreath", "mag", "mic", "popular", "cal", "medal", "bulb", "heart");
    /** 칩 색 — 보라(수여·심사)·초록(자동) */
    public static final Set<String> CHIPS = Set.of("PURPLE", "GREEN");

    private BuiltinTitles() {
    }
}
