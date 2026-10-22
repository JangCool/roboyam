package com.community.board.dto.response;

/**
 * 말머리 하나(user-web {@code PrefixView}).
 *
 * @param staffOnly 이 게시판의 매니저·스텝만 고를 수 있다(공지)
 * @param common    공통 말머리(질문·후기·잡담·공지)
 * @param color     표시색 프리셋 이름, null = 자동
 */
public record PrefixView(String id, String name, boolean staffOnly, boolean common, String color) {
}
