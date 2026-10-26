package com.community.comment.dto.response;

import com.community.debate.dto.response.CommentDebateView;
import java.util.List;

/**
 * {@code GET /api/posts/{id}/comments}(user-web {@code CommentListResponse}).
 *
 * @param threads    {@code kind} 로 거른 원댓글 묶음 — 원댓글의 입장으로 가른다(답글은 원댓글을 따라간다). 토론 의견 구분(OPINION·PRO·CON·NEUTRAL)은 운영자가 고정한 보이는 의견이
 *                   고정한 시각 순으로 맨 위에 오고, 나머지는 {@code sort} 차례
 * @param best       베스트 댓글 — 추천 수가 기본 설정 {@code bestCommentMinLikes} 이상인 보이는 댓글, 추천 많은 순 최대 3개. {@code kind} 가 ALL 이 아니면 그 구분의 원댓글 중에서만 고른다
 * @param totalCount 보이는 댓글 수(지운 것·숨긴 것 제외) — {@code kind} 로 걸러도 글 전체 수다
 * @param kind       이 응답의 구분 ALL · GENERAL · OPINION · PRO · CON · NEUTRAL
 * @param debate     하이라이트 토론 칸(늘 있다 — 하이라이트가 아닌 글은 {@code enabled false})
 * @param reactions  이 게시판의 댓글 추천·비추천 표시(비추천 켬·단추 이름)
 * @param membersOnlyCount 비멤버·비회원이 보는 비공개 소모임의 개별 공개 글에서 공개 시각 이전에 달려 가려진 댓글 수({@code hiddenBy MEMBERS_ONLY} — 소모임 5단계,
 *                         api-groups.md 11-7). 그 밖은 0
 */
public record CommentListResponse(List<CommentThreadView> threads, List<CommentView> best, int totalCount, String kind, CommentDebateView debate,
                                  CommentReactionsView reactions, int membersOnlyCount) {
}
