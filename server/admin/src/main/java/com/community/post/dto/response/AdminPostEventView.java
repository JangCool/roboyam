package com.community.post.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * 글 조치 기록 한 줄(최신이 위).
 *
 * @param type   HIDDEN · UNHIDDEN · DELETED · RESTORED · MOVED · NOTICE · PINNED · UNPINNED · REPORT_HIDDEN
 * @param reason 운영자가 적은 사유(SYSTEM 은 null)
 * @param detail 조치별 덧붙임 JSON 객체(없으면 null) — 예: 이동 {@code fromBoardId·fromPrefixId·toBoardId·toPrefixId}, 신고 숨김 {@code hiddenReason·hiddenUntil},
 *               삭제 {@code revokedPoints}, 복구 {@code restoredPoints}, 숨김 {@code notifyAuthor}, 일괄이면 {@code bulk true}, 고정이 풀렸으면 {@code unpinned true}
 */
public record AdminPostEventView(String id, String type, AdminPostEventActorView actor, String reason, Map<String, Object> detail, Instant at) {
}
