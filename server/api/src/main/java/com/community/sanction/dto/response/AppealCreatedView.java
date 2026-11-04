package com.community.sanction.dto.response;

/** {@code POST /api/me/appeals} → 201 — 접수번호 {@code no}, 상태는 늘 PENDING */
public record AppealCreatedView(String id, long no, String status) {
}
