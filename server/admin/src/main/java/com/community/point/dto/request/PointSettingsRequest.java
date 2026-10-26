package com.community.point.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code PUT /api/admin/settings/points} — 13칸 모두(admin-web {@code PointSettingsValues} — 닉네임 변경권 가격은 커머스 1-2 에서 포인트 상점 품목으로 옮겨 받지 않는다, 보내도 무시). 칸마다 정수·범위를 서비스가 검사해 틀린 칸을 모은다({@code fields.<칸>}).
 * 숫자로 받는 이유: 소수({@code 1.5})·빈 값도 칸 오류로 돌려주려고(형식 오류 {@code MALFORMED_REQUEST} 가 아니라).
 */
public record PointSettingsRequest(Number attendancePoints, Number postPoints, Number postDailyCap, Number commentPoints, Number commentDailyCap,
                                   Number popularRewardPoints, Number popularRewardTopN, Number likeBonusThreshold, Number likeBonusPoints,
                                   Number likeBonusDailyLimit, Number dealEndRequestPoints, Number dealEndRequestDailyLimit, Number signupPoints) {

    /** 칸 이름 → 받은 값(빠진 칸은 null) — 순서는 화면·요청 순서 */
    public Map<String, Number> toMap() {
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
        return map;
    }
}
