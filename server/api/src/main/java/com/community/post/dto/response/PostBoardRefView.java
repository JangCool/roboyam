package com.community.post.dto.response;

/** 글이 속한 게시판(글 상세의 {@code board}) */
public record PostBoardRefView(String id, String slug, String name, String nameEn, String color) {
}
