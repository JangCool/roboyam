package com.community.dm.dto.response;

/** 받는 사람 찾기 한 줄 — {@code threadId} 가 있으면 이미 대화 중 */
public record DmRecipientView(DmPartnerView user, String threadId) {
}
