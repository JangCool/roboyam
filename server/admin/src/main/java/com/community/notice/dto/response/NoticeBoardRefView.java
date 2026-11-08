package com.community.notice.dto.response;

/** 게시판 고르기 칩 하나 — 이름은 관리자 화면 언어로 고른다({@code nameEn} 이 없으면 {@code name}) */
public record NoticeBoardRefView(String id, String slug, String name, String nameEn) {
}
