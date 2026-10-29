import type { HomeBoardScope, HomeGadgetConfig, HomeGroupBuySort, HomePostListSort } from '~/composables/home/api/useHomeLayoutApi.types';

/** 개수 칸 하나 — 칸 이름 · 범위 · 처음 값 */
export interface GadgetCountSpec {
    field: 'count' | 'perBoard';
    min: number;
    max: number;
    initial: number;
}

/** 고칠 수 있는 종류 하나의 설정 칸 — 서버 `HomeGadgetService` 가 읽는 칸·범위·처음 값과 같다 */
export interface GadgetConfigSpec {
    /** 대상 게시판을 고르는가 */
    boards: boolean;
    count: GadgetCountSpec;
    /** 고를 정렬(없으면 정렬 칸 없음) */
    sorts: readonly string[];
    /** 정렬 처음 값 */
    sort: string | null;
}

const GROUP_BUY_SORTS: readonly HomeGroupBuySort[] = ['DEADLINE', 'NEWEST', 'POPULAR'];
const POST_LIST_SORTS: readonly HomePostListSort[] = ['LATEST', 'POPULAR', 'VIEWS', 'LIKES', 'COMMENTS'];

/** 내용 설정(⚙)을 고칠 수 있는 종류 — user-web `UtilAdminPreview` 의 `SETTABLE_KINDS` 와 같은 종류 */
const SPECS: Readonly<Record<string, GadgetConfigSpec>> = {
    GROUP_BUY: { boards: false, count: { field: 'count', min: 1, max: 10, initial: 5 }, sorts: GROUP_BUY_SORTS, sort: 'DEADLINE' },
    POST_LIST: { boards: true, count: { field: 'count', min: 1, max: 30, initial: 10 }, sorts: POST_LIST_SORTS, sort: 'LATEST' },
    GALLERY: { boards: true, count: { field: 'count', min: 1, max: 24, initial: 6 }, sorts: [], sort: null },
    BOARD_CARDS: { boards: true, count: { field: 'perBoard', min: 1, max: 10, initial: 4 }, sorts: [], sort: null },
};

/** 직접 고를 수 있는 게시판 수 한도 */
const BOARD_PICK_MAX = 20;

function clamp(value: unknown, min: number, max: number, fallback: number): number {
    const n = typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : fallback;
    return Math.min(max, Math.max(min, n));
}

/**
 * 가젯 내용 설정(관리자 실제 화면 편집의 ⚙ 설정 창)의 순수 계산 — 고칠 수 있는 종류, 종류마다 칸·범위·처음 값, 읽기·바로잡기.
 * 공구(개수·정렬, 2026-10-29) · 글 목록 · 갤러리 · 게시판 카드(대상 게시판·개수·정렬, 가젯 페이지 때 넓힘 2026-10-29).
 * 대상 게시판 `FOLDER`(「이 페이지가 연결된 폴더의 게시판 전체」)는 가젯 페이지에서만 고를 수 있다 — 홈에서는 `ALL`(전체)·`PICK`(직접 고르기).
 */
export default class UtilGadgetConfig {
    /** 직접 고를 수 있는 게시판 수 한도 */
    static readonly BOARD_PICK_MAX = BOARD_PICK_MAX;

    /**
     * 내용 설정을 고칠 수 있는 종류인가
     * @param {string} type - 가젯 종류.
     * @returns {boolean} 고칠 수 있으면 true.
     */
    static settable(type: string): boolean {
        return type in SPECS;
    }

    /**
     * 종류의 설정 칸(고칠 수 없는 종류는 null)
     * @param {string} type - 가젯 종류.
     * @returns {GadgetConfigSpec | null} 칸.
     */
    static spec(type: string): GadgetConfigSpec | null {
        return SPECS[type] ?? null;
    }

    /**
     * 처음 값 — 새로 넣은 가젯 · 설정이 없는 가젯. 가젯 페이지면 대상 게시판은 폴더 게시판 전체, 홈이면 전체.
     * @param {string} type - 가젯 종류.
     * @param {boolean} inPage - 가젯 페이지인가.
     * @returns {HomeGadgetConfig | null} 설정(고칠 수 없는 종류는 null).
     */
    static initial(type: string, inPage: boolean): HomeGadgetConfig | null {
        const spec = SPECS[type];
        if (!spec) return null;
        const out: HomeGadgetConfig = { [spec.count.field]: spec.count.initial };
        if (spec.sort) out.sort = spec.sort as HomeGadgetConfig['sort'];
        if (spec.boards) {
            out.boardScope = inPage ? 'FOLDER' : 'ALL';
            out.boardIds = [];
        }
        if (type === 'POST_LIST') out.periodTabs = false;
        if (type === 'BOARD_CARDS') out.cardHead = true;
        return out;
    }

    /**
     * 읽기·바로잡기 — 범위 밖 개수는 범위 안으로, 모르는 정렬은 처음 값으로, 고른 게시판이 없으면 `ALL`(페이지면 `FOLDER`), 페이지가 아니면 `FOLDER` 는 `ALL`.
     * 인기 정렬이 아니면 기간 탭을 끈다.
     * @param {string} type - 가젯 종류.
     * @param {HomeGadgetConfig | null | undefined} config - 받은 설정.
     * @param {boolean} inPage - 가젯 페이지인가.
     * @returns {HomeGadgetConfig | null} 설정(고칠 수 없는 종류는 null).
     */
    static normalize(type: string, config: HomeGadgetConfig | null | undefined, inPage: boolean): HomeGadgetConfig | null {
        const spec = SPECS[type];
        const initial = UtilGadgetConfig.initial(type, inPage);
        if (!spec || !initial) return null;
        const c = config ?? {};
        const out: HomeGadgetConfig = { [spec.count.field]: clamp(c[spec.count.field], spec.count.min, spec.count.max, spec.count.initial) };
        if (spec.sort) out.sort = (spec.sorts.includes(String(c.sort)) ? c.sort : spec.sort) as HomeGadgetConfig['sort'];
        if (spec.boards) {
            const ids = Array.isArray(c.boardIds) ? [...new Set(c.boardIds.filter((s): s is string => typeof s === 'string' && s.trim() !== ''))].slice(0, BOARD_PICK_MAX) : [];
            let scope: HomeBoardScope = c.boardScope === 'PICK' || c.boardScope === 'FOLDER' || c.boardScope === 'ALL' ? c.boardScope : ids.length ? 'PICK' : (initial.boardScope ?? 'ALL');
            if (scope === 'FOLDER' && !inPage) scope = 'ALL';
            if (scope === 'PICK' && !ids.length) scope = initial.boardScope ?? 'ALL';
            out.boardScope = scope;
            out.boardIds = scope === 'PICK' ? ids : [];
        }
        if (type === 'POST_LIST') out.periodTabs = out.sort === 'POPULAR' && c.periodTabs === true;
        if (type === 'BOARD_CARDS') out.cardHead = c.cardHead !== false;
        return out;
    }

    /**
     * 바뀜 비교용 글자 — 칸 차례에 상관없이 같은 설정이면 같은 글자
     * @param {HomeGadgetConfig | null | undefined} config - 설정.
     * @returns {string} 비교 글자.
     */
    static key(config: HomeGadgetConfig | null | undefined): string {
        if (!config) return '';
        const c = config;
        return JSON.stringify([c.count ?? null, c.sort ?? null, c.boardScope ?? null, c.boardIds ?? null, c.periodTabs ?? null, c.perBoard ?? null, c.cardHead ?? null]);
    }
}
