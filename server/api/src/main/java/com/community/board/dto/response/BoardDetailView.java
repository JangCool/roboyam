package com.community.board.dto.response;

import java.util.List;

/**
 * {@code GET /api/boards/{slug}} — 게시판 요약 + 유효 정책 + 지금 보는 사람의 판정(user-web {@code BoardDetail}).
 *
 * @param writeMinGradeLabel   {@code writeMin} 이 등급 키일 때 그 등급의 이름(안내 문구용), 아니면 null
 * @param commentMinGradeLabel {@code commentMin} 이 등급 키일 때 그 등급의 이름, 아니면 null
 * @param introText            게시판 안내문(평문), 없으면 null
 * @param writeGuideText       글쓰기 안내문(평문), 없으면 null
 * @param reportEnabled        신고 기능 사용(관리자 신고 정책)
 */
public record BoardDetailView(String id, String slug, String name, String nameEn, String description, boolean investmentNotice, boolean staffOnlyWrite,
                              String writeMin, String commentMin, String writeLock, boolean imageUploadAllowed, String postEditor, String commentEditor,
                              String layout, boolean dealEnabled,
                              boolean promo, boolean viewToggle, boolean allowAdult, List<PrefixView> prefixes, String color, String writeMinGradeLabel,
                              String commentMinGradeLabel, String introText, String writeGuideText, boolean reportEnabled, BoardPolicyView policy,
                              BoardGuestRulesView guestRules, BoardNewMemberRulesView newMemberRules, BoardViewerView viewer) {
}
