package com.community.point.entity;

import java.util.Optional;

/**
 * 게시판 포인트 등급(point-board-tier-contract) — 게시판 유효 {@code pointMode} 가 끔({@code OFF})이 아니면 이 셋 중 하나다. 등급마다 금액·조건은 포인트 정책 「게시판 등급별 지급」
 * ({@link com.community.point.dto.PointTierRules}). 기존 {@code ON} 은 {@link #NORMAL} 로 바뀌었다(V238).
 */
public enum PointBoardTier {

    /** 일반 — 그 밖의 게시판(쓰면 바로·최소 글자 수는 기존 전역 칸 {@code postPoints}·{@code postMinLength}) */
    NORMAL,
    /** 정보 — 뉴스·핫딜(같은 출처 링크는 한 번만 적립 스위치) */
    INFO,
    /** 핵심 글 — 사용기·팁·강좌 */
    CORE;

    /** 게시판 pointMode 값 — 포인트를 주지 않는 게시판 */
    public static final String OFF = "OFF";

    /** 게시판 유효 pointMode → 등급(끔·모르는 값이면 비었다) */
    public static Optional<PointBoardTier> ofMode(String pointMode) {
        if (pointMode == null) {
            return Optional.empty();
        }
        for (PointBoardTier tier : values()) {
            if (tier.name().equals(pointMode)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
