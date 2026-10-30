/**
 * 사업자 신청 건(`biz_applications`) 심사 — 광고·핫딜 통합 큐. 서버와 주고받는 모양(계약 전 목업, 00-overview.md ★-1~★-3).
 * 흐름: 신청(SUBMITTED) → 심사 승인 → 온라인 결제 대기(AWAITING_PAYMENT, 72시간) 또는 입금·계약 확인 대기(AWAITING_DEPOSIT, 기본 7일) → 예약(SCHEDULED) → 진행 → 종료.
 * 기간은 반열림 `[startAt, endAt)`(ISO) — 끝 시각은 포함하지 않는다.
 */

export type BizApplicationKind = 'AD_BANNER' | 'AD_TEXT_LINE' | 'HOTDEAL_POST' | 'HOTDEAL_PLACEMENT';
export const BIZ_APPLICATION_KINDS: readonly BizApplicationKind[] = ['AD_BANNER', 'AD_TEXT_LINE', 'HOTDEAL_POST', 'HOTDEAL_PLACEMENT'];

/**
 * 상태 — SCHEDULED 안의 「진행 중」·「종료」는 서버가 시각으로 계산해 `RUNNING`·`ENDED` 로 내려 준다(저장 상태는 SCHEDULED).
 */
export type BizApplicationStatus = 'SUBMITTED' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'SCHEDULED' | 'RUNNING' | 'ENDED' | 'TAKEN_DOWN' | 'REJECTED' | 'CANCELED' | 'EXPIRED';

/** 화면 큐(탭) — 서버가 상태·기한·환불 요청으로 가른다 */
export type BizApplicationQueue = 'REVIEW' | 'PAYMENT' | 'DEPOSIT' | 'DEPOSIT_OVERDUE' | 'LIVE' | 'REFUND' | 'CLOSED';
export const BIZ_APPLICATION_QUEUES: readonly BizApplicationQueue[] = ['REVIEW', 'PAYMENT', 'DEPOSIT', 'DEPOSIT_OVERDUE', 'LIVE', 'REFUND', 'CLOSED'];

export type BizPaymentMethod = 'ONLINE' | 'MANUAL';

/** 광고 유형(노출 자리 단위 — 한도·달력의 단위). 광고 일정표와 같은 값 */
export type BizAdType = 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER' | 'TEXT_LINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';
export const BIZ_AD_TYPES: readonly BizAdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY'];

export interface BizPeriod {
    startAt: string;
    endAt: string;
}

export type BizApplicationContent =
    | { type: 'AD_BANNER'; imageUrl: string; linkUrl: string; altText: string }
    | { type: 'AD_TEXT_LINE'; text: string; linkUrl: string }
    | { type: 'HOTDEAL_POST'; title: string; body: string; mallName: string; dealUrl: string; price: number; originalPrice: number }
    | { type: 'HOTDEAL_PLACEMENT'; postId: string; postTitle: string; price: number; originalPrice: number; mallName: string; categoryName: string | null };

/** 요금표 계산 — 하루 단가 × 일수(핫딜 글은 건당) + 부가세 10% */
export interface BizQuote {
    unitPrice: number;
    units: number;
    unit: 'DAY' | 'POST';
    supply: number;
    vat: number;
    total: number;
}

export interface BizAmount {
    supply: number;
    vat: number;
    total: number;
}

/** 계약서 사본 — 지우지 않는다. 새 파일로 바꾸면 옛 파일은 `replacedAt` 이 찍힌 채 남는다 */
export interface BizContractFile {
    id: string;
    fileName: string;
    mimeType: string;
    size: number;
    uploadedAt: string;
    uploadedBy: string;
    replacedAt: string | null;
    replacedBy: string | null;
}

/** 입금 확인 기록(감사 `BIZ_DEPOSIT_CONFIRM`) */
export interface BizDeposit {
    amount: number;
    /** 입금일 `YYYY-MM-DD` */
    depositedOn: string;
    depositorName: string;
    /** 입금액이 계약 금액과 다를 때 사유 */
    mismatchReason: string | null;
    confirmedBy: string;
    confirmedAt: string;
}

export interface BizDepositRequest {
    amount: number;
    depositedOn: string;
    depositorName: string;
    mismatchReason?: string;
}

export interface BizRefundRequest {
    requestedAt: string;
    reason: string;
    paidAmount: number;
    /** 일할 계산 제안 — 남은 기간 / 전체 기간 × 결제 금액 */
    proposedAmount: number;
    usedDays: number;
    totalDays: number;
    status: 'PENDING' | 'APPROVED' | 'DENIED';
}

export interface BizApplicationHistory {
    at: string;
    action: string;
    actor: string;
    note: string | null;
}

export interface BizApplicationRow {
    id: string;
    no: string;
    kind: BizApplicationKind;
    adType: BizAdType | null;
    status: BizApplicationStatus;
    business: { id: string; name: string; maskedRegNo: string };
    submittedAt: string;
    period: BizPeriod;
    /** 사업자가 고른 결제 방식 */
    preferredMethod: BizPaymentMethod;
    /** 확정 금액(승인 뒤) 또는 요금표 계산값(승인 전) */
    total: number;
    /** 결제·입금 기한(승인 뒤) */
    deadlineAt: string | null;
    hasRefundRequest: boolean;
}

export interface BizApplicationDetail extends BizApplicationRow {
    applicant: string;
    content: BizApplicationContent;
    memo: string | null;
    quote: BizQuote;
    /** 이 기간·유형의 남은 자리(승인 때 다시 판정) */
    remainingSlots: number;
    slotLimit: number;
    approval: { method: BizPaymentMethod; amount: BizAmount; adjustReason: string | null; approvedAt: string; approvedBy: string } | null;
    onlinePayment: { paidAt: string; pgTxId: string } | null;
    contracts: BizContractFile[];
    deposit: BizDeposit | null;
    refund: BizRefundRequest | null;
    rejection: { code: BizRejectCode; text: string } | null;
    policyAgreedVersion: string;
    history: BizApplicationHistory[];
}

export interface BizApplicationQuery {
    queue: BizApplicationQueue;
    kind?: BizApplicationKind;
    q?: string;
}

export interface BizApplicationList {
    items: BizApplicationRow[];
    counts: Record<BizApplicationQueue, number>;
}

/** 기간 판정 — 요금표 계산 + 남은 자리 */
export interface BizAvailability {
    quote: BizQuote;
    remainingSlots: number;
    slotLimit: number;
}

export interface BizApproveRequest {
    method: BizPaymentMethod;
    /** 공급가(조정 가능 — 부가세는 서버가 10% 계산) */
    supply: number;
    adjustReason?: string;
    /** 결제 기한(온라인) 또는 입금 기한(수동) */
    deadlineAt: string;
    period: BizPeriod;
}

export type BizRejectCode = 'CREATIVE_SPEC' | 'POLICY_VIOLATION' | 'LINK_BROKEN' | 'PERIOD_UNAVAILABLE' | 'FALSE_PRICE' | 'OTHER';
export const BIZ_REJECT_CODES: readonly BizRejectCode[] = ['CREATIVE_SPEC', 'POLICY_VIOLATION', 'LINK_BROKEN', 'PERIOD_UNAVAILABLE', 'FALSE_PRICE', 'OTHER'];
