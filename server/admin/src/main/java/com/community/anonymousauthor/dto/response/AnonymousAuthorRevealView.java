package com.community.anonymousauthor.dto.response;

/**
 * 연 작성자의 회원 요약(admin-web 열람 창 — 회원 상세로 이동).
 *
 * @param memberId  회원 ID
 * @param nickname  지금 닉네임 — 탈퇴(익명화)로 지워졌으면 null(화면 「탈퇴한 회원」)
 * @param status    회원 상태 ACTIVE · SUSPENDED · BANNED · WITHDRAWING · WITHDRAWN(회원 관리와 같은 값)
 * @param withdrawn 탈퇴·삭제된 회원인가
 */
public record AnonymousAuthorRevealView(String memberId, String nickname, String status, boolean withdrawn) {
}
