import type { GroupBuyPhase, GroupBuyStockState } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { GroupBuyRequestReason } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilCommerce from '~/utils/util-commerce';

/**
 * 공구 화면의 순수 규칙 — 주소·표시 판정(남은 수량 적음·진행률)·금액 글자. 상태도 요청도 없다.
 * 금액·남은 수량·품절 판정·단추 상태는 서버 값이고, 여기는 그 값을 **어떻게 보일지**만 정한다.
 */
export default class UtilGroupBuy {
    static readonly LIST_PATH = '/group-buys';
    static readonly PHASES: readonly GroupBuyPhase[] = ['ONGOING', 'UPCOMING', 'ENDED'];
    /** 하자·오배송 사진 최대(commerce.md 10-8) */
    static readonly PHOTO_MAX = 3;
    /** 「남은 수량 적음」 — 남은 수가 이 개수 이하이거나 */
    static readonly LOW_STOCK_COUNT = 5;
    /** 「남은 수량 적음」 — 최대의 이 비율 이하 */
    static readonly LOW_STOCK_RATIO = 0.1;

    static detailPath(id: number): string {
        return `${UtilGroupBuy.LIST_PATH}/${id}`;
    }

    static orderPath(id: number): string {
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
     * 금액 숫자 글자(쉼표) — 원·₩ 같은 단위는 문구(`payment.won`)가 붙인다
     * @param {number} amount - 원.
     * @returns {string} `47,000` · `-1,000`.
     */
    static amount(amount: number): string {
        return amount.toLocaleString('en-US');
    }
}
