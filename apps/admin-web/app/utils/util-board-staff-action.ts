import type { LocationQuery } from 'vue-router';
import type {
    AdminBoardStaffAction,
    BoardStaffActionKind,
    BoardStaffActionQuery,
    BoardStaffActionReasonCode,
    BoardStaffActionRestoredFilter,
    BoardStaffActionTargetState,
} from '~/composables/sanction/api/useBoardStaffActionApi.types';
import UtilMember from '~/utils/util-member';
import UtilSanction from '~/utils/util-sanction';

/**
 * 스텝 처리 감독 화면(`/staff-actions`)의 순수 로직 — 주소 쿼리 ↔ 필터, 되돌리기 판정, 색·아이콘. 계약 `docs/features/board-staff-action-contract.md` 정책 6·「API > 관리자」.
 * 미리 알려 주는 용도다 — **판정은 늘 서버가 한다**(서버 `BoardStaffActionService` 후보). 기간·쪽 크기·이웃 찾기는 사이트 제재(`UtilSanction`)를 그대로 쓴다.
 */

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** 주소 쿼리의 필터 — `page` 는 1부터 */
export interface BoardStaffActionFilters {
    /** 게시판 ID */
    board: string;
    kind: BoardStaffActionKind | '';
    restored: BoardStaffActionRestoredFilter;
    /** 처리자 닉네임 */
    q: string;
    from: string;
    to: string;
    page: number;
    size: number;
}

const KINDS: readonly BoardStaffActionKind[] = ['POST_HIDE', 'POST_DELETE', 'COMMENT_DELETE'];
const RESTORED: readonly BoardStaffActionRestoredFilter[] = ['ALL', 'OPEN', 'RESTORED'];
const REASONS: readonly BoardStaffActionReasonCode[] = ['SPAM', 'ABUSE', 'INVESTMENT', 'OFF_TOPIC'];
const STATES: readonly BoardStaffActionTargetState[] = ['PUBLIC', 'HIDDEN', 'DELETED'];
const NUMERIC_ID = /^\d{1,19}$/;

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

export default class UtilBoardStaffAction {
    static readonly KINDS = KINDS;
    static readonly RESTORED = RESTORED;
    static readonly REASONS = REASONS;
    static readonly STATES = STATES;
    /** 되돌리기 사유 1~200자(정책 6, 서버 `fields.reason`) */
    static readonly REASON_MIN = 1;
    static readonly REASON_MAX = 200;
    /** 처리자 닉네임 검색 최대 글자 수 */
    static readonly SEARCH_MAX = UtilSanction.SEARCH_MAX;

    static emptyFilters(): BoardStaffActionFilters {
        return { board: '', kind: '', restored: 'ALL', q: '', from: '', to: '', page: 1, size: UtilSanction.PAGE_SIZE };
    }

    /** 검색어·기간·쪽은 사이트 제재 필터 해석(`UtilSanction.parse`)을 쓰고 게시판·종류·되돌림만 따로 읽는다 */
    static parse(query: LocationQuery): BoardStaffActionFilters {
        const base = UtilSanction.parse(query);
        const kind = first(query.kind).toUpperCase() as BoardStaffActionKind;
        const restored = first(query.restored).toUpperCase() as BoardStaffActionRestoredFilter;
        const board = first(query.board).trim();
        return {
            board: NUMERIC_ID.test(board) ? board : '',
            kind: KINDS.includes(kind) ? kind : '',
            restored: RESTORED.includes(restored) ? restored : 'ALL',
            q: base.q,
            from: base.from,
            to: base.to,
            page: base.page,
            size: base.size,
        };
    }

    static toQuery(f: BoardStaffActionFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.board) query.board = f.board;
        if (f.kind) query.kind = f.kind;
        if (f.restored !== 'ALL') query.restored = f.restored;
        if (f.q) query.q = f.q;
        if (f.from) query.from = f.from;
        if (f.to) query.to = f.to;
        if (f.page > 1) query.page = String(f.page);
        if (f.size !== UtilSanction.PAGE_SIZE) query.size = String(f.size);
        return query;
    }

    /** 서버 조건 — 기간은 사이트 시간대 그날 시작·끝을 UTC ISO 로, 쪽은 0부터 */
    static toApi(f: BoardStaffActionFilters, zoneId: string): BoardStaffActionQuery {
        return {
            board: f.board || undefined,
            kind: f.kind || 'ALL',
            restored: f.restored,
            q: f.q || undefined,
            from: f.from ? UtilMember.dayStart(f.from, zoneId) : undefined,
            to: f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined,
            page: f.page - 1,
            size: f.size,
        };
    }

    static key(f: BoardStaffActionFilters): string {
        return JSON.stringify(UtilBoardStaffAction.toQuery(f));
    }

    static hasConditions(f: BoardStaffActionFilters): boolean {
        return f.board !== '' || f.kind !== '' || f.restored !== 'ALL' || f.q !== '' || f.from !== '' || f.to !== '';
    }

    static clear(f: BoardStaffActionFilters): BoardStaffActionFilters {
        return { ...UtilBoardStaffAction.emptyFilters(), size: f.size };
    }

    /** 주소의 서랍 ID(`action`) — 숫자가 아니면 null */
    static parseId(query: LocationQuery): string | null {
        const id = first(query.action).trim();
        return NUMERIC_ID.test(id) ? id : null;
    }

    /** 검색어 정리 — 앞뒤 공백을 지우고 최대 글자 수로 자른다(최소 길이 검사는 서버 `fields.q`) */
    static cleanSearch(raw: string): string {
        return Array.from(raw.trim()).slice(0, UtilBoardStaffAction.SEARCH_MAX).join('');
    }

    /* ── 판정 ── */

    /** [되돌리기] — 아직 되돌리지 않은 처리 + `keeper-action.restore`(글 상태가 달라졌는지는 서버가 `POST_STATE_INVALID` 로 판정) */
    static canRestore(action: Pick<AdminBoardStaffAction, 'restored'>, canRestorePermission: boolean): boolean {
        return canRestorePermission && action.restored === null;
    }

    /** 사유 글자 수(앞뒤 공백 제외) */
    static reasonLength(text: string): number {
        return Array.from(text.trim()).length;
    }

    /* ── 색·아이콘 ── */

    static kindSeverity(kind: BoardStaffActionKind | string): Severity {
        return kind === 'POST_HIDE' ? 'warn' : 'danger';
    }

    static kindIcon(kind: BoardStaffActionKind | string): string {
        if (kind === 'POST_HIDE') return 'pi pi-eye-slash';
        if (kind === 'COMMENT_DELETE') return 'pi pi-comment';
        return 'pi pi-trash';
    }

    static stateSeverity(state: BoardStaffActionTargetState | string): Severity {
        return state === 'PUBLIC' ? 'success' : state === 'HIDDEN' ? 'warn' : 'danger';
    }

    static stateIcon(state: BoardStaffActionTargetState | string): string {
        return state === 'PUBLIC' ? 'pi pi-eye' : state === 'HIDDEN' ? 'pi pi-eye-slash' : 'pi pi-trash';
    }
}
