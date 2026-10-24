package com.community.dm.dto.response;

/** {@code POST /api/users/{id}/dm} 의 201 — {@code created} 는 이 쪽지로 대화가 새로 만들어졌는가 */
public record DmSendResultView(String threadId, boolean created, DmMessageView message, DmQuotaView quota) {
}
