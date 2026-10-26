package com.community.group.dto.response;

/** 소모임이 글·말머리·투자 고지를 담는 소모임 게시판 — 분류에서 빠져도, 게시판이 꺼져도 그대로다. {@code nameEn} 은 영어 이름(없으면 null) */
public record GroupBoardRefView(String slug, String name, String nameEn) {
}
