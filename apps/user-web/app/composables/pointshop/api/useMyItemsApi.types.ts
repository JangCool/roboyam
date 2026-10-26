/**
 * 내 아이템 API 가 주고받는 모양 — **서버 연결됨**. 계약: `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」 API 후보.
 * 한 줄 = `user_items` 한 줄(소모형도 장마다 한 줄 — 화면이 품목별 한 카드로 묶는다).
 */
import type { PointShopItemKind, PointShopUsageType, ProfileBorderStyle } from '~/composables/pointshop/api/usePointShopApi.types';

/** 보유 상태 — 가짐 · 씀(소모형) · 회수(환불·예외 환불). 만료는 저장하지 않고 `expired`(서버가 `expiresAt` 으로 계산 — 결정 6) */
export type UserItemStatus = 'OWNED' | 'USED' | 'REVOKED';

/** 정지 중 환불 신청의 상태 — 운영자 확인 기다림 · 수락(환불됨) · 거절(아이템은 다시 쓸 수 있다) */
export type RefundRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 그 구매의 최근 환불 신청(정지 중 환불 — 계약 「1-2 정지 중 환불 신청」) */
export interface MyItemRefundRequest {
    id: string;
    status: RefundRequestStatus;
    requestedAt: string;
    /** 수락·거절한 시각 — 대기 중이면 null */
    decidedAt: string | null;
    /** 거절 사유(회원에게 공개) — 거절이 아니면 null */
    rejectReason: string | null;
}

/** 장착 자리 — 닉네임 색 · 프로필 테두리(자리마다 하나) */
export type EquipSlot = 'NICKNAME_COLOR' | 'PROFILE_BORDER';

/** `GET /api/me/items` 한 줄 */
export interface MyItemView {
    id: string;
    itemId: string;
    purchaseId: string;
    kind: PointShopItemKind | string;
    usageType: PointShopUsageType;
    /** 품목의 지금 이름(가격·이름 사본은 구매 기록에 있다 — 화면은 지금 이름을 보인다) */
    name: string;
    nameEn: string | null;
    /** 표시값은 품목의 지금 값(운영자가 색을 고치면 산 사람의 색도 바뀐다) */
    color: string | null;
    borderStyle: ProfileBorderStyle | string | null;
    /** 프로필 아바타만 — 카탈로그 ID(그림은 `/avatars/roboyam/<id>.svg`) */
    avatarId: string | null;
    /** 프로필 아바타만 — 그림 주소(`<img>` 로만) */
    avatarUrl: string | null;
    /** 프로필 아바타만 — 지금 내 프로필 아바타로 쓰는 중(`profiles.profile_image`). 아바타는 장착 칸을 쓰지 않아 `equipped` 는 늘 false — 계약 후보에 더한 칸 */
    inUse: boolean;
    status: UserItemStatus;
    /** 기간제의 기간이 끝났다(서버 계산) */
    expired: boolean;
    /** 지금 장착 중 */
    equipped: boolean;
    startsAt: string | null;
    /** 기간제만 */
    expiresAt: string | null;
    /** 처음 장착·사용한 시각 — 있으면 환불할 수 없다(지우지 않는다) */
    firstUsedAt: string | null;
    purchasedAt: string;
    /** 산 때 낸 포인트(구매 사본) */
    pricePaid: number;
    /** 지금 셀프 환불(정지 중이면 환불 신청)할 수 있다(서버 판정 — 안 썼고 `refundDays` 안, 대기 중이거나 정지 중 거절된 적 있으면 false) */
    refundable: boolean;
    /** 환불 기한(구매 + `refundDays`). 회수된 줄은 null */
    refundUntil: string | null;
    /** 그 구매의 최근 환불 신청 — 신청한 적 없으면 null. `PENDING` 이면 장착·아바타 고르기·환불이 막힌다(`409 POINT_SHOP_REFUND_PENDING`) */
    refundRequest: MyItemRefundRequest | null;
}

/** `GET /api/me/items` */
export interface MyItemList {
    refundDays: number;
    /** 지금 정지 중이라 환불이 운영자 수락을 거친다 — 화면은 [환불] 대신 [환불 신청] */
    refundNeedsApproval: boolean;
    items: MyItemView[];
}

/** 장착한 닉네임 색 */
export interface EquippedNicknameColor {
    userItemId: string;
    itemId: string;
    color: string;
    expiresAt: string | null;
}

/** 장착한 프로필 테두리 */
export interface EquippedProfileBorder {
    userItemId: string;
    itemId: string;
    borderStyle: ProfileBorderStyle | string;
    expiresAt: string | null;
}

/**
 * `PUT /api/me/equipped/{slot}` · `DELETE /api/me/equipped/{slot}` 응답 — 두 자리의 지금 장착(만료된 것은 null).
 * 오류: `409 USER_ITEM_EXPIRED`(기간 끝) · `USER_ITEM_NOT_EQUIPPABLE`(소모형·회수·자리와 종류가 다름) · `404 USER_ITEM_NOT_FOUND`.
 */
export interface MyEquipped {
    nicknameColor: EquippedNicknameColor | null;
    profileBorder: EquippedProfileBorder | null;
}

/** `PUT /api/me/equipped/{slot}` 요청 */
export interface EquipRequest {
    userItemId: string;
}

/**
 * `POST /api/me/items/{id}/refund` 응답(헤더 `Idempotency-Key`) — 원래 묶음으로 사용 취소(`POINT_SHOP_REFUND`)·재고 복원·구매 REFUNDED.
 * 정지 중이면 `202` `MyItemRefundRequested`(환불 신청). 오류: `409 POINT_SHOP_REFUND_PENDING`(대기 중)·`POINT_SHOP_REFUND_REJECTED`(정지 중 거절된 구매 재신청) · `409 POINT_SHOP_REFUND_NOT_ALLOWED`(썼거나 기한 지남 — 화면은 목록을 다시 읽는다) · `POINT_SHOP_ALREADY_REFUNDED` · `429 REQUEST_RATE_LIMITED`(`REFUND_REQUEST`). 머리글 `Idempotency-Key` 필수(멱등 키 오류는 구매와 같다).
 */
export interface MyItemRefundResult {
    refundedPoints: number;
    balance: number;
}

/** `POST /api/me/items/{id}/refund` 의 정지 중 응답(`202`) — 환불 신청이 만들어졌다(같은 키 재요청도 202). 운영자가 수락하면 환불되고 회원 알림이 온다 */
export interface MyItemRefundRequested {
    requestId: string;
    status: 'PENDING';
    requestedAt: string;
}

/** 환불 요청의 응답 — 바로 환불(200) 또는 환불 신청(202). `UtilPointShop.isRefundRequested` 로 가른다(`requestId` 유무) */
export type MyItemRefundOutcome = MyItemRefundResult | MyItemRefundRequested;
