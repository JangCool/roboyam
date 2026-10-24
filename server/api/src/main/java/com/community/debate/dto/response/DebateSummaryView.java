package com.community.debate.dto.response;

import com.community.debate.dto.DebateVotesView;

/**
 * 글 상세({@code GET /api/posts/{id}})의 토론 요약.
 *
 * @param enabled        지금 이 글이 토론 의견을 받는가 — 노출 중인 하이라이트이고 「찬성·반대 의견 받기」가 켜져 있다(DB 를 바로 읽은 값)
 * @param myStance       내가 이 글에서 고른 입장 PRO · CON · NEUTRAL — 고르지 않았거나 비로그인이면 null. 하이라이트가 끝난 뒤에도 남는다
 * @param opinionMax     입장 의견의 글자 수 한도 — 일반 댓글 한도는 그대로(지금 둘 다 2,000자)
 * @param neutralEnabled 하이라이트의 「중립도 받기」 — false 면 새로 중립을 고를 수 없다(이미 고른 중립·쓴 중립 의견은 남고 수에도 든다). 하이라이트가 없으면 false
 * @param votes          입장을 고른 사람 수(투표 수, {@code post_stances}) — 의견 수와 따로
 * @param pendingPinRequests 내가 확인할 의견 고정 요청 수(게시판 정책 {@code pinRequestReviewers} 의 글쓴이·게시판 매니저일 때 — 내 요청·지우거나 숨긴 의견 제외).
 *                           확인할 수 없는 사람·하이라이트가 아닌 글은 null
 * @param pinRequestEnabled  이 글의 게시판이 의견 고정 요청을 받는가 — 유효 정책 {@code pinRequestReviewers} 가 비어 있지 않다(게시판 값만 — 토론이 열렸는지·내 의견인지는
 *                           따로 본다). false 면 {@code POST /api/comments/{id}/pin-request} 가 409 {@code PIN_REQUEST_OFF}
 */
public record DebateSummaryView(boolean enabled, String myStance, int opinionMax, boolean neutralEnabled, DebateVotesView votes, Integer pendingPinRequests,
                                boolean pinRequestEnabled) {
}
