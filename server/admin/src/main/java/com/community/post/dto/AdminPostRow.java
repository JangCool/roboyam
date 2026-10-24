package com.community.post.dto;

import com.community.post.entity.PostEntity;
import java.time.Instant;

/**
 * 관리자 화면(인기글 관리·하이라이트)이 글 한 줄을 그리는 데 쓰는 칸 — 본문은 읽지 않는다. 지운·숨긴 글도 읽는다(상태는 {@link #stateAt}).
 * 작성 IP 는 읽지 않는다.
 */
public record AdminPostRow(Long id, Long boardId, Long prefixId, Long authorId, String guestName, String title, String status, Boolean adult,
                           Integer viewCount, Integer likeCount, Integer commentCount, Instant hiddenAt, Instant hiddenUntil, Instant deleteScheduledAt,
                           Instant deletedAt, Instant createdAt) {

    public static final String PUBLIC = "PUBLIC";
    public static final String HIDDEN = "HIDDEN";
    public static final String DELETED = "DELETED";
    public static final String ADULT = "ADULT";
    public static final String BOARD_OFF = "BOARD_OFF";

    /** 지웠거나(예약 삭제 시각 지남·임시저장 포함) 공개가 아닌가 */
    public boolean goneAt(Instant now) {
        return deletedAt != null || !PostEntity.PUBLISHED.equals(status) || (deleteScheduledAt != null && !deleteScheduledAt.isAfter(now));
    }

    /** 신고·운영 숨김 중인가(임시조치는 기간 안일 때만) */
    public boolean hiddenAt(Instant now) {
        return hiddenAt != null && (hiddenUntil == null || hiddenUntil.isAfter(now));
    }

    /**
     * 글 상태 — DELETED(지움·임시저장·예약 삭제 시각 지남) · HIDDEN(숨김) · ADULT(19금) · BOARD_OFF(게시판이 꺼졌거나 없다) · PUBLIC. 앞의 것이 이긴다.
     *
     * @param boardActive 게시판이 있고 켜져 있는가
     */
    public String stateAt(Instant now, boolean boardActive) {
        if (goneAt(now)) {
            return DELETED;
        }
        if (hiddenAt(now)) {
            return HIDDEN;
        }
        if (Boolean.TRUE.equals(adult)) {
            return ADULT;
        }
        if (!boardActive) {
            return BOARD_OFF;
        }
        return PUBLIC;
    }
}
