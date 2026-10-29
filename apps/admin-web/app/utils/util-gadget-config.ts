import type {
    HomeBoardScope,
    HomeGadgetConfig,
    HomeGroupBuySort,
    HomeLayoutGadget,
    HomePostListSort,
    HomePostMeta,
    HomePostRowStyle,
    HomePostThumbnail,
    HomeQuickLink,
    HomeQuickLinkKind,
} from '~/composables/home/api/useHomeLayoutApi.types';
import UtilGadgetStyle from '~/utils/util-gadget-style';

/** 가젯이 놓인 자리 — 홈 · 가젯 페이지 · 게시판 레이아웃(고를 수 있는 대상 게시판 범위가 다르다: `FOLDER` 는 페이지에서만, `BOARD` 는 게시판 레이아웃에서만) */
export type GadgetPlace = 'HOME' | 'PAGE' | 'BOARD';

/** 개수 칸 하나 — 칸 이름 · 범위 · 처음 값 */
export interface GadgetCountSpec {
    field: 'count' | 'perBoard';
    min: number;
    max: number;
    initial: number;
}

/** 고칠 수 있는 종류 하나의 공통 칸 — 서버 `HomeGadgetService`·user-web `UtilHomeGadget` 이 읽는 칸·범위·처음 값과 같다. 종류만의 칸은 `normalize` 에 */
export interface GadgetConfigSpec {
    /** 대상 게시판을 고르는가 */
    boards: boolean;
    /** 개수 칸(없으면 null — 바로가기·안내 글) */
    count: GadgetCountSpec | null;
    /** 고를 정렬(없으면 정렬 칸 없음) */
    sorts: readonly string[];
    /** 정렬 처음 값 */
    sort: string | null;
}

/** 설정 창이 막는 문제 하나 — 바로가기 항목의 칸 */
export interface GadgetConfigProblem {
    /** 항목 차례(0부터) */
    index: number;
    field: 'label' | 'value';
    /** 문구 키(`homeLayout.<키>`) */
    key: 'quickLabelRequired' | 'quickBoardRequired' | 'quickPathInvalid';
}

const GROUP_BUY_SORTS: readonly HomeGroupBuySort[] = ['DEADLINE', 'NEWEST', 'POPULAR'];
const POST_LIST_SORTS: readonly HomePostListSort[] = ['LATEST', 'POPULAR', 'VIEWS', 'LIKES', 'COMMENTS'];
const ROW_STYLES: readonly HomePostRowStyle[] = ['HEADLINE', 'RANK', 'ARTICLE', 'COMPACT'];
const THUMBNAILS: readonly HomePostThumbnail[] = ['AUTO', 'NONE', 'RIGHT', 'LEFT'];
const META: readonly HomePostMeta[] = ['BOARD', 'PREFIX', 'COMMENTS', 'TIME', 'AUTHOR'];
/** 글 줄 정보 처음 값 — user-web `DEFAULT_META` */
const DEFAULT_META: readonly HomePostMeta[] = ['BOARD', 'PREFIX', 'COMMENTS', 'TIME'];
const QUICK_KINDS: readonly HomeQuickLinkKind[] = ['ALL', 'POPULAR', 'LATEST', 'GROUPS', 'GROUP_BUY', 'BOARD', 'PATH'];

/** 내용 설정(⚙)을 고칠 수 있는 종류 — 8종 전부(2026-10-30). user-web `UtilAdminPreview` 의 `SETTABLE_KINDS` 와 같은 종류 */
const SPECS: Readonly<Record<string, GadgetConfigSpec>> = {
    GROUP_BUY: { boards: false, count: { field: 'count', min: 1, max: 10, initial: 5 }, sorts: GROUP_BUY_SORTS, sort: 'DEADLINE' },
    POST_LIST: { boards: true, count: { field: 'count', min: 1, max: 30, initial: 10 }, sorts: POST_LIST_SORTS, sort: 'LATEST' },
    GALLERY: { boards: true, count: { field: 'count', min: 1, max: 24, initial: 6 }, sorts: [], sort: null },
    BOARD_CARDS: { boards: true, count: { field: 'perBoard', min: 1, max: 10, initial: 4 }, sorts: [], sort: null },
    HIGHLIGHT: { boards: false, count: { field: 'count', min: 1, max: 6, initial: 5 }, sorts: [], sort: null },
    GROUPS: { boards: false, count: { field: 'count', min: 1, max: 6, initial: 3 }, sorts: [], sort: null },
    QUICK_LINKS: { boards: false, count: null, sorts: [], sort: null },
    NOTICE_TEXT: { boards: false, count: null, sorts: [], sort: null },
};

/** 직접 고를 수 있는 게시판 수 한도 */
const BOARD_PICK_MAX = 20;
/** 바로가기 항목 수 · 이름 글자 한도 — user-web `quickLinkItems` 가 이름을 12자로 자른다 */
const QUICK_LINK_MAX = 12;
const QUICK_LABEL_MAX = 12;
/** 안내 글 글자 한도 — user-web `noticeText` 가 300자로 자른다 */
const NOTICE_MAX = 300;
/** 게시판 slug 모양 — user-web `UtilHomeGadget` `SLUG` */
const SLUG = /^[A-Za-z0-9_-]{1,60}$/;

function obj(value: unknown): Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function clamp(value: unknown, min: number, max: number, fallback: number): number {
    const n = typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : fallback;
    return Math.min(max, Math.max(min, n));
}

function oneOf<T extends string>(value: unknown, list: readonly T[], fallback: T): T {
    return list.find((v) => v === value) ?? fallback;
}

/** 칸 이름 차례에 상관없는 JSON 글자 */
function stable(value: unknown): string {
    if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
    if (value && typeof value === 'object') {
        const o = value as Record<string, unknown>;
        return `{${Object.keys(o)
            .filter((k) => o[k] !== undefined)
            .sort()
            .map((k) => `${JSON.stringify(k)}:${stable(o[k])}`)
            .join(',')}}`;
    }
    return JSON.stringify(value ?? null);
}

/** 바로가기 한 항목 읽기 — 모르는 종류는 null(뺀다). 이름은 12자로 자른다 */
function readLink(raw: unknown): HomeQuickLink | null {
    const it = obj(raw);
    const kind = QUICK_KINDS.find((k) => k === it.kind);
    if (!kind) return null;
    const label = typeof it.label === 'string' ? it.label.slice(0, QUICK_LABEL_MAX) : '';
    const value = kind === 'BOARD' || kind === 'PATH' ? (typeof it.value === 'string' ? it.value : '') : null;
    return { label, kind, value };
}

/**
 * 가젯 내용 설정(관리자 실제 화면 편집의 ⚙ 설정 창 「내용」)의 순수 계산 — 고칠 수 있는 종류, 종류마다 칸·범위·처음 값, 읽기·바로잡기·검사·비교.
 * 공구(개수·정렬, 2026-10-29) · 글 목록 · 갤러리 · 게시판 카드(대상 게시판·개수·정렬, 가젯 페이지 때 넓힘 2026-10-29) ·
 * 8종 전부 + 글 목록 보기 모양 · 게시판 카드 열 수 · 하이라이트 · 소모임 · 바로가기 · 안내 글(2026-10-30 — 서버 `GadgetConfigs`·`GadgetPatches` 와 같은 칸·범위).
 * 값 범위는 user-web `UtilHomeGadget`(공개 화면이 읽는 쪽)에 맞췄다. 대상 게시판 `FOLDER` 는 가젯 페이지에서만, `BOARD`(지금 보는 게시판)는 게시판 레이아웃에서만 — 홈에서는 `ALL`·`PICK`.
 */
export default class UtilGadgetConfig {
    /** 직접 고를 수 있는 게시판 수 한도 */
    static readonly BOARD_PICK_MAX = BOARD_PICK_MAX;
    static readonly ROW_STYLES = ROW_STYLES;
    static readonly THUMBNAILS = THUMBNAILS;
    static readonly META = META;
    /** 글 목록 열 수 · 제목 줄 수(0 = 줄 모양을 따름) · 큰 글 */
    static readonly POST_COLUMNS: readonly number[] = [1, 2];
    static readonly TITLE_LINES: readonly (0 | 1 | 2)[] = [0, 1, 2];
    static readonly POST_LEADS: readonly ('NONE' | 'HERO')[] = ['NONE', 'HERO'];
    /** NEW 배지(최근 N시간, 0 끔) · HOT 배지(추천 N 이상, 0 끔) 범위 */
    static readonly NEW_BADGE = { min: 0, max: 72 } as const;
    static readonly HOT_BADGE = { min: 0, max: 1000 } as const;
    /** 게시판 카드 열 수 */
    static readonly CARD_COLUMNS: readonly (number | 'AUTO')[] = ['AUTO', 2, 3];
    /** 하이라이트 큰 글 · 사진 */
    static readonly HIGHLIGHT_LEADS: readonly ('HERO' | 'NONE')[] = ['HERO', 'NONE'];
    static readonly HIGHLIGHT_THUMBS: readonly ('ALL' | 'FIRST' | 'NONE')[] = ['ALL', 'FIRST', 'NONE'];
    /** 바로가기 모양 · 종류 · 한도 */
    static readonly QUICK_SHAPES: readonly ('PILL' | 'TAB')[] = ['PILL', 'TAB'];
    static readonly QUICK_KINDS = QUICK_KINDS;
    static readonly QUICK_LINK_MAX = QUICK_LINK_MAX;
    static readonly QUICK_LABEL_MAX = QUICK_LABEL_MAX;
    /** 안내 글 한도 · 모양 */
    static readonly NOTICE_MAX = NOTICE_MAX;
    static readonly NOTICE_FORMATS: readonly ('PLAIN' | 'BULLETS')[] = ['PLAIN', 'BULLETS'];

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
     * 그 자리에서 고를 수 있는 대상 게시판 범위(보일 차례 — 첫 값이 처음 값)
     * @param {GadgetPlace} place - 가젯이 놓인 자리.
     * @returns {HomeBoardScope[]} 범위.
     */
    static scopes(place: GadgetPlace): HomeBoardScope[] {
        if (place === 'PAGE') return ['FOLDER', 'PICK', 'ALL'];
        if (place === 'BOARD') return ['BOARD', 'PICK', 'ALL'];
        return ['ALL', 'PICK'];
    }

    /**
     * 처음 값 — 새로 넣은 가젯 · 설정이 없는 가젯. 대상 게시판은 가젯 페이지면 폴더 게시판 전체, 게시판 레이아웃이면 지금 보는 게시판, 홈이면 전체.
     * @param {string} type - 가젯 종류.
     * @param {GadgetPlace} place - 가젯이 놓인 자리.
     * @returns {HomeGadgetConfig | null} 설정(고칠 수 없는 종류는 null).
     */
    static initial(type: string, place: GadgetPlace): HomeGadgetConfig | null {
        const spec = SPECS[type];
        if (!spec) return null;
        const out: HomeGadgetConfig = spec.count ? { [spec.count.field]: spec.count.initial } : {};
        if (spec.sort) out.sort = spec.sort as HomeGadgetConfig['sort'];
        if (spec.boards) {
            out.boardScope = UtilGadgetConfig.scopes(place)[0]!;
            out.boardIds = [];
        }
        switch (type) {
            case 'POST_LIST':
                Object.assign(out, {
                    periodTabs: false,
                    rowStyle: 'HEADLINE',
                    columns: 1,
                    thumbnail: 'AUTO',
                    titleLines: 0,
                    lead: 'NONE',
                    meta: [...DEFAULT_META],
                    dividers: true,
                    newBadgeHours: 0,
                    hotBadgeLikes: 0,
                } satisfies HomeGadgetConfig);
                break;
            case 'BOARD_CARDS':
                Object.assign(out, { cardHead: true, columns: 'AUTO' } satisfies HomeGadgetConfig);
                break;
            case 'HIGHLIGHT':
                Object.assign(out, { lead: 'HERO', thumbs: 'ALL' } satisfies HomeGadgetConfig);
                break;
            case 'QUICK_LINKS':
                Object.assign(out, { shape: 'PILL', items: [] } satisfies HomeGadgetConfig);
                break;
            case 'NOTICE_TEXT':
                Object.assign(out, { text: '', format: 'PLAIN' } satisfies HomeGadgetConfig);
                break;
        }
        return out;
    }

    /**
     * 읽기·바로잡기 — 범위 밖 개수는 범위 안으로, 모르는 값은 처음 값으로, 고른 게시판이 없으면 그 자리의 처음 범위, 그 자리에서 못 고르는 범위(`FOLDER`·`BOARD`)는 `ALL`.
     * 인기 정렬이 아니면 기간 탭을 끈다. 바로가기는 모르는 종류의 항목을 빼고 12개까지, 안내 글은 300자까지.
     * @param {string} type - 가젯 종류.
     * @param {HomeGadgetConfig | null | undefined} config - 받은 설정.
     * @param {GadgetPlace} place - 가젯이 놓인 자리.
     * @returns {HomeGadgetConfig | null} 설정(고칠 수 없는 종류는 null).
     */
    static normalize(type: string, config: HomeGadgetConfig | Record<string, unknown> | null | undefined, place: GadgetPlace): HomeGadgetConfig | null {
        const spec = SPECS[type];
        const initial = UtilGadgetConfig.initial(type, place);
        if (!spec || !initial) return null;
        const c = obj(config);
        const out: HomeGadgetConfig = spec.count ? { [spec.count.field]: clamp(c[spec.count.field], spec.count.min, spec.count.max, spec.count.initial) } : {};
        if (spec.sort) out.sort = (spec.sorts.includes(String(c.sort)) ? c.sort : spec.sort) as HomeGadgetConfig['sort'];
        if (spec.boards) {
            const ids = Array.isArray(c.boardIds) ? [...new Set(c.boardIds.filter((s): s is string => typeof s === 'string' && s.trim() !== ''))].slice(0, BOARD_PICK_MAX) : [];
            const known: readonly HomeBoardScope[] = ['ALL', 'FOLDER', 'PICK', 'BOARD'];
            let scope: HomeBoardScope = known.find((k) => k === c.boardScope) ?? (ids.length ? 'PICK' : (initial.boardScope ?? 'ALL'));
            if (!UtilGadgetConfig.scopes(place).includes(scope)) scope = 'ALL';
            if (scope === 'PICK' && !ids.length) scope = initial.boardScope ?? 'ALL';
            out.boardScope = scope;
            out.boardIds = scope === 'PICK' ? ids : [];
        }
        switch (type) {
            case 'POST_LIST':
                out.periodTabs = out.sort === 'POPULAR' && c.periodTabs === true;
                out.rowStyle = oneOf(c.rowStyle, ROW_STYLES, 'HEADLINE');
                out.columns = c.columns === 2 || c.columns === '2' ? 2 : 1;
                out.thumbnail = oneOf(c.thumbnail, THUMBNAILS, 'AUTO');
                out.titleLines = c.titleLines === 1 || c.titleLines === 2 ? c.titleLines : 0;
                out.lead = c.lead === 'HERO' ? 'HERO' : 'NONE';
                out.meta = Array.isArray(c.meta) ? META.filter((k) => (c.meta as unknown[]).includes(k)) : [...DEFAULT_META];
                out.dividers = typeof c.dividers === 'boolean' ? c.dividers : true;
                out.newBadgeHours = clamp(c.newBadgeHours, UtilGadgetConfig.NEW_BADGE.min, UtilGadgetConfig.NEW_BADGE.max, 0);
                out.hotBadgeLikes = clamp(c.hotBadgeLikes, UtilGadgetConfig.HOT_BADGE.min, UtilGadgetConfig.HOT_BADGE.max, 0);
                break;
            case 'BOARD_CARDS':
                out.cardHead = c.cardHead !== false;
                out.columns = c.columns === 2 || c.columns === '2' ? 2 : c.columns === 3 || c.columns === '3' ? 3 : 'AUTO';
                break;
            case 'HIGHLIGHT':
                out.lead = c.lead === 'NONE' ? 'NONE' : 'HERO';
                out.thumbs = oneOf(c.thumbs, UtilGadgetConfig.HIGHLIGHT_THUMBS, 'ALL');
                break;
            case 'QUICK_LINKS':
                out.shape = oneOf(c.shape, UtilGadgetConfig.QUICK_SHAPES, 'PILL');
                out.items = (Array.isArray(c.items) ? c.items : [])
                    .map(readLink)
                    .filter((it): it is HomeQuickLink => it !== null)
                    .slice(0, QUICK_LINK_MAX);
                break;
            case 'NOTICE_TEXT':
                out.text = typeof c.text === 'string' ? c.text.slice(0, NOTICE_MAX) : '';
                out.format = oneOf(c.format, UtilGadgetConfig.NOTICE_FORMATS, 'PLAIN');
                break;
        }
        return out;
    }

    /**
     * 저장할 모양으로 — 바로잡고, 바로가기 이름·값과 안내 글의 앞뒤 공백을 뗀다
     * @param {string} type - 가젯 종류.
     * @param {HomeGadgetConfig} config - 편집 중 설정.
     * @param {GadgetPlace} place - 가젯이 놓인 자리.
     * @returns {HomeGadgetConfig | null} 설정.
     */
    static finish(type: string, config: HomeGadgetConfig, place: GadgetPlace): HomeGadgetConfig | null {
        const out = UtilGadgetConfig.normalize(type, config, place);
        if (!out) return null;
        if (out.items) out.items = out.items.map((it) => ({ ...it, label: it.label.trim(), value: it.value === null ? null : it.value.trim() }));
        if (typeof out.text === 'string') out.text = out.text.trim();
        return out;
    }

    /**
     * 이 사이트 안 경로인가 — `/` 로 시작하고 `//`·스킴·공백·제어 문자·역슬래시가 없고 300자 이하(user-web `safePath` 와 같다)
     * @param {string} value - 경로.
     * @returns {boolean} 쓸 수 있으면 true.
     */
    static isSitePath(value: string): boolean {
        if (!value.startsWith('/') || value.startsWith('//') || value.length > 300) return false;
        // eslint-disable-next-line no-control-regex
        return !/[\s\\\u0000-\u001f]/.test(value) && !/^\/[a-zA-Z][a-zA-Z0-9+.-]*:/.test(value);
    }

    /**
     * 적용 전에 막을 문제 — 바로가기 항목의 이름 없음 · 게시판 안 고름 · 사이트 안 경로가 아님(공개 화면은 그런 항목을 그리지 않는다)
     * @param {string} type - 가젯 종류.
     * @param {HomeGadgetConfig | null} config - 편집 중 설정.
     * @returns {GadgetConfigProblem[]} 문제(없으면 빈 배열).
     */
    static problems(type: string, config: HomeGadgetConfig | null): GadgetConfigProblem[] {
        if (type !== 'QUICK_LINKS' || !config?.items) return [];
        const out: GadgetConfigProblem[] = [];
        config.items.forEach((it, index) => {
            if (!it.label.trim()) out.push({ index, field: 'label', key: 'quickLabelRequired' });
            const value = (it.value ?? '').trim();
            if (it.kind === 'BOARD' && !SLUG.test(value)) out.push({ index, field: 'value', key: 'quickBoardRequired' });
            if (it.kind === 'PATH' && !UtilGadgetConfig.isSitePath(value)) out.push({ index, field: 'value', key: 'quickPathInvalid' });
        });
        return out;
    }

    /**
     * 바뀜 비교용 글자 — 칸 차례에 상관없이 같은 설정이면 같은 글자
     * @param {HomeGadgetConfig | null | undefined} config - 설정.
     * @returns {string} 비교 글자.
     */
    static key(config: HomeGadgetConfig | null | undefined): string {
        return config ? stable(config) : '';
    }

    /**
     * 가젯 하나의 바뀜 비교용 글자 — 제목 · PC/모바일 노출 · 꾸미기 · 내용 설정
     * @param {HomeLayoutGadget} gadget - 가젯.
     * @returns {string} 비교 글자.
     */
    static gadgetKey(gadget: HomeLayoutGadget): string {
        return JSON.stringify([gadget.title, gadget.desktop, gadget.mobile, UtilGadgetStyle.key(gadget.style, gadget.type), UtilGadgetConfig.key(gadget.config)]);
    }

    /**
     * 가젯 사본 — 꾸미기·설정 안의 배열·객체까지 새로 만든다(반응형 감싸기를 벗긴 평범한 값)
     * @param {HomeLayoutGadget} gadget - 가젯.
     * @returns {HomeLayoutGadget} 사본.
     */
    static copy(gadget: HomeLayoutGadget): HomeLayoutGadget {
        return JSON.parse(JSON.stringify(gadget)) as HomeLayoutGadget;
    }
}
