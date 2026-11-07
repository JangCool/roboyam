package com.community.pointshop.dto.response;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.UserItemStatus;
import java.time.Instant;

/**
 * {@code GET /api/me/items} 한 줄 = {@code user_items} 한 줄(user-web {@code MyItemView}). 이름·색·테두리는 품목의 <b>지금</b> 값, {@code pricePaid} 는 산 때 사본.
 *
 * @param avatarStillUrl 아바타만 — 움직이는 아바타의 정지 그림(정지 아바타·그 밖은 null — commerce-motion-avatar-contract)
 * @param animated    아바타만 — 움직이는 아바타
 * @param inUse       아바타만 — 지금 내 프로필 아바타로 쓰는 중(아바타는 장착 칸을 쓰지 않아 {@code equipped} 는 늘 false)
 * @param reserved    올리기 변경권만 — 대기 신청에 예약된 장(쓸 수 없고 환불할 수 없다, 반려·취소되면 풀린다)
 * @param expired     기간제의 기간이 끝났다(서버 계산 — 저장하지 않는다)
 * @param equipped    지금 장착 중(만료된 장착은 false)
 * @param refundable  지금 셀프 환불할 수 있다(안 썼고 지금 설정의 기한 안)
 * @param refundUntil 환불 기한(산 시각 + 지금 설정 일수). 회수된 줄은 null
 * @param refundRequest 이 구매의 가장 최근 환불 신청(정지 중 셀프 환불 — 없으면 null). 대기({@code PENDING})면 장착·고르기·환불이 막히고 {@code refundable} 은 false
 * @param source      출처 — {@code PURCHASE}(산 줄) · {@code REWARD_EVENT}(보상 이벤트로 받은 줄 — V163: purchaseId·refundUntil null, pricePaid 0, purchasedAt = 받은 시각, 환불 불가)
 */
public record MyItemView(String id, String itemId, String purchaseId, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String color,
                         String borderStyle, String avatarId, String avatarUrl, String avatarStillUrl, boolean animated, boolean inUse, UserItemStatus status,
                         boolean reserved, boolean expired, boolean equipped,
                         Instant startsAt, Instant expiresAt, Instant firstUsedAt, Instant purchasedAt, int pricePaid, boolean refundable, Instant refundUntil,
                         PointShopRefundRequestView refundRequest, String source) {

    public static final String SOURCE_PURCHASE = "PURCHASE";
    public static final String SOURCE_REWARD_EVENT = "REWARD_EVENT";
}
