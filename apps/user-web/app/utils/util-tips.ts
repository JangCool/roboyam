import type { LocationQuery } from 'vue-router';
import type { PostBoardRef } from '~/composables/board/api/usePostApi.types';

/**
 * 팁·강좌·사용기 묶음 화면(`/tips?board=&page=`)의 주소 규칙 — 게시판 칩·쪽 쿼리. 상태도 요청도 없다.
 * 묶음에 든 게시판(칩)은 서버(지금은 목업)가 정해 주므로 여기에 slug 를 적지 않는다.
 */

/** 화면 상태 — 주소의 쿼리가 곧 상태다 */
export interface TipsState {
    /** 게시판 slug — 빈 글자 = 전체 */
    board: string;
    /** 1부터 */
    page: number;
}

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

export default class UtilTips {
    static readonly PATH = '/tips';
    static readonly MAX_PAGE = 100;
    /** slug 로 쓸 수 있는 글자(게시판 주소 규칙 — 영소문자·숫자·`-`·`_`) */
    static readonly SLUG = /^[a-z0-9_-]{1,40}$/;

    /**
     * 주소의 쿼리 → 상태(틀린 게시판은 전체, 틀린 쪽은 1)
     * @param {LocationQuery} query - 라우트의 쿼리.
     * @returns {TipsState} 상태.
     */
    static parse(query: LocationQuery): TipsState {
        const board = first(query.board).trim().toLowerCase();
        const text = first(query.page).trim();
        const page = /^\d+$/.test(text) ? Number(text) : 1;
        return {
            board: UtilTips.SLUG.test(board) ? board : '',
            page: page >= 1 ? Math.min(page, UtilTips.MAX_PAGE) : 1,
        };
    }

    /**
     * 상태 → 주소의 쿼리(기본값은 뺀다)
     * @param {TipsState} state - 상태.
     * @returns {Record<string, string>} 쿼리.
     */
    static build(state: TipsState): Record<string, string> {
        const out: Record<string, string> = {};
        if (state.board) out.board = state.board;
        if (state.page > 1) out.page = String(state.page);
        return out;
    }

    /**
     * 받은 묶음에 있는 게시판만 남긴다 — 모르는 slug 는 전체(빈 글자)
     * @param {string} board - 주소의 게시판.
     * @param {PostBoardRef[] | null} boards - 묶음의 게시판(아직 못 받았으면 null — 그대로 둔다).
     * @returns {string} 게시판 slug 또는 빈 글자.
     */
    static knownBoard(board: string, boards: PostBoardRef[] | null): string {
        if (!board || !boards) return board;
        return boards.some((b) => b.slug === board) ? board : '';
    }
}
