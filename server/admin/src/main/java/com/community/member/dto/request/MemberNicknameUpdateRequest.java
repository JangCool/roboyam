package com.community.member.dto.request;

/**
 * 운영자 회원 닉네임 바꾸기({@code PUT /api/admin/members/{memberId}/nickname}) — 새 닉네임 + 사유. 검사는 서비스가 칸별로 한다({@code fields.nickname|reason}).
 */
public record MemberNicknameUpdateRequest(String nickname, String reason) {
}
