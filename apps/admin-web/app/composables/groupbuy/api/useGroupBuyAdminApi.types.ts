/**
 * 공구 관리자 API(`/api/admin/group-buys…`)가 주고받는 모양 — 계약 `docs/features/commerce-groupbuy-contract.md`(2b-1 화면 목업이 정하는 모양, 서버 DTO 후보).
 * 열·상태 이름은 계약 그대로다. 금액(원)·수량·미리 보기 값은 **서버가 계산한 값**이고 화면은 보여 주기만 한다. 시각은 UTC ISO, 날짜만인 값은 `YYYY-MM-DD`(한국 날짜).
 */

import type { PaymentMember } from '~/composables/payment/api/usePaymentAdminApi.types';

/** 공구 상태(commerce.md 10-2) */
export type GroupBuyStatus = 'DRAFT' | 'RECRUITING' | 'CLOSING' | 'CLOSED_SUCCESS' | 'ORDERED' | 'SHIPPING' | 'COMPLETED' | 'CLOSED_FAILED' | 'CANCELED';
export const GROUP_BUY_STATUSES: readonly GroupBuyStatus[] = ['DRAFT', 'RECRUITING', 'CLOSING', 'CLOSED_SUCCESS', 'ORDERED', 'SHIPPING', 'COMPLETED', 'CLOSED_FAILED', 'CANCELED'];

/** 발송 시점 — 즉시 발송(재고 보유) / 마감 후 발주. CHECK `fulfillment = 'AFTER_CLOSE' OR min_qty = 0` */
export type GroupBuyFulfillment = 'IMMEDIATE' | 'AFTER_CLOSE';
/** 마감 자동 연장 방식 */
export type GroupBuyExtensionMode = 'DEMAND' | 'ALWAYS';
/** 종료 사유 `close_reason`(결정 79) */
export type GroupBuyCloseReason = 'SOLD_OUT' | 'TIME_UP' | 'EARLY_CLOSED' | 'CANCELED';
/** 정상가 기준 `normal_price_basis`(결정 58) — 공식 판매처가 · 직전 차수 공구가 · 시가 · 종전 판매가 */
export type NormalPriceBasis = 'OFFICIAL_STORE' | 'PREVIOUS_ROUND' | 'MARKET' | 'PREVIOUS_SALE';
export const NORMAL_PRICE_BASES: readonly NormalPriceBasis[] = ['OFFICIAL_STORE', 'PREVIOUS_ROUND', 'MARKET', 'PREVIOUS_SALE'];
/** 반품 배송비 방식(배송비 정책) — 편도 / 왕복 */
export type ReturnFeeMode = 'ONE_WAY' | 'ROUND_TRIP';

/** 수량 이동 종류(결정 17·54 — 계약 「마이그레이션 계획」) */
export type QtyMovementKind = 'RESERVE' | 'RELEASE' | 'CONFIRM' | 'CANCEL' | 'RETURN' | 'RESTOCK' | 'ADD' | 'REDUCE' | 'EXCHANGE_OUT';
export const QTY_MOVEMENT_KINDS: readonly QtyMovementKind[] = ['RESERVE', 'RELEASE', 'CONFIRM', 'CANCEL', 'RETURN', 'RESTOCK', 'ADD', 'REDUCE', 'EXCHANGE_OUT'];

/** 회원 한 칸 — 닉네임·번호(이메일 없음, 결제 화면과 같다) */
export type GroupBuyMember = PaymentMember;

/** 목록·상세가 같이 쓰는 페이지 모양 */
export interface GroupBuyPage<T> {
    items: T[];
    total: number;
}

// ── 목록 ───────────────────────────────────────────

/** `GET /api/admin/group-buys?status=&q=&page=&size=` 의 조건 */
export interface GroupBuyQuery {
    status?: GroupBuyStatus;
    /** 제목·상품명 일부 또는 공구 번호 */
    q?: string;
    page: number;
    size: number;
}

export interface GroupBuyRow {
    id: string;
    title: string;
    productName: string;
    /** N차(1차는 1) */
    roundNo: number;
    roundRootId: string;
    status: GroupBuyStatus;
    closeReason: GroupBuyCloseReason | null;
    fulfillment: GroupBuyFulfillment;
    /** 0 = 선착순 */
    minQty: number;
    maxQty: number;
    currentQty: number;
    reservedQty: number;
    groupPrice: number;
    startAt: string | null;
    endAt: string | null;
    /** 연장된 마감(null = 연장 안 됨) */
    extendedUntil: string | null;
    thumbnailUrl: string | null;
    /** 진행 중·끝난 주문 수(결제 확정된 것) */
    orderCount: number;
    updatedAt: string;
}

/** 목록 응답 — 상태 탭 건수(조건 `q` 를 적용한 건수)를 같이 준다 */
export interface GroupBuyListResponse extends GroupBuyPage<GroupBuyRow> {
    counts: Record<GroupBuyStatus, number>;
}

// ── 상세·편집 ─────────────────────────────────────

export interface GroupBuyImage {
    mediaId: string;
    url: string;
    thumbUrl: string;
}

/** `POST /api/admin/group-buys/images`(대표 이미지·설명 본문 이미지 — JPEG·PNG·WebP 정지 이미지 5MB, 서버가 다시 인코딩해 EXIF 를 지운다) */
export interface GroupBuyImageUpload {
    mediaId: string;
    url: string;
    thumbUrl: string;
    width: number;
    height: number;
}

export interface GroupBuyOption {
    id: string;
    name: string;
    /** 공구가에 더하는 금액(0 이상) */
    extraPrice: number;
    /** 옵션 한도(null = 공구 최대만 본다) */
    maxQty: number | null;
    /** 모집 시작 때 고정한 처음 한도(DRAFT 는 null) */
    initialMaxQty: number | null;
    currentQty: number;
    reservedQty: number;
}

/** 모집 시작 때 공구에 복사한 배송비 정책 사본(DRAFT 는 null) */
export interface GroupBuyShippingSnapshot {
    shippingFee: number;
    freeShippingOver: number | null;
    returnShippingFee: number;
    returnFeeMode: ReturnFeeMode;
}

/** 같은 루트(1차 공구)의 차수 한 줄 */
export interface GroupBuyRoundRef {
    id: string;
    roundNo: number;
    status: GroupBuyStatus;
    title: string;
}

/** 발송 지연 공지 한 줄(`group_buy_delay_notices`, 결정 52) */
export interface GroupBuyDelayNotice {
    id: string;
    previousPlannedAt: string | null;
    newPlannedAt: string;
    reason: string;
    /** 앱 + 메일로 알린 발송 전 주문 수 */
    notifiedOrders: number;
    operator: string;
    createdAt: string;
}

/** 옵션 공급 불가 처리 한 줄(`group_buy_supply_failures`, 결정 53) */
export interface GroupBuySupplyFailure {
    id: string;
    optionId: string;
    optionName: string;
    reason: string;
    /** 환불한 주문 줄 수 · 금액 */
    refundedLines: number;
    refundAmount: number;
    /** 「나머지만 받기 / 전체 취소」를 물은 주문 수 · 그중 아직 답 없음 */
    choiceOrders: number;
    choicePending: number;
    /** 회원 선택 기한(3영업일 — 설정값) */
    choiceDeadline: string;
    operator: string;
    createdAt: string;
}

/** 이 공구에 지금 할 수 있는 일 — 서버가 상태·권한·차수로 정한다(단추 상태). 막는 것은 서버다 */
export interface GroupBuyActions {
    /** DRAFT → RECRUITING */
    canStart: boolean;
    /** RECRUITING → CLOSING(조기 마감) */
    canCloseEarly: boolean;
    /** 중단(RECRUITING~SHIPPING → CANCELED) */
    canCancel: boolean;
    /** 공구 단위 발주(CLOSED_SUCCESS → ORDERED, 마감 후 발주 공구) */
    canMarkOrdered: boolean;
    /** 추가 차수로 열기 — 막히면 `roundBlockedReason` */
    canCreateRound: boolean;
    roundBlockedReason: 'ROUND_EXISTS' | 'SOURCE_INVALID' | null;
    /** 발송 지연 공지(발송 전 주문이 있을 때) */
    canDelayNotice: boolean;
    /** 옵션 공급 불가 처리(옵션이 있고 발송 전 줄이 있을 때) */
    canSupplyFailure: boolean;
    /** 추가 입고·줄이기(RECRUITING) */
    canAdjustStock: boolean;
}

/** `GET /api/admin/group-buys/{id}` */
export interface GroupBuyDetail {
    id: string;
    status: GroupBuyStatus;
    closeReason: GroupBuyCloseReason | null;
    roundNo: number;
    roundRootId: string;
    rounds: GroupBuyRoundRef[];
    title: string;
    productName: string;
    /** 평문 ≤ 5,000 */
    summary: string;
    spec: string;
    /** 서버가 정화한 HTML */
    description: string;
    images: GroupBuyImage[];
    normalPrice: number;
    groupPrice: number;
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    options: GroupBuyOption[];
    minQty: number;
    fulfillment: GroupBuyFulfillment;
    maxQty: number;
    initialMaxQty: number | null;
    /** ADD 합(결정 80) */
    addedQty: number;
    currentQty: number;
    reservedQty: number;
    /** null = 제한 없음 */
    maxQtyPerUser: number | null;
    roundLimitShared: boolean;
    startAt: string | null;
    endAt: string | null;
    extensionMode: GroupBuyExtensionMode;
    extendedUntil: string | null;
    closingAt: string | null;
    shippingPolicyCode: string;
    shippingSnapshot: GroupBuyShippingSnapshot | null;
    /** 발송 예정일(성사형·마감 후 발주 필수, 결정 51) — 날짜 `YYYY-MM-DD` */
    shipPlannedAt: string | null;
    /** 즉시 발송 공구의 「결제 후 N영업일」 */
    shipWithinBusinessDays: number | null;
    /** 청약철회 제한 안내 문구(선택, 결정 55) */
    withdrawalRestriction: string;
    /** 발송 전 주문 수(JOINED·CONFIRMED·PREPARING) — 지연 공지 안내에 쓴다 */
    preShipOrderCount: number;
    /** 낙관 잠금(설명·이미지 편집) */
    version: number;
    /** 마지막 수정 이력 번호 */
    revisionNo: number;
    /** 상품정보제공고시 양식들(결정 87) — 배열 차례. 기본 양식(`optionNames` 빔 = 공구 전체) + 별개 상품 옵션용 양식. 모집 뒤에도 고친다 */
    notices: GroupBuyNotice[];
    actions: GroupBuyActions;
    delayNotices: GroupBuyDelayNotice[];
    supplyFailures: GroupBuySupplyFailure[];
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

/** 옵션 한 줄(저장) — 새 줄은 `id` null */
export interface GroupBuyOptionInput {
    id: string | null;
    name: string;
    extraPrice: number;
    maxQty: number | null;
}

/** `POST /api/admin/group-buys`(새로) · `PUT /api/admin/group-buys/{id}`(DRAFT 만) — 모든 칸 */
export interface GroupBuySaveRequest {
    title: string;
    productName: string;
    summary: string;
    spec: string;
    description: string;
    imageIds: string[];
    normalPrice: number;
    groupPrice: number;
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    options: GroupBuyOptionInput[];
    minQty: number;
    fulfillment: GroupBuyFulfillment;
    maxQty: number;
    maxQtyPerUser: number | null;
    roundLimitShared: boolean;
    startAt: string | null;
    endAt: string | null;
    extensionMode: GroupBuyExtensionMode;
    shippingPolicyCode: string;
    shipPlannedAt: string | null;
    shipWithinBusinessDays: number | null;
    withdrawalRestriction: string;
    /** 고시 양식 전부(결정 87) — DRAFT 는 비거나 덜 채워도 저장된다(모집 시작 때 검사) */
    notices: GroupBuyNoticeInput[];
    /** 새로 만들 때는 null */
    version: number | null;
}

/**
 * `PATCH /api/admin/group-buys/{id}/content`(모집 시작 뒤 — 설명·이미지 + 고시 양식, 결정 2·87). 한 판의 수정 이력으로 남는다
 * (고시 양식만 바뀌면 종류 `NOTICE`, 설명·이미지도 바뀌면 `CONTENT` + 바뀐 칸 `notices`). 모집 뒤라 덜 채운 양식은 `409 GROUP_BUY_NOTICE_INCOMPLETE`.
 */
export interface GroupBuyContentRequest {
    summary: string;
    spec: string;
    description: string;
    imageIds: string[];
    notices: GroupBuyNoticeInput[];
    version: number;
}

// ── 상품정보제공고시(결정 87) ───────────────────────────
// 카탈로그는 서버가 정본(`server/shared/.../groupbuy/notice-catalog-<시행일>.json`) — 응답은 그 파일 모양 그대로라고 가정한다.

/** 항목 종류 — 글 · 날짜(대체 표기, 원칙 4) · KC 인증(원칙 6) · 품질보증기준(원칙 5). 설명 원문은 `NoticeCatalog.itemKinds` */
export type NoticeItemKind = 'TEXT' | 'DATE_LIKE' | 'KC_CERT' | 'QUALITY_WARRANTY';

/** 고시 일반원칙 2~7 원문(키는 카탈로그 파일 그대로) */
export interface NoticeCatalogPrinciples {
    /** 2. 비슷한 품목 고르기 · 없으면 (39)/(40) */
    similarCategory: string;
    /** 3. 제공할 수 없으면 구체적 사유 */
    unavailableReason: string;
    /** 4. 날짜 대체 표기 */
    dateAlternatives: string;
    /** 5. 품질보증기준 */
    qualityWarranty: string;
    /** 6. KC 인증정보 */
    kcCert: string;
    /** 7. 부수 상품 · 별개 상품 */
    accessory: string;
}

/** `GET /api/admin/group-buys/notice-catalog` — 고시 품목 40개와 품목별 항목(서버 데이터 파일 그대로) */
export interface NoticeCatalog {
    /** 시행일 버전(예 `2023-01-01`) */
    version: string;
    title: string;
    /** 예 `공정거래위원회고시 제2022-15호` */
    noticeNo: string;
    effectiveDate: string;
    /** 원문을 옮긴 날 */
    transcribedAt: string;
    /** 원문 주소 */
    source: string;
    transcriptionNote: string;
    itemKinds: Record<NoticeItemKind, string>;
    principles: NoticeCatalogPrinciples;
    categories: NoticeCategory[];
}

export interface NoticeCategory {
    /** 고시 품목 번호 1~40 */
    no: number;
    /** 예 `SMALL_ELECTRONICS` — 양식이 고르는 값 */
    code: string;
    name: string;
    /** 품목 적용 범위(예 (37) 「안전확인대상생활화학제품에 한함」) */
    scope?: string;
    /** 품질보증기준이 소비자분쟁해결기준보다 불리하면 그 사실·내용(원칙 5 — (10) 노트북 · (11)~(14)) */
    warrantyDisadvantageDisclosure?: string;
    items: NoticeItemDef[];
}

export interface NoticeItemDef {
    /** 원문 번호(예 `7`, `7-1`) */
    no: string;
    /** 영문 snake_case 키 — 같은 뜻이면 품목이 달라도 같은 키 */
    key: string;
    /** 하위 번호 항목의 묶음 줄(예 「7. 이동통신 가입조건」) — 없으면 빠지거나 null */
    group?: string | null;
    name: string;
    /** 원문 괄호 설명 */
    description: string | null;
    kind: NoticeItemKind;
    /** 원칙 3 — 모든 항목이 「값」 또는 「제공 불가 + 사유」 */
    required: boolean;
    /** 「…에 한함」 — 해당 없으면 「제공 불가 + 사유(해당 없음과 그 까닭)」 */
    conditional?: boolean;
}

/** 공구 응답의 고시 양식 한 장(관리자 조회 · 수정 이력 사본) — 배열 차례가 표시 차례다 */
export interface GroupBuyNotice {
    categoryCode: string;
    /** 예 「본체」·「충전기」 */
    title: string;
    /**
     * 적용 옵션 이름. 비면 공구 전체 = 기본 상품. 옵션이 있는 공구에서 기본 양식은 하나까지, 한 옵션은 양식 하나에만.
     * 기본 양식이 없으면 모든 옵션이 어느 양식에 들어야 한다(409 `unassignedOptions`).
     */
    optionNames: string[];
    /** 고시 항목 값(`itemKey` 로 찾는다) */
    values: NoticeItemValue[];
    /** 판매자 추가 항목(`sortOrder` 0부터 차례) */
    customItems: NoticeCustomItem[];
}

/** 고시 항목 한 칸 — 값, 또는 「제공 불가」 + 사유(원칙 3) */
export interface NoticeItemValue {
    itemKey: string;
    value: string;
    unavailable: boolean;
    unavailableReason: string;
}

/**
 * 판매자 추가 항목 한 줄 — 이름 1~40자 · 내용 1~1,000자 평문, 양식당 20개, 같은 양식 안 이름 겹침 금지,
 * 고시 항목과 같은 이름 금지(공백 무시, 「크기, 무게」처럼 쉼표로 나뉜 각 이름 포함). 선택 사항이라 모집 시작 검사(409) 대상이 아니다.
 */
export interface NoticeCustomItem {
    name: string;
    value: string;
    /** 0부터 */
    sortOrder: number;
}

/** 저장 요청 `values[]` 의 판매자 추가 항목 줄 — 이름·내용 둘 다 빈 줄은 서버가 버린다 */
export interface NoticeCustomInput {
    custom: true;
    itemKey: null;
    name: string;
    value: string;
    sortOrder: number;
}

/** 저장 요청의 고시 양식 한 장 — 고시 항목 뒤에 판매자 추가 항목을 같은 `values[]` 에 */
export interface GroupBuyNoticeInput {
    categoryCode: string;
    title: string;
    optionNames: string[];
    values: (NoticeItemValue | NoticeCustomInput)[];
}

/** 모집 시작(공개)과 공개 뒤 저장의 거절 `409 GROUP_BUY_NOTICE_INCOMPLETE` 의 `extras` */
export interface GroupBuyNoticeIncomplete {
    /** 양식이 하나도 없다 */
    noNotice: boolean;
    /** 빈 항목이 있는 양식 */
    notices: GroupBuyNoticeProblem[];
    /** 기본 양식이 없을 때 어느 양식에도 들지 않은 옵션 이름(화면 해석 — 2b-2 확인 필요) */
    unassignedOptions: string[];
}

export interface GroupBuyNoticeProblem {
    /** 양식 차례(0부터 — `notices[i]` 와 같다고 가정) */
    index: number;
    title: string;
    categoryCode: string;
    /** 값이 비었거나 「제공 불가」인데 사유가 빈 항목 키 */
    missingKeys: string[];
}

/** 등록 화면의 고를 값 — `GET /api/admin/group-buys/form-options` */
export interface GroupBuyFormOptions {
    shippingPolicies: GroupBuyPolicyChoice[];
    /** 즉시 발송 공구의 기본 N영업일(공구 설정값, 기본 3) */
    defaultShipWithinBusinessDays: number;
    /** 공구 기간 상한(설정값) — 성사형 14일 · 선착순 30일 */
    periodMaxDays: { threshold: number; firstCome: number };
}

export interface GroupBuyPolicyChoice {
    code: string;
    name: string;
    /** 지금 유효한 줄(없으면 null — 모집 시작 불가) */
    current: GroupBuyPolicyLine | null;
    /** 적용 예정 줄(가장 가까운 것) */
    upcoming: GroupBuyPolicyLine | null;
}

export interface GroupBuyPolicyLine {
    baseFee: number;
    freeShippingOver: number | null;
    returnFee: number;
    returnFeeMode: ReturnFeeMode;
    effectiveFrom: string;
}

// ── 수정 이력 ───────────────────────────────────────

/** 이력 종류 — 처음 모집 시작(첫 판) · 설명·이미지 수정 · 수량(추가 입고·줄이기) · 발송 예정일 변경(지연 공지) · 고시 양식만 정정(결정 87) */
export type GroupBuyRevisionKind = 'START' | 'CONTENT' | 'STOCK' | 'SHIP_DATE' | 'NOTICE';

/** `GET /api/admin/group-buys/{id}/revisions?page=&size=` 한 줄(6개월 보관) */
export interface GroupBuyRevision {
    revisionNo: number;
    kind: GroupBuyRevisionKind;
    /** 바뀐 칸 이름(서버 열 이름 — `description`·`images`·`maxQty` …) */
    changedFields: string[];
    title: string;
    summary: string;
    /** 정화된 HTML 사본 */
    description: string;
    imageCount: number;
    maxQty: number;
    addedQty: number;
    optionMax: { name: string; maxQty: number | null }[];
    shipPlannedAt: string | null;
    /** 그 판의 고시 양식 사본 */
    notices: GroupBuyNotice[];
    operator: string;
    createdAt: string;
}

// ── 운영 동작 ───────────────────────────────────────

/** `GET /api/admin/group-buys/{id}/cancel-preview` — 중단 확인 창 숫자(결정 29) */
export interface GroupBuyCancelPreview {
    /** 발송 전(JOINED·CONFIRMED·PREPARING) — 전액 환불 */
    refundOrders: number;
    refundAmount: number;
    /** 결제 중(PENDING_PAYMENT) — 만료·선점 해제 */
    expireOrders: number;
    /** 이미 발송(SHIPPED·DELIVERED) — 그대로 */
    shippedOrders: number;
}

/** `GET /api/admin/group-buys/{id}/supply-failure-preview?optionId=` — 옵션 공급 불가 결과 미리 보기(결정 53) */
export interface GroupBuySupplyFailurePreview {
    optionId: string;
    optionName: string;
    /** 그 옵션의 발송 전 줄 — 환불할 줄 수 · 금액(배송비 재계산 없음) */
    cancelLines: number;
    refundAmount: number;
    /** 그 옵션만 산 주문 — 배송비 포함 전액 */
    fullRefundOrders: number;
    /** 다른 줄이 남은 주문 — 회원에게 「나머지만 받기 / 전체 취소」를 묻는다 */
    choiceOrders: number;
    /** 회원 선택 기한(지금 + 3영업일) */
    choiceDeadline: string;
}

/** `POST /api/admin/group-buys/{id}/delay-notices` */
export interface GroupBuyDelayNoticeRequest {
    /** 새 발송 예정일 `YYYY-MM-DD` */
    newPlannedAt: string;
    reason: string;
}

/** `POST /api/admin/group-buys/{id}/supply-failures` */
export interface GroupBuySupplyFailureRequest {
    optionId: string;
    reason: string;
}

/** `POST /api/admin/group-buys/{id}/rounds` 응답 — 새 DRAFT 차수 */
export interface GroupBuyRoundCreated {
    id: string;
    roundNo: number;
}

// ── 수량 ─────────────────────────────────────────

/** 공구 전체 또는 옵션 한 줄의 수량 */
export interface GroupBuyStockLine {
    /** 공구 전체 줄은 null */
    optionId: string | null;
    name: string;
    /** 옵션 한도가 없으면 null */
    maxQty: number | null;
    initialMaxQty: number | null;
    /** 「추가 입고 N개」 = max(0, max − initial)(결정 80) */
    addedQty: number;
    /** 확정(판매 수량) */
    currentQty: number;
    /** 선점(결제 중) */
    reservedQty: number;
    /** 남음 — 옵션은 min(옵션, 공구)(결정 84). 한도 없는 옵션은 공구 값 */
    remainingQty: number;
    /** 반품 기록 수량(폐기·반송·보관 포함 — 판매 수량은 그대로) */
    returnedQty: number;
    /** 다시 판매한 수량(RESTOCK) */
    restockedQty: number;
    soldOut: boolean;
    /** 잠시 품절(확정 + 선점 = 최대) */
    tempSoldOut: boolean;
    /** 줄이기 하한 = max(확정 + 선점, 성사형 min_qty) — 옵션은 확정 + 선점 */
    reduceFloor: number;
}

/** 선점 한 줄(결제 중 주문) */
export interface GroupBuyHold {
    orderNo: string;
    member: GroupBuyMember;
    /** 줄 요약(「블랙 × 1, 화이트 × 2」) — 서버가 만든 글 */
    lines: string;
    qty: number;
    reservedUntil: string;
}

/** `GET /api/admin/group-buys/{id}/stock` */
export interface GroupBuyStockView {
    groupBuy: { id: string; title: string; roundNo: number; status: GroupBuyStatus; minQty: number; fulfillment: GroupBuyFulfillment };
    total: GroupBuyStockLine;
    options: GroupBuyStockLine[];
    holds: GroupBuyHold[];
    canAdjust: boolean;
}

/** `GET /api/admin/group-buys/{id}/stock/movements?kind=&page=&size=` 한 줄 — 공구·옵션 양쪽의 이후 값 */
export interface GroupBuyMovement {
    id: string;
    kind: QtyMovementKind;
    optionName: string | null;
    qty: number;
    currentAfter: number;
    reservedAfter: number;
    maxAfter: number;
    optionCurrentAfter: number | null;
    optionReservedAfter: number | null;
    optionMaxAfter: number | null;
    orderNo: string | null;
    /** 운영자 이름 또는 `SYSTEM`(워커) · 회원 동작이면 닉네임 */
    actor: string;
    reason: string | null;
    createdAt: string;
}

export interface GroupBuyMovementQuery {
    kind?: QtyMovementKind;
    page: number;
    size: number;
}

/** `POST /api/admin/group-buys/{id}/stock/adjust` — 추가 입고(ADD)·줄이기(REDUCE), 사유 필수. 하한 아래면 `409 GROUP_BUY_QTY_BELOW_FLOOR`(extras.floor) */
export interface GroupBuyStockAdjustRequest {
    /** null = 공구 전체(옵션 한도가 없는 옵션은 공구로만) */
    optionId: string | null;
    direction: 'ADD' | 'REDUCE';
    qty: number;
    reason: string;
}
