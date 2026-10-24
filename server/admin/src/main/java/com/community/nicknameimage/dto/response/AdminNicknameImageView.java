package com.community.nicknameimage.dto.response;

import java.time.Instant;

/**
 * 닉네임 그림 신청 한 줄(관리자) — {@code url} 은 저장한 그림(움직이는 GIF 는 원본, 파일을 지운 신청은 null), {@code bytes} 는 올린 파일 크기, {@code width}·{@code height} 는 저장한 크기.
 * {@code reviewer} 는 승인·반려한 운영자, 해제는 {@code revokedAt}·{@code revokeReason}.
 */
public record AdminNicknameImageView(String id, NicknameImageMemberView member, String status, String url, String contentType, int bytes, int width, int height,
                                    int frames, Instant createdAt, Instant reviewedAt, NicknameImageReviewerView reviewer, String rejectReason, Instant revokedAt,
                                    String revokeReason) {
}
