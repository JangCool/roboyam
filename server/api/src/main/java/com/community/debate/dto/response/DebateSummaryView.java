package com.community.debate.dto.response;

/**
 * 글 상세({@code GET /api/posts/{id}})의 토론 요약.
 *
 * @param enabled    지금 이 글이 찬성·반대 의견을 받는가 — 노출 중인 하이라이트이고 「찬성·반대 의견 받기」가 켜져 있다(DB 를 바로 읽은 값)
 * @param myStance   내가 이 글에서 정한 입장 PRO · CON — 정하지 않았거나 비로그인이면 null. 하이라이트가 끝난 뒤에도 남는다
 * @param opinionMax 입장 배지가 붙는 댓글(토론 댓글)의 글자 수 한도 — 일반 댓글 한도는 그대로(지금 둘 다 2,000자)
 */
public record DebateSummaryView(boolean enabled, String myStance, int opinionMax) {
}
