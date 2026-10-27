package com.community.payment.dto.response;

/**
 * 결제 화면의 회원 — 번호·닉네임만(이메일 없음, 결정 17). 탈퇴했으면 번호를 싣지 않는다(포인트 상점 구매 내역과 같다).
 *
 * @param id        회원 번호(글자) — 탈퇴했으면 null
 * @param nickname  닉네임(프로필이 없으면 빈 글자)
 * @param withdrawn 탈퇴했는가
 */
public record PaymentMemberView(String id, String nickname, boolean withdrawn) {
}
