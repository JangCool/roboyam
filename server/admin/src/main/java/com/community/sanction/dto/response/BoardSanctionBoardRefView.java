package com.community.sanction.dto.response;

/** 게시판 — 이름은 화면 언어에 맞춰 {@code nameEn} 을 고른다(admin-web {@code BoardSanctionBoardRef}) */
public record BoardSanctionBoardRefView(String id, String slug, String name, String nameEn) {
}
