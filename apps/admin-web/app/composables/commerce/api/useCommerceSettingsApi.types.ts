/**
 * 커머스 설정 API 가 주고받는 모양 — 서버 DTO `RequestLimitsView`·`RequestLimitsSaveRequest`(server/admin `commerce.dto`).
 * 정책 원천: `docs/domain/commerce.md` 12-4(돈 관련 요청 속도 제한). 시각은 UTC ISO 다 — 화면은 `useDisplayTime()` 으로 그린다.
 */

/** 제한하는 요청 종류 — 화면·서버 모두 이 차례로 보인다 */
export type RequestLimitAction = 'GROUPBUY_JOIN' | 'POINT_CHARGE' | 'POINT_SHOP_PURCHASE' | 'REFUND_REQUEST';

/** 고정 차례(공구 참여 · 충전 요청 · 상점 구매 · 환불 신청) — 타입에 딸린 상수 */
export const REQUEST_LIMIT_ACTIONS: readonly RequestLimitAction[] = ['GROUPBUY_JOIN', 'POINT_CHARGE', 'POINT_SHOP_PURCHASE', 'REFUND_REQUEST'];

export interface RequestLimitItem {
    action: RequestLimitAction;
    /** 회원 한 명이 1분 동안 보낼 수 있는 수(0 = 제한 없음, 0~100) */
    limitPerMinute: number;
    /** 처음 값(12-4 표) — 「기본값 N」 표시와 되돌리기에 쓴다 */
    defaultLimit: number;
}

/** `GET`·`PUT /api/admin/commerce/request-limits` 응답 */
export interface RequestLimitsView {
    items: RequestLimitItem[];
    /** 마지막 저장 시각(UTC ISO). 저장한 적 없으면 null(기본값) */
    updatedAt: string | null;
    /** 마지막으로 저장한 운영자 이름. 저장한 적 없으면 null */
    updatedBy: string | null;
}

/** `PUT /api/admin/commerce/request-limits` 요청 — 네 종류 모두 보낸다 */
export interface RequestLimitsSaveRequest {
    limits: Record<RequestLimitAction, number>;
}

// ── 커머스 2b(공구) 구획 — 계약 `commerce-groupbuy-contract.md` 결정 8·10·11·18, commerce.md 10-2(배송비 정책)·20절(판매 스위치) ─────

/** 반품 배송비 방식 — 편도(기본) / 왕복(경고 — commerce.md 10-8) */
export type ShippingReturnFeeMode = 'ONE_WAY' | 'ROUND_TRIP';

/** 배송비 정책 한 줄(`shipping_policies`) — 같은 코드의 기간은 겹치지 않고, 적용이 시작된 줄은 고치거나 지우지 못한다 */
export interface ShippingPolicyLine {
    id: string;
    code: string;
    name: string;
    baseFee: number;
    /** 무료배송 기준(null = 없음) */
    freeShippingOver: number | null;
    returnFee: number;
    returnFeeMode: ShippingReturnFeeMode;
    /** 적용 시작(UTC ISO) */
    effectiveFrom: string;
    /** 적용 끝(null = 계속) — 다음 줄이 생기면 서버가 채운다 */
    effectiveTo: string | null;
    /** 적용이 시작됐다(고치기·지우기 불가) */
    started: boolean;
    /** 지금 유효한 줄 */
    current: boolean;
    createdBy: string;
    createdAt: string;
}

/** `GET /api/admin/commerce/shipping-policies` */
export interface ShippingPolicyList {
    items: ShippingPolicyLine[];
}

/** `POST /api/admin/commerce/shipping-policies` — 새 줄 더하기. 겹치면 `409 SHIPPING_POLICY_CONFLICT` */
export interface ShippingPolicyCreateRequest {
    code: string;
    name: string;
    baseFee: number;
    freeShippingOver: number | null;
    returnFee: number;
    returnFeeMode: ShippingReturnFeeMode;
    effectiveFrom: string;
}

/** 택배사 한 줄(`commerce_carriers`) — 6개 시드 `CJ`·`HANJIN`·`LOTTE`·`EPOST`·`LOGEN`·`KDEXP` */
export interface CarrierLine {
    code: string;
    name: string;
    /** `https://…{no}`(`{no}` 한 번) */
    trackingUrlTemplate: string;
    active: boolean;
    /** 발송 이력이 있다(지우지 못하고 끄기만 — 결정 11) */
    used: boolean;
}

/** `GET`·`PUT /api/admin/commerce/carriers`(목록 통째로 저장, 차례 = 배열 차례) */
export interface CarrierList {
    items: CarrierLine[];
    updatedAt: string | null;
    updatedBy: string | null;
}

export interface CarrierSaveRequest {
    items: { code: string; name: string; trackingUrlTemplate: string; active: boolean }[];
}

/** 사업자 정보 칸(결정 18) — 평문 저장 */
export interface BusinessInfoValues {
    companyName: string;
    representative: string;
    /** 사업자등록번호 `000-00-00000`(체크섬은 서버가 판정) */
    businessNo: string;
    mailOrderNo: string;
    /** 통신판매업 신고 기관 */
    mailOrderAuthority: string;
    address: string;
    phone: string;
    email: string;
    businessHours: string;
    privacyOfficer: string;
    hostingProvider: string;
    /** 배송·환불 정책 문구 ≤ 3,000 */
    shippingRefundPolicy: string;
}

/** `GET`·`PUT /api/admin/commerce/business-info` */
export interface BusinessInfoView {
    values: BusinessInfoValues;
    /** 판매 켜기 자동 조건에 드는 필수 칸이 다 찼다 */
    ready: boolean;
    updatedAt: string | null;
    updatedBy: string | null;
}

/** 판매 켜기 자동 조건(commerce.md 20절) */
export type SalesAutoCheckKey = 'BUSINESS_INFO' | 'PG_KEYS' | 'CARRIER' | 'IDENTITY';
/** 운영 체크 항목(수동) */
export type SalesChecklistKey = 'BUSINESS_REG' | 'MAIL_ORDER_REPORT' | 'TERMS' | 'PRIVACY_POLICY' | 'PG_CONTRACT' | 'RETURN_ADDRESS';

export interface SalesChecklistItem {
    key: SalesChecklistKey;
    checked: boolean;
    checkedBy: string | null;
    checkedAt: string | null;
}

/** `GET /api/admin/commerce/sales` */
export interface SalesSwitchView {
    enabled: boolean;
    autoChecks: { key: SalesAutoCheckKey; ok: boolean }[];
    checklist: SalesChecklistItem[];
    /** 자동 조건과 체크리스트가 모두 찼다(켜기는 최고 관리자만) */
    canEnable: boolean;
    changedAt: string | null;
    changedBy: string | null;
}

/** `POST /api/admin/commerce/sales` — 켜기(최고 관리자만)·끄기(최고 관리자 또는 `commerce.settings`), 사유 필수 */
export interface SalesToggleRequest {
    enabled: boolean;
    reason: string;
}

/** 공구 설정 값(결정 10·8·51·53) — 칸 이름은 서버 `group_buy_settings` 열 후보 */
export interface GroupBuySettingsValues {
    holdMinutes: number;
    thresholdPeriodMaxDays: number;
    firstComePeriodMaxDays: number;
    extensionMaxMinutes: number;
    extensionWindowMinutes: number;
    judgeGraceMinutes: number;
    compensationCap: number;
    /** 0 = 끔 */
    largeRefundThreshold: number;
    addressRevealMinutes: number;
    expiredAddressRetentionDays: number;
    shipWithinBusinessDays: number;
    supplyChoiceBusinessDays: number;
    /** 휴일(주말 외) `YYYY-MM-DD` */
    holidays: string[];
}

/** 숫자 칸 이름 */
export type GroupBuySettingsNumberKey = Exclude<keyof GroupBuySettingsValues, 'holidays'>;

/** `GET`·`PUT /api/admin/commerce/group-buy-settings` — 범위·기본값은 서버가 준다 */
export interface GroupBuySettingsView {
    values: GroupBuySettingsValues;
    defaults: Record<GroupBuySettingsNumberKey, number>;
    ranges: Record<GroupBuySettingsNumberKey, { min: number; max: number }>;
    updatedAt: string | null;
    updatedBy: string | null;
}

export interface GroupBuySettingsSaveRequest {
    values: GroupBuySettingsValues;
}
