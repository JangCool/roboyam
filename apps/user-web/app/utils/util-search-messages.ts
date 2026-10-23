import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SearchProblem } from '~/utils/util-search-rules';

/**
 * 검색 화면의 계산이 낀 문구 — 검색어가 걸리는 까닭·결과 안내 한 줄. 문구 본문은 `i18n/locales/{ko,en}/search.ts`.
 */
export default class UtilSearchMessages {
    /**
     * 검색어가 걸리는 까닭 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {SearchProblem} problem - 까닭.
     * @returns {string} 안내.
     */
    static problem(t: Translate, problem: SearchProblem): string {
        if (problem === 'TOO_LONG') return t('search.tooLong');
        if (problem === 'SHORT_WORD') return t('search.shortWord');
        return t('search.tooShort');
    }

    /**
     * 결과 안내 한 줄(스크린리더·결과 위) — 「‘로봇’ 검색 결과 · 2쪽 · 20개 · 더 있어요」
     * @param {Translate} t - 번역 함수.
     * @param {object} input - 검색어·상태·개수·쪽(데스크톱 쪽 번호일 때만)·더 있는가.
     * @returns {string} 안내. 결과가 없거나 끝나지 않았으면 빈 글자 또는 상태 안내.
     */
    static summary(t: Translate, input: { q: string; status: string; count: number; page: number | null; hasMore: boolean }): string {
        if (input.status === 'loading') return t('search.searching', { q: input.q });
        if (input.status !== 'ready') return '';
        if (input.count === 0) return t('search.emptyTitle', { q: input.q });
        const text = input.page !== null ? t('search.summaryPage', { q: input.q, page: input.page, count: input.count }) : t('search.summary', { q: input.q, count: input.count });
        return input.hasMore ? t('search.summaryMore', { text }) : text;
    }
}
