package com.community.debate.dto.response;

/**
 * {@code GET /api/admin/debates/{postId}/opinions} — 예전 하이라이트 의견 목록과 같은 모양(글 번호 기준).
 *
 * @param highlightId    이 글의 하이라이트(없으면 null)
 * @param debate         지금 그 글의 토론이 열려 있다(닫혀 있어도 이미 쓴 의견은 다룰 수 있다)
 * @param open           {@code debate} 와 같다(예전 칸 — 호환으로 둔다)
 * @param neutralEnabled 토론의 「중립도 받기」(닫혀 있으면 true) — 화면은 이것이 켜졌거나 {@code neutral.total > 0} 일 때 중립 탭을 보이면 된다
 */
public record DebateOpinionListView(String highlightId, String postId, boolean debate, boolean open, boolean neutralEnabled, DebateOpinionPageView pro,
                                    DebateOpinionPageView con, DebateOpinionPageView neutral) {
}
