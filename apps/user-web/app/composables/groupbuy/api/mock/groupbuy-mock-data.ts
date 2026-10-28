// MOCK: 공구 목업의 메모리 데이터 — `groupbuy-mock.ts`(목록·상세·견적·주문·결제)와 `groupbuy-order-mock.ts`(내 주문)가 같이 쓴다. 서버가 붙으면 목업 폴더째 지운다.
// 새로고침하면 처음 상태로 돌아간다. 시각은 「지금」 기준으로 만든다(마감 임박·선점 남은 시간이 늘 보이게).
//
// ── 시드(계약 결정 11 의 local 시드 넷 + 예정 하나 + 종료 몇 개) ─
//   101 성사형 모집 중(마감 후 발주, min 30 · 18/100, 발송 예정일)
//   102 선착순 즉시 발송 · 옵션 셋(본체 세트 47,000 · 예비 배터리 5,000 · 확장 센서 팩 20,000 — 잠시 구매 불가) · 추가 입고 20개
//   103 마감 임박 · 남은 수량 적음 · 2차 공구(1차 = 104) · 마감 무렵 연장 안내
//   104 다 팔려 끝난 1차(SOLD_OUT) — 「다음 차수 알림 받기」
//   105 모집 예정(가격 미리 공개)
//   106 기간 끝 · 불발(CLOSED_FAILED · TIME_UP)
//   107 조기 마감(EARLY_CLOSED) · 성사 · 발주(ORDERED) · 발송 지연 공지 · 옵션 하나 공급 불가
//   108 운영 종료(CANCELED)
//
// ── 배송비 정책(모집 시작 때 고정한 사본) ─
//   STANDARD: 3,000 · 무료 50,000 · 반품 3,000 · 편도   ROUND: 3,000 · 무료 50,000 · 반품 3,000 · 왕복(103만)

import type { GroupBuyDetail, GroupBuyShipping } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { MyGroupBuyOrderDetail } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

export const at = (offsetMs: number): string => new Date(Date.now() + offsetMs).toISOString();
export const ymd = (offsetDays: number): string => new Date(Date.now() + offsetDays * DAY + 9 * HOUR).toISOString().slice(0, 10);
export const MOCK_TIME = { MIN, HOUR, DAY };

/** 목업 그림 — 바깥 주소를 쓰지 않으려고 색 면 + 글자 SVG(data URL) */
function picture(label: string, hue: number): string {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600"><rect width="800" height="600" fill="hsl(${hue},45%,88%)"/><circle cx="400" cy="260" r="120" fill="hsl(${hue},40%,70%)"/><text x="400" y="470" font-family="sans-serif" font-size="44" text-anchor="middle" fill="hsl(${hue},40%,30%)">${label}</text></svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

const STANDARD: GroupBuyShipping = { fee: 3000, freeShippingOver: 50000, returnFee: 3000, returnFeeMode: 'ONE_WAY' };
const ROUND: GroupBuyShipping = { fee: 3000, freeShippingOver: 50000, returnFee: 3000, returnFeeMode: 'ROUND_TRIP' };

/** 정화된 HTML 이라고 가정한 고정 문자열(서버가 공용 편집기 정화를 거친다 — 결정 2) */
const DESC = (name: string) =>
    `<p><strong>${name}</strong>는 로봇 교육·취미용으로 만든 제품이에요.</p><ul><li>구성품은 아래 사양을 확인해 주세요.</li><li>실제 색상은 화면과 조금 다를 수 있어요.</li></ul><p>사용 설명서는 상자 안에 들어 있어요.</p>`;

function base(over: Partial<GroupBuyDetail> & Pick<GroupBuyDetail, 'id' | 'title'>): GroupBuyDetail {
    return {
        thumbnailUrl: picture(over.title.slice(0, 8), (over.id * 47) % 360),
        status: 'RECRUITING',
        phase: 'ONGOING',
        closeReason: null,
        fulfillment: 'AFTER_CLOSE',
        groupPrice: 10000,
        priceFrom: false,
        normalPrice: null,
        minQty: 0,
        maxQty: 100,
        currentQty: 0,
        remainingQty: 100,
        stockState: 'AVAILABLE',
        startAt: at(-2 * DAY),
        endAt: at(5 * DAY),
        extendedUntil: null,
        closingSoon: false,
        roundNo: 1,
        images: [
            { url: picture(over.title.slice(0, 8), (over.id * 47) % 360), alt: `${over.title} 대표 이미지` },
            { url: picture('구성품', (over.id * 47 + 40) % 360), alt: `${over.title} 구성품` },
        ],
        descriptionHtml: DESC(over.title),
        summary: null,
        spec: null,
        normalPriceBasis: null,
        shipPlannedAt: null,
        shipWithinBusinessDays: null,
        shipping: STANDARD,
        withdrawalRestriction: null,
        extensionMode: 'DEMAND',
        extensionMaxMinutes: 30,
        addedQty: 0,
        maxQtyPerUser: null,
        roundLimitShared: false,
        options: [],
        delayNotice: null,
        purchase: { reason: null },
        actions: { order: true, watchRestock: false, watchClosingSoon: true, watchNextRound: false },
        watches: { RESTOCK: false, CLOSING_SOON: false, NEXT_ROUND: false },
        myOrderNo: null,
        ...over,
    };
}

export function seedGroupBuys(): GroupBuyDetail[] {
    return [
        base({
            id: 101,
            title: '로봇 청소기 X1 공동구매',
            fulfillment: 'AFTER_CLOSE',
            groupPrice: 289000,
            normalPrice: 349000,
            normalPriceBasis: 'OFFICIAL_STORE',
            minQty: 30,
            maxQty: 100,
            currentQty: 18,
            remainingQty: 80,
            endAt: at(3 * DAY),
            shipPlannedAt: ymd(14),
            maxQtyPerUser: 2,
            summary: '30대가 모이면 성사돼요. 성사되면 공급처에 한 번에 발주해 발송 예정일에 보내 드려요.',
            spec: '크기 350 × 350 × 95mm\n무게 3.2kg\n배터리 5,200mAh (약 150분)\n구성 본체 · 충전 스테이션 · 사이드 브러시 2개 · 설명서',
            withdrawalRestriction: '포장 비닐만 뜯은 것은 반품할 수 있어요. 본체를 사용해 가치가 크게 떨어진 경우(먼지통 사용 흔적 등)는 단순 변심 반품이 어려워요.',
        }),
        base({
            id: 102,
            title: '코딩 로봇 키트 R1 선착순',
            fulfillment: 'IMMEDIATE',
            groupPrice: 5000,
            priceFrom: true,
            normalPrice: 59000,
            normalPriceBasis: 'PREV_SALE',
            minQty: 0,
            maxQty: 220,
            currentQty: 141,
            remainingQty: 59,
            endAt: at(10 * DAY),
            shipWithinBusinessDays: 3,
            addedQty: 20,
            maxQtyPerUser: 5,
            summary: '재고를 가진 선착순 공구예요. 결제가 확정되면 3영업일 안에 보내 드려요.',
            spec: '본체 세트: 메인보드 · 모터 2개 · 바퀴 · 배터리 1개\n예비 배터리: 1,200mAh 리튬폴리머\n확장 센서 팩: 거리 · 색상 · 소리 센서',
            options: [
                { id: 1021, name: '본체 세트', extraPrice: 42000, unitPrice: 47000, remainingQty: 31, maxQty: 120, stockState: 'AVAILABLE', watchingRestock: false, canWatchRestock: false },
                { id: 1022, name: '예비 배터리', extraPrice: 0, unitPrice: 5000, remainingQty: 59, maxQty: null, stockState: 'AVAILABLE', watchingRestock: false, canWatchRestock: false },
                { id: 1023, name: '확장 센서 팩', extraPrice: 15000, unitPrice: 20000, remainingQty: 0, maxQty: 40, stockState: 'TEMP_SOLD_OUT', watchingRestock: false, canWatchRestock: true },
            ],
            actions: { order: true, watchRestock: false, watchClosingSoon: true, watchNextRound: false },
        }),
        base({
            id: 103,
            title: '데스크 로봇 팔 미니 2차',
            fulfillment: 'IMMEDIATE',
            groupPrice: 129000,
            normalPrice: 139000,
            normalPriceBasis: 'PREV_ROUND',
            minQty: 0,
            maxQty: 50,
            currentQty: 44,
            remainingQty: 4,
            endAt: at(3 * HOUR),
            closingSoon: true,
            roundNo: 2,
            shipWithinBusinessDays: 2,
            shipping: ROUND,
            maxQtyPerUser: 1,
            roundLimitShared: true,
            summary: '1차가 다 팔려 2차로 다시 열었어요. 1차에서 산 수량도 1인 한도에 함께 세요.',
            spec: '관절 4축 · 최대 하중 200g · USB-C 전원 · 앱 연동',
            actions: { order: true, watchRestock: false, watchClosingSoon: true, watchNextRound: false },
        }),
        base({
            id: 104,
            title: '데스크 로봇 팔 미니 1차',
            status: 'CLOSED_SUCCESS',
            phase: 'ENDED',
            closeReason: 'SOLD_OUT',
            fulfillment: 'IMMEDIATE',
            groupPrice: 139000,
            minQty: 0,
            maxQty: 30,
            currentQty: 30,
            remainingQty: 0,
            stockState: 'SOLD_OUT',
            startAt: at(-20 * DAY),
            endAt: at(-12 * DAY),
            shipWithinBusinessDays: 2,
            purchase: { reason: 'NOT_RECRUITING' },
            actions: { order: false, watchRestock: false, watchClosingSoon: false, watchNextRound: true },
        }),
        base({
            id: 105,
            title: '휴머노이드 교육 로봇 H5 예약',
            status: 'DRAFT',
            phase: 'UPCOMING',
            fulfillment: 'AFTER_CLOSE',
            groupPrice: 890000,
            normalPrice: 1090000,
            normalPriceBasis: 'MARKET',
            minQty: 20,
            maxQty: 60,
            currentQty: 0,
            remainingQty: 60,
            startAt: at(2 * DAY),
            endAt: at(12 * DAY),
            shipPlannedAt: ymd(30),
            purchase: { reason: 'NOT_STARTED' },
            actions: { order: false, watchRestock: false, watchClosingSoon: false, watchNextRound: false },
        }),
        base({
            id: 106,
            title: '자율주행 RC카 키트',
            status: 'CLOSED_FAILED',
            phase: 'ENDED',
            closeReason: 'TIME_UP',
            minQty: 40,
            maxQty: 80,
            currentQty: 12,
            remainingQty: 68,
            groupPrice: 159000,
            startAt: at(-16 * DAY),
            endAt: at(-2 * DAY),
            shipPlannedAt: ymd(10),
            purchase: { reason: 'NOT_RECRUITING' },
            actions: { order: false, watchRestock: false, watchClosingSoon: false, watchNextRound: false },
        }),
        base({
            id: 107,
            title: '스마트 화분 로봇 P2',
            status: 'ORDERED',
            phase: 'ENDED',
            closeReason: 'EARLY_CLOSED',
            minQty: 10,
            maxQty: 50,
            currentQty: 37,
            remainingQty: 13,
            groupPrice: 39000,
            priceFrom: true,
            startAt: at(-15 * DAY),
            endAt: at(-5 * DAY),
            shipPlannedAt: ymd(9),
            options: [
                { id: 1071, name: '화이트', extraPrice: 0, unitPrice: 39000, remainingQty: 0, maxQty: null, stockState: 'AVAILABLE', watchingRestock: false, canWatchRestock: false },
                { id: 1072, name: '민트(공급 불가)', extraPrice: 3000, unitPrice: 42000, remainingQty: 0, maxQty: 12, stockState: 'SOLD_OUT', watchingRestock: false, canWatchRestock: false },
            ],
            delayNotice: { previousPlannedAt: ymd(2), newPlannedAt: ymd(9), reason: '공급처 통관이 늦어져 입고가 일주일 미뤄졌어요.', noticedAt: at(-1 * DAY) },
            purchase: { reason: 'NOT_RECRUITING' },
            actions: { order: false, watchRestock: false, watchClosingSoon: false, watchNextRound: false },
        }),
        base({
            id: 108,
            title: '로봇 강아지 펫봇 S',
            status: 'CANCELED',
            phase: 'ENDED',
            closeReason: 'CANCELED',
            minQty: 20,
            maxQty: 40,
            currentQty: 9,
            remainingQty: 31,
            groupPrice: 249000,
            startAt: at(-9 * DAY),
            endAt: at(-1 * DAY),
            shipPlannedAt: ymd(20),
            purchase: { reason: 'NOT_RECRUITING' },
            actions: { order: false, watchRestock: false, watchClosingSoon: false, watchNextRound: false },
        }),
    ];
}

/** 가린 배송지 — 주문 시드가 같이 쓴다 */
const MASKED = { recipient: '홍*동', phone: '010-****-5678', postcode: '06236', address1: '서울특별시 강남구 ****', address2: '****', memo: '문 앞에 놓아 주세요', masked: true, purged: false };
/** 「전체 보기」 값(목업) */
export const FULL_ADDRESS = { recipient: '홍길동', phone: '010-1234-5678', postcode: '06236', address1: '서울특별시 강남구 테헤란로 000', address2: '0동 000호', memo: '문 앞에 놓아 주세요' };

const NO_ACTIONS = { payAgain: false, revealAddress: true, editAddress: false, cancelNow: false, requestCancel: false, requestReturn: false, delayCancel: false, supplyChoice: false };

function gbRef(gb: GroupBuyDetail): MyGroupBuyOrderDetail['groupBuy'] {
    return { id: gb.id, title: gb.title, thumbnailUrl: gb.thumbnailUrl, status: gb.status, fulfillment: gb.fulfillment, closeReason: gb.closeReason, roundNo: gb.roundNo };
}

function order(over: Partial<MyGroupBuyOrderDetail> & Pick<MyGroupBuyOrderDetail, 'orderNo' | 'status' | 'groupBuy' | 'items'>): MyGroupBuyOrderDetail {
    const itemsAmount = over.items.reduce((sum, item) => sum + item.itemAmount, 0);
    return {
        itemsAmount,
        shippingFee: 0,
        amount: itemsAmount,
        refundedAmount: 0,
        compensationAmount: 0,
        createdAt: at(-3 * DAY),
        paidAt: at(-3 * DAY + 2 * MIN),
        reservedUntil: null,
        shipPlannedAt: null,
        shipWithinBusinessDays: null,
        address: { ...MASKED },
        addressEditableUntilText: null,
        shipment: null,
        delayNotice: null,
        supplyChoice: null,
        requests: [],
        refunds: [],
        returnUntil: null,
        defectReturnUntil: null,
        actions: { ...NO_ACTIONS },
        ...over,
    };
}

/** 내 주문 시드 — 여러 상태(배송완료·발송 전·결제 대기·환불 진행·공급 불가 선택 대기·지연 공지·발송 중·만료·취소) */
export function seedOrders(gbs: GroupBuyDetail[]): MyGroupBuyOrderDetail[] {
    const gb = (id: number) => gbs.find((g) => g.id === id)!;
    const g101 = gb(101);
    const g102 = gb(102);
    const g103 = gb(103);
    const g107 = gb(107);
    return [
        // 배송완료 — 결정 68 예시(47,000 + 5,000 무료배송 → B 5,000 변심 반품: 업체회수 −1,000 / 직접 발송 +2,000)
        order({
            orderNo: 'G2026102800012',
            status: 'DELIVERED',
            groupBuy: gbRef(g102),
            createdAt: at(-4 * DAY),
            paidAt: at(-4 * DAY + 2 * MIN),
            items: [
                { id: 1, optionName: '본체 세트', qty: 1, unitPrice: 47000, itemAmount: 47000, canceledQty: 0, returnedQty: 0, selectableQty: 1 },
                { id: 2, optionName: '예비 배터리', qty: 1, unitPrice: 5000, itemAmount: 5000, canceledQty: 0, returnedQty: 0, selectableQty: 1 },
            ],
            shipWithinBusinessDays: 3,
            shipment: { carrierName: 'CJ대한통운', trackingNo: '123456789012', trackingUrl: 'https://trace.cjlogistics.com/next/tracking.html?wblNo=123456789012', kind: 'ORIGINAL', shippedAt: at(-3 * DAY), deliveredAt: at(-1 * DAY) },
            returnUntil: at(6 * DAY),
            defectReturnUntil: at(89 * DAY),
            actions: { ...NO_ACTIONS, requestReturn: true },
        }),
        // 발송 전(성사형 모집 중 JOINED) — 본인 즉시 취소(일부 줄·수량)
        order({
            orderNo: 'G2026102700005',
            status: 'JOINED',
            groupBuy: gbRef(g101),
            createdAt: at(-1 * DAY),
            paidAt: at(-1 * DAY + MIN),
            items: [{ id: 3, optionName: null, qty: 2, unitPrice: 289000, itemAmount: 578000, canceledQty: 0, returnedQty: 0, selectableQty: 2 }],
            shipPlannedAt: g101.shipPlannedAt,
            addressEditableUntilText: '공구가 발주되기 전까지',
            actions: { ...NO_ACTIONS, editAddress: true, cancelNow: true },
        }),
        // 결제 대기 — 선점 남은 시간 · 결제 다시 열기
        order({
            orderNo: 'G2026102800020',
            status: 'PENDING_PAYMENT',
            groupBuy: gbRef(g103),
            createdAt: at(-1 * MIN),
            paidAt: null,
            reservedUntil: at(4 * MIN),
            items: [{ id: 4, optionName: null, qty: 1, unitPrice: 129000, itemAmount: 129000, canceledQty: 0, returnedQty: 0, selectableQty: 0 }],
            shipWithinBusinessDays: 2,
            actions: { ...NO_ACTIONS, payAgain: true, revealAddress: false },
        }),
        // 환불 진행 — 전체 반품 완료(REFUNDED) · 환불 진행 중 + 이전 요청 거절·철회
        order({
            orderNo: 'G2026102500031',
            status: 'REFUNDED',
            groupBuy: gbRef(g102),
            createdAt: at(-12 * DAY),
            paidAt: at(-12 * DAY + MIN),
            items: [{ id: 5, optionName: '본체 세트', qty: 1, unitPrice: 47000, itemAmount: 47000, canceledQty: 0, returnedQty: 1, selectableQty: 0 }],
            shippingFee: 3000,
            amount: 50000,
            refundedAmount: 50000,
            shipment: { carrierName: '한진택배', trackingNo: '987654321098', trackingUrl: null, kind: 'ORIGINAL', shippedAt: at(-11 * DAY), deliveredAt: at(-9 * DAY) },
            requests: [
                {
                    id: 9003,
                    type: 'RETURN',
                    status: 'COMPLETED',
                    items: [{ orderItemId: 5, optionName: '본체 세트', qty: 1, reason: 'DEFECT' }],
                    collectionMethod: 'COMPANY_PICKUP',
                    resolution: 'REFUND',
                    previewAmount: 50000,
                    refundAmount: 50000,
                    returnFeeDue: null,
                    rejectReasonCode: null,
                    pickupTrackingNo: '555566667777',
                    createdAt: at(-6 * DAY),
                    handledAt: at(-1 * DAY),
                    actions: { withdraw: false, payReturnFee: false },
                },
                {
                    id: 9002,
                    type: 'RETURN',
                    status: 'REJECTED',
                    items: [{ orderItemId: 5, optionName: '본체 세트', qty: 1, reason: 'CHANGE_OF_MIND' }],
                    collectionMethod: 'COMPANY_PICKUP',
                    resolution: 'REFUND',
                    previewAmount: 41000,
                    refundAmount: null,
                    returnFeeDue: null,
                    rejectReasonCode: 'USED_VALUE_LOSS',
                    pickupTrackingNo: null,
                    createdAt: at(-8 * DAY),
                    handledAt: at(-7 * DAY),
                    actions: { withdraw: false, payReturnFee: false },
                },
                {
                    id: 9001,
                    type: 'RETURN',
                    status: 'WITHDRAWN',
                    items: [{ orderItemId: 5, optionName: '본체 세트', qty: 1, reason: 'CHANGE_OF_MIND' }],
                    collectionMethod: 'CUSTOMER_SEND',
                    resolution: 'REFUND',
                    previewAmount: 44000,
                    refundAmount: null,
                    returnFeeDue: null,
                    rejectReasonCode: null,
                    pickupTrackingNo: null,
                    createdAt: at(-9 * DAY + HOUR),
                    handledAt: at(-9 * DAY + 2 * HOUR),
                    actions: { withdraw: false, payReturnFee: false },
                },
            ],
            refunds: [{ id: 7001, cause: 'REQUEST', amount: 50000, progress: 'IN_PROGRESS', method: 'PG_CANCEL', requestedAt: at(-1 * DAY), completedAt: null }],
            returnUntil: at(-2 * DAY),
            defectReturnUntil: at(80 * DAY),
        }),
        // 공급 불가 선택 대기 — 민트 줄 취소·환불 + 「나머지만 받기 / 전체 취소」(기한 3영업일)
        order({
            orderNo: 'G2026102300007',
            status: 'CONFIRMED',
            groupBuy: gbRef(g107),
            createdAt: at(-10 * DAY),
            paidAt: at(-10 * DAY + MIN),
            items: [
                { id: 6, optionName: '화이트', qty: 1, unitPrice: 39000, itemAmount: 39000, canceledQty: 0, returnedQty: 0, selectableQty: 1 },
                { id: 7, optionName: '민트(공급 불가)', qty: 1, unitPrice: 42000, itemAmount: 42000, canceledQty: 1, returnedQty: 0, selectableQty: 0 },
            ],
            refundedAmount: 42000,
            shipPlannedAt: g107.delayNotice?.newPlannedAt ?? null,
            supplyChoice: { optionName: '민트(공급 불가)', reason: '공급처에서 민트 색상 생산이 중단됐어요.', deadline: at(2 * DAY), choice: null, defaulted: false, cancelAllRefund: 39000 },
            refunds: [{ id: 7002, cause: 'SUPPLY_FAILURE', amount: 42000, progress: 'COMPLETED', method: 'PG_CANCEL', requestedAt: at(-1 * DAY), completedAt: at(-1 * DAY + 3 * HOUR) }],
            actions: { ...NO_ACTIONS, supplyChoice: true, requestCancel: true },
        }),
        // 지연 공지 — 준비중(PREPARING) · 「바로 전액 취소」 · 취소 요청(승인 필요)
        order({
            orderNo: 'G2026102200003',
            status: 'PREPARING',
            groupBuy: gbRef(g107),
            createdAt: at(-11 * DAY),
            paidAt: at(-11 * DAY + MIN),
            items: [{ id: 8, optionName: '화이트', qty: 1, unitPrice: 39000, itemAmount: 39000, canceledQty: 0, returnedQty: 0, selectableQty: 1 }],
            shippingFee: 3000,
            amount: 42000,
            shipPlannedAt: g107.delayNotice?.newPlannedAt ?? null,
            delayNotice: g107.delayNotice ? { ...g107.delayNotice, refundAmount: 42000 } : null,
            actions: { ...NO_ACTIONS, delayCancel: true, requestCancel: true },
        }),
        // 발송 중(SHIPPED) — 송장 조회
        order({
            orderNo: 'G2026102600009',
            status: 'SHIPPED',
            groupBuy: gbRef(g102),
            createdAt: at(-2 * DAY),
            paidAt: at(-2 * DAY + MIN),
            items: [{ id: 9, optionName: '예비 배터리', qty: 2, unitPrice: 5000, itemAmount: 10000, canceledQty: 0, returnedQty: 0, selectableQty: 0 }],
            shippingFee: 3000,
            amount: 13000,
            shipWithinBusinessDays: 3,
            shipment: { carrierName: '우체국택배', trackingNo: '6000123456789', trackingUrl: 'https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=6000123456789', kind: 'ORIGINAL', shippedAt: at(-6 * HOUR), deliveredAt: null },
        }),
        // 만료(결제하지 않음)
        order({
            orderNo: 'G2026102100002',
            status: 'EXPIRED',
            groupBuy: gbRef(g103),
            createdAt: at(-7 * DAY),
            paidAt: null,
            items: [{ id: 10, optionName: null, qty: 1, unitPrice: 129000, itemAmount: 129000, canceledQty: 0, returnedQty: 0, selectableQty: 0 }],
            address: { ...MASKED },
            actions: { ...NO_ACTIONS, revealAddress: false },
        }),
        // 본인 취소(CANCELED) — 환불을 운영자가 직접 처리함(MANUAL)
        order({
            orderNo: 'G2026102000001',
            status: 'CANCELED',
            groupBuy: gbRef(g101),
            createdAt: at(-8 * DAY),
            paidAt: at(-8 * DAY + MIN),
            items: [{ id: 11, optionName: null, qty: 1, unitPrice: 289000, itemAmount: 289000, canceledQty: 1, returnedQty: 0, selectableQty: 0 }],
            refundedAmount: 289000,
            refunds: [{ id: 7003, cause: 'USER_CANCEL', amount: 289000, progress: 'MANUAL', method: 'PG_CANCEL', requestedAt: at(-8 * DAY + HOUR), completedAt: at(-6 * DAY) }],
        }),
    ];
}
