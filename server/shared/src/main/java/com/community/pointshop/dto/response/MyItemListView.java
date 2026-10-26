package com.community.pointshop.dto.response;

import java.util.List;

/**
 * {@code GET /api/me/items} — 최근에 산 줄이 위. 소모형은 장마다 한 줄(화면이 품목별로 묶는다).
 *
 * @param refundNeedsApproval 지금 정지·영구 정지 중이라 셀프 환불이 환불 신청(운영자 수락)으로 처리된다 — 화면은 [환불] 대신 [환불 신청]
 */
public record MyItemListView(int refundDays, boolean refundNeedsApproval, List<MyItemView> items) {
}
