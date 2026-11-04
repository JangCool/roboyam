package com.community.sanction.dto.response;

import java.util.List;

/** 회원 상세의 {@code boardSanctions}(admin-web {@code MemberBoardSanctionSummary}) — 무효 아닌 전체 수 · 진행 중 쓰기 제한 수 · 열린 반복 검토 수 · 게시판별 · 최근 이력 5개 */
public record MemberBoardSanctionSummaryView(long total, long active, long openReviews, List<MemberBoardSanctionBoardView> boards, List<BoardSanctionLineView> recent) {
}
