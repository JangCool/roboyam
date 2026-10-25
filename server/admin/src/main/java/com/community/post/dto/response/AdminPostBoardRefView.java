package com.community.post.dto.response;

/** 게시글 관리 줄의 게시판 — 관리자가 입력한 이름 그대로(영어 이름은 {@code nameEn}, 없으면 null). 게시판을 찾지 못하면 이름·주소가 null */
public record AdminPostBoardRefView(String id, String name, String nameEn, String slug) {
}
