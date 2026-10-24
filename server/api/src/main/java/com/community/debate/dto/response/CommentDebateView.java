package com.community.debate.dto.response;

/**
 * 댓글 목록({@code GET /api/posts/{id}/comments})의 토론 칸 — 하이라이트가 아닌 글에도 늘 있다({@code enabled false}, {@code pro}·{@code con} 0).
 * 화면은 {@code enabled} 이거나 {@code counts.pro + counts.con > 0} 일 때 구분 탭(전체·일반·찬성·반대)을 보이면 된다(하이라이트가 끝난 글의 탭은 읽기 전용).
 *
 * @param enabled    {@link DebateSummaryView#enabled()} 와 같다
 * @param myStance   {@link DebateSummaryView#myStance()} 와 같다
 * @param opinionMax {@link DebateSummaryView#opinionMax()} 와 같다
 * @param counts     구분별 수 — {@code kind} 로 걸러도 글 전체 기준이다
 */
public record CommentDebateView(boolean enabled, String myStance, int opinionMax, DebateCountsView counts) {
}
