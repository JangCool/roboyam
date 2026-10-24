import type { LocationQuery } from 'vue-router';
import type {
    AdminPostBoardOption,
    AdminPostItem,
    AdminPostQuery,
    PostBulkAction,
    PostBulkResult,
    PostModerationEventType,
    PostSearchField,
    PostSort,
    PostState,
    PostStateFilter,
} from '~/composables/post/api/usePostApi.types';
import UtilMember from '~/utils/util-member';

/**
 * 게시글 관리 화면(`/posts`)의 순수 로직 — 주소 쿼리 ↔ 필터 · 검색어 검사(대상별) · 작성일 기간 · 사유 검사 · 글마다 가능한 조치 · 일괄 대상 · 이동 검사 · 상태·기록 색.
 * 계약 `docs/features/post-admin-contract.md` 와 같은 규칙이다(community `util-admin-post.ts` 를 로보얌 상태·권한에 맞게 옮김). 서버가 거절할 것을 미리 알려 주는 용도이고 **판정은 늘 서버가 한다**.
 * 계산이 낀 문구는 `util-post-messages.ts`.
 */

/** 주소 쿼리에 실리는 필터 — `page` 는 1부터(사람이 읽는 쪽 번호), API 에 보낼 때 0부터로 바꾼다 */
export interface PostFilters {
    /** 검색 대상(기본 `TITLE`) */
    field: PostSearchField;
    q: string;
    /** 게시판 ID(없으면 빈 글자) */
    board: string;
    /** 말머리 ID(게시판을 골랐을 때만) */
    prefix: string;
    /** 작성일 시작(`YYYY-MM-DD`, 사이트 시간대의 날짜 — 없으면 빈 글자) */
    from: string;
    /** 작성일 끝(`YYYY-MM-DD`, 없으면 빈 글자) */
    to: string;
    state: PostStateFilter;
    sort: PostSort;
    page: number;
    /** 한 쪽의 건수(`PAGE_SIZES` 중 하나) */
    size: number;
}

/** 검색어 검사 결과 — 비었다 / 너무 짧다(제목·작성자) / 숫자가 아니다(글 번호) / 보낼 수 있다 */
export type PostSearchKind = 'NONE' | 'TOO_SHORT' | 'ID_INVALID' | 'OK';

/** 사유 입력의 문제 */
export type PostReasonProblem = 'REQUIRED' | 'TOO_LONG';

/** 이동 칸의 문제 */
export type PostMoveProblem = 'BOARD_REQUIRED' | 'SAME_LOCATION';

/** 글 하나의 조치 */
export type PostActionKind = 'HIDE' | 'UNHIDE' | 'DELETE' | 'RESTORE' | 'MOVE' | 'NOTICE' | 'PIN' | 'UNPIN';

/** 글 한 줄에서 누를 수 있는 조치 — 화면이 메뉴·단추를 그릴지 가른다 */
export type PostActions = Record<PostActionKind, boolean>;

/** 조치를 가르는 권한 — `post.manage` · `post.restore` */
export interface PostActionPermissions {
    manage: boolean;
    restore: boolean;
}

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** 글 번호 검색어 — 숫자만(앞의 `#` 허용) */
const ID_INPUT = /^#?\s*(\d+)$/;
/** 달력 날짜 글자 */
const YMD = /^(\d{4})-(\d{2})-(\d{2})$/;
/** 주소의 `post` — 숫자만 */
const POST_ID = /^\d{1,19}$/;

const SEARCH_FIELDS: readonly PostSearchField[] = ['TITLE', 'AUTHOR', 'ID'];
const STATE_FILTERS: readonly PostStateFilter[] = ['ALL', 'PUBLIC', 'HIDDEN', 'DELETED'];
const SORTS: readonly PostSort[] = ['newest', 'oldest'];

const EVENT_SEVERITY: Record<PostModerationEventType, Severity> = {
    HIDDEN: 'warn',
    REPORT_HIDDEN: 'warn',
    UNHIDDEN: 'success',
    DELETED: 'danger',
    RESTORED: 'success',
    MOVED: 'info',
    NOTICE: 'info',
    PINNED: 'info',
    UNPINNED: 'secondary',
};

/** 유니코드 한 글자씩 센다(서버도 코드포인트로 센다) */
function lengthOf(text: string): number {
    return Array.from(text).length;
}

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

/** 있는 날짜인 `YYYY-MM-DD` 만 — 아니면 빈 글자 */
function ymd(value: string): string {
    const m = YMD.exec(value);
    if (!m) return '';
    const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
    const date = new Date(Date.UTC(y, mo - 1, d));
    return date.getUTCFullYear() === y && date.getUTCMonth() === mo - 1 && date.getUTCDate() === d ? value : '';
}

export default class UtilPost {
    /** 한 쪽의 기본 건수 — 서버 기본값과 같다 */
    static readonly PAGE_SIZE = 20;
    /** 쪽 크기 고르개의 선택지 — 서버 상한이 50 이다 */
    static readonly PAGE_SIZES: readonly number[] = [10, 20, 50];
    /** 검색 대상 고르개의 차례 — 첫째가 기본 */
    static readonly SEARCH_FIELDS = SEARCH_FIELDS;
    /** 제목·작성자 검색어의 최소 글자 수 */
    static readonly SEARCH_MIN = 2;
    /** 검색칸의 `maxlength` */
    static readonly SEARCH_MAX = 100;
    /** 조치 사유 1~200자 */
    static readonly REASON_MAX = 200;
    /** 일괄 처리가 한 번에 받는 최대 글 수 */
    static readonly BULK_MAX = 50;
    /** 상태 탭 차례 */
    static readonly STATE_FILTERS = STATE_FILTERS;
    static readonly SORTS = SORTS;
    /** 일괄 조치 차례 */
    static readonly BULK_ACTIONS: readonly PostBulkAction[] = ['HIDE', 'UNHIDE', 'DELETE'];
    /** ⋯ 메뉴·바닥 단추의 차례 */
    static readonly ACTION_ORDER: readonly PostActionKind[] = ['HIDE', 'UNHIDE', 'MOVE', 'NOTICE', 'PIN', 'UNPIN', 'DELETE', 'RESTORE'];

    /* ── 주소 ↔ 필터 ── */

    /** 기본 필터 */
    static emptyFilters(): PostFilters {
        return { field: 'TITLE', q: '', board: '', prefix: '', from: '', to: '', state: 'ALL', sort: 'newest', page: 1, size: UtilPost.PAGE_SIZE };
    }

    /**
     * 주소의 쿼리를 필터로 읽는다 — 모르는 값은 기본값. 말머리는 게시판이 있을 때만 읽는다. 작성일(`from`·`to`)은 있는 `YYYY-MM-DD` 만.
     * @param {LocationQuery} query - `route.query`.
     * @returns {PostFilters} 필터.
     */
    static parse(query: LocationQuery): PostFilters {
        const field = first(query.field).toUpperCase() as PostSearchField;
        const state = first(query.state).toUpperCase() as PostStateFilter;
        const sort = first(query.sort).toLowerCase() as PostSort;
        const page = Number.parseInt(first(query.page), 10);
        const size = Number.parseInt(first(query.size), 10);
        const board = first(query.board).trim();
        return {
            field: SEARCH_FIELDS.includes(field) ? field : 'TITLE',
            q: Array.from(first(query.q).trim()).slice(0, UtilPost.SEARCH_MAX).join(''),
            board,
            prefix: board ? first(query.prefix).trim() : '',
            from: ymd(first(query.from).trim()),
            to: ymd(first(query.to).trim()),
            state: STATE_FILTERS.includes(state) ? state : 'ALL',
            sort: SORTS.includes(sort) ? sort : 'newest',
            page: Number.isFinite(page) && page >= 1 ? page : 1,
            size: UtilPost.PAGE_SIZES.includes(size) ? size : UtilPost.PAGE_SIZE,
        };
    }

    /**
     * 필터를 주소의 쿼리로 — 기본값은 싣지 않는다.
     * @param {PostFilters} f - 필터.
     * @returns {Record<string, string>} 쿼리.
     */
    static toQuery(f: PostFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.field !== 'TITLE') query.field = f.field;
        if (f.q) query.q = f.q;
        if (f.board) query.board = f.board;
        if (f.board && f.prefix) query.prefix = f.prefix;
        if (f.from) query.from = f.from;
        if (f.to) query.to = f.to;
        if (f.state !== 'ALL') query.state = f.state;
        if (f.sort !== 'newest') query.sort = f.sort;
        if (f.page > 1) query.page = String(f.page);
        if (f.size !== UtilPost.PAGE_SIZE) query.size = String(f.size);
        return query;
    }

    /**
     * API 조건 — 쪽은 0부터. 작성일은 회원 관리 가입일과 같은 변환(사이트 시간대 그날 00:00:00.000 ~ 23:59:59.999 → UTC ISO).
     * @param {PostFilters} f - 필터.
     * @param {string} zoneId - 기간 경계를 잡을 시간대(표시 시간대 = 사이트 시간대).
     * @returns {AdminPostQuery} API 조건(빈 값은 API 층이 뺀다).
     */
    static toApi(f: PostFilters, zoneId: string): AdminPostQuery {
        return {
            q: f.q,
            searchField: f.q ? f.field : undefined,
            board: f.board,
            prefix: f.prefix,
            createdFrom: f.from ? UtilMember.dayStart(f.from, zoneId) : undefined,
            createdTo: f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined,
            state: f.state,
            sort: f.sort,
            page: f.page - 1,
            size: f.size,
        };
    }

    /** 필터가 같은지 가르는 열쇠 — 바뀌면 목록을 다시 받는다 */
    static key(f: PostFilters): string {
        return JSON.stringify(UtilPost.toQuery(f));
    }

    /** 필터의 일부를 바꾼다 — 쪽 말고 다른 것(쪽 크기 포함)을 바꾸면 1쪽으로, 게시판을 바꾸면 말머리를 비운다 */
    static change(f: PostFilters, patch: Partial<PostFilters>): PostFilters {
        const next: PostFilters = { ...f, ...patch };
        if (patch.board !== undefined && patch.board !== f.board) next.prefix = patch.prefix ?? '';
        if (patch.page === undefined) next.page = 1;
        return next;
    }

    /** 조건(검색·게시판·말머리·작성일·상태)이 걸려 있다 — 검색 대상만 고른 것·정렬·쪽·쪽 크기는 조건이 아니다 */
    static hasConditions(f: PostFilters): boolean {
        return f.q !== '' || f.board !== '' || f.prefix !== '' || f.from !== '' || f.to !== '' || f.state !== 'ALL';
    }

    /** 조건만 지운다(검색 대상·작성일 포함 — 정렬·쪽 크기는 그대로) */
    static clear(f: PostFilters): PostFilters {
        return { ...UtilPost.emptyFilters(), sort: f.sort, size: f.size };
    }

    /** 주소의 `post`(열 글 번호) — 숫자가 아니면 null */
    static parsePost(query: LocationQuery): string | null {
        const id = first(query.post).trim();
        return POST_ID.test(id) ? id : null;
    }

    /** 쪽 수 */
    static pageCount(total: number, size: number): number {
        return Math.max(1, Math.ceil(total / Math.max(1, size)));
    }

    /* ── 검색 ── */

    /**
     * 검색어를 대상에 맞춰 검사한다 — 제목·작성자는 2자 이상, 글 번호는 숫자만(앞의 `#` 는 떼고 보낸다). 판정은 서버도 한다(`fields.q`).
     * @param {PostSearchField} field - 검색 대상.
     * @param {string} raw - 입력한 검색어.
     * @returns {{ kind: PostSearchKind; text: string }} 결과와 서버에 보낼 글자(보내지 않으면 빈 글자).
     */
    static parseSearch(field: PostSearchField, raw: string): { kind: PostSearchKind; text: string } {
        const text = raw.trim();
        if (text === '') return { kind: 'NONE', text: '' };
        if (field === 'ID') {
            const m = ID_INPUT.exec(text);
            return m?.[1] ? { kind: 'OK', text: m[1] } : { kind: 'ID_INVALID', text: '' };
        }
        if (lengthOf(text) < UtilPost.SEARCH_MIN) return { kind: 'TOO_SHORT', text: '' };
        return { kind: 'OK', text };
    }

    /* ── 사유 ── */

    static reasonLength(text: string): number {
        return lengthOf(text.trim());
    }

    /** 사유의 문제 — 필수 1~200자(서버 `fields.reason` 과 같은 규칙) */
    static reasonProblem(text: string): PostReasonProblem | null {
        const n = UtilPost.reasonLength(text);
        if (n === 0) return 'REQUIRED';
        if (n > UtilPost.REASON_MAX) return 'TOO_LONG';
        return null;
    }

    /* ── 가능한 조치 ── */

    static noActions(): PostActions {
        return { HIDE: false, UNHIDE: false, DELETE: false, RESTORE: false, MOVE: false, NOTICE: false, PIN: false, UNPIN: false };
    }

    /**
     * 한 글에 누를 수 있는 조치 — 상태와 권한이 정한다(서버도 `409 POST_STATE_INVALID` 로 막는다).
     * 공개: 숨기기·이동·공지 지정(이미 고정된 공지가 아니면)·고정 또는 고정 해제·삭제. 숨김: 숨김 해제·이동·삭제. 삭제: 복구(`post.restore`, 파기 전).
     * @param {AdminPostItem} item - 글.
     * @param {PostActionPermissions} can - 권한.
     * @returns {PostActions} 누를 수 있는 조치.
     */
    static actionsOf(item: Pick<AdminPostItem, 'state' | 'notice' | 'pinned' | 'purged'>, can: PostActionPermissions): PostActions {
        const actions = UtilPost.noActions();
        if (item.state === 'DELETED') {
            actions.RESTORE = can.restore && !item.purged;
            return actions;
        }
        if (!can.manage) return actions;
        if (item.state === 'PUBLIC') {
            actions.HIDE = true;
            actions.MOVE = true;
            actions.NOTICE = !(item.notice && item.pinned);
            actions.PIN = !item.pinned;
            actions.UNPIN = item.pinned;
            actions.DELETE = true;
        } else {
            actions.UNHIDE = true;
            actions.MOVE = true;
            actions.DELETE = true;
        }
        return actions;
    }

    static hasAction(actions: PostActions): boolean {
        return Object.values(actions).some(Boolean);
    }

    /** 허락된 조치(정해진 차례) */
    static actionList(actions: PostActions): PostActionKind[] {
        return UtilPost.ACTION_ORDER.filter((kind) => actions[kind]);
    }

    /** 위험한 조치 — 빨강 단추 */
    static isDanger(kind: PostActionKind): boolean {
        return kind === 'HIDE' || kind === 'DELETE';
    }

    static actionIcon(kind: PostActionKind): string {
        const icons: Record<PostActionKind, string> = {
            HIDE: 'pi pi-eye-slash',
            UNHIDE: 'pi pi-eye',
            DELETE: 'pi pi-trash',
            RESTORE: 'pi pi-undo',
            MOVE: 'pi pi-arrow-right-arrow-left',
            NOTICE: 'pi pi-megaphone',
            PIN: 'pi pi-thumbtack',
            UNPIN: 'pi pi-thumbtack',
        };
        return icons[kind];
    }

    /* ── 일괄 ── */

    /** 이 일괄 조치를 이 글에 할 수 있나 — 숨기기는 공개 글, 숨김 해제는 숨긴 글, 삭제는 공개·숨김 글 */
    static bulkEligible(action: PostBulkAction, item: Pick<AdminPostItem, 'state'>): boolean {
        if (action === 'HIDE') return item.state === 'PUBLIC';
        if (action === 'UNHIDE') return item.state === 'HIDDEN';
        return item.state === 'PUBLIC' || item.state === 'HIDDEN';
    }

    /** 줄을 고를 수 있나 — 공개·숨김 글(지운 글은 일괄 조치가 없다) */
    static isSelectable(item: Pick<AdminPostItem, 'state'>): boolean {
        return item.state !== 'DELETED';
    }

    static bulkTargets<T extends Pick<AdminPostItem, 'state'>>(action: PostBulkAction, items: readonly T[]): T[] {
        return items.filter((item) => UtilPost.bulkEligible(action, item));
    }

    /** 결과를 글마다 한 줄로 — 처리됨 → 못 함 */
    static bulkRows(result: PostBulkResult): { id: string; ok: boolean; code: string | null }[] {
        return [...result.done.map((id) => ({ id, ok: true, code: null })), ...result.failed.map((f) => ({ id: f.id, ok: false, code: f.code }))];
    }

    /* ── 이동 ── */

    /** 지금 자리 그대로인가 — 같은 게시판·같은 말머리(둘 다 없음 포함) */
    static isSameLocation(item: Pick<AdminPostItem, 'board' | 'prefix'>, boardId: string, prefixId: string | null): boolean {
        return item.board.id === boardId && (item.prefix?.id ?? null) === (prefixId || null);
    }

    static moveProblem(item: Pick<AdminPostItem, 'board' | 'prefix'>, board: AdminPostBoardOption | null | undefined, prefixId: string | null): PostMoveProblem | null {
        if (!board) return 'BOARD_REQUIRED';
        if (UtilPost.isSameLocation(item, board.id, prefixId)) return 'SAME_LOCATION';
        return null;
    }

    /* ── 이웃·모양 ── */

    /** 지금 쪽의 줄 순서에서 열린 글의 위·아래 이웃 — 이 쪽에 없으면 둘 다 null */
    static neighbors<T extends { id: string }>(items: readonly T[], id: string | null): { prev: T | null; next: T | null } {
        const at = id === null ? -1 : items.findIndex((item) => item.id === id);
        if (at < 0) return { prev: null, next: null };
        return { prev: items[at - 1] ?? null, next: items[at + 1] ?? null };
    }

    static stateSeverity(state: PostState): Severity {
        return state === 'PUBLIC' ? 'success' : state === 'HIDDEN' ? 'warn' : 'danger';
    }

    static stateIcon(state: PostState): string {
        return state === 'PUBLIC' ? 'pi pi-globe' : state === 'HIDDEN' ? 'pi pi-eye-slash' : 'pi pi-trash';
    }

    static eventSeverity(type: string): Severity {
        return EVENT_SEVERITY[type as PostModerationEventType] ?? 'secondary';
    }

    /** 조치 기록의 덧붙임 중 글자로 보일 수 있는 것(문자·숫자·참거짓)만 — 모양은 서버가 정한다 */
    static detailPairs(detail: Record<string, unknown> | null): [string, string][] {
        if (!detail) return [];
        return Object.entries(detail)
            .filter(([, v]) => typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean')
            .map(([k, v]) => [k, String(v)]);
    }

    /** HTML 본문이 비었나 — 글자도 그림·영상·표도 없다 */
    static isHtmlEmpty(html: string): boolean {
        if (/<(?:img|video|audio|iframe|table|hr)\b|data-youtube-id/i.test(html)) return false;
        return html.replace(/<[^>]*>/g, '').replace(/&nbsp;|\s/g, '') === '';
    }
}
