package com.community.comment.dto.response;

import com.community.debate.dto.response.CommentDebateView;
import java.util.List;

/**
 * {@code GET /api/posts/{id}/comments}(user-web {@code CommentListResponse}).
 *
 * @param threads    {@code kind} 로 거른 원댓글 묶음 — 원댓글의 입장으로 가른다(답글은 원댓글을 따라간다)
 * @param best       베스트 댓글 — 추천 수가 기본 설정 {@code bestCommentMinLikes} 이상인 보이는 댓글, 추천 많은 순 최대 3개. {@code kind} 가 ALL 이 아니면 그 구분의 원댓글 중에서만 고른다
 * @param totalCount 보이는 댓글 수(지운 것·숨긴 것 제외) — {@code kind} 로 걸러도 글 전체 수다
 * @param kind       이 응답의 구분 ALL · GENERAL · PRO · CON
 * @param debate     하이라이트 토론 칸(늘 있다 — 하이라이트가 아닌 글은 {@code enabled false})
 */
public record CommentListResponse(List<CommentThreadView> threads, List<CommentView> best, int totalCount, String kind, CommentDebateView debate) {
}
