package com.community.aimember.dto;

import java.util.List;

/**
 * AI 연애상담소 설정(관리자 — {@code GET/PUT /api/admin/ai-members/counsel}, ai-love-counsel-contract). 화면은 아직 없다(관리자 게시판 화면은 다른 브랜치가 고치는 중 —
 * 이 API 로 켠다). ID 는 문자열.
 */
public final class AiCounselAdminDtos {

    private AiCounselAdminDtos() {
    }

    /**
     * 설정 보기.
     *
     * @param prefixes   고를 수 있는 말머리 — AI 가 쓸 수 있는 일반 게시판의 고유 말머리 전부(게시판 차례 → 말머리 차례)와 그중 상담소인 것({@code counsel})
     * @param counselors AI 회원 전부와 그중 상담사인 것({@code counselor}) · 성향 · 쉬는 이유(쉬지 않으면 null — 시간대는 보지 않는다)
     * @param todayPosts 오늘(한국 날짜) 올라온 상담 사연 수(사이트 전체 — 지운 글 포함)
     */
    public record View(int memberDailyLimit, int siteDailyLimit, AiMemberViews.Range memberDailyRange, AiMemberViews.Range siteDailyRange, long todayPosts,
                       List<Prefix> prefixes, List<Counselor> counselors, int counselorsMax, int styleMax) {
    }

    public record Prefix(String prefixId, String boardId, String boardSlug, String boardName, String name, boolean active, boolean counsel) {
    }

    public record Counselor(String aiMemberId, String userId, String nickname, String provider, String model, boolean enabled, boolean counselor, String style,
                            String restingReason) {
    }

    /**
     * 저장 — 넘긴 말머리만 상담소, 넘긴 AI 회원만 상담사(빠진 것은 푼다). 하루 상한은 회원 0~10 · 사이트 0~5000(0 = 받지 않음).
     *
     * @param counselPrefixIds 상담소로 둘 말머리 ID
     * @param counselors       상담사(최대 5 — 화면은 셋을 가정), 차례는 계정을 만든 차례로 보인다
     */
    public record Save(Integer memberDailyLimit, Integer siteDailyLimit, List<String> counselPrefixIds, List<CounselorSave> counselors) {
    }

    /** @param style 상담 성향 한 마디(1~20자 — 「직설형」·「공감형」·「현실 분석형」) */
    public record CounselorSave(String aiMemberId, String style) {
    }
}
