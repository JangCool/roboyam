package com.community.emoticon.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 로보티콘 작가 월 정산 API 의 응답 모양(S4) — admin-web {@code useEmoticonAdminApi.types.ts} 의 정산 타입 · user-web {@code useEmoticonStudioApi.types.ts} 의 {@code EmoticonSettlement}.
 * 금액은 원. 정산 줄 ID = {@code yyyyMM-작가회원번호}(예: {@code 202610-1234}).
 */
public final class EmoticonSettlementView {

    private EmoticonSettlementView() {
    }

    // ── 관리자 ──────────────────────────────────────────────────────────────

    /** 정산 계좌(가린 값) — {@code bank} 는 은행 코드, {@code maskedNumber} 는 {@code ****1234} */
    public record AccountMasked(String bank, String maskedNumber, String holderMasked) {
    }

    public record Hold(String reason, Instant at, String by) {
    }

    public record Paid(String transferDate, String memo, Instant at, String by) {
    }

    /**
     * {@code GET /api/admin/emoticons/settlements?month=} 의 한 줄. {@code payeeType} 은 굳힌 줄이면 확정 때의 구분, 예정이면 지금 정산 정보(없으면 null — 원천징수는 개인으로 어림).
     * 목업에 없던 칸: {@code refundCount} · {@code shareAmount}(원천징수 전 작가 몫) · {@code feeRatePercent}(굳힌 줄은 확정 때 값) · {@code confirmedAt}(포기 줄은 포기한 시각).
     * 포기({@code status = WAIVED}, V148): {@code waivedAmount} = 그 줄에서 포기한 금액(원천징수 전 — 다른 상태는 0), {@code carriedOut} 은 다음 달로 이어지는 금액만(포기한 양수는 빠진다),
     * {@code payoutAmount} 0. 처리 기록의 {@code WAIVED} 가 회원 동의(동의 시각) · 운영자 포기 처리(사유·처리자)를 가른다.
     */
    public record AdminRow(String id, String month, EmoticonAdminView.CreatorRef creator, String payeeType, String businessNoMasked, int salesCount, long salesAmount,
                           int refundCount, long refundAmount, long vatAmount, long baseAmount, int feeRatePercent, long feeAmount, long shareAmount, long withholdingAmount,
                           long carriedIn, long payoutAmount, long carriedOut, long waivedAmount, String status, String payoutDate, Instant closesAt, Instant confirmedAt,
                           AccountMasked account, Hold hold, Paid paid) {
    }

    public record Totals(long salesAmount, long refundAmount, long vatAmount, long feeAmount, long withholdingAmount, long payoutAmount, long carriedOut, long waivedAmount) {
    }

    public record Rules(int feeRatePercent, int payoutMinimum, int refundDays, int settlementDay) {
    }

    /** {@code GET /api/admin/emoticons/settlements?month=} */
    public record AdminList(String month, List<AdminRow> items, Totals totals, List<String> months, Rules rules) {
    }

    /** 정산 한 줄의 팩별 내역 — 이름은 그 달 마지막 구매의 산 때 사본, 그림은 지금 상점 판 */
    public record PackLine(String packId, String packName, String coverUrl, int price, int salesCount, long salesAmount, int refundCount, long refundAmount) {
    }

    /**
     * 처리 기록 — {@code CREATED} 는 그 달 첫 판매(또는 첫 환불) 때로 서버가 만들어 넣는다({@code by} null = 시스템). {@code WAIVED}(포기, V148)만 {@code amount}(그 줄에서 포기한 금액) ·
     * {@code consentedAt}(회원이 탈퇴 때 동의한 시각 — 운영자 포기 처리는 null, 그때 {@code by} = 처리한 운영자 · {@code note} = 사유). 다른 기록은 둘 다 null.
     */
    public record Event(String action, Instant at, String by, String note, Long amount, Instant consentedAt) {
    }

    /** {@code GET /api/admin/emoticons/settlements/{id}} — 줄 + 팩별 내역 + 처리 기록(최근이 앞) */
    public record AdminDetail(String id, String month, EmoticonAdminView.CreatorRef creator, String payeeType, String businessNoMasked, int salesCount, long salesAmount,
                              int refundCount, long refundAmount, long vatAmount, long baseAmount, int feeRatePercent, long feeAmount, long shareAmount,
                              long withholdingAmount, long carriedIn, long payoutAmount, long carriedOut, long waivedAmount, String status, String payoutDate,
                              Instant closesAt, Instant confirmedAt, AccountMasked account, Hold hold, Paid paid, List<PackLine> packs, List<Event> events) {

        public static AdminDetail of(AdminRow r, List<PackLine> packs, List<Event> events) {
            return new AdminDetail(r.id(), r.month(), r.creator(), r.payeeType(), r.businessNoMasked(), r.salesCount(), r.salesAmount(), r.refundCount(), r.refundAmount(),
                    r.vatAmount(), r.baseAmount(), r.feeRatePercent(), r.feeAmount(), r.shareAmount(), r.withholdingAmount(), r.carriedIn(), r.payoutAmount(), r.carriedOut(),
                    r.waivedAmount(), r.status(), r.payoutDate(), r.closesAt(), r.confirmedAt(), r.account(), r.hold(), r.paid(), packs, events);
        }
    }

    /** 확정에서 건너뛴 줄 하나 — 까닭 {@code NOT_FOUND}·{@code NOT_ESTIMATED}·{@code NOT_CLOSED}·{@code PREVIOUS_OPEN}·{@code PAYOUT_ACCOUNT_MISSING} */
    public record Skipped(String id, String reason) {
    }

    /** {@code POST /api/admin/emoticons/settlements/confirm { ids }} — 목업에 없던 칸 {@code skippedItems} */
    public record BulkResult(int confirmed, int skipped, List<Skipped> skippedItems) {
    }

    // ── 작가(스튜디오) ──────────────────────────────────────────────────────

    /**
     * {@code GET /api/me/emoticon-studio/settlements} 의 한 줄(user-web {@code EmoticonSettlement}). {@code payoutAmount} 는 넘어온 금액을 넣은 지급액(넘기면 0),
     * {@code carriedOver} = 다음 달로 넘겼다(넘긴 금액 {@code carriedOut} — 음수면 다음 달에서 뺀다), {@code payDate} 는 넘기면 null.
     * 목업에 없던 칸: {@code salesCount} · {@code shareAmount} · {@code carriedOut}.
     */
    public record StudioRow(String id, String month, int salesCount, long salesAmount, long refundAmount, long vatAmount, long baseAmount, long feeAmount, long shareAmount,
                            long withholdingAmount, long payoutAmount, long carriedIn, boolean carriedOver, long carriedOut, String status, String holdReason, String payDate) {
    }

    /** {@code GET /api/me/emoticon-studio/settlements} — 최근 달 먼저 */
    public record StudioList(List<StudioRow> items) {
    }
}
