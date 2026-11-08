package com.community.notice.dto;

import java.time.Instant;
import java.util.List;

/**
 * 살아 있는 전체 공지 한 줄(V174 줄 + 그 공지 글) — {@code NoticeGlobalPinQueries#live}.
 *
 * @param pinnedAt           글의 고정 시각(= 줄의 사본, 순서 — 최근 고정이 위)
 * @param excludedBoardIds   뺄 게시판
 * @param excludeGroupBoards 소모임 게시판 전체를 뺀다
 * @param expiresAt          다른 게시판에서 내려갈 시각(null = 풀 때까지)
 */
public record NoticeGlobalPinRow(long postId, String title, Instant createdAt, Instant pinnedAt, List<Long> excludedBoardIds, boolean excludeGroupBoards,
                                 Instant expiresAt) {

    /** 지금 이 게시판 맨 위에 보이는가 — 공지 게시판 자신 · 뺀 게시판 · 소모임 게시판 전체 빼기 · 기간 지남이면 아니다 */
    public boolean showsOn(long boardId, boolean groupBoard, long noticeBoardId, Instant now) {
        if (boardId == noticeBoardId || (expiresAt != null && !expiresAt.isAfter(now))) {
            return false;
        }
        if (groupBoard && excludeGroupBoards) {
            return false;
        }
        return !excludedBoardIds.contains(boardId);
    }
}
