package com.community.withdrawal.dto;

import java.time.Instant;

/**
 * 분리 보관한 탈퇴 회원 연락처의 상태 — 이메일은 싣지 않는다(관리자 회원 상세 「연락용 이메일 · 분리 보관」의 `GET …/withdrawn-contact`).
 *
 * @param withdrawnAt 탈퇴 시각
 * @param purgeAfter  파기 예정 시각(마지막 거래일 + 5년)
 */
public record WithdrawnContactInfo(Instant withdrawnAt, Instant purgeAfter) {
}
