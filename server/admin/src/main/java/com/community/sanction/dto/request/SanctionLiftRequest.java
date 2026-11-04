package com.community.sanction.dto.request;

/** {@code POST /api/admin/sanctions/{id}/lift} — 해제 사유 1~500자 */
public record SanctionLiftRequest(String reason) {
}
