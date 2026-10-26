package com.community.payment.gateway;

import java.util.List;

/**
 * 결제 다건 조회(페이지 기반 {@code GET /payments}) 한 쪽 — 대사(2a-3)가 쓴다.
 *
 * @param items      결제 건
 * @param number     0부터 센 쪽 번호
 * @param size       쪽 크기
 * @param totalCount 포트원이 알려 준 건수({@code page.totalCount})
 */
public record PgPaymentPage(List<PgPayment> items, int number, int size, int totalCount) {

    public PgPaymentPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
