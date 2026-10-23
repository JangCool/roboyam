import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { PopularFill, PopularListResponse, PopularPeriod } from '~/composables/home/api/useHomeApi.types';
import UtilPopular from '~/utils/util-popular';

/** 제목 + 설명 */
export interface FeedCopy {
    title: string;
    text: string;
}

/**
 * 최신글 전체·인기글 전체 화면의 계산이 낀 문구 — 개수 안내·기간 이름·빈 상태·채운 줄·이번 주 범위·쪽 제목. 문구 본문은 `i18n/locales/{ko,en}/feed.ts`.
 * `name` 은 서비스 이름(`useSiteTerms().term('LATEST'|'POPULAR')`)이다.
 */
export default class UtilFeedMessages {
    /**
     * 스크린리더가 읽는 결과 한 줄 — 「최신글 20개 · 더 있어요」
     * @param {Translate} t - 번역 함수.
     * @param {string} name - 목록 이름.
     * @param {'loading' | 'ready' | 'error'} status - 상태.
     * @param {number} count - 보이는 글 수.
     * @param {boolean} hasMore - 더 있는가.
     * @returns {string} 안내. 오류일 때는 빈 글자(오류 상태가 따로 알린다).
     */
    static summary(t: Translate, name: string, status: 'loading' | 'ready' | 'error', count: number, hasMore: boolean): string {
        if (status === 'loading') return t('feed.loading', { name });
        if (status !== 'ready') return '';
        if (count === 0) return t('feed.summaryEmpty', { name });
        return hasMore ? t('feed.summaryCountMore', { name, count }) : t('feed.summaryCount', { name, count });
    }

    /**
     * 기간 탭 이름
     * @param {Translate} t - 번역 함수.
     * @param {PopularPeriod} period - 기간.
     * @returns {string} 「실시간」「어제」「이번 주」.
     */
    static periodLabel(t: Translate, period: PopularPeriod): string {
        if (period === 'YESTERDAY') return t('feed.periodYesterday');
        if (period === 'WEEK') return t('feed.periodWeek');
        return t('feed.periodRealtime');
    }

    /**
     * 머리 아래 한 줄 설명
     * @param {Translate} t - 번역 함수.
     * @param {PopularPeriod} period - 기간.
     * @returns {string} 설명.
     */
    static subtitle(t: Translate, period: PopularPeriod): string {
        if (period === 'YESTERDAY') return t('feed.subtitleYesterday');
        if (period === 'WEEK') return t('feed.subtitleWeek');
        return t('feed.subtitleRealtime');
    }

    /**
     * 그 기간에 순위 글이 없을 때
     * @param {Translate} t - 번역 함수.
     * @param {PopularPeriod} period - 기간.
     * @returns {FeedCopy} 제목과 설명.
     */
    static popularEmpty(t: Translate, period: PopularPeriod): FeedCopy {
        if (period === 'YESTERDAY') return { title: t('feed.emptyYesterdayTitle'), text: t('feed.emptyYesterdayText') };
        if (period === 'WEEK') return { title: t('feed.emptyWeekTitle'), text: t('feed.emptyWeekText') };
        return { title: t('feed.emptyRealtimeTitle'), text: t('feed.emptyRealtimeText') };
    }

    /**
     * 채운 줄의 작은 표시 글자
     * @param {Translate} t - 번역 함수.
     * @param {PopularFill} fill - 출처.
     * @returns {{ label: string; aria: string }} 보이는 글자와 스크린리더 글자.
     */
    static fillTag(t: Translate, fill: PopularFill): { label: string; aria: string } {
        return fill === 'LAST_WEEK'
            ? { label: t('feed.fillLastWeek'), aria: t('feed.fillLastWeekLabel') }
            : { label: t('feed.fillYesterday'), aria: t('feed.fillYesterdayLabel') };
    }

    /**
     * 채운 줄이 섞인 까닭 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {PopularFill} fill - 처음 나온 채운 줄의 출처.
     * @returns {string} 안내.
     */
    static fillNotice(t: Translate, fill: PopularFill): string {
        return fill === 'LAST_WEEK' ? t('feed.fillNoticeLastWeek') : t('feed.fillNoticeYesterday');
    }

    /**
     * 이번 주 탭 옆의 범위 — 「이번 주 10.05 ~ 10.11」(서버가 정한 범위, 서비스 달력)
     * @param {Translate} t - 번역 함수.
     * @param {PopularPeriod} period - 기간.
     * @param {Pick<PopularListResponse, 'from' | 'to'> | null} res - 응답.
     * @returns {string} 범위. 이번 주 탭이 아니거나 범위가 없으면 빈 글자.
     */
    static rangeNote(t: Translate, period: PopularPeriod, res: Pick<PopularListResponse, 'from' | 'to'> | null): string {
        const days = period === 'WEEK' ? UtilPopular.rangeDays(res) : null;
        if (!days) return '';
        const md = (d: { month: number; day: number }) => `${String(d.month).padStart(2, '0')}.${String(d.day).padStart(2, '0')}`;
        return t('feed.rangeNote', { period: UtilFeedMessages.periodLabel(t, period), from: md(days.from), to: md(days.to) });
    }

    /**
     * 쪽 제목 — 「인기글 · 이번 주 (2쪽)」
     * @param {Translate} t - 번역 함수.
     * @param {string} name - 서비스 이름.
     * @param {PopularPeriod} period - 기간.
     * @param {number} page - 쪽.
     * @returns {string} 제목.
     */
    static popularSeoTitle(t: Translate, name: string, period: PopularPeriod, page: number): string {
        const label = UtilFeedMessages.periodLabel(t, period);
        return page > 1 ? t('feed.popularSeoTitlePage', { name, period: label, page }) : t('feed.popularSeoTitle', { name, period: label });
    }

    /**
     * 쪽 설명
     * @param {Translate} t - 번역 함수.
     * @param {PopularPeriod} period - 기간.
     * @returns {string} 설명.
     */
    static popularSeoDescription(t: Translate, period: PopularPeriod): string {
        if (period === 'YESTERDAY') return t('feed.seoYesterday');
        if (period === 'WEEK') return t('feed.seoWeek');
        return t('feed.seoRealtime');
    }
}
