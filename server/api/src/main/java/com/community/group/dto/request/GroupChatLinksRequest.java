package com.community.group.dto.request;

/** {@code PUT /api/groups/{id}/chat-links} — 채팅 링크(null 이면 지운다) */
public record GroupChatLinksRequest(Link kakao, Link discord) {

    /** @param label 선택 30자 */
    public record Link(String url, String label) {
    }
}
