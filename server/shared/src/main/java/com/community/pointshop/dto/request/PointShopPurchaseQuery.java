package com.community.pointshop.dto.request;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import java.time.Instant;

/**
 * {@code GET /api/admin/point-shop/purchases?from&to&status&kind&q&page&size} — 조건은 모두 선택(null = 없음). {@code q} 는 닉네임 일부 또는 회원번호.
 * {@code refundRequest} 는 그 상태의 환불 신청이 있는 구매만({@code PENDING} = 「환불 신청 대기」). {@code page} 0 이상, {@code size} 1~{@value #SIZE_MAX}(서비스가 검사 — {@code fields.page|size}).
 */
public record PointShopPurchaseQuery(Instant from, Instant to, PointShopPurchaseStatus status, PointShopItemKind kind, String q, int page, int size,
                                     PointShopRefundRequestStatus refundRequest) {

    public static final int SIZE_MAX = 100;
}
