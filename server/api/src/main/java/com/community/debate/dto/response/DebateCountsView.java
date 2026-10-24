package com.community.debate.dto.response;

/**
 * 글의 댓글 수 구분.
 *
 * @param all     글의 보이는 댓글 수(지운 것·숨긴 것 제외, 답글 포함) — 목록의 {@code totalCount} 와 같다
 * @param general 입장 배지가 없는 원댓글 수(지운 것·숨긴 것 제외)
 * @param pro     찬성 원댓글(의견) 수
 * @param con     반대 원댓글(의견) 수
 */
public record DebateCountsView(int all, int general, int pro, int con) {
}
