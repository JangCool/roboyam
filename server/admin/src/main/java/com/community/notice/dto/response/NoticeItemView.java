package com.community.notice.dto.response;

import com.community.post.dto.response.AdminPostPrefixRefView;
import java.time.Instant;

/**
 * 공지 한 줄 — 상태 칸은 게시글 관리 {@code AdminPostItem} 과 같은 규칙(숨김 칸은 지금 숨김 중일 때만, {@code deletedAt} 은 예약 삭제 시각이 지났으면 그 시각).
 *
 * @param state     PUBLIC · HIDDEN · DELETED
 * @param editedAt  마지막 수정 시각(고친 적 없으면 null)
 * @param publicUrl 공개 사이트의 글 주소(사이트 공개 주소 + {@code /posts/{id}}), 공개 주소가 없으면 null
 * @param global    전체 공지 설정(V174) — 고정 범위가 모든 게시판(ALL)인 고정 글만, 아니면 null
 */
public record NoticeItemView(String id, AdminPostPrefixRefView prefix, String title, NoticeAuthorView author, String state, String hiddenReason, Instant hiddenAt,
                             Instant hiddenUntil, Instant deletedAt, Instant deleteScheduledAt, boolean purged, boolean pinned, Instant pinnedAt, int viewCount,
                             int commentCount, Instant createdAt, Instant editedAt, String publicUrl,
                             NoticeGlobalView global) {
}
