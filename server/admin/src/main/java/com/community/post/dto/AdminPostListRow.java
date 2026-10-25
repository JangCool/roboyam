package com.community.post.dto;

import com.community.post.entity.PostEntity;
import java.time.Instant;

/**
 * 게시글 관리 목록·상세의 글 한 줄 — 본문·작성 IP 는 읽지 않는다({@code AdminPostRepositoryQDSL} 의 투영). 상태는 {@link #stateAt}.
 */
public record AdminPostListRow(Long id, Long boardId, Long prefixId, Long authorId, String guestName, String title, String status, Boolean adult,
                               Integer viewCount, Integer likeCount, Integer commentCount, Instant pinnedAt, Instant hiddenAt, String hiddenReason,
                               Instant hiddenUntil, Instant deleteScheduledAt, Instant deletedAt, Instant purgedAt, Instant createdAt) {

    public static final String PUBLIC = "PUBLIC";
    public static final String HIDDEN = "HIDDEN";
    public static final String DELETED = "DELETED";

    /** 지웠거나(예약 삭제 시각 지남·임시저장 포함) 공개가 아닌가 */
    public boolean goneAt(Instant now) {
        return deletedAt != null || !PostEntity.PUBLISHED.equals(status) || (deleteScheduledAt != null && !deleteScheduledAt.isAfter(now));
    }

    /** 숨김 중인가(임시조치는 기간 안일 때만) */
    public boolean hiddenAt(Instant now) {
        return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now));
    }

    /** PUBLIC · HIDDEN · DELETED — 지움이 숨김을 이긴다 */
    public String stateAt(Instant now) {
        if (goneAt(now)) {
            return DELETED;
        }
        return hiddenAt(now) ? HIDDEN : PUBLIC;
    }
}
