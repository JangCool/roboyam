// MOCK: 내 주문(공구) API 의 메모리 목업 — `useGroupBuyOrderApi.ts` 가 부른다. 저장소는 `groupbuy-mock.ts` 와 같이 쓴다(주문서에서 만든 주문이 여기 보인다). 서버가 붙으면 목업 폴더째 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여러 개) ─
//   목록     orders-fail(500) · orders-empty(빈 목록)
//   상세     order-fail(500) · order-not-found(404 GROUP_BUY_ORDER_NOT_FOUND)
//   결제     gb-hold-expired(결제 다시 열기 409 GROUP_BUY_HOLD_EXPIRED — 남은 선점 1분 미만과 같다)
//   배송지   order-reveal-fail(500) · order-address-fail(409 GROUP_BUY_ADDRESS_LOCKED — 수정 기한 지남)
//   취소     order-preview-fail(미리 보기 500) · order-cancel-fail(409 GROUP_BUY_ORDER_STATE — 그 사이 준비중이 됨)
//   요청     order-request-fail(500) · order-amount-changed(409 GROUP_BUY_AMOUNT_CHANGED — 미리 보기와 다름) · order-photo-fail(사진 올리기 500)
//
// ── 목업 가정(서버 `GroupBuyRefundCalculator` 의 정본 규칙을 흉내만 — commerce.md 10-8 표의 일부) ─
//   · 단순 변심·기타(고객 부담): 업체회수면 반품비를 빼고, 일부만 반품해 무료배송 기준 아래가 되면 처음 배송비를 다시 받는다. 무료배송 전체 반품 + 왕복이면 처음 배송비도 받는다.
//   · 하자·오배송 줄이 하나라도 있으면(업체 부담 — 결정 44) 반품비·재부과 없이 상품 금액, 전체 반품이면 낸 배송비도 돌려준다. 직접 발송이면 택배비 보상 대상(금액은 운영자가 정한다).
//   · 결과가 마이너스이면 요청은 `AWAITING_PAYMENT`(부족분 결제해야 접수 — 결정 68), 결제창 대상(`GROUP_BUY_RETURN_FEE`, 유효 30분)을 같이 준다.
//   · 본인 취소·지연 취소·공급 불가 전체 취소의 환불은 「진행 중」으로 더한다(카드사 반영 3~7영업일).
//   · 줄은 단품 단위(결정 88). 본인 즉시 취소는 `lines[{ skuId, qty }]` — 본 상품을 모두 취소하면서 추가 구성상품을 남기면 409 GROUP_BUY_ADDON_ONLY {skuIds}(서버 2b-2).
//     취소 요청·반품 요청도 단품 줄(`skuId`)로 받는다(서버 2b-2 답 — 주문 안 단품 줄은 하나뿐). 반품도 본 상품을 모두 고르고 추가 구성상품을 남기면 409 `GROUP_BUY_ADDON_ONLY` 가 서버 규칙이지만, 화면이 먼저 「함께 고를까요?」를 묻는다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { at, FULL_ADDRESS, MOCK_TIME } from '~/composables/groupbuy/api/mock/groupbuy-mock-data';
import { mockGroupBuyStore, mockPaymentStart } from '~/composables/groupbuy/api/mock/groupbuy-mock';
import type { GroupBuyAddressInput, GroupBuyPaymentStart } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type {
    GroupBuyCancelPreview,
    GroupBuyCancelRequest,
    GroupBuyCollectionMethod,
    GroupBuyItemQty,
    GroupBuyRequestCreate,
    GroupBuyRequestCreated,
    GroupBuyRequestPhoto,
    GroupBuyReturnLine,
    GroupBuyReturnOutcome,
    GroupBuyReturnPreview,
    GroupBuySupplyChoiceRequest,
    MyGroupBuyAddress,
    MyGroupBuyOrderDetail,
    MyGroupBuyOrderItem,
    MyGroupBuyOrderPage,
    MyGroupBuyRequest,
} from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';

const RETURN_ADDRESS = '(06236) 서울특별시 강남구 테헤란로 000, 0층 로보얌 시험상점 반품 담당 (시험 주소)';
const OPEN: MyGroupBuyRequest['status'][] = ['AWAITING_PAYMENT', 'REQUESTED', 'APPROVED'];

const notFound = () => new ApiError({ status: 404, code: 'GROUP_BUY_ORDER_NOT_FOUND', message: '주문을 찾을 수 없어요.' });

function find(orderNo: string): MyGroupBuyOrderDetail {
    const order = mockGroupBuyStore().orders.find((o) => o.orderNo === orderNo);
    if (!order) throw notFound();
    return order;
}

/** 요청 줄을 뺀 지금 고를 수 있는 수량(서버 몫 흉내) */
function refreshSelectable(order: MyGroupBuyOrderDetail): void {
    const inOpen = new Map<number, number>();
    for (const request of order.requests) {
        if (!OPEN.includes(request.status)) continue;
        for (const item of request.items) inOpen.set(item.skuId, (inOpen.get(item.skuId) ?? 0) + item.qty);
    }
    const selectableStatus = ['JOINED', 'CONFIRMED', 'PREPARING', 'DELIVERED'].includes(order.status);
    for (const item of order.items) item.selectableQty = selectableStatus ? Math.max(0, item.qty - item.canceledQty - item.returnedQty - (inOpen.get(item.skuId) ?? 0)) : 0;
}

/** 요청이 닫힌 뒤 단추 되살리기(서버 몫 흉내) */
function refreshActions(order: MyGroupBuyOrderDetail): void {
    const open = order.requests.some((r) => OPEN.includes(r.status));
    const immediate = order.groupBuy.fulfillment === 'IMMEDIATE';
    const live = order.items.some((i) => i.qty - i.canceledQty - i.returnedQty > 0);
    order.actions.requestReturn = !open && live && order.status === 'DELIVERED' && order.defectReturnUntil !== null && new Date(order.defectReturnUntil).getTime() > Date.now();
    order.actions.requestCancel = !open && live && (order.status === 'PREPARING' || (!immediate && order.status === 'CONFIRMED'));
    order.actions.cancelNow = !open && live && (order.status === 'JOINED' || (immediate && order.status === 'CONFIRMED'));
    order.actions.delayCancel = !!order.delayNotice && ['JOINED', 'CONFIRMED', 'PREPARING'].includes(order.status);
    refreshSelectable(order);
}

function summaryOf(order: MyGroupBuyOrderDetail): MyGroupBuyOrderPage['items'][number] {
    const fee = order.requests.some((r) => r.status === 'AWAITING_PAYMENT');
    return {
        orderNo: order.orderNo,
        groupBuyId: order.groupBuy.id,
        title: order.groupBuy.title,
        thumbnailUrl: order.groupBuy.thumbnailUrl,
        status: order.status,
        // 줄 하나뿐이고 이름이 공구 이름 앞부분과 같으면(축 없는 본 상품) 줄 이름을 따로 보이지 않는다
        firstItemName: order.items.length === 1 && order.groupBuy.title.startsWith(order.items[0]!.skuName) ? null : (order.items[0]?.skuName ?? null),
        itemCount: order.items.length,
        amount: order.amount,
        refundedAmount: order.refundedAmount,
        createdAt: order.createdAt,
        attention:
            order.status === 'PENDING_PAYMENT' ? 'PAY' : order.actions.supplyChoice ? 'SUPPLY_CHOICE' : fee ? 'RETURN_FEE' : order.actions.delayCancel ? 'DELAY' : null,
    };
}

export async function mockMyOrders(page: number): Promise<MyGroupBuyOrderPage> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('orders-fail')) throw MOCK_ERRORS.internal();
    const size = 10;
    const all = flags.has('orders-empty') ? [] : mockGroupBuyStore().orders;
    return { items: all.slice(page * size, page * size + size).map(summaryOf), page, size, total: all.length };
}

export async function mockMyOrder(orderNo: string): Promise<MyGroupBuyOrderDetail> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('order-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('order-not-found')) throw notFound();
    const order = find(orderNo);
    // 결제 대기의 선점이 지났으면 조회 때 정리(본인 주문만 — 결정 85)
    if (order.status === 'PENDING_PAYMENT' && order.reservedUntil && new Date(order.reservedUntil).getTime() <= Date.now()) {
        order.status = 'EXPIRED';
        order.reservedUntil = null;
        order.actions = { ...order.actions, payAgain: false };
    }
    refreshSelectable(order);
    return mockClone(order);
}

export async function mockReopenPayment(orderNo: string): Promise<GroupBuyPaymentStart> {
    await mockDelay();
    const s = mockGroupBuyStore();
    const order = find(orderNo);
    const left = order.reservedUntil ? new Date(order.reservedUntil).getTime() - Date.now() : 0;
    if (order.status !== 'PENDING_PAYMENT' || mockFlags().has('gb-hold-expired') || left < MOCK_TIME.MIN) {
        throw new ApiError({ status: 409, code: 'GROUP_BUY_HOLD_EXPIRED', message: '결제할 수 있는 시간이 끝났어요. 공동구매 화면에서 다시 주문해 주세요.' });
    }
    // 아직 유효한(결과를 남기지 않은) 결제가 있으면 그것을 돌려준다(결정 63)
    for (const [paymentId, payment] of s.payments) {
        if (payment.orderType === 'GROUP_BUY' && payment.orderRefId === orderNo && payment.outcome === null) {
            return { orderType: 'GROUP_BUY', orderRefId: orderNo, orderNo, expiresAt: order.reservedUntil!, customer: null, checkout: { gateway: 'FAKE', paymentId, orderName: order.groupBuy.title, amount: order.amount, currency: 'KRW', storeId: null, channelKey: null } };
        }
    }
    return mockPaymentStart('GROUP_BUY', orderNo, orderNo, order.groupBuy.title, order.amount, order.reservedUntil!);
}

export async function mockRevealAddress(orderNo: string): Promise<MyGroupBuyAddress> {
    await mockDelay();
    if (mockFlags().has('order-reveal-fail')) throw MOCK_ERRORS.internal();
    const order = find(orderNo);
    if (!order.actions.revealAddress) throw new ApiError({ status: 409, code: 'GROUP_BUY_ADDRESS_PURGED', message: '보관 기간이 지나 배송지를 지웠어요.' });
    return { ...FULL_ADDRESS, memo: order.address.memo, masked: false, purged: false };
}

export async function mockUpdateAddress(orderNo: string, body: GroupBuyAddressInput): Promise<MyGroupBuyOrderDetail> {
    await mockDelay();
    if (mockFlags().has('order-address-fail')) throw new ApiError({ status: 409, code: 'GROUP_BUY_ADDRESS_LOCKED', message: '배송지를 고칠 수 있는 기한이 지났어요.' });
    const order = find(orderNo);
    const fields: Record<string, string> = {};
    if (!body.recipient.trim()) fields.recipient = '받는 분을 넣어 주세요.';
    if (!/^0\d{8,10}$/.test(body.phone.replace(/-/g, ''))) fields.phone = '전화번호를 다시 확인해 주세요.';
    if (!/^\d{5}$/.test(body.postcode)) fields.postcode = '우편번호 5자리를 넣어 주세요.';
    if (!body.address1.trim()) fields.address1 = '주소를 넣어 주세요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    order.address = {
        recipient: `${body.recipient.slice(0, 1)}*${body.recipient.slice(2)}`,
        phone: '010-****-' + body.phone.replace(/-/g, '').slice(-4),
        postcode: body.postcode,
        address1: body.address1.split(' ').slice(0, 2).join(' ') + ' ****',
        address2: '****',
        memo: body.memo,
        masked: true,
        purged: false,
    };
    return mockClone(order);
}

/* ── 계산(서버 몫 흉내) ── */

function shippingOf(order: MyGroupBuyOrderDetail) {
    return mockGroupBuyStore().groupBuys.find((g) => g.id === order.groupBuy.id)!.shipping;
}

/** 이 줄들을 빼고 남는 상품 금액 */
function remainingAfter(order: MyGroupBuyOrderDetail, lines: GroupBuyItemQty[]): number {
    return order.items.reduce((sum, item) => {
        const taken = lines.filter((l) => l.skuId === item.skuId).reduce((n, l) => n + l.qty, 0);
        return sum + (item.qty - item.canceledQty - item.returnedQty - taken) * item.unitPrice;
    }, 0);
}

function itemsOf(order: MyGroupBuyOrderDetail, lines: GroupBuyItemQty[]): number {
    return lines.reduce((sum, line) => sum + (order.items.find((i) => i.skuId === line.skuId)?.unitPrice ?? 0) * line.qty, 0);
}

function checkSelection(order: MyGroupBuyOrderDetail, lines: GroupBuyItemQty[]): void {
    refreshSelectable(order);
    const fields: Record<string, string> = {};
    if (lines.length === 0 || lines.every((l) => l.qty < 1)) fields.items = '취소·반품할 상품과 수량을 골라 주세요.';
    lines.forEach((line, index) => {
        const item = order.items.find((i) => i.skuId === line.skuId);
        if (!item || line.qty > item.selectableQty) fields[`items[${index}].qty`] = '고를 수 있는 수량보다 많아요.';
    });
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
}

function cancelCalc(order: MyGroupBuyOrderDetail, lines: GroupBuyItemQty[]): GroupBuyCancelPreview {
    const shipping = shippingOf(order);
    const itemRefund = itemsOf(order, lines);
    const rest = remainingAfter(order, lines);
    const full = rest === 0;
    const shippingRefund = full ? order.shippingFee : 0;
    const reshippingFee = !full && order.shippingFee === 0 && shipping.freeShippingOver !== null && rest < shipping.freeShippingOver ? shipping.fee : 0;
    return { refundAmount: itemRefund + shippingRefund - reshippingFee, itemRefund, shippingRefund, reshippingFee };
}

function returnCalc(order: MyGroupBuyOrderDetail, lines: GroupBuyReturnLine[]): GroupBuyReturnPreview {
    const shipping = shippingOf(order);
    const company = lines.some((l) => l.reason === 'DEFECT' || l.reason === 'WRONG_ITEM');
    const itemRefund = itemsOf(order, lines);
    const rest = remainingAfter(order, lines);
    const full = rest === 0;
    const free = order.shippingFee === 0;
    const outcome = (method: GroupBuyCollectionMethod): GroupBuyReturnOutcome => {
        if (company) {
            const shippingRefund = full ? order.shippingFee : 0;
            return { refundAmount: itemRefund + shippingRefund, itemRefund, shippingRefund, returnFee: 0, reshippingFee: 0, compensationEligible: method === 'CUSTOMER_SEND' };
        }
        let reshippingFee = 0;
        if (!full && free && shipping.freeShippingOver !== null && rest < shipping.freeShippingOver) reshippingFee = shipping.fee;
        if (full && free && shipping.returnFeeMode === 'ROUND_TRIP' && method === 'COMPANY_PICKUP') reshippingFee = shipping.fee;
        const returnFee = method === 'COMPANY_PICKUP' ? shipping.returnFee : 0;
        return { refundAmount: itemRefund - reshippingFee - returnFee, itemRefund, shippingRefund: 0, returnFee, reshippingFee, compensationEligible: false };
    };
    return { feeBearer: company ? 'COMPANY' : 'CUSTOMER', byMethod: { COMPANY_PICKUP: outcome('COMPANY_PICKUP'), CUSTOMER_SEND: outcome('CUSTOMER_SEND') }, returnAddress: RETURN_ADDRESS };
}

/** 취소한 줄을 반영하고 환불을 「진행 중」으로 더한다 */
function applyCancel(order: MyGroupBuyOrderDetail, lines: GroupBuyItemQty[], amount: number, cause: 'USER_CANCEL' | 'DELAY' | 'SUPPLY_FAILURE'): void {
    for (const line of lines) {
        const item = order.items.find((i) => i.skuId === line.skuId);
        if (item) item.canceledQty += line.qty;
    }
    order.refundedAmount += amount;
    order.refunds.unshift({ id: Date.now(), cause, amount, progress: 'IN_PROGRESS', method: 'PG_CANCEL', requestedAt: at(0), completedAt: null });
    if (order.items.every((i) => i.qty - i.canceledQty === 0)) {
        order.status = 'CANCELED';
        order.actions = { ...order.actions, editAddress: false, cancelNow: false, requestCancel: false, delayCancel: false, supplyChoice: false };
        for (const request of order.requests) if (OPEN.includes(request.status)) request.status = 'SUPERSEDED';
    }
    refreshActions(order);
}

/** 본인 취소 줄(`lines[{ skuId, qty }]` — 서버 2b-2)을 요청 줄 모양으로(둘 다 단품 단위) */
function toItemLines(order: MyGroupBuyOrderDetail, body: GroupBuyCancelRequest): GroupBuyItemQty[] {
    return body.lines.map((l) => ({ skuId: l.skuId, qty: l.qty }));
}

/** 본 상품을 모두 취소하면서 추가 구성상품을 남기면 409 `GROUP_BUY_ADDON_ONLY {skuIds}`(서버 2b-2) — 남는 추가 구성상품 단품 ID */
function addonLeftAlone(order: MyGroupBuyOrderDetail, lines: GroupBuyItemQty[]): number[] {
    const left = (i: MyGroupBuyOrderItem) => i.qty - i.canceledQty - i.returnedQty - lines.filter((l) => l.skuId === i.skuId).reduce((n, l) => n + l.qty, 0);
    if (order.items.some((i) => i.kind === 'MAIN' && left(i) > 0)) return [];
    return order.items.filter((i) => i.kind === 'ADDON' && left(i) > 0).map((i) => i.skuId);
}

export async function mockCancelPreview(orderNo: string, body: GroupBuyCancelRequest): Promise<GroupBuyCancelPreview> {
    await mockDelay();
    if (mockFlags().has('order-preview-fail')) throw MOCK_ERRORS.internal();
    const order = find(orderNo);
    const lines = toItemLines(order, body);
    checkSelection(order, lines);
    return cancelCalc(order, lines);
}

export async function mockCancelNow(orderNo: string, body: GroupBuyCancelRequest): Promise<MyGroupBuyOrderDetail> {
    await mockDelay();
    if (mockFlags().has('order-cancel-fail')) throw new ApiError({ status: 409, code: 'GROUP_BUY_ORDER_STATE', message: '그 사이 상품 준비가 시작돼 바로 취소할 수 없어요. 취소 요청으로 해 주세요.' });
    const order = find(orderNo);
    if (!order.actions.cancelNow) throw new ApiError({ status: 409, code: 'GROUP_BUY_ORDER_STATE', message: '지금은 바로 취소할 수 없어요.' });
    const lines = toItemLines(order, body);
    checkSelection(order, lines);
    const alone = addonLeftAlone(order, lines);
    if (alone.length > 0) throw new ApiError({ status: 409, code: 'GROUP_BUY_ADDON_ONLY', message: '추가 구성상품은 본 상품 없이 받을 수 없어요. 함께 취소해 주세요.', extras: { skuIds: alone } });
    applyCancel(order, lines, cancelCalc(order, lines).refundAmount, 'USER_CANCEL');
    return mockClone(order);
}

export async function mockReturnPreview(orderNo: string, body: { items: GroupBuyReturnLine[] }): Promise<GroupBuyReturnPreview> {
    await mockDelay();
    if (mockFlags().has('order-preview-fail')) throw MOCK_ERRORS.internal();
    const order = find(orderNo);
    checkSelection(order, body.items);
    return returnCalc(order, body.items);
}

export async function mockCreateRequest(orderNo: string, body: GroupBuyRequestCreate): Promise<GroupBuyRequestCreated> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('order-request-fail')) throw MOCK_ERRORS.internal();
    const order = find(orderNo);
    if (order.requests.some((r) => OPEN.includes(r.status))) throw new ApiError({ status: 409, code: 'GROUP_BUY_REQUEST_OPEN', message: '이미 처리 중인 요청이 있어요.' });
    const lines = body.items.filter((l) => l.qty > 0);
    checkSelection(order, lines);
    const fields: Record<string, string> = {};
    if (body.type === 'RETURN') {
        lines.forEach((line, index) => {
            if (!line.reason) fields[`items[${index}].reason`] = '사유를 골라 주세요.';
        });
        if (!body.collectionMethod) fields.collectionMethod = '회수 방식을 골라 주세요.';
        const defect = lines.some((l) => l.reason === 'DEFECT' || l.reason === 'WRONG_ITEM');
        if (body.resolution === 'EXCHANGE' && (!defect || lines.some((l) => l.reason === 'CHANGE_OF_MIND' || l.reason === 'OTHER'))) fields.resolution = '교환은 하자·오배송 상품만 할 수 있어요.';
        if (body.photoIds.length > 3) fields.photoIds = '사진은 3장까지 올릴 수 있어요.';
    }
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    let amount: number;
    if (body.type === 'CANCEL') amount = cancelCalc(order, lines).refundAmount;
    else {
        const preview = returnCalc(order, lines as GroupBuyReturnLine[]);
        amount = body.resolution === 'EXCHANGE' ? 0 : preview.byMethod[body.collectionMethod!].refundAmount;
    }
    if (flags.has('order-amount-changed') || (body.expectedAmount !== null && body.expectedAmount !== amount)) {
        throw new ApiError({ status: 409, code: 'GROUP_BUY_AMOUNT_CHANGED', message: '환불 예정 금액이 바뀌었어요. 다시 확인해 주세요.', extras: { expectedAmount: body.expectedAmount, currentAmount: amount } });
    }
    const due = amount < 0 ? -amount : null;
    const request: MyGroupBuyRequest = {
        id: Date.now() % 1_000_000,
        type: body.type,
        status: due ? 'AWAITING_PAYMENT' : 'REQUESTED',
        items: lines.map((l) => {
            const item = order.items.find((i) => i.skuId === l.skuId)!;
            return { skuId: l.skuId, skuName: item.skuName, kind: item.kind, qty: l.qty, reason: l.reason };
        }),
        collectionMethod: body.collectionMethod,
        resolution: body.resolution,
        previewAmount: amount,
        refundAmount: null,
        returnFeeDue: due,
        rejectReasonCode: null,
        pickupTrackingNo: null,
        createdAt: at(0),
        handledAt: null,
        actions: { withdraw: true, payReturnFee: !!due },
    };
    order.requests.unshift(request);
    refreshActions(order);
    const returnFeePayment = due ? mockPaymentStart('GROUP_BUY_RETURN_FEE', String(request.id), orderNo, `${order.groupBuy.title} 반품 비용`, due, at(30 * MOCK_TIME.MIN)) : null;
    return { request: mockClone(request), returnFeePayment };
}

export async function mockRequestFeePayment(orderNo: string, requestId: number): Promise<GroupBuyPaymentStart> {
    await mockDelay();
    const s = mockGroupBuyStore();
    const order = find(orderNo);
    const request = order.requests.find((r) => r.id === requestId);
    if (!request || request.status !== 'AWAITING_PAYMENT' || !request.returnFeeDue) throw new ApiError({ status: 409, code: 'GROUP_BUY_REQUEST_STATE', message: '결제할 반품 비용이 없어요.' });
    for (const [paymentId, payment] of s.payments) {
        if (payment.orderType === 'GROUP_BUY_RETURN_FEE' && payment.orderRefId === String(requestId) && payment.outcome === null) {
            return { orderType: 'GROUP_BUY_RETURN_FEE', orderRefId: String(requestId), orderNo, expiresAt: payment.expiresAt, customer: null, checkout: { gateway: 'FAKE', paymentId, orderName: `${order.groupBuy.title} 반품 비용`, amount: payment.amount, currency: 'KRW', storeId: null, channelKey: null } };
        }
    }
    return mockPaymentStart('GROUP_BUY_RETURN_FEE', String(requestId), orderNo, `${order.groupBuy.title} 반품 비용`, request.returnFeeDue, at(30 * MOCK_TIME.MIN));
}

export async function mockWithdrawRequest(orderNo: string, requestId: number): Promise<MyGroupBuyOrderDetail> {
    await mockDelay();
    const order = find(orderNo);
    const request = order.requests.find((r) => r.id === requestId);
    if (!request || !OPEN.includes(request.status) || request.status === 'APPROVED') throw new ApiError({ status: 409, code: 'GROUP_BUY_REQUEST_STATE', message: '이미 처리된 요청이라 철회할 수 없어요.' });
    request.status = 'WITHDRAWN';
    request.handledAt = at(0);
    request.actions = { withdraw: false, payReturnFee: false };
    refreshActions(order);
    return mockClone(order);
}

export async function mockDelayCancel(orderNo: string): Promise<MyGroupBuyOrderDetail> {
    await mockDelay();
    const order = find(orderNo);
    if (!order.actions.delayCancel || !order.delayNotice) throw new ApiError({ status: 409, code: 'GROUP_BUY_ORDER_STATE', message: '지금은 지연 취소를 할 수 없어요.' });
    const lines = order.items.map((i) => ({ skuId: i.skuId, qty: i.qty - i.canceledQty - i.returnedQty })).filter((l) => l.qty > 0);
    applyCancel(order, lines, order.delayNotice.refundAmount, 'DELAY');
    return mockClone(order);
}

export async function mockSupplyChoice(orderNo: string, body: GroupBuySupplyChoiceRequest): Promise<MyGroupBuyOrderDetail> {
    await mockDelay();
    const order = find(orderNo);
    if (!order.supplyChoice || !order.actions.supplyChoice) throw new ApiError({ status: 409, code: 'GROUP_BUY_ORDER_STATE', message: '이미 답했거나 기한이 지났어요.' });
    order.supplyChoice.choice = body.choice;
    order.actions.supplyChoice = false;
    if (body.choice === 'CANCEL_ALL') {
        const lines = order.items.map((i) => ({ skuId: i.skuId, qty: i.qty - i.canceledQty - i.returnedQty })).filter((l) => l.qty > 0);
        applyCancel(order, lines, order.supplyChoice.cancelAllRefund, 'SUPPLY_FAILURE');
    }
    return mockClone(order);
}

export async function mockUploadPhoto(orderNo: string, file: File): Promise<GroupBuyRequestPhoto> {
    await mockDelay();
    find(orderNo);
    if (mockFlags().has('order-photo-fail')) throw MOCK_ERRORS.internal();
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) throw MOCK_ERRORS.validation({ file: 'JPEG·PNG·WebP 사진만 올릴 수 있어요.' });
    if (file.size > 5 * 1024 * 1024) throw MOCK_ERRORS.validation({ file: '사진은 5MB까지 올릴 수 있어요.' });
    return { id: `photo-${Date.now()}`, url: URL.createObjectURL(file) };
}
