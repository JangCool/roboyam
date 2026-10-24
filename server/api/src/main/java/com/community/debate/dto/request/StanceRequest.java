package com.community.debate.dto.request;

/** {@code PUT /api/posts/{postId}/stance} — {@code stance} PRO · CON(대소문자·앞뒤 공백 무시) */
public record StanceRequest(String stance) {
}
