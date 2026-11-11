import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';
import type { GroupBuyListItem } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { BoardCardsData, GadgetStyle, GroupBuyData, Gadget, GadgetsResponse } from '~/composables/gadget/api/useGadgetApi.types';

/** 공구 가젯 정렬 — 마감 임박 순(기본) · 새로 시작한 순 · 참여 많은 순(사용자 결정 2026-10-29) */
export type AdminPreviewGroupBuySort = 'DEADLINE' | 'NEWEST' | 'POPULAR';

/**
 * 가젯 내용 설정(관리자 ⚙ 설정 창 — admin-web `UtilGadgetConfig`) — 서버 JSON 그대로의 평평한 모양, 종류마다 쓰는 칸만(보낸 칸만 덮는다 — 없는 칸은 공개 응답 그대로):
 * 공구 `count`·`sort` · 글 목록 `boardScope`·`boardIds`·`count`·`sort`·`periodTabs` + 보기 모양(`rowStyle`·`columns`·`thumbnail`·`titleLines`·`lead`·`meta`·`dividers`·`newBadgeHours`·`hotBadgeLikes`) ·
 * 갤러리 `boardScope`·`boardIds`·`count` · 게시판 카드 `boardScope`·`boardIds`·`perBoard`·`cardHead`·`columns`·`outline`·`dividers`·`leadPlaceholder` · 하이라이트 `count`·`lead`·`thumbs`·`showAuthor` · 소모임 `count` ·
 * 바로가기 `shape`·`items` · 안내 글 `text`·`format`(2026-10-30 — 8종 전부) · 광고 배너 `adType` · 한 줄 광고 `count`.
 */
export interface AdminPreviewGadgetConfig {
    count?: number;
    sort?: string;
    /** `BOARD` = 지금 보는 게시판(게시판 레이아웃에서만, 2026-10-30) */
    boardScope?: 'ALL' | 'FOLDER' | 'PICK' | 'BOARD';
    boardIds?: string[];
    periodTabs?: boolean;
    perBoard?: number;
    cardHead?: boolean;
    outline?: boolean;
    leadPlaceholder?: boolean;
    rowStyle?: string;
    columns?: number | 'AUTO';
    thumbnail?: string;
    titleLines?: number;
    lead?: string;
    meta?: string[];
    dividers?: boolean;
    newBadgeHours?: number;
    hotBadgeLikes?: number;
    thumbs?: string;
    /** 하이라이트 — 작성자(아바타·닉네임) 보이기 */
    showAuthor?: boolean;
    shape?: string;
    items?: { label: string; kind: string; value: string | null }[];
    text?: string;
    format?: string;
    /** 마우스 올림 효과(항목이 있는 종류 공통 — `HOVER_KINDS`) */
    hover?: boolean;
    /** 가젯 페이지 제목 카드에 넣기(관리자 ⚙ 「노출」) */
    pageHead?: boolean;
    /** `AD` 배너 유형 */
    adType?: string;
}

/** 가젯 페이지 머리(관리자의 저장 전 값 — 가젯 페이지 미리보기일 때만) */
export interface AdminPreviewPage {
    name: string;
    description: string;
    writeButton: boolean;
}

/** 게시판 레이아웃 미리보기 값(관리자의 저장 전 값 — 게시판 목록·글 보기를 띄웠을 때만) — 적용 화면. 배치는 `home` 자리로 온다(본문 칸 `BOARD_BODY` 포함) */
export interface AdminPreviewBoard {
    screens: { list: boolean; post: boolean };
}

/**
 * 관리자 미리보기의 홈 구성 — 관리자 홈 구성 편집기의 저장 전 값(가젯은 id·종류·제목·보이는 폭 + 꾸미기 `style`(관리자가 고친 것만 — 없으면 공개 응답 그대로) + 내용 설정 `config`).
 * `style` 은 받은 모양 그대로 두고, 그릴 때 `UtilGadget.sanitize` 가 공개 응답과 같은 규칙으로 읽는다.
 */
export interface AdminPreviewHome {
    layout: HomeLayout;
    gadgets: {
        id: string;
        type: string;
        title: string;
        desktop: boolean;
        mobile: boolean;
        style?: Record<string, unknown> | null;
        config?: AdminPreviewGadgetConfig | null;
        /** 편집기의 종류별 최소 높이(행) — 「n칸 줄여도」가 그 아래로 권하지 않게 */
        minH?: number;
    }[];
}

/** 관리자가 보내는 미리보기 값 — 메뉴 칸은 운영자 설정 모양 그대로(`THEME` 또는 값, 읽기는 `UtilMenuPosition` 이 한다) */
export interface AdminPreviewMessage {
    theme: string;
    menu: {
        menuPosition: unknown;
        topItems: unknown;
        barGroupIds: unknown;
        menuWidth: unknown;
        menuOverflow: unknown;
        menuStyle: Record<string, unknown>;
        /** 메뉴 막대 글자 기본 굵게(켬·끔) — 불리언이 아니면 덮지 않는다 */
        menuBold?: unknown;
    } | null;
    home: AdminPreviewHome | null;
    /** 홈 구성 편집 중인가 — 격자에 손잡이를 얹는다 */
    editing: boolean;
    /** 가젯 페이지 머리(가젯 페이지 `/p/{key}` 를 띄웠을 때) — 없으면 null */
    page: AdminPreviewPage | null;
    /** 게시판 레이아웃의 적용 화면(게시판 목록·글 보기를 띄웠을 때) — 없으면 null */
    board: AdminPreviewBoard | null;
}

/** 편집 중 격자가 관리자에게 알리는 일 — 옮기기(놓은 칸) · 크기(칸·행) · 빼기 · 설정 열기(⚙) */
export type AdminPreviewLayoutOp =
    | { op: 'place'; id: string; x: number; y: number }
    | { op: 'size'; id: string; w: number; h: number }
    | { op: 'remove'; id: string }
    | { op: 'settings'; id: string };

/** 관리자에서 설정(⚙)을 고칠 수 있는 가젯 종류 — 8종 전부(2026-10-30) + 광고 2종. admin-web `UtilGadgetConfig` 의 종류와 같다 */
const SETTABLE_KINDS: readonly string[] = ['HIGHLIGHT', 'POST_LIST', 'BOARD_CARDS', 'GALLERY', 'GROUPS', 'GROUP_BUY', 'QUICK_LINKS', 'NOTICE_TEXT', 'AD', 'AD_TEXT_LINE'];
/** 마우스 올림 효과 칸(`hover`)이 있는 종류 — 서버 `GadgetConfigs.HOVER_KINDS`·`UtilGadget.hoverOn` 과 같다(핫딜은 ⚙ 미리보기 종류에 없어도 이 칸은 따른다) */
const HOVER_KINDS: readonly string[] = ['HIGHLIGHT', 'POST_LIST', 'BOARD_CARDS', 'GALLERY', 'GROUPS', 'GROUP_BUY', 'HOT_DEALS', 'QUICK_LINKS'];
/** 광고 가젯 — 후보는 페이지가 채우므로 설정이 없어도 새 가젯을 그린다 */
const AD_KINDS: readonly string[] = ['AD', 'AD_TEXT_LINE'];
const AD_BANNER_TYPES: readonly string[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER'];
const GROUP_BUY_SORTS: readonly string[] = ['DEADLINE', 'NEWEST', 'POPULAR'];
const POST_LIST_SORTS: readonly string[] = ['LATEST', 'POPULAR', 'VIEWS', 'LIKES', 'COMMENTS'];
const ROW_STYLES: readonly string[] = ['HEADLINE', 'RANK', 'ARTICLE', 'COMPACT'];
const THUMBNAILS: readonly string[] = ['AUTO', 'NONE', 'RIGHT', 'LEFT'];
const META_KEYS: readonly string[] = ['BOARD', 'PREFIX', 'COMMENTS', 'TIME', 'AUTHOR'];
/** 글 목록 보기 모양 칸 — 받은 것만 가젯 `config` 에 덮는다 */
const POST_VIEW_KEYS = ['rowStyle', 'columns', 'thumbnail', 'titleLines', 'lead', 'meta', 'dividers', 'newBadgeHours', 'hotBadgeLikes'] as const;
/** 데이터를 받지 않고 설정으로 바로 그리는 종류 — 같은 종류가 응답에 없어도 새 가젯을 그린다 */
const DIRECT_KINDS: readonly string[] = ['QUICK_LINKS', 'NOTICE_TEXT'];
/** 게시판 레이아웃의 고정 칸 「게시판 본문」 — 그 자리에 게시판 목록(·글 보기) 화면이 그려진다(admin-web `UtilBoardLayout.BODY` 와 같은 값). 빼기·⚙ 가 없다 */
export const BOARD_BODY = 'BOARD_BODY';

/** 값이 정해진(undefined 가 아닌) 칸만 */
function defined(value: AdminPreviewGadgetConfig): AdminPreviewGadgetConfig {
    return Object.fromEntries(Object.entries(value).filter(([, v]) => v !== undefined)) as AdminPreviewGadgetConfig;
}

function oneOf(value: unknown, list: readonly string[]): string | undefined {
    return typeof value === 'string' && list.includes(value) ? value : undefined;
}

/** 관리자 → 공개 사이트 메시지 종류 */
export const ADMIN_PREVIEW_APPLY = 'roboyam:admin-preview';
/** 공개 사이트 → 관리자 메시지 종류 — 준비됨 · 메뉴 자리를 눌렀다 · 홈 격자 편집(`AdminPreviewLayoutOp`) */
export const ADMIN_PREVIEW_READY = 'roboyam:admin-preview-ready';
export const ADMIN_PREVIEW_EDIT_MENU = 'roboyam:admin-preview-edit-menu';
export const ADMIN_PREVIEW_LAYOUT = 'roboyam:admin-preview-layout';

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * 관리자 미리보기(관리자 테마 화면의 iframe, 사용자 결정 2026-10-28 — 미리보기는 실제 공개 화면과 똑같아야 한다) 메시지 읽기와 홈 구성 덮기.
 * 받는 쪽은 `plugins/03.admin-preview.client.ts`, 보내는 쪽은 admin-web `components/theme/ThemeLivePreview.vue`.
 */
export default class UtilAdminPreview {
    /**
     * 이 창이 관리자 미리보기로 열렸는가 — iframe 안이고 주소에 `adminPreview=1` 이 있다(관리자 `ThemeLivePreview` 가 붙인다).
     * 미리보기 켜기(`plugins/03.admin-preview.client.ts`)와 방문 신호 빼기(`plugins/04.analytics.client.ts`)가 같은 판정을 쓴다 — 앱이 뜰 때의 주소로 한 번 본다.
     * @param {boolean} inFrame - iframe 안인가(`window.parent !== window`).
     * @param {string} search - 처음 주소의 쿼리(`window.location.search`).
     * @returns {boolean} 미리보기 창이면 true.
     */
    static isPreviewWindow(inFrame: boolean, search: string): boolean {
        return inFrame && new URLSearchParams(search).get('adminPreview') === '1';
    }

    /**
     * 받은 메시지를 읽는다 — 모양이 아니면 null. 값의 뜻(테마 id·메뉴 칸 값) 검사는 쓰는 곳이 기존 읽기 함수로 한다.
     * @param {unknown} data - `MessageEvent.data`.
     * @returns {AdminPreviewMessage | null} 읽은 값.
     */
    static read(data: unknown): AdminPreviewMessage | null {
        if (!isRecord(data) || data.type !== ADMIN_PREVIEW_APPLY || typeof data.theme !== 'string') return null;
        const menu = isRecord(data.menu) && isRecord(data.menu.menuStyle) ? (data.menu as AdminPreviewMessage['menu']) : null;
        return { theme: data.theme, menu, home: UtilAdminPreview.readHome(data.home), editing: data.editing === true, page: UtilAdminPreview.readPage(data.page), board: UtilAdminPreview.readBoard(data.board) };
    }

    /**
     * 게시판 레이아웃 적용 화면 읽기 — 모양이 맞을 때만(칸이 참·거짓이 아니면 켬).
     * @param {unknown} value - 메시지의 `board`.
     * @returns {AdminPreviewBoard | null} 적용 화면.
     */
    static readBoard(value: unknown): AdminPreviewBoard | null {
        if (!isRecord(value) || !isRecord(value.screens)) return null;
        return { screens: { list: value.screens.list !== false, post: value.screens.post !== false } };
    }

    /**
     * 편집 손잡이에 × 빼기를 다는가 — 고정 칸(게시판 본문)은 뺄 수 없다.
     * @param {string} type - 가젯 종류.
     * @returns {boolean} 뺄 수 있으면 true.
     */
    static removable(type: string): boolean {
        return type !== BOARD_BODY;
    }

    /**
     * 가젯 페이지 머리 읽기 — 이름·설명·글쓰기 단추가 모양이 맞을 때만.
     * @param {unknown} value - 메시지의 `page`.
     * @returns {AdminPreviewPage | null} 머리.
     */
    static readPage(value: unknown): AdminPreviewPage | null {
        if (!isRecord(value) || typeof value.name !== 'string' || typeof value.description !== 'string' || typeof value.writeButton !== 'boolean') return null;
        return { name: value.name, description: value.description, writeButton: value.writeButton };
    }

    /**
     * 홈 구성 읽기 — 칸 수·자리 목록·가젯 목록이 모양이 맞을 때만(자리 범위·겹침은 화면의 `UtilHomeLayout.read` 가 바로잡는다).
     * @param {unknown} value - 메시지의 `home`.
     * @returns {AdminPreviewHome | null} 홈 구성.
     */
    static readHome(value: unknown): AdminPreviewHome | null {
        if (!isRecord(value) || !isRecord(value.layout) || !Array.isArray(value.layout.items) || !Array.isArray(value.gadgets)) return null;
        const gadgets = value.gadgets
            .filter(
                (g): g is AdminPreviewHome['gadgets'][number] =>
                    isRecord(g) && typeof g.id === 'string' && typeof g.type === 'string' && typeof g.title === 'string' && typeof g.desktop === 'boolean' && typeof g.mobile === 'boolean',
            )
            .map((g) => ({
                ...g,
                style: isRecord(g.style) ? g.style : null,
                config: UtilAdminPreview.readConfig(g.type, g.config),
                minH: typeof g.minH === 'number' && Number.isInteger(g.minH) && g.minH > 0 ? g.minH : undefined,
            }));
        return { layout: value.layout as unknown as HomeLayout, gadgets };
    }

    /**
     * 가젯 내용 설정 읽기 — 종류마다 아는 칸만, 범위 안으로(값 범위는 `UtilGadget` 이 읽는 것과 같다). 받지 않은 칸은 빼서 공개 응답 값이 남게 한다:
     * 공구 개수 1~10·정렬 3종 · 글 목록 개수 1~30·정렬 5종·보기 모양 · 갤러리 1~24 · 게시판 카드 글 수 1~10·열 · 하이라이트 1~6·큰 글·사진 · 소모임 1~6 · 바로가기 모양·항목 12개 · 안내 글 300자·모양.
     * 항목이 있는 종류(`HOVER_KINDS` — 핫딜 포함)는 공통 마우스 올림 효과 `hover` 도 읽는다.
     * @param {string} type - 가젯 종류.
     * @param {unknown} value - 가젯의 `config`.
     * @returns {AdminPreviewGadgetConfig | null} 설정(모르는 종류·모양이 아니면 null).
     */
    static readConfig(type: string, value: unknown): AdminPreviewGadgetConfig | null {
        if (!isRecord(value)) return null;
        const out = SETTABLE_KINDS.includes(type) ? UtilAdminPreview.readKindConfig(type, value) : HOVER_KINDS.includes(type) ? {} : null;
        if (out && HOVER_KINDS.includes(type) && typeof value.hover === 'boolean') out.hover = value.hover;
        if (out && HOVER_KINDS.includes(type) && typeof value.pageHead === 'boolean') out.pageHead = value.pageHead;
        return out;
    }

    /**
     * 종류만의 내용 설정 읽기(`readConfig` 가 부른다)
     * @param {string} type - 가젯 종류(`SETTABLE_KINDS` 중 하나).
     * @param {Record<string, unknown>} value - 가젯의 `config`.
     * @returns {AdminPreviewGadgetConfig | null} 설정.
     */
    private static readKindConfig(type: string, value: Record<string, unknown>): AdminPreviewGadgetConfig | null {
        const int = (v: unknown, min: number, max: number): number | undefined => (Number.isInteger(v) ? Math.min(max, Math.max(min, Number(v))) : undefined);
        const bool = (v: unknown): boolean | undefined => (typeof v === 'boolean' ? v : undefined);
        const boardIds = Array.isArray(value.boardIds) ? value.boardIds.filter((s): s is string => typeof s === 'string') : undefined;
        const boardScope = value.boardScope === 'ALL' || value.boardScope === 'FOLDER' || value.boardScope === 'PICK' || value.boardScope === 'BOARD' ? value.boardScope : undefined;
        switch (type) {
            case 'GROUP_BUY': {
                const count = int(value.count, 1, 10);
                if (count === undefined || !GROUP_BUY_SORTS.includes(String(value.sort))) return null;
                return { count, sort: String(value.sort) };
            }
            case 'POST_LIST':
                return defined({
                    count: int(value.count, 1, 30),
                    sort: oneOf(value.sort, POST_LIST_SORTS),
                    periodTabs: value.periodTabs === true,
                    boardScope,
                    boardIds,
                    rowStyle: oneOf(value.rowStyle, ROW_STYLES),
                    columns: value.columns === 1 || value.columns === 2 ? value.columns : undefined,
                    thumbnail: oneOf(value.thumbnail, THUMBNAILS),
                    titleLines: value.titleLines === 0 || value.titleLines === 1 || value.titleLines === 2 ? value.titleLines : undefined,
                    lead: oneOf(value.lead, ['NONE', 'HERO']),
                    meta: Array.isArray(value.meta) ? META_KEYS.filter((k) => (value.meta as unknown[]).includes(k)) : undefined,
                    dividers: bool(value.dividers),
                    newBadgeHours: int(value.newBadgeHours, 0, 72),
                    hotBadgeLikes: int(value.hotBadgeLikes, 0, 1000),
                });
            case 'GALLERY':
                return defined({ count: int(value.count, 1, 24), boardScope, boardIds });
            case 'BOARD_CARDS':
                return defined({
                    perBoard: int(value.perBoard, 1, 10),
                    boardScope,
                    boardIds,
                    cardHead: value.cardHead !== false,
                    outline: value.outline === true,
                    dividers: value.dividers === true,
                    leadPlaceholder: value.leadPlaceholder !== false,
                    columns: value.columns === 'AUTO' || value.columns === 2 || value.columns === 3 ? value.columns : undefined,
                });
            case 'HIGHLIGHT':
                return defined({ count: int(value.count, 1, 6), lead: oneOf(value.lead, ['NONE', 'HERO']), thumbs: oneOf(value.thumbs, ['ALL', 'FIRST', 'NONE']), showAuthor: bool(value.showAuthor) });
            case 'GROUPS':
                return defined({ count: int(value.count, 1, 6) });
            case 'AD':
                return defined({ adType: oneOf(value.adType, AD_BANNER_TYPES) });
            case 'AD_TEXT_LINE':
                return defined({ count: int(value.count, 1, 5) });
            case 'QUICK_LINKS':
                return defined({
                    shape: oneOf(value.shape, ['PILL', 'TAB']),
                    items: Array.isArray(value.items)
                        ? value.items
                              .filter(isRecord)
                              .map((it) => ({ label: typeof it.label === 'string' ? it.label : '', kind: typeof it.kind === 'string' ? it.kind : '', value: typeof it.value === 'string' ? it.value : null }))
                              .slice(0, 12)
                        : undefined,
                });
            default:
                return defined({ text: typeof value.text === 'string' ? value.text.slice(0, 300) : undefined, format: oneOf(value.format, ['PLAIN', 'BULLETS']) });
        }
    }

    /**
     * 관리자에서 내용 설정(⚙)을 고칠 수 있는 종류인가 — 편집 손잡이에 ⚙ 단추를 단다.
     * @param {string} type - 가젯 종류.
     * @returns {boolean} 고칠 수 있으면 true.
     */
    static settable(type: string): boolean {
        return SETTABLE_KINDS.includes(type);
    }

    /**
     * 공구 가젯 줄을 저장 전 설정대로 — 받은 줄 안에서 정렬을 다시 하고 개수만큼 자른다(서버가 준 줄보다 늘리지는 못한다 — 저장하면 서버가 그 설정으로 다시 고른다).
     * @param {GroupBuyData} data - 가젯 데이터.
     * @param {AdminPreviewGadgetConfig} config - 설정.
     * @returns {GroupBuyData} 고친 데이터.
     */
    static groupBuy(data: GroupBuyData, config: AdminPreviewGadgetConfig): GroupBuyData {
        const deadline = (it: GroupBuyListItem) => Date.parse(it.extendedUntil ?? it.endAt);
        const order: Record<AdminPreviewGroupBuySort, (a: GroupBuyListItem, b: GroupBuyListItem) => number> = {
            DEADLINE: (a, b) => deadline(a) - deadline(b),
            NEWEST: (a, b) => Date.parse(b.startAt) - Date.parse(a.startAt),
            POPULAR: (a, b) => b.currentQty - a.currentQty || deadline(a) - deadline(b),
        };
        const sort = (GROUP_BUY_SORTS.includes(String(config.sort)) ? config.sort : 'DEADLINE') as AdminPreviewGroupBuySort;
        return { ...data, items: [...data.items].sort(order[sort]).slice(0, config.count ?? 5) };
    }

    /**
     * 저장 전 내용 설정을 받은 데이터 안에서 따른다(서버가 준 줄보다 늘리거나 다른 게시판 글을 가져오지는 못한다 — 저장하면 서버가 그 설정으로 다시 고른다):
     *   · 공구 — 정렬·개수(`groupBuy`)
     *   · 글 목록 — 정렬·기간 탭은 설정 값으로(탭을 끄면 기간 글을 뺀다), 개수만큼 자른다
     *   · 갤러리 — 개수만큼 자른다
     *   · 게시판 카드 — 직접 고른 게시판이면 그 게시판 카드만(고른 차례), 카드마다 글 수만큼 자른다, 카드 머리 보이기·열 수는 설정 값으로
     *   · 하이라이트 — 개수만큼 자른다, 큰 글·사진은 설정 값으로 / 소모임 — 개수는 설정 값으로(받은 줄 안에서)
     *   · 바로가기·안내 글 — 데이터를 받지 않고 설정으로 바로 만든다(항목·글을 화면이 다시 읽는다 — `UtilGadget.quickLinkItems`·`noticeText`)
     *   · 글 목록 보기 모양 — 받은 칸을 설정에 덮는다(그리기는 `UtilGadget.postList` 가 읽는다)
     * @param {Gadget} gadget - 가젯(사본 — 고친다).
     * @param {AdminPreviewGadgetConfig} config - 설정.
     */
    static applyConfig(gadget: Gadget, config: AdminPreviewGadgetConfig): void {
        // 마우스 올림 효과 — 종류에 상관없이 받은 값을 덮는다(그리기는 껍데기 `GadgetFrame` 이 `UtilGadget.hoverOn` 으로 읽는다)
        if (config.hover !== undefined) gadget.config = { ...gadget.config, hover: config.hover };
        if (AD_KINDS.includes(gadget.type)) {
            gadget.config = { ...gadget.config, ...defined({ adType: config.adType, count: config.count }) };
            return;
        }
        if (gadget.type === 'QUICK_LINKS') {
            if (config.shape) gadget.config = { ...gadget.config, shape: config.shape };
            if (config.items) {
                gadget.data = { items: config.items };
                // 비었는지는 가젯이 항목으로 판정한다(서버의 `empty` 는 저장 전 항목을 모른다)
                gadget.empty = false;
            }
            return;
        }
        if (gadget.type === 'NOTICE_TEXT') {
            if (config.format) gadget.config = { ...gadget.config, format: config.format };
            if (config.text !== undefined) {
                gadget.data = { text: config.text, format: config.format ?? (isRecord(gadget.data) ? gadget.data.format : undefined) ?? 'PLAIN' };
                gadget.empty = false;
            }
            return;
        }
        const data = isRecord(gadget.data) ? gadget.data : null;
        if (gadget.type === 'POST_LIST') {
            const view = Object.fromEntries(POST_VIEW_KEYS.filter((k) => config[k] !== undefined).map((k) => [k, config[k]]));
            gadget.config = { ...gadget.config, ...view };
        } else if (gadget.type === 'BOARD_CARDS' && config.columns !== undefined) {
            gadget.config = { ...gadget.config, columns: config.columns };
        } else if (gadget.type === 'HIGHLIGHT') {
            gadget.config = { ...gadget.config, ...defined({ lead: config.lead, thumbs: config.thumbs, showAuthor: config.showAuthor }) };
            if (config.count && data && Array.isArray(data.picks)) data.picks = data.picks.slice(0, config.count);
            return;
        } else if (gadget.type === 'GROUPS') {
            if (config.count) gadget.config = { ...gadget.config, count: config.count };
            return;
        }
        if (!data) return;
        if (gadget.type === 'GROUP_BUY' && Array.isArray(data.items)) {
            gadget.data = UtilAdminPreview.groupBuy(data as unknown as GroupBuyData, config);
        } else if (gadget.type === 'POST_LIST') {
            if (config.sort) gadget.config = { ...gadget.config, sort: config.sort, periodTabs: config.periodTabs === true };
            const count = config.count;
            if (count && Array.isArray(data.items)) data.items = data.items.slice(0, count);
            if (!config.periodTabs) delete data.periods;
            else if (count && isRecord(data.periods)) for (const key of Object.keys(data.periods)) if (Array.isArray(data.periods[key])) data.periods[key] = (data.periods[key] as unknown[]).slice(0, count);
        } else if (gadget.type === 'GALLERY') {
            if (config.count && Array.isArray(data.items)) data.items = data.items.slice(0, config.count);
        } else if (gadget.type === 'BOARD_CARDS' && Array.isArray(data.cards)) {
            const cards = (data as unknown as BoardCardsData).cards;
            const picked = config.boardScope === 'PICK' && config.boardIds?.length ? config.boardIds.map((slug) => cards.find((c) => c.board.slug === slug)).filter((c) => c !== undefined) : cards;
            data.cards = picked.map((c) => ({ ...c, items: config.perBoard ? c.items.slice(0, config.perBoard) : c.items }));
            gadget.config = { ...gadget.config, cardHead: config.cardHead !== false, outline: config.outline === true, dividers: config.dividers === true, leadPlaceholder: config.leadPlaceholder !== false };
        }
    }

    /**
     * 새로 넣은 가젯(받은 응답에 같은 ID 가 없다)의 그릴 내용 — 같은 종류 중 설정이 가장 비슷한 것(글 목록은 같은 정렬, 게시판 카드는 고른 게시판이 든 것), 없으면 같은 종류의 첫 가젯.
     * @param {Gadget[]} pool - 받은 가젯들.
     * @param {AdminPreviewHome['gadgets'][number]} p - 관리자의 가젯.
     * @returns {Gadget | undefined} 바탕 가젯.
     */
    static baseOf(pool: readonly Gadget[], p: AdminPreviewHome['gadgets'][number]): Gadget | undefined {
        const same = pool.filter((g) => g.type === p.type);
        const c = p.config;
        if (c && p.type === 'POST_LIST' && c.sort) {
            const hit = same.find((g) => g.config.sort === c.sort && (c.sort !== 'POPULAR' || Boolean(g.config.periodTabs) === Boolean(c.periodTabs))) ?? same.find((g) => g.config.sort === c.sort);
            if (hit) return hit;
        }
        if (c && p.type === 'BOARD_CARDS' && c.boardScope === 'PICK' && c.boardIds?.length) {
            const hit = same.find((g) => isRecord(g.data) && Array.isArray(g.data.cards) && (g.data as unknown as BoardCardsData).cards.some((card) => c.boardIds?.includes(card.board.slug)));
            if (hit) return hit;
        }
        return same[0];
    }

    /**
     * 빈 가젯 — 설정으로 바로 그리는 종류(바로가기·안내 글)를 응답에 같은 종류가 없을 때 새로 그리는 바탕. 꾸미기는 빈 값(읽을 때 처음 값)
     * @param {string} type - 가젯 종류.
     * @returns {Gadget} 가젯.
     */
    static blank(type: string): Gadget {
        return { id: '', type, title: '', desktop: true, mobile: true, empty: false, locked: false, style: {} as GadgetStyle, config: {}, more: null, data: null };
    }

    /**
     * 받은 홈(·가젯 페이지) 가젯 응답에 관리자의 구성을 덮는다 — 배치는 관리자 것, 가젯은 같은 id 의 가젯(새로 넣은 가젯은 같은 종류 중 설정이 비슷한 것 — `baseOf`, 꾸미기는 빼고)을 관리자 제목·보이는 폭으로.
     * 저장 전 내용 설정(`config`)은 받은 데이터 안에서 따른다(`applyConfig`). 꾸미기(`style`)는 관리자가 고친 것이 있으면 통째로 바꾼다(읽기는 그 뒤 `UtilGadget.sanitize`).
     * `extra` 는 바탕으로만 쓰는 가젯(가젯 페이지 미리보기에서 홈 가젯 — 페이지에 없는 종류를 넣었을 때).
     * 응답에 같은 종류가 없는 새 가젯은 그릴 내용이 없어 뺀다 — 바로가기·안내 글은 설정으로 바로 그리므로 빈 가젯에서 만든다. 게시판 본문(`BOARD_BODY`)·광고 가젯(`AD`·`AD_TEXT_LINE`)도 빈 가젯에서 만든다(내용은 화면이 채운다).
     * @param {GadgetsResponse} res - 가젯 응답.
     * @param {AdminPreviewHome} home - 관리자 구성.
     * @param {readonly Gadget[]} [extra] - 바탕으로 더 쓸 가젯.
     * @returns {GadgetsResponse} 덮은 응답.
     */
    static applyHome(res: GadgetsResponse, home: AdminPreviewHome, extra: readonly Gadget[] = []): GadgetsResponse {
        const byId = new Map(res.gadgets.map((g) => [g.id, g]));
        const pool = [...res.gadgets, ...extra];
        const gadgets = home.gadgets
            .map((p): Gadget | null => {
                const base =
                    byId.get(p.id) ??
                    (p.type === BOARD_BODY ? UtilAdminPreview.blank(p.type) : undefined) ??
                    UtilAdminPreview.baseOf(pool, p) ??
                    ((DIRECT_KINDS.includes(p.type) && p.config) || AD_KINDS.includes(p.type) ? UtilAdminPreview.blank(p.type) : undefined);
                if (!base) return null;
                const gadget: Gadget = { ...(JSON.parse(JSON.stringify(base)) as Gadget), id: p.id, title: p.title, desktop: p.desktop, mobile: p.mobile };
                // 새 가젯은 바탕 가젯의 꾸미기(예: 비었을 때 숨김)를 물려받지 않고 그 종류의 처음 꾸미기로 그린다 — 저장하면 처음 꾸미기가 되므로
                if (p.style) gadget.style = JSON.parse(JSON.stringify(p.style)) as GadgetStyle;
                else if (!byId.has(p.id)) gadget.style = {} as GadgetStyle;
                if (p.config) UtilAdminPreview.applyConfig(gadget, p.config);
                return gadget;
            })
            .filter((g): g is Gadget => g !== null);
        return { ...res, layout: home.layout, gadgets };
    }
}
