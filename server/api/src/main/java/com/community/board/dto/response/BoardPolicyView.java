package com.community.board.dto.response;

/** 공개 화면이 쓰는 정책 유효값(user-web {@code BoardPublicPolicy}) — {@code bestCommentMinLikes} 는 기본 설정 값(게시판별로 덮어쓰지 않는다) */
public record BoardPolicyView(int listSize, int pinnedMax, int commentProtectMin, boolean commentProtectEdit, String commentProtectDelete,
                              int commentProtectDelayMinutes, int commentGuestDelayMinutes, int bestCommentMinLikes) {
}
