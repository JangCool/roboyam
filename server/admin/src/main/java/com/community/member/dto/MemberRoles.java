package com.community.member.dto;

import com.community.member.dto.response.MemberRoleDetail;
import java.util.List;

/**
 * 한 회원이 갖는 회원 역할 전부와 거기서 읽는 값. 등급 역할(프로필의 등급)과 일반 회원(사업자 회원 역할이 없는 회원)은 행이 없는 <b>파생 역할</b>이라 조회가 채워 넣는다.
 *
 * @param roles      회원 유형 → 등급 → 직접 부여받은 역할 순서. 탈퇴 회원은 빈 목록
 * @param memberType {@code GENERAL} · {@code BUSINESS}
 * @param boardRole  {@code MANAGER}(매니저가 우선) · {@code STAFF} · {@code NONE}
 */
public record MemberRoles(List<MemberRoleDetail> roles, String memberType, String boardRole) {
}
