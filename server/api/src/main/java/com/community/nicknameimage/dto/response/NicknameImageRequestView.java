package com.community.nicknameimage.dto.response;

import java.time.Instant;

/** 가장 최근의 대기·반려 신청 — user-web {@code NicknameImageRequest}. 반려면 사유와 다시 신청할 수 있는 시각 */
public record NicknameImageRequestView(String id, String status, String url, Instant createdAt, String rejectReason, Instant canReapplyAt) {
}
