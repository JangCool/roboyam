package com.community.group.dto.response;

/** 소모임의 분류(쓰는 게시판이 할당된 분류) — 분류에 없으면 응답에서 null(「분류 없음」) */
public record GroupCategoryRefView(String id, String name) {
}
