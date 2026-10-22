package com.community.board.dto.response;

import com.community.board.dto.BoardDefaultsValues;
import com.community.board.dto.BoardPolicyValues;
import java.util.List;

/** 게시판 설정 화면 한 번에(admin-web {@code BoardDetail}) — {@code GET /api/admin/boards/by-slug/{slug}} · {@code GET·PUT /api/admin/boards/{id}} */
public record BoardDetailView(String id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                              String color, boolean allowAdult, boolean investmentNotice, boolean active, long postCount, String writeLock, String introText,
                              String writeGuideText, BoardPolicyValues policy, List<BoardPrefixView> prefixes, BoardDefaultsValues defaults, int diffCount) {
}
