package com.community.point.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 포인트 정책 — 관리자 정책 화면의 지급·조건 칸(admin-web {@code PointSettingsValues}, 칸 순서 = 화면·요청 순서). 숫자 0 이면 그 일로는 지급하지 않는다(추천 보너스 문턱은 1 이상),
 * 조건 칸은 0 · false 면 그 조건이 없다. 닉네임 변경권 가격은 포인트 상점 품목 가격이 정본이라 여기 없다(커머스 1-2 결정 2). 큰 금액 조정 문턱은 {@link PointLotRules#adjustLargeThreshold()}.
 * 범위({@link #LIMITS})는 화면 {@code util-point.ts} 의 {@code LIMITS}·V51·V89 의 CHECK 와 같다.
 *
 * <p>커머스 1-3(commerce.md 6-2, V89): {@code postMinLength}·{@code commentMinLength}(서식 태그·공백을 뺀 글자 수) · {@code commentOwnPostExcluded} · {@code commentOncePerPost} ·
 * {@code writeIntervalSeconds}(직전에 포인트를 받은 글·댓글과의 간격) · {@code dailyTotalCap}(활동 적립 하루 총 한도) · {@code attendanceStreakDays}·{@code attendanceStreakBonus}.
 */
public record PointRules(int attendancePoints, int postPoints, int postDailyCap, int commentPoints, int commentDailyCap, int popularRewardPoints,
                         int popularRewardTopN, int likeBonusThreshold, int likeBonusPoints, int likeBonusDailyLimit, int dealEndRequestPoints,
                         int dealEndRequestDailyLimit, int signupPoints, int postMinLength, int commentMinLength, boolean commentOwnPostExcluded,
                         boolean commentOncePerPost, int writeIntervalSeconds, int dailyTotalCap, int attendanceStreakDays, int attendanceStreakBonus) {

    /** 숫자 칸 이름 — 화면·요청 순서 */
    public static final List<String> FIELDS = List.of("attendancePoints", "postPoints", "postDailyCap", "commentPoints", "commentDailyCap", "popularRewardPoints",
            "popularRewardTopN", "likeBonusThreshold", "likeBonusPoints", "likeBonusDailyLimit", "dealEndRequestPoints", "dealEndRequestDailyLimit", "signupPoints",
            "postMinLength", "commentMinLength", "writeIntervalSeconds", "dailyTotalCap", "attendanceStreakDays", "attendanceStreakBonus");

    /** 켬·끔 칸 이름 */
    public static final List<String> BOOLEAN_FIELDS = List.of("commentOwnPostExcluded", "commentOncePerPost");

    /** 숫자 칸마다 [최소, 최대] */
    public static final Map<String, int[]> LIMITS = limits();

    /** 정책 행이 없을 때의 기본값(V51·V89 기본값과 같다) */
    public static final PointRules DEFAULTS = new PointRules(10, 20, 5, 5, 20, 100, 5, 5, 10, 3, 3, 5, 0, 50, 10, true, true, 30, 200, 7, 20);

    /** 칸 이름 → 값(숫자는 Integer, 켬·끔은 Boolean) — 화면 순서 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("attendancePoints", attendancePoints);
        map.put("postPoints", postPoints);
        map.put("postDailyCap", postDailyCap);
        map.put("commentPoints", commentPoints);
        map.put("commentDailyCap", commentDailyCap);
        map.put("popularRewardPoints", popularRewardPoints);
        map.put("popularRewardTopN", popularRewardTopN);
        map.put("likeBonusThreshold", likeBonusThreshold);
        map.put("likeBonusPoints", likeBonusPoints);
        map.put("likeBonusDailyLimit", likeBonusDailyLimit);
        map.put("dealEndRequestPoints", dealEndRequestPoints);
        map.put("dealEndRequestDailyLimit", dealEndRequestDailyLimit);
        map.put("signupPoints", signupPoints);
        map.put("postMinLength", postMinLength);
        map.put("commentMinLength", commentMinLength);
        map.put("commentOwnPostExcluded", commentOwnPostExcluded);
        map.put("commentOncePerPost", commentOncePerPost);
        map.put("writeIntervalSeconds", writeIntervalSeconds);
        map.put("dailyTotalCap", dailyTotalCap);
        map.put("attendanceStreakDays", attendanceStreakDays);
        map.put("attendanceStreakBonus", attendanceStreakBonus);
        return map;
    }

    /** 칸 이름 → 값의 맵에서 만든다(모든 칸이 있어야 한다 — 숫자는 Integer, 켬·끔은 Boolean) */
    public static PointRules of(Map<String, ?> values) {
        return new PointRules(i(values, "attendancePoints"), i(values, "postPoints"), i(values, "postDailyCap"), i(values, "commentPoints"),
                i(values, "commentDailyCap"), i(values, "popularRewardPoints"), i(values, "popularRewardTopN"), i(values, "likeBonusThreshold"),
                i(values, "likeBonusPoints"), i(values, "likeBonusDailyLimit"), i(values, "dealEndRequestPoints"), i(values, "dealEndRequestDailyLimit"),
                i(values, "signupPoints"), i(values, "postMinLength"), i(values, "commentMinLength"), b(values, "commentOwnPostExcluded"),
                b(values, "commentOncePerPost"), i(values, "writeIntervalSeconds"), i(values, "dailyTotalCap"), i(values, "attendanceStreakDays"),
                i(values, "attendanceStreakBonus"));
    }

    /** 연속 출석 보너스가 켜져 있나(일수·보너스 둘 다 1 이상) */
    public boolean streakBonusEnabled() {
        return attendanceStreakDays > 0 && attendanceStreakBonus > 0;
    }

    private static int i(Map<String, ?> values, String field) {
        return (Integer) values.get(field);
    }

    private static boolean b(Map<String, ?> values, String field) {
        return (Boolean) values.get(field);
    }

    private static Map<String, int[]> limits() {
        Map<String, int[]> map = new LinkedHashMap<>();
        map.put("attendancePoints", new int[] {0, 10000});
        map.put("postPoints", new int[] {0, 10000});
        map.put("postDailyCap", new int[] {0, 1000});
        map.put("commentPoints", new int[] {0, 10000});
        map.put("commentDailyCap", new int[] {0, 1000});
        map.put("popularRewardPoints", new int[] {0, 10000});
        map.put("popularRewardTopN", new int[] {0, 50});
        map.put("likeBonusThreshold", new int[] {1, 1000});
        map.put("likeBonusPoints", new int[] {0, 10000});
        map.put("likeBonusDailyLimit", new int[] {0, 1000});
        map.put("dealEndRequestPoints", new int[] {0, 10000});
        map.put("dealEndRequestDailyLimit", new int[] {0, 1000});
        map.put("signupPoints", new int[] {0, 10000});
        map.put("postMinLength", new int[] {0, 10000});
        map.put("commentMinLength", new int[] {0, 1000});
        map.put("writeIntervalSeconds", new int[] {0, 3600});
        map.put("dailyTotalCap", new int[] {0, 100000});
        map.put("attendanceStreakDays", new int[] {0, 365});
        map.put("attendanceStreakBonus", new int[] {0, 10000});
        return Map.copyOf(map);
    }
}
