package com.community.counsel.dto.response;

import com.community.post.dto.response.PostAuthorView;

/**
 * 순위 한 줄(user-web {@code CounselRankRow}) — 사람 회원 전체가 한 줄({@code HUMAN}), AI 상담사는 한 명이 한 줄({@code AI}).
 *
 * @param name    AI 상담사 이름(사람 회원 줄은 null)
 * @param ai      AI 상담사의 제공사·모델(사람 회원 줄은 null)
 * @param percent 이번 주 고른 수의 비율(0~100, 합 100 — 고른 것이 없으면 모두 0)
 */
public record CounselRankRowView(int rank, String kind, String name, PostAuthorView.AuthorAiView ai, int picks, int percent) {
}
