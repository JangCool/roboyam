package com.community.board.dto.response;

import com.community.board.dto.BoardDefaultsValues;
import com.community.board.dto.BoardPolicyValues;
import java.util.List;

/**
 * 게시판 설정 화면 한 번에(admin-web {@code BoardDetail}) — {@code GET /api/admin/boards/by-slug/{slug}} · {@code GET·PUT /api/admin/boards/{id}}
 *
 * @param gadgetExclusions 「가젯 노출 금지」로 고른 가젯 이름({@code BoardGadget}, 목록 순서) — 게시판마다의 값(기본 설정에서 물려받지 않는다)
 * @param gadgets          고를 수 있는 가젯 전부(목록 순서, 이름은 요청 언어)
 * @param groupCategoryId   할당된 소모임 분류(소모임 게시판만) — 분류에 안 들었거나 일반 게시판이면 null
 * @param groupCategoryName 그 분류 이름 — 없으면 null
 * @param adsHidden        「이 게시판에 광고 숨김」(기본 false) — 19금 게시판은 광고 자리 설정 {@code adult.enabled} 가 꺼져 있으면 이 칸과 상관없이 광고가 없다
 */
public record BoardDetailView(String id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                              String color, boolean allowAdult, boolean investmentNotice, boolean active, long postCount, String writeLock, String introText,
                              String writeGuideText, String writeTemplate, List<String> gadgetExclusions, BoardPolicyValues policy, List<BoardPrefixView> prefixes,
                              BoardDefaultsValues defaults, int diffCount, List<BoardGadgetView> gadgets, String groupCategoryId,
                              String groupCategoryName, boolean adsHidden) {
}
