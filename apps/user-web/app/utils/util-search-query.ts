import type { LocationQuery } from 'vue-router';
import type { SearchOrder, SearchScope } from '~/composables/search/api/useSearchApi.types';
import UtilSearchRules from '~/utils/util-search-rules';

/**
 * 검색 화면 주소의 쿼리(`/search?q=&scope=&board=&order=&page=`) 읽고 만들기 — 주소가 화면의 상태다(뒤로 가기·공유·새로고침이 같은 결과로 돌아온다).
 * 이상한 값은 기본값(전체 범위·모든 게시판·최신순·1쪽)으로 돌려 놓고, 기본값은 주소에서 뺀다. 최근 검색어(이 기기에만 저장) 다루기도 여기 있다.
 */

const SCOPES: readonly SearchScope[] = ['all', 'title', 'body', 'author'];

/** 검색 화면의 상태 */
export interface SearchState {
    q: string;
    scope: SearchScope;
    /** 게시판 slug — 빈 글자면 모든 게시판 */
    board: string;
    order: SearchOrder;
    page: number;
}

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

export default class UtilSearchQuery {
    /** 쪽 번호 상한 — 서버가 주는 `totalPages` 도 10 까지다(그 뒤는 커서 더보기) */
    static readonly MAX_PAGE = 10;

    /** 최근 검색어 개수 상한 */
    static readonly RECENT_MAX = 10;

    /** 범위 차례 */
    static readonly SCOPES: readonly SearchScope[] = SCOPES;

    /**
     * 주소의 쿼리 → 검증된 상태
     * @param {LocationQuery} query - 라우트의 쿼리.
     * @returns {SearchState} 상태.
     */
    static parse(query: LocationQuery): SearchState {
        const scope = first(query.scope);
        const page = Number(first(query.page));
        return {
            q: UtilSearchRules.normalize(first(query.q)),
            scope: SCOPES.find((s) => s === scope) ?? 'all',
            board: first(query.board).trim(),
            order: first(query.order) === 'oldest' ? 'oldest' : 'latest',
            page: Number.isInteger(page) && page >= 1 ? Math.min(page, UtilSearchQuery.MAX_PAGE) : 1,
        };
    }

    /**
     * 상태 → 주소의 쿼리(기본값은 뺀다)
     * @param {Partial<SearchState>} value - 상태.
     * @returns {Record<string, string>} 쿼리.
     */
    static build(value: Partial<SearchState>): Record<string, string> {
        const out: Record<string, string> = {};
        if (value.q) out.q = value.q;
        if (value.scope && value.scope !== 'all') out.scope = value.scope;
        if (value.board) out.board = value.board;
        if (value.order === 'oldest') out.order = 'oldest';
        if (value.page && value.page > 1) out.page = String(value.page);
        return out;
    }

    /**
     * 상태 하나를 가리키는 글자 — 같은 검색인지 비교하고, 방금 본 결과를 기억하는 열쇠로 쓴다
     * @param {SearchState} state - 상태.
     * @returns {string} 열쇠.
     */
    static key(state: SearchState): string {
        return `${state.q}|${state.scope}|${state.board}|${state.order}|${state.page}`;
    }

    /**
     * 저장해 둔 최근 검색어 읽기 — 글자만, 다듬어서, 중복 없이, 상한까지
     * @param {unknown} raw - 저장소에서 읽은 값(JSON 을 푼 것).
     * @returns {string[]} 최근 검색어(최근 것이 앞).
     */
    static sanitizeRecent(raw: unknown): string[] {
        if (!Array.isArray(raw)) return [];
        const out: string[] = [];
        for (const v of raw) {
            if (typeof v !== 'string') continue;
            const q = UtilSearchRules.normalize(v);
            if (q && !out.includes(q) && UtilSearchRules.check(q) === null) out.push(q);
            if (out.length >= UtilSearchQuery.RECENT_MAX) break;
        }
        return out;
    }

    /**
     * 최근 검색어에 하나 더한다 — 맨 앞으로, 같은 것은 지우고, 상한까지
     * @param {readonly string[]} list - 지금 목록.
     * @param {string} q - 검색어.
     * @returns {string[]} 새 목록.
     */
    static recordRecent(list: readonly string[], q: string): string[] {
        const term = UtilSearchRules.normalize(q);
        if (!term) return [...list];
        return [term, ...list.filter((v) => v !== term)].slice(0, UtilSearchQuery.RECENT_MAX);
    }
}
