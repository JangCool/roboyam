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
 */
public record WithdrawalView(String state, Losses losses, Money money, Remains remains, List<PendingItem> pending, List<DeliveredOrder> delivered, Business business,
                             List<GroupLed> groupsLed, Rejoin rejoin, Verification verification, String supportEmail, Scheduled scheduled) {

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
