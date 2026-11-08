package com.community.notice.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 공지의 전체 공지 설정(V174) — 고정 범위가 {@code ALL} 일 때만 공지 한 줄에 실린다(아니면 null).
 *
 * @param excludedBoardIds      뺀 게시판 ID
 * @param excludeAllGroupBoards 소모임 게시판 전체를 뺐다
 * @param expiresAt             다른 게시판에서 내려갈 시각(null = 풀 때까지)
 * @param expired               기간이 지나 다른 게시판에서는 내려갔다(공지 게시판 고정은 그대로)
 */
public record NoticeGlobalView(List<String> excludedBoardIds, boolean excludeAllGroupBoards, Instant expiresAt, boolean expired) {
}
