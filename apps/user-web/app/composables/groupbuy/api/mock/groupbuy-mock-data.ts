// MOCK: 공구 목업의 메모리 데이터 — `groupbuy-mock.ts`(목록·상세·견적·주문·결제)와 `groupbuy-order-mock.ts`(내 주문)가 같이 쓴다. 서버가 붙으면 목업 폴더째 지운다.
// 새로고침하면 처음 상태로 돌아간다. 시각은 「지금」 기준으로 만든다(마감 임박·선점 남은 시간이 늘 보이게).
//
// ── 시드(계약 결정 11 의 local 시드 넷 + 예정 하나 + 종료 몇 개) ─ 옵션 축·단품·추가 구성상품은 결정 88
//   101 성사형 모집 중(마감 후 발주, min 30 · 18/100, 발송 예정일) · 축 없음
//   102 선착순 즉시 발송 · 축 없음 + 추가 구성상품 「여분 배터리」 5,000(한도 없음) · 「충전 독」 15,000(잠시 구매 불가 — 단품 알림) · 추가 입고 20개 · 고시 양식 셋
//   103 마감 임박 · 남은 수량 적음 · 2차 공구(1차 = 104) · 색상 축(실버 · 블랙 +5,000)
//   104 다 팔려 끝난 1차(SOLD_OUT) — 「다음 차수 알림 받기」
//   105 모집 예정(가격 미리 공개)
//   106 기간 끝 · 불발(CLOSED_FAILED · TIME_UP)
//   107 조기 마감(EARLY_CLOSED) · 성사 · 발주(ORDERED) · 발송 지연 공지 · 색상 축(민트 단품 공급 불가)
//   108 운영 종료(CANCELED)
//   ── 로봇 밖 예시 품목(결정 87) ─
//   109 햅쌀 성사형 · 중량 축(10kg · 20kg) · 110 과일 선착순 · 종류 × 중량 축(종류마다 고시 양식) · 111 수산물 모집 예정 · 종류 축(종류마다 고시 양식)
//   112 김치 다 팔림 · 중량 축 · 113 티셔츠 선착순 · 색상 2 × 사이즈 3(화이트 / S 판매 끔 · 블랙 / M 잠시 품절 · L +1,000) · 114 러닝화 성사형 마감 임박 · 사이즈 축 하나(280 다 팔림)
//
// ── 단품 ID(목업 규칙) ─ 본 상품 = 공구 ID × 100 + 조합 차례(1부터), 추가 구성상품 = 공구 ID × 100 + 50 + 차례. 축 ID = 공구 ID × 10 + 차례, 값 ID = 공구 ID × 1000 + 축 차례 × 100 + 값 차례.
//
// ── 배송비 정책(모집 시작 때 고정한 사본) ─
//   STANDARD: 3,000 · 무료 50,000 · 반품 3,000 · 편도   ROUND: 3,000 · 무료 50,000 · 반품 3,000 · 왕복(103만)

import type { GroupBuyAxis, GroupBuyDetail, GroupBuyShipping, GroupBuySku } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { MyGroupBuyOrderDetail, MyGroupBuyOrderItem } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import { mockNotices } from '~/composables/groupbuy/api/mock/groupbuy-notice-mock-data';

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

/** 로봇 밖 상품 설명(정화된 HTML 이라고 가정) */
const GOODS_DESC = (what: string, note: string) => `<p><strong>${what}</strong>을(를) 공동구매가로 모아요.</p><p>${note}</p><p>실제 색상·크기는 화면과 조금 다를 수 있어요.</p>`;

/* ── 단품 만들기(서버 2b-2 `Availability` 모양 흉내 — MAIN 이름은 값을 「 / 」로 잇고, 축이 없으면 상품 이름) ── */

interface SkuSpec {
    /** MAIN 추가금 */
    extra?: number;
    /** 남은 수량 — null = 제한 없음(한도 없는 ADDON) */
    remaining: number | null;
    /** 잠시 구매 불가(다른 분이 결제 중) */
    temp?: boolean;
    /** 운영자가 판매를 끈 조합 */
    off?: boolean;
}

function sku(skuId: number, kind: GroupBuySku['kind'], valueIds: number[], name: string, unitPrice: number, spec: SkuSpec): GroupBuySku {
    const onSale = !spec.off;
    const remaining = onSale ? spec.remaining : 0;
    const temp = onSale && !!spec.temp;
    return { skuId, kind, name, valueIds, unitPrice, remaining, soldOut: onSale && !temp && remaining === 0, temporarilyUnavailable: temp, onSale };
}

/** 축 없는 공구의 MAIN 단품 하나(이름 = 상품 이름) */
function single(gbId: number, name: string, price: number, spec: SkuSpec): GroupBuySku {
    return sku(gbId * 100 + 1, 'MAIN', [], name, price, spec);
}

/**
 * 축과 조합 단품 — `specs` 키는 단품 이름(「블랙 / M」). 키가 없는 조합은 판매 끔.
 * @returns 축 · MAIN 단품(조합 차례 — 앞 축이 바깥 고리)
 */
function withAxes(gbId: number, groupPrice: number, axisDefs: [string, string[]][], specs: Record<string, SkuSpec>): { axes: GroupBuyAxis[]; skus: GroupBuySku[] } {
    const axes: GroupBuyAxis[] = axisDefs.map(([name, values], a) => ({
        axisId: gbId * 10 + a + 1,
        name,
        values: values.map((value, v) => ({ valueId: gbId * 1000 + a * 100 + v + 1, value })),
    }));
    let combos: { ids: number[]; names: string[] }[] = [{ ids: [], names: [] }];
    for (const axis of axes) combos = combos.flatMap((c) => axis.values.map((v) => ({ ids: [...c.ids, v.valueId], names: [...c.names, v.value] })));
    const skus = combos.map((c, i) => {
        const name = c.names.join(' / ');
        const spec = specs[name] ?? { remaining: 0, off: true };
        return sku(gbId * 100 + i + 1, 'MAIN', c.ids, name, groupPrice + (spec.extra ?? 0), spec);
    });
    return { axes, skus };
}

/** 추가 구성상품 — 단독 구매 불가, 공구 수량·1인 한도에 세지 않는다(결정 88) */
function addon(gbId: number, n: number, name: string, price: number, spec: SkuSpec): GroupBuySku {
    return sku(gbId * 100 + 50 + n, 'ADDON', [], name, price, spec);
}

type Seed = Partial<GroupBuyDetail> & Pick<GroupBuyDetail, 'id' | 'title'> & { axes?: GroupBuyAxis[]; skus?: GroupBuySku[]; productName?: string };

function base(seed: Seed): GroupBuyDetail {
    const { axes = [], skus = [], productName, ...over } = seed;
    const out: GroupBuyDetail = {
        thumbnailUrl: picture(over.title.slice(0, 8), (over.id * 47) % 360),
        status: 'RECRUITING',
        phase: 'ONGOING',
        closeReason: null,
        // 카테고리는 목록 목업(`groupbuy-mock.ts` 의 CATEGORY_OF)이 다시 정한다
        categoryId: 'ct-robot',
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
        availability: { groupBuyId: over.id, remaining: 0, soldOut: false, temporarilyUnavailable: false, addedQty: 0, axes: [], skus: [] },
        skuWatches: { watchable: [], watching: [] },
        notices: [],
        delayNotice: null,
        purchase: { reason: null },
        actions: { order: true, watchRestock: false, watchClosingSoon: true, watchNextRound: false },
        watches: { RESTOCK: false, CLOSING_SOON: false, NEXT_ROUND: false },
        myOrderNo: null,
        ...over,
    };
    // 단품을 따로 주지 않은 공구 = 축 없는 MAIN 하나(공구 수량을 그대로)
    const allSkus = skus.length > 0 ? skus : [single(out.id, productName ?? out.title, out.groupPrice, { remaining: out.remainingQty, temp: out.stockState === 'TEMP_SOLD_OUT' })];
    out.availability = {
        groupBuyId: out.id,
        remaining: out.remainingQty,
        soldOut: out.stockState === 'SOLD_OUT',
        temporarilyUnavailable: out.stockState === 'TEMP_SOLD_OUT',
        addedQty: out.addedQty,
        axes,
        skus: allSkus,
    };
    // 상품정보제공고시(결정 87) — 품목별 양식은 `groupbuy-notice-mock-data.ts`(적용 단품 이름 → 단품 ID 로 바꿔 넣는다)
    out.notices = mockNotices(out.id, out.title, allSkus);
    return out;
}

export function seedGroupBuys(): GroupBuyDetail[] {
    return [
        base({
            id: 101,
            title: '로봇 청소기 X1 공동구매',
            productName: '로봇 청소기 X1',
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
            groupPrice: 47000,
            normalPrice: 59000,
            normalPriceBasis: 'PREV_SALE',
            minQty: 0,
            maxQty: 150,
            currentQty: 119,
            remainingQty: 31,
            endAt: at(10 * DAY),
            shipWithinBusinessDays: 3,
            addedQty: 20,
            maxQtyPerUser: 5,
            summary: '재고를 가진 선착순 공구예요. 결제가 확정되면 3영업일 안에 보내 드려요. 여분 배터리·충전 독은 본체와 함께 담을 수 있어요.',
            spec: '본체: 메인보드 · 모터 2개 · 바퀴 · 배터리 1개\n여분 배터리(추가 구성): 1,200mAh 리튬폴리머\n충전 독(추가 구성): USB-C 거치형 충전기',
            skus: [
                single(102, '코딩 로봇 키트 R1', 47000, { remaining: 31 }),
                addon(102, 1, '여분 배터리', 5000, { remaining: null }),
                addon(102, 2, '충전 독', 15000, { remaining: 0, temp: true }),
            ],
            skuWatches: { watchable: [10252], watching: [] },
            actions: { order: true, watchRestock: false, watchClosingSoon: true, watchNextRound: false },
        }),
        base({
            id: 103,
            title: '데스크 로봇 팔 미니 2차',
            fulfillment: 'IMMEDIATE',
            groupPrice: 129000,
            priceFrom: true,
            normalPrice: 139000,
            normalPriceBasis: 'PREV_ROUND',
            minQty: 0,
            maxQty: 50,
            currentQty: 46,
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
            ...withAxes(103, 129000, [['색상', ['실버', '블랙']]], {
                실버: { remaining: 3 },
                블랙: { extra: 5000, remaining: 1 },
            }),
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
            ...withAxes(107, 39000, [['색상', ['화이트', '민트']]], {
                화이트: { remaining: 13 },
                민트: { extra: 3000, remaining: 0 },
            }),
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
        // ── 로봇 밖 예시 품목(결정 87 — 쌀·과일·생선·김치·옷·신발) ──
        base({
            id: 109,
            title: '이천 햅쌀 산지 공동구매',
            fulfillment: 'AFTER_CLOSE',
            groupPrice: 42900,
            priceFrom: true,
            normalPrice: 52000,
            normalPriceBasis: 'MARKET',
            minQty: 50,
            maxQty: 200,
            currentQty: 37,
            remainingQty: 163,
            endAt: at(6 * DAY),
            shipPlannedAt: ymd(10),
            maxQtyPerUser: 4,
            descriptionHtml: GOODS_DESC('올가을 수확한 햅쌀', '50포가 모이면 성사되고, 마감 뒤 도정해 보내 드려요.'),
            summary: '50포가 모이면 성사돼요(10kg·20kg 함께 세요). 마감 뒤 도정해 발송 예정일에 보내 드려요.',
            spec: '품종 추청(아키바레)\n중량 10kg · 20kg\n도정 발송 3일 전',
            ...withAxes(109, 42900, [['중량', ['10kg', '20kg']]], {
                '10kg': { remaining: 120 },
                '20kg': { extra: 39000, remaining: 43 },
            }),
        }),
        base({
            id: 110,
            title: '제철 사과·감귤 선착순',
            fulfillment: 'IMMEDIATE',
            groupPrice: 15900,
            priceFrom: true,
            normalPrice: 39000,
            normalPriceBasis: 'PREV_SALE',
            minQty: 0,
            maxQty: 160,
            currentQty: 83,
            remainingQty: 77,
            endAt: at(4 * DAY),
            shipWithinBusinessDays: 2,
            maxQtyPerUser: 3,
            descriptionHtml: GOODS_DESC('제철 과일', '사과와 감귤은 다른 농가 상품이라 상품정보를 따로 적었어요.'),
            summary: '산지 창고 재고예요. 결제 다음 날 수확·선별해 보내 드려요.',
            spec: '사과 3kg(8~10과) · 5kg(14~16과)\n감귤 3kg · 5kg(S~M)',
            withdrawalRestriction: '신선식품이라 받은 뒤 시간이 지나 가치가 크게 떨어지면 단순 변심 반품이 제한될 수 있어요(상한 것·오배송은 제외).',
            ...withAxes(
                110,
                15900,
                [
                    ['종류', ['사과', '감귤']],
                    ['중량', ['3kg', '5kg']],
                ],
                {
                    '사과 / 3kg': { extra: 4000, remaining: 20 },
                    '사과 / 5kg': { extra: 14000, remaining: 8 },
                    '감귤 / 3kg': { remaining: 25 },
                    '감귤 / 5kg': { extra: 8000, remaining: 24 },
                },
            ),
        }),
        base({
            id: 111,
            title: '손질 고등어·활전복 산지 직송 예약',
            status: 'DRAFT',
            phase: 'UPCOMING',
            fulfillment: 'AFTER_CLOSE',
            groupPrice: 24900,
            priceFrom: true,
            normalPrice: 45000,
            normalPriceBasis: 'MARKET',
            minQty: 30,
            maxQty: 100,
            currentQty: 0,
            remainingQty: 100,
            startAt: at(3 * DAY),
            endAt: at(10 * DAY),
            shipPlannedAt: ymd(14),
            descriptionHtml: GOODS_DESC('산지 수산물', '고등어는 냉동, 전복은 냉장으로 따로 보내요.'),
            summary: '30세트가 모이면 성사돼요. 마감 뒤 출하해 보내 드려요.',
            ...withAxes(111, 24900, [['종류', ['손질 고등어 1kg (냉동)', '활전복 1kg (냉장)']]], {
                '손질 고등어 1kg (냉동)': { remaining: 60 },
                '활전복 1kg (냉장)': { extra: 12000, remaining: 40 },
            }),
            purchase: { reason: 'NOT_STARTED' },
            actions: { order: false, watchRestock: false, watchClosingSoon: false, watchNextRound: false },
        }),
        base({
            id: 112,
            title: '김장 포기김치 선착순',
            status: 'CLOSED_SUCCESS',
            phase: 'ENDED',
            closeReason: 'SOLD_OUT',
            fulfillment: 'IMMEDIATE',
            groupPrice: 25900,
            priceFrom: true,
            minQty: 0,
            maxQty: 100,
            currentQty: 100,
            remainingQty: 0,
            stockState: 'SOLD_OUT',
            startAt: at(-9 * DAY),
            endAt: at(-3 * DAY),
            shipWithinBusinessDays: 2,
            descriptionHtml: GOODS_DESC('전라도식 포기김치', '준비한 수량이 다 팔렸어요.'),
            ...withAxes(112, 25900, [['중량', ['3kg', '5kg']]], {
                '3kg': { remaining: 0 },
                '5kg': { extra: 14000, remaining: 0 },
            }),
            purchase: { reason: 'NOT_RECRUITING' },
            actions: { order: false, watchRestock: false, watchClosingSoon: false, watchNextRound: true },
        }),
        base({
            id: 113,
            title: '오버핏 코튼 티셔츠 선착순',
            fulfillment: 'IMMEDIATE',
            groupPrice: 15900,
            priceFrom: true,
            normalPrice: 25000,
            normalPriceBasis: 'PREV_SALE',
            minQty: 0,
            maxQty: 200,
            currentQty: 136,
            remainingQty: 64,
            endAt: at(7 * DAY),
            shipWithinBusinessDays: 3,
            maxQtyPerUser: 5,
            descriptionHtml: GOODS_DESC('기본 반팔 티셔츠', '색상·사이즈만 다른 같은 상품이라 상품정보는 하나예요. 화이트 S 는 이번 공구에서 팔지 않아요.'),
            spec: '면 100% · 20수 싱글 저지',
            ...withAxes(
                113,
                15900,
                [
                    ['색상', ['화이트', '블랙']],
                    ['사이즈', ['S', 'M', 'L']],
                ],
                {
                    // 「화이트 / S」 는 키가 없어 판매 끔
                    '화이트 / M': { remaining: 3 },
                    '화이트 / L': { extra: 1000, remaining: 20 },
                    '블랙 / S': { remaining: 12 },
                    '블랙 / M': { remaining: 0, temp: true },
                    '블랙 / L': { extra: 1000, remaining: 29 },
                },
            ),
            skuWatches: { watchable: [11305], watching: [] },
        }),
        base({
            id: 114,
            title: '쿠션 러닝화 RX-1 (마감 임박)',
            fulfillment: 'AFTER_CLOSE',
            groupPrice: 64000,
            normalPrice: 89000,
            normalPriceBasis: 'OFFICIAL_STORE',
            minQty: 60,
            maxQty: 100,
            currentQty: 56,
            remainingQty: 44,
            endAt: at(5 * HOUR),
            closingSoon: true,
            shipPlannedAt: ymd(12),
            maxQtyPerUser: 2,
            descriptionHtml: GOODS_DESC('쿠션 러닝화', '60켤레가 모이면 성사돼요. 사이즈만 다른 같은 상품이라 상품정보는 하나예요.'),
            ...withAxes(114, 64000, [['사이즈', ['240', '250', '260', '270', '280']]], {
                '240': { remaining: 14 },
                '250': { remaining: 12 },
                '260': { remaining: 3 },
                '270': { remaining: 15 },
                '280': { remaining: 0 },
            }),
            skuWatches: { watchable: [11405], watching: [] },
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

/** 주문 줄 — 단품 사본(이름·종류·단가)을 공구 단품에서 옮긴다 */
function line(gb: GroupBuyDetail, id: number, skuId: number, qty: number, over: Partial<MyGroupBuyOrderItem> = {}): MyGroupBuyOrderItem {
    const s = gb.availability.skus.find((k) => k.skuId === skuId)!;
    return { id, skuId, skuName: s.name, kind: s.kind, qty, unitPrice: s.unitPrice, itemAmount: s.unitPrice * qty, canceledQty: 0, returnedQty: 0, selectableQty: 0, ...over };
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
        // 배송완료 — 결정 68 예시(본체 47,000 + 추가 구성 여분 배터리 5,000 무료배송 → 배터리 변심 반품: 업체회수 −1,000 / 직접 발송 +2,000)
        // 본체만 반품을 고르면 「추가 구성상품도 함께 반품할까요?」(결정 88)
        order({
            orderNo: 'G2026102800012',
            status: 'DELIVERED',
            groupBuy: gbRef(g102),
            createdAt: at(-4 * DAY),
            paidAt: at(-4 * DAY + 2 * MIN),
            items: [line(g102, 1, 10201, 1, { selectableQty: 1 }), line(g102, 2, 10251, 1, { selectableQty: 1 })],
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
            items: [line(g101, 3, 10101, 2, { selectableQty: 2 })],
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
            items: [line(g103, 4, 10301, 1)],
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
            items: [line(g102, 5, 10201, 1, { returnedQty: 1 })],
            shippingFee: 3000,
            amount: 50000,
            refundedAmount: 50000,
            shipment: { carrierName: '한진택배', trackingNo: '987654321098', trackingUrl: null, kind: 'ORIGINAL', shippedAt: at(-11 * DAY), deliveredAt: at(-9 * DAY) },
            requests: [
                {
                    id: 9003,
                    type: 'RETURN',
                    status: 'COMPLETED',
                    items: [{ skuId: 10201, skuName: '코딩 로봇 키트 R1', kind: 'MAIN', qty: 1, reason: 'DEFECT' }],
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
                    items: [{ skuId: 10201, skuName: '코딩 로봇 키트 R1', kind: 'MAIN', qty: 1, reason: 'CHANGE_OF_MIND' }],
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
                    items: [{ skuId: 10201, skuName: '코딩 로봇 키트 R1', kind: 'MAIN', qty: 1, reason: 'CHANGE_OF_MIND' }],
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
        // 단품 공급 불가 선택 대기 — 민트 줄 취소·환불 + 「나머지만 받기 / 전체 취소」(기한 3영업일)
        order({
            orderNo: 'G2026102300007',
            status: 'CONFIRMED',
            groupBuy: gbRef(g107),
            createdAt: at(-10 * DAY),
            paidAt: at(-10 * DAY + MIN),
            items: [line(g107, 6, 10701, 1, { selectableQty: 1 }), line(g107, 7, 10702, 1, { canceledQty: 1 })],
            refundedAmount: 42000,
            shipPlannedAt: g107.delayNotice?.newPlannedAt ?? null,
            supplyChoice: { skuName: '민트', reason: '공급처에서 민트 색상 생산이 중단됐어요.', deadline: at(2 * DAY), choice: null, defaulted: false, cancelAllRefund: 39000 },
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
            items: [line(g107, 8, 10701, 1, { selectableQty: 1 })],
            shippingFee: 3000,
            amount: 42000,
            shipPlannedAt: g107.delayNotice?.newPlannedAt ?? null,
            delayNotice: g107.delayNotice ? { ...g107.delayNotice, refundAmount: 42000 } : null,
            actions: { ...NO_ACTIONS, delayCancel: true, requestCancel: true },
        }),
        // 발송 중(SHIPPED) — 송장 조회 · 본체 + 추가 구성(무료배송)
        order({
            orderNo: 'G2026102600009',
            status: 'SHIPPED',
            groupBuy: gbRef(g102),
            createdAt: at(-2 * DAY),
            paidAt: at(-2 * DAY + MIN),
            items: [line(g102, 9, 10201, 1), line(g102, 12, 10251, 1)],
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
            items: [line(g103, 10, 10301, 1)],
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
            items: [line(g101, 11, 10101, 1, { canceledQty: 1 })],
            refundedAmount: 289000,
            refunds: [{ id: 7003, cause: 'USER_CANCEL', amount: 289000, progress: 'MANUAL', method: 'PG_CANCEL', requestedAt: at(-8 * DAY + HOUR), completedAt: at(-6 * DAY) }],
        }),
    ];
}
