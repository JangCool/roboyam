package com.community.point.dto.response;

import java.time.Instant;

/**
 * 덜 회수된 기록({@code point_revoke_shortfalls} 한 줄) — admin-web {@code PointRevokeShortfall}. 돌려받을 양({@code requested}) 중 실제로 가져간 양({@code applied}).
 *
 * @param refType {@code POST} · {@code COMMENT}
 * @param refId   그 글·댓글 ID(문자열)
 */
public record PointRevokeShortfallView(String id, String refType, String refId, long requested, long applied, Instant createdAt) {
}
