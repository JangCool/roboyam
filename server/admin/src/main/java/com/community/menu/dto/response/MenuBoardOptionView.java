package com.community.menu.dto.response;

/** 고를 수 있는 게시판 — admin-web {@code MenuBoardOption}. 꺼진 게시판도 넣을 수 있다(방문자에게 안 보인다) */
public record MenuBoardOptionView(String id, String slug, String name, String nameEn, boolean active) {
}
