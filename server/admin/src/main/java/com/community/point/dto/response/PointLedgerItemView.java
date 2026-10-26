package com.community.point.dto.response;

import java.time.Instant;

/**
 * 원장 한 줄 — admin-web {@code PointLedgerItem}. ID 는 문자열.
 *
 * @param memberNickname 지금 닉네임(조회 시점)
 * @param kind           {@code EARN} · {@code USE} · {@code REVOKE} · {@code CANCEL_USE} · {@code EXPIRE} · {@code REFUND}
 * @param reasonCode     사유 코드(admin-web {@code PointReasonCode})
 * @param reason         요청 언어로 푼 사유 이름(출석체크·글 작성 …)
 * @param note           운영 조정의 공개 사유 — 그 밖은 null
 * @param delta          실제 반영된 증감 — {@code freeDelta + paidDelta}
 * @param freeDelta      그중 무상(적립) 포인트의 증감
 * @param paidDelta      그중 유상(충전) 포인트의 증감
 * @param balanceAfter   이 줄 뒤의 잔액(합계)
 * @param actorNickname  운영 조정을 한 운영자 이름 — 자동 지급·회수는 null(화면은 「시스템」)
 */
public record PointLedgerItemView(String entryId, String memberId, String memberNickname, String kind, String reasonCode, String reason, String note, int delta,
                                  long freeDelta, long paidDelta, long balanceAfter, String actorNickname, Instant createdAt) {
}
