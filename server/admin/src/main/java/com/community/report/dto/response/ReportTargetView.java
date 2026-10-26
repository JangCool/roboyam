package com.community.report.dto.response;

import java.time.Instant;

/**
 * 사건의 대상.
 *
 * @param postId       대상 글(댓글 사건이면 그 댓글의 글) — 소모임 사건이면 null
 * @param commentId    댓글 사건이면 그 댓글, 그 밖 null
 * @param title        대상 글 제목(댓글 사건이면 그 댓글의 글 제목) · 소모임 사건이면 지금 소모임 이름 — 원문을 파기했거나 없으면 null(화면은 신고 당시 스냅샷)
 * @param excerpt      대상 본문(글)·댓글·소모임 소개의 앞 80자 — 원문을 파기했거나 없으면 null
 * @param state        PUBLIC · HIDDEN · DELETED(지움이 숨김을 이긴다) · INACTIVE(비활성 소모임 — 소모임 사건만). 소모임은 열림 PUBLIC, 해산·없음 DELETED
 * @param hiddenReason 숨김 중이면 까닭(REPORTED · ILLEGAL · RIGHTS · STAFF)
 * @param hiddenUntil  권리침해 임시조치처럼 기한이 있는 숨김이면 그 시각
 * @param groupId      소모임 사건이면 그 소모임 ID(V95), 그 밖 null
 */
public record ReportTargetView(String postId, String commentId, String title, String excerpt, String state, String hiddenReason, Instant hiddenUntil,
                               String groupId) {
}
