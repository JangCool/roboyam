package com.community.highlight.dto.response;

/**
 * {@code GET /api/admin/highlights/{id}/opinions}.
 *
 * @param debate 이 하이라이트의 「찬성·반대 의견 받기」(꺼져 있어도 이미 쓴 의견은 다룰 수 있다)
 * @param open   지금 토론이 열려 있다(노출 중 + 의견 받기 켬)
 * @param neutralEnabled 이 하이라이트의 「중립도 받기」 — 화면은 이것이 켜졌거나 {@code neutral.total > 0} 일 때 중립 탭을 보이면 된다
 */
public record HighlightOpinionListView(String highlightId, String postId, boolean debate, boolean open, boolean neutralEnabled, HighlightOpinionPageView pro,
                                       HighlightOpinionPageView con, HighlightOpinionPageView neutral) {
}
