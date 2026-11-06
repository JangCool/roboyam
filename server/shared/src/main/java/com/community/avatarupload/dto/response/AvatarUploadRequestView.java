package com.community.avatarupload.dto.response;

import java.time.Instant;

/**
 * 대기·반려 신청 하나 — {@code url} 은 저장한 그림(움직이는 GIF 는 원본), {@code stillUrl} 은 움직이는 GIF 의 첫 장면 PNG(그 밖 null). 파일이 지워졌으면 둘 다 null.
 *
 * @param status {@code PENDING} · {@code REJECTED}
 */
public record AvatarUploadRequestView(String id, String status, String url, String stillUrl, boolean animated, Instant createdAt, String rejectReason) {
}
