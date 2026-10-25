package com.community.popular.dto.response;

import com.community.post.dto.response.AdminPostRefView;
import java.time.Instant;

/**
 * 인기글에서 뺀 글 한 줄(기간과 무관 — 뺀 글은 세 기간 모두에서 빠진다).
 *
 * @param postId     글 ID
 * @param available  글이 아직 있고 지금 볼 수 있는가(지웠거나 숨겼거나 19금이 됐거나 게시판이 꺼졌으면 false — 뺀 기록은 남는다)
 * @param post       글 한 줄(글 행이 아예 없으면 null)
 * @param reason     INAPPROPRIATE · PROMOTION · MANIPULATION · DISPUTE · OTHER
 * @param memo       메모(없으면 null)
 * @param excludedBy 뺀 운영자(지워진 운영자면 null)
 */
public record PopularExcludedView(String postId, boolean available, AdminPostRefView post, String reason, String memo, PopularOperatorView excludedBy,
                                  Instant excludedAt) {
}
