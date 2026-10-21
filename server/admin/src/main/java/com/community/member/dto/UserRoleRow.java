package com.community.member.dto;

/** 회원이 직접 부여받은 역할 한 건 — 역할의 이름·유형·부여 방식을 함께 읽은 값. 게시판 단위 역할의 {@code boardId} 는 게시판 표가 생길 때 이름으로 바뀐다 */
public record UserRoleRow(Long userId, String roleId, String roleName, String roleTypeId, String grantMode, Long boardId) {
}
