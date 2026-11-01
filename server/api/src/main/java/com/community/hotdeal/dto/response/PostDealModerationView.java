package com.community.hotdeal.dto.response;

/**
 * 글 상세 {@code dealModeration}(user-web {@code PostDetail.dealModeration}) — 보는 사람이 그 핫딜 게시판의 매니저·스텝(정지 아님)일 때만 싣는다(아니면 null).
 * 작성자 본인이 매니저여도 작성자 조치({@code deal.mine}·{@code reopenUntil})와 따로 그대로 싣는다.
 *
 * @param role      {@code MANAGER} · {@code STAFF}
 * @param canEnd    진행·의심·기간 지남 — {@code POST /api/posts/{id}/deal/moderate/end}
 * @param canReopen 종료(ENDED) — {@code POST /api/posts/{id}/deal/moderate/reopen}
 */
public record PostDealModerationView(String role, boolean canEnd, boolean canReopen) {
}
