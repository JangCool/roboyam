import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BoardView } from '~/composables/board/api/useBoardApi.types';
import type { MenuBoardRef, MenuGroupNode, MenuItemNode, MenuNode, MenuResponse } from '~/composables/menu/api/useMenuApi.types';
import type { SiteTerms } from '~/composables/site/useSiteTerms.types';
import UtilBoard from '~/utils/util-board';
import UtilSiteTerms from '~/utils/util-site-terms';

/**
 * 메뉴 규칙 — 햄버거(모바일)·왼쪽/오른쪽 사이드 메뉴(세로)·상단 메뉴(가로)·모바일 상단 탭·`/boards` 모음이 **같은 트리·같은 판정**을 쓰도록 한 곳에 둔다.
 *
 * 트리의 출처는 서버(`GET /api/menu`)다. 서버가 보는 사람 기준으로 거르고 정렬까지 끝내 주므로 여기서는 다시 거르거나 정렬하지 않는다(`docs/api-menu.md` 5절).
 * 서버 메뉴를 못 받았을 때만 기본 규칙(홈·공동구매 + "게시판" 한 묶음(서버가 준 순서) + 소모임 고정 항목)으로 **같은 노드 모양**의 기본 메뉴를 만든다(`fallbackNodes`).
 * 세로·가로는 이 트리를 어떤 모양으로 그리는지만 다르다(`AppMenuList` · `AppTopMenu`). 상태도 요청도 없다(상태는 `useMenu`·`useMenuItems`).
 */

/** 지금 그릴 트리가 어디서 왔나 — `fallback` 이면 화면이 "기본 메뉴를 보여 드려요" 안내를 단다 */
export type MenuSource = 'server' | 'fallback';

/**
 * 메뉴 자리가 어떤 모양인가
 * - `loading` 받는 중(줄 모양 스켈레톤) · `ready` 서버 메뉴 · `empty` 서버가 빈 메뉴를 줬다 ·
 * - `fallback` 서버 메뉴는 못 받았고 게시판 목록으로 기본 메뉴를 그린다 · `error` 서버 메뉴도 게시판 목록도 못 받았다(고정 항목·소모임만 그리고 다시 시도를 둔다)
 */
export type MenuViewState = 'loading' | 'ready' | 'empty' | 'fallback' | 'error';

/** 메뉴 서버 요청의 진행 — `idle` 아직 안 물어봄 · `loading` 첫 응답 전 · `ready` 받았다 · `error` 못 받았고 가진 메뉴도 없다 */
export type MenuPhase = 'idle' | 'loading' | 'ready' | 'error';

/** 줄을 만들 때 필요한 바깥 사정 — 지금 있는 곳·접힘 기억 */
export interface MenuContext {
    /** 지금 있는 곳으로 밝힐 항목의 ID. 없으면 null */
    activeId: string | null;
    /** 사용자가 접거나 편 기억(노드 ID → 접힘). 기억이 없는 그룹은 노드의 `collapsed` 기본값을 쓴다 */
    memory: Readonly<Record<string, boolean>>;
}

/** 메뉴 항목 한 줄 */
export interface MenuItemRow {
    key: string;
    node: MenuItemNode;
    /** 들여쓰기 단계 — 최상위 그룹의 직속 항목은 0, 하위 그룹 안은 그 그룹의 단계 */
    level: number;
    /** 지금 보고 있는 곳인가(`aria-current="page"`) */
    active: boolean;
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
    title: string | null;
    /** 제목의 영문명 — 없으면 null */
    titleEn: string | null;
    entries: MenuBarEntry[];
}

/** 서버 메뉴가 없을 때 만드는 기본 메뉴 */
export interface FallbackMenu {
    nodes: MenuNode[];
    mobileTabs: string[];
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

function readNode(raw: unknown, depth: number): MenuNode | null {
    if (!isRecord(raw) || typeof raw.id !== 'string' || raw.id === '' || typeof raw.label !== 'string' || raw.label === '') return null;
    if (raw.kind === 'GROUP') {
        if (depth >= MAX_READ_DEPTH) return null;
        const children = Array.isArray(raw.children) ? readNodes(raw.children, depth + 1) : [];
        // 보이는 항목이 하나도 없는 그룹은 뺀다 — 서버도 같은 규칙으로 빼지만, 읽다가 항목이 빠진 그룹이 제목만 남지 않게 한다
        if (children.length === 0) return null;
        const collapsible = raw.collapsible === true;
        return { id: raw.id, kind: 'GROUP', label: raw.label, nameEn: readNameEn(raw), collapsible, collapsed: collapsible && raw.collapsed === true, children };
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
            board: readBoardRef(raw.board),
            extra: isRecord(raw.extra) ? raw.extra : null,
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

function toRow(node: MenuItemNode, level: number, activeId: string | null): MenuItemRow {
    // 색 점은 운영자가 고른 표시색이 있을 때만 단다 — 서버 `color` 가 12색 이름이 아니면(비었거나 모르는 값) 점이 없다
    const color = UtilBoard.presetColor(node.board?.color);
    return { key: node.id, node, level, active: node.id === activeId, notice: node.board?.investmentNotice === true, color };
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
            current.items.push(toRow(node, level, ctx.activeId));
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
            if (isBoardItem(node)) out.push(node);
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
     * 메뉴의 "내 소모임" 이름 — 소모임 이름(`GROUPS`) 앞에 "내"
     * @param {Translate} t - 번역 함수.
     * @param {Partial<SiteTerms> | null} [terms] - 운영자가 바꾼 서비스 이름 표. 없으면 기본 이름.
     * @returns {string} 예: "내 소모임"(영어는 "My Clubs").
     */
    static myGroupsLabel(t: Translate, terms: Partial<SiteTerms> | null = null): string {
        return t('menu.myGroups', { groups: UtilSiteTerms.term(t, 'GROUPS', terms) });
    }

    /**
     * 메뉴의 "전체 소모임" 이름 — 소모임 이름(`GROUPS`) 앞에 "전체"
     * @param {Translate} t - 번역 함수.
     * @param {Partial<SiteTerms> | null} [terms] - 운영자가 바꾼 서비스 이름 표. 없으면 기본 이름.
     * @returns {string} 예: "전체 소모임"(영어는 "All Clubs").
     */
    static allGroupsLabel(t: Translate, terms: Partial<SiteTerms> | null = null): string {
        return t('menu.allGroups', { groups: UtilSiteTerms.term(t, 'GROUPS', terms) });
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
     * 지금 있는 곳으로 밝힐 항목 **하나** — 모든 항목 중 주소가 가장 길게 맞는 것이다(그래서 `/groups/me` 는 내 소모임만, `/groups/12` 는 전체 소모임만 밝다).
     * - `matchPrefix` 가 false 면 주소가 정확히 같을 때만, true 면 `path === p || path.startsWith(p + '/')` 일 때 맞는다(홈은 `/` 에서만).
     * - 게시판 항목은 지금 보는 게시판(`currentSlug`)이 그 게시판이면 주소와 상관없이 밝다 — 글 상세·글쓰기 화면에서도 그 게시판 줄이 켜지게 하려는 것이다.
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
            if (currentSlug && isBoardItem(item) && item.board!.slug === currentSlug) {
                score = Number.MAX_SAFE_INTEGER;
            } else if (item.path.startsWith('/')) {
                const p = normalizePath(item.path);
                const hit = item.matchPrefix ? here === p || here.startsWith(p + '/') : here === p;
                if (hit) score = p.length;
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
                    entries.push({ kind: 'item', key: node.id, row: toRow(node, 0, ctx.activeId) });
                    continue;
                }
                const blocks: MenuBlock[] = [];
                collect(node.children, 0, null, ctx, false, blocks);
                entries.push({ kind: 'dropdown', key: node.id, group: node, active: containsId(node, ctx.activeId), blocks });
            }
            return entries;
        };
        return splitTop(nodes).map((part, index) => {
            if ('run' in part) return { key: `top-${index}-${part.run[0]!.id}`, title: null, titleEn: null, entries: entriesOf(part.run) };
            return { key: part.group.id, title: part.group.label, titleEn: part.group.nameEn ?? null, entries: entriesOf(part.group.children) };
        });
    }

    /**
     * 게시판 모음(`/boards`)의 구역 — 게시판 항목이 있는 그룹만 남기고(앱 화면 항목·빈 그룹은 뺀다) 하위 그룹은 평평하게 펴지 않고 소제목으로 둔다. 접지 않는다.
     * @param {readonly MenuNode[]} nodes - 메뉴 트리.
     * @returns {MenuSection[]} 구역들.
     */
    static boardSections(nodes: readonly MenuNode[]): MenuSection[] {
        return UtilMenu.sections(pruneToBoards(nodes), { activeId: null, memory: {} }, false);
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
                const slug = node.type === 'BOARD' ? node.board?.slug : undefined;
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
     * 서버 메뉴를 못 받았을 때의 기본 메뉴 — 같은 노드 모양으로 만든다.
     * 홈·공동구매(고정) → "게시판" 한 묶음(서버가 준 순서 — 게시판은 성격으로 나누지 않는다) → 소모임 구역.
     * 소모임은 서버 게시판 설정과 무관한 고정 항목이라 게시판 목록을 못 받아도 있고, "내 소모임"은 로그인했을 때만 있다.
     * 모바일 탭은 홈뿐이다(어느 게시판을 탭에 올릴지는 운영자가 메뉴 구성에서 정하는 일이라 기본 메뉴는 게시판을 탭으로 올리지 않는다). **"보이는 대상"을 몰라 등급·운영진 전용으로 정한 항목이 보일 수 있다**(게시판 주소의 원래 규칙은 그대로 적용된다 — 메뉴 숨김은 접근 통제가 아니다).
     * 노드 ID 는 `fb-` 로 시작해 서버 ID 와 섞이지 않는다.
     * @param {Translate} t - 번역 함수(홈·소모임 묶음·게시판 묶음 이름).
     * @param {readonly BoardView[]} boards - 게시판 목록(`useBoards`). 못 받았으면 빈 배열.
     * @param {boolean} loggedIn - 로그인했는가.
     * @param {Partial<SiteTerms> | null} [terms] - 서비스 이름 표 — 공동구매·소모임 글자. 없으면 기본 이름.
     * @returns {FallbackMenu} 노드 트리와 모바일 탭 ID.
     */
    static fallbackNodes(t: Translate, boards: readonly BoardView[], loggedIn: boolean, terms: Partial<SiteTerms> | null = null): FallbackMenu {
        const builtin = (id: string, key: string, label: string, path: string, matchPrefix: boolean, mobileTab: boolean): MenuItemNode => ({
            id,
            kind: 'ITEM',
            type: 'BUILTIN',
            key,
            label,
            path,
            matchPrefix,
            newTab: false,
            mobileTab,
            board: null,
        });
        const groupItems: MenuItemNode[] = [];
        if (loggedIn) groupItems.push(builtin('fb-groups-mine', 'GROUPS_MINE', UtilMenu.myGroupsLabel(t, terms), '/groups/me', false, false));
        groupItems.push(builtin('fb-groups-all', 'GROUPS_ALL', UtilMenu.allGroupsLabel(t, terms), '/groups', true, false));
        const groupsNode: MenuGroupNode = { id: 'fb-groups', kind: 'GROUP', label: UtilSiteTerms.term(t, 'GROUPS', terms), collapsible: false, collapsed: false, children: groupItems };

        const nodes: MenuNode[] = [
            builtin('fb-home', 'HOME', t('menu.home'), '/', false, true),
            builtin('fb-group-buys', 'GROUP_BUYS', UtilSiteTerms.term(t, 'GROUP_BUY', terms), '/group-buys', true, false),
        ];
        const mobileTabs: string[] = ['fb-home'];
        const bundle = UtilBoard.bundleBoards(t, boards);
        if (bundle) {
            nodes.push({
                id: 'fb-boards',
                kind: 'GROUP',
                label: bundle.label,
                collapsible: false,
                collapsed: false,
                children: bundle.boards.map<MenuItemNode>((b) => ({
                    id: `fb-board-${b.id}`,
                    kind: 'ITEM',
                    type: 'BOARD',
                    key: null,
                    label: b.name,
                    nameEn: b.nameEn ?? null,
                    path: `/boards/${b.slug}`,
                    matchPrefix: true,
                    newTab: false,
                    mobileTab: false,
                    board: { id: b.id, slug: b.slug, color: UtilBoard.presetColor(b.color), investmentNotice: b.investmentNotice },
                })),
            });
        }
        nodes.push(groupsNode);
        return { nodes, mobileTabs };
    }

    /**
     * 메뉴 자리의 모양을 정한다 — 서버 메뉴가 있으면 그것(비었으면 `empty`), 없으면 기본 메뉴(`fallback`)나 오류.
     * 서버 메뉴의 줄·점(`color`)은 메뉴 응답만으로 그려지므로 서버 메뉴가 있으면 게시판 목록을 기다리지 않는다.
     * 기본 메뉴는 게시판 목록으로 만들므로, 서버 메뉴가 없을 때 게시판 목록이 아직 오는 중이면 메뉴도 받는 중으로 둔다.
     * @param {MenuPhase} phase - 서버 메뉴 요청의 진행.
     * @param {number | null} nodeCount - 서버 메뉴의 최상위 노드 수. 받은 메뉴가 없으면 null.
     * @param {boolean} boardsPending - 게시판 목록이 아직 오는 중인가(받은 적도 실패한 적도 없다).
     * @param {boolean} boardsFailed - 게시판 목록도 못 받았나.
     * @returns {MenuViewState} 메뉴 자리의 모양.
     */
    static viewState(phase: MenuPhase, nodeCount: number | null, boardsPending: boolean, boardsFailed: boolean): MenuViewState {
        if (nodeCount !== null) return nodeCount === 0 ? 'empty' : 'ready';
        if (phase === 'idle' || phase === 'loading') return 'loading';
        if (boardsPending) return 'loading';
        return boardsFailed ? 'error' : 'fallback';
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
     * 점이 없는 줄도 이 자리를 비워 둔다(세로 메뉴·햄버거·상단 드롭다운, `docs/decisions.md` "공개 메뉴 색 점 — 정렬·홈 카드 로고").
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
     * 상단 가로 막대 한 칸의 클래스 — 높이를 고정(2.5rem)해 막대 높이가 글자 크기에 흔들리지 않고, 활성은 아래 줄로 표시한다.
     * @param {boolean} active - 지금 보고 있는 곳인가.
     * @returns {string} 클래스 문자열(다크 모드 포함).
     */
    static barItemClass(active: boolean): string {
        return [
            'flex h-10 shrink-0 items-center gap-1.5 border-b-2 px-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
            active ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color hover:text-color',
        ].join(' ');
    }
}
