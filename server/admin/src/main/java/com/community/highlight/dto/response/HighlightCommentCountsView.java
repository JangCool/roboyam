package com.community.highlight.dto.response;

import com.community.debate.dto.DebateVotesView;

/**
 * 하이라이트 글의 댓글 수 구분(공개 댓글 목록의 {@code debate.counts} 와 같은 뜻) + 투표 수.
 *
 * @param all     글의 보이는 댓글 수(답글 포함)
 * @param general 입장 배지가 없는 원댓글 수(지운·숨긴 것 제외)
 * @param pro     찬성 의견(원댓글) 수
 * @param con     반대 의견(원댓글) 수
 * @param neutral 중립 의견(원댓글) 수
 * @param votes   입장을 고른 사람 수(투표 수 — 공개 {@code debate.votes} 와 같다)
 */
public record HighlightCommentCountsView(int all, int general, int pro, int con, int neutral, DebateVotesView votes) {
}
