import { ApiError } from '~/composables/common/useApiClient';
import type { GroupListQuery, GroupManagerPermission, GroupRow, GroupStatusFilter, GroupVisibilityFilter } from '~/composables/group/api/useGroupAdminApi.types';

/**
 * 소모임 목록(`/groups`)·개설 승인(`/group-requests`) 화면의 순수 로직 — 주소 쿼리 읽기·만들기, 서버 요청 쿼리, 사유·메모 규칙, 줄의 표시 판단.
 * community `util-admin-group-manage.ts`·`util-group-admin.ts` 를 옮겼다. 사유 한도는 서버(community `GroupRules`)와 같다 — 판정은 서버다.
 *
 * 필터는 **주소에 들어 있다**(`?status=INACTIVE&closed=1&offline=1&visibility=PUBLIC&q=서울&page=2`, 열어 둔 소모임은 `&group=ID`) — 새로고침해도 그대로다.
 * 기본값(전체·성질 필터 없음·검색 없음·1쪽)은 주소에서 뺀다. 쪽은 주소에서 1부터, 서버 요청에서 0부터. 모르는 값은 기본값으로 읽는다. 정렬은 서버가 최신순(개설일)으로 고정한다.
 */

/** 목록 화면의 필터·쪽 */
export interface GroupListFilters {
    status: GroupStatusFilter;
    /** 모임장이 모집을 마감한 것만 */
    closed: boolean;
    /** 오프라인 모임만 */
    offline: boolean;
    visibility: GroupVisibilityFilter;
    /** 앞뒤 공백을 뗀 검색어(이름·모임장 닉네임). 비면 조건 없음 */
    q: string;
    /** 1부터 */
    page: number;
}

/** 이름 뒤에 붙는 딱지 */
export type GroupBadgeKey = 'PUBLIC' | 'PRIVATE' | 'INACTIVE' | 'CLOSED' | 'FULL' | 'OFFLINE' | 'STOCK' | 'APPROVAL';

/** 비어 있는 목록의 갈래 — 관리할 소모임이 하나도 없다 / 조건에 맞는 소모임이 없다 */
export type GroupEmptyKind = 'NOTHING' | 'FILTERED';

type RawQuery = Record<string, unknown>;

const PAGE_MAX = 100_000;
const STATUSES: readonly GroupStatusFilter[] = ['ALL', 'ACTIVE', 'INACTIVE'];
const VISIBILITIES: readonly GroupVisibilityFilter[] = ['ALL', 'PUBLIC', 'PRIVATE'];
/** 소모임 ID — 숫자만(서버가 문자열로 준다). 주소의 이상한 글자는 거른다 */
const ID = /^[0-9]{1,19}$/;

function first(value: unknown): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

export default class UtilGroup {
    /** 소모임 목록 주소 */
    static readonly LIST_PATH = '/groups';
    /** 개설 승인 주소 */
    static readonly REQUESTS_PATH = '/group-requests';
    /** 소모임 분류 주소 */
    static readonly CATEGORIES_PATH = '/group-categories';
    /** 소모임 설정 주소 */
    static readonly SETTINGS_PATH = '/group-settings';
    /** 개설 신청 대기 알림 이벤트(운영자, 예약됨 — 서버 `AdminNotificationEvent.CLUB_OPEN_REQUEST_PENDING`) */
    static readonly OPEN_REQUEST_EVENT_PATH = '/settings/notifications/operator/CLUB_OPEN_REQUEST_PENDING';

    /** 한 쪽의 줄 수 — 서버 기본값과 같다 */
    static readonly PAGE_SIZE = 20;
    /** 검색어 최대 글자 수 */
    static readonly SEARCH_MAX = 50;
    /** 반려·비활성 사유 최대 글자 수(필수) — 서버 `fields.reason` 과 같다 */
    static readonly REASON_MAX = 200;
    /** 다시 열기 메모 최대 글자 수(선택) — 서버 `fields.memo` 와 같다 */
    static readonly MEMO_MAX = 200;
    /** 소개가 이보다 길면(글자·줄) 접고 「소개 전체 보기」를 둔다 */
    static readonly LONG_CHARS = 120;
    static readonly LONG_LINES = 3;

    /** 매니저가 맡을 수 있는 일 — 서버 배열의 고정 순서 */
    static readonly MANAGER_PERMISSIONS: readonly GroupManagerPermission[] = ['APPLICATIONS', 'KICK', 'RECRUITING', 'EVENTS', 'UNPUBLISH'];

    /** 처음 모습 */
    static readonly DEFAULTS: Readonly<GroupListFilters> = Object.freeze({ status: 'ALL', closed: false, offline: false, visibility: 'ALL', q: '', page: 1 });

    /**
     * 주소의 쿼리를 필터로 읽는다 — 모르는 값은 기본값이 된다.
     * @param {RawQuery} query - 라우터가 준 쿼리.
     * @returns {GroupListFilters} 필터.
     */
    static parse(query: RawQuery): GroupListFilters {
        const rawStatus = first(query.status).toUpperCase();
        const status = (STATUSES as readonly string[]).includes(rawStatus) ? (rawStatus as GroupStatusFilter) : 'ALL';
        const rawVisibility = first(query.visibility).toUpperCase();
        const visibility = (VISIBILITIES as readonly string[]).includes(rawVisibility) ? (rawVisibility as GroupVisibilityFilter) : 'ALL';
        const q = Array.from(first(query.q).trim()).slice(0, UtilGroup.SEARCH_MAX).join('').trim();
        const pageNumber = Number(first(query.page));
        const page = Number.isInteger(pageNumber) && pageNumber >= 1 ? Math.min(pageNumber, PAGE_MAX) : 1;
        return { status, closed: first(query.closed) === '1', offline: first(query.offline) === '1', visibility, q, page };
    }

    /**
     * 주소의 `group`(서랍으로 연 소모임 ID) — 숫자가 아니면 없는 것으로 본다.
     * @param {RawQuery} query - 라우터가 준 쿼리.
     * @returns {string | null} 소모임 ID.
     */
    static parseGroup(query: RawQuery): string | null {
        const id = first(query.group).trim();
        return ID.test(id) ? id : null;
    }

    /**
     * 필터를 주소의 쿼리로 — 기본값은 뺀다.
     * @param {GroupListFilters} filters - 필터.
     * @returns {Record<string, string>} 쿼리.
     */
    static toQuery(filters: GroupListFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (filters.status !== 'ALL') query.status = filters.status;
        if (filters.closed) query.closed = '1';
        if (filters.offline) query.offline = '1';
        if (filters.visibility !== 'ALL') query.visibility = filters.visibility;
        if (filters.q) query.q = filters.q;
        if (filters.page > 1) query.page = String(filters.page);
        return query;
    }

    /**
     * 필터를 서버 요청의 쿼리로 — 쪽은 0부터, 한 쪽 20건. 꺼진 성질 필터·빈 검색어는 보내지 않는다.
     * @param {GroupListFilters} filters - 필터.
     * @returns {GroupListQuery} 쿼리.
     */
    static toApi(filters: GroupListFilters): GroupListQuery {
        const query: GroupListQuery = { page: filters.page - 1, size: UtilGroup.PAGE_SIZE };
        if (filters.status !== 'ALL') query.status = filters.status;
        if (filters.closed) query.closed = true;
        if (filters.offline) query.offline = true;
        if (filters.visibility !== 'ALL') query.visibility = filters.visibility;
        if (filters.q) query.q = filters.q;
        return query;
    }

    /**
     * 필터가 같은지 견주는 열쇠.
     * @param {GroupListFilters} filters - 필터.
     * @returns {string} 열쇠 글자.
     */
    static key(filters: GroupListFilters): string {
        return JSON.stringify([filters.status, filters.closed, filters.offline, filters.visibility, filters.q, filters.page]);
    }

    /**
     * 필터를 고친다 — 조건을 바꾸면 1쪽으로 돌아간다. `page` 를 함께 주면 그 쪽으로 간다.
     * @param {GroupListFilters} filters - 지금 필터.
     * @param {Partial<GroupListFilters>} patch - 바꿀 칸.
     * @returns {GroupListFilters} 새 필터.
     */
    static change(filters: GroupListFilters, patch: Partial<GroupListFilters>): GroupListFilters {
        const next: GroupListFilters = { ...filters, ...patch };
        if (patch.page === undefined) next.page = 1;
        return next;
    }

    /**
     * 조건이 걸려 있는가(쪽은 조건이 아니다).
     * @param {GroupListFilters} filters - 필터.
     * @returns {boolean} 하나라도 걸려 있으면 true.
     */
    static hasConditions(filters: GroupListFilters): boolean {
        return filters.status !== 'ALL' || filters.closed || filters.offline || filters.visibility !== 'ALL' || filters.q !== '';
    }

    /**
     * 목록이 비었을 때의 갈래.
     * @param {GroupListFilters} filters - 필터.
     * @returns {GroupEmptyKind} 조건이 있으면 FILTERED.
     */
    static emptyKind(filters: GroupListFilters): GroupEmptyKind {
        return UtilGroup.hasConditions(filters) ? 'FILTERED' : 'NOTHING';
    }

    /**
     * 지금 쪽이 범위를 벗어났는가 — 건수는 있는데 이 쪽이 비었다(주소로 큰 쪽 번호를 넣었다).
     * @param {number} itemCount - 이 쪽에 받은 줄 수.
     * @param {number} total - 전체 개수.
     * @param {number} page - 지금 쪽(1부터).
     * @returns {boolean} 벗어났으면 true.
     */
    static outOfRange(itemCount: number, total: number, page: number): boolean {
        return itemCount === 0 && total > 0 && page > 1;
    }

    /**
     * 전체 쪽 수(0건이어도 1).
     * @param {number} total - 전체 개수.
     * @returns {number} 쪽 수.
     */
    static pageCount(total: number): number {
        return Math.max(1, Math.ceil(total / UtilGroup.PAGE_SIZE));
    }

    /**
     * 앞뒤 공백을 뗀 글자 수 — 유니코드 한 글자씩(서버도 코드포인트로 센다).
     * @param {string} text - 입력한 글.
     * @returns {number} 글자 수.
     */
    static trimmedLength(text: string): number {
        return Array.from(text.trim()).length;
    }

    /**
     * 사유·메모로 낼 수 있는가 — 필수면 비어 있지 않아야 하고, 한도를 넘으면 안 된다.
     * @param {string} text - 입력한 글.
     * @param {number} max - 최대 글자 수.
     * @param {boolean} required - 필수인가.
     * @returns {boolean} 낼 수 있으면 true.
     */
    static reasonOk(text: string, max: number, required: boolean): boolean {
        const length = UtilGroup.trimmedLength(text);
        if (length > max) return false;
        return !required || length > 0;
    }

    /**
     * 소개가 길어 접어 보일까.
     * @param {string} text - 소개.
     * @returns {boolean} 길면 true.
     */
    static isLong(text: string): boolean {
        return text.length > UtilGroup.LONG_CHARS || text.split('\n').length > UtilGroup.LONG_LINES;
    }

    /**
     * 분류 칸 아래에 덧붙일 게시판 이름 — 분류가 없으면 늘(어느 게시판의 소모임인지 알려 준다), 분류가 있어도 이름이 다르면 붙인다.
     * @param {Pick<GroupRow, 'category' | 'board'>} row - 목록·상세의 한 줄.
     * @returns {string} 게시판 이름 또는 빈 글자.
     */
    static boardLine(row: Pick<GroupRow, 'category' | 'board'>): string {
        const board = row.board?.name?.trim() ?? '';
        if (board === '') return '';
        return row.category && row.category.name === board ? '' : board;
    }

    /**
     * 이름 뒤 딱지 — 공개 범위 · 비활성 · 모집 마감/정원 참 · 오프라인 · 투자 고지 · 승인제 가입 순.
     * @param {Pick<GroupRow, 'status' | 'recruitState' | 'offline' | 'stockNotice' | 'joinType' | 'visibility'>} row - 줄.
     * @returns {GroupBadgeKey[]} 딱지 열쇠.
     */
    static badges(row: Pick<GroupRow, 'status' | 'recruitState' | 'offline' | 'stockNotice' | 'joinType' | 'visibility'>): GroupBadgeKey[] {
        const out: GroupBadgeKey[] = [row.visibility];
        if (row.status === 'INACTIVE') out.push('INACTIVE');
        if (row.recruitState === 'CLOSED') out.push('CLOSED');
        else if (row.recruitState === 'FULL') out.push('FULL');
        if (row.offline) out.push('OFFLINE');
        if (row.stockNotice) out.push('STOCK');
        if (row.joinType === 'APPROVAL') out.push('APPROVAL');
        return out;
    }

    /**
     * 딱지 색 — 비활성은 붉게, 투자 고지는 노랗게, 공개는 파랗게, 나머지는 중립.
     * @param {GroupBadgeKey} key - 딱지.
     * @returns {'danger' | 'warn' | 'info' | 'secondary'} PrimeVue Tag 색.
     */
    static badgeSeverity(key: GroupBadgeKey): 'danger' | 'warn' | 'info' | 'secondary' {
        if (key === 'INACTIVE') return 'danger';
        if (key === 'STOCK') return 'warn';
        if (key === 'PUBLIC') return 'info';
        return 'secondary';
    }

    /**
     * 매니저의 맡은 일 중 화면이 아는 것만(서버 순서 그대로).
     * @param {readonly string[]} permissions - 맡은 일.
     * @returns {GroupManagerPermission[]} 아는 것만.
     */
    static knownPermissions(permissions: readonly string[]): GroupManagerPermission[] {
        return permissions.filter((key): key is GroupManagerPermission => (UtilGroup.MANAGER_PERMISSIONS as readonly string[]).includes(key));
    }

    /**
     * 그 사이 다른 운영자가 처리했다는 답인가 — 승인·반려의 `404 GROUP_NOT_FOUND`.
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 그렇다면 true.
     */
    static isGone(e: unknown): boolean {
        return e instanceof ApiError && e.status === 404 && e.code === 'GROUP_NOT_FOUND';
    }

    /**
     * 비활성화·다시 열기가 「이미 처리됨」(409)인가.
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 그렇다면 true.
     */
    static isConflict(e: unknown): boolean {
        return e instanceof ApiError && e.status === 409 && (e.code === 'GROUP_ALREADY_INACTIVE' || e.code === 'GROUP_NOT_INACTIVE');
    }

    /**
     * 사유·메모 칸의 서버 거절 문구(`fields.reason`·`fields.memo`).
     * @param {unknown} e - 잡은 오류.
     * @param {'reason' | 'memo'} field - 칸 이름.
     * @returns {string | null} 문구. 해당 없으면 null.
     */
    static fieldError(e: unknown, field: 'reason' | 'memo'): string | null {
        return e instanceof ApiError && e.status === 400 ? (e.field(field) ?? null) : null;
    }

    /**
     * 권한이 없다는 답인가(접속 중에 권한을 거뒀다) — 화면 오류가 아니라 안내로 다룬다.
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 403 이면 true.
     */
    static isForbidden(e: unknown): boolean {
        return e instanceof ApiError && e.status === 403;
    }
}
