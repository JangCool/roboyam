package com.community.highlight.dto.response;

import java.time.Instant;

/**
 * 하이라이트 의견 한 줄(관리자) — 의견 = 그 글의 입장 배지가 붙은 원댓글.
 *
 * @param stance       PRO · CON
 * @param authorKind   MEMBER · GUEST
 * @param authorId     회원 ID(비회원 null)
 * @param authorName   닉네임(비회원은 입력한 이름)
 * @param bodyPreview  본문 앞 200자(HTML 은 보이는 글자, 줄바꿈은 공백, 넘으면 끝에 …)
 * @param replyCount   지우거나 숨기지 않은 답글 수
 * @param pinned       운영자가 맨 앞에 고정했다
 * @param hidden       지금 숨김 중이다(운영자 숨김·신고 숨김 모두 — 화면이 흐리게)
 * @param hiddenReason 숨김 까닭 STAFF(운영자 — 숨김 해제로 풀 수 있다) · REPORTED · ILLEGAL · RIGHTS(신고 — 여기서 풀 수 없다), 숨김이 아니면 null
 */
public record HighlightOpinionView(String commentId, String stance, String authorKind, String authorId, String authorName, String bodyPreview, int likeCount,
                                   int replyCount, Instant createdAt, boolean pinned, boolean hidden, String hiddenReason) {
}
