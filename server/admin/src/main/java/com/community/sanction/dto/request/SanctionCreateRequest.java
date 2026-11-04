package com.community.sanction.dto.request;

/** {@code POST /api/admin/sanctions} — 검사는 서비스가 한다(칸 {@code kind}·{@code reason}·{@code skipStep}·{@code confirmNickname}) */
public record SanctionCreateRequest(String userId, String kind, String reason, Boolean skipStep, String confirmNickname, String reportCaseId) {
}
