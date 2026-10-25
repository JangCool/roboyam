package com.community.point.dto.response;

import java.time.Instant;

/**
 * 내 포인트 내역 한 줄(user-web {@code MyPointLedgerItem}) — 관리자 원장의 본인 줄에서 운영자·회원 칸을 뺐다.
 *
 * @param kind       {@code EARN} · {@code USE} · {@code REVOKE}
 * @param reasonCode 사유 코드({@code PointReason})
 * @param reason     요청 언어로 푼 사유 이름(관리자 원장과 같은 번들 {@code point.reason.<코드>})
 * @param note       운영 조정의 공개 사유 — 그 밖은 null
 * @param target     글·댓글 사유만, 그 밖은 null
 */
public record MyPointLedgerItemView(String entryId, String kind, String reasonCode, String reason, String note, int delta, long balanceAfter,
                                    MyPointLedgerTargetView target, Instant createdAt) {
}
