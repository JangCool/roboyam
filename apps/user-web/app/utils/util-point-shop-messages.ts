import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { PointShopItemView } from '~/composables/pointshop/api/usePointShopApi.types';
import type { MyItemView } from '~/composables/pointshop/api/useMyItemsApi.types';
import type { PointShopBlockReason } from '~/utils/util-point-shop';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilPoint from '~/utils/util-point';
import UtilPointShop from '~/utils/util-point-shop';

/** 딱지 하나 — 글자와 톤 */
export interface PointShopTagLine {
    key: string;
    text: string;
    tone: 'neutral' | 'primary' | 'success' | 'warn' | 'danger';
}

/**
 * 포인트 상점의 계산이 낀 문구(사용 방식·판매 상태·보유·남은 기간·오류 한 줄) — `t` 를 첫 인자로 받는다(`i18n.md` 3). 상수 문구는 화면이 `t('pointShop.*')` 를 직접 부른다.
 * 날짜는 표시 값의 시간대로 「M월 D일」(`time.parts`)을 만든다.
 */
export default class UtilPointShopMessages {
    /**
     * 「M월 D일」 — 표시 값 시간대
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 시각 함수 모음(`useDisplayTime().value`).
     * @param {string | null | undefined} iso - 시각.
     * @returns {string} `10월 30일` · `10/30`. 시각이 없으면 빈 글자.
     */
    static monthDay(t: Translate, time: DisplayTime, iso: string | null | undefined): string {
        const p = time.parts(iso);
        return p ? t('pointShop.monthDay', { month: p.mo, day: p.d }) : '';
    }

    /**
     * 사용 방식 — 영구 · 소모형 · 기간제 N일
     * @param {Translate} t - 번역 함수.
     * @param {{ usageType: string; durationDays?: number | null }} item - 품목(또는 보유 줄).
     * @returns {string} 딱지 글자.
     */
    static usage(t: Translate, item: { usageType: string; durationDays?: number | null }): string {
        if (item.usageType === 'CONSUMABLE') return t('pointShop.usageConsumable');
        if (item.usageType === 'PERIOD') return item.durationDays ? t('pointShop.usagePeriodDays', { days: item.durationDays }) : t('pointShop.usagePeriod');
        return t('pointShop.usagePermanent');
    }

    /**
     * 카드·상세의 판매·보유 딱지들 — 판매 상태(예정·임박·품절·남은 N개) 다음 보유(보유 중·N장 보유·~날짜까지 사용 중)
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 시각 함수 모음.
     * @param {PointShopItemView} item - 품목.
     * @returns {PointShopTagLine[]} 딱지들(사용 방식 딱지는 빼고).
     */
    static tags(t: Translate, time: DisplayTime, item: PointShopItemView): PointShopTagLine[] {
        const out: PointShopTagLine[] = [];
        if (item.saleState === 'UPCOMING') out.push({ key: 'sale', text: t('pointShop.saleFrom', { date: UtilPointShopMessages.monthDay(t, time, item.saleStartAt) }), tone: 'primary' });
        else if (item.saleState === 'ENDED') out.push({ key: 'sale', text: t('pointShop.saleEnded'), tone: 'neutral' });
        else if (item.saleState === 'SOLD_OUT') out.push({ key: 'sale', text: t('pointShop.soldOut'), tone: 'danger' });
        else {
            if (UtilPointShop.endingSoon(item)) out.push({ key: 'ending', text: t('pointShop.saleUntil', { date: UtilPointShopMessages.monthDay(t, time, item.saleEndAt) }), tone: 'warn' });
            if (item.stock !== null) out.push({ key: 'stock', text: t('pointShop.stockLeft', { count: item.stock }), tone: 'warn' });
        }
        if (item.usageType === 'PERMANENT' && item.mine.owned) out.push({ key: 'mine', text: t('pointShop.owned'), tone: 'success' });
        else if (item.usageType === 'CONSUMABLE' && item.mine.count > 0) out.push({ key: 'mine', text: t('pointShop.ownedCount', { count: item.mine.count }), tone: 'success' });
        else if (item.usageType === 'PERIOD' && UtilPointShop.periodContinuesFrom(item)) {
            out.push({ key: 'mine', text: t('pointShop.ownedUntil', { date: UtilPointShopMessages.monthDay(t, time, item.mine.periodEndsAt) }), tone: 'success' });
        }
        return out;
    }

    /**
     * 상세의 「살 수 없어요」 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 시각 함수 모음.
     * @param {PointShopItemView} item - 품목.
     * @param {PointShopBlockReason} reason - 까닭.
     * @param {number} balance - 내 포인트.
     * @returns {string} 안내 한 줄.
     */
    static blocked(t: Translate, time: DisplayTime, item: PointShopItemView, reason: PointShopBlockReason, balance: number): string {
        if (reason === 'UPCOMING') return t('pointShop.blockedUpcoming', { date: UtilPointShopMessages.monthDay(t, time, item.saleStartAt) });
        if (reason === 'ENDED') return t('pointShop.blockedEnded');
        if (reason === 'SOLD_OUT') return t('pointShop.blockedSoldOut');
        if (reason === 'OWNED') return t('pointShop.blockedOwned');
        return t('pointShop.blockedInsufficient', { points: UtilPoint.points(item.price - balance) });
    }

    /**
     * 내 아이템 한 줄의 기간 — 「N일 남음」 · 「기간 끝」. 영구·소모형은 빈 글자
     * @param {Translate} t - 번역 함수.
     * @param {MyItemView} row - 보유 줄.
     * @returns {string} 기간 글자.
     */
    static period(t: Translate, row: MyItemView): string {
        if (row.usageType !== 'PERIOD') return '';
        if (row.expired) return t('pointShop.periodOver');
        const left = UtilPointShop.daysLeft(row.expiresAt);
        if (left === null) return '';
        // 아직 시작 전(이어 붙인 기간) — 시작부터 센 남은 날이 아니라 끝까지 남은 날을 보인다
        return t('pointShop.daysLeft', { days: Math.max(1, left) });
    }

    /**
     * 서버 오류 한 줄 — 서버 문구 그대로(가공 금지), 429 는 기다릴 시간을 덧붙인다. `ApiError` 가 아니면(네트워크 등) 공통 대체 문구
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 시각 함수 모음(기다릴 시간 말).
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 보일 한 줄.
     */
    static error(t: Translate, time: DisplayTime, e: unknown): string {
        if (!(e instanceof ApiError)) return UtilAuthMessages.fallback(t, 0, 'NETWORK');
        if (e.status === 429 && e.retryAfterSeconds) return t('pointShop.retryAfter', { message: e.message, wait: time.duration(e.retryAfterSeconds) });
        return e.message;
    }
}
