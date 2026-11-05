package com.community.withdrawal.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/me/withdrawal} — 화면 타입 {@code WithdrawalView}(apps/user-web … useWithdrawalApi.types.ts 와 같은 모양).
 *
 * @param state     {@code NONE} · {@code SCHEDULED}
 * @param money     충전 포인트가 없으면 null
 * @param business  사업자 회원이 아니거나 진행 중 건이 없으면 null
 * @param scheduled 탈퇴 진행 중일 때만
 * @param emoticonCreator 로보티콘 작가 요약(V148) — 작가(만든 팩이 있거나 정산 정보를 등록함)가 아니면 null
 */
public record WithdrawalView(String state, Losses losses, Money money, Remains remains, List<PendingItem> pending, List<DeliveredOrder> delivered, Business business,
                             List<GroupLed> groupsLed, Rejoin rejoin, Verification verification, String supportEmail, Scheduled scheduled,
                             EmoticonCreator emoticonCreator) {

    /**
     * 로보티콘 작가 요약 — 탈퇴하면 새 판매가 멈출 판매 중 팩 수 · 반려로 닫힐 심사 대기 건 수 · 아직 지급되지 않은 수익(원천징수 전, 원 — 예정·확정·보류 + 넘긴 금액, 양수만 ·
     * 최소 지급액과 상관없이 셈) · 정산 정보 등록 여부 · {@code decisionRequired} = 수익이 있는데 정산 정보가 없어 「정산 정보 등록」 또는 「포기 동의」를 골라야 탈퇴가 된다.
     */
    public record EmoticonCreator(int onSalePacks, int pendingReviews, long unpaidEarnings, boolean payoutAccountRegistered, boolean decisionRequired) {
    }

    public record Losses(long freePoints, int items, int refundableItems, String gradeName, int attendanceStreak) {
    }

    public record Money(long paidPoints, long refundEstimate, long fee, long feeBasePoints, int feeRatePercent) {
    }

    public record Remains(int posts, int comments, boolean hasPaymentHistory, int retentionYears) {
    }

    public record PendingItem(String code, String label, String link, Instant waitUntil) {
    }

    public record DeliveredOrder(String orderNo, String link, Instant deliveredAt, Instant returnDeadline) {
    }

    public record BizRunning(String id, String kind, String label, String link, Instant endsAt, long forfeitAmount) {
    }

    public record Business(List<BizRunning> running, long forfeitTotal, long refundWaitingTotal) {
    }

    public record GroupCandidate(String id, String nickname, String role) {
    }

    public record GroupLed(String id, String name, String link, int memberCount, List<GroupCandidate> candidates, GroupCandidate autoDelegate) {
    }

    public record Rejoin(Instant availableAt, boolean extendedByRestriction, Instant restrictionEndsAt, String restrictionReason) {
    }

    public record Verification(String method, boolean recentLoginOk) {
    }

    public record Scheduled(Instant requestedAt, Instant expectedCompleteAt, List<PendingItem> pending) {
    }
}
