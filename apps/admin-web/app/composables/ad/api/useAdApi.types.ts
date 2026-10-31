import type { BizAdType, BizAmount, BizContractFile, BizDeposit, BizDomain, BizPaymentMethod, BizPeriod, BizQuote, BizRefundRequest } from '~/composables/bizapplication/api/useBizApplicationApi.types';

/**
 * 광고 일정표(관리자) — 광고 건 · 유형별 한도 · 날짜별 남은 칸 · 운영자 등록. 서버와 주고받는 모양(서버 `AdAdminView`·`AdAdminRequest`, `AdAdvertiserService.Option`·`Revealed` — V115).
 * 장부 = 신청 건이라 ID·번호·처리 기록 코드(`BizHistoryAction`)는 신청 건과 같다.
 * 광고 건은 사업자 신청 건이 승인되면 생기거나(`APPLICATION`), 운영자가 직접 등록한다(`OPERATOR` — 늘 수동 결제, 광고 심사에서 승인).
 * 기간은 반열림 `[startAt, endAt)` — 화면은 「~10/10 00:00」 처럼 끝 시각을 적고 그 시각은 포함하지 않는다.
 */

export type AdType = BizAdType;

/**
 * 장부에 나올 수 있는 상태 — `SUBMITTED` = 운영자 등록 뒤 광고 심사 대기(04-ia-review §2 — 자리는 승인 때부터 차지한다), `CANCELED` = 승인 뒤 취소.
 * `RUNNING`·`ENDED` 는 서버가 시각으로 계산한다(저장 상태는 SCHEDULED).
 */
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

/** 광고주 — 사업자이거나 계정 없는 광고주(운영자가 만든다). `id` 는 `adv-…`(광고주)·`biz-…`(광고주와 연결되지 않은 승인 사업자) */
export interface AdAdvertiser {
    id: string;
    kind: 'BUSINESS' | 'ADVERTISER';
    name: string;
    businessId: string | null;
    maskedRegNo: string | null;
    /** 가린 연락처(없으면 가린 이메일) */
    contactMasked: string | null;
}

/** 광고주 연락처 평문(`ad.contact.reveal`) — `hideAt` 이 지나면 화면이 다시 가린다 */
export interface AdAdvertiserRevealed {
    contactName: string | null;
    contactPhone: string | null;
    email: string | null;
    hideAt: string;
}

export interface AdBookingRow {
    id: string;
    type: AdType;
    advertiser: AdAdvertiser;
    period: BizPeriod;
    status: AdBookingStatus;
    /** 승인 전이면 희망 방식 */
    paymentMethod: BizPaymentMethod;
    /** 같은 자리에 여럿이 돌 때의 비중(1~10) */
    weight: number;
    source: 'APPLICATION' | 'OPERATOR';
    /** 신청 건 번호(BA-…) */
    applicationNo: string | null;
    lastActionAt: string;
    /** 업무 도메인 — 「심사에서 열기」 주소(AD → `/ad-reviews`, HOTDEAL → `/hotdeal-reviews`) */
    domain: BizDomain;
}

export interface AdCalendar {
    summary: AdTypeSummary;
    days: AdDaySlot[];
    /** 그 달에 걸친 장부 건(이름·상태·기간 — 달력 칸에 그린다) */
    bookings: AdBookingRow[];
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
    /** 신청 건 처리 기록 코드(`BizHistoryAction`) — `UtilBizApplicationMessages.historyAction` 으로 번역한다 */
    action: string;
    actor: string;
    note: string | null;
}

/** 긴급 내림 뒤 광고주 메일 결과 — 메일 서버가 없으면 FAILED, 받을 주소가 없으면 NO_ADDRESS */
export type AdTakedownMail = 'SENT' | 'FAILED' | 'NO_ADDRESS';
export const AD_TAKEDOWN_MAILS: readonly AdTakedownMail[] = ['SENT', 'FAILED', 'NO_ADDRESS'];

/** 내림 — `cause` EMERGENCY(긴급)·SUSPENDED(사업자 정지), `mail` 광고주 메일 결과(보내지 않았으면 null) */
export interface AdTakedown {
    at: string;
    cause: 'EMERGENCY' | 'SUSPENDED';
    reason: string | null;
    by: string | null;
    mailSent: boolean;
    mail: AdTakedownMail | null;
}

/** 운영자 등록 건의 등록자 — `me` 면 지금 운영자가 등록했다(사업자 신청 건은 null) */
export interface AdRegistrant {
    id: string;
    name: string;
    me: boolean;
}

export interface AdBookingDetail extends AdBookingRow {
    /** 확정 금액 → 제안 금액 → 요금표 계산 순 */
    amount: BizAmount;
    /** 운영자 등록 때 적은 계약 금액 제안(공급가) */
    proposedSupply: number | null;
    creatives: AdCreativeVersion[];
    onlinePayment: { paidAt: string; pgTxId: string } | null;
    contracts: BizContractFile[];
    deposit: BizDeposit | null;
    takedown: AdTakedown | null;
    /** 가장 최근 환불(신청 건 상세와 같은 모양) */
    refund: BizRefundRequest | null;
    memo: string | null;
    registrant: AdRegistrant | null;
    history: AdHistory[];
}

export interface AdBookingQuery {
    type: AdType;
    status?: AdBookingStatus;
    q?: string;
    page: number;
    /** 100 까지 */
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

/** 새 광고주 — 같은 이름 400 `fields.name`, 연락처 숫자 9~11자리 `fields.contactPhone`, 이메일 형식 `fields.email` */
export interface AdAdvertiserCreateRequest {
    name: string;
    contactName: string;
    contactPhone: string;
    email: string;
    memo: string;
    /** 연결할 사업자 신원 ID(선택 — 이미 연결된 사업자면 400 `fields.businessId`) */
    businessId?: string;
}

/** 운영자가 올린 광고 이미지(`POST /images` — PNG·JPG·GIF·WEBP 2MB) */
export interface AdImage {
    id: string;
    fileName: string;
    size: number;
    mimeType: string;
    url: string;
}

/** 소재 — 배너는 `imageFileId`(올린 이미지)·`linkUrl`(https)·`altText`, 한 줄은 `text`(40자)·`linkUrl` */
export interface AdManualCreative {
    imageFileId: string | null;
    text: string | null;
    linkUrl: string;
    altText: string | null;
}

/**
 * 운영자 등록 = 신청 건 `SUBMITTED`(source=OPERATOR) — 광고 심사에서 승인 → 계약서 사본 + 입금 확인 → [예약 확정].
 * `supply` 는 계약 금액 제안(승인 때 확정, 요금표와 다르면 승인 창에서 사유). 계약서는 오프라인으로 먼저 받았을 때만 미리 붙인다(선택).
 * 광고 종류(배너 넷·한 줄)만 — 핫딜 노출 자리는 400 `fields.type`. 자리가 없으면 409 `BIZ_SLOT_FULL`(등록 때는 잠그지 않고 안내 판정).
 */
export interface AdManualCreateRequest {
    advertiserId: string;
    type: AdType;
    period: BizPeriod;
    weight: number;
    creative: AdManualCreative;
    supply: number;
    contractFileIds: string[];
    memo: string;
}

export interface AdTakedownRequest {
    reason: string;
    notifyMail: boolean;
}

/** 되돌릴 때 보상(F5) — 중단 시간만큼 끝 시각 연장(자리 없으면 409 `BIZ_SLOT_FULL`) · 일할 환불 */
export type AdRestoreCompensation = 'EXTEND' | 'PRORATE_REFUND';

export interface AdRestoreRequest {
    compensation: AdRestoreCompensation;
    reason: string;
}
