package com.community.debate.dto.response;

/** {@code PUT /api/posts/{postId}/stance} 응답 — 바뀐(또는 그대로인) 내 입장 PRO · CON */
public record StanceView(String stance) {
}
