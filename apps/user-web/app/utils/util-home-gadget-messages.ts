import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';

/**
 * 홈 가젯의 계산이 낀 문구 — 분기(종류별 빈 안내·마감 표시)와 자리표시자(금액)만 둔다. 상수 문구는 컴포넌트가 `t('home.…')` 로 부른다(`i18n.md`).
 */
export default class UtilHomeGadgetMessages {
    /**
     * 비었을 때 안내 — 운영자가 적은 문구가 있으면 그것, 없으면 종류별 기본 문구
     * @param {Translate} t - 번역 함수.
     * @param {HomeGadget} gadget - 가젯.
     * @returns {string} 안내 문구.
     */
    static emptyTitle(t: Translate, gadget: HomeGadget): string {
        const custom = gadget.style.empty.text.trim();
        if (custom !== '') return custom;
        switch (gadget.type) {
            case 'POST_LIST':
            case 'BOARD_CARDS':
                return t('home.emptyPosts');
            case 'GALLERY':
                return t('home.emptyGallery');
            case 'HIGHLIGHT':
                return t('home.emptyHighlight');
            case 'GROUPS':
                return t('home.emptyGroups');
            case 'GROUP_BUY':
                return t('home.emptyGroupBuy');
            default:
                return t('home.emptyDefault');
        }
    }

    /**
     * 모바일 접기 단추의 이름
     * @param {Translate} t - 번역 함수.
     * @param {string} title - 가젯 제목.
     * @param {boolean} open - 지금 펼쳐져 있는가.
     * @returns {string} 단추 이름.
     */
    static foldLabel(t: Translate, title: string, open: boolean): string {
        return t(open ? 'home.foldClose' : 'home.foldOpen', { title });
    }

    /**
     * 금액(원) — 세 자리마다 쉼표
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 금액(원).
     * @returns {string} 예: 「39,000원」 · 「₩39,000」.
     */
    static price(t: Translate, amount: number): string {
        return t('home.price', { amount: amount.toLocaleString('ko-KR') });
    }

    /**
     * 공구 마감 표시 — 24시간이 안 남았으면 「곧 마감」, 아니면 「D-n」. 지났으면 빈 문자열(그리지 않는다)
     * @param {Translate} t - 번역 함수.
     * @param {number | null} days - 남은 날(`UtilHomeGadget.daysLeft`).
     * @returns {string} 표시 글자.
     */
    static deadline(t: Translate, days: number | null): string {
        if (days === null || days < 0) return '';
        return days === 0 ? t('home.groupBuySoon') : t('home.groupBuyDday', { n: days });
    }
}
