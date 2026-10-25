package com.community.debate.dto;

/**
 * 글 하나의 투표 수 — 입장을 고른 <b>사람 수</b>({@code post_stances} 행 수). 의견(입장 있는 댓글) 수와 따로 센다. 공개 글 상세·댓글 목록 {@code debate.votes}, 관리자 하이라이트
 * {@code commentCounts.votes} 가 같은 모양을 쓴다.
 *
 * @param total {@code pro + con + neutral}
 */
public record DebateVotesView(int pro, int con, int neutral, int total) {

    public static final DebateVotesView EMPTY = new DebateVotesView(0, 0, 0, 0);

    public static DebateVotesView of(int pro, int con, int neutral) {
        return new DebateVotesView(pro, con, neutral, pro + con + neutral);
    }
}
