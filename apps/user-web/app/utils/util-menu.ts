import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BoardKind, BoardView } from '~/composables/board/api/useBoardApi.types';
import type { MenuBoardRef, MenuDivider, MenuGroupNode, MenuItemNode, MenuNode, MenuResponse } from '~/composables/menu/api/useMenuApi.types';
import UtilBoard from '~/utils/util-board';
import type { ColorStyle } from '~/utils/util-board';
import UtilColor from '~/utils/util-color';
import type { MenuBarScope, TopItemsPosition } from '~/utils/util-menu-position';

/**
 * 메뉴 규칙 — 햄버거(모바일)·왼쪽/오른쪽 사이드 메뉴(세로)·상단 메뉴(가로)·모바일 상단 탭·`/boards` 모음이 **같은 트리·같은 판정**을 쓰도록 한 곳에 둔다.
 *
 * 트리의 출처는 서버(`GET /api/menu`)다. 서버가 보는 사람 기준으로 거르고 정렬까지 끝내 주므로 여기서는 다시 거르거나 정렬하지 않는다(`docs/api-menu.md` 5절).
 * 서버 메뉴를 못 받으면 화면 코드가 따로 만든 기본 메뉴를 그리지 않는다 — 안내와 다시 시도만 둔다(2026-11-07 사용자 결정: 보이는 규칙은 서버 한 곳에만).
 * 세로·가로는 이 트리를 어떤 모양으로 그리는지만 다르다(`AppMenuList` · `AppTopMenu`). 상태도 요청도 없다(상태는 `useMenu`·`useMenuItems`).
 */

/**
 * 메뉴 자리가 어떤 모양인가
 * - `loading` 받는 중(줄 모양 스켈레톤) · `ready` 서버 메뉴 · `empty` 서버가 빈 메뉴를 줬다 ·
 * - `error` 서버 메뉴를 못 받았다(메뉴 줄 대신 안내 + 다시 시도)
 */
export type MenuViewState = 'loading' | 'ready' | 'empty' | 'error';

/** 메뉴 서버 요청의 진행 — `idle` 아직 안 물어봄 · `loading` 첫 응답 전 · `ready` 받았다 · `error` 못 받았고 가진 메뉴도 없다 */
export type MenuPhase = 'idle' | 'loading' | 'ready' | 'error';

/** 줄을 만들 때 필요한 바깥 사정 — 지금 있는 곳·접힘 기억 */
export interface MenuContext {
    /** 지금 있는 곳으로 밝힐 항목의 ID(게시판이면 진짜 자리 — 바로가기가 아닌 항목). 없으면 null */
    activeId: string | null;
    /** 지금 있는 곳 항목이 게시판이면 그 slug — 다른 묶음에 놓인 그 게시판의 **바로가기** 줄도 밝힌다(`MenuItemRow.active`). 아니면 null */
    activeSlug: string | null;
    /** 사용자가 접거나 편 기억(노드 ID → 접힘). 기억이 없는 그룹은 노드의 `collapsed` 기본값을 쓴다 */
    memory: Readonly<Record<string, boolean>>;
}

/** 메뉴 항목 한 줄 */
export interface MenuItemRow {
    key: string;
    node: MenuItemNode;
    /** 들여쓰기 단계 — 최상위 그룹의 직속 항목은 0, 하위 그룹 안은 그 그룹의 단계 */
    level: number;
    /**
     * 밝게 그리는가(`aria-current="page"`) — 지금 있는 곳 항목(`here`), 또는 지금 보는 게시판의 **바로가기**(다른 묶음에도 보이게 둔 같은 게시판 줄).
     * 예: 핫 로토픽 묶음을 열어 둔 채 커뮤니티 소속 게시판을 보면 핫 로토픽 안의 그 바로가기 줄도 밝다.
     */
    active: boolean;
    /**
     * 진짜 지금 있는 곳(`activeId`)인가 — 「지금 있는 곳이 든 묶음」(사이드 그룹 고르기·막대의 활성 그룹·밑줄)은 이것으로 정한다.
     * 바로가기 줄은 밝아도(`active`) 여기는 false 라, 게시판의 진짜 자리 묶음(예: 커뮤니티)이 지금 있는 곳 묶음이 된다.
     */
    here: boolean;
    /** 투자 고지가 붙는 게시판인가 */
    notice: boolean;
    /** 운영자가 고른 표시색(12색 프리셋 이름) 또는 null(색 없음) — 있을 때만 이 색의 점을 그린다(자동 색 점은 없다) */
    color: string | null;
}

/**
 * 제목(소제목) 하나와 그 아래 직속 항목들 — 하위 그룹을 평평하게 이어 놓은 덩어리다.
 * `group` 이 null 이면 소제목 없이 이어지는 항목들이다(그룹 앞이나 하위 그룹 뒤에 남은 직속 항목).
 */
export interface MenuBlock {
    key: string;
    level: number;
    group: MenuGroupNode | null;
    /** 접을 수 있는 그룹인가(상단 가로 메뉴에서는 늘 false) */
    collapsible: boolean;
    collapsed: boolean;
    /** 그 그룹 아래 항목 수(하위 그룹 안 포함) — 접혔을 때 제목 옆에 보인다 */
    count: number;
    items: MenuItemRow[];
}

/** 세로 메뉴·모음의 구역 하나 — 제목 있는 최상위 그룹, 또는 제목 없이 이어지는 최상위 항목들 */
export interface MenuSection {
    key: string;
    group: MenuGroupNode | null;
    title: string | null;
    /** 제목의 영문명(`group.nameEn`) — 제목이 없거나 영문명이 없으면 null. 화면은 `localizedName(title, titleEn)` 으로 그린다 */
    titleEn: string | null;
    collapsible: boolean;
    collapsed: boolean;
    count: number;
    blocks: MenuBlock[];
}

/** 상단 가로 막대의 칸 하나 — 링크이거나, 눌렀을 때 펼치는 하위 그룹(드롭다운) */
export type MenuBarEntry =
    | { kind: 'item'; key: string; row: MenuItemRow }
    | { kind: 'dropdown'; key: string; group: MenuGroupNode; /** 드롭다운 안에 지금 있는 곳이 있나 */ active: boolean; blocks: MenuBlock[] };

/** 상단 가로 막대의 구역 하나 — 제목 + 직속 칸들(왼쪽에 구분선) */
export interface MenuBarSection {
    key: string;
    /** 제목이 된 최상위 그룹(제목 이름을 누르면 가는 곳 `path` 가 여기 있다) — 제목 없는 구역이면 null */
    group: MenuGroupNode | null;
    title: string | null;
    /** 제목의 영문명 — 없으면 null */
    titleEn: string | null;
    entries: MenuBarEntry[];
}

/**
 * 메뉴를 그리는 자리(범위) — 같은 트리를 나눠 그린다(`UtilMenu.split`).
 * `all` 전체(햄버거·모바일) · `bar` 상단 막대 · `side` 사이드 메뉴 · `logo` 로고 옆 작은 링크 줄
 */
export type MenuScope = 'all' | 'bar' | 'side' | 'logo';

/** 데스크톱 메뉴 배치 — 최상위 항목 위치 + 상단 막대 범위(`UtilMenuPosition.barScope`) */
export interface MenuLayout {
    topItems: TopItemsPosition;
    bar: MenuBarScope;
}

/** 나눈 결과 — 각 자리가 그릴 노드(원래 트리의 노드를 그대로 쓴다) */
export interface MenuSplit {
    /** 로고 옆 작은 링크 줄 — 최상위 항목(`topItems = LOGO` 일 때만) */
    logo: MenuNode[];
    /** 상단 막대 */
    bar: MenuNode[];
    /** 사이드 메뉴 */
    side: MenuNode[];
}

/** 글쓰기의 게시판 고르기 목록의 묶음 하나 — 제목(메뉴 그룹 이름)과 그 아래 게시판들 */
export interface BoardPickGroup {
    /** 목록 키 — 메뉴 그룹이면 그 노드 ID, 메뉴에 없는 게시판 묶음이면 `unplaced` 이다 */
    key: string;
    label: string;
    boards: BoardView[];
}

/** 접힘 기억 — 노드 ID → 접힘 */
export type MenuCollapseMemory = Record<string, boolean>;

/** 트리 깊이를 이 이상 읽지 않는다 — 서버 한도(그룹 3단계·끝 항목 4단계)보다 넉넉히 잡은 안전망 */
const MAX_READ_DEPTH = 8;

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 주소가 우리가 그릴 수 있는 모양인가 — 사이트 안 경로(`/…`, `//` 아님) 또는 `http(s)://` */
function isUsablePath(path: string): boolean {
    return (path.startsWith('/') && !path.startsWith('//')) || /^https?:\/\//i.test(path);
}

function readBoardRef(raw: unknown): MenuBoardRef | null {
    if (!isRecord(raw) || typeof raw.slug !== 'string' || raw.slug === '') return null;
    return {
        id: typeof raw.id === 'string' ? raw.id : '',
        slug: raw.slug,
        color: typeof raw.color === 'string' ? raw.color : null,
        investmentNotice: raw.investmentNotice === true,
    };
}

/** 서버가 준 영문명(`nameEn`) — 글자가 아니거나 공백뿐이면 null(그때는 기본 이름 `label` 을 쓴다) */
function readNameEn(raw: Record<string, unknown>): string | null {
    return typeof raw.nameEn === 'string' && raw.nameEn.trim() !== '' ? raw.nameEn.trim() : null;
}

/** 서버가 준 구분선 — 모르는 값·없음이면 null(긋지 않는다) */
function readDivider(raw: Record<string, unknown>): MenuDivider | null {
    return raw.divider === 'BEFORE' || raw.divider === 'AFTER' ? raw.divider : null;
}

/** 배지 글자 한도(코드포인트) — 서버 `menu_nodes.badge` 와 같다 */
const BADGE_MAX = 4;

/** 서버가 준 배지 — 앞뒤 공백을 걷고 비면 null, 한도를 넘으면 잘라 읽는다(서버가 이미 막지만 줄이 깨지지 않게) */
function readBadge(raw: unknown): string | null {
    if (typeof raw !== 'string') return null;
    const text = Array.from(raw.trim()).slice(0, BADGE_MAX).join('');
    return text === '' ? null : text;
}

/** 개발 미리보기 `?divider=이름:before|after,…` 를 이 탭에 기억하는 저장 키 */
const DIVIDER_PREVIEW_KEY = 'roboyam:menu-divider-preview';

/** `이름:before|after` 쉼표 목록 → 이름 → 구분선. 읽을 수 없으면 null */
function readDividerPreview(text: string | null): Map<string, MenuDivider> | null {
    if (!text) return null;
    const map = new Map<string, MenuDivider>();
    for (const part of text.split(',')) {
        const [name, side] = part.split(':').map((s) => s.trim());
        if (!name) continue;
        const value = side?.toLowerCase() === 'after' ? 'AFTER' : side?.toLowerCase() === 'before' ? 'BEFORE' : null;
        if (value) map.set(name, value);
    }
    return map.size ? map : null;
}

/** 개발 미리보기 `?groupLink=이름:/주소,…` 를 이 탭에 기억하는 저장 키 */
const GROUP_LINK_PREVIEW_KEY = 'roboyam:menu-group-link-preview';

/** `이름:/주소` 쉼표 목록(주소 끝 `!` = 이동만) → 이름 → 주소(사이트 안 주소만). 읽을 수 없으면 null */
function readGroupLinkPreview(text: string | null): Map<string, string> | null {
    if (!text) return null;
    const map = new Map<string, string>();
    for (const part of text.split(',')) {
        const at = part.indexOf(':');
        if (at < 1) continue;
        const name = part.slice(0, at).trim();
        const path = part.slice(at + 1).trim();
        if (name && path.startsWith('/') && !path.startsWith('//')) map.set(name, path);
    }
    return map.size ? map : null;
}

function readNode(raw: unknown, depth: number): MenuNode | null {
    if (!isRecord(raw) || typeof raw.id !== 'string' || raw.id === '' || typeof raw.label !== 'string' || raw.label === '') return null;
    if (raw.kind === 'GROUP') {
        if (depth >= MAX_READ_DEPTH) return null;
        const children = Array.isArray(raw.children) ? readNodes(raw.children, depth + 1) : [];
        // 보이는 항목이 하나도 없는 그룹은 뺀다 — 서버도 같은 규칙으로 빼지만, 읽다가 항목이 빠진 그룹이 제목만 남지 않게 한다
        if (children.length === 0) return null;
        const collapsible = raw.collapsible === true;
        // 이름을 누르면 가는 곳 — 그릴 수 없는 주소면 연결 없는 그룹으로 읽는다
        const path = typeof raw.path === 'string' && isUsablePath(raw.path) ? raw.path : null;
        return {
            id: raw.id,
            kind: 'GROUP',
            label: raw.label,
            nameEn: readNameEn(raw),
            path,
            newTab: path !== null && raw.newTab === true,
            linkClick: raw.linkClick === 'NAVIGATE' ? 'NAVIGATE' : 'NAVIGATE_OPEN',
            extra: path !== null && isRecord(raw.extra) ? raw.extra : null,
            collapsible,
            collapsed: collapsible && raw.collapsed === true,
            divider: readDivider(raw),
            textColor: UtilColor.normalize(raw.textColor),
            bold: raw.bold === true,
            badge: readBadge(raw.badge),
            children,
        };
    }
    if (raw.kind === 'ITEM') {
        if (typeof raw.path !== 'string' || !isUsablePath(raw.path)) return null;
        return {
            id: raw.id,
            kind: 'ITEM',
            type: typeof raw.type === 'string' ? raw.type : '',
            key: typeof raw.key === 'string' ? raw.key : null,
            label: raw.label,
            nameEn: readNameEn(raw),
            path: raw.path,
            matchPrefix: raw.matchPrefix === true,
            newTab: raw.newTab === true,
            mobileTab: raw.mobileTab === true,
            shortcut: raw.shortcut === true,
            board: readBoardRef(raw.board),
            extra: isRecord(raw.extra) ? raw.extra : null,
            divider: readDivider(raw),
            description: typeof raw.description === 'string' && raw.description.trim() !== '' ? raw.description.trim() : null,
            textColor: UtilColor.normalize(raw.textColor),
            bold: raw.bold === true,
            badge: readBadge(raw.badge),
        };
    }
    return null;
}

function readNodes(raw: readonly unknown[], depth: number): MenuNode[] {
    const out: MenuNode[] = [];
    for (const entry of raw) {
        const node = readNode(entry, depth);
        if (node) out.push(node);
    }
    return out;
}

/** 이 그룹 아래 항목(끝 노드) 수 — 하위 그룹 안까지 센다 */
function countItems(group: MenuGroupNode): number {
    let count = 0;
    for (const child of group.children) count += child.kind === 'ITEM' ? 1 : countItems(child);
    return count;
}

function containsId(group: MenuGroupNode, id: string | null): boolean {
    if (id === null) return false;
    return group.children.some((child) => (child.kind === 'ITEM' ? child.id === id : containsId(child, id)));
}

/** 게시판 항목이면 true */
function isBoardItem(node: MenuItemNode): boolean {
    return node.type === 'BOARD' && !!node.board;
}

function toRow(node: MenuItemNode, level: number, ctx: MenuContext): MenuItemRow {
    // 색 점은 운영자가 고른 표시색이 있을 때만 단다 — 서버 `color` 가 13색 이름·`#RRGGBB` 가 아니면(비었거나 모르는 값) 점이 없다
    const color = UtilBoard.boardColor(node.board?.color);
    const here = node.id === ctx.activeId;
    // 바로가기 줄 — 지금 보는 게시판이면 함께 밝게(진짜 자리는 아니다)
    const echo = !here && node.shortcut === true && ctx.activeSlug !== null && isBoardItem(node) && node.board!.slug === ctx.activeSlug;
    return { key: node.id, node, level, active: here || echo, here, notice: node.board?.investmentNotice === true, color };
}

/** 한 부모 아래 자식들을 소제목 덩어리로 이어 놓는다 — 접힌 그룹 아래는 항목을 내려 주지 않는다 */
function collect(children: readonly MenuNode[], level: number, start: MenuBlock | null, ctx: MenuContext, honorCollapse: boolean, out: MenuBlock[]): void {
    let current = start;
    for (const node of children) {
        if (node.kind === 'ITEM') {
            if (!current) {
                current = { key: `items-${node.id}`, level, group: null, collapsible: false, collapsed: false, count: 0, items: [] };
                out.push(current);
            }
            current.items.push(toRow(node, level, ctx));
            continue;
        }
        const collapsible = honorCollapse && node.collapsible;
        const collapsed = collapsible && UtilMenu.isCollapsed(node, ctx.memory);
        const head: MenuBlock = { key: node.id, level: level + 1, group: node, collapsible, collapsed, count: countItems(node), items: [] };
        out.push(head);
        if (!collapsed) collect(node.children, level + 1, head, ctx, honorCollapse, out);
        current = null;
    }
}

/** 연속된 최상위 항목들을 구역 하나로 묶고, 최상위 그룹은 구역 하나씩으로 나눈다 */
function splitTop(nodes: readonly MenuNode[]): ({ run: MenuItemNode[] } | { group: MenuGroupNode })[] {
    const out: ({ run: MenuItemNode[] } | { group: MenuGroupNode })[] = [];
    let run: MenuItemNode[] = [];
    for (const node of nodes) {
        if (node.kind === 'ITEM') {
            run.push(node);
            continue;
        }
        if (run.length) out.push({ run });
        run = [];
        out.push({ group: node });
    }
    if (run.length) out.push({ run });
    return out;
}

function pruneToBoards(nodes: readonly MenuNode[]): MenuNode[] {
    const out: MenuNode[] = [];
    for (const node of nodes) {
        if (node.kind === 'ITEM') {
            // 바로가기는 뺀다 — 게시판 모음은 게시판의 진짜 자리(소속 묶음)에만 한 번 보인다
            if (isBoardItem(node) && node.shortcut !== true) out.push(node);
            continue;
        }
        const children = pruneToBoards(node.children);
        if (children.length) out.push({ ...node, children });
    }
    return out;
}

function findPath(nodes: readonly MenuNode[], id: string, trail: string[]): string[] | null {
    for (const node of nodes) {
        if (node.kind === 'ITEM') {
            if (node.id === id) return trail;
            continue;
        }
        const found = findPath(node.children, id, [...trail, node.id]);
        if (found) return found;
    }
    return null;
}

function normalizePath(path: string): string {
    return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path;
}

/** 공지 게시판의 고정 코드 — 서버 `SiteNoticeBars.NOTICE_BOARD_SLUG` 와 같다(바로 가기 확성기 아이콘) */
const NOTICE_BOARD_SLUG = 'notice';

/** 회원장터 게시판 코드(설치 프리셋의 `market`) — 서버에 따로 표시가 없어 코드로 알아본다. 운영자가 코드를 바꾸면 목록 아이콘으로 돌아간다(바로 가기 가게 아이콘) */
const MARKET_BOARD_SLUG = 'market';

/** 바로 가기 칸의 앱 화면별 아이콘(`BUILTIN` 화면 키 — 서버 `MenuBuiltin`). 없는 키는 묶음별 기본(`UtilMenu.itemIcon`) */
const BUILTIN_ICONS: Readonly<Record<string, string>> = {
    HOME: 'pi pi-home',
    BOARDS_HUB: 'pi pi-th-large',
    // 인기글은 하단 탭과 같은 불꽃 — PrimeIcons 에 없어 Tabler 선(`tabler:` 로 시작하면 그리는 쪽이 `TablerIcon` 을 쓴다)
    POPULAR: 'tabler:flame',
    LATEST: 'pi pi-clock',
    SEARCH: 'pi pi-search',
    GROUP_BUYS: 'pi pi-shopping-bag',
    GROUP_BUY_ORDERS: 'pi pi-receipt',
    MY_GRADE: 'pi pi-star',
    MY_POINTS: 'pi pi-wallet',
    MY_SHOP: 'pi pi-shopping-bag',
    MESSAGES: 'pi pi-envelope',
    NOTIFICATIONS: 'pi pi-bell',
};

export default class UtilMenu {
    /** 접힘 기억을 이 브라우저에 남기는 저장 키 — 계정에 저장하지 않는다(보기 설정과 분리, docs/api-menu.md 4-3) */
    static readonly COLLAPSE_STORAGE_KEY = 'roboyam:menu-collapsed';

    /**
     * 지금 주소가 공동구매인가 — 목록(`/group-buys`)·상세·주문서(`/group-buys/…`)가 같은 메뉴 항목을 밝힌다. 내 주문(`/me/orders`)은 내 정보 쪽이라 밝히지 않는다.
     * @param {string} path - 지금 주소의 경로.
     * @returns {boolean} 공동구매 화면이면 true.
     */
    static onGroupBuys(path: string): boolean {
        return path === '/group-buys' || path.startsWith('/group-buys/');
    }

    /**
     * 지금 주소가 "내 소모임"인가
     * @param {string} path - 지금 주소의 경로.
     * @returns {boolean} `/groups/me` 이면 true.
     */
    static onMyGroups(path: string): boolean {
        return path === '/groups/me';
    }

    /**
     * 지금 주소가 "전체 소모임"쪽인가 — `/groups/me` 는 내 소모임이고 그 밖의 `/groups…`(목록·상세·만들기·관리)는 전체 소모임이 밝다
     * @param {string} path - 지금 주소의 경로.
     * @returns {boolean} 전체 소모임이 밝아야 하면 true.
     */
    static onAllGroups(path: string): boolean {
        return (path === '/groups' || path.startsWith('/groups/')) && !UtilMenu.onMyGroups(path);
    }

    /**
     * 가로로 미는 줄에서 한 항목을 가운데에 놓으려면 줄을 얼마나 밀어야 하나.
     * @param {number} itemLeft - 항목의 왼쪽 위치(줄 안, `offsetLeft`).
     * @param {number} itemWidth - 항목의 너비(`offsetWidth`).
     * @param {number} boxWidth - 줄의 보이는 너비(`clientWidth`).
     * @returns {number} `scrollLeft` 에 넣을 값(음수가 나오면 브라우저가 0 으로 자른다).
     */
    static centerScrollLeft(itemLeft: number, itemWidth: number, boxWidth: number): number {
        return itemLeft - (boxWidth - itemWidth) / 2;
    }

    /**
     * 개발 모드 전용 구분선 미리보기 — 주소 `?divider=공동구매:before,커뮤니티:after`(최상위 노드 이름)를 이 탭에 기억하고 최상위 노드에 입힌다. 서버가 `divider` 를 주면 서버 값이 이긴다.
     * 운영 빌드(`import.meta.dev` 거짓)에서는 그대로 돌려준다. 저장소가 막혀 있으면 미리보기 없이.
     * @param {MenuNode[]} nodes - 읽은 메뉴 트리.
     * @returns {MenuNode[]} 구분선을 입힌 트리(최상위만).
     */
    static withDividerPreview(nodes: MenuNode[]): MenuNode[] {
        if (!import.meta.dev) return nodes;
        let map: Map<string, MenuDivider> | null = null;
        try {
            const fromUrl = new URLSearchParams(window.location.search).get('divider');
            if (fromUrl !== null) window.sessionStorage.setItem(DIVIDER_PREVIEW_KEY, fromUrl);
            map = readDividerPreview(fromUrl ?? window.sessionStorage.getItem(DIVIDER_PREVIEW_KEY));
        } catch {
            return nodes;
        }
        if (!map) return nodes;
        const preview = map;
        return nodes.map((node) => (node.divider || !preview.has(node.label) ? node : { ...node, divider: preview.get(node.label)! }));
    }

    /**
     * 개발 모드 전용 그룹 연결 미리보기(서버 칸이 생기기 전 화면 확인용) — 주소 `?groupLink=소모임:/groups,주제:/boards!`(그룹 이름:주소, 끝 `!` 는 「이동만」)를 이 탭에 기억하고 그 이름의 그룹(어느 단계든)에 `path` 를 입힌다.
     * 서버가 `path` 를 주면 서버 값이 이긴다. 운영 빌드에서는 그대로 돌려준다. 저장소가 막혀 있으면 미리보기 없이.
     * @param {MenuNode[]} nodes - 읽은 메뉴 트리.
     * @returns {MenuNode[]} 연결을 입힌 트리.
     */
    static withGroupLinkPreview(nodes: MenuNode[]): MenuNode[] {
        if (!import.meta.dev) return nodes;
        let map: Map<string, string> | null = null;
        try {
            const fromUrl = new URLSearchParams(window.location.search).get('groupLink');
            if (fromUrl !== null) window.sessionStorage.setItem(GROUP_LINK_PREVIEW_KEY, fromUrl);
            map = readGroupLinkPreview(fromUrl ?? window.sessionStorage.getItem(GROUP_LINK_PREVIEW_KEY));
        } catch {
            return nodes;
        }
        if (!map) return nodes;
        const preview = map;
        const walk = (list: MenuNode[]): MenuNode[] =>
            list.map((node) => {
                if (node.kind !== 'GROUP') return node;
                if (node.path || !preview.has(node.label)) return { ...node, children: walk(node.children) };
                const raw = preview.get(node.label)!;
                const only = raw.endsWith('!');
                return { ...node, path: only ? raw.slice(0, -1) : raw, linkClick: only ? 'NAVIGATE' : 'NAVIGATE_OPEN', children: walk(node.children) };
            });
        return walk(nodes);
    }

    /**
     * 상단 가로 메뉴 칸 옆 구분선 클래스 — 칸(`li`)을 flex 로 두고 `before:`/`after:` 로 세로 선(높이 16, 머리카락 선 색)을 긋는다. 구분선이 없으면 빈 글자.
     * @param {MenuDivider | null | undefined} divider - 노드의 구분선.
     * @returns {string} 클래스 문자열.
     */
    static dividerClass(divider: MenuDivider | null | undefined): string {
        if (divider === 'BEFORE') return "flex items-center before:mx-1.5 before:h-4 before:w-px before:bg-hairline-strong before:content-['']";
        if (divider === 'AFTER') return "flex items-center after:mx-1.5 after:h-4 after:w-px after:bg-hairline-strong after:content-['']";
        return '';
    }

    /**
     * 서버 응답을 읽는다 — 모양이 깨졌으면 null(받지 못한 것으로 보고 기본 메뉴로 그린다). 모르는 칸은 버리고, 그릴 수 없는 노드(주소·글자가 없다)는 뺀다.
     * 서버가 이미 거르고 정렬해 준 것이라 거르거나 정렬하지는 않는다. 읽다가 항목이 다 빠진 그룹만 뺀다.
     * @param {unknown} raw - `GET /api/menu` 응답.
     * @returns {MenuResponse | null} 읽은 메뉴. `nodes` 가 배열이 아니면 null.
     */
    static parse(raw: unknown): MenuResponse | null {
        if (!isRecord(raw) || !Array.isArray(raw.nodes)) return null;
        const nodes = readNodes(raw.nodes, 1);
        const mobileTabs = Array.isArray(raw.mobileTabs) ? raw.mobileTabs.filter((id): id is string => typeof id === 'string') : [];
        return { nodes, mobileTabs, version: typeof raw.version === 'string' ? raw.version : undefined };
    }

    /**
     * 트리의 모든 항목(끝 노드)을 위에서 아래 순서로 편다.
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @returns {MenuItemNode[]} 깊이 우선 순서의 항목들.
     */
    static items(nodes: readonly MenuNode[]): MenuItemNode[] {
        const out: MenuItemNode[] = [];
        for (const node of nodes) {
            if (node.kind === 'ITEM') out.push(node);
            else out.push(...UtilMenu.items(node.children));
        }
        return out;
    }

    /**
     * 전체 화면 메뉴(`/menu`)의 「바로 가기」 칸에 놓을 항목 — 메뉴 트리의 최상위 항목(그룹에 들지 않은 끝 노드) 전부, 서버 순서 그대로(mobile-nav-prefix-style-contract 4절 — 운영자 메뉴 구성 그대로, 따로 고르는 설정 없음).
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @returns {MenuItemNode[]} 최상위 항목들.
     */
    static topItems(nodes: readonly MenuNode[]): MenuItemNode[] {
        return nodes.filter((node): node is MenuItemNode => node.kind === 'ITEM');
    }

    /**
     * 바로 가기 칸의 기본 아이콘 — 공개 메뉴 항목에는 아이콘 칸이 없어 연결 유형으로 고른다: 앱 화면(`BUILTIN`)은 화면 키별, 게시판은 글 목록, 외부 링크는 링크, 그 밖은 칸 아이콘.
     * 게시판 중 공지 게시판(서버 고정 slug `notice` — `SiteNoticeBars.NOTICE_BOARD_SLUG`)은 확성기, 핫딜 게시판(종류 `DEAL`)은 할인 배지, 회원장터(코드 `market`)는 가게(모두 Tabler — 사용자 결정 2026-11-12).
     * @param {Pick<MenuItemNode, 'type' | 'key' | 'board'>} node - 메뉴 항목.
     * @param {BoardKind | undefined} boardKind - 게시판 항목이면 그 게시판 종류(게시판 목록 `useBoards`에서). 모르면 비운다.
     * @returns {string} PrimeIcons 클래스(`pi pi-…`) 또는 `tabler:<이름>`(`TablerIcon` 으로 그린다).
     */
    static itemIcon(node: Pick<MenuItemNode, 'type' | 'key' | 'board'>, boardKind?: BoardKind): string {
        if (node.type === 'BUILTIN' && node.key) {
            const key = node.key;
            if (key in BUILTIN_ICONS) return BUILTIN_ICONS[key]!;
            if (key.startsWith('GROUPS')) return 'pi pi-users';
            if (key.startsWith('SETTINGS')) return 'pi pi-cog';
            if (key.startsWith('MY_')) return 'pi pi-user';
        }
        if (node.type === 'BOARD') {
            if (node.board?.slug === NOTICE_BOARD_SLUG) return 'tabler:speakerphone';
            if (boardKind === 'DEAL') return 'tabler:rosette-discount';
            if (node.board?.slug === MARKET_BOARD_SLUG) return 'tabler:building-store';
            return 'pi pi-list';
        }
        if (node.type === 'URL') return 'pi pi-link';
        return 'pi pi-th-large';
    }

    /**
     * 모바일 상단 탭에 올릴 항목 — 서버가 준 ID 순서 그대로, 트리에서 찾지 못한 ID 는 건너뛴다(화면이 순서를 다시 계산하지 않는다).
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @param {readonly string[]} ids - `mobileTabs` — 탭 항목의 ID 배열.
     * @returns {MenuItemNode[]} 탭 항목들.
     */
    static mobileTabs(nodes: readonly MenuNode[], ids: readonly string[]): MenuItemNode[] {
        const byId = new Map(UtilMenu.items(nodes).map((item) => [item.id, item]));
        const out: MenuItemNode[] = [];
        for (const id of ids) {
            const item = byId.get(id);
            if (item) out.push(item);
        }
        return out;
    }

    /**
     * 이 항목의 주소가 사이트 밖(절대 `https://`·`http://`)인가 — 그러면 라우터 링크(`NuxtLink`)가 아니라 `<a href>` 로 그린다. 사이트 안 경로(`/…`)는 라우터 링크다.
     * @param {Pick<MenuItemNode, 'path'>} node - 메뉴 항목.
     * @returns {boolean} 절대 주소면 true.
     */
    static isAbsoluteLink(node: Pick<MenuItemNode, 'path'>): boolean {
        return /^https?:\/\//i.test(node.path);
    }

    /**
     * 작은 외부 링크 아이콘(`pi pi-external-link`)을 그릴까 — 서버가 `extra.external = true` 를 줬을 때만이다(주소만 보고 화면이 추측하지 않는다).
     * @param {Pick<MenuItemNode, 'extra'>} node - 메뉴 항목.
     * @returns {boolean} 그리면 true.
     */
    static isExternal(node: Pick<MenuItemNode, 'extra'>): boolean {
        return node.extra?.external === true;
    }

    /**
     * 새 탭으로 여는 링크에 붙일 속성 — 서버가 준 `newTab` 만 따른다. 새 탭이면 `target="_blank" rel="noopener noreferrer"`(`window.opener` 를 끊는다).
     * @param {Pick<MenuItemNode, 'newTab'>} node - 메뉴 항목.
     * @returns {{ target?: string; rel?: string }} 링크 속성. 새 탭이 아니면 빈 객체.
     */
    static linkAttrs(node: Pick<MenuItemNode, 'newTab'>): { target?: string; rel?: string } {
        return node.newTab ? { target: '_blank', rel: 'noopener noreferrer' } : {};
    }

    /**
     * 그룹의 「누르면 가는 곳」이 지금 주소인가 — 사이트 안 경로(`/…`)만 보고 끝의 `/` 는 무시한다(그룹 첫 화면 — 예: 커뮤니티 가젯 페이지)
     * @param {string | null | undefined} groupPath - 그룹의 `path`.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 같으면 true.
     */
    static isGroupHome(groupPath: string | null | undefined, path: string): boolean {
        return Boolean(groupPath?.startsWith('/')) && normalizePath(groupPath as string) === normalizePath(path);
    }

    /**
     * 지금 있는 곳으로 밝힐 항목 **하나** — 모든 항목 중 주소가 가장 길게 맞는 것이다(그래서 `/groups/me` 는 내 소모임만, `/groups/12` 는 전체 소모임만 밝다).
     * - `matchPrefix` 가 false 면 주소가 정확히 같을 때만, true 면 `path === p || path.startsWith(p + '/')` 일 때 맞는다(홈은 `/` 에서만).
     * - 게시판 항목은 지금 보는 게시판(`currentSlug`)이 그 게시판이면 주소와 상관없이 밝다 — 글 상세·글쓰기 화면에서도 그 게시판 줄이 켜지게 하려는 것이다.
     * - 같은 게시판이 진짜 자리와 바로가기(`shortcut`, 다른 묶음에도 보이기)로 두 번 있으면 진짜 자리다(바로가기 줄은 `MenuItemRow.active` 로만 함께 밝다).
     * - 맞는 항목이 없으면(메뉴에 없는 게시판·`/me/orders` 같은 메뉴 밖 화면) null.
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @param {string | null} currentSlug - 지금 보고 있는 게시판의 slug(`useCurrentBoard`). 없으면 null.
     * @returns {string | null} 밝힐 항목의 ID. 없으면 null.
     */
    static activeId(nodes: readonly MenuNode[], path: string, currentSlug: string | null): string | null {
        const here = normalizePath(path);
        let best: string | null = null;
        let bestScore = -1;
        for (const item of UtilMenu.items(nodes)) {
            let score = -1;
            // 바로가기(`shortcut`)는 같은 게시판의 진짜 자리보다 한 점 낮다 — 진짜 자리가 보이면 그쪽이 지금 있는 곳이고, 진짜 자리가 이 사람에게 안 보일 때만 바로가기가 맡는다
            const penalty = item.shortcut === true ? 1 : 0;
            if (currentSlug && isBoardItem(item) && item.board!.slug === currentSlug) {
                score = Number.MAX_SAFE_INTEGER - penalty;
            } else if (item.path.startsWith('/')) {
                const p = normalizePath(item.path);
                const hit = item.matchPrefix ? here === p || here.startsWith(p + '/') : here === p;
                if (hit) score = p.length * 2 - penalty;
            }
            // 같은 점수면 트리에서 먼저 나온 항목을 둔다(같은 주소를 두 항목이 가리키지 못하게 서버가 막지만 안전하게)
            if (score > bestScore) {
                best = item.id;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * 항목 위쪽 그룹들의 ID — 위에서 아래 순서. 접힌 그룹을 펼쳐야 할 때와 가로 막대의 드롭다운 표시에 쓴다.
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @param {string | null} id - 항목의 ID.
     * @returns {string[]} 조상 그룹 ID들. 못 찾았거나 최상위 항목이면 빈 배열.
     */
    static ancestorIds(nodes: readonly MenuNode[], id: string | null): string[] {
        if (id === null) return [];
        return findPath(nodes, id, []) ?? [];
    }

    /**
     * 그룹이 지금 접혀 있나 — 사용자가 접거나 편 기억이 먼저이고, 없으면 노드의 처음 상태(`collapsed`)다. 접을 수 없는 그룹은 늘 false.
     * @param {MenuGroupNode} group - 그룹 노드.
     * @param {Readonly<Record<string, boolean>>} memory - 접힘 기억.
     * @returns {boolean} 접혀 있으면 true.
     */
    static isCollapsed(group: MenuGroupNode, memory: Readonly<Record<string, boolean>>): boolean {
        if (!group.collapsible) return false;
        const own = memory[group.id];
        return typeof own === 'boolean' ? own : group.collapsed;
    }

    /**
     * 지금 있는 곳이 접힌 그룹 안이면 그 조상 그룹들을 펼친 기억을 만든다 — 접어 둔 채로는 "내가 어디 있는지"가 안 보인다(S-3 M-3).
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @param {string | null} activeId - 지금 있는 곳의 항목 ID.
     * @param {Readonly<Record<string, boolean>>} memory - 지금 접힘 기억.
     * @returns {MenuCollapseMemory | null} 바뀐 기억. 펼칠 그룹이 없으면 null(기억을 건드리지 않는다).
     */
    static expandForActive(nodes: readonly MenuNode[], activeId: string | null, memory: Readonly<Record<string, boolean>>): MenuCollapseMemory | null {
        if (activeId === null) return null;
        const groups = new Map<string, MenuGroupNode>();
        const index = (list: readonly MenuNode[]) => {
            for (const node of list) {
                if (node.kind === 'GROUP') {
                    groups.set(node.id, node);
                    index(node.children);
                }
            }
        };
        index(nodes);
        let next: MenuCollapseMemory | null = null;
        for (const groupId of UtilMenu.ancestorIds(nodes, activeId)) {
            const group = groups.get(groupId);
            if (group && UtilMenu.isCollapsed(group, memory)) next = { ...(next ?? memory), [groupId]: false };
        }
        return next;
    }

    /**
     * 데스크톱 메뉴 배치에 따라 트리를 로고 옆 · 상단 막대 · 사이드로 나눈다 — 모든 셸·메뉴 부품이 이 한 규칙을 쓴다(`useMenuItems(scope)`).
     * - 최상위 항목(그룹 밖에 놓인 항목 — 홈·인기글 …): `LOGO` 면 로고 옆에만, `MENU` 면 메뉴 안(상단 막대가 있으면 막대, 없으면 사이드).
     * - 최상위 그룹: `ALL` 이면 모두 막대, `NONE` 이면 모두 사이드, 그룹 ID 목록이면 고른 그룹은 막대·나머지는 사이드.
     *   고른 ID 가 비었거나 지금 트리에 없는 ID 뿐이면 **첫 최상위 그룹 하나**를 막대에 둔다.
     * - 막대는 **언제나 트리 차례 그대로**다(사용자 결정 2026-10-29 — 차례의 주인은 메뉴 관리. 섞인 배치에서도 항목을 앞에 모으지 않는다: 그룹 뒤에 둔 항목은 막대에서도 그룹 뒤).
     * 활성 판정은 나눈 뒤에도 전체 트리 기준이다(`useMenuItems` 가 전체 트리로 `activeId` 를 구한다).
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @param {MenuLayout} layout - 배치.
     * @returns {MenuSplit} 자리별 노드.
     */
    static split(nodes: readonly MenuNode[], layout: MenuLayout): MenuSplit {
        const items = nodes.filter((node) => node.kind === 'ITEM');
        const logo = layout.topItems === 'LOGO' ? items : [];
        const inMenu = layout.topItems === 'LOGO' ? nodes.filter((node) => node.kind === 'GROUP') : [...nodes];
        if (layout.bar === 'ALL') return { logo, bar: inMenu, side: [] };
        if (layout.bar === 'NONE') return { logo, bar: [], side: inMenu };

        const groups = nodes.filter((node): node is MenuGroupNode => node.kind === 'GROUP');
        const wanted = new Set(layout.bar);
        let barGroups = groups.filter((group) => wanted.has(group.id));
        if (barGroups.length === 0) barGroups = groups.slice(0, 1);
        const barIds = new Set(barGroups.map((group) => group.id));
        return {
            logo,
            bar: nodes.filter((node) => (node.kind === 'ITEM' ? layout.topItems === 'MENU' : barIds.has(node.id))),
            side: groups.filter((group) => !barIds.has(group.id)),
        };
    }

    /**
     * 트리를 세로 메뉴(왼쪽·오른쪽·햄버거)·게시판 모음의 구역으로 편다.
     * 연속된 최상위 항목은 제목 없는 한 구역, 최상위 그룹은 제목 있는 구역, 2단계 이하 그룹은 구역 안의 소제목(`MenuBlock.group`)이다.
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @param {MenuContext} ctx - 지금 있는 곳·접힘 기억.
     * @param {boolean} [honorCollapse] - 접힘을 따를지. 게시판 모음은 접지 않으므로 false.
     * @returns {MenuSection[]} 위에서 아래로 놓을 구역들. 접힌 그룹은 항목을 내려 주지 않는다.
     */
    static sections(nodes: readonly MenuNode[], ctx: MenuContext, honorCollapse = true): MenuSection[] {
        return splitTop(nodes).map((part, index) => {
            if ('run' in part) {
                const blocks: MenuBlock[] = [];
                collect(part.run, 0, null, ctx, honorCollapse, blocks);
                return { key: `top-${index}-${part.run[0]!.id}`, group: null, title: null, titleEn: null, collapsible: false, collapsed: false, count: 0, blocks };
            }
            const group = part.group;
            const collapsible = honorCollapse && group.collapsible;
            const collapsed = collapsible && UtilMenu.isCollapsed(group, ctx.memory);
            const blocks: MenuBlock[] = [];
            if (!collapsed) collect(group.children, 0, null, ctx, honorCollapse, blocks);
            return { key: group.id, group, title: group.label, titleEn: group.nameEn ?? null, collapsible, collapsed, count: countItems(group), blocks };
        });
    }

    /**
     * 트리를 상단 가로 막대의 구역으로 편다 — 1단계를 가로로 놓는다.
     * 최상위 항목은 제목 없는 한 구역, 최상위 그룹은 "제목 + 직속 칸 한 줄", 그 안의 하위 그룹은 눌렀을 때 펼치는 드롭다운 칸이다. 접힘은 무시한다(가로라 접지 않는다).
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @param {MenuContext} ctx - 지금 있는 곳(접힘 기억은 쓰지 않는다).
     * @returns {MenuBarSection[]} 왼쪽에서 오른쪽으로 놓을 구역들.
     */
    static barSections(nodes: readonly MenuNode[], ctx: MenuContext): MenuBarSection[] {
        const entriesOf = (children: readonly MenuNode[]): MenuBarEntry[] => {
            const entries: MenuBarEntry[] = [];
            for (const node of children) {
                if (node.kind === 'ITEM') {
                    entries.push({ kind: 'item', key: node.id, row: toRow(node, 0, ctx) });
                    continue;
                }
                const blocks: MenuBlock[] = [];
                collect(node.children, 0, null, ctx, false, blocks);
                entries.push({ kind: 'dropdown', key: node.id, group: node, active: containsId(node, ctx.activeId), blocks });
            }
            return entries;
        };
        return splitTop(nodes).map((part, index) => {
            if ('run' in part) return { key: `top-${index}-${part.run[0]!.id}`, group: null, title: null, titleEn: null, entries: entriesOf(part.run) };
            return { key: part.group.id, group: part.group, title: part.group.label, titleEn: part.group.nameEn ?? null, entries: entriesOf(part.group.children) };
        });
    }

    /**
     * 게시판 모음(`/boards`)의 구역 — 게시판 항목이 있는 그룹만 남기고(앱 화면 항목·빈 그룹은 뺀다) 하위 그룹은 평평하게 펴지 않고 소제목으로 둔다. 접지 않는다.
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @returns {MenuSection[]} 구역들.
     */
    static boardSections(nodes: readonly MenuNode[]): MenuSection[] {
        return UtilMenu.sections(pruneToBoards(nodes), { activeId: null, activeSlug: null, memory: {} }, false);
    }

    /**
     * 글쓰기의 게시판 고르기 목록 — 게시판을 **메뉴의 그룹 이름 아래**로 묶는다(운영자가 메뉴 구성에서 그룹 이름을 바꾸면 따라간다).
     * 게시판이 속한 그룹은 메뉴 트리에서 그 게시판 항목의 **가장 가까운 부모 그룹**이다. 묶음·게시판 순서는 메뉴 순서(서버가 정렬해 준 그대로)다.
     * 메뉴에 없는 게시판(숨김·보이는 대상 밖·그룹 없이 최상위에 놓인 항목)은 기본 이름 "게시판"(`UtilBoard.bundleBoards`) 한 묶음으로 맨 뒤에 모은다
     * (메뉴 그룹 이름이 그 기본 이름과 같으면 같은 제목이 두 번 나오지 않게 그 묶음 끝에 붙인다) —
     * 메뉴를 못 받았을 때(`nodes` 가 빈 배열)는 전부 이쪽이라 "게시판" 한 묶음이다.
     * @param {Translate} t - 번역 함수(기본 묶음 이름 `board.bundleLabel`).
     * @param {readonly MenuNode[]} nodes - 메뉴 트리(서버 메뉴. 못 받았으면 빈 배열).
     * @param {readonly BoardView[]} boards - 고를 수 있는 게시판(이미 걸러진 것). 이 목록에 없는 게시판은 메뉴에 있어도 나오지 않는다.
     * @returns {BoardPickGroup[]} 게시판이 하나 이상 있는 묶음들.
     */
    static boardPickGroups(t: Translate, nodes: readonly MenuNode[], boards: readonly BoardView[]): BoardPickGroup[] {
        const bySlug = new Map(boards.map((b) => [b.slug, b] as const));
        const placed = new Set<string>();
        const groups = new Map<string, BoardPickGroup>();

        const walk = (list: readonly MenuNode[], parent: MenuGroupNode | null, depth: number): void => {
            if (depth >= MAX_READ_DEPTH) return;
            for (const node of list) {
                if (node.kind === 'GROUP') {
                    walk(node.children, node, depth + 1);
                    continue;
                }
                // 바로가기는 소속 묶음이 아니다 — 게시판은 진짜 자리 묶음 이름 아래에 둔다
                const slug = node.type === 'BOARD' && node.shortcut !== true ? node.board?.slug : undefined;
                const board = slug ? bySlug.get(slug) : undefined;
                // 부모 그룹이 없는 최상위 항목은 이름을 줄 그룹이 없다 — 메뉴에 없는 게시판과 같이 기본 이름으로 간다
                if (!board || !parent || placed.has(board.slug)) continue;
                placed.add(board.slug);
                let group = groups.get(parent.id);
                if (!group) {
                    group = { key: parent.id, label: parent.label, boards: [] };
                    groups.set(parent.id, group);
                }
                group.boards.push(board);
            }
        };
        walk(nodes, null, 0);

        const out = [...groups.values()];
        const rest = UtilBoard.bundleBoards(t, boards.filter((b) => !placed.has(b.slug)));
        if (rest) {
            // 메뉴 그룹 이름이 기본 이름과 같으면 같은 제목이 두 번 나오지 않게 그 묶음 끝에 붙인다
            const same = out.find((o) => o.label === rest.label);
            if (same) same.boards.push(...rest.boards);
            else out.push({ key: 'unplaced', label: rest.label, boards: rest.boards });
        }
        return out;
    }

    /**
     * 메뉴 자리의 모양을 정한다 — 서버 메뉴가 있으면 그것(비었으면 `empty`), 받는 중이면 `loading`, 못 받았으면 `error`.
     * @param {MenuPhase} phase - 서버 메뉴 요청의 진행.
     * @param {number | null} nodeCount - 서버 메뉴의 최상위 노드 수. 받은 메뉴가 없으면 null.
     * @returns {MenuViewState} 메뉴 자리의 모양.
     */
    static viewState(phase: MenuPhase, nodeCount: number | null): MenuViewState {
        if (nodeCount !== null) return nodeCount === 0 ? 'empty' : 'ready';
        if (phase === 'idle' || phase === 'loading') return 'loading';
        return 'error';
    }

    /**
     * 저장해 둔 접힘 기억을 읽는다 — 없거나 깨졌으면 빈 기억(그룹의 처음 상태를 쓴다). 참·거짓이 아닌 값은 버린다.
     * @param {string | null | undefined} text - 저장소에서 읽은 글자(`{ [노드ID]: boolean }` JSON).
     * @returns {MenuCollapseMemory} 접힘 기억.
     */
    static parseCollapse(text: string | null | undefined): MenuCollapseMemory {
        if (!text) return {};
        try {
            const raw: unknown = JSON.parse(text);
            if (!isRecord(raw)) return {};
            const out: MenuCollapseMemory = {};
            for (const [id, value] of Object.entries(raw)) if (typeof value === 'boolean') out[id] = value;
            return out;
        } catch {
            return {};
        }
    }

    /**
     * 접힘 기억을 저장할 글자로 만든다.
     * @param {Readonly<Record<string, boolean>>} memory - 접힘 기억.
     * @returns {string} JSON 글자.
     */
    static serializeCollapse(memory: Readonly<Record<string, boolean>>): string {
        return JSON.stringify(memory);
    }

    /**
     * 세로 메뉴(왼쪽·오른쪽·햄버거) 한 줄의 클래스 — 항목과 그룹 제목 단추가 같은 줄 모양을 쓴다.
     * @param {boolean} active - 지금 보고 있는 곳인가.
     * @returns {string} 클래스 문자열(다크 모드 포함).
     */
    static listItemClass(active: boolean): string {
        return [
            'flex items-center justify-between gap-2 px-4 py-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
            active ? 'bg-primary-50 font-semibold text-primary dark:bg-primary-950/60' : 'text-color hover:bg-surface-100 dark:hover:bg-surface-800',
        ].join(' ');
    }

    /**
     * 색 점 자리의 클래스 — 점 폭(`size-2`) + 글자와의 간격(`mr-2`) 한 곳. 점이 있는 줄과 없는 줄이 같은 자리를 써서 글자 시작 위치가 맞는다.
     * 상단 드롭다운은 점이 없는 줄도 이 자리를 비워 둔다(community `docs/decisions.md` "공개 메뉴 색 점 — 정렬·홈 카드 로고").
     * 세로 메뉴·햄버거(`AppMenuList`)는 점이 없는 줄에 자리를 두지 않고 글자를 왼쪽에 붙인다(사용자 결정 2026-10-28).
     */
    static readonly DOT_SLOT_CLASS = 'mr-2 inline-block size-2 shrink-0';

    /**
     * 메뉴 줄의 색 점 자리 클래스 — 표시색이 있으면 둥근 점, 없으면 같은 폭의 빈 자리.
     * @param {string | null} color - 줄의 표시색(12색 이름) 또는 null.
     * @returns {string} 클래스 문자열.
     */
    static dotClass(color: string | null): string {
        return color ? `${UtilMenu.DOT_SLOT_CLASS} rounded-full` : UtilMenu.DOT_SLOT_CLASS;
    }

    /**
     * 메뉴 이름에 칠할 글자 색(운영자 「글자 색」) — 12색은 표의 라이트·다크 짝, `#RRGGBB` 는 라이트·다크 바탕에서 읽히게 **명도만** 보정한 짝(`UtilColor.cssPair` → `light-dark()`).
     * 이름 앞 점(●)의 게시판 표시색과 따로다. 기본(null)이면 undefined — 줄 클래스의 색(지금 있는 곳 `text-primary`·hover)을 그대로 따른다.
     * 색을 고른 줄은 지금 있는 곳이어도 이 색을 유지한다 — 지금 있는 곳 표시는 줄 바탕·밑줄·굵기가 맡는다.
     * @param {Pick<MenuItemNode, 'textColor'>} node - 메뉴 항목.
     * @returns {ColorStyle | undefined} 글자색 스타일.
     */
    static textStyle(node: Pick<MenuItemNode, 'textColor'>): ColorStyle | undefined {
        const pair = UtilColor.cssPair(node.textColor);
        return pair ? { color: pair } : undefined;
    }

    /**
     * 메뉴 이름의 굵기 클래스(운영자 「강조 › 굵게」) — 굵게면 `font-semibold`(600, 지금 있는 곳 줄과 같은 굵기).
     * @param {Pick<MenuItemNode, 'bold'>} node - 메뉴 항목.
     * @returns {string} 클래스 문자열(굵게가 아니면 빈 문자열).
     */
    static labelClass(node: Pick<MenuItemNode, 'bold'>): string {
        return node.bold === true ? 'font-semibold' : '';
    }

    /** 이름 뒤 배지(알약)의 클래스 — 글자 11px · 위아래 대칭 여백 · 줄 높이 1(components.md 2-4). 안쪽 글자는 `top-[-0.09em]` 으로 한글 내려앉음을 보정한다 */
    static readonly BADGE_CLASS = 'ml-1.5 inline-flex shrink-0 items-center justify-center rounded-full px-1.5 py-0.5 text-[11px] font-semibold leading-none';

    /** 좁은 칸(모바일 상단 탭·로고 옆 링크)의 작은 배지 — 글자 10px · 여백을 줄여 줄 높이를 거의 늘리지 않는다. 칸이 `whitespace-nowrap`·`shrink-0` 이라 줄바꿈 없이 옆으로 민다 */
    static readonly BADGE_COMPACT_CLASS = 'ml-1 inline-flex shrink-0 items-center justify-center rounded-full px-1 py-px text-[10px] font-semibold leading-none';

    /**
     * 이름 뒤 배지의 색 — 배지 색 = 글자 색: 바탕은 그 색 14% + 카드 바탕, 글자는 그 바탕 위에서 4.5:1 이 되게 보정한 색(말머리 태그와 같은 식, `UtilBoard.tagStyle`).
     * 글자 색이 기본(null)이면 주 색(`--p-primary-color`)으로 같은 식을 쓴다.
     * @param {Pick<MenuItemNode, 'textColor'>} node - 메뉴 항목.
     * @returns {ColorStyle} 배지 스타일.
     */
    static badgeStyle(node: Pick<MenuItemNode, 'textColor'>): ColorStyle {
        const pair = UtilColor.cssPair(node.textColor);
        const text = UtilColor.cssTagText(node.textColor);
        if (pair && text) return { '--menu-badge-c': pair, color: text, background: 'color-mix(in srgb, var(--menu-badge-c) 14%, var(--p-content-background))' };
        return { color: 'var(--p-primary-color)', background: 'color-mix(in srgb, var(--p-primary-color) 14%, var(--p-content-background))' };
    }

    /**
     * 상단 가로 막대 한 칸의 클래스 — 높이를 고정(2.5rem)해 막대 높이가 글자 크기에 흔들리지 않고, 활성은 아래 줄로 표시한다.
     * @param {boolean} active - 지금 보고 있는 곳인가.
     * @param {boolean} [bold=false] - 메뉴 글자 기본 굵게(테마 메뉴 설정 `menuBold`)인가 — 켜면 칸이 모두 700(활성 포함), 끄면 활성만 600.
     * @returns {string} 클래스 문자열(다크 모드 포함).
     */
    static barItemClass(active: boolean, bold = false): string {
        return [
            'flex h-10 shrink-0 items-center gap-1.5 border-b-2 px-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
            bold ? 'font-bold' : active ? 'font-semibold' : '',
            active ? 'border-primary text-color' : 'border-transparent text-muted-color hover:text-color',
        ].join(' ');
    }
}
