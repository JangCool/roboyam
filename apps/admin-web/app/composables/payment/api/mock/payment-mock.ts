// MOCK: 결제 관리자 API(`/api/admin/payments/**` · `/api/admin/payment-webhooks` · `/api/admin/payment-anomalies` · `/api/admin/payment-cancels/**` · `/api/admin/payment-reconciliation/**`)의 메모리 목업 —
// `usePaymentAdminApi` 의 `// MOCK:` 줄이 부른다. 서버(2a-3)가 붙으면 이 폴더(`payment/api/mock/`)째 지운다.
// 모양은 계약 `docs/features/commerce-payment-contract.md` 의 표(`payment_orders`·`payment_cancels`·`payment_events`·`payment_webhook_logs`·`reconciliation_issues`·`payment_reconcile_days`)를 따른다.
// 메모리 상태는 새로고침하면 처음으로 돌아간다(화면 전환 사이에는 남는다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   orders-fail      결제 주문 목록 실패(500)          orders-empty     결제 주문이 하나도 없다
//   detail-fail      결제 상세 실패(500)               detail-gone      결제 상세가 404 PAYMENT_ORDER_NOT_FOUND
//   webhooks-fail    웹훅 수신 내역 실패(500)          anomalies-fail   결제 이상 목록 실패(500)
//   reconcile-fail   대사 화면 실패(500)              gateway-down     「포트원에서 다시 확인」·「다시 대사」가 503 PAYMENT_GATEWAY_UNAVAILABLE
//   stale            처리 단추가 409(다른 운영자가 먼저 처리 — PAYMENT_CANCEL_NOT_GAVE_UP · RECONCILE_ISSUE_NOT_OPEN)
//   forbidden        모든 요청이 403 PERMISSION_DENIED
//
// ── 목업 가정(서버가 정할 것) ─
//   · 이력의 `detail`·대사 이슈의 `detail`·취소의 `lastError` 는 서버가 요청 언어로 풀어 주는 한 줄이라 한국어로만 준다(`i18n.md` 5절).
//   · 「다시 시도」 = GAVE_UP 취소를 PENDING 으로 되돌리고 시도 횟수를 0 으로(다음 시도 = 지금). 「수동 처리 완료」 = 운영자가 포트원 콘솔 등에서 직접 끝냈다고 표시(MANUAL) —
//     결제 주문의 취소 누계는 바꾸지 않는다(실제로 취소됐는지는 「포트원에서 다시 확인」·대사가 맞춘다).
//   · 「포트원에서 다시 확인」 = 포트원 단건 조회 → 확정 코드(READY·EXPIRED 인데 포트원이 PAID 면 확정, `confirmedVia: ADMIN_RECHECK`). 목업은 READY·EXPIRED 중 번호 끝이 짝수면 PAID 로 본다.
//   · 「이 날 다시 대사」는 바로 돌지 않고 RUNNING 이 됐다가 3초 뒤 다시 읽으면 끝나 있다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    PaymentAnomalyPage,
    PaymentAnomalyQuery,
    PaymentAnomalyRow,
    PaymentCancelKind,
    PaymentCancelStatus,
    PaymentCancelView,
    PaymentConfirmedVia,
    PaymentEventKind,
    PaymentEventView,
    PaymentMember,
    PaymentOrderDetail,
    PaymentOrderQuery,
    PaymentOrderRow,
    PaymentOrderType,
    PaymentPage,
    PaymentRecheckResult,
    PaymentWebhookQuery,
    PaymentWebhookRow,
    ReconcileDayRow,
    ReconcileIssueQuery,
    ReconcileIssueRow,
    ReconcileIssueType,
    ReconcileOverview,
} from '~/composables/payment/api/usePaymentAdminApi.types';

const MIN = 60 * 1000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const NOW = Date.now();
const at = (msAgo: number) => new Date(NOW - msAgo).toISOString();
/** KST 달력 날짜(`YYYY-MM-DD`) — 대사의 하루 */
const kstDay = (ms: number) => new Date(ms + 9 * HOUR).toISOString().slice(0, 10);
const OPERATOR = '김운영';

// ── 공통 오류 ─

const notFound = () => new ApiError({ status: 404, code: 'PAYMENT_ORDER_NOT_FOUND', message: '결제 주문을 찾을 수 없어요.' });
const gatewayDown = () => new ApiError({ status: 503, code: 'PAYMENT_GATEWAY_UNAVAILABLE', message: '포트원에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.' });
const cancelStale = () => new ApiError({ status: 409, code: 'PAYMENT_CANCEL_NOT_GAVE_UP', message: '이미 다른 운영자가 처리한 취소예요. 화면을 새로 고칠게요.' });
const issueStale = () => new ApiError({ status: 409, code: 'RECONCILE_ISSUE_NOT_OPEN', message: '이미 처리된 대사 이슈예요. 화면을 새로 고칠게요.' });

async function gate(fail?: string): Promise<Set<string>> {
    const flags = mockFlags();
    await mockDelay();
    if (flags.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (fail && flags.has(fail)) throw MOCK_ERRORS.internal();
    return flags;
}

/** 사유 5~200자(앞뒤 공백 뗀 글자 수) — 서버 검증 흉내 */
function checkReason(reason: string): void {
    const length = Array.from(reason.trim()).length;
    if (length < 5) throw MOCK_ERRORS.validation({ reason: '사유를 5자 이상 적어 주세요.' });
    if (length > 200) throw MOCK_ERRORS.validation({ reason: '사유는 200자까지 쓸 수 있어요.' });
}

// ── 데이터 ─

const NICKNAMES = ['달빛고양이', '초록바다', '여름감자', '새벽러너', '책읽는곰', '모카라떼', '바람개비', '하늘정원', '도토리묵', '파란우산', '고요한숲', '반짝별'];
const member = (i: number): PaymentMember => (i === 7 ? { id: null, nickname: '탈퇴 회원', withdrawn: true } : { id: String(90_000_000_000 + i * 7919), nickname: NICKNAMES[i % NICKNAMES.length]!, withdrawn: false });

/** 결정적인 `pay_` + 32자 */
function paymentIdOf(n: number): string {
    let s = '';
    let x = (n + 1) * 2_654_435_761;
    while (s.length < 32) {
        x = (x * 1_103_515_245 + 12_345) % 2_147_483_648;
        s += (x % 36).toString(36);
    }
    return `pay_${s}`;
}

interface MockOrder {
    detail: PaymentOrderDetail;
}

let seq = 0;
const nextId = () => String(7_000_000_000_000 + ++seq);

function event(kind: PaymentEventKind, msAgo: number, detail: string | null = null, providerEventId: string | null = null): PaymentEventView {
    return { id: nextId(), kind, detail, providerEventId, createdAt: at(msAgo) };
}

function cancel(kind: PaymentCancelKind, amount: number, status: PaymentCancelStatus, msAgo: number, more: Partial<PaymentCancelView> = {}): PaymentCancelView {
    const done = status === 'SUCCEEDED' || status === 'MANUAL';
    return {
        id: nextId(),
        amount,
        kind,
        status,
        pgStatus: status === 'SUCCEEDED' ? 'SUCCEEDED' : status === 'GAVE_UP' ? 'FAILED' : status === 'SENDING' ? 'REQUESTED' : null,
        attempts: status === 'GAVE_UP' ? 5 : status === 'SUCCEEDED' ? 1 : 0,
        nextAttemptAt: status === 'PENDING' ? at(-5 * MIN) : null,
        lastError: null,
        reason: '',
        requestedBy: kind === 'ADMIN' || kind === 'REFUND' ? OPERATOR : null,
        resolvedBy: null,
        resolveNote: null,
        createdAt: at(msAgo),
        completedAt: done ? at(msAgo - 2 * MIN) : null,
        ...more,
    };
}

function webhook(paymentId: string | null, orderId: string | null, eventType: string | null, result: PaymentWebhookRow['result'], msAgo: number, more: Partial<PaymentWebhookRow> = {}): PaymentWebhookRow {
    const httpStatus = result === 'REJECTED' ? 400 : result === 'RETRY' ? 503 : 200;
    return { id: nextId(), webhookId: result === 'REJECTED' && !more.webhookId ? null : `msg_${nextId()}`, eventType, paymentId, paymentOrderId: orderId, result, httpStatus, errorCode: null, receivedAt: at(msAgo), ...more };
}

const ORDER_NAMES: Record<PaymentOrderType, string[]> = {
    POINT_CHARGE: ['포인트 10,000P 충전', '포인트 30,000P 충전', '포인트 50,000P 충전'],
    GROUP_BUY: ['[공구] 제주 감귤 5kg', '[공구] 수제 그래놀라 3봉', '[공구] 캠핑 의자 1개', '[공구] 원두 1kg'],
    PAYMENT_TEST: ['시험 결제'],
};

interface Spec {
    type: PaymentOrderType;
    amount: number;
    status: PaymentOrderDetail['status'];
    msAgo: number;
    via?: PaymentConfirmedVia;
    anomaly?: PaymentOrderDetail['anomaly'];
    paidAmount?: number;
    cancels?: PaymentCancelView[];
    canceledAmount?: number;
    extraEvents?: PaymentEventView[];
    memberIndex?: number;
}

const orders: MockOrder[] = [];
const webhooks: PaymentWebhookRow[] = [];

function addOrder(n: number, spec: Spec): PaymentOrderDetail {
    const id = nextId();
    const paymentId = paymentIdOf(n);
    const names = ORDER_NAMES[spec.type];
    const created = spec.msAgo;
    const paid = spec.status !== 'READY' && spec.status !== 'EXPIRED';
    const paidMsAgo = created - 2 * MIN;
    const cancels = spec.cancels ?? [];
    const canceledAmount = spec.canceledAmount ?? cancels.filter((c) => c.status === 'SUCCEEDED').reduce((sum, c) => sum + c.amount, 0);
    const events: PaymentEventView[] = [event('CREATED', created, `결제창 열림 · 유효 시간 10분`)];
    const via = paid ? (spec.via ?? 'COMPLETE_API') : null;
    const paidWebhook = paid ? `msg_${nextId()}` : null;
    if (paid) {
        events.push(event('PAID', paidMsAgo, `${(spec.paidAmount ?? spec.amount).toLocaleString('ko-KR')}원 결제 · ${viaText(via!)}`, via === 'WEBHOOK' ? paidWebhook : null));
        webhooks.push(webhook(paymentId, id, 'Transaction.Paid', 'PROCESSED', paidMsAgo - 10 * 1000, { webhookId: paidWebhook }));
    }
    if (spec.status === 'EXPIRED') events.push(event('EXPIRED', created - 20 * MIN, '유효 시간이 지나 READY 정리가 닫음'));
    if (spec.anomaly) events.push(event('ANOMALY', paidMsAgo - 1000, anomalyText(spec.anomaly)));
    for (const c of cancels) {
        events.push(event('CANCEL_REQUESTED', NOW - Date.parse(c.createdAt), `${c.amount.toLocaleString('ko-KR')}원 취소 요청 · ${cancelKindText(c.kind)}`));
        if (c.status === 'SUCCEEDED') {
            events.push(event('CANCEL_SUCCEEDED', NOW - Date.parse(c.completedAt ?? c.createdAt), `${c.amount.toLocaleString('ko-KR')}원 취소 완료`));
            webhooks.push(webhook(paymentId, id, c.amount === (spec.paidAmount ?? spec.amount) ? 'Transaction.Cancelled' : 'Transaction.PartialCancelled', 'PROCESSED', NOW - Date.parse(c.completedAt ?? c.createdAt) - 5000));
        }
        if (c.status === 'GAVE_UP') events.push(event('CANCEL_GAVE_UP', NOW - Date.parse(c.createdAt) - 14 * HOUR, '취소 5회 실패 — 운영자 확인 필요'));
    }
    events.push(...(spec.extraEvents ?? []));
    events.sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));

    const detail: PaymentOrderDetail = {
        id,
        paymentId,
        member: member(spec.memberIndex ?? n),
        orderType: spec.type,
        orderRefId: spec.type === 'PAYMENT_TEST' ? nextId() : String(5_000_000_000 + n * 131),
        orderName: names[n % names.length]!,
        amount: spec.amount,
        paidAmount: paid ? (spec.paidAmount ?? spec.amount) : null,
        canceledAmount,
        status: spec.status,
        anomaly: spec.anomaly ?? null,
        payMethod: paid ? (n % 3 === 0 ? '카카오페이' : n % 3 === 1 ? '신용카드(국민)' : '토스페이') : null,
        paidAt: paid ? at(paidMsAgo) : null,
        expiresAt: at(created - 10 * MIN),
        createdAt: at(created),
        cancelPending: cancels.some((c) => c.status === 'PENDING' || c.status === 'SENDING' || c.status === 'GAVE_UP'),
        currency: 'KRW',
        pgProvider: paid ? 'TOSSPAYMENTS' : null,
        pgTxId: paid ? `tx_${paymentId.slice(4, 20)}` : null,
        receiptUrl: paid ? `https://dashboard.portone.io/receipts/${paymentId.slice(4, 16)}` : null,
        confirmedVia: via,
        updatedAt: at(Math.min(created, ...cancels.map((c) => NOW - Date.parse(c.completedAt ?? c.createdAt)))),
        events,
        cancels,
        webhooks: [],
        openIssueCount: 0,
    };
    orders.push({ detail });
    return detail;
}

function viaText(via: PaymentConfirmedVia): string {
    return { COMPLETE_API: '완료 요청으로 확정', WEBHOOK: '웹훅으로 확정', READY_CLEANUP: 'READY 정리가 확정', ADMIN_RECHECK: '운영자 다시 확인으로 확정' }[via];
}
function anomalyText(anomaly: NonNullable<PaymentOrderDetail['anomaly']>): string {
    return { AMOUNT_MISMATCH: '결제 금액이 주문 금액과 달라 받지 않음 — 전액 자동 취소', DOMAIN_REJECTED: '공구가 이미 마감돼 받지 않음 — 전액 자동 취소', LATE_PAYMENT: '유효 시간이 지난 뒤 결제됨 — 전액 자동 취소' }[anomaly];
}
function cancelKindText(kind: PaymentCancelKind): string {
    return { USER_CANCEL: '회원 취소', REFUND: '환불', AUTO_REJECT: '자동 취소', ADMIN: '운영자 취소', CHARGE_REFUND: '충전 환불' }[kind];
}

// 특별한 경우 — 상태·이상·취소 대기열을 한 번씩 보이게
addOrder(1, { type: 'POINT_CHARGE', amount: 10_000, status: 'PAID', msAgo: 25 * MIN });
addOrder(2, { type: 'GROUP_BUY', amount: 24_900, status: 'PAID', msAgo: 2 * HOUR, via: 'WEBHOOK' });
addOrder(3, { type: 'GROUP_BUY', amount: 39_000, status: 'PARTIAL_CANCELED', msAgo: 3 * DAY, cancels: [cancel('REFUND', 13_000, 'SUCCEEDED', 2 * DAY, { reason: '옵션 1개 품절 — 부분 환불' })] });
addOrder(4, { type: 'GROUP_BUY', amount: 18_000, status: 'CANCELED', msAgo: 4 * DAY, cancels: [cancel('USER_CANCEL', 18_000, 'SUCCEEDED', 4 * DAY - HOUR, { reason: '회원 참여 취소(마감 전)' })] });
addOrder(5, { type: 'POINT_CHARGE', amount: 30_000, status: 'READY', msAgo: 3 * MIN });
addOrder(6, { type: 'POINT_CHARGE', amount: 50_000, status: 'EXPIRED', msAgo: 5 * HOUR });
addOrder(8, {
    type: 'POINT_CHARGE',
    amount: 30_000,
    paidAmount: 3_000,
    status: 'CANCELED',
    anomaly: 'AMOUNT_MISMATCH',
    msAgo: 26 * HOUR,
    cancels: [cancel('AUTO_REJECT', 3_000, 'SUCCEEDED', 26 * HOUR - 3 * MIN, { reason: '금액 불일치 자동 취소' })],
});
addOrder(9, {
    type: 'GROUP_BUY',
    amount: 24_900,
    status: 'PAID',
    anomaly: 'DOMAIN_REJECTED',
    msAgo: 40 * MIN,
    cancels: [cancel('AUTO_REJECT', 24_900, 'PENDING', 38 * MIN, { reason: '공구 마감 뒤 결제 — 자동 취소', attempts: 2, lastError: '포트원 응답 시간 초과(10초)' })],
});
addOrder(10, {
    type: 'POINT_CHARGE',
    amount: 10_000,
    status: 'PAID',
    anomaly: 'LATE_PAYMENT',
    msAgo: 30 * HOUR,
    cancels: [cancel('AUTO_REJECT', 10_000, 'GAVE_UP', 30 * HOUR - 3 * MIN, { reason: '늦은 결제 자동 취소', lastError: 'PG_PROVIDER_ERROR: 취소 가능 기간이 지났어요(카드사 응답)' })],
});
addOrder(11, {
    type: 'GROUP_BUY',
    amount: 56_000,
    status: 'PAID',
    msAgo: 6 * DAY,
    cancels: [cancel('REFUND', 56_000, 'GAVE_UP', 3 * DAY, { reason: '배송 불가 지역 — 전액 환불', lastError: 'PG_PROVIDER_ERROR: 가상계좌 환불 계좌 정보가 필요해요' })],
});
const consoleCanceled = addOrder(12, {
    type: 'GROUP_BUY',
    amount: 21_000,
    status: 'PAID',
    msAgo: 2 * DAY,
    extraEvents: [event('EXTERNAL_CANCEL', DAY + 3 * HOUR, '포트원 콘솔에서 직접 취소된 21,000원 — 우리 상태는 그대로, 대사 이슈로 남김')],
});
addOrder(13, { type: 'POINT_CHARGE', amount: 10_000, status: 'PAID', msAgo: 9 * HOUR, via: 'READY_CLEANUP' });
addOrder(14, { type: 'PAYMENT_TEST', amount: 1_000, status: 'PAID', msAgo: 50 * MIN, memberIndex: 0 });
addOrder(15, { type: 'PAYMENT_TEST', amount: 1_000, status: 'EXPIRED', msAgo: 70 * MIN, memberIndex: 0 });
addOrder(16, {
    type: 'GROUP_BUY',
    amount: 42_000,
    status: 'PAID',
    msAgo: 2 * HOUR + 20 * MIN,
    cancels: [cancel('ADMIN', 42_000, 'SENDING', 3 * MIN, { reason: '중복 결제 — 운영자 취소', attempts: 1 })],
});
addOrder(17, {
    type: 'POINT_CHARGE',
    amount: 10_000,
    status: 'PAID',
    anomaly: 'LATE_PAYMENT',
    msAgo: 9 * DAY,
    cancels: [cancel('AUTO_REJECT', 10_000, 'MANUAL', 9 * DAY - 3 * MIN, { reason: '늦은 결제 자동 취소', resolvedBy: OPERATOR, resolveNote: '포트원 콘솔에서 직접 취소함(카드사 기간 초과)' })],
    extraEvents: [event('CANCEL_MANUAL', 8 * DAY, `수동 처리 완료 · ${OPERATOR}`)],
});

// 채움 줄 — 보통 결제·만료·취소
const FILL_TYPES: PaymentOrderType[] = ['POINT_CHARGE', 'GROUP_BUY', 'GROUP_BUY', 'POINT_CHARGE'];
for (let n = 20; n < 46; n++) {
    const type = FILL_TYPES[n % FILL_TYPES.length]!;
    const amount = type === 'POINT_CHARGE' ? [10_000, 30_000, 50_000][n % 3]! : [18_000, 24_900, 39_000, 56_000][n % 4]!;
    const msAgo = (n - 18) * 7 * HOUR + (n % 5) * 13 * MIN;
    if (n % 7 === 0) addOrder(n, { type, amount, status: 'EXPIRED', msAgo });
    else if (n % 9 === 0) addOrder(n, { type, amount, status: 'CANCELED', msAgo, cancels: [cancel(type === 'POINT_CHARGE' ? 'CHARGE_REFUND' : 'USER_CANCEL', amount, 'SUCCEEDED', msAgo - 3 * HOUR, { reason: '회원 요청' })] });
    else addOrder(n, { type, amount, status: 'PAID', msAgo, via: n % 4 === 0 ? 'WEBHOOK' : 'COMPLETE_API' });
}

// 우리 결제가 아니거나 받지 않은 웹훅
webhooks.push(webhook(null, null, null, 'REJECTED', 15 * MIN, { errorCode: 'WEBHOOK_SIGNATURE_INVALID' }));
webhooks.push(webhook(null, null, null, 'REJECTED', 3 * HOUR, { errorCode: 'WEBHOOK_TIMESTAMP_SKEW', webhookId: 'msg_old_replay_01' }));
webhooks.push(webhook(null, null, null, 'REJECTED', 2 * DAY, { errorCode: 'WEBHOOK_HEADER_MISSING' }));
webhooks.push(webhook('pay_not_ours_0000000000000000000001', null, 'Transaction.Paid', 'IGNORED', 5 * HOUR, { errorCode: 'UNKNOWN_PAYMENT' }));
webhooks.push(webhook(orders[1]!.detail.paymentId, orders[1]!.detail.id, 'Transaction.Ready', 'IGNORED', 2 * HOUR + 30 * 1000, { errorCode: 'UNKNOWN_EVENT' }));
webhooks.push(webhook(orders[0]!.detail.paymentId, orders[0]!.detail.id, 'Transaction.Paid', 'RETRY', 25 * MIN - 5 * 1000, { errorCode: 'PAYMENT_GATEWAY_UNAVAILABLE' }));
webhooks.push(webhook(orders[0]!.detail.paymentId, orders[0]!.detail.id, 'Transaction.Paid', 'PROCESSED', 23 * MIN, { errorCode: 'DUPLICATE' }));
webhooks.push(webhook(consoleCanceled.paymentId, consoleCanceled.id, 'Transaction.Cancelled', 'PROCESSED', DAY + 3 * HOUR));
webhooks.sort((a, b) => Date.parse(b.receivedAt) - Date.parse(a.receivedAt));

// ── 대사 ─

const today = kstDay(NOW);
const dayOf = (back: number) => kstDay(Date.parse(`${today}T00:00:00+09:00`) - back * DAY + 12 * HOUR);
const days: ReconcileDayRow[] = [];
for (let back = 1; back <= 14; back++) {
    const day = dayOf(back);
    const finishedAt = new Date(Date.parse(`${day}T04:52:00+09:00`) + DAY).toISOString();
    days.push({ day, status: 'DONE', checkedCount: 20 + ((back * 7) % 15), issueCount: 0, openIssueCount: 0, finishedAt });
}
const dayRow = (back: number) => days[back - 1]!;

const issues: ReconcileIssueRow[] = [];
function addIssue(type: ReconcileIssueType, back: number, paymentId: string, orderId: string | null, detail: string, resolved = false): void {
    const row = dayRow(back);
    issues.push({
        id: nextId(),
        type,
        paymentId,
        paymentOrderId: orderId,
        detectedDay: row.day,
        detail,
        status: resolved ? 'RESOLVED' : 'OPEN',
        resolveNote: resolved ? '포트원 콘솔과 맞춰 봄 — 회원 문의로 이미 환불된 건' : null,
        resolvedBy: resolved ? OPERATOR : null,
        resolvedAt: resolved ? at(back * DAY - 6 * HOUR) : null,
        createdAt: row.finishedAt ?? at(back * DAY),
    });
    row.issueCount += 1;
    if (!resolved) row.openIssueCount += 1;
    row.status = 'MISMATCH';
}
const byN = (n: number) => orders.find((o) => o.detail.paymentId === paymentIdOf(n))!.detail;
addIssue('PG_PAID_LOCAL_UNPAID', 1, byN(6).paymentId, byN(6).id, '포트원: PAID 50,000원 · 우리: EXPIRED');
addIssue('CANCEL_MISMATCH', 1, consoleCanceled.paymentId, consoleCanceled.id, '포트원: 취소 21,000원(콘솔) · 우리: 취소 0원');
addIssue('AMOUNT_MISMATCH', 2, byN(22).paymentId, byN(22).id, `포트원: 결제 ${(byN(22).amount - 1_000).toLocaleString('ko-KR')}원 · 우리: 결제 ${byN(22).amount.toLocaleString('ko-KR')}원`);
addIssue('LOCAL_PAID_PG_MISSING', 4, byN(25).paymentId, byN(25).id, '포트원: 결제 없음 · 우리: PAID');
addIssue('PG_ONLY', 6, 'pay_console_only_9f3a00000000000000000', null, '포트원에만 있는 결제 15,000원(우리 주문 없음)');
addIssue('CANCEL_MISMATCH', 8, byN(27).paymentId, byN(27).id, '포트원: 취소 10,000원 · 우리: 취소 0원', true);
dayRow(3).status = 'FAILED';
dayRow(3).checkedCount = 0;
dayRow(3).finishedAt = null;

for (const issue of issues) {
    const order = orders.find((o) => o.detail.id === issue.paymentOrderId);
    if (order && issue.status === 'OPEN') order.detail.openIssueCount += 1;
}

/** 다시 대사가 끝날 시각(날짜별) */
const rerunDoneAt = new Map<string, number>();

// ── 조회 도구 ─

function rowOf(detail: PaymentOrderDetail): PaymentOrderRow {
    const { currency: _c, pgProvider: _p, pgTxId: _t, receiptUrl: _r, confirmedVia: _v, updatedAt: _u, events: _e, cancels: _x, webhooks: _w, openIssueCount: _i, ...row } = detail;
    return row;
}

function within(iso: string, from?: string, to?: string): boolean {
    const t = Date.parse(iso);
    return (!from || t >= Date.parse(from)) && (!to || t < Date.parse(to));
}

function paged<T>(items: T[], page: number, size: number): PaymentPage<T> {
    return mockClone({ items: items.slice(page * size, page * size + size), total: items.length });
}

function findOrder(id: string): PaymentOrderDetail {
    const found = orders.find((o) => o.detail.id === id);
    if (!found) throw notFound();
    return found.detail;
}

function refreshPending(detail: PaymentOrderDetail): void {
    detail.cancelPending = detail.cancels.some((c) => c.status === 'PENDING' || c.status === 'SENDING' || c.status === 'GAVE_UP');
}

// ── API ─

export async function mockPaymentOrders(query: PaymentOrderQuery): Promise<PaymentPage<PaymentOrderRow>> {
    const flags = await gate('orders-fail');
    if (flags.has('orders-empty')) return { items: [], total: 0 };
    const q = query.q?.trim().toLowerCase();
    const found = orders
        .map((o) => o.detail)
        .filter((d) => !query.status || d.status === query.status)
        .filter((d) => !query.anomaly || (query.anomaly === 'ANY' ? d.anomaly !== null : d.anomaly === query.anomaly))
        .filter((d) => !query.orderType || d.orderType === query.orderType)
        .filter((d) => within(d.createdAt, query.from, query.to))
        .filter((d) => !q || d.paymentId.toLowerCase().includes(q) || d.member.id === q)
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
        .map(rowOf);
    return paged(found, query.page, query.size);
}

export async function mockPaymentOrderDetail(id: string): Promise<PaymentOrderDetail> {
    const flags = await gate('detail-fail');
    if (flags.has('detail-gone')) throw notFound();
    const detail = findOrder(id);
    return mockClone({ ...detail, webhooks: webhooks.filter((w) => w.paymentOrderId === id) });
}

export async function mockPaymentRecheck(id: string, reason: string): Promise<PaymentRecheckResult> {
    const flags = await gate();
    checkReason(reason);
    if (flags.has('gateway-down')) throw gatewayDown();
    const detail = findOrder(id);
    let outcome: PaymentRecheckResult['outcome'] = 'UNCHANGED';
    if (detail.status === 'READY' || detail.status === 'EXPIRED') {
        if (Number.parseInt(detail.id.slice(-1), 10) % 2 === 0) {
            const late = detail.status === 'EXPIRED';
            detail.status = 'PAID';
            detail.paidAmount = detail.amount;
            detail.paidAt = new Date().toISOString();
            detail.confirmedVia = 'ADMIN_RECHECK';
            detail.payMethod = '신용카드(국민)';
            detail.events.push(event('PAID', 0, `${detail.amount.toLocaleString('ko-KR')}원 결제 · ${viaText('ADMIN_RECHECK')}`));
            if (late) {
                // EXPIRED 였으면 늦은 결제(결정 4) — 받지 않고 자동 취소를 넣는다
                detail.anomaly = 'LATE_PAYMENT';
                detail.events.push(event('ANOMALY', 0, anomalyText('LATE_PAYMENT')));
                const c = cancel('AUTO_REJECT', detail.amount, 'PENDING', 0, { reason: '늦은 결제 자동 취소' });
                detail.cancels.push(c);
                detail.events.push(event('CANCEL_REQUESTED', 0, `${c.amount.toLocaleString('ko-KR')}원 취소 요청 · 자동 취소`));
                refreshPending(detail);
            }
            outcome = 'CONFIRMED';
        } else outcome = 'PG_NOT_PAID';
    }
    detail.events.push(event('RECHECKED', 0, `포트원에서 다시 확인 · ${OPERATOR} · ${reason.trim()}`));
    detail.updatedAt = new Date().toISOString();
    return mockClone({ outcome, detail: { ...detail, webhooks: webhooks.filter((w) => w.paymentOrderId === id) } });
}

export async function mockPaymentWebhooks(query: PaymentWebhookQuery): Promise<PaymentPage<PaymentWebhookRow>> {
    await gate('webhooks-fail');
    const q = query.q?.trim().toLowerCase();
    const found = webhooks
        .filter((w) => !query.result || w.result === query.result)
        .filter((w) => within(w.receivedAt, query.from, query.to))
        .filter((w) => !q || (w.paymentId ?? '').toLowerCase().includes(q) || (w.webhookId ?? '').toLowerCase().includes(q));
    return paged(found, query.page, query.size);
}

function anomalyRows(): PaymentAnomalyRow[] {
    const out: PaymentAnomalyRow[] = [];
    for (const { detail } of orders) {
        if (detail.anomaly) {
            const c = detail.cancels.find((x) => x.kind === 'AUTO_REJECT') ?? null;
            const detected = detail.events.find((e) => e.kind === 'ANOMALY')?.createdAt ?? detail.paidAt ?? detail.createdAt;
            out.push({ key: `ORDER:${detail.id}`, kind: detail.anomaly, order: rowOf(detail), cancel: c, detectedAt: detected, resolved: c ? c.status === 'SUCCEEDED' || c.status === 'MANUAL' : false });
        }
        for (const c of detail.cancels) {
            if (detail.anomaly && c.kind === 'AUTO_REJECT') continue;
            if (c.status !== 'GAVE_UP' && !(c.status === 'MANUAL' || (c.status === 'PENDING' && c.resolvedBy))) continue;
            const gaveUp = detail.events.filter((e) => e.kind === 'CANCEL_GAVE_UP').at(-1)?.createdAt ?? c.createdAt;
            out.push({ key: `CANCEL:${c.id}`, kind: 'CANCEL_GAVE_UP', order: rowOf(detail), cancel: c, detectedAt: gaveUp, resolved: c.status === 'MANUAL' });
        }
    }
    return out.sort((a, b) => Date.parse(b.detectedAt) - Date.parse(a.detectedAt));
}

export async function mockPaymentAnomalies(query: PaymentAnomalyQuery): Promise<PaymentAnomalyPage> {
    await gate('anomalies-fail');
    const all = anomalyRows();
    const found = all
        .filter((r) => query.includeResolved || !r.resolved)
        .filter((r) => !query.kind || r.kind === query.kind)
        .filter((r) => within(r.detectedAt, query.from, query.to));
    return { ...paged(found, query.page, query.size), openCount: all.filter((r) => !r.resolved).length };
}

function findCancel(cancelId: string): { detail: PaymentOrderDetail; cancel: PaymentCancelView } {
    for (const { detail } of orders) {
        const c = detail.cancels.find((x) => x.id === cancelId);
        if (c) return { detail, cancel: c };
    }
    throw new ApiError({ status: 404, code: 'PAYMENT_CANCEL_NOT_FOUND', message: '취소 요청을 찾을 수 없어요.' });
}

export async function mockPaymentCancelRetry(cancelId: string, reason: string): Promise<PaymentCancelView> {
    const flags = await gate();
    checkReason(reason);
    const { detail, cancel: c } = findCancel(cancelId);
    if (flags.has('stale') || c.status !== 'GAVE_UP') throw cancelStale();
    c.status = 'PENDING';
    c.pgStatus = null;
    c.attempts = 0;
    c.nextAttemptAt = new Date().toISOString();
    c.resolvedBy = OPERATOR;
    c.resolveNote = reason.trim();
    detail.events.push(event('CANCEL_REQUESTED', 0, `다시 시도 · ${OPERATOR} · ${reason.trim()}`));
    refreshPending(detail);
    return mockClone(c);
}

export async function mockPaymentCancelManual(cancelId: string, reason: string): Promise<PaymentCancelView> {
    const flags = await gate();
    checkReason(reason);
    const { detail, cancel: c } = findCancel(cancelId);
    if (flags.has('stale') || c.status !== 'GAVE_UP') throw cancelStale();
    c.status = 'MANUAL';
    c.resolvedBy = OPERATOR;
    c.resolveNote = reason.trim();
    c.completedAt = new Date().toISOString();
    detail.events.push(event('CANCEL_MANUAL', 0, `수동 처리 완료 · ${OPERATOR} · ${reason.trim()}`));
    refreshPending(detail);
    return mockClone(c);
}

function settleReruns(): void {
    for (const [day, doneAt] of rerunDoneAt) {
        if (Date.now() < doneAt) continue;
        const row = days.find((d) => d.day === day);
        if (row) {
            row.status = row.openIssueCount > 0 ? 'MISMATCH' : 'DONE';
            row.checkedCount = row.checkedCount || 18;
            row.finishedAt = new Date(doneAt).toISOString();
        }
        rerunDoneAt.delete(day);
    }
}

export async function mockReconcileOverview(): Promise<ReconcileOverview> {
    await gate('reconcile-fail');
    settleReruns();
    const nextRun = Date.parse(`${today}T04:50:00+09:00`);
    const lastChecked = days.find((d) => d.status === 'DONE' || d.status === 'MISMATCH')?.day ?? null;
    return mockClone({
        lastCheckedDay: lastChecked,
        nextRunAt: new Date(nextRun > Date.now() ? nextRun : nextRun + DAY).toISOString(),
        days,
        openIssueCount: issues.filter((i) => i.status === 'OPEN').length,
    });
}

export async function mockReconcileIssues(query: ReconcileIssueQuery): Promise<PaymentPage<ReconcileIssueRow>> {
    await gate('reconcile-fail');
    const found = issues
        .filter((i) => !query.type || i.type === query.type)
        .filter((i) => !query.status || i.status === query.status)
        .filter((i) => !query.day || i.detectedDay === query.day)
        .sort((a, b) => (a.status === b.status ? Date.parse(b.createdAt) - Date.parse(a.createdAt) : a.status === 'OPEN' ? -1 : 1));
    return paged(found, query.page, query.size);
}

export async function mockReconcileResolve(issueId: string, reason: string): Promise<ReconcileIssueRow> {
    const flags = await gate();
    checkReason(reason);
    const issue = issues.find((i) => i.id === issueId);
    if (!issue) throw new ApiError({ status: 404, code: 'RECONCILE_ISSUE_NOT_FOUND', message: '대사 이슈를 찾을 수 없어요.' });
    if (flags.has('stale') || issue.status !== 'OPEN') throw issueStale();
    issue.status = 'RESOLVED';
    issue.resolveNote = reason.trim();
    issue.resolvedBy = OPERATOR;
    issue.resolvedAt = new Date().toISOString();
    const row = days.find((d) => d.day === issue.detectedDay);
    if (row) row.openIssueCount = Math.max(0, row.openIssueCount - 1);
    const order = orders.find((o) => o.detail.id === issue.paymentOrderId);
    if (order) order.detail.openIssueCount = Math.max(0, order.detail.openIssueCount - 1);
    return mockClone(issue);
}

export async function mockReconcileRerun(day: string): Promise<ReconcileDayRow> {
    const flags = await gate();
    if (flags.has('gateway-down')) throw gatewayDown();
    const row = days.find((d) => d.day === day);
    if (!row) throw MOCK_ERRORS.validation({ day: '어제까지의 날짜만 다시 대사할 수 있어요.' });
    if (row.status === 'RUNNING') throw new ApiError({ status: 409, code: 'RECONCILE_ALREADY_RUNNING', message: '이 날의 대사가 이미 돌고 있어요.' });
    row.status = 'RUNNING';
    rerunDoneAt.set(day, Date.now() + 3000);
    return mockClone(row);
}
