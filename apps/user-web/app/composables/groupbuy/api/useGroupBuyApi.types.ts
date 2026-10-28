import type { PaymentCheckout } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 공구(회원) API 의 요청·응답 모양 — 목록·상세·견적·주문·결제 다시 열기·알림 신청. 계약 `docs/features/commerce-groupbuy-contract.md`(2b-1 화면 목업이 제안하는 모양, 서버는 2b-2·2b-4).
 * 열·상태 이름은 계약 그대로(JSON 은 카멜 표기 — `min_qty` → `minQty`, `close_reason` → `closeReason`, `round_no` → `roundNo`). 금액은 원(KRW) 정수, 시각은 ISO 순간, 날짜는 `YYYY-MM-DD`(한국 날짜).
 * **금액·남은 수량·품절 판정·단추 상태는 서버가 계산한 값**이다 — 화면은 보여 주기만 한다.
 */

/** commerce.md 10-2 — 회원 화면에 오는 것은 DRAFT(공개 예약 — 모집 예정)·RECRUITING 이후 */
export type GroupBuyStatus = 'DRAFT' | 'RECRUITING' | 'CLOSING' | 'CLOSED_SUCCESS' | 'ORDERED' | 'SHIPPING' | 'COMPLETED' | 'CLOSED_FAILED' | 'CANCELED';
/** 목록 탭 — 서버가 상태·시각으로 정한다(예정 = 모집 시작 전 공개, 진행 = 모집 중·집계 중, 종료 = 그 밖) */
export type GroupBuyPhase = 'UPCOMING' | 'ONGOING' | 'ENDED';
/** 결정 79 — 다 팔림 · 기간 끝 · 운영 종료(중단) */
export type GroupBuyCloseReason = 'SOLD_OUT' | 'TIME_UP' | 'EARLY_CLOSED' | 'CANCELED';
export type GroupBuyFulfillment = 'IMMEDIATE' | 'AFTER_CLOSE';
export type GroupBuyExtensionMode = 'DEMAND' | 'ALWAYS';
export type GroupBuyReturnFeeMode = 'ONE_WAY' | 'ROUND_TRIP';
/** 결정 58 — 공식 판매처가 · 직전 차수 공구가 · 시가 · 종전 판매가 */
export type GroupBuyNormalPriceBasis = 'OFFICIAL_STORE' | 'PREV_ROUND' | 'MARKET' | 'PREV_SALE';
/** 결정 17·84 — 살 수 있음 · 잠시 구매 불가(다른 분이 결제 중 — 확정 + 선점 = 최대) · 다 팔림(확정 = 최대) */
export type GroupBuyStockState = 'AVAILABLE' | 'TEMP_SOLD_OUT' | 'SOLD_OUT';
/** 알림 신청 종류 — 수량 풀림 · 마감 임박 · 다음 차수 */
export type GroupBuyWatchKind = 'RESTOCK' | 'CLOSING_SOON' | 'NEXT_ROUND';
/**
 * 주문하기 단추가 꺼진(또는 다른 길로 가는) 까닭 — 서버가 정한다. `null` 이면 주문할 수 있다.
 * LOGIN_REQUIRED·IDENTITY_REQUIRED 는 「로그인하고 / 본인인증하고 주문하기」로 이어 가고, 나머지는 막는 안내다.
 */
export type GroupBuyPurchaseReason =
    | 'NOT_RECRUITING'
    | 'NOT_STARTED'
    | 'SOLD_OUT'
    | 'TEMP_SOLD_OUT'
    | 'ALREADY_JOINED'
    | 'LIMIT_REACHED'
    | 'LOGIN_REQUIRED'
    | 'IDENTITY_REQUIRED'
    | 'MINOR'
    | 'BANNED'
    | 'SALES_CLOSED';

/** 모집 시작 때 고정한 배송비 사본(10-2) — 금액 고지 문구는 이 값으로 만든다 */
export interface GroupBuyShipping {
    /** 처음 배송비 */
    fee: number;
    /** 무료배송 기준(상품 합) — null 이면 없음 */
    freeShippingOver: number | null;
    /** 반품 배송비(편도 한 번) */
    returnFee: number;
    returnFeeMode: GroupBuyReturnFeeMode;
}

/** 발송 지연 공지(결정 52) */
export interface GroupBuyDelayNotice {
    /** 이전 발송 예정일 */
    previousPlannedAt: string | null;
    /** 새 발송 예정일 */
    newPlannedAt: string;
    reason: string;
    noticedAt: string;
}

/** `GET /api/group-buys?phase=&page=` 의 한 줄 */
export interface GroupBuyListItem {
    id: number;
    title: string;
    thumbnailUrl: string | null;
    status: GroupBuyStatus;
    phase: GroupBuyPhase;
    closeReason: GroupBuyCloseReason | null;
    fulfillment: GroupBuyFulfillment;
    /** 공구가(옵션이 있으면 가장 싼 옵션 기준 — `priceFrom`) */
    groupPrice: number;
    /** true 면 「N원부터」(옵션마다 값이 다르다) */
    priceFrom: boolean;
    normalPrice: number | null;
    /** 0 = 선착순 */
    minQty: number;
    maxQty: number;
    /** 확정 수량(판매 수량) */
    currentQty: number;
    /** 남은 수량(서버 계산 — 최대 − 확정 − 선점, 0 아래 없음) */
    remainingQty: number;
    stockState: GroupBuyStockState;
    startAt: string;
    endAt: string;
    /** 연장 중이면 연장 끝 시각 */
    extendedUntil: string | null;
    /** 마감 임박(서버가 정한 기준 — 목업은 24시간 안) */
    closingSoon: boolean;
    roundNo: number;
}

export interface GroupBuyListPage {
    items: GroupBuyListItem[];
    page: number;
    size: number;
    total: number;
}

export interface GroupBuyImage {
    url: string;
    alt: string;
}

/** 옵션 한 줄(결정 84 — 남은 수량·품절 판정은 서버 값) */
export interface GroupBuyOption {
    id: number;
    name: string;
    extraPrice: number;
    /** 공구가 + 추가금 */
    unitPrice: number;
    /** 이 옵션으로 살 수 있는 남은 수량 = min(옵션 남음, 공구 남음) */
    remainingQty: number;
    /** 옵션 최대 — null 이면 옵션 한도 없음(공구 값을 따른다) */
    maxQty: number | null;
    stockState: GroupBuyStockState;
    /** 이 옵션의 수량 풀림 알림을 신청했나 */
    watchingRestock: boolean;
    /** 이 옵션에 수량 풀림 알림을 신청할 수 있나(서버) */
    canWatchRestock: boolean;
}

/* ── 상품정보제공고시(결정 87 — 공정위고시 제2022-15호) ── */

/** 항목 종류 — 글 · 날짜(대체 표기 원칙 ④) · KC 인증(원칙 ⑥) · 품질보증기준(원칙 ⑤) */
export type NoticeRowKind = 'TEXT' | 'DATE' | 'KC' | 'WARRANTY';
/** 날짜 항목 표기 — 정확한 날짜 · 가장 빠른 날짜 · 범위 · 주문일/발송일 기준 기간 */
export type NoticeDateMode = 'EXACT' | 'EARLIEST' | 'RANGE' | 'ORDER_BASED';
/** KC 항목 — 인증번호(식별부호) · 면제 사실 */
export type NoticeKcMode = 'CERT' | 'EXEMPT';

/** 고시 양식의 항목 한 줄 — 이름은 서버 카탈로그가 붙여 준다 */
export interface NoticeRowView {
    /** 카탈로그 항목 키 */
    key: string;
    name: string;
    kind: NoticeRowKind;
    /** 「제공 불가」면 빈 문자열일 수 있다 */
    value: string;
    /** 「제공 불가」(원칙 ③ — 사유를 그대로 보인다) */
    unavailable: boolean;
    reason: string;
    dateMode?: NoticeDateMode;
    kcMode?: NoticeKcMode;
}

/** 고시 양식 한 장(품목 하나) — `optionIds` 가 비면 기본 상품(공구 전체), 아니면 별개 상품 옵션용(원칙 ⑦) */
export interface GroupBuyNoticeView {
    id: string;
    order: number;
    /** 품목 번호 1~40 */
    categoryNo: number;
    /** 품목 이름(서버 카탈로그) */
    categoryName: string;
    /** 운영자가 붙인 제목(예 「본체」·「확장 센서 팩」) */
    title: string;
    /** 적용 옵션 ID — 한 옵션은 양식 하나에만 속한다 */
    optionIds: number[];
    /** 카탈로그 버전(예 `2023-01-01`) */
    catalogVersion: string;
    /** 카탈로그 출처(예 「공정거래위원회고시 제2022-15호」) */
    catalogSource: string;
    rows: NoticeRowView[];
}

/** 서버가 정한 단추 상태 */
export interface GroupBuyActions {
    /** 주문서로 갈 수 있나 */
    order: boolean;
    watchRestock: boolean;
    watchClosingSoon: boolean;
    watchNextRound: boolean;
}

/** `GET /api/group-buys/{id}` */
export interface GroupBuyDetail extends GroupBuyListItem {
    images: GroupBuyImage[];
    /** 서버가 정화한 HTML(공용 편집기 — 결정 2) */
    descriptionHtml: string;
    /** 평문 ≤ 5,000 */
    summary: string | null;
    /** 평문 ≤ 5,000(줄바꿈 유지) */
    spec: string | null;
    normalPriceBasis: GroupBuyNormalPriceBasis | null;
    /** 성사형·마감 후 발주의 발송 예정일 `YYYY-MM-DD`(결정 51) */
    shipPlannedAt: string | null;
    /** 선착순 즉시 발송의 「결제 후 N영업일 안 발송」 */
    shipWithinBusinessDays: number | null;
    shipping: GroupBuyShipping;
    /** 청약철회 제한 안내(결정 55) — 없으면 null */
    withdrawalRestriction: string | null;
    extensionMode: GroupBuyExtensionMode;
    /** 연장 최대(분, 공구 설정값) */
    extensionMaxMinutes: number;
    /** 「추가 입고 N개」(결정 80 — max(0, 최대 − 처음 최대)) */
    addedQty: number;
    /** 1인 한도 — null 이면 없음 */
    maxQtyPerUser: number | null;
    /** 차수 사이 1인 한도 합산(결정 24) */
    roundLimitShared: boolean;
    /** 옵션 없는 공구는 빈 배열 */
    options: GroupBuyOption[];
    /** 상품정보제공고시 양식(결정 87 — `order` 차례, 기본 양식 하나는 늘 있다) */
    notices: GroupBuyNoticeView[];
    delayNotice: GroupBuyDelayNotice | null;
    purchase: { reason: GroupBuyPurchaseReason | null };
    actions: GroupBuyActions;
    /** 공구 단위 알림 신청 여부(옵션 단위 RESTOCK 은 옵션 줄에) */
    watches: Record<GroupBuyWatchKind, boolean>;
    /** 진행 중인 내 주문(ALREADY_JOINED 일 때 그 주문으로 가는 길) */
    myOrderNo: string | null;
}

/* ── 견적·주문 ── */

/** 주문 줄 — 같은 옵션 두 줄 금지(결정 23), 옵션 없는 공구는 `optionId: null` 한 줄 */
export interface GroupBuyOrderLine {
    optionId: number | null;
    qty: number;
}

/** `POST /api/group-buys/{id}/quote` */
export interface GroupBuyQuoteRequest {
    items: GroupBuyOrderLine[];
}

/** 견적 — 화면은 계산하지 않는다(주문 때 서버가 다시 계산) */
export interface GroupBuyQuote {
    lines: { optionId: number | null; qty: number; unitPrice: number; amount: number }[];
    itemsAmount: number;
    shippingFee: number;
    totalAmount: number;
    /** 무료배송까지 남은 상품 금액 — 기준이 없거나 이미 넘었으면 null */
    freeShippingRemaining: number | null;
}

/** 배송지 입력(평문 — 서버가 받자마자 암호화한다, 10-9) */
export interface GroupBuyAddressInput {
    recipient: string;
    phone: string;
    postcode: string;
    address1: string;
    address2: string;
    memo: string;
}

/** `POST /api/group-buys/{id}/orders` + 머리글 `Idempotency-Key` */
export interface GroupBuyOrderRequest {
    items: GroupBuyOrderLine[];
    address: GroupBuyAddressInput;
    /** 동의 3종(결정 59 — 기본 체크 없음, 셋 다 true 여야 받는다) */
    agreements: { purchaseTerms: boolean; refundPolicy: boolean; privacy: boolean };
    /** 화면이 본 합계 — 비교용으로만(다르면 409 `GROUP_BUY_AMOUNT_CHANGED`, 청구액은 늘 서버 계산 — 결정 72) */
    expectedAmount: number;
}

/** 결제창 대상 — 공구 주문(`GROUP_BUY`)과 반품비 부족분(`GROUP_BUY_RETURN_FEE`) 둘 다 이 모양 */
export interface GroupBuyPaymentStart {
    orderType: 'GROUP_BUY' | 'GROUP_BUY_RETURN_FEE';
    orderRefId: string;
    /** 결제 유효 시각 — 공구 주문이면 선점 끝(`reserved_until`)과 같다(결정 41) */
    expiresAt: string;
    checkout: PaymentCheckout;
    /** 결과를 보러 갈 주문 번호 */
    orderNo: string;
    /** 포트원 구매자(KG이니시스가 요구 — 서버가 주문의 배송지·회원 이메일로 채운다). 가짜 게이트웨이면 null */
    customer: { fullName: string; phoneNumber: string; email: string } | null;
}

/** 주문 응답 — 주문 PENDING_PAYMENT + 선점 시작 */
export interface GroupBuyOrderCreated {
    orderNo: string;
    amount: number;
    /** 선점 끝 시각(5분 — 설정값) */
    reservedUntil: string;
    payment: GroupBuyPaymentStart;
}

/** `PUT /api/group-buys/{id}/watches` · `DELETE …/watches?kind=&optionId=` */
export interface GroupBuyWatchRequest {
    kind: GroupBuyWatchKind;
    /** 옵션 단위 수량 풀림 알림(결정 84) — 공구 단위면 null */
    optionId: number | null;
}
