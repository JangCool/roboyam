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
 * @param debate        그 글의 찬반 토론이 열려 있다({@code post_debates} — 하이라이트 노출과 무관)
 * @param neutralEnabled 글 토론의 「중립도 받기」(토론이 없으면 true)
 * @param stanceChangeAllowed 글 토론의 「입장 바꾸기 허용」(토론이 없으면 true)
 * @param debateOpenedBy 토론을 연 사람 MEMBER(글쓴이 — 화면은 「글쓴이가 연 토론」) · OPERATOR(운영자·게시판 매니저), 토론이 없으면 null
 * @param post          대상 글(글 행이 아예 없으면 null — 상태 BLOCKED). {@code post.state} PUBLIC · HIDDEN · DELETED · ADULT · BOARD_OFF
 * @param commentCounts 글의 댓글 수 구분(입장별 의견 수 + 투표 수 {@code votes})
 * @param updatedBy     마지막으로 고친 운영자(없으면 null)
 */
public record HighlightItemView(String id, int order, String title, String summary, String imageFileId, String imageUrl, boolean visible, Instant showFrom,
                                Instant showUntil, String status, boolean debate, boolean neutralEnabled, boolean stanceChangeAllowed, String debateOpenedBy,
                                AdminPostRefView post,
                                HighlightCommentCountsView commentCounts,
                                Instant createdAt, Instant updatedAt, HighlightOperatorView updatedBy) {
}
