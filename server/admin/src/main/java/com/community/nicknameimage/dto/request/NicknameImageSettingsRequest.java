package com.community.nicknameimage.dto.request;

/**
 * {@code PUT /api/admin/nickname-image-settings} — admin-web 닉네임 이미지 설정 화면. 칸마다 서비스가 검사해 틀린 칸을 모은다({@code 400 VALIDATION fields.<칸>}).
 * 숫자를 {@link Number} 로 받는 이유: 소수·빈 값도 칸 오류로 돌려주려고(형식 오류 {@code MALFORMED_REQUEST} 가 아니라 — 포인트 정책과 같은 판단).
 *
 * @param mode                 {@code GRADE} · {@code CUSTOM}
 * @param gradeKey             최소 등급 키 — GRADE 면 필수, CUSTOM 이면 null(등급 조건 없음) 또는 있는 등급
 * @param requireEmailVerified 필수 — GRADE 면 무엇을 보내도 true 로 저장한다
 */
public record NicknameImageSettingsRequest(String mode, String gradeKey, Number minDays, Number minActivity, Number minPoints, Boolean requireEmailVerified) {
}
