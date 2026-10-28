// MOCK: 공구 API(목록·상세·견적·주문·알림 신청)와 공구 결제(가짜 결제창 결과·완료 요청)의 메모리 목업 — `useGroupBuyApi.ts` 가 부른다. 서버가 붙으면 목업 폴더째 지운다.
// 데이터는 `groupbuy-mock-data.ts`, 내 주문은 `groupbuy-order-mock.ts`(이 파일의 저장소를 같이 쓴다). 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여러 개) ─
//   목록     gb-list-fail(500) · gb-list-empty(빈 목록)
//   상세     gb-detail-fail(500) · gb-not-found(404 GROUP_BUY_NOT_FOUND)
//   단추     gb-login-required · gb-identity-required · gb-minor · gb-banned · gb-sales-closed · gb-already-joined · gb-limit-reached
//            (모집 중 공구의 `purchase.reason` 을 그 값으로 바꾸고 `actions.order` 를 끈다 — 로그인·본인인증은 주문으로 이어 가는 단추)
//   수량     gb-temp-soldout(공구 전체 잠시 구매 불가) · gb-soldout(공구 다 팔림 — 마감 직전) · gb-extended(103 이 마감 연장 중)
//   견적     gb-quote-fail(500)
//   주문     gb-amount-changed(첫 주문은 409 GROUP_BUY_AMOUNT_CHANGED — 배송비가 1,000원 올랐다고 가정, 다음 견적부터 새 금액)
//            gb-order-soldout(409 GROUP_BUY_SKU_SOLD_OUT) · gb-order-fail(500) · gb-hold-short(선점을 50초만 — 「결제 다시 열기」가 409 GROUP_BUY_HOLD_EXPIRED)
//   단품     스위치 없이 주소로 — 주문서 `?items=` 에 판매 끈 조합(113 화이트 / S = 11301)을 넣으면 409 GROUP_BUY_SKU_NOT_ON_SALE,
//            잠시 품절 단품(113 블랙 / M = 11305)이면 409 GROUP_BUY_SKU_SOLD_OUT(temporary), 추가 구성상품만(102 = 10251)이면 400 fields.items(견적·주문 둘 다 — 서버 2b-2)
//   결제     gb-pay-reject(완료 요청이 받지 않음 — anomaly DOMAIN_REJECTED, 자동 취소 진행) · gb-pay-complete-fail(완료 요청 503)
//   알림     gb-watch-fail(500)
//
// ── 목업 가정(서버 몫을 흉내 낸 것 — 실제 규칙의 정본은 서버) ─
//   · 결제창은 늘 가짜 게이트웨이(`FAKE`). 가짜 결제 결과는 이 목업이 기억한다(실제는 `POST /api/dev/payments/{id}/fake-checkout` — 시험 주문 전용 주소라 공구 목업은 흉내만).
//   · 견적: 상품 합 = Σ 단품 값 × 수량(추가 구성상품 포함 — 무료배송 기준에 더한다), 무료배송 기준 이상이면 배송비 0. 공구 가격·남은 수량은 시드 값 그대로(선점해도 줄이지 않는다).
//   · 줄 검사(결정 88): 단품 하나에 한 줄 · 판매 끈 조합 거절 · MAIN 줄 없으면 거절 · 단품 남은 수량 · 1인 한도는 MAIN 수량 합만.
//   · 주문 번호 `G` + 오늘(yyyyMMdd) + 일련 5자리(30000부터). 선점 5분.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { at, MOCK_TIME, seedGroupBuys, seedOrders } from '~/composables/groupbuy/api/mock/groupbuy-mock-data';
import type {
    GroupBuyDetail,
    GroupBuyListItem,
    GroupBuyListPage,
    GroupBuyOrderCreated,
    GroupBuyOrderRequest,
    GroupBuyPaymentStart,
    GroupBuyPhase,
    GroupBuyPurchaseReason,
    GroupBuyQuote,
    GroupBuyQuoteItem,
    GroupBuyQuoteRequest,
    GroupBuyWatchRequest,
} from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { MyGroupBuyOrderDetail } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type { PaymentCompleteRequest, PaymentFakeCheckoutRequest, PaymentResult } from '~/composables/payment/api/usePaymentApi.types';

/** 가짜 결제창에서 남긴 결과 */
interface FakePayment {
    orderType: GroupBuyPaymentStart['orderType'];
    orderRefId: string;
    amount: number;
    expiresAt: string;
    outcome: PaymentFakeCheckoutRequest | null;
}

interface MockStore {
    groupBuys: GroupBuyDetail[];
    orders: MyGroupBuyOrderDetail[];
    payments: Map<string, FakePayment>;
    seq: number;
    /** gb-amount-changed — 한 번 올린 뒤 남는 배송비 인상분 */
    amountBump: number;
    amountBumped: boolean;
}

let store: MockStore | null = null;

/** 목업 저장소 — 처음 부를 때 시드로 채운다(내 주문 목업도 같이 쓴다) */
export function mockGroupBuyStore(): MockStore {
    if (store) return store;
    const groupBuys = seedGroupBuys();
    store = { groupBuys, orders: seedOrders(groupBuys), payments: new Map(), seq: 30000, amountBump: 0, amountBumped: false };
    return store;
}

const notFound = () => new ApiError({ status: 404, code: 'GROUP_BUY_NOT_FOUND', message: '공동구매를 찾을 수 없어요.' });

/** 스위치로 바꾼 상세 — 저장소 값은 그대로 두고 응답만 */
function view(gb: GroupBuyDetail): GroupBuyDetail {
    const flags = mockFlags();
    const out = mockClone(gb);
    const recruiting = out.status === 'RECRUITING';
    const reasonFlags: [string, GroupBuyPurchaseReason][] = [
        ['gb-login-required', 'LOGIN_REQUIRED'],
        ['gb-identity-required', 'IDENTITY_REQUIRED'],
        ['gb-minor', 'MINOR'],
        ['gb-banned', 'BANNED'],
        ['gb-sales-closed', 'SALES_CLOSED'],
        ['gb-already-joined', 'ALREADY_JOINED'],
        ['gb-limit-reached', 'LIMIT_REACHED'],
    ];
    if (recruiting && flags.has('gb-temp-soldout')) {
        out.stockState = 'TEMP_SOLD_OUT';
        out.remainingQty = 0;
        out.availability = { ...out.availability, remaining: 0, temporarilyUnavailable: true, skus: out.availability.skus.map((k) => (k.kind === 'MAIN' && k.onSale ? { ...k, remaining: 0, temporarilyUnavailable: true } : k)) };
        out.purchase.reason = 'TEMP_SOLD_OUT';
        out.actions = { ...out.actions, order: false, watchRestock: true };
    }
    if (recruiting && flags.has('gb-soldout')) {
        out.stockState = 'SOLD_OUT';
        out.remainingQty = 0;
        out.availability = { ...out.availability, remaining: 0, soldOut: true, skus: out.availability.skus.map((k) => (k.kind === 'MAIN' && k.onSale ? { ...k, remaining: 0, soldOut: true, temporarilyUnavailable: false } : k)) };
        out.currentQty = out.maxQty;
        out.purchase.reason = 'SOLD_OUT';
        out.actions = { ...out.actions, order: false, watchRestock: false };
    }
    if (out.id === 103 && flags.has('gb-extended')) {
        out.endAt = at(-5 * MOCK_TIME.MIN);
        out.extendedUntil = at(25 * MOCK_TIME.MIN);
    }
    for (const [flag, reason] of reasonFlags) {
        if (recruiting && flags.has(flag)) {
            out.purchase.reason = reason;
            out.actions = { ...out.actions, order: false };
            if (reason === 'ALREADY_JOINED') out.myOrderNo = 'G2026102700005';
        }
    }
    return out;
}

export async function mockGroupBuyList(phase: GroupBuyPhase, page: number): Promise<GroupBuyListPage> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('gb-list-fail')) throw MOCK_ERRORS.internal();
    const size = 12;
    const all = flags.has('gb-list-empty') ? [] : mockGroupBuyStore().groupBuys.filter((gb) => gb.phase === phase).map(view);
    const items = all.slice(page * size, page * size + size).map(
        (gb): GroupBuyListItem => ({
            id: gb.id,
            title: gb.title,
            thumbnailUrl: gb.thumbnailUrl,
            status: gb.status,
            phase: gb.phase,
            closeReason: gb.closeReason,
            fulfillment: gb.fulfillment,
            groupPrice: gb.groupPrice,
            priceFrom: gb.priceFrom,
            normalPrice: gb.normalPrice,
            minQty: gb.minQty,
            maxQty: gb.maxQty,
            currentQty: gb.currentQty,
            remainingQty: gb.remainingQty,
            stockState: gb.stockState,
            startAt: gb.startAt,
            endAt: gb.endAt,
            extendedUntil: gb.extendedUntil,
            closingSoon: gb.closingSoon,
            roundNo: gb.roundNo,
        }),
    );
    return { items, page, size, total: all.length };
}

export async function mockGroupBuyDetail(id: number): Promise<GroupBuyDetail> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('gb-detail-fail')) throw MOCK_ERRORS.internal();
    const gb = mockGroupBuyStore().groupBuys.find((g) => g.id === id);
    if (!gb || flags.has('gb-not-found')) throw notFound();
    return view(gb);
}

/** 견적 계산(서버 몫 흉내) — 주문도 같은 계산. 줄 검사를 먼저 거친 요청만 온다 */
function quoteOf(gb: GroupBuyDetail, body: GroupBuyQuoteRequest): GroupBuyQuote {
    const s = mockGroupBuyStore();
    const items = body.items.map((line): GroupBuyQuoteItem => {
        const sku = gb.availability.skus.find((k) => k.skuId === line.skuId)!;
        return { skuId: sku.skuId, kind: sku.kind, name: sku.name, qty: line.qty, unitPrice: sku.unitPrice, itemAmount: sku.unitPrice * line.qty };
    });
    const itemsAmount = items.reduce((sum, line) => sum + line.itemAmount, 0);
    const free = gb.shipping.freeShippingOver !== null && itemsAmount >= gb.shipping.freeShippingOver;
    const shippingFee = (free ? 0 : gb.shipping.fee) + s.amountBump;
    return {
        items,
        itemsAmount,
        shippingFee,
        totalAmount: itemsAmount + shippingFee,
        freeShippingRemaining: gb.shipping.freeShippingOver !== null && !free ? gb.shipping.freeShippingOver - itemsAmount : null,
    };
}

/** 줄 칸 오류(400, 서버 2b-2) — 없는 단품 · 같은 단품 두 줄 · 수량 0 · 추가 구성상품만 · 단품 남은 수량 · 1인 한도(MAIN 합만) */
function lineFields(gb: GroupBuyDetail, body: GroupBuyQuoteRequest): Record<string, string> {
    const fields: Record<string, string> = {};
    const skus = gb.availability.skus;
    if (body.items.length === 0) fields.items = '상품과 수량을 골라 주세요.';
    const seen = new Set<number>();
    let mainQty = 0;
    body.items.forEach((line, index) => {
        const sku = skus.find((k) => k.skuId === line.skuId);
        if (!sku) fields[`items[${index}].skuId`] = '이 공동구매에 없는 상품이에요.';
        if (seen.has(line.skuId)) fields.items = '같은 상품은 한 줄에만 담을 수 있어요.';
        seen.add(line.skuId);
        if (!Number.isInteger(line.qty) || line.qty < 1) fields.items = '수량을 1개 이상 넣어 주세요.';
        else if (sku && sku.onSale && sku.remaining !== null && line.qty > sku.remaining && sku.remaining > 0) fields[`items[${index}].qty`] = `「${sku.name}」 남은 수량은 ${sku.remaining}개예요.`;
        if (sku?.kind === 'MAIN' && Number.isInteger(line.qty)) mainQty += line.qty;
    });
    if (body.items.length > 0 && !fields.items && !body.items.some((line) => skus.find((k) => k.skuId === line.skuId)?.kind === 'MAIN')) fields.items = '추가 구성상품은 본 상품과 함께만 살 수 있어요. 본 상품을 먼저 골라 주세요.';
    if (!fields.items && gb.maxQtyPerUser !== null && mainQty > gb.maxQtyPerUser) fields.items = `1인 한도는 본 상품 ${gb.maxQtyPerUser}개예요(추가 구성상품은 세지 않아요).`;
    return fields;
}

/** 줄 충돌(409, 서버 2b-2) — 판매 안 함 `GROUP_BUY_SKU_NOT_ON_SALE {skuId}` · 품절 `GROUP_BUY_SKU_SOLD_OUT {skuId, temporary}` */
function lineConflict(gb: GroupBuyDetail, body: GroupBuyQuoteRequest): ApiError | null {
    const skus = body.items.map((line) => gb.availability.skus.find((k) => k.skuId === line.skuId)!);
    const off = skus.find((k) => !k.onSale);
    if (off) return new ApiError({ status: 409, code: 'GROUP_BUY_SKU_NOT_ON_SALE', message: `「${off.name}」은(는) 판매하지 않는 조합이에요. 다른 옵션을 골라 주세요.`, extras: { skuId: off.skuId } });
    const out = skus.find((k) => k.soldOut || k.temporarilyUnavailable || k.remaining === 0);
    if (out) {
        const temporary = out.temporarilyUnavailable;
        return new ApiError({ status: 409, code: 'GROUP_BUY_SKU_SOLD_OUT', message: temporary ? `「${out.name}」은(는) 다른 분이 결제 중이라 잠시 살 수 없어요.` : `「${out.name}」은(는) 다 팔렸어요. 빼거나 다른 옵션을 골라 주세요.`, extras: { skuId: out.skuId, temporary } });
    }
    return null;
}

export async function mockGroupBuyQuote(id: number, body: GroupBuyQuoteRequest): Promise<GroupBuyQuote> {
    await mockDelay();
    if (mockFlags().has('gb-quote-fail')) throw MOCK_ERRORS.internal();
    const gb = mockGroupBuyStore().groupBuys.find((g) => g.id === id);
    if (!gb) throw notFound();
    const fields = lineFields(gb, body);
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    const conflict = lineConflict(gb, body);
    if (conflict) throw conflict;
    return quoteOf(gb, body);
}

function today(): string {
    const d = new Date(Date.now() + 9 * MOCK_TIME.HOUR);
    return `${d.getUTCFullYear()}${String(d.getUTCMonth() + 1).padStart(2, '0')}${String(d.getUTCDate()).padStart(2, '0')}`;
}

/** 결제창 대상 하나를 만든다(가짜 게이트웨이) */
export function mockPaymentStart(orderType: GroupBuyPaymentStart['orderType'], orderRefId: string, orderNo: string, orderName: string, amount: number, expiresAt: string): GroupBuyPaymentStart {
    const s = mockGroupBuyStore();
    const paymentId = `mock-${orderType === 'GROUP_BUY' ? 'gb' : 'rf'}-${orderRefId}-${++s.seq}`;
    s.payments.set(paymentId, { orderType, orderRefId, amount, expiresAt, outcome: null });
    return { orderType, orderRefId, orderNo, expiresAt, customer: null, checkout: { gateway: 'FAKE', paymentId, orderName, amount, currency: 'KRW', storeId: null, channelKey: null } };
}

export async function mockGroupBuyOrder(id: number, body: GroupBuyOrderRequest): Promise<GroupBuyOrderCreated> {
    await mockDelay();
    const flags = mockFlags();
    const s = mockGroupBuyStore();
    if (flags.has('gb-order-fail')) throw MOCK_ERRORS.internal();
    const gb = s.groupBuys.find((g) => g.id === id);
    if (!gb) throw notFound();
    if (gb.status !== 'RECRUITING') throw new ApiError({ status: 409, code: 'GROUP_BUY_NOT_RECRUITING', message: '지금은 모집 중인 공동구매가 아니에요.' });
    const fields = lineFields(gb, body);
    const a = body.address;
    if (!a.recipient.trim()) fields['address.recipient'] = '받는 분을 넣어 주세요.';
    if (!/^0\d{8,10}$/.test(a.phone.replace(/-/g, ''))) fields['address.phone'] = '전화번호를 다시 확인해 주세요.';
    if (!/^\d{5}$/.test(a.postcode)) fields['address.postcode'] = '우편번호 5자리를 넣어 주세요.';
    if (!a.address1.trim()) fields['address.address1'] = '주소를 넣어 주세요.';
    if (!body.agreements.purchaseTerms) fields['agreements.purchaseTerms'] = '구매 조건에 동의해 주세요.';
    if (!body.agreements.refundPolicy) fields['agreements.refundPolicy'] = '환불 안내에 동의해 주세요.';
    if (!body.agreements.privacy) fields['agreements.privacy'] = '개인정보 수집·이용에 동의해 주세요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    const conflict = lineConflict(gb, body);
    if (conflict) throw conflict;
    if (flags.has('gb-order-soldout')) throw new ApiError({ status: 409, code: 'GROUP_BUY_SKU_SOLD_OUT', message: '고른 상품 중 남은 수량이 모자란 것이 있어요. 수량을 줄이거나 다른 옵션을 골라 주세요.' });
    if (flags.has('gb-amount-changed') && !s.amountBumped) {
        s.amountBumped = true;
        s.amountBump = 1000;
    }
    const quote = quoteOf(gb, body);
    if (body.expectedAmount !== quote.totalAmount) {
        throw new ApiError({ status: 409, code: 'GROUP_BUY_AMOUNT_CHANGED', message: '결제 금액이 바뀌었어요. 새 금액을 확인해 주세요.', extras: { expectedAmount: body.expectedAmount, currentAmount: quote.totalAmount } });
    }
    const orderNo = `G${today()}${String(++s.seq).padStart(5, '0')}`;
    const reservedUntil = at(flags.has('gb-hold-short') ? 50_000 : 5 * MOCK_TIME.MIN);
    const orderName = gb.title.length > 30 ? `${gb.title.slice(0, 30)}…` : gb.title;
    const payment = mockPaymentStart('GROUP_BUY', orderNo, orderNo, orderName, quote.totalAmount, reservedUntil);
    const order: MyGroupBuyOrderDetail = {
        orderNo,
        status: 'PENDING_PAYMENT',
        groupBuy: { id: gb.id, title: gb.title, thumbnailUrl: gb.thumbnailUrl, status: gb.status, fulfillment: gb.fulfillment, closeReason: gb.closeReason, roundNo: gb.roundNo },
        items: quote.items.map((line, index) => ({
            id: s.seq * 10 + index,
            skuId: line.skuId,
            skuName: line.name,
            kind: line.kind,
            qty: line.qty,
            unitPrice: line.unitPrice,
            itemAmount: line.itemAmount,
            canceledQty: 0,
            returnedQty: 0,
            selectableQty: 0,
        })),
        itemsAmount: quote.itemsAmount,
        shippingFee: quote.shippingFee,
        amount: quote.totalAmount,
        refundedAmount: 0,
        compensationAmount: 0,
        createdAt: at(0),
        paidAt: null,
        reservedUntil,
        shipPlannedAt: gb.shipPlannedAt,
        shipWithinBusinessDays: gb.shipWithinBusinessDays,
        address: { recipient: `${a.recipient.slice(0, 1)}*${a.recipient.slice(2)}`, phone: '010-****-' + a.phone.replace(/-/g, '').slice(-4), postcode: a.postcode, address1: a.address1.split(' ').slice(0, 2).join(' ') + ' ****', address2: '****', memo: a.memo, masked: true, purged: false },
        addressEditableUntilText: null,
        shipment: null,
        delayNotice: null,
        supplyChoice: null,
        requests: [],
        refunds: [],
        returnUntil: null,
        defectReturnUntil: null,
        actions: { payAgain: true, revealAddress: false, editAddress: false, cancelNow: false, requestCancel: false, requestReturn: false, delayCancel: false, supplyChoice: false },
    };
    s.orders.unshift(order);
    return { orderNo, amount: quote.totalAmount, reservedUntil, payment };
}

export async function mockGroupBuyWatch(id: number, body: GroupBuyWatchRequest, on: boolean): Promise<void> {
    await mockDelay();
    if (mockFlags().has('gb-watch-fail')) throw MOCK_ERRORS.internal();
    const gb = mockGroupBuyStore().groupBuys.find((g) => g.id === id);
    if (!gb) throw notFound();
    if (body.skuId !== null) {
        const w = gb.skuWatches;
        w.watching = on ? [...new Set([...w.watching, body.skuId])] : w.watching.filter((skuId) => skuId !== body.skuId);
        return;
    }
    gb.watches[body.kind] = on;
}

/* ── 결제(가짜 게이트웨이) ── */

export async function mockGroupBuyFakeCheckout(paymentId: string, body: PaymentFakeCheckoutRequest): Promise<void> {
    await mockDelay();
    const payment = mockGroupBuyStore().payments.get(paymentId);
    if (!payment) throw new ApiError({ status: 404, code: 'PAYMENT_ORDER_NOT_FOUND', message: '결제를 찾을 수 없어요.' });
    if (payment.outcome) throw new ApiError({ status: 409, code: 'FAKE_PG_ALREADY_DONE', message: '이미 결과를 남긴 결제예요.' });
    payment.outcome = { ...body };
}

/** 완료 요청 — 받아들이면 주문(또는 반품 요청)을 바꾼다 */
export async function mockGroupBuyCompletePayment(paymentId: string, body: PaymentCompleteRequest): Promise<PaymentResult> {
    await mockDelay();
    const flags = mockFlags();
    const s = mockGroupBuyStore();
    if (flags.has('gb-pay-complete-fail')) throw new ApiError({ status: 503, code: 'PAYMENT_GATEWAY_UNAVAILABLE', message: '결제 결과를 아직 확인하지 못했어요. 잠시 뒤 내 주문에서 다시 확인해 주세요.' });
    const payment = s.payments.get(paymentId);
    if (!payment || payment.orderRefId !== body.orderRefId) throw new ApiError({ status: 409, code: 'PAYMENT_ORDER_MISMATCH', message: '이 주문의 결제가 아니에요.' });
    if (!payment.outcome || payment.outcome.outcome !== 'PAID') throw new ApiError({ status: 409, code: 'PAYMENT_NOT_PAID', message: '아직 결제가 끝나지 않았어요.' });
    const paid = payment.outcome;
    const anomaly = paid.amount !== payment.amount ? 'AMOUNT_MISMATCH' : paid.late ? 'LATE_PAYMENT' : flags.has('gb-pay-reject') ? 'DOMAIN_REJECTED' : null;
    const accepted = anomaly === null;
    if (accepted) {
        if (payment.orderType === 'GROUP_BUY') {
            const order = s.orders.find((o) => o.orderNo === payment.orderRefId);
            if (order) {
                order.status = order.groupBuy.fulfillment === 'IMMEDIATE' ? 'CONFIRMED' : 'JOINED';
                order.paidAt = at(0);
                order.reservedUntil = null;
                order.addressEditableUntilText = order.groupBuy.fulfillment === 'IMMEDIATE' ? '상품 준비가 시작되기 전까지' : '공구가 발주되기 전까지';
                order.actions = { ...order.actions, payAgain: false, revealAddress: true, editAddress: true, cancelNow: true };
            }
        } else {
            for (const order of s.orders) {
                const request = order.requests.find((r) => String(r.id) === payment.orderRefId);
                if (request && request.status === 'AWAITING_PAYMENT') {
                    request.status = 'REQUESTED';
                    request.actions = { withdraw: true, payReturnFee: false };
                }
            }
        }
    }
    return {
        paymentId,
        status: accepted ? 'PAID' : 'CANCELED',
        anomaly,
        accepted,
        amount: payment.amount,
        paidAmount: paid.amount,
        canceledAmount: accepted ? 0 : paid.amount,
        paidAt: at(0),
        cancel: accepted ? null : { amount: paid.amount, status: 'PENDING' },
    };
}
