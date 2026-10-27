package com.community.payment.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 대사 날짜 한 줄 — 상태 PENDING(아직 안 함 — 기록 없음)·RUNNING·DONE·MISMATCH·FAILED.
 *
 * @param issueCount     그 날 마지막 대사에서 찾은 불일치 수(이미 열려 있던 것 포함)
 * @param openIssueCount 그 날 발견해 아직 열린 이슈 수
 */
public record ReconcileDayView(LocalDate day, String status, int checkedCount, int issueCount, int openIssueCount, Instant finishedAt) {
}
