package com.community.point.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게시판 등급 하나의 금액·조건(point-board-tier-contract 「등급별 칸」) — 칸마다 0~10,000 정수. 0 이면 그 일로는 주지 않거나(금액) 조건이 없다(최소 글자·사진·보류 시간).
 *
 * @param postPoints      쓰면 바로(P) — 하루 총 한도({@code dailyTotalCap})에 든다
 * @param likeBonusPoints 글이 추천 문턱({@code likeBonusThreshold})을 채우면 주는 보너스(P) — 한도 밖. 댓글 추천 보너스는 전역 {@code likeBonusPoints}
 * @param featuredPoints  운영자가 우수 글로 선정하면 주는 포인트(P) — 한도 밖
 * @param minLength       적립 최소 글자 수(서식·공백 뺀 글자)
 * @param minImages       적립 사진 최소(장)
 * @param holdHours       추천 문턱 보너스·우수 글 보상의 보류(시간) — 0 이면 바로 준다
 */
public record PointTierValues(int postPoints, int likeBonusPoints, int featuredPoints, int minLength, int minImages, int holdHours) {

    /** 칸 이름 — 화면·요청 순서 */
    public static final List<String> FIELDS = List.of("postPoints", "likeBonusPoints", "featuredPoints", "minLength", "minImages", "holdHours");
    /** 칸마다 [최소, 최대] */
    public static final int[] LIMIT = {0, 10_000};

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("postPoints", postPoints);
        map.put("likeBonusPoints", likeBonusPoints);
        map.put("featuredPoints", featuredPoints);
        map.put("minLength", minLength);
        map.put("minImages", minImages);
        map.put("holdHours", holdHours);
        return map;
    }

    /** 칸 이름 → 값(모든 칸이 있어야 한다) */
    public static PointTierValues of(Map<String, ?> values) {
        return new PointTierValues(i(values, "postPoints"), i(values, "likeBonusPoints"), i(values, "featuredPoints"), i(values, "minLength"), i(values, "minImages"),
                i(values, "holdHours"));
    }

    private static int i(Map<String, ?> values, String field) {
        return (Integer) values.get(field);
    }
}
