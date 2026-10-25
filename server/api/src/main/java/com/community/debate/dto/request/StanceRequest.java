package com.community.debate.dto.request;

/** {@code PUT /api/posts/{postId}/stance} — {@code stance} PRO · CON · NEUTRAL(대소문자·앞뒤 공백 무시). 처음 고르기와 바꾸기 모두 */
public record StanceRequest(String stance) {
}
