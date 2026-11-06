package com.community.nicknameimage.dto.response;

import java.time.Instant;

/**
 * 가장 최근의 대기·반려 신청 — user-web {@code NicknameImageRequest}. 반려면 사유와 다시 신청할 수 있는 시각. {@code ticketReserved} 는 대기 신청에 변경권 한 장이 예약돼 있다
 * (취소·반려되면 돌아온다 — 이 규칙 전에 올라온 대기 신청은 false, 반려 신청도 false).
 */
public record NicknameImageRequestView(String id, String status, String url, Instant createdAt, String rejectReason, Instant canReapplyAt, boolean ticketReserved) {
}
