package com.community.debate.dto.response;

import com.community.debate.dto.DebateVotesView;

/**
 * 댓글 목록({@code GET /api/posts/{id}/comments})의 토론 칸 — 토론이 없는 글에도 늘 있다({@code enabled false}, 수 0).
 * 화면은 {@code enabled} 이거나 {@code votes.total > 0} 이거나 {@code counts.opinions > 0} 일 때 토론 구획을 보이면 된다(닫힌 토론은 읽기 전용).
 *
 * @param enabled        {@link DebateSummaryView#enabled()} 와 같다
 * @param myStance       {@link DebateSummaryView#myStance()} 와 같다
 * @param opinionMax     {@link DebateSummaryView#opinionMax()} 와 같다
 * @param neutralEnabled {@link DebateSummaryView#neutralEnabled()} 와 같다
 * @param stanceChangeAllowed {@link DebateSummaryView#stanceChangeAllowed()} 와 같다
 * @param votes          입장을 고른 사람 수(투표 수 — 비율 막대)
 * @param counts         의견·일반 댓글 수(의견 거르기 칩·구획 머리) — {@code kind} 로 걸러도 글 전체 기준이다
 * @param myOpinionCount 내가 이 글에 쓴 지금 입장({@code myStance})의 의견 수 — 원댓글·답글, 숨긴 것 포함, 지운 것 제외. 입장을 바꾸면({@code PUT …/stance}) 이만큼이 새 입장으로
 *                       옮겨 간다(확인창의 「N개가 바뀐다」). 비로그인·입장 없음은 0
 * @param pendingPinRequests {@link DebateSummaryView#pendingPinRequests()} 와 같다
 * @param pinRequestEnabled  {@link DebateSummaryView#pinRequestEnabled()} 와 같다
 * @param openedBy       {@link DebateSummaryView#openedBy()} 와 같다
 * @param canManage      {@link DebateSummaryView#canManage()} 와 같다
 * @param canClose       {@link DebateSummaryView#canClose()} 와 같다
 */
public record CommentDebateView(boolean enabled, String myStance, int opinionMax, boolean neutralEnabled, boolean stanceChangeAllowed, DebateVotesView votes,
                                DebateCountsView counts,
                                int myOpinionCount, Integer pendingPinRequests, boolean pinRequestEnabled, String openedBy, boolean canManage, boolean canClose) {
}
