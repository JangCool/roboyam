package com.community.title.dto.response;

import java.util.List;

/**
 * 명예의 전당({@code GET /api/hall-of-fame} — 계약 「구현 결정」 7). 정상(ACTIVE) 회원만, 꺼진 칭호는 빈 목록.
 *
 * @param honorary 명예 작가(받은 차례)
 * @param yearly   역대 올해의 작가(최신 해부터)
 * @param monthly  역대 이달의 작가(최신 달부터)
 */
public record HallOfFameView(List<MemberView> honorary, List<YearView> yearly, List<MonthView> monthly) {

    /** 회원 — ID·닉네임·아바타(고르지 않았으면 기본 그림) */
    public record MemberView(String id, String nickname, String avatarUrl) {
    }

    public record YearView(String year, List<MemberView> members) {
    }

    public record MonthView(String month, List<MemberView> members) {
    }
}
