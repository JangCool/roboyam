package com.community.pointshop.dto.response;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopUsageType;
import java.time.Instant;

/**
 * 관리자 구매 내역 한 줄 — 품목 칸은 <b>산 때의 사본</b>(admin-web {@code PointShopPurchaseRow}).
 *
 * @param firstUsedAt 처음 장착·사용·프로필로 고른 시각(없으면 아직 안 씀)
 * @param selfRefund  완료 줄만(환불된 줄은 null)
 * @param refund      환불된 줄만(완료 줄은 null)
 * @param refundRequest 이 구매의 가장 최근 환불 신청(정지 중 셀프 환불 — 없으면 null). 대기면 [수락]·[거절]
 */
public record PointShopPurchaseRowView(String id, Instant createdAt, PointShopPurchaseMemberView member, String itemId, String itemCode, String itemName, String avatarId,
                                       PointShopItemKind kind, PointShopUsageType usageType, int pricePoint, Integer durationDays, PointShopPurchaseStatus status,
                                       Instant firstUsedAt, PointShopSelfRefundView selfRefund, PointShopPurchaseRefundView refund,
                                       PointShopRefundRequestView refundRequest) {
}
