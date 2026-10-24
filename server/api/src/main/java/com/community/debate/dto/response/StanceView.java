package com.community.debate.dto.response;

/**
 * {@code PUT /api/posts/{postId}/stance} 응답.
 *
 * @param stance 바뀐(또는 그대로인) 내 입장 PRO · CON
 * @param moved  이번에 새 입장으로 옮긴 내 의견 수(원댓글·답글, 숨긴 것 포함, 지운 것 제외) — 같은 값이라 바뀌지 않았으면 0
 */
public record StanceView(String stance, int moved) {
}
