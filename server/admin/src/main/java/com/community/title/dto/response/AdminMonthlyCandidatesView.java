package com.community.title.dto.response;

import java.util.List;

/**
 * 이달의 작가 뽑기({@code GET /api/admin/titles/monthly-candidates?month=YYYY-MM} — 계약 「구현 결정」 3·7, admin-web {@code MonthlyCandidates}).
 *
 * @param max        그 달 최대 인원(설정)
 * @param awarded    그 달 이미 받은 회원(받은 줄 — {@code grantId}·{@code member} …)
 * @param candidates 후보 = 그 달 우수 글로 뽑힌(살아 있는 선정) 글의 글쓴이 + 「후보에 넣기」로 넣은 글의 글쓴이 — 글 많은 차례. AI 회원·운영팀 계정은 빠진다
 */
public record AdminMonthlyCandidatesView(String month, int max, List<AdminTitleGrantView> awarded, List<Candidate> candidates) {

    /** 후보 회원 하나 — {@code awarded} 그 달 이미 받았나, {@code avatarUrl} 아바타 */
    public record Candidate(AdminTitleGrantView.MemberView member, String avatarUrl, boolean awarded, List<Post> posts) {
    }

    /** 후보 글 — {@code featured} 우수 글 선정, {@code manual} 「후보에 넣기」로 넣음(둘 다일 수 있다) */
    public record Post(String id, String title, boolean featured, boolean manual) {
    }
}
