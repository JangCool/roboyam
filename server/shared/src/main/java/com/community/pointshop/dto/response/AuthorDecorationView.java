package com.community.pointshop.dto.response;

/**
 * 작성자 장식(user-web {@code AuthorDecoration}) — 글·댓글·목록·쪽지 상대·공개 프로필. 만료된 장착은 빠진다. 값은 품목의 지금 값(색을 고치면 산 사람의 표시도 바뀐다).
 *
 * @param nicknameColor 원색 {@code #RRGGBB}(대비 보정은 그릴 때) — 없으면 null
 * @param profileBorder 팔레트 키 — 없으면 null
 */
public record AuthorDecorationView(String nicknameColor, String profileBorder) {
}
