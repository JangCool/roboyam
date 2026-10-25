package com.community.home.dto.response;

import com.community.post.dto.response.PostFeedRowView;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/home/popular}(user-web {@code PopularListResponse}) — 그 기간의 순위 순, 순위 스냅샷(최대 50개)에 든 글 중 지금 볼 수 있는 글만(쪽 번호).
 *
 * @param pinned     늘 빈 목록(게시판 목록과 같은 모양을 맞춘 칸)
 * @param page       요청한 쪽(1부터) — 순위가 없으면 1
 * @param totalPages 최소 1
 * @param nextCursor 늘 null(쪽 번호 방식)
 * @param fills      이번 응답에 든 채운 줄만 — 글 ID → YESTERDAY · LAST_WEEK. 없으면 빈 객체
 * @param from       이번 주 탭(WEEK)일 때만 그 주의 시작(한국시간, 설정의 주 시작 요일 0시) — 다른 탭은 null
 * @param to         이번 주 탭일 때만 다음 주 시작(미포함) — 다른 탭은 null
 * @param weekStart  설정(인기글 설정)의 주 시작 요일 MONDAY · SUNDAY — 어느 탭에도 늘 실린다
 */
public record PopularListResponse(List<PostFeedRowView> pinned, List<PostFeedRowView> items, int page, int totalPages, long totalCount, boolean hasNext,
                                  String nextCursor, String period, Map<String, String> fills, Instant from, Instant to, String weekStart) {
}
