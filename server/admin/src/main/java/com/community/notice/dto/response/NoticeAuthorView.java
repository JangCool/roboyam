package com.community.notice.dto.response;

/**
 * 공지 작성자 — 게시글 관리 {@code AdminPostAuthorView} 칸 + 이어진 운영자 이름.
 *
 * @param kind         MEMBER · GUEST
 * @param name         회원이면 지금 닉네임(프로필이 없으면 빈 글자), 비회원이면 이름
 * @param withdrawn    탈퇴 · 삭제 · 프로필 없음
 * @param operatorName 그 회원 계정에 이어진 운영자 이름(연결이 없으면 null — 멈춘 연결도 이름을 보인다)
 */
public record NoticeAuthorView(String kind, String id, String name, boolean withdrawn, String operatorName) {
}
