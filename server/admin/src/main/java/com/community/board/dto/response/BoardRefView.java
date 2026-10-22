package com.community.board.dto.response;

/** 게시판을 가리키는 짧은 모양 — 이름·영문명을 함께 내리고 어느 쪽을 보일지는 화면이 정한다 */
public record BoardRefView(String id, String slug, String name, String nameEn) {
}
