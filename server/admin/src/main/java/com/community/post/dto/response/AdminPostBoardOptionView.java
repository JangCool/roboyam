package com.community.post.dto.response;

import java.util.List;

/**
 * 게시판 고르기의 게시판(켜진 것만, 정렬 순서) — {@code pinnedMax} 는 유효 고정 한도(0 이면 고정 불가).
 * {@code kind} 는 게시판 종류(GENERAL·DEAL·PROMO·GROUP) — 필터·이름 풀기는 모두 쓰고, 글 옮기기는 소모임(GROUP)을 뺀다
 */
public record AdminPostBoardOptionView(String id, String slug, String name, String nameEn, String kind, int pinnedMax, List<AdminPostPrefixOptionView> prefixes) {
}
