package com.community.mfa.dto;

import java.time.Instant;

/** 신뢰 기기 한 대({@code operator_trusted_devices}) — 쿠키 값의 해시는 싣지 않는다(지금 기기 판정은 {@code current}) */
public record TrustedDeviceRow(long id, String agent, String ip, Instant trustedAt, Instant expiresAt, Instant lastUsedAt, boolean current) {
}
