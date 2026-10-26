package com.community.point.dto.request;

/**
 * {@code POST /api/admin/members/{id}/points} — 검사는 서비스가 칸을 모아 한다(정수 아님·범위 밖·빈 사유를 한 번에 {@code fields.delta|reason} 으로).
 * 무상(적립) 포인트만 바꾼다 — 유상을 고르는 칸은 없고, 알 수 없는 칸은 무시된다(commerce.md 5-4 「운영 지급은 무상만」).
 *
 * @param delta  0 이 아닌 정수 −100000~100000(+ 지급, − 차감). 소수·문자는 {@code fields.delta}. 크기가 {@code point_settings.adjust_large_threshold} 를 넘으면
 *               {@code point.adjust.large} 가 필요하다
 * @param reason 1~100자(연속 공백은 한 칸) — 회원의 포인트 내역에 공개된다
 */
public record PointAdjustRequest(Number delta, String reason) {
}
