package com.community.withdrawal.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 회원 상세 「연락용 이메일 · 분리 보관」({@code GET /api/admin/members/{memberId}/withdrawn-contact}, 계약 「0-4」) — <b>이메일은 싣지 않는다</b>(열람 API 로만 받는다).
 *
 * @param stored      분리 보관한 이메일이 있는가(결제 이력 없이 탈퇴했거나, 탈퇴하지 않았거나, 파기됐으면 false)
 * @param withdrawnAt 탈퇴 시각 — 보관했으면 보관 표의 값, 아니면 회원의 탈퇴 시각(탈퇴하지 않았으면 null)
 * @param purgeAfter  파기 예정일(서비스 날짜 {@code YYYY-MM-DD} — 마지막 거래일 + 5년). 보관한 것이 없으면 null
 * @param reveals     열람 기록(최근 것부터 50건)
 */
public record WithdrawnContactView(boolean stored, Instant withdrawnAt, LocalDate purgeAfter, List<WithdrawnContactRevealView> reveals) {
}
