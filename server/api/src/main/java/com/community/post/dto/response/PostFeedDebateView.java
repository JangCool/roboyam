package com.community.post.dto.response;

import com.community.debate.dto.DebateVotesView;

/**
 * 섞인 목록 줄의 찬반 토론 요약(user-web {@code PostFeedDebate}) — 입장을 고른 사람 수(글 상세 {@code debate.votes} 와 같은 기준 — {@code post_stances} 행 수).
 * 토론 글 = 토론이 열려 있거나({@code post_debates} 행) 이미 표가 있는 글(글 상세의 토론 구획이 보이는 기준 — user-web {@code UtilDebate.visible} 과 같다). 그 밖의 글·숨긴 글은 줄의 칸이 null.
 * docs/features/feed-debate-counts-contract.md.
 *
 * @param neutral 중립 수 — 열린 토론이 중립을 받지 않으면({@code neutralEnabled = false}) null(화면이 찬·반만 그린다). 닫힌 토론은 글 상세처럼 중립을 받는 것으로 본다
 */
public record PostFeedDebateView(int pro, int con, Integer neutral) {

    public static PostFeedDebateView of(DebateVotesView votes, boolean neutralEnabled) {
        return new PostFeedDebateView(votes.pro(), votes.con(), neutralEnabled ? votes.neutral() : null);
    }
}
