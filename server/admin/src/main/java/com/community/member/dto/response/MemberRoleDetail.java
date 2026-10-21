package com.community.member.dto.response;

import java.util.List;

/**
 * 보유 회원 역할 한 개(상세).
 *
 * @param typeId     {@code memberGrade} · {@code memberKind} · {@code memberBoard} · {@code memberCustom}
 * @param grantMode  {@code AUTO} · {@code APPOINTED} · {@code MANUAL}
 * @param boardNames 게시판 단위 역할이 맡은 게시판 이름들 — 게시판 표가 아직 없어 늘 빈 배열이다
 */
public record MemberRoleDetail(String roleId, String roleName, String typeId, String grantMode, List<String> boardNames) {
}
