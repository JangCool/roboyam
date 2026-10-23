package com.community.commerce.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 커머스 설정 › 요청 제한 — 요청 종류 차례 고정(공구 참여 · 포인트 충전 · 상점 구매 · 환불 신청). 마지막 저장은 네 줄 중 가장 늦게 바뀐 줄의 시각·운영자(저장한 적 없으면 null).
 *
 * @param updatedBy 바꾼 운영자 이름(운영자가 지워졌으면 null)
 */
public record RequestLimitsView(List<Item> items, Instant updatedAt, String updatedBy) {

    /** @param limitPerMinute 회원 1명 1분 한도(0 = 제한 없음) */
    public record Item(String action, int limitPerMinute, int defaultLimit) {
    }
}
