package com.community.member.dto.response;

/** 회원 역할의 이름표 — 목록 한 줄의 역할, 역할 고르개({@code GET …/members/role-options})의 선택지가 같은 모양이다 */
public record MemberRoleRef(String roleId, String roleName) {
}
