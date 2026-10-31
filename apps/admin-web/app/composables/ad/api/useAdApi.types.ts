import type { BizAdType, BizAmount, BizContractFile, BizDeposit, BizPaymentMethod, BizPeriod, BizQuote } from '~/composables/bizapplication/api/useBizApplicationApi.types';

/**
 * 광고 일정표(관리자) — 광고 건 · 유형별 한도 · 날짜별 남은 칸 · 운영자 수동 등록. 서버와 주고받는 모양(계약 전 목업, 02-ad.md 7-1 · 00-overview.md ★-2).
 * 광고 건은 사업자 신청 건이 승인되면 생기거나(`APPLICATION`), 운영자가 직접 등록한다(`OPERATOR` — 늘 수동 결제).
 * 기간은 반열림 `[startAt, endAt)` — 화면은 「~10/10 00:00」 처럼 끝 시각을 적고 그 시각은 포함하지 않는다.
 */

export type AdType = BizAdType;

/** `SUBMITTED` = 운영자 수동 등록 뒤 광고 심사 대기(04-ia-review §2 — 자리는 승인 때부터 차지한다) */
export type AdBookingStatus = 'SUBMITTED' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'SCHEDULED' | 'RUNNING' | 'ENDED' | 'TAKEN_DOWN' | 'CANCELED';
export const AD_BOOKING_STATUSES: readonly AdBookingStatus[] = ['SUBMITTED', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'SCHEDULED', 'RUNNING', 'ENDED', 'TAKEN_DOWN', 'CANCELED'];

export interface AdTypeSummary {
    type: AdType;
    /** 같은 시각에 함께 나갈 수 있는 수 */
    limit: number;
    running: number;
    scheduled: number;
    /** 결제·입금 대기(자리를 이미 잡고 있다) */
    awaitingPayment: number;
}

/** 날짜별 남은 칸 — 0 이면 꽉 참 */
export interface AdDaySlot {
    date: string;
    remaining: number;
    limit: number;
}

export interface AdCalendar {
    summary: AdTypeSummary;
    days: AdDaySlot[];
}

/** 광고주 — 사업자이거나 계정 없는 광고주(운영자가 만든다) */
export interface AdAdvertiser {
    id: string;
    kind: 'BUSINESS' | 'ADVERTISER';
    name: string;
    businessId: string | null;
    maskedRegNo: string | null;
    contactMasked: string | null;
}

export interface AdBookingRow {
    id: string;
    type: AdType;
    advertiser: AdAdvertiser;
    period: BizPeriod;
    status: AdBookingStatus;
    paymentMethod: BizPaymentMethod;
    /** 같은 자리에 여럿이 돌 때의 비중(1~10) */
    weight: number;
    source: 'APPLICATION' | 'OPERATOR';
    applicationNo: string | null;
    lastActionAt: string;
}

export interface AdCreativeVersion {
    version: number;
    imageUrl: string | null;
    text: string | null;
    linkUrl: string;
    altText: string | null;
    /** 심사 승인 시각·승인자 — 심사 대기(`SUBMITTED`)는 null */
    approvedAt: string | null;
    approvedBy: string | null;
    current: boolean;
}

export interface AdHistory {
    at: string;
    action: string;
    actor: string;
    note: string | null;
}

export interface AdBookingDetail extends AdBookingRow {
    amount: BizAmount;
    creatives: AdCreativeVersion[];
    onlinePayment: { paidAt: string; pgTxId: string } | null;
    contracts: BizContractFile[];
    deposit: BizDeposit | null;
    takedown: { at: string; reason: string; by: string; mailSent: boolean } | null;
    memo: string | null;
    history: AdHistory[];
}

export interface AdBookingQuery {
    type: AdType;
    status?: AdBookingStatus;
    q?: string;
    page: number;
    size: number;
}

export interface AdBookingPage {
    items: AdBookingRow[];
    total: number;
}

/** 기간 판정 — 그 기간 가장 빠듯한 날의 남은 칸, 꽉 찬 날, 요금표 계산값 */
export interface AdAvailability {
    remaining: number;
    limit: number;
    fullDates: string[];
    quote: BizQuote;
}

export interface AdAdvertiserCreateRequest {
    name: string;
    contactName: string;
    contactPhone: string;
    email: string;
    memo: string;
}

/**
 * 운영자 수동 등록 = 신청 건 `SUBMITTED`(source=OPERATOR) — 광고 심사에서 승인 → 계약서 사본 + 입금 확인 → [예약 확정].
 * `supply` 는 계약 금액 제안(승인 때 확정, 요금표와 다르면 승인 창에서 사유). 계약서는 오프라인으로 먼저 받았을 때만 미리 붙인다(선택).
 */
export interface AdManualCreateRequest {
    advertiserId: string;
    type: AdType;
    period: BizPeriod;
    weight: number;
    creative: { imageUrl: string | null; text: string | null; linkUrl: string; altText: string | null };
    supply: number;
    contractFileIds: string[];
    memo: string;
}

export interface AdTakedownRequest {
    reason: string;
    notifyMail: boolean;
}

/** 되돌릴 때 보상(F5) — 중단 시간만큼 끝 시각 연장 · 일할 환불 */
export type AdRestoreCompensation = 'EXTEND' | 'PRORATE_REFUND';

export interface AdRestoreRequest {
    compensation: AdRestoreCompensation;
    reason: string;
}
