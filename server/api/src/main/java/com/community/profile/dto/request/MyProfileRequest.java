package com.community.profile.dto.request;

/**
 * {@code PUT /api/me/profile} — 두 칸 모두 보낸다. 검사 순서·칸 경로가 계약이라 Bean Validation 없이 서비스가 본다.
 *
 * @param nickname  닉네임 — 지금 값과 같으면 바꾸지 않는다(소개만 고치는 저장)
 * @param bio       한 줄 소개 — 앞뒤 공백을 자른 뒤 0~100자, 빈 값·null 은 소개를 지운다
 * @param useTicket 무료 변경을 다 썼을 때 닉네임 변경권 1장을 써서 바꾼다(선택, 없으면 false). 무료로 바꿀 수 있거나 닉네임이 그대로면 보내도 쓰지 않는다
 *                  (member-mypage-contract 「후속 작업: 닉네임 변경권」)
 */
public record MyProfileRequest(String nickname, String bio, Boolean useTicket) {
}
