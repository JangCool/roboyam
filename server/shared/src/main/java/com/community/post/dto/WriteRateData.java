package com.community.post.dto;

import java.time.Instant;

/**
 * 작성 속도 판정에 쓰는 최근 기록 — 같은 작성자(회원 ID 또는 비회원 IP)가 창 안에서 쓴 수와 마지막 시각.
 *
 * @param countInWindow 창(하루) 안에서 쓴 수(지운 것 포함)
 * @param lastAt        가장 최근에 쓴 시각 — 없으면 null
 */
public record WriteRateData(long countInWindow, Instant lastAt) {
}
