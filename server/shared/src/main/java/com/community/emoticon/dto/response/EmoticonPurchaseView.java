package com.community.emoticon.dto.response;

import com.community.payment.dto.response.PaymentCheckoutView;
import java.time.Instant;
import java.util.List;

/**
 * 로보티콘 유료 구매 API 의 응답 모양 — user-web {@code useEmoticonApi.types.ts}({@code EmoticonPurchaseStart})·{@code useMyEmoticonsApi.types.ts}({@code MyEmoticonPurchase}·
 * {@code MyEmoticonRefundResult}), admin-web {@code useEmoticonAdminApi.types.ts}({@code EmoticonSaleRow}·{@code EmoticonSalePage}·{@code EmoticonSaleRefundResult})와 같은 이름·칸.
 * ID 는 문자열이다.
 */
public final class EmoticonPurchaseView {

    private EmoticonPurchaseView() {
    }

    /** 포트원 구매자(KG이니시스 요구) — 가짜 게이트웨이면 null */
    public record Customer(String fullName, String phoneNumber, String email) {
    }

    /** {@code POST /api/emoticons/packs/{id}/purchase} — 결제창 값(금액은 서버가 팩에서 읽었다). 이후 공용 {@code POST /api/payments/{paymentId}/complete} */
    public record PurchaseStart(String orderType, String orderRefId, Instant expiresAt, PaymentCheckoutView checkout, String packId, Customer customer) {
    }

    /** 구매 내역 한 줄 — {@code status} PAID · REFUNDED, {@code refundable} = 지금 셀프 환불할 수 있는가(서버 판정) */
    public record MyPurchase(String id, String packId, String packName, String coverUrl, int amount, String sellerName, String creatorNickname, String receiptUrl,
                             Instant paidAt, Instant refundUntil, Instant usedAt, String status, Instant refundedAt, boolean refundable) {
    }

    /** {@code GET /api/me/emoticon-purchases} — 최근 결제 먼저 */
    public record MyPurchaseList(List<MyPurchase> items) {
    }

    /** {@code POST /api/me/emoticon-purchases/{id}/refund} — 결제 취소(전액)를 넣었고 보유를 뺐다 */
    public record RefundResult(String id, String status, Instant refundedAt, int canceledAmount) {
    }

    // ── 관리자 판매 내역 ──────────────────────────────────────────────────────

    /** 구매 회원 */
    public record Buyer(String memberNo, String nickname, boolean withdrawn) {
    }

    /** 셀프 환불 판정(결제 완료 줄만) — 막힌 까닭 {@code USED}·{@code EXPIRED}·null */
    public record SelfRefund(boolean allowed, Instant until, String blockedBy) {
    }

    /** 환불 기록 — 종류 SELF·EXCEPTION, {@code by} = 운영자 이름(셀프·지워진 운영자는 null) */
    public record Refund(String kind, Instant at, String by, String reason) {
    }

    /** {@code GET /api/admin/emoticons/sales} 의 한 줄 — {@code settlementMonth} 는 정산(S4)이 생기기 전까지 null */
    public record SaleRow(String id, Instant paidAt, String packId, String packName, String coverUrl, EmoticonAdminView.CreatorRef creator, Buyer buyer, int amount,
                          String paymentId, Instant firstUsedAt, String status, SelfRefund selfRefund, Refund refund, String settlementMonth) {
    }

    /** {@code GET /api/admin/emoticons/sales} — {@code paidAmount} = 거른 결과 전체의 결제 완료(환불 뺀) 합계 */
    public record SalePage(List<SaleRow> items, long total, long paidAmount) {
    }

    /** {@code POST /api/admin/emoticons/sales/{id}/refund} */
    public record SaleRefundResult(SaleRow sale) {
    }
}
