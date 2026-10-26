package com.community.point.dto.response;

import java.time.Instant;

/**
 * 내 포인트 내역 한 줄(user-web {@code MyPointLedgerItem}) — 관리자 원장의 본인 줄에서 운영자·회원 칸을 뺐다.
 *
 * @param kind          {@code EARN} · {@code USE} · {@code REVOKE} · {@code CANCEL_USE} · {@code EXPIRE}(그 밖의 종류 {@code REFUND} 는 충전 기능(3단계) 전에는 생기지 않는다)
 * @param reasonCode    사유 코드({@code PointReason})
 * @param reason        요청 언어로 푼 사유 이름(관리자 원장과 같은 번들 {@code point.reason.<코드>})
 * @param note          운영 조정의 공개 사유 — 그 밖은 null
 * @param target        글·댓글 사유({@code ref_type} 이 POST·COMMENT)만, 그 밖은 null
 * @param restoredUntil 이 줄이 돌려준 양 중 원래 묶음이 만료돼 <b>새로 만든 묶음</b>({@code restored_from_lot_id} 있음 — 사용 취소·글 복구)이 있으면 그 소멸 시각(여럿이면 가장 이른 것), 없으면 null
 */
public record MyPointLedgerItemView(String entryId, String kind, String reasonCode, String reason, String note, int delta, long balanceAfter,
                                    MyPointLedgerTargetView target, Instant restoredUntil, Instant createdAt) {
}
