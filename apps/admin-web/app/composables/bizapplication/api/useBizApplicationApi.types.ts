/**
 * 사업자 신청 건(`biz_applications`) 심사 — 서버는 엔진 하나, 화면·권한은 도메인(광고 / 핫딜)별. 서버와 주고받는 모양(서버 `BizAdminView`·`BizAdminRequest`, V112).
 * 흐름: 신청(SUBMITTED) → 심사 승인 → 온라인 결제 대기(AWAITING_PAYMENT, 72시간) 또는 입금·계약 확인 대기(AWAITING_DEPOSIT, 기본 7일) → 예약(SCHEDULED) → 진행 → 종료.
 * 기간은 반열림 `[startAt, endAt)`(ISO) — 끝 시각은 포함하지 않는다.
 */

export type BizApplicationKind = 'AD_BANNER' | 'AD_TEXT_LINE' | 'HOTDEAL_POST' | 'HOTDEAL_PLACEMENT';
export const BIZ_APPLICATION_KINDS: readonly BizApplicationKind[] = ['AD_BANNER', 'AD_TEXT_LINE', 'HOTDEAL_POST', 'HOTDEAL_PLACEMENT'];

/** 업무 도메인 — 종류(kind)를 화면·권한 단위로 가른다(서버 `BizDomain`, 04-ia-review S1) */
export type BizDomain = 'AD' | 'HOTDEAL';
export const BIZ_DOMAINS: readonly BizDomain[] = ['AD', 'HOTDEAL'];
/** 도메인 → 그 도메인의 종류(서버 `BizApplicationKind.domain()` 과 같게) */
export const BIZ_DOMAIN_KINDS: Readonly<Record<BizDomain, readonly BizApplicationKind[]>> = {
    AD: ['AD_BANNER', 'AD_TEXT_LINE'],
    HOTDEAL: ['HOTDEAL_POST', 'HOTDEAL_PLACEMENT'],
};

/**
 * 상태 — SCHEDULED 안의 「진행 중」·「종료」는 서버가 시각으로 계산해 `RUNNING`·`ENDED` 로 내려 준다(저장 상태는 SCHEDULED).
 */
export type BizApplicationStatus = 'SUBMITTED' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'SCHEDULED' | 'RUNNING' | 'ENDED' | 'TAKEN_DOWN' | 'REJECTED' | 'CANCELED' | 'EXPIRED';

/** 화면 큐(탭) — 서버가 상태·기한·환불 요청으로 가른다 */
export type BizApplicationQueue = 'REVIEW' | 'PAYMENT' | 'DEPOSIT' | 'DEPOSIT_OVERDUE' | 'LIVE' | 'REFUND' | 'CLOSED';
export const BIZ_APPLICATION_QUEUES: readonly BizApplicationQueue[] = ['REVIEW', 'PAYMENT', 'DEPOSIT', 'DEPOSIT_OVERDUE', 'LIVE', 'REFUND', 'CLOSED'];

export type BizPaymentMethod = 'ONLINE' | 'MANUAL';

/** 광고 유형(노출 자리 단위 — 한도·달력의 단위). 광고 일정표와 같은 값(서버 `BizProduct`) */
export type BizAdType = 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER' | 'TEXT_LINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';
export const BIZ_AD_TYPES: readonly BizAdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY'];

export interface BizPeriod {
    startAt: string;
    endAt: string;
}

export type BizApplicationContent =
    | { type: 'AD_BANNER'; imageUrl: string; linkUrl: string; altText: string }
    | { type: 'AD_TEXT_LINE'; text: string; linkUrl: string }
    | {
          type: 'HOTDEAL_POST';
          title: string;
          body: string;
          mallName: string;
          dealUrl: string;
          price: number;
          /** 정가 — 사업자가 비워 두면 null */
          originalPrice: number | null;
          shippingFee?: number | null;
          /** 딜 끝 날짜 `YYYY-MM-DD` */
          endsOn?: string | null;
          stock?: 'PLENTY' | 'LIMITED' | 'UNKNOWN' | null;
          placement?: 'NONE' | 'HOME_TOP' | 'CATEGORY_TOP' | null;
          affiliate?: boolean | null;
      }
    | { type: 'HOTDEAL_PLACEMENT'; postId: string; postTitle: string; price: number; originalPrice: number | null; mallName: string; categoryName: string | null };

/** 요금표 한 줄 — 핫딜 글 + 노출이면 두 줄. `product` 는 자리 상품 이름(`BizAdType`) 또는 `HOTDEAL_POST` */
export interface BizQuoteLine {
    product: BizAdType | 'HOTDEAL_POST';
    unit: 'DAY' | 'POST';
    unitPrice: number;
    units: number;
    supply: number;
}

/** 요금표 계산 — 하루 단가 × 일수(핫딜 글은 건당) + 부가세 10%. 단가·단위는 자리를 차지하는 줄(없으면 첫 줄)의 것 */
export interface BizQuote {
    unitPrice: number;
    units: number;
    unit: 'DAY' | 'POST';
    supply: number;
    vat: number;
    total: number;
    /** 줄 전부(광고 일정표 목업의 계산값에는 없다) */
    lines?: BizQuoteLine[];
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

/** 계약서 사본 열람 — `data:` 주소(사업자등록증 열람과 같은 모양). 열람 기록이 남는다 */
export interface BizContractView {
    url: string;
    mimeType: string;
    fileName: string;
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

/** 환불 심사 상태 — 열린 환불 = REQUESTED(「환불 심사」 큐) */
export type BizRefundStatus = 'REQUESTED' | 'APPROVED' | 'DENIED';
/** 환불 종류 — 시작 전 전액 자동 · 진행 중 일할 심사 · 사업자 정지로 내림(일할) */
export type BizRefundType = 'FULL_AUTO' | 'PRORATED' | 'SUSPEND_PRORATED';

export interface BizRefundRequest {
    requestedAt: string;
    reason: string;
    paidAmount: number;
    /** 일할 계산 제안 — 남은 기간 / 전체 기간 × 결제 금액 */
    proposedAmount: number;
    usedDays: number;
    totalDays: number;
    status: BizRefundStatus;
    type: BizRefundType;
    /** 돌려주는 길 — 온라인(PG 취소) · 수동(운영자 송금) */
    method: BizPaymentMethod;
    /** 승인 금액(승인 뒤) */
    approvedAmount: number | null;
    /** 승인·거절 사유 */
    decisionReason: string | null;
    /** 수동 송금 — PENDING(송금 대기) · DONE. 온라인이면 null */
    transferStatus: 'PENDING' | 'DONE' | null;
    transferredAt: string | null;
    completedAt: string | null;
}

/** 처리 기록 코드(서버 `BizEventAction`) — 화면이 `bizApplication.action.<코드>` 로 번역한다 */
export const BIZ_HISTORY_ACTIONS = [
    'SUBMITTED',
    'CREATIVE_CHANGED',
    'APPROVED_ONLINE',
    'APPROVED_MANUAL',
    'REJECTED',
    'CANCELED',
    'PAID',
    'EXPIRED',
    'MANUAL_SWITCH',
    'CONTRACT_RECEIVED',
    'CONTRACT_REPLACED',
    'CONTRACT_VIEWED',
    'DEPOSIT_CONFIRMED',
    'DEPOSIT_DEADLINE',
    'SCHEDULED',
    'TAKEN_DOWN',
    'RESTORED',
    'REFUND_REQUESTED',
    'REFUND_APPROVED',
    'REFUND_DENIED',
    'REFUNDED',
    'REFUND_TRANSFERRED',
    'AUTO_CANCELED',
    'FORFEITED',
] as const;
export type BizHistoryAction = (typeof BIZ_HISTORY_ACTIONS)[number];

export interface BizApplicationHistory {
    at: string;
    /** 코드(`BizHistoryAction`) — 모르는 코드는 그대로 보인다 */
    action: string;
    /** 운영자 이름 · 회원 닉네임 · 「시스템」(서버가 채운 표시 이름) */
    actor: string;
    /** 코드별 덧붙임(사유 코드·금액·`PERIOD_MOVED` 등) — `UtilBizApplicationMessages.historyNote` 가 읽는다 */
    note: string | null;
}

/** 신청 건을 만든 쪽 — 사업자 신청 · 운영자 직접 등록 */
export type BizSource = 'BUSINESS' | 'OPERATOR';

/** 내림 — 사업자 정지 · 긴급 내림 */
export interface BizTakedown {
    at: string;
    cause: 'SUSPENDED' | 'EMERGENCY';
    text: string | null;
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
    /** 열린 환불(REQUESTED)이 있다 */
    hasRefundRequest: boolean;
}

export interface BizApplicationDetail extends BizApplicationRow {
    source: BizSource;
    applicant: string;
    content: BizApplicationContent;
    memo: string | null;
    quote: BizQuote;
    /** 이 기간·유형의 남은 자리(자기 제외 — 승인 때 다시 판정). 한도 없는 핫딜 글은 1/1 */
    remainingSlots: number;
    slotLimit: number;
    approval: { method: BizPaymentMethod; amount: BizAmount; adjustReason: string | null; approvedAt: string; approvedBy: string } | null;
    onlinePayment: { paidAt: string; pgTxId: string } | null;
    contracts: BizContractFile[];
    deposit: BizDeposit | null;
    refund: BizRefundRequest | null;
    rejection: { code: BizRejectCode; text: string } | null;
    takedown: BizTakedown | null;
    /** 「광고 정책 v1 · 환불 규정 v1」 — 동의 기록이 없으면(운영자 등록 건) null */
    policyAgreedVersion: string | null;
    history: BizApplicationHistory[];
}

export interface BizApplicationQuery {
    /** 필수 — 그 도메인의 건·건수만 온다(없으면 400 `fields.domain`) */
    domain: BizDomain;
    queue: BizApplicationQueue;
    /** 그 도메인의 종류만(아니면 400 `fields.kind`) */
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
    /** 결제 기한(온라인 — 시작 시각 이전) 또는 입금 기한(수동) */
    deadlineAt: string;
    period: BizPeriod;
}

/** 수동 환불 송금 완료 표시 */
export interface BizTransferRequest {
    note?: string;
}

export type BizRejectCode = 'CREATIVE_SPEC' | 'POLICY_VIOLATION' | 'LINK_BROKEN' | 'PERIOD_UNAVAILABLE' | 'FALSE_PRICE' | 'OTHER';
export const BIZ_REJECT_CODES: readonly BizRejectCode[] = ['CREATIVE_SPEC', 'POLICY_VIOLATION', 'LINK_BROKEN', 'PERIOD_UNAVAILABLE', 'FALSE_PRICE', 'OTHER'];
