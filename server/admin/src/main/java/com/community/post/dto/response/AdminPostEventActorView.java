package com.community.post.dto.response;

/**
 * 조치한 사람.
 *
 * @param kind OPERATOR · SYSTEM(신고 자동 숨김) · BOARD_STAFF(그 게시판 매니저·스텝 — board-staff-action-contract 정책 8)
 * @param id   운영자 ID · BOARD_STAFF 면 회원 ID(SYSTEM 은 null)
 * @param name 운영자의 지금 이름 · BOARD_STAFF 면 회원의 지금 닉네임(자리 없이 — 화면이 「닉네임(자리)」로 붙인다). SYSTEM·지워진 사람은 null
 * @param role BOARD_STAFF 면 처리 때의 자리 MANAGER · STAFF, 그 밖 null
 */
public record AdminPostEventActorView(String kind, String id, String name, String role) {
}
