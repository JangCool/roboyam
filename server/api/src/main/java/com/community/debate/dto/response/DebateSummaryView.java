package com.community.debate.dto.response;

import com.community.debate.dto.DebateVotesView;

/**
 * 글 상세({@code GET /api/posts/{id}})의 토론 요약 — {@code PUT /api/posts/{id}/debate} 의 응답도 같은 모양(docs/features/post-debate-contract.md).
 *
 * @param enabled        지금 이 글의 토론이 열려 있는가 — 글의 {@code post_debates} 행이 있다(하이라이트·노출과 무관, DB 를 바로 읽은 값)
 * @param myStance       내가 이 글에서 고른 입장 PRO · CON · NEUTRAL — 고르지 않았거나 비로그인이면 null. 토론이 닫혀도 남는다
 * @param opinionMax     입장 의견의 글자 수 한도 — 일반 댓글 한도는 그대로(지금 둘 다 2,000자)
 * @param neutralEnabled 토론의 「중립도 받기」 — false 면 새로 중립을 고를 수 없다(이미 고른 중립·쓴 중립 의견은 남고 수에도 든다). 닫혀 있으면 true
 * @param stanceChangeAllowed 토론의 「입장 바꾸기 허용」 — false 면 한 번 고른 입장을 바꿀 수 없다({@code PUT …/stance} 409 {@code STANCE_CHANGE_LOCKED}, 처음 고르기는 된다).
 *                            나중에 꺼져도 이미 고른 입장은 그대로. 닫혀 있으면 true
 * @param votes          입장을 고른 사람 수(투표 수, {@code post_stances}) — 의견 수와 따로
 * @param pendingPinRequests 내가 확인할 의견 고정 요청 수(게시판 정책 {@code pinRequestReviewers} 의 글쓴이·게시판 매니저일 때 — 내 요청·지우거나 숨긴 의견 제외).
 *                           확인할 수 없는 사람·토론이 닫혀 있고 투표도 없는 글은 null
 * @param pinRequestEnabled  이 글의 게시판이 의견 고정 요청을 받는가 — 유효 정책 {@code pinRequestReviewers} 가 비어 있지 않다(게시판 값만 — 토론이 열렸는지·내 의견인지는
 *                           따로 본다). false 면 {@code POST /api/comments/{id}/pin-request} 가 409 {@code PIN_REQUEST_OFF}
 * @param openedBy       토론을 연 사람 MEMBER(글쓴이) · OPERATOR(운영자 또는 그 게시판 매니저 — 글쓴이에게 읽기 전용). 닫혀 있으면 null
 * @param canManage      지금 보는 사람이 {@code PUT /api/posts/{id}/debate} 로 열고·바꿀 수 있는가 — 그 게시판 매니저, 또는 글쓴이(회원 글 + 게시판 {@code debateEnabled}
 *                       + 토론이 없거나 글쓴이가 연 토론)
 * @param canClose       닫기까지 되는가 — 열려 있고, 매니저이거나 글쓴이면 투표가 없을 때(있으면 409 {@code DEBATE_HAS_VOTES})
 */
public record DebateSummaryView(boolean enabled, String myStance, int opinionMax, boolean neutralEnabled, boolean stanceChangeAllowed, DebateVotesView votes,
                                Integer pendingPinRequests, boolean pinRequestEnabled, String openedBy, boolean canManage, boolean canClose) {
}
