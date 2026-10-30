/**
 * 사업자 센터 API 의 주고받는 모양 — 목업 단계(서버 없음). 요구사항 정본 `docs/plans/biz-ad-hotdeal/00-overview.md` 「★ 확정」.
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 날짜(`YYYY-MM-DD`)는 서비스 달력 날짜 글자, 시각은 ISO-8601 순간이다.
 */
import type { BusinessUserStatus } from '~/composables/auth/api/useAuthApi.types';
import type { AddressValue } from '~/components/address/AddressInput.types';
import type { PaymentCheckout, PaymentFakeCheckoutRequest } from '~/composables/payment/api/usePaymentApi.types';

/** 목업 결제 완료 요청 — 가짜 결제창에서 고른 결과(서버가 붙으면 `payment` 모듈의 완료 API 가 대신한다) */
export type BizPaymentCompleteRequest = PaymentFakeCheckoutRequest;

/* ── 사업자 신원 ─────────────────────────────────────────── */

/** 사업자 신원 상태 — 로그인 정보의 `businessStatus` 와 같은 값 */
export type BusinessProfileStatus = BusinessUserStatus;

/** 반려 사유 코드(운영자가 고른다 — 글은 따로) */
export type BusinessRejectCode = 'DOCUMENT_UNREADABLE' | 'INFO_MISMATCH' | 'CLOSED_BUSINESS' | 'POLICY' | 'OTHER';

/** 첨부 파일(사업자등록증 사본·광고 이미지) — 올리기 API 가 준다 */
export interface BusinessFile {
    fileId: string;
    name: string;
    /** 바이트 */
    size: number;
    contentType: string;
    /** 이미지면 미리보기 주소(목업은 `blob:` 주소) */
    previewUrl: string | null;
}

/** `GET /api/business/me` — 내 사업자 신원. 신청한 적이 없으면 `status: NONE` 이고 나머지 칸은 null */
export interface BusinessProfile {
    status: BusinessProfileStatus;
    companyName: string | null;
    /** 가린 사업자등록번호(`123-45-*****`) — 평문은 내려 주지 않는다 */
    bizNoMasked: string | null;
    ownerName: string | null;
    /** 가린 연락처(`010-****-5678`) */
    phoneMasked: string | null;
    email: string | null;
    address: AddressValue | null;
    certificate: Pick<BusinessFile, 'fileId' | 'name' | 'size'> | null;
    submittedAt: string | null;
    reviewedAt: string | null;
    rejectCode: BusinessRejectCode | null;
    rejectReason: string | null;
    suspendReason: string | null;
    /** 승인 뒤 상호·번호를 고쳐 다시 심사 중(F7) — 이미 예약·진행 중인 건은 그대로, 새 신청만 막힌다 */
    reReview: boolean;
}

/** `POST /api/business/me`(신청) · `PUT /api/business/me`(수정) */
export interface BusinessApplyRequest {
    companyName: string;
    /** 숫자 10자리(하이픈 없이) — 수정 때 바꾸지 않으면 null */
    bizNo: string | null;
    ownerName: string;
    /** 수정 때 바꾸지 않으면 null */
    phone: string | null;
    email: string;
    address: AddressValue;
    certificateFileId: string;
    /** 동의한 약관 버전 — 서버가 저장한다(H4) */
    agreedTermsVersion: string;
    agreedPrivacyVersion: string;
}

/* ── 사업자 신청 건(광고·핫딜) ─────────────────────────────── */

/** 신청 건 종류(★-1) */
export type BizApplicationKind = 'AD_BANNER' | 'AD_TEXT_LINE' | 'HOTDEAL_POST' | 'HOTDEAL_PLACEMENT';

/** 신청 건 상태(★-3). 「예약 → 진행 → 종료」는 `SCHEDULED` 안에서 시각으로 계산한 `phase` 다 */
export type BizApplicationStatus = 'SUBMITTED' | 'REJECTED' | 'CANCELED' | 'AWAITING_PAYMENT' | 'EXPIRED' | 'AWAITING_DEPOSIT' | 'SCHEDULED' | 'TAKEN_DOWN';

/** `SCHEDULED` 의 시각 계산 단계 — 그 밖의 상태는 null */
export type BizSchedulePhase = 'UPCOMING' | 'RUNNING' | 'ENDED';

/** 결제 방식(★-2) */
export type BizPaymentMethod = 'ONLINE' | 'MANUAL';

/** 광고 종류 — 배너 4유형 + 한 줄 */
export type AdType = 'WIDE' | 'STRIP' | 'SQUARE' | 'SMALL' | 'TEXT_LINE';

/** 핫딜 노출 자리 */
export type DealPlacement = 'NONE' | 'HOME_TOP' | 'CATEGORY_TOP';

/** 금액(원) — 공급가 + 부가세 10% = 합계 */
export interface BizAmount {
    supply: number;
    vat: number;
    total: number;
}

/** 광고 소재 — 배너는 이미지·링크·대체 글, 한 줄은 문구·링크 */
export interface BizCreative {
    /** 심사 승인 때 고정되는 소재 버전(A2) */
    version: number;
    imageUrl: string | null;
    imageName: string | null;
    linkUrl: string;
    altText: string | null;
    text: string | null;
}

/** 사업자 핫딜 정보 */
export interface BizDeal {
    linkUrl: string;
    shopName: string;
    listPrice: number | null;
    dealPrice: number;
    /** 0 = 무료, null = 모름 */
    shippingFee: number | null;
    /** 마감 날짜(없으면 null) */
    endsOn: string | null;
    stock: 'PLENTY' | 'LIMITED' | 'UNKNOWN';
    body: string;
    placement: DealPlacement;
}

/** 입금 계좌·계약서(수동 결제) */
export interface BizDepositInfo {
    bankName: string;
    accountNo: string;
    accountHolder: string;
    /** 입금자명 — 이 이름으로 넣어 달라고 안내한다 */
    depositorName: string;
    /** 계약서 사본 — 운영팀이 올린다(F3). `WAITING` 아직 · `RECEIVED` 받음 */
    contract: 'WAITING' | 'RECEIVED';
    depositConfirmed: boolean;
}

/** 취소·환불 요청(H1) */
export interface BizRefundRequest {
    /** `FULL_AUTO` 시작 전 전액 자동 · `PRORATED_REVIEW` 진행 중 일할(운영팀 확인) */
    type: 'FULL_AUTO' | 'PRORATED_REVIEW';
    reason: string;
    requestedAt: string;
    status: 'REQUESTED' | 'REFUNDED';
    refundAmount: number | null;
}

/** 처리 기록 한 줄 */
export interface BizHistoryEntry {
    at: string;
    actor: 'ME' | 'OPERATOR' | 'SYSTEM';
    action:
        | 'SUBMITTED'
        | 'CREATIVE_CHANGED'
        | 'APPROVED_ONLINE'
        | 'APPROVED_MANUAL'
        | 'REJECTED'
        | 'CANCELED'
        | 'PAID'
        | 'EXPIRED'
        | 'CONTRACT_RECEIVED'
        | 'DEPOSIT_CONFIRMED'
        | 'SCHEDULED'
        | 'STARTED'
        | 'ENDED'
        | 'TAKEN_DOWN'
        | 'REFUND_REQUESTED'
        | 'REFUNDED';
    /** 사유·메모(서버 문구 그대로) */
    note: string | null;
}

/** 목록 한 줄 */
export interface BizApplicationSummary {
    id: string;
    kind: BizApplicationKind;
    title: string;
    adType: AdType | null;
    placement: DealPlacement | null;
    startDate: string;
    /** 끝 날짜(포함) */
    endDate: string;
    status: BizApplicationStatus;
    phase: BizSchedulePhase | null;
    paymentMethod: BizPaymentMethod;
    /** 확정 금액이 있으면 그것, 없으면 예상 금액 */
    amount: BizAmount;
    amountConfirmed: boolean;
    /** 결제 기한(`AWAITING_PAYMENT`) */
    paymentDueAt: string | null;
    /** 입금 기한(`AWAITING_DEPOSIT`) */
    depositDueAt: string | null;
    submittedAt: string;
    refundRequested: boolean;
}

/** `GET /api/business/applications/{id}` */
export interface BizApplicationDetail extends BizApplicationSummary {
    estimate: BizAmount;
    /** 승인 때 확정한 금액(조정했으면 사유와 함께) */
    confirmed: BizAmount | null;
    adjustReason: string | null;
    creative: BizCreative | null;
    deal: BizDeal | null;
    rejectCode: BusinessRejectCode | null;
    rejectReason: string | null;
    takedownReason: string | null;
    deposit: BizDepositInfo | null;
    refund: BizRefundRequest | null;
    history: BizHistoryEntry[];
}

/** 목록 거르기(화면 SelectButton) — 상태 묶음 */
export type BizApplicationBucket = 'ALL' | 'REVIEW' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'UPCOMING' | 'RUNNING' | 'ENDED' | 'CLOSED';

/** `GET /api/business/applications?bucket=` */
export interface BizApplicationPage {
    items: BizApplicationSummary[];
    total: number;
    /** 묶음별 건수(SelectButton 옆 숫자) */
    counts: Record<BizApplicationBucket, number>;
}

/* ── 광고 신청 ─────────────────────────────────────────── */

/** `GET /api/business/ad-products` — 광고 종류 하나 */
export interface AdProduct {
    type: AdType;
    /** 하루 단가(공급가, 원) */
    dailyPrice: number;
    /** 한 번에 신청할 수 있는 최대 일수 */
    maxDays: number;
    /** 며칠 뒤까지 미리 신청할 수 있나 */
    maxLeadDays: number;
    /** 동시 노출 한도 */
    capacity: number;
    /** 앞으로 30일 중 빈 날 수(요약) */
    openDays: number;
    /** 신청 받기 꺼짐 — 흐리게 「지금은 받지 않아요」 */
    accepting: boolean;
}

/** 남은 칸을 셀 자리 — 광고 종류 또는 핫딜 노출 자리(광고 건 한도 엔진 하나 — X8) */
export type AvailabilitySlot = AdType | Exclude<DealPlacement, 'NONE'>;

/** `GET /api/business/availability?slot=&from=&to=` — 날짜별 남은 칸 */
export interface AvailabilityDay {
    date: string;
    remaining: number;
    capacity: number;
}

/** `POST /api/business/applications/ads` */
export interface AdApplyRequest {
    adType: AdType;
    startDate: string;
    endDate: string;
    imageFileId: string | null;
    linkUrl: string;
    altText: string | null;
    text: string | null;
    paymentMethod: BizPaymentMethod;
    agreedAdPolicyVersion: string;
    agreedRefundPolicyVersion: string;
}

/* ── 사업자 핫딜 신청 ─────────────────────────────────────── */

/** `GET /api/business/deal-link-check?url=` */
export interface DealLinkCheck {
    host: string;
    shopName: string | null;
    /** 올릴 수 없는 쇼핑몰(단축 URL·차단 도메인) */
    blocked: boolean;
    blockedReason: string | null;
    /** 제휴 링크라 「제휴」 표시가 자동으로 켜진다 */
    affiliate: boolean;
}

/** `GET /api/business/deal-products` — 사업자 핫딜 요금(게시 1건 + 노출 자리별 하루 단가) */
export interface DealProduct {
    /** 게시 1건 공급가(원) */
    postPrice: number;
    placements: { placement: Exclude<DealPlacement, 'NONE'>; dailyPrice: number; capacity: number; maxDays: number }[];
}

/** `POST /api/business/applications/deals` — 노출을 고르면 같은 신청 건에 묶는다(목업 가정) */
export interface DealApplyRequest {
    title: string;
    deal: BizDeal;
    placementStartDate: string | null;
    placementEndDate: string | null;
    paymentMethod: BizPaymentMethod;
    agreedAdPolicyVersion: string;
    agreedRefundPolicyVersion: string;
}

/** 신청 접수 결과 */
export interface BizApplicationCreated {
    id: string;
}

/* ── 결제(온라인) ───────────────────────────────────────── */

/** `POST /api/business/applications/{id}/payment` — 결제 시작(결제창 정보). `payment` 모듈 한 길(`PaymentOrderType.BIZ_APPLICATION`) */
export interface BizPaymentStart {
    orderType: 'BIZ_APPLICATION';
    orderRefId: string;
    expiresAt: string;
    checkout: PaymentCheckout;
}

/** 목업 운영자 처리(개발용) — 서버가 붙으면 관리자 화면이 한다 */
export type BizMockOperatorAction = 'APPROVE_ONLINE' | 'APPROVE_MANUAL' | 'REJECT' | 'CONTRACT_AND_DEPOSIT' | 'START' | 'END' | 'NEAR_DUE';
