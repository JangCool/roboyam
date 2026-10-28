import type {
    GroupBuyAddressInput,
    GroupBuyCloseReason,
    GroupBuyDelayNotice,
    GroupBuyFulfillment,
    GroupBuyPaymentStart,
    GroupBuySkuKind,
    GroupBuyStatus,
} from '~/composables/groupbuy/api/useGroupBuyApi.types';

/**
 * 내 주문(공구) API 의 요청·응답 모양 — 목록·상세·결제 다시 열기·배송지·본인 취소·취소/반품 요청·지연 취소·공급 불가 선택. 계약 `commerce-groupbuy-contract.md`(2b-1 목업 제안, 서버는 2b-2·2b-3).
 * **환불 예정 금액·회수 방식별 결과·단추 상태는 서버 값**(`GroupBuyRefundCalculator` 한 곳 — 결정 72)이다. 화면은 보여 주기만 한다.
 */

/** commerce.md 10-3 */
export type GroupBuyOrderStatus = 'PENDING_PAYMENT' | 'JOINED' | 'CONFIRMED' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED' | 'REFUNDED' | 'EXPIRED';
export type GroupBuyRequestType = 'CANCEL' | 'RETURN';
/**
 * 요청 상태 — 계약의 다섯 + `SUPERSEDED`(결정 69 — 지연 취소·공급 불가 등으로 자동 종료) + `AWAITING_PAYMENT`(결정 68 — 반품비 부족분을 결제해야 접수, 이름은 목업 가정).
 */
export type GroupBuyRequestStatus = 'AWAITING_PAYMENT' | 'REQUESTED' | 'APPROVED' | 'RECEIVED' | 'COMPLETED' | 'REJECTED' | 'WITHDRAWN' | 'SUPERSEDED' | 'EXPIRED';
export type GroupBuyRequestReason = 'CHANGE_OF_MIND' | 'DEFECT' | 'WRONG_ITEM' | 'DAMAGED' | 'OTHER';
export type GroupBuyCollectionMethod = 'COMPANY_PICKUP' | 'CUSTOMER_SEND';
/** 결정 54 — 하자·오배송만 교환 */
export type GroupBuyResolution = 'REFUND' | 'EXCHANGE';
/** 결정 55 — 반품 거절 사유 코드 */
export type GroupBuyRejectReason = 'CUSTOMER_DAMAGE' | 'USED_VALUE_LOSS' | 'TIME_VALUE_LOSS' | 'OUT_OF_WINDOW' | 'ALREADY_SHIPPED';
/** 결정 16 — 환불 진행 표시(결제 취소 상태: PENDING·SENDING → 진행 중, SUCCEEDED → 완료, GAVE_UP → 지연, MANUAL → 운영자 처리) */
export type GroupBuyRefundProgress = 'IN_PROGRESS' | 'COMPLETED' | 'DELAYED' | 'MANUAL';
/** 결정 36 — 환불 원인 */
export type GroupBuyRefundCause = 'REQUEST' | 'USER_CANCEL' | 'FAILED' | 'CANCELED' | 'ADMIN' | 'SUPPLY_FAILURE' | 'DELAY' | 'RETURN_FEE_REFUND';
/** 결정 53 */
export type GroupBuySupplyChoiceValue = 'KEEP_REST' | 'CANCEL_ALL';

/** `GET /api/me/orders?page=` 의 한 줄 */
export interface MyGroupBuyOrderSummary {
    orderNo: string;
    groupBuyId: string;
    title: string;
    thumbnailUrl: string | null;
    status: GroupBuyOrderStatus;
    /** 「블랙 / M 외 1건」의 첫 줄 단품 이름(줄이 하나뿐이고 축 없는 본 상품이면 null — 상품 이름과 같아서) */
    firstItemName: string | null;
    itemCount: number;
    amount: number;
    refundedAmount: number;
    createdAt: string;
    /** 회원이 할 일·알 일이 있는 표시 — 결제 대기 · 공급 불가 답 대기 · 발송 지연 공지 · 반품비 결제 대기 */
    attention: 'PAY' | 'SUPPLY_CHOICE' | 'DELAY' | 'RETURN_FEE' | null;
}

export interface MyGroupBuyOrderPage {
    items: MyGroupBuyOrderSummary[];
    page: number;
    size: number;
    total: number;
}

/** 주문 줄 — 단품 하나에 한 줄(결정 88). 내 주문 응답 칸 이름은 목업 가정(2b-2 가 아직 알려 주지 않음) */
export interface MyGroupBuyOrderItem {
    id: string;
    skuId: string;
    /** 단품 이름(주문 때 사본 — 「블랙 / M」, 축 없으면 상품 이름) */
    skuName: string;
    /** 추가 구성상품이면 ADDON(「추가 구성」 딱지 — 본 상품 줄을 모두 취소·반품하면 함께 묻는다) */
    kind: GroupBuySkuKind;
    qty: number;
    unitPrice: number;
    itemAmount: number;
    canceledQty: number;
    returnedQty: number;
    /** 열린 요청에 들어가 있지 않고 지금 취소·반품을 고를 수 있는 수량(서버) */
    selectableQty: number;
}

/** 배송지 — 기본은 가린 값(`홍*동`·`010-****-5678`·주소 앞 두 어절), 「전체 보기」는 따로 받는다 */
export interface MyGroupBuyAddress {
    recipient: string;
    phone: string;
    postcode: string;
    address1: string;
    address2: string;
    memo: string;
    /** true 면 가린 값 */
    masked: boolean;
    /** 보관 기간이 지나 지웠다(10-9) — 이때 다른 칸은 빈 글자 */
    purged: boolean;
}

export interface MyGroupBuyShipment {
    carrierName: string;
    trackingNo: string;
    /** 택배사 조회 주소(템플릿에 송장 번호를 넣은 것) — 없으면 null */
    trackingUrl: string | null;
    kind: 'ORIGINAL' | 'EXCHANGE';
    shippedAt: string;
    deliveredAt: string | null;
}

export interface MyGroupBuyRequestItem {
    skuId: string;
    /** 주문 줄의 단품 이름 */
    skuName: string;
    kind: GroupBuySkuKind;
    qty: number;
    reason: GroupBuyRequestReason | null;
}

export interface MyGroupBuyRequest {
    id: string;
    type: GroupBuyRequestType;
    status: GroupBuyRequestStatus;
    items: MyGroupBuyRequestItem[];
    collectionMethod: GroupBuyCollectionMethod | null;
    resolution: GroupBuyResolution | null;
    /** 요청 때 보인 환불 예정 금액(서버 계산 — `preview_amount`) */
    previewAmount: number | null;
    /** 확정 환불액(완료 뒤) */
    refundAmount: number | null;
    /** 반품비 부족분(결정 68) — 결제해야 접수 */
    returnFeeDue: number | null;
    rejectReasonCode: GroupBuyRejectReason | null;
    /** 업체회수 송장(승인 뒤) */
    pickupTrackingNo: string | null;
    createdAt: string;
    handledAt: string | null;
    /** 서버가 정한 단추 — 철회 · 부족분 결제(다시 열기) */
    actions: { withdraw: boolean; payReturnFee: boolean };
}

export interface MyGroupBuyRefund {
    id: string;
    cause: GroupBuyRefundCause;
    amount: number;
    progress: GroupBuyRefundProgress;
    /** 계좌 송금으로 돌려주는 경우(결정 67) */
    method: 'PG_CANCEL' | 'BANK_TRANSFER' | 'NONE';
    requestedAt: string;
    completedAt: string | null;
}

/** 단품 공급 불가 선택(결정 53 → 88) */
export interface MyGroupBuySupplyChoice {
    /** 공급할 수 없게 된 단품 이름 */
    skuName: string;
    reason: string;
    /** 답 기한 — 지나면 나머지만 받기 */
    deadline: string;
    /** 고른 답 — 아직이면 null */
    choice: GroupBuySupplyChoiceValue | null;
    /** 기한이 지나 기본값(나머지만 받기)으로 정해졌나 */
    defaulted: boolean;
    /** 「전체 취소」를 고르면 돌려받을 금액(서버) */
    cancelAllRefund: number;
}

/** 서버가 정한 주문 단추 */
export interface MyGroupBuyOrderActions {
    payAgain: boolean;
    revealAddress: boolean;
    editAddress: boolean;
    /** 본인 즉시 취소(승인 없음) */
    cancelNow: boolean;
    /** 취소 요청(운영자 승인) */
    requestCancel: boolean;
    requestReturn: boolean;
    /** 발송 지연 공지 뒤 즉시 전액 취소(결정 52) */
    delayCancel: boolean;
    /** 공급 불가 「나머지만 받기 / 전체 취소」 고르기 */
    supplyChoice: boolean;
}

/** `GET /api/me/orders/{orderNo}` */
export interface MyGroupBuyOrderDetail {
    orderNo: string;
    status: GroupBuyOrderStatus;
    groupBuy: {
        id: string;
        title: string;
        thumbnailUrl: string | null;
        status: GroupBuyStatus;
        fulfillment: GroupBuyFulfillment;
        closeReason: GroupBuyCloseReason | null;
        roundNo: number;
    };
    items: MyGroupBuyOrderItem[];
    itemsAmount: number;
    shippingFee: number;
    amount: number;
    /** 상품·배송비 환불 누계 */
    refundedAmount: number;
    /** 고객 택배비 보상 누계 */
    compensationAmount: number;
    createdAt: string;
    paidAt: string | null;
    /** 결제 대기면 선점 끝 */
    reservedUntil: string | null;
    shipPlannedAt: string | null;
    shipWithinBusinessDays: number | null;
    address: MyGroupBuyAddress;
    /** 배송지 수정 기한 안내용(마감 후 발주는 발주 전, 즉시 발송은 준비중 전 — 결정 30) */
    addressEditableUntilText: string | null;
    shipment: MyGroupBuyShipment | null;
    delayNotice: (GroupBuyDelayNotice & { refundAmount: number }) | null;
    supplyChoice: MyGroupBuySupplyChoice | null;
    requests: MyGroupBuyRequest[];
    refunds: MyGroupBuyRefund[];
    /** 반품 신청 기한(단순 변심 — 배송완료 + 7일) */
    returnUntil: string | null;
    /** 하자·오배송 신청 기한(배송완료 + 3개월) */
    defectReturnUntil: string | null;
    actions: MyGroupBuyOrderActions;
}

/* ── 요청 본문 ── */

/** 줄·수량 고르기 */
export interface GroupBuyItemQty {
    skuId: string;
    qty: number;
}

/**
 * `POST /api/me/orders/{orderNo}/cancel/preview` 와 `…/cancel`(본인 즉시 취소)의 본문(서버 2b-2 — 단품 단위 `lines`).
 * 본 상품을 모두 취소하면서 추가 구성상품을 남기면 409 `GROUP_BUY_ADDON_ONLY {skuIds}` — 화면이 「추가 구성상품도 함께 취소할까요?」로 묻고 함께 다시 보낸다.
 */
export interface GroupBuyCancelRequest {
    lines: { skuId: string; qty: number }[];
}

/** 취소 환불 미리 보기(결정 42 — 일부 줄 취소로 무료배송 기준 아래면 배송비 재계산) */
export interface GroupBuyCancelPreview {
    refundAmount: number;
    itemRefund: number;
    shippingRefund: number;
    /** 다시 받는 배송비(빼는 금액, 0 이상) */
    reshippingFee: number;
}

export interface GroupBuyReturnLine extends GroupBuyItemQty {
    reason: GroupBuyRequestReason;
}

/** `POST /api/me/orders/{orderNo}/requests/preview` — 반품 회수 방식별 결과 */
export interface GroupBuyReturnPreviewRequest {
    items: GroupBuyReturnLine[];
}

/** 회수 방식 하나의 환불 결과(서버 계산) */
export interface GroupBuyReturnOutcome {
    /** 환불 예정 금액 — 음수면 부족분(결정 68) */
    refundAmount: number;
    itemRefund: number;
    shippingRefund: number;
    /** 뺄 반품 배송비(0 이상) */
    returnFee: number;
    /** 다시 받는 배송비(0 이상) */
    reshippingFee: number;
    /** 고객이 낸 택배비 보상 예정(업체 부담 + 직접 발송) — 금액은 운영자가 확인 뒤 정한다 */
    compensationEligible: boolean;
}

export interface GroupBuyReturnPreview {
    /** 반품 비용을 누가 내나 — 하자·오배송 줄이 하나라도 있으면 업체(결정 44) */
    feeBearer: 'CUSTOMER' | 'COMPANY';
    byMethod: Record<GroupBuyCollectionMethod, GroupBuyReturnOutcome>;
    /** 반품 보낼 곳(직접 발송 안내) */
    returnAddress: string;
}

/** `POST /api/me/orders/{orderNo}/requests` */
export interface GroupBuyRequestCreate {
    type: GroupBuyRequestType;
    /** 취소면 사유 없음(`reason: null`) */
    items: (GroupBuyItemQty & { reason: GroupBuyRequestReason | null })[];
    collectionMethod: GroupBuyCollectionMethod | null;
    resolution: GroupBuyResolution | null;
    /** 기타·하자 설명(≤ 500자) */
    reasonText: string;
    /** 먼저 올린 사진 번호(하자·오배송, 최대 3) */
    photoIds: string[];
    /** 화면이 본 환불 예정 금액(비교용 — 다르면 409 `GROUP_BUY_AMOUNT_CHANGED`) */
    expectedAmount: number | null;
}

/** 요청 응답 — 부족분이 있으면 그 결제창 대상(결제해야 접수) */
export interface GroupBuyRequestCreated {
    request: MyGroupBuyRequest;
    returnFeePayment: GroupBuyPaymentStart | null;
}

/** `POST /api/me/orders/{orderNo}/photos`(multipart `file`) */
export interface GroupBuyRequestPhoto {
    id: string;
    url: string;
}

/** `PUT /api/me/orders/{orderNo}/address` */
export type GroupBuyAddressUpdate = GroupBuyAddressInput;

/** `POST /api/me/orders/{orderNo}/supply-choice` */
export interface GroupBuySupplyChoiceRequest {
    choice: GroupBuySupplyChoiceValue;
}
