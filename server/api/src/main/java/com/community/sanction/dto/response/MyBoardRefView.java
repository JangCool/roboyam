package com.community.sanction.dto.response;

/** 게시판 제재가 적용된 게시판 — {@code nameEn} 선택 영문명 */
public record MyBoardRefView(String id, String slug, String name, String nameEn) {
}
