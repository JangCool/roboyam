package com.community.highlight.dto.response;

import com.community.post.dto.response.AdminPostRefView;
import java.time.Instant;

/**
 * 하이라이트 한 줄(목록·만들기·고치기·노출 응답).
 *
 * @param order         1부터 연속(저장된 순서)
 * @param title         노출 제목(없으면 null)
 * @param summary       요약 문구(없으면 null)
 * @param imageFileId   대표 이미지 파일 ID(없으면 null)
 * @param imageUrl      대표 이미지 주소(없으면 null)
 * @param status        SHOWING(지금 노출 중) · SCHEDULED(노출 기간 전) · EXPIRED(기간이 지났다) · OFF(노출을 껐다) · BLOCKED(글을 지금 볼 수 없다).
 *                      앞의 것이 이긴다: BLOCKED → OFF → SCHEDULED → EXPIRED → SHOWING
 * @param debate        「찬성·반대 의견 받기」 — 켜져 있고 SHOWING 일 때만 그 글에 토론이 열린다
 * @param post          대상 글(글 행이 아예 없으면 null — 상태 BLOCKED). {@code post.state} PUBLIC · HIDDEN · DELETED · ADULT · BOARD_OFF
 * @param commentCounts 글의 댓글 수 구분
 * @param updatedBy     마지막으로 고친 운영자(없으면 null)
 */
public record HighlightItemView(String id, int order, String title, String summary, String imageFileId, String imageUrl, boolean visible, Instant showFrom,
                                Instant showUntil, String status, boolean debate, AdminPostRefView post, HighlightCommentCountsView commentCounts,
                                Instant createdAt, Instant updatedAt, HighlightOperatorView updatedBy) {
}
