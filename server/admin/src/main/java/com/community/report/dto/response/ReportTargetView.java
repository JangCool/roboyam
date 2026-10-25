package com.community.report.dto.response;

import java.time.Instant;

/**
 * 사건의 대상.
 *
 * @param title        대상 글 제목(댓글 사건이면 그 댓글의 글 제목) — 원문을 파기했으면 신고 당시 스냅샷
 * @param excerpt      대상 본문(글)·댓글의 앞 80자 — 원문을 파기했으면 신고 당시 스냅샷
 * @param state        PUBLIC · HIDDEN · DELETED(지움이 숨김을 이긴다)
 * @param hiddenReason 숨김 중이면 까닭(REPORTED · ILLEGAL · RIGHTS · STAFF)
 * @param hiddenUntil  권리침해 임시조치처럼 기한이 있는 숨김이면 그 시각
 */
public record ReportTargetView(String postId, String commentId, String title, String excerpt, String state, String hiddenReason, Instant hiddenUntil) {
}
