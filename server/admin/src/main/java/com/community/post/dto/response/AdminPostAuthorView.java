package com.community.post.dto.response;

/**
 * 게시글 관리 줄의 작성자.
 *
 * @param kind      MEMBER · GUEST
 * @param id        회원 ID(비회원 null)
 * @param name      회원은 지금 닉네임(탈퇴·없음이면 빈 글자), 비회원은 입력한 이름
 * @param withdrawn 탈퇴·삭제된 회원인가(비회원 false)
 */
public record AdminPostAuthorView(String kind, String id, String name, boolean withdrawn) {
}
