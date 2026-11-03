package com.community.dashboard.dto.response.gadget;

import java.time.LocalDate;

/**
 * {@code RECONCILE} — 결제 대사 상태.
 *
 * @param lastDate        기록이 있는 가장 최근 대사일(한국 날짜 — {@code payment_reconcile_days}), 한 번도 없으면 null
 * @param status          MISMATCHED = 열린 대사 이슈가 있음(날짜와 관계없이) · MATCHED = 열린 이슈가 없고 최근 대사일이 끝남(DONE, 또는 불일치였으나 모두 처리한 MISMATCH) ·
 *                        PENDING = 열린 이슈가 없고 대사 전 · 도는 중(RUNNING) · 실패(FAILED — 다음 실행이 다시 한다)
 * @param mismatched      열린(OPEN) 대사 이슈 수 — 날짜와 관계없이 운영자가 아직 처리하지 않은 것
 * @param webhookFailures 최근 24시간 웹훅 처리 실패(REJECTED 서명·머리글 거절 + RETRY 다시 받기)
 */
public record DashboardReconcileView(LocalDate lastDate, String status, long mismatched, long webhookFailures) implements DashboardGadgetData {
}
