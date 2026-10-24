package com.community.highlight.dto.response;

/**
 * 하이라이트 글의 댓글 수 구분(공개 댓글 목록의 {@code debate.counts} 와 같은 뜻).
 *
 * @param all     글의 보이는 댓글 수(답글 포함)
 * @param general 입장 배지가 없는 원댓글 수(지운·숨긴 것 제외)
 * @param pro     찬성 의견 수
 * @param con     반대 의견 수
 */
public record HighlightCommentCountsView(int all, int general, int pro, int con) {
}
