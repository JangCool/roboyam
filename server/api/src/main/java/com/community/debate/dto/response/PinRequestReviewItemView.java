package com.community.debate.dto.response;

import com.community.post.dto.response.PostAuthorView;
import java.time.Instant;

/**
 * 확인할 고정 요청 한 줄(공개 {@code GET /api/posts/{postId}/pin-requests}).
 *
 * @param id          요청 ID — 승인 {@code POST /api/pin-requests/{id}/approve} · 거절 {@code …/reject}
 * @param stance      의견의 입장 PRO · CON · NEUTRAL
 * @param author      의견을 쓴(요청한) 회원(보는 사람의 메모 {@code memo} 포함)
 * @param excerpt     의견 본문 앞 200자(HTML 은 보이는 글자)
 * @param requestedAt 요청한 시각
 */
public record PinRequestReviewItemView(String id, String commentId, String stance, PostAuthorView author, String excerpt, int likeCount, Instant commentCreatedAt,
                                       Instant requestedAt) {
}
