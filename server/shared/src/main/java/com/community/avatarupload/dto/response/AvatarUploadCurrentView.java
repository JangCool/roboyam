package com.community.avatarupload.dto.response;

import java.time.Instant;

/** 승인된 「내가 올린 아바타」 — 정지 그림({@code stillUrl})은 움직이는 GIF 만 */
public record AvatarUploadCurrentView(String url, String stillUrl, boolean animated, Instant approvedAt) {
}
