import type { LocationQuery } from 'vue-router';
import type { AppealQuery, AppealSort, AppealStatusFilter } from '~/composables/sanction/api/useAppealApi.types';
import type {
    AppealStatus,
    SanctionKind,
    SanctionLine,
    SanctionOption,
    SanctionPreview,
    SanctionQuery,
    SanctionScope,
    SanctionSearchField,
    SanctionSort,
    SanctionStatus,
} from '~/composables/sanction/api/useSanctionApi.types';
import type { SanctionStepValue } from '~/composables/sanction/useSanctionIssue.types';
import UtilMember from '~/utils/util-member';

/**
 * 제재·이의제기 화면(`/sanctions`·`/appeals`·회원 상세 제재 구획·신고 서랍 제재 곁들이기)의 순수 로직 — 주소 쿼리 ↔ 필터, 검색어·사유 검사, 단계 선택지 판정, 해제 권한, 색·아이콘.
 * 규칙은 계약 `docs/features/member-sanction-contract.md` 「정책」 그대로이고(서버 후보 클래스 `SanctionPolicy`·`AppealService`), 미리 알려 주는 용도다 — **판정은 늘 서버가 한다**.
 * 계산이 낀 문구는 `util-sanction-messages.ts`.
 */

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** `/sanctions` 주소 쿼리의 필터 — `page` 는 1부터 */
export interface SanctionFilters {
    scope: SanctionScope;
    kind: SanctionKind | '';
    field: SanctionSearchField;
    q: string;
    /** 부여일 시작(`YYYY-MM-DD`, 사이트 시간대) */
    from: string;
    to: string;
    /** 그 회원만(회원 상세·신고 서랍에서 넘어옴) */
    userId: string;
    sort: SanctionSort;
    page: number;
    size: number;
}

/** `/appeals` 주소 쿼리의 필터 */
export interface AppealFilters {
    status: AppealStatusFilter;
    sort: AppealSort;
    page: number;
    size: number;
}

/** 검색어 검사 결과 */
export type SanctionSearchKind = 'NONE' | 'TOO_SHORT' | 'NUMBER_INVALID' | 'OK';

/** 사유·답변 글의 문제 */
export type SanctionTextProblem = 'REQUIRED' | 'TOO_LONG';

/** 단계 선택지 하나를 그릴 값 — 누를 수 있나 · 왜 못 누르나 */
export interface SanctionChoice {
    option: SanctionOption;
    enabled: boolean;
    /** 못 누르는 까닭 — `LOWER`(이미 지난 단계) · `BLOCKED`(이 회원에게는 못 줌) · `NO_BAN`(`sanction.ban` 없음) · `NO_MANAGE`(`sanction.manage` 없음) */
    disabledBy: 'LOWER' | 'BLOCKED' | 'NO_BAN' | 'NO_MANAGE' | null;
}

const LADDER: readonly SanctionKind[] = ['WARNING', 'SUSPEND_3', 'SUSPEND_7', 'SUSPEND_30', 'BAN'];
const SCOPES: readonly SanctionScope[] = ['ALL', 'ACTIVE', 'CLOSED'];
const SEARCH_FIELDS: readonly SanctionSearchField[] = ['NICKNAME', 'SANCTION_NO'];
const APPEAL_STATUS_FILTERS: readonly AppealStatusFilter[] = ['PENDING', 'ACCEPTED', 'REJECTED', 'ALL'];
const YMD = /^(\d{4})-(\d{2})-(\d{2})$/;
const NUMERIC_ID = /^\d{1,19}$/;
const NUMBER_INPUT = /^#?\s*(\d+)$/;

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

function lengthOf(text: string): number {
    return Array.from(text.trim()).length;
}

export default class UtilSanction {
    static readonly PAGE_SIZE = 20;
    static readonly PAGE_SIZES: readonly number[] = [10, 20, 50];
    static readonly LADDER = LADDER;
    static readonly SCOPES = SCOPES;
    static readonly SEARCH_FIELDS = SEARCH_FIELDS;
    static readonly APPEAL_STATUS_FILTERS = APPEAL_STATUS_FILTERS;
    static readonly SEARCH_MIN = 2;
    static readonly SEARCH_MAX = 50;
    /** 제재 사유·해제 사유·이의제기 답변 1~500자 */
    static readonly TEXT_MAX = 500;
    /** 정지 전까지 쌓이는 경고 수 */
    static readonly WARNING_LIMIT = 2;
    /** 표의 사유 앞부분 글자 수 */
    static readonly REASON_PREVIEW = 40;

    /* ── `/sanctions` 주소 ↔ 필터 ── */

    static emptyFilters(): SanctionFilters {
        return { scope: 'ALL', kind: '', field: 'NICKNAME', q: '', from: '', to: '', userId: '', sort: 'newest', page: 1, size: UtilSanction.PAGE_SIZE };
    }

    static parse(query: LocationQuery): SanctionFilters {
        const scope = first(query.scope).toUpperCase() as SanctionScope;
        const kind = first(query.kind).toUpperCase() as SanctionKind;
        const field = first(query.field).toUpperCase() as SanctionSearchField;
        const sort = first(query.sort).toLowerCase() as SanctionSort;
        const page = Number.parseInt(first(query.page), 10);
        const size = Number.parseInt(first(query.size), 10);
        const userId = first(query.userId).trim();
        return {
            scope: SCOPES.includes(scope) ? scope : 'ALL',
            kind: LADDER.includes(kind) ? kind : '',
            field: SEARCH_FIELDS.includes(field) ? field : 'NICKNAME',
            q: Array.from(first(query.q).trim()).slice(0, UtilSanction.SEARCH_MAX).join(''),
            from: ymd(first(query.from).trim()),
            to: ymd(first(query.to).trim()),
            userId: NUMERIC_ID.test(userId) ? userId : '',
            sort: sort === 'oldest' ? 'oldest' : 'newest',
            page: Number.isFinite(page) && page >= 1 ? page : 1,
            size: UtilSanction.PAGE_SIZES.includes(size) ? size : UtilSanction.PAGE_SIZE,
        };
    }

    static toQuery(f: SanctionFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.scope !== 'ALL') query.scope = f.scope;
        if (f.kind) query.kind = f.kind;
        if (f.field !== 'NICKNAME') query.field = f.field;
        if (f.q) query.q = f.q;
        if (f.from) query.from = f.from;
        if (f.to) query.to = f.to;
        if (f.userId) query.userId = f.userId;
        if (f.sort !== 'newest') query.sort = f.sort;
        if (f.page > 1) query.page = String(f.page);
        if (f.size !== UtilSanction.PAGE_SIZE) query.size = String(f.size);
        return query;
    }

    /** API 조건 — 쪽은 0부터, 기간은 사이트 시간대 그날 처음·끝 순간(UTC ISO) */
    static toApi(f: SanctionFilters, zoneId: string): SanctionQuery {
        return {
            scope: f.scope,
            kind: f.kind || undefined,
            q: f.q || undefined,
            searchField: f.q ? f.field : undefined,
            from: f.from ? UtilMember.dayStart(f.from, zoneId) : undefined,
            to: f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined,
            userId: f.userId || undefined,
            sort: f.sort,
            page: f.page - 1,
            size: f.size,
        };
    }

    static key(f: SanctionFilters): string {
        return JSON.stringify(UtilSanction.toQuery(f));
    }

    /** 일부를 바꾼다 — 쪽 말고 다른 것을 바꾸면 1쪽으로 */
    static change<T extends { page: number }>(f: T, patch: Partial<T>): T {
        const next = { ...f, ...patch };
        if (patch.page === undefined) next.page = 1;
        return next;
    }

    static hasConditions(f: SanctionFilters): boolean {
        return f.scope !== 'ALL' || f.kind !== '' || f.q !== '' || f.from !== '' || f.to !== '' || f.userId !== '';
    }

    /** 조건만 지운다(정렬·쪽 크기는 그대로) */
    static clear(f: SanctionFilters): SanctionFilters {
        return { ...UtilSanction.emptyFilters(), sort: f.sort, size: f.size };
    }

    /** 주소의 서랍 ID(`sanction`·`appeal`) — 숫자가 아니면 null */
    static parseId(query: LocationQuery, name: 'sanction' | 'appeal'): string | null {
        const id = first(query[name]).trim();
        return NUMERIC_ID.test(id) ? id : null;
    }

    static pageCount(total: number, size: number): number {
        return Math.max(1, Math.ceil(total / Math.max(1, size)));
    }

    /** 닉네임은 2자 이상, 제재 번호는 숫자만(앞의 `#` 는 뗀다) */
    static parseSearch(field: SanctionSearchField, raw: string): { kind: SanctionSearchKind; text: string } {
        const text = raw.trim();
        if (text === '') return { kind: 'NONE', text: '' };
        if (field === 'SANCTION_NO') {
            const m = NUMBER_INPUT.exec(text);
            return m?.[1] ? { kind: 'OK', text: m[1] } : { kind: 'NUMBER_INVALID', text: '' };
        }
        return lengthOf(text) < UtilSanction.SEARCH_MIN ? { kind: 'TOO_SHORT', text: '' } : { kind: 'OK', text };
    }

    /* ── `/appeals` 주소 ↔ 필터 ── */

    static emptyAppealFilters(): AppealFilters {
        return { status: 'PENDING', sort: 'oldest', page: 1, size: UtilSanction.PAGE_SIZE };
    }

    static parseAppeal(query: LocationQuery): AppealFilters {
        const status = first(query.status).toUpperCase() as AppealStatusFilter;
        const sort = first(query.sort).toLowerCase();
        const page = Number.parseInt(first(query.page), 10);
        const size = Number.parseInt(first(query.size), 10);
        return {
            status: APPEAL_STATUS_FILTERS.includes(status) ? status : 'PENDING',
            sort: sort === 'newest' ? 'newest' : 'oldest',
            page: Number.isFinite(page) && page >= 1 ? page : 1,
            size: UtilSanction.PAGE_SIZES.includes(size) ? size : UtilSanction.PAGE_SIZE,
        };
    }

    static appealToQuery(f: AppealFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.status !== 'PENDING') query.status = f.status;
        if (f.sort !== 'oldest') query.sort = f.sort;
        if (f.page > 1) query.page = String(f.page);
        if (f.size !== UtilSanction.PAGE_SIZE) query.size = String(f.size);
        return query;
    }

    static appealToApi(f: AppealFilters): AppealQuery {
        return { status: f.status, sort: f.sort, page: f.page - 1, size: f.size };
    }

    static appealKey(f: AppealFilters): string {
        return JSON.stringify(UtilSanction.appealToQuery(f));
    }

    /* ── 글 검사 ── */

    static textLength(text: string): number {
        return lengthOf(text);
    }

    /** 사유·답변 1~500자(서버 `fields.reason`·`fields.answer` 와 같은 규칙) */
    static textProblem(text: string): SanctionTextProblem | null {
        const n = lengthOf(text);
        if (n === 0) return 'REQUIRED';
        return n > UtilSanction.TEXT_MAX ? 'TOO_LONG' : null;
    }

    /** 표의 사유 앞부분 */
    static reasonPreview(reason: string): string {
        const chars = Array.from(reason.replace(/\s+/g, ' ').trim());
        return chars.length > UtilSanction.REASON_PREVIEW ? `${chars.slice(0, UtilSanction.REASON_PREVIEW).join('')}…` : chars.join('');
    }

    /* ── 단계 선택지 ── */

    /**
     * 선택지마다 누를 수 있는지 — `requiresBan`(영구 정지·건너뛰기)은 `sanction.ban` 만 있으면 되고, 그 밖(경고·기간 정지의 다음 단계)은 `sanction.manage`(서버 `SanctionOperator` 와 같다).
     * `LOWER`·`BLOCKED` 는 늘 못 누른다.
     * @param {SanctionPreview} preview - 현황.
     * @param {{ canManage: boolean; canBan: boolean }} perm - 권한.
     * @returns {SanctionChoice[]} 단계 차례(경고 → 영구 정지).
     */
    static choices(preview: SanctionPreview, perm: { canManage: boolean; canBan: boolean }): SanctionChoice[] {
        return preview.options.map((option) => {
            if (option.state === 'LOWER') return { option, enabled: false, disabledBy: 'LOWER' };
            if (option.state === 'BLOCKED') return { option, enabled: false, disabledBy: 'BLOCKED' };
            if (option.requiresBan) return perm.canBan ? { option, enabled: true, disabledBy: null } : { option, enabled: false, disabledBy: 'NO_BAN' };
            if (!perm.canManage) return { option, enabled: false, disabledBy: 'NO_MANAGE' };
            return { option, enabled: true, disabledBy: null };
        });
    }

    /** 제재를 줄 수 있는 운영자인가 — 부여 입구는 `sanction.manage` 또는 `sanction.ban`(서버 `anyOf`) */
    static canIssue(perm: { canManage: boolean; canBan: boolean }): boolean {
        return perm.canManage || perm.canBan;
    }

    /** 처음 골라 둘 단계 — 누를 수 있는 다음 단계(`NEXT`), 없으면 null(운영자가 고른다) */
    static defaultKind(choices: readonly SanctionChoice[]): SanctionKind | null {
        return choices.find((c) => c.enabled && c.option.state === 'NEXT')?.option.kind ?? null;
    }

    /** 고른 단계가 건너뛰기인가 */
    static isSkip(preview: SanctionPreview | null, kind: SanctionKind | null): boolean {
        return Boolean(preview && kind && preview.options.find((o) => o.kind === kind)?.state === 'SKIP');
    }

    /** 단계 고르개의 빈 값 — 누를 수 있는 다음 단계를 골라 둔다 */
    static emptyStep(preview: SanctionPreview | null, perm: { canManage: boolean; canBan: boolean }): SanctionStepValue {
        return { kind: preview ? UtilSanction.defaultKind(UtilSanction.choices(preview, perm)) : null, skipConfirmed: false, confirmNickname: '' };
    }

    /**
     * 고른 단계를 보낼 수 있나 — 단계를 골랐고(누를 수 있는 것), 건너뛰기면 근거 확인, 영구 정지면 닉네임이 같다(서버 `fields.kind|skipStep|confirmNickname` 와 같은 규칙).
     * @returns {boolean} 보낼 수 있으면 true.
     */
    static stepReady(value: SanctionStepValue, preview: SanctionPreview | null, perm: { canManage: boolean; canBan: boolean }): boolean {
        if (!preview || !value.kind) return false;
        const choice = UtilSanction.choices(preview, perm).find((c) => c.option.kind === value.kind);
        if (!choice?.enabled) return false;
        if (choice.option.state === 'SKIP' && !value.skipConfirmed) return false;
        return value.kind !== 'BAN' || value.confirmNickname.trim() === preview.user.nickname;
    }

    /** 보낼 단계 값 — 건너뛰기 여부는 서버 선택지 상태로, 닉네임은 영구 정지일 때만 */
    static stepRequest(value: SanctionStepValue, preview: SanctionPreview): { kind: SanctionKind; skipStep: boolean; confirmNickname: string | null } {
        const kind = value.kind as SanctionKind;
        return { kind, skipStep: UtilSanction.isSkip(preview, kind), confirmNickname: kind === 'BAN' ? value.confirmNickname.trim() : null };
    }

    /** 이 회원에게 줄 수 있는 단계가 하나라도 있나(권한과 상관없이) */
    static hasAnyOption(preview: SanctionPreview): boolean {
        return preview.options.some((o) => o.state === 'NEXT' || o.state === 'SKIP');
    }

    /* ── 해제 ── */

    /** 진행 중인 정지·영구 정지인가(해제할 수 있는 상태) */
    static isRunning(line: Pick<SanctionLine, 'kind' | 'status'>): boolean {
        return line.kind !== 'WARNING' && line.status === 'ACTIVE';
    }

    /** 해제 단추를 보일까 — 기간 정지 `sanction.manage`, 영구 정지 `sanction.ban` */
    static canLift(line: Pick<SanctionLine, 'kind' | 'status'>, perm: { canManage: boolean; canBan: boolean }): boolean {
        if (!UtilSanction.isRunning(line)) return false;
        return line.kind === 'BAN' ? perm.canBan : perm.canManage;
    }

    /** 이력에서 진행 중인 정지·영구 정지 */
    static running(lines: readonly SanctionLine[]): SanctionLine | null {
        return lines.find((l) => UtilSanction.isRunning(l)) ?? null;
    }

    /* ── 이의제기 ── */

    /** 대기 시간(밀리초) — 처리된 것은 접수 ~ 처리, 대기 중은 접수 ~ 지금 */
    static waitMs(createdAt: string, answeredAt: string | null, now: number = Date.now()): number {
        const start = Date.parse(createdAt);
        const end = answeredAt ? Date.parse(answeredAt) : now;
        return Number.isNaN(start) || Number.isNaN(end) ? 0 : Math.max(0, end - start);
    }

    /** 대기가 길다(하루 넘음) — 표에서 강조 */
    static isLongWait(createdAt: string, answeredAt: string | null, now: number = Date.now()): boolean {
        return answeredAt === null && UtilSanction.waitMs(createdAt, null, now) >= 24 * 60 * 60 * 1000;
    }

    /* ── 색·아이콘 ── */

    static kindSeverity(kind: SanctionKind | string): Severity {
        if (kind === 'WARNING') return 'warn';
        if (kind === 'BAN') return 'contrast';
        return 'danger';
    }

    static kindIcon(kind: SanctionKind | string): string {
        if (kind === 'WARNING') return 'pi pi-exclamation-triangle';
        if (kind === 'BAN') return 'pi pi-ban';
        return 'pi pi-clock';
    }

    /** 상태 색 — 진행 중 정지 빨강 · 유효한 경고 노랑 · 해제 초록 · 끝남·무효 회색 */
    static statusSeverity(status: SanctionStatus | string, kind: SanctionKind | string = 'SUSPEND_3'): Severity {
        if (status === 'ACTIVE') return kind === 'WARNING' ? 'warn' : 'danger';
        if (status === 'LIFTED') return 'success';
        return 'secondary';
    }

    static statusIcon(status: SanctionStatus | string): string {
        if (status === 'ACTIVE') return 'pi pi-circle-fill';
        if (status === 'LIFTED') return 'pi pi-lock-open';
        if (status === 'VOIDED') return 'pi pi-undo';
        return 'pi pi-check-circle';
    }

    static appealSeverity(status: AppealStatus | string): Severity {
        if (status === 'PENDING') return 'warn';
        if (status === 'ACCEPTED') return 'success';
        return 'secondary';
    }

    static appealIcon(status: AppealStatus | string): string {
        if (status === 'PENDING') return 'pi pi-hourglass';
        if (status === 'ACCEPTED') return 'pi pi-check';
        return 'pi pi-times';
    }

    static neighbors<T extends { id: string }>(items: readonly T[], id: string | null): { prev: T | null; next: T | null } {
        const at = id === null ? -1 : items.findIndex((item) => item.id === id);
        if (at < 0) return { prev: null, next: null };
        return { prev: items[at - 1] ?? null, next: items[at + 1] ?? null };
    }
}
