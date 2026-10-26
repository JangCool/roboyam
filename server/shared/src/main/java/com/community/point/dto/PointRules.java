package com.community.point.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 포인트 정책 13칸(admin-web {@code PointSettingsValues}, 칸 순서 = 화면·요청 순서). 0 이면 그 일로는 지급하지 않는다(추천 보너스 문턱은 1 이상).
 * 닉네임 변경권 가격은 포인트 상점 품목 가격이 정본이라 여기 없다(커머스 1-2 결정 2 — V86 이 {@code nickname_ticket_price} 를 옮기고 지웠다).
 * 범위({@link #LIMITS})는 화면 {@code util-point.ts} 의 {@code LIMITS}·V51 의 CHECK 와 같다.
 */
public record PointRules(int attendancePoints, int postPoints, int postDailyCap, int commentPoints, int commentDailyCap, int popularRewardPoints,
                         int popularRewardTopN, int likeBonusThreshold, int likeBonusPoints, int likeBonusDailyLimit, int dealEndRequestPoints,
                         int dealEndRequestDailyLimit, int signupPoints) {

    /** 칸 이름 — 화면·요청 순서 */
    public static final List<String> FIELDS = List.of("attendancePoints", "postPoints", "postDailyCap", "commentPoints", "commentDailyCap", "popularRewardPoints",
            "popularRewardTopN", "likeBonusThreshold", "likeBonusPoints", "likeBonusDailyLimit", "dealEndRequestPoints", "dealEndRequestDailyLimit", "signupPoints");

    /** 칸마다 [최소, 최대] */
    public static final Map<String, int[]> LIMITS = limits();

    /** 정책 행이 없을 때의 기본값(V51 기본값과 같다) */
    public static final PointRules DEFAULTS = new PointRules(10, 20, 5, 5, 20, 100, 5, 5, 10, 3, 3, 5, 0);

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
        return map;
    }

    /** 칸 이름 → 값의 맵에서 만든다(모든 칸이 있어야 한다) */
    public static PointRules of(Map<String, Integer> values) {
        return new PointRules(values.get("attendancePoints"), values.get("postPoints"), values.get("postDailyCap"), values.get("commentPoints"),
                values.get("commentDailyCap"), values.get("popularRewardPoints"), values.get("popularRewardTopN"), values.get("likeBonusThreshold"),
                values.get("likeBonusPoints"), values.get("likeBonusDailyLimit"), values.get("dealEndRequestPoints"), values.get("dealEndRequestDailyLimit"),
                values.get("signupPoints"));
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
        return Map.copyOf(map);
    }
}
