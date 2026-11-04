import type { LocationQuery } from 'vue-router';
import type { BoardSanctionKind, BoardSanctionLine, BoardSanctionQuery, BoardSanctionReasonCode } from '~/composables/sanction/api/useBoardSanctionApi.types';
import type { BoardSanctionReviewDetail, BoardSanctionReviewQuery, BoardSanctionReviewResult, BoardSanctionReviewStatus } from '~/composables/sanction/api/useBoardSanctionReviewApi.types';
import type { SanctionScope, SanctionSearchField, SanctionSort } from '~/composables/sanction/api/useSanctionApi.types';
import UtilMember from '~/utils/util-member';
import UtilSanction from '~/utils/util-sanction';

/**
 * 게시판 제재 화면(`/board-sanctions` 이력·반복 검토 탭, 회원 상세 「게시판 제재」 구획, 이의제기의 게시판 제재)의 순수 로직 — 주소 쿼리 ↔ 필터, 해제·무효·닫기 판정, 색·아이콘.
 * 규칙은 계약 `docs/features/board-sanction-contract.md` 「규칙」 그대로(서버 후보 클래스 `BoardSanctionPolicy`·`BoardSanctionReviewService`)이고 미리 알려 주는 용도다 — **판정은 늘 서버가 한다**.
 * 검색어 검사·쪽 계산·이웃 찾기는 사이트 제재(`UtilSanction`)를 그대로 쓴다. 계산이 낀 문구는 `util-board-sanction-messages.ts`.
 */

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** 화면 탭 — 이력(`/board-sanctions`) · 반복 검토(`/board-sanctions/reviews`) */
export type BoardSanctionTab = 'history' | 'reviews';

/** 이력 탭 주소 쿼리의 필터 — `page` 는 1부터 */
export interface BoardSanctionFilters {
    scope: SanctionScope;
    /** 게시판 ID */
    board: string;
    kind: BoardSanctionKind | '';
    field: SanctionSearchField;
    q: string;
    from: string;
    to: string;
    userId: string;
    sort: SanctionSort;
    page: number;
    size: number;
}

/** 반복 검토 탭(`/board-sanctions/reviews`) 주소 쿼리의 필터 */
export interface BoardSanctionReviewFilters {
    status: BoardSanctionReviewStatus;
    page: number;
    size: number;
}

const KINDS: readonly BoardSanctionKind[] = ['WARNING', 'RESTRICT_1', 'RESTRICT_3', 'RESTRICT_7'];
const REASONS: readonly BoardSanctionReasonCode[] = ['SPAM', 'ABUSE', 'INVESTMENT', 'OFF_TOPIC', 'OTHER'];
const REVIEW_STATUSES: readonly BoardSanctionReviewStatus[] = ['OPEN', 'CLOSED'];
const REVIEW_RESULTS: readonly BoardSanctionReviewResult[] = ['KEPT', 'SITE_SANCTIONED', 'VOIDED'];
const NUMERIC_ID = /^\d{1,19}$/;

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

export default class UtilBoardSanction {
    static readonly KINDS = KINDS;
    static readonly REASONS = REASONS;
    static readonly REVIEW_STATUSES = REVIEW_STATUSES;
    static readonly REVIEW_RESULTS = REVIEW_RESULTS;
    /** 반복 검토 닫기 메모 0~200자 */
    static readonly MEMO_MAX = 200;
    /** 반복 검토가 열리는 건수(같은 회원 · 같은 게시판 · 최근 7일) */
    static readonly REVIEW_THRESHOLD = 3;
    /** 회원 상세 구획의 최근 이력 줄 수 */
    static readonly MEMBER_RECENT = 5;

    /* ── 이력 탭 주소 ↔ 필터 ── */

    static emptyFilters(): BoardSanctionFilters {
        return { scope: 'ALL', board: '', kind: '', field: 'NICKNAME', q: '', from: '', to: '', userId: '', sort: 'newest', page: 1, size: UtilSanction.PAGE_SIZE };
    }

    /** 사이트 제재 필터 해석(`UtilSanction.parse`)을 쓰고 게시판·게시판 제재 단계만 따로 읽는다 */
    static parse(query: LocationQuery): BoardSanctionFilters {
        const base = UtilSanction.parse(query);
        const kind = first(query.kind).toUpperCase() as BoardSanctionKind;
        const board = first(query.board).trim();
        return {
            scope: base.scope,
            board: NUMERIC_ID.test(board) ? board : '',
            kind: KINDS.includes(kind) ? kind : '',
            field: base.field,
            q: base.q,
            from: base.from,
            to: base.to,
            userId: base.userId,
            sort: base.sort,
            page: base.page,
            size: base.size,
        };
    }

    static toQuery(f: BoardSanctionFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.scope !== 'ALL') query.scope = f.scope;
        if (f.board) query.board = f.board;
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

    static toApi(f: BoardSanctionFilters, zoneId: string): BoardSanctionQuery {
        return {
            scope: f.scope,
            boardId: f.board || undefined,
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

    static key(f: BoardSanctionFilters): string {
        return JSON.stringify(UtilBoardSanction.toQuery(f));
    }

    static hasConditions(f: BoardSanctionFilters): boolean {
        return f.scope !== 'ALL' || f.board !== '' || f.kind !== '' || f.q !== '' || f.from !== '' || f.to !== '' || f.userId !== '';
    }

    static clear(f: BoardSanctionFilters): BoardSanctionFilters {
        return { ...UtilBoardSanction.emptyFilters(), sort: f.sort, size: f.size };
    }

    /* ── 반복 검토 탭 주소 ↔ 필터 ── */

    static parseReview(query: LocationQuery): BoardSanctionReviewFilters {
        const status = first(query.status).toUpperCase() as BoardSanctionReviewStatus;
        const pageNo = Number.parseInt(first(query.page), 10);
        const size = Number.parseInt(first(query.size), 10);
        return {
            status: REVIEW_STATUSES.includes(status) ? status : 'OPEN',
            page: Number.isFinite(pageNo) && pageNo >= 1 ? pageNo : 1,
            size: UtilSanction.PAGE_SIZES.includes(size) ? size : UtilSanction.PAGE_SIZE,
        };
    }

    static reviewToQuery(f: BoardSanctionReviewFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.status !== 'OPEN') query.status = f.status;
        if (f.page > 1) query.page = String(f.page);
        if (f.size !== UtilSanction.PAGE_SIZE) query.size = String(f.size);
        return query;
    }

    static reviewToApi(f: BoardSanctionReviewFilters): BoardSanctionReviewQuery {
        return { status: f.status, page: f.page - 1, size: f.size };
    }

    static reviewKey(f: BoardSanctionReviewFilters): string {
        return JSON.stringify(UtilBoardSanction.reviewToQuery(f));
    }

    /** 주소의 서랍 ID(`boardSanction`·`review`) — 숫자가 아니면 null */
    static parseId(query: LocationQuery, name: 'boardSanction' | 'review'): string | null {
        const id = first(query[name]).trim();
        return NUMERIC_ID.test(id) ? id : null;
    }

    /* ── 판정 ── */

    /** 진행 중인 쓰기 제한인가(해제할 수 있는 상태) */
    static isRunning(line: Pick<BoardSanctionLine, 'kind' | 'status'>): boolean {
        return line.kind !== 'WARNING' && line.status === 'ACTIVE';
    }

    /** [해제] — 진행 중 쓰기 제한 + `sanction.manage` */
    static canLift(line: Pick<BoardSanctionLine, 'kind' | 'status'>, canManage: boolean): boolean {
        return canManage && UtilBoardSanction.isRunning(line);
    }

    /** [무효] — 무효가 아닌 제재 + `sanction.manage`(해제·끝난 제재도 무효로 할 수 있다) */
    static canVoid(line: Pick<BoardSanctionLine, 'status'>, canManage: boolean): boolean {
        return canManage && line.status !== 'VOIDED';
    }

    /** 반복 검토에서 「게시판 제재 무효」로 고를 수 있는 제재 — 이 검토가 센 것 중 무효가 아닌 것 */
    static voidable(detail: Pick<BoardSanctionReviewDetail, 'sanctions' | 'reviewSanctionIds'>): BoardSanctionLine[] {
        return detail.sanctions.filter((l) => detail.reviewSanctionIds.includes(l.id) && l.status !== 'VOIDED');
    }

    /** 「여러 게시판」 배지 — 최근 7일 안 2곳 이상 */
    static isMultiBoard(recentBoardCount: number): boolean {
        return recentBoardCount >= 2;
    }

    static memoLength(text: string): number {
        return Array.from(text.trim()).length;
    }

    static memoTooLong(text: string): boolean {
        return UtilBoardSanction.memoLength(text) > UtilBoardSanction.MEMO_MAX;
    }

    /* ── 색·아이콘 ── */

    static kindSeverity(kind: BoardSanctionKind | string): Severity {
        return kind === 'WARNING' ? 'warn' : 'danger';
    }

    static kindIcon(kind: BoardSanctionKind | string): string {
        return kind === 'WARNING' ? 'pi pi-exclamation-triangle' : 'pi pi-pencil';
    }

    static reviewStatusSeverity(status: BoardSanctionReviewStatus | string): Severity {
        return status === 'OPEN' ? 'warn' : 'secondary';
    }

    static reviewStatusIcon(status: BoardSanctionReviewStatus | string): string {
        return status === 'OPEN' ? 'pi pi-eye' : 'pi pi-check-circle';
    }

    static reviewResultSeverity(result: BoardSanctionReviewResult | string): Severity {
        if (result === 'SITE_SANCTIONED') return 'danger';
        if (result === 'VOIDED') return 'info';
        return 'secondary';
    }

    static reviewResultIcon(result: BoardSanctionReviewResult | string): string {
        if (result === 'SITE_SANCTIONED') return 'pi pi-ban';
        if (result === 'VOIDED') return 'pi pi-undo';
        return 'pi pi-check';
    }
}
