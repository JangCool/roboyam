package com.community.notice.dto.response;

/**
 * {@code GET /api/admin/notices/context}.
 *
 * @param board 공지 게시판 — 없으면 null(꺼져 있어도 내려간다 — {@code active})
 */
public record NoticeContextView(NoticeBoardView board, NoticeWriterView writer, NoticeBarSummaryView noticeBar) {
}
