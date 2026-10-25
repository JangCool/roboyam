package com.community.debate.dto.response;

/**
 * 글의 댓글 수 구분 — 의견(입장 있는 댓글)과 일반 댓글을 따로 센다. 모두 지운 것·숨긴 것 제외. 구획은 <b>원댓글</b>의 입장으로 가른다(답글은 원댓글을 따라간다).
 *
 * @param all           글의 보이는 댓글 수(답글 포함, 두 구획 모두) — 목록의 {@code totalCount} 와 같다
 * @param general       일반 원댓글 수(배지 없음) — {@code kind=GENERAL} 의 원댓글 수
 * @param generalTotal  일반 구획의 보이는 댓글 수(일반 원댓글 + 그 아래 답글) — 댓글 구획 머리의 「댓글 N」
 * @param opinions      의견 원댓글 수({@code pro + con + neutral}) — {@code kind=OPINION} 의 원댓글 수
 * @param opinionsTotal 토론 구획의 보이는 댓글 수(의견 원댓글 + 그 아래 답글)
 * @param pro           찬성 의견 원댓글 수
 * @param con           반대 의견 원댓글 수
 * @param neutral       중립 의견 원댓글 수
 */
public record DebateCountsView(int all, int general, int generalTotal, int opinions, int opinionsTotal, int pro, int con, int neutral) {
}
