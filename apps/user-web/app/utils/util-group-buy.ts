import type { GroupBuyListItem, GroupBuyOrderLine, GroupBuyPhase, GroupBuySku, GroupBuyStockState } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { GroupBuyRequestReason } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilCommerce from '~/utils/util-commerce';

/** 축 값 하나의 상태 — 그 값으로 갈 수 있는 단품들의 품절 판정을 모은 것, 판매하는 단품이 없으면 OFF(판매 안 함) */
export type GroupBuyValueState = GroupBuyStockState | 'OFF';

/** 목록 정렬(화면 안) — `UtilGroupBuy.SORTS` */
export type GroupBuyListSort = 'DEFAULT' | 'DEADLINE' | 'PRICE' | 'POPULAR';

/**
 * 공구 화면의 순수 규칙 — 주소·표시 판정(남은 수량 적음·진행률)·금액 글자·고른 상품 목록(결정 88)의 주소 글자와 담을 수 있는 수량. 상태도 요청도 없다.
 * 금액·남은 수량·품절 판정·단추 상태는 서버 값이고, 여기는 그 값을 **어떻게 보일지**만 정한다. 담는 수량의 상한은 편의 — 최종 판정은 서버(견적·주문).
 */
export default class UtilGroupBuy {
    static readonly LIST_PATH = '/group-buys';
    static readonly PHASES: readonly GroupBuyPhase[] = ['ONGOING', 'UPCOMING', 'ENDED'];
    /** 고른 상품 목록을 상세 ↔ 주문서로 넘기는 쿼리 이름 — `?items=skuId:qty,…`(장바구니가 없어 화면 상태를 주소로 — 결정 88) */
    static readonly ITEMS_QUERY = 'items';
    /** 한 주문에 담는 줄 상한(주소 글자를 지나치게 길게 받지 않게 — 편의) */
    static readonly ITEMS_MAX = 50;
    /** 하자·오배송 사진 최대(commerce.md 10-8) */
    static readonly PHOTO_MAX = 3;
    /** 「남은 수량 적음」 — 남은 수가 이 개수 이하이거나 */
    static readonly LOW_STOCK_COUNT = 5;
    /** 「남은 수량 적음」 — 최대의 이 비율 이하 */
    static readonly LOW_STOCK_RATIO = 0.1;

    static detailPath(id: string): string {
        return `${UtilGroupBuy.LIST_PATH}/${id}`;
    }

    static orderPath(id: string): string {
        return `${UtilGroupBuy.LIST_PATH}/${id}/order`;
    }

    static myOrderPath(orderNo: string): string {
        return `${UtilCommerce.ORDERS_PATH}/${encodeURIComponent(orderNo)}`;
    }

    /**
     * 주소의 `?phase=` → 탭(모르는 값이면 진행)
     * @param {unknown} value - 쿼리 값.
     * @returns {GroupBuyPhase} 탭.
     */
    static phaseOf(value: unknown): GroupBuyPhase {
        return UtilGroupBuy.PHASES.includes(value as GroupBuyPhase) ? (value as GroupBuyPhase) : 'ONGOING';
    }

    /**
     * 남은 수량 적음 — 남은 10% 이하 또는 5개 이하(0 은 품절이라 따로 보인다)
     * @param {number} remaining - 서버가 준 남은 수량.
     * @param {number} max - 최대 수량.
     * @returns {boolean} 적으면 true.
     */
    static lowStock(remaining: number, max: number): boolean {
        if (remaining <= 0) return false;
        return remaining <= UtilGroupBuy.LOW_STOCK_COUNT || remaining <= max * UtilGroupBuy.LOW_STOCK_RATIO;
    }

    /**
     * 성사형 진행률(확정 / 최소, 100 을 넘으면 100) — 선착순(min 0)이면 null
     * @param {number} current - 확정 수량.
     * @param {number} min - 성사 최소 수량.
     * @returns {number | null} 0~100.
     */
    static progress(current: number, min: number): number | null {
        if (min <= 0) return null;
        return Math.min(100, Math.floor((current / min) * 100));
    }

    /**
     * 성사형 달성률 글자(확정 / 최소, 100 을 넘어도 그대로 — 「150% 달성」) — 선착순(min 0)이면 null
     * @param {number} current - 확정 수량.
     * @param {number} min - 성사 최소 수량.
     * @returns {number | null} 0 이상.
     */
    static reached(current: number, min: number): number | null {
        if (min <= 0) return null;
        return Math.floor((current / min) * 100);
    }

    /**
     * 정상가 대비 할인율(내림, 정수 %) — 정상가가 없거나 공구가가 같거나 비싸면 null.
     * 「N원부터」(단품마다 값이 다름)는 가장 싼 값 기준이라 할인율이 부풀려 보일 수 있어 보이지 않는다(표시·광고 고시 — 결정 58).
     * @param {number | null} normalPrice - 정상가.
     * @param {number} price - 공구가.
     * @param {boolean} priceFrom - 「N원부터」인가.
     * @returns {number | null} 1~99.
     */
    static discountRate(normalPrice: number | null, price: number, priceFrom: boolean): number | null {
        if (priceFrom || !normalPrice || normalPrice <= 0 || price >= normalPrice) return null;
        const rate = Math.floor(((normalPrice - price) / normalPrice) * 100);
        return rate >= 1 ? rate : null;
    }

    /** 목록 정렬(화면 안 — 불러온 목록만). `DEFAULT` 는 서버 차례 그대로 */
    static readonly SORTS = ['DEFAULT', 'DEADLINE', 'PRICE', 'POPULAR'] as const;

    /**
     * 주소의 `?sort=` → 정렬(모르는 값이면 `DEFAULT`)
     * @param {unknown} value - 쿼리 값.
     * @returns {GroupBuyListSort} 정렬.
     */
    static sortOf(value: unknown): GroupBuyListSort {
        return UtilGroupBuy.SORTS.includes(value as GroupBuyListSort) ? (value as GroupBuyListSort) : 'DEFAULT';
    }

    /**
     * 불러온 목록을 정렬한 새 배열 — 마감 빠른순(연장 끝 → 마감, 예정은 시작 시각) · 낮은 가격순 · 많이 모인순(확정 수량). 같으면 서버 차례.
     * @param {GroupBuyListItem[]} items - 서버 차례의 목록.
     * @param {GroupBuyListSort} sort - 정렬.
     * @returns {GroupBuyListItem[]} 정렬한 목록(`DEFAULT` 면 그대로).
     */
    static sortItems(items: GroupBuyListItem[], sort: GroupBuyListSort): GroupBuyListItem[] {
        if (sort === 'DEFAULT') return items;
        const key = (gb: GroupBuyListItem): number => {
            if (sort === 'PRICE') return gb.groupPrice;
            if (sort === 'POPULAR') return -gb.currentQty;
            return Date.parse(gb.phase === 'UPCOMING' ? gb.startAt : (gb.extendedUntil ?? gb.endAt));
        };
        return items
            .map((gb, index) => ({ gb, index, k: key(gb) }))
            .sort((a, b) => a.k - b.k || a.index - b.index)
            .map((x) => x.gb);
    }

    /**
     * 살 수 없는 품절 상태인가(잠시 구매 불가 포함)
     * @param {GroupBuyStockState} state - 서버 판정.
     * @returns {boolean} 품절이면 true.
     */
    static unavailable(state: GroupBuyStockState): boolean {
        return state !== 'AVAILABLE';
    }

    /**
     * 업체 부담 사유인가(하자·오배송 — 사진·교환을 받는다)
     * @param {GroupBuyRequestReason | null} reason - 사유.
     * @returns {boolean} 하자·오배송이면 true.
     */
    static isCompanyFault(reason: GroupBuyRequestReason | null): boolean {
        return reason === 'DEFECT' || reason === 'WRONG_ITEM';
    }

    /**
     * 고른 상품 목록 → 쿼리 글자 `10201:1,10251:2`
     * @param {GroupBuyOrderLine[]} lines - 고른 줄.
     * @returns {string} 쿼리 값(줄이 없으면 빈 글자).
     */
    static formatItems(lines: GroupBuyOrderLine[]): string {
        return lines.map((l) => `${l.skuId}:${l.qty}`).join(',');
    }

    /**
     * 쿼리 글자 → 고른 상품 목록. 모양만 본다(어느 단품이 있는지·팔 수 있는지는 화면이 공구 단품과 맞춰 보고, 최종은 서버)
     * @param {unknown} value - `route.query.items`.
     * @returns {GroupBuyOrderLine[] | null} 줄 목록(없으면 빈 배열), 깨졌으면(모양이 틀림·같은 단품 두 줄·너무 많음) null.
     */
    static parseItems(value: unknown): GroupBuyOrderLine[] | null {
        if (value === undefined || value === null || value === '') return [];
        if (typeof value !== 'string') return null;
        const parts = value.split(',');
        if (parts.length > UtilGroupBuy.ITEMS_MAX) return null;
        const out: GroupBuyOrderLine[] = [];
        for (const part of parts) {
            // 단품 ID 는 서버 Long(눈송이 ID 18~19자리) — 글자 그대로 둔다
            const m = /^(\d{1,19}):(\d{1,4})$/.exec(part.trim());
            if (!m) return null;
            const skuId = m[1]!;
            const qty = Number(m[2]);
            if (qty < 1 || out.some((l) => l.skuId === skuId)) return null;
            out.push({ skuId, qty });
        }
        return out;
    }

    /** 한도 없는 추가 구성상품(`remaining: null`)의 담기 상한(편의 — 주소 글자 4자리) */
    static readonly QTY_MAX = 999;

    /**
     * 단품의 품절 판정 → 화면 상태(서버 `soldOut`·`temporarilyUnavailable`)
     * @param {Pick<GroupBuySku, 'soldOut' | 'temporarilyUnavailable'>} sku - 단품.
     * @returns {GroupBuyStockState} 상태.
     */
    static skuState(sku: Pick<GroupBuySku, 'soldOut' | 'temporarilyUnavailable'>): GroupBuyStockState {
        if (sku.temporarilyUnavailable) return 'TEMP_SOLD_OUT';
        return sku.soldOut ? 'SOLD_OUT' : 'AVAILABLE';
    }

    /**
     * 지금 담을 수 있는 단품인가 — 판매 중 · 품절 아님 · 남은 수량 0 아님(null = 제한 없음)
     * @param {GroupBuySku} sku - 단품.
     * @returns {boolean} 담을 수 있으면 true.
     */
    static buyable(sku: GroupBuySku): boolean {
        return sku.onSale && !sku.soldOut && !sku.temporarilyUnavailable && sku.remaining !== 0;
    }

    /**
     * 축 값 하나의 상태 — 앞 축에서 고른 값(`chosen`, 고르지 않은 칸은 null)과 이 값으로 갈 수 있는 MAIN 단품을 모아 본다.
     * 판매하는 단품 중 하나라도 살 수 있으면 AVAILABLE, 모두 품절이면 잠시 구매 불가가 하나라도 있을 때 TEMP_SOLD_OUT 아니면 SOLD_OUT, 판매하는 단품이 없으면 OFF.
     * @param {GroupBuySku[]} skus - 공구의 단품(ADDON 은 무시한다).
     * @param {(string | null)[]} chosen - 축마다 고른 값 ID(이 축보다 앞만 본다).
     * @param {number} axisIndex - 이 값이 속한 축 차례.
     * @param {string} valueId - 값 ID.
     * @returns {GroupBuyValueState} 상태.
     */
    static valueState(skus: GroupBuySku[], chosen: (string | null)[], axisIndex: number, valueId: string): GroupBuyValueState {
        const reach = skus.filter(
            (s) => s.kind === 'MAIN' && s.onSale && s.valueIds[axisIndex] === valueId && chosen.slice(0, axisIndex).every((c, i) => c === null || s.valueIds[i] === c),
        );
        if (reach.length === 0) return 'OFF';
        if (reach.some((s) => UtilGroupBuy.buyable(s))) return 'AVAILABLE';
        return reach.some((s) => s.temporarilyUnavailable) ? 'TEMP_SOLD_OUT' : 'SOLD_OUT';
    }

    /**
     * 축 값 조합의 MAIN 단품(축 차례대로 값 ID)
     * @param {GroupBuySku[]} skus - 공구의 단품.
     * @param {string[]} valueIds - 값 ID 조합.
     * @returns {GroupBuySku | null} 단품(조합이 없으면 null).
     */
    static comboSku(skus: GroupBuySku[], valueIds: string[]): GroupBuySku | null {
        return skus.find((s) => s.kind === 'MAIN' && s.valueIds.length === valueIds.length && s.valueIds.every((v, i) => v === valueIds[i])) ?? null;
    }

    /**
     * 이 단품 줄에 담을 수 있는 수량 상한(편의 — 최종은 서버). 단품 남은 수량, 본 상품이면 1인 한도에서 다른 본 상품 줄 수량을 뺀 것(추가 구성상품은 1인 한도에 세지 않는다 — 결정 88).
     * @param {GroupBuySku} sku - 단품.
     * @param {GroupBuyOrderLine[]} lines - 지금 고른 줄(이 단품 줄 포함 가능).
     * @param {GroupBuySku[]} skus - 공구의 단품(줄의 종류를 찾는다).
     * @param {number | null} maxQtyPerUser - 1인 한도.
     * @returns {number} 0 이상.
     */
    static lineCap(sku: GroupBuySku, lines: GroupBuyOrderLine[], skus: GroupBuySku[], maxQtyPerUser: number | null): number {
        let cap = UtilGroupBuy.buyable(sku) ? (sku.remaining ?? UtilGroupBuy.QTY_MAX) : 0;
        if (sku.kind === 'MAIN' && maxQtyPerUser !== null) {
            const others = lines.filter((l) => l.skuId !== sku.skuId && skus.find((s) => s.skuId === l.skuId)?.kind === 'MAIN').reduce((n, l) => n + l.qty, 0);
            cap = Math.min(cap, maxQtyPerUser - others);
        }
        return Math.max(0, cap);
    }

    /**
     * 고른 줄에 본 상품(MAIN)이 있나 — 추가 구성상품만으로는 살 수 없다(결정 88)
     * @param {GroupBuyOrderLine[]} lines - 고른 줄.
     * @param {GroupBuySku[]} skus - 공구의 단품.
     * @returns {boolean} 있으면 true.
     */
    static hasMain(lines: GroupBuyOrderLine[], skus: GroupBuySku[]): boolean {
        return lines.some((l) => skus.find((s) => s.skuId === l.skuId)?.kind === 'MAIN');
    }

    /**
     * 금액 숫자 글자(쉼표) — 원·₩ 같은 단위는 문구(`payment.won`)가 붙인다
     * @param {number} amount - 원.
     * @returns {string} `47,000` · `-1,000`.
     */
    static amount(amount: number): string {
        return amount.toLocaleString('en-US');
    }
}
