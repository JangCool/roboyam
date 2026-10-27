package com.community.payment.gateway;

import java.time.Instant;
import java.util.List;

/**
 * 포트원 결제 단건 조회 결과에서 결제 모듈이 쓰는 칸(V2 {@code Payment}). 본문의 나머지(구매자 정보·PG 응답 등)는 읽지 않는다.
 *
 * @param paymentId       결제 건 아이디({@code id})
 * @param storeId         상점 아이디({@code storeId}) — 우리 상점이어야 한다
 * @param status          상태
 * @param totalAmount     총 결제금액({@code amount.total}) — 우리가 청구한 금액과 비교한다
 * @param cancelledAmount 총 취소금액({@code amount.cancelled})
 * @param currency        통화({@code currency}, 예 KRW)
 * @param paidAt          결제 완료 시점
 * @param pgTxId          PG사 거래 아이디
 * @param receiptUrl      거래 영수증 URL
 * @param payMethod       결제수단 종류({@code method.type}, 예 {@code PaymentMethodCard})
 * @param pgProvider      PG사 결제 모듈({@code channel.pgProvider})
 * @param cancellations   취소 내역
 * @param statusChangedAt 상태 업데이트 시점(대사 기준)
 */
public record PgPayment(String paymentId, String storeId, PgPaymentStatus status, long totalAmount, long cancelledAmount, String currency, Instant paidAt,
                        String pgTxId, String receiptUrl, String payMethod, String pgProvider, List<PgCancellation> cancellations, Instant statusChangedAt) {

    public PgPayment {
        cancellations = cancellations == null ? List.of() : List.copyOf(cancellations);
    }

    /** 포트원에 없는 결제 */
    public static PgPayment notFound(String paymentId) {
        return new PgPayment(paymentId, null, PgPaymentStatus.NOT_FOUND, 0, 0, null, null, null, null, null, null, List.of(), null);
    }

    /** 결제 완료 상태(취소가 없다)인가 — 확정은 이것만 받아들인다 */
    public boolean paid() {
        return status == PgPaymentStatus.PAID;
    }
}
