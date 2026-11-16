package com.community.point.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code PUT /api/admin/settings/points} — 정책 칸 모두(+ 게시판 등급 칸 {@code boardTiers}·스위치 {@code infoSourceOnce}·{@code excludeAiAndOps} — point-board-tier-contract)(admin-web {@code PointSettingsValues} — 지급 13칸 + 커머스 1-3 적립 조건 8칸 + 큰 금액 조정 문턱). 닉네임 변경권 가격은 커머스 1-2 에서
 * 포인트 상점 품목으로 옮겨 받지 않는다(보내도 무시). 칸마다 정수·범위·켬끔을 서비스가 검사해 틀린 칸을 모은다({@code fields.<칸>}).
 * 숫자로 받는 이유: 소수({@code 1.5})·빈 값도 칸 오류로 돌려주려고(형식 오류 {@code MALFORMED_REQUEST} 가 아니라).
 */
public record PointSettingsRequest(Number attendancePoints, Number postPoints, Number postDailyCap, Number commentPoints, Number commentDailyCap,
                                   Number popularRewardPoints, Number popularRewardTopN, Number likeBonusThreshold, Number likeBonusPoints,
                                   Number likeBonusDailyLimit, Number dealEndRequestPoints, Number dealEndRequestDailyLimit, Number signupPoints,
                                   Number postMinLength, Number commentMinLength, Boolean commentOwnPostExcluded, Boolean commentOncePerPost,
                                   Number writeIntervalSeconds, Number dailyTotalCap, Number attendanceStreakDays, Number attendanceStreakBonus,
                                   Number adjustLargeThreshold, Map<String, Tier> boardTiers, Boolean infoSourceOnce, Boolean excludeAiAndOps) {

    /**
     * 게시판 등급 하나의 칸(point-board-tier-contract) — {@code boardTiers.NORMAL|INFO|CORE}. 일반 등급의 {@code postPoints}·{@code minLength} 는 받아도 무시한다(전역 칸
     * {@code postPoints}·{@code postMinLength} 를 쓴다 — 화면이 그렇게 묶었다).
     */
    public record Tier(Number postPoints, Number likeBonusPoints, Number featuredPoints, Number minLength, Number minImages, Number holdHours) {

        /** 칸 이름 → 받은 값(빠진 칸은 null) */
        public Map<String, Number> numbers() {
            Map<String, Number> map = new LinkedHashMap<>();
            map.put("postPoints", postPoints);
            map.put("likeBonusPoints", likeBonusPoints);
            map.put("featuredPoints", featuredPoints);
            map.put("minLength", minLength);
            map.put("minImages", minImages);
            map.put("holdHours", holdHours);
            return map;
        }
    }

    /** 칸 없이 만든 빈 요청(본문이 없을 때) */
    public static PointSettingsRequest empty() {
        return new PointSettingsRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    /** 숫자 칸 이름 → 받은 값(빠진 칸은 null) — 순서는 화면·요청 순서. 큰 금액 조정 문턱 포함 */
    public Map<String, Number> numbers() {
        Map<String, Number> map = new LinkedHashMap<>();
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
        map.put("writeIntervalSeconds", writeIntervalSeconds);
        map.put("dailyTotalCap", dailyTotalCap);
        map.put("attendanceStreakDays", attendanceStreakDays);
        map.put("attendanceStreakBonus", attendanceStreakBonus);
        map.put("adjustLargeThreshold", adjustLargeThreshold);
        return map;
    }

    /** 켬·끔 칸 이름 → 받은 값(빠진 칸은 null) */
    public Map<String, Boolean> switches() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("commentOwnPostExcluded", commentOwnPostExcluded);
        map.put("commentOncePerPost", commentOncePerPost);
        map.put("infoSourceOnce", infoSourceOnce);
        map.put("excludeAiAndOps", excludeAiAndOps);
        return map;
    }
}
