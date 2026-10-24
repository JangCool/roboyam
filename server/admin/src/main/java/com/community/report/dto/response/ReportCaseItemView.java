package com.community.report.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 신고 사건 한 줄(목록·상세 공통) — docs/features/report-admin-contract.md {@code ReportCaseItem}.
 *
 * @param status      PENDING · IN_REVIEW · RESOLVED · DISMISSED
 * @param targetType  POST · COMMENT
 * @param reasons     사유별 신고 수(사유 코드 차례)
 * @param action      닫힌 사건의 처리(HIDE · DELETE · KEEP_HIDDEN · DISMISS), 열린 사건은 null
 * @param reasonCode  닫힌 사건의 사유 코드(없으면 null) — 계약에 더한 칸
 * @param note        닫힌 사건의 처리 글(없으면 null) — 계약에 더한 칸
 * @param falseReport 허위 신고로 표시한 무시인가 — 계약에 더한 칸
 */
public record ReportCaseItemView(String id, long caseNo, String status, String targetType, ReportTargetView target, ReportBoardRefView board,
                                 ReportAuthorView author, int reportCount, List<ReportReasonCountView> reasons, Instant firstReportedAt, Instant lastReportedAt,
                                 ReportOperatorRefView reviewer, Instant closedAt, ReportOperatorRefView closedBy, String action, String reasonCode, String note,
                                 boolean falseReport) {
}
