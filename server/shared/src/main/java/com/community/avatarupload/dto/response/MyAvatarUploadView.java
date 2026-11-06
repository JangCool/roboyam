package com.community.avatarupload.dto.response;

/**
 * {@code GET /api/me/avatar-upload} — 내 아바타 올리기 상태 한 벌({@code POST} 의 201 도 같은 모양). commerce-motion-avatar-contract 「API 계약」.
 *
 * @param ticketCount  쓸 수 있는 아바타 변경권 수(대기 신청에 예약된 장은 빼고)
 * @param ticketPrice  한 장 값(P) — 품목이 없으면 null
 * @param ticketItemId 사러 갈 변경권 품목 ID(없으면 null)
 * @param reserved     대기 신청에 예약된 장이 있다(취소·반려되면 돌아온다)
 * @param request      가장 최근의 대기·반려 신청(없으면 null)
 * @param current      승인된 「내가 올린 아바타」(없으면 null)
 * @param inUse        그 그림을 지금 프로필 아바타로 쓰는 중
 */
public record MyAvatarUploadView(int ticketCount, Integer ticketPrice, String ticketItemId, boolean reserved, AvatarUploadRequestView request,
                                 AvatarUploadCurrentView current, boolean inUse, AvatarUploadLimitsView limits) {
}
