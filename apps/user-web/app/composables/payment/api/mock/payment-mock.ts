// MOCK: 결제 완료 API(`POST /api/payments/{paymentId}/complete`)와 시험 주문 API(`/api/dev/payments/**`)의 메모리 목업 — `usePaymentApi`·`usePaymentTestApi` 의 `// MOCK:` 줄이 부른다.
// 서버(2a-2)가 붙으면 이 폴더(`payment/api/mock/`)째 지운다. 새로고침하면 처음으로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   payment-portone   서버 게이트웨이가 포트원(결제창 정보에 상점 ID·채널 키) — 가짜 결제창을 열 수 없다
//   create-fail       시험 주문 만들기 실패(500)
//   not-configured    시험 주문 만들기가 503 PAYMENT_NOT_CONFIGURED(포트원인데 결제 설정이 비었음)
//   gateway-down      완료 요청이 503 PAYMENT_GATEWAY_UNAVAILABLE(포트원 조회 실패 — 웹훅·READY 정리가 이어 받는다)
//
// ── 목업 규칙(서버 확정 흐름 흉내 — 계약 「확정 흐름」·결정 4) ─
//   · 가짜 PG 가 PAID 가 아니면 완료 요청은 409 PAYMENT_NOT_PAID.
//   · 결제 시각 > 유효 시각이면 늦은 결제(LATE_PAYMENT), 금액이 다르면 AMOUNT_MISMATCH — 둘 다 시험 핸들러를 부르지 않고 전액 자동 취소를 넣는다.
//   · 자동 취소는 2초 뒤 다시 읽으면 끝나 있다(커밋 뒤 즉시 시도 흉내). 시험 핸들러는 늘 받아들인다(ACCEPT).
//   · 문구는 서버 몫이라 한국어로만 준다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { PaymentCompleteRequest, PaymentFakeCheckoutRequest, PaymentResult, PaymentTestOrder, PaymentTestOrderRequest } from '~/composables/payment/api/usePaymentApi.types';

interface MockOrder {
    order: PaymentTestOrder;
    pg: { status: 'READY' | 'PAID' | 'FAILED'; amount: number; paidAt: string | null };
    result: PaymentResult | null;
    cancelDoneAt: number | null;
}

const orders = new Map<string, MockOrder>();

function randomId(length: number): string {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    let out = '';
    for (let i = 0; i < length; i++) out += chars[Math.floor(Math.random() * chars.length)];
    return out;
}

function find(paymentId: string): MockOrder {
    const found = orders.get(paymentId);
    if (!found) throw new ApiError({ status: 404, code: 'PAYMENT_ORDER_NOT_FOUND', message: '결제 주문을 찾을 수 없어요.' });
    return found;
}

function settle(found: MockOrder): void {
    const r = found.result;
    if (!r?.cancel || r.cancel.status === 'SUCCEEDED' || found.cancelDoneAt === null || Date.now() < found.cancelDoneAt) return;
    r.cancel.status = 'SUCCEEDED';
    r.canceledAmount = r.cancel.amount;
    r.status = 'CANCELED';
}

export async function mockCreateTestOrder(body: PaymentTestOrderRequest): Promise<PaymentTestOrder> {
    const flags = mockFlags();
    await mockDelay();
    if (flags.has('create-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('not-configured')) throw new ApiError({ status: 503, code: 'PAYMENT_NOT_CONFIGURED', message: '결제 설정이 아직 끝나지 않았어요. 관리자에게 알려 주세요.' });
    const fields: Record<string, string> = {};
    if (!Number.isInteger(body.amount) || body.amount < 100 || body.amount > 1_000_000) fields.amount = '금액은 100원 ~ 1,000,000원이에요.';
    if (!Number.isInteger(body.validMinutes) || body.validMinutes < 1 || body.validMinutes > 60) fields.validMinutes = '유효 시간은 1 ~ 60분이에요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const portone = flags.has('payment-portone');
    const order: PaymentTestOrder = {
        orderType: 'PAYMENT_TEST',
        orderRefId: String(7_100_000_000_000 + Math.floor(Math.random() * 1_000_000)),
        expiresAt: new Date(Date.now() + body.validMinutes * 60_000).toISOString(),
        checkout: {
            gateway: portone ? 'PORTONE' : 'FAKE',
            paymentId: `pay_${randomId(32)}`,
            orderName: '시험 결제',
            amount: body.amount,
            currency: 'KRW',
            storeId: portone ? 'store-00000000-0000-0000-0000-000000000000' : null,
            channelKey: portone ? 'channel-key-00000000-0000-0000-0000-000000000000' : null,
        },
    };
    orders.set(order.checkout.paymentId, { order, pg: { status: 'READY', amount: body.amount, paidAt: null }, result: null, cancelDoneAt: null });
    return mockClone(order);
}

export async function mockFakeCheckout(paymentId: string, body: PaymentFakeCheckoutRequest): Promise<void> {
    await mockDelay();
    const found = find(paymentId);
    if (found.order.checkout.gateway !== 'FAKE') throw new ApiError({ status: 409, code: 'PAYMENT_GATEWAY_NOT_FAKE', message: '가짜 결제창은 가짜 게이트웨이에서만 쓸 수 있어요.' });
    if (found.pg.status !== 'READY') throw new ApiError({ status: 409, code: 'FAKE_PG_ALREADY_DONE', message: '이미 결제창을 끝낸 주문이에요.' });
    if (body.outcome === 'FAILED') {
        found.pg.status = 'FAILED';
        return;
    }
    if (!Number.isInteger(body.amount) || body.amount < 1) throw MOCK_ERRORS.validation({ amount: '결제할 금액을 확인해 주세요.' });
    found.pg = { status: 'PAID', amount: body.amount, paidAt: body.late ? new Date(Date.parse(found.order.expiresAt) + 60_000).toISOString() : new Date().toISOString() };
}

export async function mockComplete(paymentId: string, body: PaymentCompleteRequest): Promise<PaymentResult> {
    const flags = mockFlags();
    await mockDelay();
    const found = find(paymentId);
    if (found.order.orderType !== body.orderType || found.order.orderRefId !== body.orderRefId) throw new ApiError({ status: 409, code: 'PAYMENT_ORDER_MISMATCH', message: '이 주문의 결제가 아니에요.' });
    if (found.result) {
        settle(found);
        return mockClone(found.result);
    }
    if (flags.has('gateway-down')) throw new ApiError({ status: 503, code: 'PAYMENT_GATEWAY_UNAVAILABLE', message: '결제를 확인하지 못했어요. 결제가 됐다면 잠시 뒤 자동으로 반영돼요.' });
    if (found.pg.status !== 'PAID') throw new ApiError({ status: 409, code: 'PAYMENT_NOT_PAID', message: '아직 결제가 완료되지 않았어요.' });
    const amount = found.order.checkout.amount;
    const late = Date.parse(found.pg.paidAt!) > Date.parse(found.order.expiresAt);
    const anomaly = late ? 'LATE_PAYMENT' : found.pg.amount !== amount ? 'AMOUNT_MISMATCH' : null;
    found.result = {
        paymentId,
        status: 'PAID',
        anomaly,
        accepted: anomaly === null,
        amount,
        paidAmount: found.pg.amount,
        canceledAmount: 0,
        paidAt: found.pg.paidAt,
        cancel: anomaly ? { amount: found.pg.amount, status: 'PENDING' } : null,
    };
    if (anomaly) found.cancelDoneAt = Date.now() + 2000;
    return mockClone(found.result);
}

export async function mockTestOrderResult(paymentId: string): Promise<PaymentResult> {
    await mockDelay();
    const found = find(paymentId);
    if (!found.result) {
        const expired = Date.now() > Date.parse(found.order.expiresAt);
        return { paymentId, status: expired ? 'EXPIRED' : 'READY', anomaly: null, accepted: false, amount: found.order.checkout.amount, paidAmount: null, canceledAmount: 0, paidAt: null, cancel: null };
    }
    settle(found);
    return mockClone(found.result);
}
