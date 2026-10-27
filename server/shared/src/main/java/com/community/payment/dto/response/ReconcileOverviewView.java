package com.community.payment.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 날짜별 대사 요약({@code GET /api/admin/payment-reconciliation}).
 *
 * @param lastCheckedDay 대사가 끝난(DONE·MISMATCH) 마지막 날
 * @param nextRunAt      다음 자동 실행 시각(배치의 지금 cron — 기본 매일 04:50 KST)
 * @param days           최근 14일(어제부터 거꾸로)
 * @param openIssueCount 열린 이슈 전체 수
 */
public record ReconcileOverviewView(LocalDate lastCheckedDay, Instant nextRunAt, List<ReconcileDayView> days, long openIssueCount) {
}
