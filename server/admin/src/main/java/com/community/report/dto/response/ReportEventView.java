package com.community.report.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * 사건 기록 한 줄 — {@code type} REPORTED · AUTO_HIDDEN · REVIEW_STARTED · RESOLVED · DISMISSED · REOPENED.
 * 덧붙임: REPORTED {@code reportId}·{@code reason}, AUTO_HIDDEN {@code hiddenReason}·{@code hiddenUntil}, RESOLVED/DISMISSED {@code action}·{@code reasonCode}·{@code note}·{@code falseReport}
 * (+ DELETE 면 {@code revokedPoints}, 무시로 자동 숨김을 풀었으면 {@code restored}, 일괄이면 {@code bulk})
 */
public record ReportEventView(String id, String type, ReportEventActorView actor, Map<String, Object> detail, Instant createdAt) {
}
