package com.community.popular.dto.request;

/**
 * {@code PUT /api/admin/popular-settings} — 11칸을 모두 보낸다. 칸마다 서비스가 검사해 틀린 칸을 모은다({@code 400 VALIDATION fields.<칸>}).
 * 숫자를 {@link Number} 로 받는 이유: 소수·빈 값도 칸 오류로 돌려주려고(형식 오류 {@code MALFORMED_REQUEST} 가 아니라 — 포인트 정책·닉네임 이미지 설정과 같은 판단).
 *
 * @param weekStart MONDAY · SUNDAY(대소문자 무시)
 */
public record PopularSettingsRequest(Number likeWeight, Number commentWeight, Number viewWeight, Number gravity, Number ageOffsetHours, Number minLikes,
                                     Number minComments, Number minViews, Boolean excludeStaffBoards, Number maxPerAuthor, String weekStart) {
}
