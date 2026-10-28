/**
 * 공구 취소·반품 요청 관리자 API(`/api/admin/group-buy-requests…`)가 주고받는 모양 — 계약 `commerce-groupbuy-contract.md`(결정 15·44·54·55·68·72, commerce.md 10-8).
 * 요청 = `group_buy_requests`, 요청 줄 = `group_buy_request_items`(줄마다 사유·수량·환불액), 처리 방법 = `group_buy_return_dispositions`.
 * 환불액·미리 보기는 `GroupBuyRefundCalculator`(서버 한 곳)의 값이다.
 */

import type { GroupBuyFulfillment, GroupBuyMember, GroupBuyPage, GroupBuySkuKind, GroupBuyStatus } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

export type GroupBuyRequestType = 'CANCEL' | 'RETURN';
export type GroupBuyRequestStatus = 'REQUESTED' | 'APPROVED' | 'COMPLETED' | 'REJECTED' | 'WITHDRAWN' | 'SUPERSEDED';
export const GROUP_BUY_REQUEST_STATUSES: readonly GroupBuyRequestStatus[] = ['REQUESTED', 'APPROVED', 'COMPLETED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED'];

/** 줄 사유 — 단순 변심 · 하자 · 오배송 · 기타 */
export type GroupBuyReasonCode = 'CHANGE_OF_MIND' | 'DEFECT' | 'WRONG_ITEM' | 'OTHER';
/** 회수 방식 — 업체회수 / 고객발송 */
export type GroupBuyCollectionMethod = 'COMPANY_PICKUP' | 'CUSTOMER_SEND';
/** 하자·오배송 반품의 회원 선택 — 환불 / 교환(결정 54) */
export type GroupBuyResolution = 'REFUND' | 'EXCHANGE';
/** 반품 거절 사유 코드(결정 55 — 필수) */
export type GroupBuyRejectReasonCode = 'CUSTOMER_DAMAGE' | 'USED_VALUE_LOSS' | 'TIME_VALUE_LOSS' | 'OUT_OF_WINDOW' | 'ALREADY_SHIPPED';
export const GROUP_BUY_REJECT_REASONS: readonly GroupBuyRejectReasonCode[] = ['CUSTOMER_DAMAGE', 'USED_VALUE_LOSS', 'TIME_VALUE_LOSS', 'OUT_OF_WINDOW', 'ALREADY_SHIPPED'];
/** 반품 상품 처리 방법(결정 15) — 다시 판매 · 폐기 · 공급처 반송 · 보관 */
export type GroupBuyDisposition = 'RESELL' | 'DISCARD' | 'RETURN_TO_SUPPLIER' | 'KEEP';
export const GROUP_BUY_DISPOSITIONS: readonly GroupBuyDisposition[] = ['RESELL', 'DISCARD', 'RETURN_TO_SUPPLIER', 'KEEP'];
/** 처리 기한 표시 — 2영업일 경고 · 3영업일 심각(commerce.md 10-8, P35) */
export type GroupBuyDeadlineLevel = 'OK' | 'WARN' | 'CRITICAL';
/** 반품비 부족분 추가 결제(결정 68) 상태 */
export type GroupBuyExtraPaymentStatus = 'READY' | 'PAID' | 'EXPIRED' | 'PARTIAL_REFUNDED' | 'REFUNDED';

/** `GET /api/admin/group-buy-requests?status=&type=&level=&q=&page=&size=` */
export interface GroupBuyRequestQuery {
    status?: GroupBuyRequestStatus;
    type?: GroupBuyRequestType;
    /** 기한 경고만(WARN 이상) */
    level?: 'WARN';
    /** 주문 번호 · 닉네임 · 회원 번호 */
    q?: string;
    page: number;
    size: number;
}

export interface GroupBuyExtraPayment {
    status: GroupBuyExtraPaymentStatus;
    amount: number;
    paymentId: string;
    paidAt: string | null;
    /** 남는 몫을 돌려준 금액(철회·거절·재계산 — 원인 `RETURN_FEE_REFUND`) */
    refundedAmount: number;
}

export interface GroupBuyRequestRow {
    id: string;
    type: GroupBuyRequestType;
    status: GroupBuyRequestStatus;
    resolution: GroupBuyResolution;
    order: { id: string; orderNo: string };
    groupBuy: { id: string; title: string; roundNo: number; status: GroupBuyStatus; fulfillment: GroupBuyFulfillment };
    member: GroupBuyMember;
    lineCount: number;
    totalQty: number;
    /** 대표 사유(줄 사유가 섞이면 하자·오배송이 앞) */
    mainReason: GroupBuyReasonCode;
    collectionMethod: GroupBuyCollectionMethod | null;
    /** 요청 때 회원이 본 환불 예정 금액(`preview_amount`) */
    previewAmount: number;
    createdAt: string;
    /** 반환받은 날(반품 — 3영업일 기한 시작) */
    receivedAt: string | null;
    /** 기한 시작점(취소 = 요청한 날, 반품 = 반환받은 날 · 아직 안 받았으면 요청한 날)부터 지난 영업일 */
    elapsedBusinessDays: number;
    deadlineLevel: GroupBuyDeadlineLevel;
    extraPayment: GroupBuyExtraPayment | null;
}

export interface GroupBuyRequestListResponse extends GroupBuyPage<GroupBuyRequestRow> {
    counts: Record<GroupBuyRequestStatus, number>;
}

/** 요청 줄 */
export interface GroupBuyRequestItem {
    id: string;
    /** 주문 줄 */
    orderItemId: string;
    /** 주문 줄의 단품 이름 사본(축 없는 단품 하나는 빈 글자) · 종류 */
    skuName: string;
    kind: GroupBuySkuKind;
    qty: number;
    reasonCode: GroupBuyReasonCode;
    reasonText: string;
    /** 줄별 환불액(서버 계산) */
    refundAmount: number;
    /** 처리 방법(완료 뒤) */
    dispositions: { disposition: GroupBuyDisposition; qty: number }[];
}

/** 환불 계산 나눔(서버 계산 — 화면은 보여 주기만) */
export interface GroupBuyRefundBreakdown {
    itemRefund: number;
    shippingRefund: number;
    returnFee: number;
    reshippingFee: number;
    reshippingRefund: number;
    compensation: number;
    total: number;
    /** 반품비를 운영자가 부담(하자·오배송 줄이 있음 — 결정 44) */
    operatorPaysReturnFee: boolean;
}

/** 이 요청에 지금 할 수 있는 일(서버가 정한다) */
export interface GroupBuyRequestActions {
    canApprove: boolean;
    canReject: boolean;
    /** 업체회수 송장 입력(APPROVED, 업체회수) */
    canSetPickup: boolean;
    /** 회수 확인 — 반환받은 날(APPROVED, 반품) */
    canReceive: boolean;
    /** 반품 완료 — 처리 방법·보상액(반환받은 뒤) */
    canComplete: boolean;
    /** 교환 재발송(교환, 반환받은 뒤) */
    canExchangeShip: boolean;
    /** 교환할 물건이 없음 → 환불로 바꾸기 */
    canConvertToRefund: boolean;
    /** 다시 판매를 고를 수 있음 — 공구 RECRUITING(줄 사유가 단순 변심일 때만 줄에 보인다, 결정 15) */
    resellAllowed: boolean;
}

/** `GET /api/admin/group-buy-requests/{id}` */
export interface GroupBuyRequestDetail extends GroupBuyRequestRow {
    items: GroupBuyRequestItem[];
    photos: { id: string; url: string }[];
    pickup: { carrierCode: string; trackingNo: string } | null;
    /** 계산 나눔(요청 때 값) */
    breakdown: GroupBuyRefundBreakdown;
    /** 지금 다시 계산한 값 — `preview_amount` 와 다르면 두 값과 사유를 보이고 운영자가 확인(결정 72) */
    recalculated: { breakdown: GroupBuyRefundBreakdown; reasons: string[] } | null;
    /** 고객발송 하자·오배송의 고객 택배비 보상액과 상한(설정값, 기본 10,000) */
    compensationAmount: number;
    compensationCap: number;
    /** 이 공구에 청약철회 제한 문구가 있는가(없으면 `USED_VALUE_LOSS` 거절 경고 — 결정 55) */
    withdrawalRestricted: boolean;
    rejectReasonCode: GroupBuyRejectReasonCode | null;
    rejectReason: string | null;
    rejectPhotos: { id: string; url: string }[];
    exchangeShipment: { carrierCode: string; trackingNo: string; shippedAt: string } | null;
    /** 운영자가 금액을 정한 환불의 누계·문턱(보상액 — 결정 70) */
    adminRefundTotal: number;
    largeRefundThreshold: number;
    handledBy: string | null;
    handledAt: string | null;
    history: { status: GroupBuyRequestStatus; actor: string; note: string | null; at: string }[];
    actions: GroupBuyRequestActions;
}

/** `POST …/{id}/approve` — 화면이 본 금액(다시 계산한 값)을 비교용으로 싣는다. 그 사이 또 바뀌었으면 `409 GROUP_BUY_REFUND_AMOUNT_CHANGED` */
export interface GroupBuyApproveRequest {
    expectedAmount: number;
}

/** `POST …/{id}/reject` — 사유 코드 필수 + 근거 사진 최대 3장(결정 55) */
export interface GroupBuyRejectRequest {
    reasonCode: GroupBuyRejectReasonCode;
    reason: string;
    photoIds: string[];
}

/** `POST …/{id}/pickup` */
export interface GroupBuyPickupRequest {
    carrierCode: string;
    trackingNo: string;
}

/** `POST …/{id}/receive` — 반환받은 날 `YYYY-MM-DD` */
export interface GroupBuyReceiveRequest {
    receivedAt: string;
}

/** `POST …/{id}/complete` — 줄마다 처리 방법과 수량(합 = 그 줄 반품 수량), 보상액(고객발송 하자·오배송) */
export interface GroupBuyCompleteRequest {
    dispositions: { requestItemId: string; disposition: GroupBuyDisposition; qty: number }[];
    compensationAmount: number;
    expectedAmount: number;
}

/** `POST …/{id}/exchange-ship` */
export interface GroupBuyExchangeShipRequest {
    carrierCode: string;
    trackingNo: string;
}

/** `POST /api/admin/group-buy-requests/photos` — 거절 근거 사진 */
export interface GroupBuyEvidencePhoto {
    id: string;
    url: string;
}
