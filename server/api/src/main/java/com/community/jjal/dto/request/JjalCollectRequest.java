package com.community.jjal.dto.request;

/**
 * {@code POST /api/me/jjals/collect} — 글·댓글에 보이는 짤을 내 보관함에 담는다.
 *
 * @param jjalId    짤 공개 ID(토큰 {@code :jjal-ID:} 의 ID)
 * @param postId    그 짤이 보이는 글(댓글의 짤이면 그 댓글의 글) — 필수. 서버가 「보이는 글·댓글의 짤인가」·「19금 게시판·19금 글이 아닌가」를 이 글로 판정한다
 * @param commentId 댓글의 짤이면 그 댓글 ID(글 본문의 짤이면 빠짐)
 */
public record JjalCollectRequest(String jjalId, String postId, String commentId) {
}
