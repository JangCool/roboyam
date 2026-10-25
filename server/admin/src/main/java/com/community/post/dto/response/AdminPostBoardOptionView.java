package com.community.post.dto.response;

import java.util.List;

/** 게시판 고르기의 게시판(켜진 것만, 정렬 순서) — {@code pinnedMax} 는 유효 고정 한도(0 이면 고정 불가) */
public record AdminPostBoardOptionView(String id, String slug, String name, String nameEn, int pinnedMax, List<AdminPostPrefixOptionView> prefixes) {
}
