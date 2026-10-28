/**
 * 공구 주문 관리자 API(`/api/admin/group-buy-orders…`)가 주고받는 모양 — 계약 `commerce-groupbuy-contract.md`(결정 13·16·23·29·30·32·46·47·53·70·71).
 * 주문 = `group_buy_participations`, 주문 줄 = `group_buy_participation_items`. 금액은 모두 서버 계산 값이다.
 */

import type { GroupBuyFulfillment, GroupBuyMember, GroupBuyPage, GroupBuySkuKind, GroupBuyStatus } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

/** 주문 상태(commerce.md 10-3) */
export type GroupBuyOrderStatus = 'PENDING_PAYMENT' | 'JOINED' | 'CONFIRMED' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED' | 'REFUNDED' | 'EXPIRED';
export const GROUP_BUY_ORDER_STATUSES: readonly GroupBuyOrderStatus[] = ['PENDING_PAYMENT', 'JOINED', 'CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELED', 'REFUNDED', 'EXPIRED'];

/** 화면이 따로 거르는 운영 조건(대시보드 링크) — 발송 예정일·기한 지난 발송 전 주문 / 발송 뒤 N일 지난 SHIPPED */
export type GroupBuyOrderAlert = 'SHIP_OVERDUE' | 'DELIVERY_STALE';

/** `GET /api/admin/group-buy-orders?status=&groupBuyId=&alert=&q=&from=&to=&page=&size=` */
export interface GroupBuyOrderQuery {
    status?: GroupBuyOrderStatus;
    groupBuyId?: string;
    alert?: GroupBuyOrderAlert;
    /** 주문 번호(`G2026102800012`) · 닉네임 · 회원 번호 — 배송지는 암호문이라 찾지 못한다 */
    q?: string;
    from?: string;
    to?: string;
    page: number;
    size: number;
}

export interface GroupBuyOrderRow {
    id: string;
    orderNo: string;
    groupBuy: { id: string; title: string; roundNo: number; status: GroupBuyStatus; fulfillment: GroupBuyFulfillment };
    member: GroupBuyMember;
    /** 줄 요약(「블랙 × 2 외 1줄」) — 서버가 만든 글 */
    itemSummary: string;
    /** 남은 수량 합(Σ qty − canceled − returned) */
    totalQty: number;
    amount: number;
    refundedAmount: number;
    status: GroupBuyOrderStatus;
    /** 열린 요청(REQUESTED·APPROVED) — 있으면 발송 못 함(결정 47) */
    openRequest: { id: string; type: 'CANCEL' | 'RETURN' } | null;
    /** 공급 불가 회원 선택을 기다리는 중(기한 전에는 발송하지 않는다 — 결정 53) */
    supplyChoicePending: boolean;
    /** 발송 지연 공지로 회원이 즉시 취소할 수 있는 주문(결정 52) */
    delayCancelable: boolean;
    carrierCode: string | null;
    trackingNo: string | null;
    createdAt: string;
    paidAt: string | null;
    shippedAt: string | null;
    deliveredAt: string | null;
}

/** 목록 응답 — 상태 탭 건수(조건을 적용한 건수, 상태 칸 제외) */
export interface GroupBuyOrderListResponse extends GroupBuyPage<GroupBuyOrderRow> {
    counts: Record<GroupBuyOrderStatus, number>;
}

/**
 * 주문 줄 — 단품 하나(결정 88, 2b-2 확정 전 가정). 주문에는 MAIN 줄이 하나 이상 있다(추가 구성상품 단독 구매 불가 `GROUP_BUY_ADDON_ONLY`).
 * `skuName` 은 주문 때 단품 이름 사본(축 없는 단품 하나는 빈 글자 — 화면은 「단품 하나」).
 */
export interface GroupBuyOrderItem {
    id: string;
    skuId: string;
    skuName: string;
    kind: GroupBuySkuKind;
    qty: number;
    unitPrice: number;
    itemAmount: number;
    canceledQty: number;
    returnedQty: number;
    restockedQty: number;
}

/** 금액 — 남은 결제액 = cash − refunded − compensation(결정 38) */
export interface GroupBuyOrderAmounts {
    itemsAmount: number;
    shippingFee: number;
    amount: number;
    cashAmount: number;
    pointAmount: number;
    refundedAmount: number;
    compensationAmount: number;
    reshippingFeeCharged: number;
    remaining: number;
}

/** 가린 배송지(기본 표시) — 파기됐으면 `erasedAt` */
export interface GroupBuyMaskedAddress {
    recipient: string;
    phone: string;
    postcode: string;
    address: string;
    memo: string;
    erasedAt: string | null;
}

/** `POST /api/admin/group-buy-orders/{id}/address-reveal` 응답 — `expiresAt`(10분 — 설정값) 뒤 화면이 다시 가린다 */
export interface GroupBuyRevealedAddress {
    recipient: string;
    phone: string;
    postcode: string;
    address1: string;
    address2: string;
    memo: string;
    expiresAt: string;
}

export interface GroupBuyAddressView {
    operator: string;
    reason: string;
    viewedAt: string;
}

export interface GroupBuyStatusChange {
    from: GroupBuyOrderStatus | null;
    to: GroupBuyOrderStatus;
    actor: string;
    note: string | null;
    at: string;
}

/** 환불 원인(결정 36·53·52·68) */
export type GroupBuyRefundCause = 'REQUEST' | 'USER_CANCEL' | 'FAILED' | 'CANCELED' | 'ADMIN' | 'SUPPLY_FAILURE' | 'DELAY' | 'RETURN_FEE_REFUND';
/** 환불 방법(결정 67) */
export type GroupBuyRefundMethod = 'PG_CANCEL' | 'BANK_TRANSFER' | 'NONE';
/** 환불 진행 표시(결정 16) — 진행 중 · 완료 · 지연(GAVE_UP) · 운영자가 직접 처리(MANUAL) · 고액 승인 대기 */
export type GroupBuyRefundState = 'PENDING' | 'DONE' | 'DELAYED' | 'MANUAL' | 'AWAITING_APPROVAL';

/** 환불 기록 한 줄(`group_buy_refunds`) — 금액 나눔(결정 75) */
export interface GroupBuyRefundRecord {
    id: string;
    cause: GroupBuyRefundCause;
    method: GroupBuyRefundMethod;
    state: GroupBuyRefundState;
    itemRefund: number;
    shippingRefund: number;
    /** 뺀 반품비(양수로 적는다 — 화면이 −) */
    returnFee: number;
    reshippingFee: number;
    reshippingRefund: number;
    compensation: number;
    /** 이번에 돌려준 합 */
    total: number;
    /** 사업자 귀책(재부과·반품비 없음 — 결정 53) */
    businessFault: boolean;
    reason: string | null;
    operator: string | null;
    requestId: string | null;
    requestedAt: string;
    completedAt: string | null;
}

/** 배송 한 줄 — 원래 발송 · 교환 재발송(결정 54) */
export interface GroupBuyShipment {
    kind: 'ORIGINAL' | 'EXCHANGE';
    carrierCode: string;
    carrierName: string;
    trackingNo: string;
    trackingUrl: string | null;
    shippedAt: string;
    deliveredAt: string | null;
}

/** 이 주문에 지금 할 수 있는 일(서버가 정한다) */
export interface GroupBuyOrderActions {
    canPrepare: boolean;
    canShip: boolean;
    canFixTracking: boolean;
    canDeliver: boolean;
    canRevealAddress: boolean;
    /** 운영 환불 — 발송 전은 줄·수량(LINES), 배송완료는 금액만(AMOUNT), SHIPPED 는 막힘(결정 32) */
    refundMode: 'LINES' | 'AMOUNT' | null;
    refundBlockedReason: 'SHIPPED' | 'OPEN_REQUEST' | 'CLOSED' | 'PAYMENT_UNRECONCILED' | null;
}

/** `GET /api/admin/group-buy-orders/{id}` — 열면 서버가 `GROUPBUY_ORDER_VIEW`(개인정보 열람) 감사 */
export interface GroupBuyOrderDetail extends GroupBuyOrderRow {
    items: GroupBuyOrderItem[];
    amounts: GroupBuyOrderAmounts;
    payment: { paymentOrderId: string; paymentId: string; status: string; payMethod: string | null; escrow: boolean; paidAt: string | null } | null;
    address: GroupBuyMaskedAddress;
    addressViews: GroupBuyAddressView[];
    adminMemo: string;
    history: GroupBuyStatusChange[];
    refunds: GroupBuyRefundRecord[];
    shipments: GroupBuyShipment[];
    requests: { id: string; type: 'CANCEL' | 'RETURN'; status: string; createdAt: string }[];
    /** 공급 불가 회원 선택(있을 때) */
    supplyChoice: { choice: 'KEEP_REST' | 'CANCEL_ALL' | null; deadline: string; defaulted: boolean } | null;
    /** 발송 예정일·기한(성사형은 날짜, 즉시 발송은 결제 후 N영업일로 계산한 날짜) */
    shipDueAt: string | null;
    /** 운영자가 금액을 정한 환불의 누계(고액 문턱 판단 — 결정 70) · 문턱(설정값, 0 = 끔) */
    adminRefundTotal: number;
    largeRefundThreshold: number;
    actions: GroupBuyOrderActions;
}

/** 일괄 처리 한 줄의 결과(부분 성공) */
export interface GroupBuyBulkRowResult {
    orderId: string;
    orderNo: string;
    ok: boolean;
    /** 실패면 서버 코드·문구 그대로 */
    code: string | null;
    message: string | null;
}

export interface GroupBuyBulkResult {
    succeeded: number;
    failed: number;
    results: GroupBuyBulkRowResult[];
}

/** 발송 처리 한 줄(최대 200줄) */
export interface GroupBuyShipRow {
    orderId: string;
    carrierCode: string;
    /** 숫자·영문 6~30자로 서버가 정리한다 */
    trackingNo: string;
}

/** `POST /api/admin/group-buy-orders/{id}/tracking` — 송장 정정(사유 필수) */
export interface GroupBuyTrackingFixRequest {
    carrierCode: string;
    trackingNo: string;
    reason: string;
}

/** 운영 환불 줄(발송 전 주문) */
export interface GroupBuyRefundLine {
    itemId: string;
    qty: number;
}

/** `POST /api/admin/group-buy-orders/{id}/refund-preview` · `…/refunds` 요청 — LINES 면 `lines`, AMOUNT 면 `amount` */
export interface GroupBuyAdminRefundRequest {
    lines: GroupBuyRefundLine[] | null;
    amount: number | null;
    /** 사업자 귀책 — 재부과·반품비 없음(결정 53) */
    businessFault: boolean;
    reason: string;
}

/** 운영 환불 미리 보기(서버 계산) */
export interface GroupBuyRefundPreview {
    itemRefund: number;
    shippingRefund: number;
    reshippingFee: number;
    total: number;
    remainingAfter: number;
    /** 이 환불까지 더한 운영자 금액 누계 */
    adminRefundTotalAfter: number;
    largeRefundThreshold: number;
    /** 누계가 문턱 이상 — `commerce.refund.large` 필요(없으면 승인 대기) */
    needsLarge: boolean;
}

/** 운영 환불 결과 — 고액 권한이 없으면 승인 대기 */
export interface GroupBuyAdminRefundResult {
    state: 'QUEUED' | 'AWAITING_APPROVAL';
    order: GroupBuyOrderDetail;
}

/** `POST /api/admin/group-buy-orders/export` — 배송 명단(발송 전 주문만, 권한 `commerce.export` + `commerce.address.reveal`) */
export interface GroupBuyExportRequest {
    groupBuyId: string;
    reason: string;
}

/** 내보내기 결과 — 한 번 쓰는 내려받기 주소(서버가 CSV 를 만들고 감사에 건수·사유를 남긴다) */
export interface GroupBuyExportResult {
    fileName: string;
    rowCount: number;
    downloadUrl: string;
}

/** `GET /api/admin/group-buy-orders/carriers` — 발송·송장 정정에 고를 수 있는 켜진 택배사(설정 권한 없이 고르기용) */
export interface GroupBuyCarrierChoice {
    code: string;
    name: string;
}
