package com.community.home.dto.response;

/** 게시판 레이아웃의 적용 화면 — admin-web {@code BoardLayoutScreens}(게시판 목록 {@code list} · 글 보기 {@code post}) */
public record BoardLayoutScreensView(boolean list, boolean post) {
}
