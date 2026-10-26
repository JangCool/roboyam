import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { PointShopItemField, PointShopSelfRefundBlock } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilPointShop, { type PointShopItemProblem, type PointShopReasonProblem, type PointShopRefundDaysProblem } from '~/utils/util-point-shop';

/** 이름 키가 있는 아바타 카테고리 — 카탈로그(`avatars/manifest.json`)의 14개와 같다 */
const AVATAR_CATEGORY_IDS: readonly string[] = ['basic', 'hobby', 'job', 'sports', 'military', 'transport', 'animal', 'office', 'factory', 'care', 'service', 'arts-driver', 'sports2', 'traditional'];

/**
 * 포인트 상점 관리 화면의 **계산이 낀 문구**(칸·문제에 따라 고르는 문구, 범위 자리표시자) — 상수 문구는 컴포넌트가 `t('pointShop.…')` 로 부른다(`i18n.md` 3절).
 */
export default class UtilPointShopMessages {
    /**
     * 품목 칸 하나의 문제 문구.
     * @param {Translate} t - 번역 함수.
     * @param {PointShopItemField} field - 칸.
     * @param {PointShopItemProblem} problem - 문제.
     * @param {string} locale - 숫자 표기 언어.
     * @returns {string} 칸 아래에 보일 한 줄.
     */
    static itemProblem(t: Translate, field: PointShopItemField, problem: PointShopItemProblem, locale: string): string {
        if (problem === 'REQUIRED') {
            if (field === 'kind') return t('pointShop.problem.kindRequired');
            if (field === 'usageType') return t('pointShop.problem.usageRequired');
            if (field === 'borderStyle') return t('pointShop.problem.borderRequired');
            return t('pointShop.problem.required');
        }
        if (problem === 'NOT_INTEGER') return t('pointShop.problem.notInteger');
        if (problem === 'OUT_OF_RANGE') {
            const [min, max] = field === 'durationDays' ? [UtilPointShop.DURATION.min, UtilPointShop.DURATION.max] : [UtilPointShop.PRICE.min, UtilPointShop.PRICE.max];
            return t('pointShop.problem.outOfRange', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
        }
        if (problem === 'CODE_FORMAT') return t('pointShop.problem.codeFormat', { min: UtilPointShop.CODE.min, max: UtilPointShop.CODE.max });
        if (problem === 'TOO_LONG') {
            const max = field === 'description' || field === 'descriptionEn' ? UtilPointShop.DESCRIPTION_MAX : UtilPointShop.NAME_MAX;
            return t('pointShop.problem.tooLong', { max });
        }
        if (problem === 'COLOR_FORMAT') return t('pointShop.problem.colorFormat');
        if (problem === 'END_BEFORE_START') return t('pointShop.problem.endBeforeStart');
        return t('pointShop.problem.usageNotAllowed');
    }

    /** 예외 환불 사유의 문제 문구 */
    static reasonProblem(t: Translate, problem: PointShopReasonProblem): string {
        if (problem === 'REQUIRED') return t('pointShop.refund.reasonRequired');
        if (problem === 'TOO_SHORT') return t('pointShop.refund.reasonTooShort', { min: UtilPointShop.REASON.min });
        return t('pointShop.refund.reasonTooLong', { max: UtilPointShop.REASON.max });
    }

    /** 환불 기한 칸의 문제 문구 */
    static refundDaysProblem(t: Translate, problem: PointShopRefundDaysProblem): string {
        if (problem === 'REQUIRED') return t('pointShop.problem.required');
        if (problem === 'NOT_INTEGER') return t('pointShop.problem.notInteger');
        return t('pointShop.problem.outOfRange', { min: UtilPointShop.REFUND_DAYS.min, max: UtilPointShop.REFUND_DAYS.max });
    }

    /**
     * 셀프 환불이 안 되는 이유 한 마디.
     * @param {Translate} t - 번역 함수.
     * @param {PointShopSelfRefundBlock} block - 이유.
     * @returns {string} 「사용함」·「기한 지남」.
     */
    static selfRefundBlock(t: Translate, block: PointShopSelfRefundBlock): string {
        return block === 'USED' ? t('pointShop.selfRefund.used') : t('pointShop.selfRefund.expired');
    }

    /**
     * 사용 방식 + 기간을 한 마디로 — 기간제면 「기간제 · 30일」.
     * @param {Translate} t - 번역 함수.
     * @param {string} usageType - 사용 방식.
     * @param {number | null} durationDays - 기간(기간제만).
     * @returns {string} 표시 글자.
     */
    static usageLine(t: Translate, usageType: string, durationDays: number | null): string {
        const usage = t(`pointShop.usage.${usageType}`);
        return usageType === 'PERIOD' && durationDays !== null ? t('pointShop.usageWithDays', { usage, days: durationDays }) : usage;
    }

    /**
     * 아바타 카테고리 이름 — 카탈로그 ID 를 i18n 키(`pointShop.avatarCategory.<id>`)로 푼다(카탈로그 이름의 「(남/여)」 꼬리표는 쓰지 않는다). 모르는 ID 는 그대로.
     * @param {Translate} t - 번역 함수.
     * @param {string} id - 카테고리 ID.
     * @returns {string} 표시 이름.
     */
    static avatarCategory(t: Translate, id: string): string {
        return AVATAR_CATEGORY_IDS.includes(id) ? t(`pointShop.avatarCategory.${id}`) : id;
    }

    /**
     * 가격 범위 — 같으면 한 값, 다르면 「최저~최고P」, 없으면 「—」.
     * @param {Translate} t - 번역 함수.
     * @param {number | null} min - 최저.
     * @param {number | null} max - 최고.
     * @param {string} locale - 숫자 표기 언어.
     * @returns {string} 표시 글자.
     */
    static priceRange(t: Translate, min: number | null, max: number | null, locale: string): string {
        if (min === null || max === null) return '—';
        if (min === max) return t('pointShop.pricePoint', { price: min.toLocaleString(locale) });
        return t('pointShop.avatar.priceRange', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
    }

    /**
     * 대비 보정 안내 — 라이트·다크 중 보정된 쪽을 말한다. 둘 다 아니면 ''.
     * @param {Translate} t - 번역 함수.
     * @param {boolean} light - 라이트에서 보정됐다.
     * @param {boolean} dark - 다크에서 보정됐다.
     * @returns {string} 안내 한 줄.
     */
    static correctedNote(t: Translate, light: boolean, dark: boolean): string {
        if (light && dark) return t('pointShop.preview.correctedBoth');
        if (light) return t('pointShop.preview.correctedLight');
        if (dark) return t('pointShop.preview.correctedDark');
        return '';
    }
}
