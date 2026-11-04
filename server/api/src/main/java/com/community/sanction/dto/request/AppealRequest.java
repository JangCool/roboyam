package com.community.sanction.dto.request;

/** {@code POST /api/me/appeals} — 검사는 서비스가 한다(사유 10~500자 {@code fields.reason}) */
public record AppealRequest(String sanctionId, String reason) {
}
