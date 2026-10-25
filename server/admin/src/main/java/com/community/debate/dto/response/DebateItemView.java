package com.community.debate.dto.response;

import com.community.debate.dto.DebateVotesView;
import com.community.post.dto.response.AdminPostRefView;
import java.time.Instant;

/**
 * 열린 토론 한 줄 — {@code GET·PUT /api/admin/debates/{postId}} 응답(docs/features/post-debate-contract.md).
 *
 * @param post               대상 글(글 행이 아예 없으면 null). {@code post.state} PUBLIC · HIDDEN · DELETED · ADULT · BOARD_OFF
 * @param openedBy           MEMBER(글쓴이가 연 토론) · OPERATOR(운영자 또는 그 게시판 매니저가 연 토론)
 * @param votes              입장을 고른 사람 수(투표 수 — 공개 {@code debate.votes} 와 같다)
 * @param opinions           의견(입장 있는 원댓글) 수 — 지우거나 숨긴 것 제외
 * @param pendingPinRequests 대기 중인 의견 고정 요청 수 — 지우거나 숨긴 의견의 요청 제외
 * @param highlightId        이 글의 하이라이트(없으면 null — 노출 여부와 무관)
 * @param createdAt          연 시각
 */
public record DebateItemView(String postId, AdminPostRefView post, boolean neutralEnabled, boolean stanceChangeAllowed, String openedBy, DebateVotesView votes,
                             int opinions, int pendingPinRequests, String highlightId, Instant createdAt, Instant updatedAt) {
}
