import type { LocationQuery } from 'vue-router';
import type {
    ReportBulkResult,
    ReportCaseAction,
    ReportCaseDetail,
    ReportCaseItem,
    ReportCaseQuery,
    ReportCaseStatus,
    ReportEvent,
    ReportEventType,
    ReportReasonCode,
    ReportResolveAction,
    ReportSearchField,
    ReportSort,
    ReportStatusFilter,
    ReportTarget,
    ReportTargetState,
    ReportTargetType,
} from '~/composables/report/api/useReportApi.types';
import UtilMember from '~/utils/util-member';

/**
 * 신고 관리 화면(`/reports`)의 순수 로직 — 주소 쿼리 ↔ 필터 · 검색어 검사(대상별) · 마지막 신고일 기간 · 조치 글 검사 · 사건마다 가능한 처리 · 일괄 대상 · 정렬 머리 · 스냅샷 비교 · 색.
 * 계약 `docs/features/report-admin-contract.md` 와 같은 규칙이다(게시글 관리 `util-post.ts` 와 같은 구조). 서버가 거절할 것을 미리 알려 주는 용도이고 **판정은 늘 서버가 한다**.
 * 계산이 낀 문구는 `util-report-messages.ts`.
 */

/** 주소 쿼리에 실리는 필터 — `page` 는 1부터(사람이 읽는 쪽 번호), API 에 보낼 때 0부터로 바꾼다 */
export interface ReportFilters {
    /** 검색 대상(기본 `TITLE`) */
    field: ReportSearchField;
    q: string;
    /** 대상 종류(빈 글자 = 전체) */
    type: ReportTargetType | '';
    /** 신고 사유(빈 글자 = 전체) */
    reason: ReportReasonCode | '';
    /** 게시판 ID(없으면 빈 글자) */
    board: string;
    /** 마지막 신고일 시작(`YYYY-MM-DD`, 사이트 시간대의 날짜 — 없으면 빈 글자) */
    from: string;
    /** 마지막 신고일 끝(`YYYY-MM-DD`) */
    to: string;
    status: ReportStatusFilter;
    /** 그 글과 그 글 댓글의 사건만(게시글 관리 「신고」 칸에서 넘어옴) — 없으면 빈 글자 */
    postId: string;
    /** 그 소모임 사건만(관리자 소모임 상세 서랍의 「신고 보기」에서 넘어옴) — 없으면 빈 글자 */
    groupId: string;
    sort: ReportSort;
    page: number;
    /** 한 쪽의 건수(`PAGE_SIZES` 중 하나) */
    size: number;
}

/** 검색어 검사 결과 — 비었다 / 너무 짧다(제목·작성자) / 숫자가 아니다(사건 번호) / 보낼 수 있다 */
export type ReportSearchKind = 'NONE' | 'TOO_SHORT' | 'NUMBER_INVALID' | 'OK';

/** 조치 글의 문제 */
export type ReportNoteProblem = 'REQUIRED' | 'TOO_LONG';

/** 처리 대화창의 종류 — 조치 셋 + 무시 */
export type ReportActKind = ReportResolveAction | 'DISMISS';

/** 사건 하나에서 누를 수 있는 처리 */
export type ReportActions = Record<ReportActKind, boolean>;

/** 닫힌 사건의 처리 결과(사건 칸 → 없으면 처리 기록의 덧붙임) */
export interface ReportClosure {
    action: ReportCaseAction | null;
    reasonCode: ReportReasonCode | null;
    note: string | null;
    falseReport: boolean;
    by: string | null;
    at: string | null;
}

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** 사건 번호 검색어 — 숫자만(앞의 `#` 허용) */
const NUMBER_INPUT = /^#?\s*(\d+)$/;
const YMD = /^(\d{4})-(\d{2})-(\d{2})$/;
/** 주소의 숫자 ID(`report`·`postId`) */
const NUMERIC_ID = /^\d{1,19}$/;

const SEARCH_FIELDS: readonly ReportSearchField[] = ['TITLE', 'AUTHOR', 'CASE_NO'];
/** 상태 라디오 차례 — 열린 사건(대기 + 검토 중, 기본) · 대기 · 검토 중 · 처리됨 · 무시 · 전체 */
const STATUS_FILTERS: readonly ReportStatusFilter[] = ['OPEN', 'PENDING', 'IN_REVIEW', 'RESOLVED', 'DISMISSED', 'ALL'];
const TARGET_TYPES: readonly ReportTargetType[] = ['POST', 'COMMENT', 'GROUP'];
/** 글·댓글 신고 사유 차례 — 게시판 신고 정책 화면의 사유 표와 같다 */
const POST_REASONS: readonly ReportReasonCode[] = ['SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'OTHER'];
/** 소모임 신고 사유 차례(서버 `ReportReasons.GROUP_ALL`) */
const GROUP_REASONS: readonly ReportReasonCode[] = ['PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION', 'OTHER'];
/** 모든 사유 — 사유 거르기·칩 차례(서버 `ReportReasons.EVERY`) */
const REASONS: readonly ReportReasonCode[] = ['SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION', 'OTHER'];
const SORTS: readonly ReportSort[] = ['newest', 'oldest', 'most'];
/** 자동 숨김(신고 누적·불법 즉시·권리침해 임시조치) — 무시하면 서버가 복구하고, 「자동 숨김 확정」을 할 수 있다. 운영자 숨김(STAFF)은 아니다 */
const AUTO_HIDDEN = new Set(['REPORTED', 'ILLEGAL', 'RIGHTS']);

const EVENT_SEVERITY: Record<ReportEventType, Severity> = {
    REPORTED: 'secondary',
    AUTO_HIDDEN: 'warn',
    REVIEW_STARTED: 'info',
    RESOLVED: 'danger',
    DISMISSED: 'success',
    REOPENED: 'info',
};

function lengthOf(text: string): number {
    return Array.from(text).length;
}

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

function ymd(value: string): string {
    const m = YMD.exec(value);
    if (!m) return '';
    const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
    const date = new Date(Date.UTC(y, mo - 1, d));
    return date.getUTCFullYear() === y && date.getUTCMonth() === mo - 1 && date.getUTCDate() === d ? value : '';
}

function str(value: unknown): string | null {
    return typeof value === 'string' && value.trim() !== '' ? value : null;
}

/** 비교용 — 태그·공백을 걷어 낸 글자 */
function plain(text: string | null | undefined): string {
    return (text ?? '')
        .replace(/<[^>]*>/g, ' ')
        .replace(/&nbsp;/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

export default class UtilReport {
    static readonly PAGE_SIZE = 20;
    /** 쪽 크기 고르개 — 서버 상한이 50 이다 */
    static readonly PAGE_SIZES: readonly number[] = [10, 20, 50];
    static readonly SEARCH_FIELDS = SEARCH_FIELDS;
    static readonly SEARCH_MIN = 2;
    static readonly SEARCH_MAX = 100;
    /** 조치 글 1~500자(무시는 0~500자) */
    static readonly NOTE_MAX = 500;
    /** 소모임 비활성화 글 1~200자 */
    static readonly GROUP_NOTE_MAX = 200;
    /** 일괄 무시가 한 번에 받는 최대 사건 수 */
    static readonly BULK_MAX = 50;
    static readonly STATUS_FILTERS = STATUS_FILTERS;
    static readonly TARGET_TYPES = TARGET_TYPES;
    static readonly REASONS = REASONS;
    static readonly SORTS = SORTS;
    /** 처리 단추의 차례 */
    static readonly ACT_ORDER: readonly ReportActKind[] = ['HIDE', 'KEEP_HIDDEN', 'DELETE', 'DEACTIVATE', 'DISMISS'];

    /* ── 주소 ↔ 필터 ── */

    static emptyFilters(): ReportFilters {
        return { field: 'TITLE', q: '', type: '', reason: '', board: '', from: '', to: '', status: 'OPEN', postId: '', groupId: '', sort: 'newest', page: 1, size: UtilReport.PAGE_SIZE };
    }

    /**
     * 주소의 쿼리를 필터로 읽는다 — 모르는 값은 기본값. 기간(`from`·`to`)은 있는 `YYYY-MM-DD` 만, `postId` 는 숫자만.
     * @param {LocationQuery} query - `route.query`.
     * @returns {ReportFilters} 필터.
     */
    static parse(query: LocationQuery): ReportFilters {
        const field = first(query.field).toUpperCase() as ReportSearchField;
        const type = first(query.type).toUpperCase() as ReportTargetType;
        const reason = first(query.reason).toUpperCase() as ReportReasonCode;
        const status = first(query.status).toUpperCase() as ReportStatusFilter;
        const sort = first(query.sort).toLowerCase() as ReportSort;
        const page = Number.parseInt(first(query.page), 10);
        const size = Number.parseInt(first(query.size), 10);
        const postId = first(query.postId).trim();
        const groupId = first(query.groupId).trim();
        return {
            field: SEARCH_FIELDS.includes(field) ? field : 'TITLE',
            q: Array.from(first(query.q).trim()).slice(0, UtilReport.SEARCH_MAX).join(''),
            type: TARGET_TYPES.includes(type) ? type : '',
            reason: REASONS.includes(reason) ? reason : '',
            board: first(query.board).trim(),
            from: ymd(first(query.from).trim()),
            to: ymd(first(query.to).trim()),
            status: STATUS_FILTERS.includes(status) ? status : 'OPEN',
            postId: NUMERIC_ID.test(postId) ? postId : '',
            groupId: NUMERIC_ID.test(groupId) ? groupId : '',
            sort: SORTS.includes(sort) ? sort : 'newest',
            page: Number.isFinite(page) && page >= 1 ? page : 1,
            size: UtilReport.PAGE_SIZES.includes(size) ? size : UtilReport.PAGE_SIZE,
        };
    }

    /** 필터를 주소의 쿼리로 — 기본값은 싣지 않는다 */
    static toQuery(f: ReportFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.field !== 'TITLE') query.field = f.field;
        if (f.q) query.q = f.q;
        if (f.type) query.type = f.type;
        if (f.reason) query.reason = f.reason;
        if (f.board) query.board = f.board;
        if (f.from) query.from = f.from;
        if (f.to) query.to = f.to;
        if (f.status !== 'OPEN') query.status = f.status;
        if (f.postId) query.postId = f.postId;
        if (f.groupId) query.groupId = f.groupId;
        if (f.sort !== 'newest') query.sort = f.sort;
        if (f.page > 1) query.page = String(f.page);
        if (f.size !== UtilReport.PAGE_SIZE) query.size = String(f.size);
        return query;
    }

    /**
     * API 조건 — 쪽은 0부터. 기간은 게시글 관리 작성일과 같은 변환(사이트 시간대 그날 00:00:00.000 ~ 23:59:59.999 → UTC ISO).
     * @param {ReportFilters} f - 필터.
     * @param {string} zoneId - 기간 경계를 잡을 시간대(표시 시간대 = 사이트 시간대).
     * @returns {ReportCaseQuery} API 조건(빈 값은 API 층이 뺀다).
     */
    static toApi(f: ReportFilters, zoneId: string): ReportCaseQuery {
        return {
            status: f.status,
            targetType: f.type || undefined,
            reason: f.reason || undefined,
            q: f.q,
            searchField: f.q ? f.field : undefined,
            from: f.from ? UtilMember.dayStart(f.from, zoneId) : undefined,
            to: f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined,
            board: f.board,
            postId: f.postId,
            groupId: f.groupId,
            sort: f.sort,
            page: f.page - 1,
            size: f.size,
        };
    }

    static key(f: ReportFilters): string {
        return JSON.stringify(UtilReport.toQuery(f));
    }

    /** 필터의 일부를 바꾼다 — 쪽 말고 다른 것(쪽 크기 포함)을 바꾸면 1쪽으로 */
    static change(f: ReportFilters, patch: Partial<ReportFilters>): ReportFilters {
        const next: ReportFilters = { ...f, ...patch };
        if (patch.page === undefined) next.page = 1;
        return next;
    }

    /** 조건(검색·대상·사유·게시판·기간·상태·글)이 걸려 있다 — 정렬·쪽·쪽 크기는 조건이 아니다. 상태는 기본(`OPEN`)이 아니면 조건이다 */
    static hasConditions(f: ReportFilters): boolean {
        return f.q !== '' || f.type !== '' || f.reason !== '' || f.board !== '' || f.from !== '' || f.to !== '' || f.status !== 'OPEN' || f.postId !== '' || f.groupId !== '';
    }

    /** 조건만 지운다(정렬·쪽 크기는 그대로) */
    static clear(f: ReportFilters): ReportFilters {
        return { ...UtilReport.emptyFilters(), sort: f.sort, size: f.size };
    }

    /** 주소의 `report`(열 사건 ID) — 숫자가 아니면 null */
    static parseReport(query: LocationQuery): string | null {
        const id = first(query.report).trim();
        return NUMERIC_ID.test(id) ? id : null;
    }

    static pageCount(total: number, size: number): number {
        return Math.max(1, Math.ceil(total / Math.max(1, size)));
    }

    /* ── 정렬 머리 ── */

    /** 표의 정렬 머리 — 신고 많은 순은 「신고 수」, 나머지는 「마지막 신고」 */
    static sortField(sort: ReportSort): 'reportCount' | 'lastReportedAt' {
        return sort === 'most' ? 'reportCount' : 'lastReportedAt';
    }

    static sortOrder(sort: ReportSort): 1 | -1 {
        return sort === 'oldest' ? 1 : -1;
    }

    /** 머리를 눌렀다 — 「신고 수」는 많은 순 하나뿐(서버가 아는 정렬), 「마지막 신고」는 오름차순 = 오래된, 내림차순 = 최신 */
    static sortOf(field: string | null | undefined, order: number | null | undefined): ReportSort {
        if (field === 'reportCount') return 'most';
        return order === 1 ? 'oldest' : 'newest';
    }

    /* ── 검색 ── */

    /**
     * 검색어를 대상에 맞춰 검사한다 — 제목·작성자는 2자 이상, 사건 번호는 숫자만(앞의 `#` 는 떼고 보낸다). 판정은 서버도 한다(`fields.q`).
     */
    static parseSearch(field: ReportSearchField, raw: string): { kind: ReportSearchKind; text: string } {
        const text = raw.trim();
        if (text === '') return { kind: 'NONE', text: '' };
        if (field === 'CASE_NO') {
            const m = NUMBER_INPUT.exec(text);
            return m?.[1] ? { kind: 'OK', text: m[1] } : { kind: 'NUMBER_INVALID', text: '' };
        }
        if (lengthOf(text) < UtilReport.SEARCH_MIN) return { kind: 'TOO_SHORT', text: '' };
        return { kind: 'OK', text };
    }

    /* ── 조치 글 ── */

    static noteLength(text: string): number {
        return lengthOf(text.trim());
    }

    /** 처리 종류의 글 최대 — 소모임 비활성화는 모임장에게 사유로 가고 소모임 행에 남아 200자(서버 `GroupAdminService.REASON_MAX`), 그 밖 500자 */
    static noteMax(kind: ReportActKind | null | undefined): number {
        return kind === 'DEACTIVATE' ? UtilReport.GROUP_NOTE_MAX : UtilReport.NOTE_MAX;
    }

    /** 조치 글의 문제 — 조치는 필수 1~500자(소모임 비활성화 1~200자), 무시는 선택 0~500자(서버 `fields.note` 와 같은 규칙) */
    static noteProblem(text: string, required: boolean, max: number = UtilReport.NOTE_MAX): ReportNoteProblem | null {
        const n = UtilReport.noteLength(text);
        if (n === 0) return required ? 'REQUIRED' : null;
        if (n > max) return 'TOO_LONG';
        return null;
    }

    /** 사건 대상의 사유 체계 — 소모임은 소모임 사유 4종, 글·댓글은 6종(조치 사유 코드도 이 안에서만 — 서버 `fields.reasonCode`) */
    static reasonsFor(type: ReportTargetType): readonly ReportReasonCode[] {
        return type === 'GROUP' ? GROUP_REASONS : POST_REASONS;
    }

    /** 소모임 사유인가(문구 키가 다르다) */
    static isGroupReason(code: string): boolean {
        return code !== 'OTHER' && (GROUP_REASONS as readonly string[]).includes(code);
    }

    /* ── 가능한 처리 ── */

    /** 열린 사건(대기·검토 중) */
    static isOpen(status: ReportCaseStatus): boolean {
        return status === 'PENDING' || status === 'IN_REVIEW';
    }

    /** 자동 숨김된 대상 — 신고 누적·불법 즉시·권리침해 임시조치(운영자 숨김은 아니다) */
    static isAutoHidden(target: Pick<ReportTarget, 'state' | 'hiddenReason'>): boolean {
        return target.state === 'HIDDEN' && target.hiddenReason !== null && AUTO_HIDDEN.has(target.hiddenReason);
    }

    static noActions(): ReportActions {
        return { HIDE: false, KEEP_HIDDEN: false, DELETE: false, DEACTIVATE: false, DISMISS: false };
    }

    /**
     * 사건 하나에 누를 수 있는 처리 — 열린 사건 + `report.manage` 일 때만(서버도 `409 REPORT_CASE_CLOSED`·`REPORT_ACTION_INVALID` 로 막는다).
     * 글·댓글: 숨기기 = 공개 대상만 · 자동 숨김 확정 = 자동 숨김된 대상만 · 삭제 = 지우지 않은 대상 · 무시 = 늘.
     * 소모임: 비활성화 = 열린(`PUBLIC`) 소모임만 · 무시 = 늘(숨기기·삭제는 없다 — community 와 같다).
     */
    static actionsOf(item: Pick<ReportCaseItem, 'status' | 'target' | 'targetType'>, canManage: boolean): ReportActions {
        const actions = UtilReport.noActions();
        if (!canManage || !UtilReport.isOpen(item.status)) return actions;
        actions.DISMISS = true;
        if (item.targetType === 'GROUP') {
            actions.DEACTIVATE = item.target.state === 'PUBLIC';
            return actions;
        }
        actions.HIDE = item.target.state === 'PUBLIC';
        actions.KEEP_HIDDEN = UtilReport.isAutoHidden(item.target);
        actions.DELETE = item.target.state !== 'DELETED';
        return actions;
    }

    static actionList(actions: ReportActions): ReportActKind[] {
        return UtilReport.ACT_ORDER.filter((kind) => actions[kind]);
    }

    static hasAction(actions: ReportActions): boolean {
        return Object.values(actions).some(Boolean);
    }

    /** 위험한 처리 — 빨강 단추 */
    static isDanger(kind: ReportActKind): boolean {
        return kind === 'HIDE' || kind === 'DELETE' || kind === 'DEACTIVATE';
    }

    static actIcon(kind: ReportActKind): string {
        const icons: Record<ReportActKind, string> = { HIDE: 'pi pi-eye-slash', KEEP_HIDDEN: 'pi pi-lock', DELETE: 'pi pi-trash', DEACTIVATE: 'pi pi-ban', DISMISS: 'pi pi-times-circle' };
        return icons[kind];
    }

    /** 대상 종류 칩의 색 — 글 파랑 · 댓글 회색 · 소모임 주황 */
    static targetTypeSeverity(type: ReportTargetType): Severity {
        return type === 'POST' ? 'info' : type === 'GROUP' ? 'warn' : 'secondary';
    }

    /**
     * 처리 대화창의 사유 코드 첫 값 — 신고가 가장 많은 사유(같으면 사유 표 차례). 신고 사유가 없으면 null.
     */
    static topReason(item: Pick<ReportCaseItem, 'reasons'>): ReportReasonCode | null {
        const sorted = [...(item.reasons ?? [])].sort((a, b) => b.count - a.count || REASONS.indexOf(a.code) - REASONS.indexOf(b.code));
        return sorted[0]?.code ?? null;
    }

    /** 사유 분포 칩 — 많은 순(같으면 사유 표 차례) */
    static reasonChips(item: Pick<ReportCaseItem, 'reasons'>): { code: ReportReasonCode; count: number }[] {
        return [...(item.reasons ?? [])].filter((r) => r.count > 0).sort((a, b) => b.count - a.count || REASONS.indexOf(a.code) - REASONS.indexOf(b.code));
    }

    /* ── 일괄 ── */

    /** 줄을 고를 수 있나 — 열린 사건(일괄은 무시만) */
    static isSelectable(item: Pick<ReportCaseItem, 'status'>): boolean {
        return UtilReport.isOpen(item.status);
    }

    static bulkRows(result: ReportBulkResult): { id: string; ok: boolean; code: string | null }[] {
        return [...result.done.map((id) => ({ id, ok: true, code: null })), ...result.failed.map((f) => ({ id: f.id, ok: false, code: f.code }))];
    }

    /* ── 서랍 ── */

    static neighbors<T extends { id: string }>(items: readonly T[], id: string | null): { prev: T | null; next: T | null } {
        const at = id === null ? -1 : items.findIndex((item) => item.id === id);
        if (at < 0) return { prev: null, next: null };
        return { prev: items[at - 1] ?? null, next: items[at + 1] ?? null };
    }

    /**
     * 신고 당시 스냅샷과 지금 원문이 다른가 — 제목(글 대상)·본문을 태그·공백을 걷어 내고 비교한다.
     * 스냅샷이 없거나 지금 원문을 볼 수 없으면(지움·파기) 비교하지 않는다(false).
     */
    static snapshotChanged(detail: Pick<ReportCaseDetail, 'content' | 'case'>): boolean {
        const snap = detail.content.snapshot ?? null;
        if (!snap || detail.content.body === null) return false;
        if ((detail.case.targetType === 'POST' || detail.case.targetType === 'GROUP') && snap.title !== null && plain(snap.title) !== plain(detail.case.target.title)) return true;
        return snap.body !== null && plain(snap.body) !== plain(detail.content.body);
    }

    /** 처리 기록 — 최근 것부터(서버 차례와 상관없이) */
    static eventsNewestFirst(events: readonly ReportEvent[]): ReportEvent[] {
        return [...events].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    }

    /**
     * 닫힌 사건의 처리 결과 — 사건 칸(`reasonCode`·`note`·`falseReport`)을 먼저, 없으면 마지막 RESOLVED/DISMISSED 기록의 덧붙임에서 찾는다(계약에 모양이 없어 둘 다 본다).
     */
    static closure(detail: Pick<ReportCaseDetail, 'case' | 'events'>): ReportClosure {
        const c = detail.case;
        const last = UtilReport.eventsNewestFirst(detail.events ?? []).find((ev) => ev.type === 'RESOLVED' || ev.type === 'DISMISSED');
        const d = last?.detail ?? {};
        const code = (c.reasonCode ?? str(d.reasonCode)) as ReportReasonCode | null;
        return {
            action: c.action ?? (str(d.action) as ReportCaseAction | null) ?? (c.status === 'DISMISSED' ? 'DISMISS' : null),
            reasonCode: code && REASONS.includes(code) ? code : null,
            note: c.note ?? str(d.note),
            falseReport: c.falseReport ?? d.falseReport === true,
            by: c.closedBy?.name ?? last?.actor?.name ?? null,
            at: c.closedAt ?? last?.createdAt ?? null,
        };
    }

    /* ── 색·아이콘 ── */

    static statusSeverity(status: ReportCaseStatus): Severity {
        if (status === 'PENDING') return 'danger';
        if (status === 'IN_REVIEW') return 'warn';
        if (status === 'RESOLVED') return 'success';
        return 'secondary';
    }

    static targetStateSeverity(state: ReportTargetState): Severity {
        return state === 'PUBLIC' ? 'success' : state === 'HIDDEN' ? 'warn' : 'danger';
    }

    static targetStateIcon(state: ReportTargetState): string {
        return state === 'PUBLIC' ? 'pi pi-globe' : state === 'HIDDEN' ? 'pi pi-eye-slash' : state === 'INACTIVE' ? 'pi pi-ban' : 'pi pi-trash';
    }

    static eventSeverity(type: string): Severity {
        return EVENT_SEVERITY[type as ReportEventType] ?? 'secondary';
    }

    /** HTML 본문이 비었나 — 글자도 그림·영상·표도 없다 */
    static isHtmlEmpty(html: string): boolean {
        if (/<(?:img|video|audio|iframe|table|hr)\b|data-youtube-id/i.test(html)) return false;
        return html.replace(/<[^>]*>/g, '').replace(/&nbsp;|\s/g, '') === '';
    }
}
