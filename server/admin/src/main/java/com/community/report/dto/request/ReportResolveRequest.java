package com.community.report.dto.request;

import com.community.sanction.dto.request.SanctionWithReportRequest;

/**
 * 신고 사건 조치 — {@code action} HIDE · DELETE · KEEP_HIDDEN · DEACTIVATE(소모임 사건만 — note 1~200자, 모임장에게 사유로 간다) · DEAL_END(핫딜 글 사건만) ·
 * NONE(「제재만」 — 대상은 그대로, {@code sanction} 필수, 사건 조치는 SANCTION_ONLY), {@code reasonCode} 사건 대상 체계의 신고 사유 코드, {@code note} 1~500자(작성자 알림에 간다).
 * {@code sanction}(member-sanction-contract 결정 4): 사건 작성자에게 곁들이는 제재 — 조치와 같은 트랜잭션, 제재 오류면 조치도 일어나지 않는다.
 */
public record ReportResolveRequest(String action, String reasonCode, String note, SanctionWithReportRequest sanction) {
}
