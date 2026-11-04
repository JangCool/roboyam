import type { BoardStaffActionFilter, BoardStaffActionReasonCode, BoardStaffTools } from '~/composables/board/api/useBoardStaffApi.types';
import { BOARD_STAFF_ACTION_REASONS } from '~/composables/board/api/useBoardStaffApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import type { SanctionTone } from '~/utils/util-sanction';

/**
 * 게시판 스텝 처리 규칙 — 글 숨기기·글 삭제·댓글 삭제의 메뉴 판정·결과 갈래, 처리 내역 거르기·되돌리기 사유 검사(`docs/features/board-staff-action-contract.md`). 상태도 요청도 없다.
 * 판정은 서버다(공지·고정 글·같은 게시판 매니저·스텝의 글·댓글은 서버가 `409 BOARD_STAFF_TARGET_PROTECTED` 로 막는다) — 화면은 미리 감추기만 한다. 문구는 `UtilBoardStaffActionMessages`.
 */

/** 처리 대화창 제출 결과의 갈래(오류 코드로 가른다) */
export type BoardStaffActionOutcome =
    | 'LOGIN'
    | 'DAILY_LIMIT'
    | 'TARGET_PROTECTED'
    /** 이미 숨김·삭제(`POST_STATE_INVALID`·`COMMENT_STATE_INVALID`) 또는 그새 없어짐(`POST_NOT_FOUND`·`COMMENT_NOT_FOUND`) — 다시 읽어 맞춘다 */
    | 'STALE'
    | 'FORBIDDEN'
    | 'VALIDATION'
    | 'OTHER';

/** 되돌리기 거절 중 닫고 목록을 다시 받을 것 */
const RESTORE_STALE: ReadonlySet<string> = new Set(['BOARD_STAFF_ACTION_RESTORED', 'POST_STATE_INVALID', 'POST_PURGED', 'COMMENT_STATE_INVALID', 'BOARD_STAFF_FORBIDDEN']);

/** 널 문자를 빼고 앞뒤 공백을 뗀 글 */
function cleanText(text: string): string {
    // eslint-disable-next-line no-control-regex
    return text.replace(/\u0000/g, '').trim();
}

export default class UtilBoardStaffAction {
    /** 되돌리기 사유 최대 글자 수(1~200자) */
    static readonly RESTORE_REASON_MAX = 200;

    /**
     * 처리 내역 화면 주소
     * @param {string} slug - 게시판 slug.
     * @returns {string} `/boards/{slug}/actions`.
     */
    static listPath(slug: string): string {
        return `/boards/${encodeURIComponent(slug)}/actions`;
    }

    /**
     * 글 처리(숨기기·삭제)인가 — 글 한도를 쓴다
     * @param {string} kind - 처리 종류.
     * @returns {boolean} 글 처리면 true.
     */
    static isPostKind(kind: string): boolean {
        return kind === 'POST_HIDE' || kind === 'POST_DELETE';
    }

    /**
     * 글 ⋯ 에 보일 처리 — 본인 글·소모임 글·공지·고정 글·같은 게시판 매니저·스텝의 글(`staffProtected`)에는 없다
     * @param {BoardStaffTools | null} tools - 게시판의 `viewer.staffTools`.
     * @param {{ mine: boolean; group?: unknown; staffProtected?: boolean }} post - 글.
     * @returns {Array<'POST_HIDE' | 'POST_DELETE'>} 숨기기·삭제 중 할 수 있는 것.
     */
    static postKinds(tools: BoardStaffTools | null, post: { mine: boolean; group?: unknown; staffProtected?: boolean }): Array<'POST_HIDE' | 'POST_DELETE'> {
        if (!tools || post.mine || post.group || post.staffProtected) return [];
        const kinds: Array<'POST_HIDE' | 'POST_DELETE'> = [];
        if (tools.abilities.postHide) kinds.push('POST_HIDE');
        if (tools.abilities.postDelete) kinds.push('POST_DELETE');
        return kinds;
    }

    /**
     * 댓글 ⋯ 「댓글 삭제」를 보일까 — 본인 댓글·지운 댓글·같은 게시판 매니저·스텝의 댓글(`staffProtected`) 제외. 숨긴 댓글(`HIDDEN`)은 자리만 그려 ⋯ 가 없고 서버도 `COMMENT_STATE_INVALID` 로 막으므로 보이는 댓글(`VISIBLE`)만
     * @param {boolean} enabled - 보는 사람에게 `abilities.commentDelete` 가 있다.
     * @param {{ mine: boolean; state: string; staffProtected?: boolean }} comment - 댓글.
     * @returns {boolean} 보이면 true.
     */
    static commentDeletable(enabled: boolean, comment: { mine: boolean; state: string; staffProtected?: boolean }): boolean {
        return enabled && !comment.mine && !comment.staffProtected && comment.state === 'VISIBLE';
    }

    /**
     * 처리 대화창에 보일 댓글 앞부분 — 에디터 댓글(HTML)은 글자만, 공백을 하나로, 60자에서 자른다
     * @param {string | null} content - 댓글 본문.
     * @param {string | undefined} format - `TEXT`·`HTML`.
     * @returns {string} 앞부분.
     */
    static commentExcerpt(content: string | null, format: string | undefined): string {
        let text = content ?? '';
        if (format === 'HTML') {
            text = typeof DOMParser === 'undefined' ? text.replace(/<[^>]*>/g, ' ') : (new DOMParser().parseFromString(text, 'text/html').body.textContent ?? '');
        }
        const chars = Array.from(text.replace(/\s+/g, ' ').trim());
        return chars.length > 60 ? `${chars.slice(0, 60).join('')}…` : chars.join('');
    }

    /**
     * 알려진 사유 코드인가
     * @param {unknown} value - 값.
     * @returns {boolean} 네 코드 중 하나면 true.
     */
    static isReasonCode(value: unknown): value is BoardStaffActionReasonCode {
        return typeof value === 'string' && (BOARD_STAFF_ACTION_REASONS as readonly string[]).includes(value);
    }

    /**
     * 주소의 종류 거르기 값 — 모르면 전체
     * @param {unknown} value - 쿼리 값.
     * @returns {BoardStaffActionFilter} 거르기.
     */
    static filterOf(value: unknown): BoardStaffActionFilter {
        const raw = Array.isArray(value) ? value[0] : value;
        return raw === 'POST' || raw === 'COMMENT' ? raw : 'ALL';
    }

    /**
     * 종류 태그 색 — 글 숨기기 노랑, 삭제 빨강
     * @param {string} kind - 처리 종류.
     * @returns {SanctionTone} 색.
     */
    static kindTone(kind: string): SanctionTone {
        return kind === 'POST_HIDE' ? 'amber' : 'red';
    }

    /**
     * 되돌리기 사유 검사 — 1~200자
     * @param {string} text - 사유.
     * @returns {'EMPTY' | 'TOO_LONG' | null} 문제. 괜찮으면 null.
     */
    static restoreReasonProblem(text: string): 'EMPTY' | 'TOO_LONG' | null {
        const n = UtilBoardStaffAction.textLength(text);
        if (n === 0) return 'EMPTY';
        if (n > UtilBoardStaffAction.RESTORE_REASON_MAX) return 'TOO_LONG';
        return null;
    }

    /**
     * 글자 수(앞뒤 공백 뺀 유니코드 글자)
     * @param {string} text - 글.
     * @returns {number} 글자 수.
     */
    static textLength(text: string): number {
        return Array.from(cleanText(text)).length;
    }

    /**
     * 보낼 사유 — 앞뒤 공백을 뗀 값
     * @param {string} text - 사유.
     * @returns {string} 보낼 값.
     */
    static textBody(text: string): string {
        return cleanText(text);
    }

    /**
     * 처리 제출 실패의 갈래 — 문구가 아니라 코드로
     * @param {unknown} e - 잡은 오류.
     * @returns {BoardStaffActionOutcome} 갈래.
     */
    static outcomeOf(e: unknown): BoardStaffActionOutcome {
        if (!(e instanceof ApiError)) return 'OTHER';
        if (e.code === 'UNAUTHENTICATED' || e.status === 401) return 'LOGIN';
        if (e.code === 'BOARD_STAFF_DAILY_LIMIT') return 'DAILY_LIMIT';
        if (e.code === 'BOARD_STAFF_TARGET_PROTECTED') return 'TARGET_PROTECTED';
        if (e.code === 'POST_STATE_INVALID' || e.code === 'COMMENT_STATE_INVALID' || e.code === 'POST_NOT_FOUND' || e.code === 'COMMENT_NOT_FOUND') return 'STALE';
        if (e.code === 'VALIDATION') return 'VALIDATION';
        if (e.status === 403) return 'FORBIDDEN';
        return 'OTHER';
    }

    /**
     * 한도 초과 오류의 한도 — `kind`·`limit`·`used`·`resetsAt` extras
     * @param {unknown} e - 잡은 오류.
     * @returns {{ kind: 'POST' | 'COMMENT' | null; limit: number; used: number; resetsAt: string | null } | null} 값. 읽을 수 없으면 null.
     */
    static limitOf(e: unknown): { kind: 'POST' | 'COMMENT' | null; limit: number; used: number; resetsAt: string | null } | null {
        if (!(e instanceof ApiError) || e.code !== 'BOARD_STAFF_DAILY_LIMIT') return null;
        const limit = Number(e.extraText('limit'));
        const used = Number(e.extraText('used'));
        if (!Number.isFinite(limit) || !Number.isFinite(used)) return null;
        const kind = e.extraText('kind');
        return { kind: kind === 'POST' || kind === 'COMMENT' ? kind : null, limit, used, resetsAt: e.extraText('resetsAt') ?? null };
    }

    /**
     * 되돌리기 거절 중 대화창을 닫고 목록을 다시 받을 것인가(이미 되돌림·글 상태가 달라짐·파기·권한 바뀜·없어짐)
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 그러면 true.
     */
    static restoreStale(e: unknown): boolean {
        return e instanceof ApiError && (RESTORE_STALE.has(e.code) || e.status === 403 || e.status === 404);
    }
}
