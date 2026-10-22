package com.community.point.dto.request;

/**
 * {@code POST /api/admin/members/{id}/points} — 검사는 서비스가 칸을 모아 한다(정수 아님·범위 밖·빈 사유를 한 번에 {@code fields.delta|reason} 으로).
 *
 * @param delta  0 이 아닌 정수 −10000~10000(+ 지급, − 차감). 소수·문자는 {@code fields.delta}
 * @param reason 1~100자(연속 공백은 한 칸) — 회원의 포인트 내역에 공개된다
 */
public record PointAdjustRequest(Number delta, String reason) {
}
