package com.community.point.dto.response;

import java.time.Instant;

/**
 * 관리자 회원 상세 「확정 대기」 한 줄 — admin-web {@code PointPendingGrant}(point-board-tier-contract 「보류 확정 방식」).
 *
 * @param reason    LIKE_BONUS · POST_FEATURED
 * @param refType   POST · COMMENT
 * @param postTitle 대상 글 제목(댓글이면 그 댓글이 달린 글) — 없거나 파기된 글이면 null
 * @param amount    확정 때 줄 양(보류를 시작한 순간의 정책값)
 * @param releaseAt 확정 예정 시각
 * @param createdAt 보류를 시작한 시각
 */
public record PointPendingGrantView(String id, String reason, String refType, String refId, String postTitle, int amount, Instant releaseAt, Instant createdAt) {
}
