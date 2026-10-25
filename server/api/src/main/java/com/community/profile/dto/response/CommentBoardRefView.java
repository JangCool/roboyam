package com.community.profile.dto.response;

/** 작성 댓글 줄의 게시판 — 이름은 운영자가 입력한 값({@code nameEn} 은 없으면 null) */
public record CommentBoardRefView(String slug, String name, String nameEn) {
}
