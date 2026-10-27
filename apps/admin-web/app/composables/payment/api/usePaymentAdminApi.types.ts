/**
 * 결제 관리자 API 의 요청·응답 모양(계약 `docs/features/commerce-payment-contract.md` — 2a-1 화면 목업이 제안하는 모양, 2a-3 이 서버를 이 모양으로 만든다).
 * 금액은 원(KRW) 정수, 시각은 ISO 순간, 날짜(`day`)는 KST 달력 날짜 `YYYY-MM-DD`, ID 는 Snowflake 글자.
 * 회원은 번호·닉네임만 내려온다(이메일 없음 — 세션 정리 결정 17).
 */

/** 결제 주문의 대상 종류 — 충전(3단계)·공구(2b)·시험 주문(local·dev) */
export type PaymentOrderType = 'POINT_CHARGE' | 'GROUP_BUY' | 'PAYMENT_TEST';
export type PaymentStatus = 'READY' | 'PAID' | 'PARTIAL_CANCELED' | 'CANCELED' | 'EXPIRED';
/** 이상 결제 — 받아들이지 않은 결제(자동 취소 대상) */
export type PaymentAnomaly = 'AMOUNT_MISMATCH' | 'DOMAIN_REJECTED' | 'LATE_PAYMENT';
/** 결제를 확정한 길 */
export type PaymentConfirmedVia = 'COMPLETE_API' | 'WEBHOOK' | 'READY_CLEANUP' | 'ADMIN_RECHECK';
export type PaymentCancelKind = 'USER_CANCEL' | 'REFUND' | 'AUTO_REJECT' | 'ADMIN' | 'CHARGE_REFUND';
export type PaymentCancelStatus = 'PENDING' | 'SENDING' | 'SUCCEEDED' | 'GAVE_UP' | 'MANUAL';
export type PaymentPgCancelStatus = 'REQUESTED' | 'SUCCEEDED' | 'FAILED';
export type PaymentEventKind = 'CREATED' | 'PAID' | 'ANOMALY' | 'EXPIRED' | 'CANCEL_REQUESTED' | 'CANCEL_SUCCEEDED' | 'CANCEL_GAVE_UP' | 'CANCEL_MANUAL' | 'EXTERNAL_CANCEL' | 'RECHECKED';
export type PaymentWebhookResult = 'PROCESSED' | 'IGNORED' | 'REJECTED' | 'RETRY';
export type ReconcileIssueType = 'PG_PAID_LOCAL_UNPAID' | 'LOCAL_PAID_PG_MISSING' | 'AMOUNT_MISMATCH' | 'CANCEL_MISMATCH' | 'PG_ONLY';
export type ReconcileIssueStatus = 'OPEN' | 'RESOLVED';
/** 날짜 하나의 대사 상태 — 아직 안 함 · 도는 중 · 끝(불일치 없음) · 끝(불일치 있음) · 실패(포트원 장애 등) */
export type ReconcileDayStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'MISMATCH' | 'FAILED';

export const PAYMENT_ORDER_TYPES: readonly PaymentOrderType[] = ['POINT_CHARGE', 'GROUP_BUY', 'PAYMENT_TEST'];
export const PAYMENT_STATUSES: readonly PaymentStatus[] = ['READY', 'PAID', 'PARTIAL_CANCELED', 'CANCELED', 'EXPIRED'];
export const PAYMENT_ANOMALIES: readonly PaymentAnomaly[] = ['AMOUNT_MISMATCH', 'DOMAIN_REJECTED', 'LATE_PAYMENT'];
export const PAYMENT_WEBHOOK_RESULTS: readonly PaymentWebhookResult[] = ['PROCESSED', 'IGNORED', 'REJECTED', 'RETRY'];
export const RECONCILE_ISSUE_TYPES: readonly ReconcileIssueType[] = ['PG_PAID_LOCAL_UNPAID', 'LOCAL_PAID_PG_MISSING', 'AMOUNT_MISMATCH', 'CANCEL_MISMATCH', 'PG_ONLY'];

/** 회원 — 번호·닉네임만. 탈퇴했으면 `withdrawn` (닉네임은 서버가 「탈퇴 회원」으로 준다) */
export interface PaymentMember {
    id: string | null;
    nickname: string;
    withdrawn: boolean;
}

/** 목록 한 줄(`GET /api/admin/payments`) */
export interface PaymentOrderRow {
    id: string;
    paymentId: string;
    member: PaymentMember;
    orderType: PaymentOrderType;
    orderRefId: string;
    orderName: string;
    amount: number;
    paidAmount: number | null;
    canceledAmount: number;
    status: PaymentStatus;
    anomaly: PaymentAnomaly | null;
    /** 포트원이 준 결제 수단 이름(카드·간편결제 등 — 서버가 표시 글자로) */
    payMethod: string | null;
    paidAt: string | null;
    expiresAt: string;
    createdAt: string;
    /** 끝나지 않은 취소(PENDING·SENDING·GAVE_UP)가 있는가 */
    cancelPending: boolean;
}

export interface PaymentOrderQuery {
    status?: PaymentStatus;
    anomaly?: PaymentAnomaly | 'ANY';
    orderType?: PaymentOrderType;
    /** 만든 시각 기준 [from, to) */
    from?: string;
    to?: string;
    /** 결제 번호(`pay_…`) 또는 회원 번호 */
    q?: string;
    page: number;
    size: number;
}

export interface PaymentPage<T> {
    items: T[];
    total: number;
}

/** 이력 한 줄(`payment_events`) — `detail` 은 서버가 요청 언어로 푼 한 줄(개인정보 없음) */
export interface PaymentEventView {
    id: string;
    kind: PaymentEventKind;
    detail: string | null;
    /** 웹훅에서 온 이벤트면 `webhook-id` */
    providerEventId: string | null;
    createdAt: string;
}

/** 취소 대기열 한 줄(`payment_cancels`) — 마지막 오류는 비밀값을 가린 서버 글자 */
export interface PaymentCancelView {
    id: string;
    amount: number;
    kind: PaymentCancelKind;
    status: PaymentCancelStatus;
    pgStatus: PaymentPgCancelStatus | null;
    attempts: number;
    nextAttemptAt: string | null;
    lastError: string | null;
    /** 내부 사유(회원에게 보이지 않는다) */
    reason: string;
    requestedBy: string | null;
    resolvedBy: string | null;
    resolveNote: string | null;
    createdAt: string;
    completedAt: string | null;
}

/** 웹훅 한 줄(`payment_webhook_logs`) — 본문은 저장하지 않는다 */
export interface PaymentWebhookRow {
    id: string;
    webhookId: string | null;
    eventType: string | null;
    paymentId: string | null;
    /** 우리 결제 주문이면 그 번호(서랍 열기) */
    paymentOrderId: string | null;
    result: PaymentWebhookResult;
    httpStatus: number;
    errorCode: string | null;
    receivedAt: string;
}

export interface PaymentWebhookQuery {
    result?: PaymentWebhookResult;
    from?: string;
    to?: string;
    /** 결제 번호 */
    q?: string;
    page: number;
    size: number;
}

/** 상세(`GET /api/admin/payments/{id}`) — 열면 서버가 `PAYMENT_ORDER_VIEW` 감사(개인정보 열람)를 남긴다 */
export interface PaymentOrderDetail extends PaymentOrderRow {
    currency: 'KRW';
    pgProvider: string | null;
    pgTxId: string | null;
    receiptUrl: string | null;
    confirmedVia: PaymentConfirmedVia | null;
    updatedAt: string;
    events: PaymentEventView[];
    cancels: PaymentCancelView[];
    webhooks: PaymentWebhookRow[];
    /** 이 결제에 걸린 열린 대사 이슈 수 */
    openIssueCount: number;
}

/** 「포트원에서 다시 확인」 결과 — 무엇이 바뀌었는지 + 새 상세 */
export interface PaymentRecheckResult {
    /** CONFIRMED = 이번에 확정됨, UNCHANGED = 포트원과 같다, PG_NOT_PAID = 포트원도 아직 결제 전 */
    outcome: 'CONFIRMED' | 'UNCHANGED' | 'PG_NOT_PAID';
    detail: PaymentOrderDetail;
}

/** 결제 이상 한 줄(`GET /api/admin/payment-anomalies`) — 이상 결제(anomaly)와 최종 실패한 취소(GAVE_UP)를 같이 본다 */
export interface PaymentAnomalyRow {
    /** 줄 번호 — 이상 결제는 `ORDER:{주문 ID}`, 취소 실패는 `CANCEL:{취소 ID}` */
    key: string;
    kind: PaymentAnomaly | 'CANCEL_GAVE_UP';
    order: PaymentOrderRow;
    /** 그 결제의 자동 취소(이상 결제)·최종 실패 취소 줄 */
    cancel: PaymentCancelView | null;
    detectedAt: string;
    /** 처리됨 — 취소가 SUCCEEDED·MANUAL 이면 true */
    resolved: boolean;
}

export interface PaymentAnomalyQuery {
    kind?: PaymentAnomalyRow['kind'];
    /** 기본 false — 처리 안 된 것만 */
    includeResolved?: boolean;
    from?: string;
    to?: string;
    page: number;
    size: number;
}

/** 결제 이상 목록 — 쪽 + 처리 안 된 것 전체 수(거르기와 무관, 제목 옆 숫자) */
export interface PaymentAnomalyPage extends PaymentPage<PaymentAnomalyRow> {
    openCount: number;
}

/** 대사 날짜 한 줄(`payment_reconcile_days`) */
export interface ReconcileDayRow {
    day: string;
    status: ReconcileDayStatus;
    checkedCount: number;
    issueCount: number;
    openIssueCount: number;
    finishedAt: string | null;
}

export interface ReconcileIssueRow {
    id: string;
    type: ReconcileIssueType;
    paymentId: string;
    paymentOrderId: string | null;
    detectedDay: string;
    /** 서버가 요청 언어로 푼 한 줄(포트원 값 ↔ 우리 값) */
    detail: string;
    status: ReconcileIssueStatus;
    resolveNote: string | null;
    resolvedBy: string | null;
    resolvedAt: string | null;
    createdAt: string;
}

export interface ReconcileOverview {
    /** 매일 대사가 마지막으로 끝난 날 */
    lastCheckedDay: string | null;
    /** 다음 자동 실행 시각(매일 04:50 KST) */
    nextRunAt: string;
    days: ReconcileDayRow[];
    openIssueCount: number;
}

export interface ReconcileIssueQuery {
    type?: ReconcileIssueType;
    status?: ReconcileIssueStatus;
    day?: string;
    page: number;
    size: number;
}

/** 사유가 필요한 처리(5~200자) — 감사(돈 관련)에 남는다 */
export interface PaymentReasonRequest {
    reason: string;
}
