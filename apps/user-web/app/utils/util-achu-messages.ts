import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AchuListResponse, AchuPeriod } from '~/composables/highlight/api/useHighlightApi.types';
import UtilTime from '#layers/display-time/utils/util-time';

/** 「아추!」 페이지의 계산이 낀 문구(기간 이름·설명·범위·빈 화면·화면 읽기 상태) — 규칙은 `UtilAchu`(achu-page-contract) */

/** 서비스 달력의 「MM.DD」 */
function monthDay(ms: number): string {
    const [, month = '', day = ''] = (UtilTime.serviceDayOf(ms) ?? '').split('-');
    return `${month}.${day}`;
}

export default class UtilAchuMessages {
    /**
     * 기간 탭 이름
     * @param {Translate} t - 번역 함수.
     * @param {AchuPeriod} period - 기간.
     * @returns {string} 「일간 아추!」「주간 아추!」「월간 아추!」.
     */
    static periodLabel(t: Translate, period: AchuPeriod): string {
        if (period === 'WEEK') return t('achu.periodWeek');
        if (period === 'MONTH') return t('achu.periodMonth');
        return t('achu.periodDay');
    }

    /**
     * 머리의 한 줄 설명 — 고른 기간이 무엇을 보여 주는지
     * @param {Translate} t - 번역 함수.
     * @param {AchuPeriod} period - 기간.
     * @returns {string} 설명.
     */
    static subtitle(t: Translate, period: AchuPeriod): string {
        if (period === 'WEEK') return t('achu.subtitleWeek');
        if (period === 'MONTH') return t('achu.subtitleMonth');
        return t('achu.subtitleDay');
    }

    /**
     * 탭 옆의 기간 범위 — 일간은 「11.19」, 주간은 「11.16 ~ 11.22」, 월간은 「11월」. 받은 응답이 없으면 빈 글자
     * @param {Translate} t - 번역 함수.
     * @param {AchuPeriod} period - 기간.
     * @param {Pick<AchuListResponse, 'from' | 'to'> | null} res - 응답의 범위.
     * @returns {string} 범위 글자.
     */
    static rangeNote(t: Translate, period: AchuPeriod, res: Pick<AchuListResponse, 'from' | 'to'> | null): string {
        const from = res ? Date.parse(res.from) : NaN;
        const to = res ? Date.parse(res.to) : NaN;
        if (!Number.isFinite(from) || !Number.isFinite(to)) return '';
        if (period === 'DAY') return monthDay(from);
        if (period === 'WEEK') return t('achu.rangeWeek', { from: monthDay(from), to: monthDay(to - 1) });
        const [, month = ''] = (UtilTime.serviceDayOf(from) ?? '').split('-');
        return t('achu.rangeMonth', { month: Number(month) });
    }

    /**
     * 그 기간에 고른 글이 없을 때
     * @param {Translate} t - 번역 함수.
     * @param {AchuPeriod} period - 기간.
     * @returns {{ title: string; text: string }} 제목과 설명.
     */
    static empty(t: Translate, period: AchuPeriod): { title: string; text: string } {
        if (period === 'WEEK') return { title: t('achu.emptyWeekTitle'), text: t('achu.emptyText') };
        if (period === 'MONTH') return { title: t('achu.emptyMonthTitle'), text: t('achu.emptyText') };
        return { title: t('achu.emptyDayTitle'), text: t('achu.emptyDayText') };
    }

    /**
     * 화면 읽기용 상태 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {string} name - 「일간 아추!」 같은 기간 이름.
     * @param {'loading' | 'ready' | 'error'} status - 받는 상태.
     * @param {number} count - 그 기간 글 수.
     * @returns {string} 상태 글자.
     */
    static summary(t: Translate, name: string, status: 'loading' | 'ready' | 'error', count: number): string {
        if (status === 'loading') return t('achu.loading', { name });
        if (status !== 'ready') return '';
        return count === 0 ? t('achu.summaryEmpty', { name }) : t('achu.summaryCount', { name, count });
    }

    /**
     * 쪽 제목 — 「아추! · 주간 아추! (2쪽)」
     * @param {Translate} t - 번역 함수.
     * @param {AchuPeriod} period - 기간.
     * @param {number} page - 쪽.
     * @returns {string} 제목.
     */
    static seoTitle(t: Translate, period: AchuPeriod, page: number): string {
        const values = { name: t('achu.name'), period: UtilAchuMessages.periodLabel(t, period), page };
        return page > 1 ? t('achu.seoTitlePage', values) : t('achu.seoTitle', values);
    }
}
