package com.community.dm.dto.response;

/** 쪽지함 한 줄 — user-web {@code DmThreadItem}. {@code unreadCount} 는 상대가 보낸 안 읽은 쪽지 수 */
public record DmThreadItemView(String id, DmPartnerView partner, DmLastMessageView last, int unreadCount) {
}
