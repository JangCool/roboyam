package com.community.board.dto.response;

/**
 * 담당 후보(admin-web {@code BoardStaffCandidate}).
 *
 * @param email      가린 이메일
 * @param boardCount 지금 맡은 게시판 수(매니저·스텝 합산, 최대 5)
 * @param ineligible 자격 미달 사유(ALREADY_ASSIGNED · SUSPENDED · EMAIL_UNVERIFIED · GRADE_TOO_LOW · BOARD_LIMIT) — null 이면 지정할 수 있다
 */
public record BoardStaffCandidateView(String memberId, String nickname, String email, String gradeKey, long boardCount, String ineligible) {
}
