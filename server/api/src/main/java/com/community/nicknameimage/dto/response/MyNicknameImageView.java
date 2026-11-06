package com.community.nicknameimage.dto.response;

import java.time.Instant;

/**
 * {@code GET /api/me/nickname-image}(올리기 201 도 같은 모양) — user-web {@code MyNicknameImage}.
 *
 * @param eligible         정지가 아니고 모든 조건을 채웠다
 * @param ineligibleReason {@code REQUIREMENTS_NOT_MET} · {@code SUSPENDED} · null — 올리기가 낼 오류와 같은 차례(이메일 인증 조건 → 정지 → 나머지 조건)
 * @param grade            내 등급
 * @param requirements     신청 조건(켜진 것만)
 * @param ticketCount      쓸 수 있는 닉네임 이미지 변경권 수(대기 신청에 예약된 장은 빼고 — commerce-motion-avatar-contract 규칙 0)
 * @param ticketPrice      한 장 값(P) — 품목이 없으면 null
 * @param ticketItemId     사러 갈 변경권 품목 ID(없으면 null)
 * @param paused           적용 중인 그림이 움직이고 움직이는 아바타를 쓰는 중이라 쉬는 중(규칙 2 — 저장하지 않고 판정, {@code POST …/resume} 으로 다시 쓴다)
 */
public record MyNicknameImageView(boolean eligible, String ineligibleReason, NicknameImageGradeView grade, NicknameImageRequirementsView requirements,
                                  NicknameImageCurrentView current, NicknameImageRequestView request, Instant reapplyAt, NicknameImageLimitsView limits, int ticketCount,
                                  Integer ticketPrice, String ticketItemId, boolean paused) {
}
