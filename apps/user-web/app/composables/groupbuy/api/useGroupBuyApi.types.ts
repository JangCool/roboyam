import type { GroupBuyReviewStats } from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';
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
export type GroupBuyNormalPriceBasis = 'OFFICIAL_STORE' | 'PREVIOUS_ROUND' | 'MARKET' | 'PREVIOUS_SALE';
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
    id: string;
    title: string;
    thumbnailUrl: string | null;
    status: GroupBuyStatus;
    phase: GroupBuyPhase;
    closeReason: GroupBuyCloseReason | null;
    fulfillment: GroupBuyFulfillment;
    /** 공구가(본 상품 단품 값이 다르면 가장 싼 것 — `priceFrom`) */
    groupPrice: number;
    /** true 면 「N원부터」(본 상품 단품마다 값이 다르다) */
    priceFrom: boolean;
    normalPrice: number | null;
    /** 0 = 선착순. 공구 최대·확정·남은 수량·성사 판정은 본 상품(MAIN) 수량만 센다(결정 88) */
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
    /** 상품의 카테고리(결정 89 — 아래 단, 아래 단이 없으면 위 단) */
    categoryId: string;
    /** 리뷰 평균 별점·수(같은 상품의 모든 차수 — 2b-6). 리뷰가 없으면 `count: 0` */
    reviews?: GroupBuyReviewStats | null;
}

/** `GET /api/group-buys/categories` — 회원 목록 필터(2단, 숨긴 카테고리는 서버가 뺀다). 2b-2 확정 전 가정 */
export interface GroupBuyCategoryNode {
    id: string;
    name: string;
    children: { id: string; name: string }[];
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

/* ── 옵션 축 · 단품(결정 88) — 서버 2b-2(커밋 9c23966a)가 정한 남은 수량(Availability) 모양 ── */

/** 옵션 축의 값 하나(예 「블랙」·「M」) */
export interface GroupBuyAxisValue {
    valueId: string;
    value: string;
}

/** 옵션 축 하나(예 「색상」) — 공구마다 0~3개, 배열 차례가 고르는 차례 */
export interface GroupBuyAxis {
    axisId: string;
    name: string;
    values: GroupBuyAxisValue[];
}

/** 단품 종류 — 본 상품(축 값 조합) · 추가 구성상품(단독 구매 불가) */
export type GroupBuySkuKind = 'MAIN' | 'ADDON';

/**
 * 단품(SKU) — 실제로 파는 단위. 수량·품절·수량 풀림 알림·주문 줄·반품은 모두 단품 단위(결정 88). 값은 모두 서버 판정이다.
 */
export interface GroupBuySku {
    skuId: string;
    kind: GroupBuySkuKind;
    /** MAIN 이름 = 값을 「 / 」로 이은 것(「블랙 / M」), 축이 없으면 상품 이름. ADDON 은 상품 이름 */
    name: string;
    /** MAIN 의 축 값 조합(축 차례대로 값 ID) — ADDON 은 빈 배열 */
    valueIds: string[];
    /** 한 개 값 — MAIN = 공구가 + 추가금, ADDON = 자기 가격 */
    unitPrice: number;
    /** 남은 수량 — MAIN = min(단품 남은, 공구 남은), ADDON 은 자기 한도만. null = 제한 없음(한도 없는 ADDON) */
    remaining: number | null;
    /** 다 팔림(확정 = 최대) */
    soldOut: boolean;
    /** 잠시 구매 불가(다른 분이 결제 중 — 확정 + 선점 = 최대) */
    temporarilyUnavailable: boolean;
    /** false 면 판매 끈 조합(회색 — 고를 수 없음) */
    onSale: boolean;
}

/** 상세의 남은 수량 묶음(서버 `Availability`) — 공구 남은 수량·품절과 축·단품 */
export interface GroupBuyAvailability {
    groupBuyId: string;
    /** 공구 남은 수량(MAIN 만 센다) */
    remaining: number;
    soldOut: boolean;
    temporarilyUnavailable: boolean;
    addedQty: number;
    /** 0~3, 차례대로 */
    axes: GroupBuyAxis[];
    /** MAIN 은 축 값 조합마다 하나(축이 없으면 하나), ADDON 은 추가 구성상품. 배열 차례가 표시 차례 */
    skus: GroupBuySku[];
}

/** 단품 단위 수량 풀림 알림(결정 84·88) — 목업 가정: 서버 2b-2 가 아직 칸 이름을 정하지 않았다 */
export interface GroupBuySkuWatches {
    /** 알림을 신청할 수 있는 단품(품절·잠시 구매 불가 중 서버가 허락한 것) */
    watchable: string[];
    /** 알림을 신청한 단품 */
    watching: string[];
}

/* ── 상품정보제공고시(결정 87 — 공정위고시 제2022-15호) ── */

/** 항목 종류(서버 카탈로그) — 글 · 날짜(대체 표기 원칙 4) · KC 인증(원칙 6) · 품질보증기준(원칙 5) */
export type NoticeRowKind = 'TEXT' | 'DATE_LIKE' | 'KC_CERT' | 'QUALITY_WARRANTY';

/** 고시 양식의 항목 한 줄 — 항목 번호·묶음·이름·설명은 서버가 카탈로그에서 붙여 준다(가정) */
export interface NoticeRowView {
    itemKey: string;
    /** 원문 번호(예 `7`, `7-1`) */
    no: string;
    /** 하위 번호 항목의 묶음 줄(예 「7. 이동통신 가입조건」) — 없으면 null */
    group: string | null;
    name: string;
    /** 원문 괄호 설명 */
    description: string | null;
    kind: NoticeRowKind;
    /** 「제공 불가」면 빈 문자열일 수 있다 */
    value: string;
    /** 「제공 불가」(원칙 3 — 사유를 그대로 보인다) */
    unavailable: boolean;
    unavailableReason: string;
}

/** 판매자 추가 항목 한 줄(서버 `customItems` — 0부터 차례) */
export interface NoticeCustomItemView {
    name: string;
    value: string;
    sortOrder: number;
}

/** 고시 양식 한 장(품목 하나) — 배열 차례가 표시 차례. 적용 단품이 비면 기본 양식(MAIN 만 덮는다), 아니면 그 단품들 전용(원칙 7 — 추가 구성상품은 단품마다 자기 양식) */
export interface GroupBuyNoticeView {
    /** 품목 코드(예 `SMALL_ELECTRONICS`) */
    categoryCode: string;
    /** 품목 번호 1~40(표시용 — 서버가 붙여 줌) */
    categoryNo: number;
    /** 품목 이름(표시용 — 서버가 붙여 줌) */
    categoryName: string;
    /** 운영자가 붙인 제목(예 「본체」·「확장 센서 팩」) */
    title: string;
    /** 적용 단품(결정 88 — 한 단품은 양식 하나에만 속한다, 예전 `optionNames`) — 비면 기본 양식 */
    skuIds: string[];
    /** 적용 단품 이름(`skuIds` 차례) */
    skuNames: string[];
    rows: NoticeRowView[];
    customItems: NoticeCustomItemView[];
    /** 카탈로그 버전(예 `2023-01-01`) */
    catalogVersion: string;
    /** 고시 번호(예 「공정거래위원회고시 제2022-15호」) — 출처 줄 */
    noticeNo: string;
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
    /** 1인 한도(본 상품 MAIN 수량만 센다 — 결정 88) — null 이면 없음 */
    maxQtyPerUser: number | null;
    /** 차수 사이 1인 한도 합산(결정 24) */
    roundLimitShared: boolean;
    /** 남은 수량 · 옵션 축 · 단품(서버 `Availability`, 예전 `options` 를 대신한다 — 결정 88) */
    availability: GroupBuyAvailability;
    /** 단품 단위 수량 풀림 알림(목업 가정 — 서버 칸 이름 미정) */
    skuWatches: GroupBuySkuWatches;
    /** 상품정보제공고시 양식(결정 87 — 배열 차례. 공개된 공구는 양식이 다 찬 상태다) */
    notices: GroupBuyNoticeView[];
    delayNotice: GroupBuyDelayNotice | null;
    purchase: { reason: GroupBuyPurchaseReason | null };
    actions: GroupBuyActions;
    /** 공구 단위 알림 신청 여부(단품 단위 RESTOCK 은 단품에) */
    watches: Record<GroupBuyWatchKind, boolean>;
    /** 진행 중인 내 주문(ALREADY_JOINED 일 때 그 주문으로 가는 길) */
    myOrderNo: string | null;
}

/* ── 견적·주문 ── */

/**
 * 주문 줄(서버 2b-2) — 단품 하나에 한 줄, 축 없는 공구도 MAIN 단품 ID 로 보낸다.
 * MAIN 줄이 하나 이상 있어야 한다 — 추가 구성상품만·같은 단품 두 줄·수량 0 은 400 `fields.items`(서버 문구).
 * 판매 안 함 409 `GROUP_BUY_SKU_NOT_ON_SALE {skuId}` · 품절 409 `GROUP_BUY_SKU_SOLD_OUT {skuId, temporary}`. 1인 한도·공구 최대·성사는 MAIN 만 센다.
 */
export interface GroupBuyOrderLine {
    skuId: string;
    qty: number;
}

/** `POST /api/group-buys/{id}/quote` */
export interface GroupBuyQuoteRequest {
    items: GroupBuyOrderLine[];
}

/** 견적 줄(서버 2b-2) */
export interface GroupBuyQuoteItem {
    skuId: string;
    kind: GroupBuySkuKind;
    name: string;
    qty: number;
    unitPrice: number;
    itemAmount: number;
}

/** 견적 — 화면은 계산하지 않는다(주문 때 서버가 다시 계산). 무료배송 기준은 추가 구성상품까지 더한 상품 합(결정 88) */
export interface GroupBuyQuote {
    items: GroupBuyQuoteItem[];
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

/** `PUT /api/group-buys/{id}/watches` · `DELETE …/watches?kind=&skuId=` */
export interface GroupBuyWatchRequest {
    kind: GroupBuyWatchKind;
    /** 단품 단위 수량 풀림 알림(결정 84·88 — `group_buy_watches.sku_id`) — 공구 단위면 null */
    skuId: string | null;
}
