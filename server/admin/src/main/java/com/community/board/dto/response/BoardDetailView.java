package com.community.board.dto.response;

import com.community.board.dto.BoardDefaultsValues;
import com.community.board.dto.BoardPolicyValues;
import java.util.List;

/**
 * 게시판 설정 화면 한 번에(admin-web {@code BoardDetail}) — {@code GET /api/admin/boards/by-slug/{slug}} · {@code GET·PUT /api/admin/boards/{id}}
 *
 * @param gadgetExclusions 「가젯 노출 금지」로 고른 가젯 이름({@code BoardGadget}, 목록 순서) — 게시판마다의 값(기본 설정에서 물려받지 않는다)
 * @param gadgets          고를 수 있는 가젯 전부(목록 순서, 이름은 요청 언어)
 */
public record BoardDetailView(String id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                              String color, boolean allowAdult, boolean investmentNotice, boolean active, long postCount, String writeLock, String introText,
                              String writeGuideText, List<String> gadgetExclusions, BoardPolicyValues policy, List<BoardPrefixView> prefixes,
                              BoardDefaultsValues defaults, int diffCount, List<BoardGadgetView> gadgets) {
}
