package com.community.board.dto.response;

import java.time.Instant;

/** 게시판 매니저·스텝 한 명(admin-web {@code BoardStaffMember}). appointedBy = 지정한 운영자 이름(모르면 빈 글자) */
public record BoardStaffMemberView(String memberId, String nickname, String gradeKey, String role, Instant appointedAt, String appointedBy) {
}
