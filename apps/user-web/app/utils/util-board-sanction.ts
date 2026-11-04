import type { BoardSanctionKind, BoardSanctionReasonCode, BoardSanctionScope, BoardWriteRestriction } from '~/composables/board/api/useBoardStaffApi.types';
import { BOARD_SANCTION_KINDS, BOARD_SANCTION_REASONS } from '~/composables/board/api/useBoardStaffApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import type { SanctionTone } from '~/utils/util-sanction';

/**
 * 게시판 제재 규칙 — 매니저·스텝의 제재 대화창 검사·권장 단계, 제재 내역 거르기, 쓰기 제한 안내(`docs/features/board-sanction-contract.md` 「규칙」). 상태도 요청도 없다.
 * 판정은 서버다(후보 서버 클래스 `BoardStaffSanctionService`·`BoardSanctionPolicy`) — 화면은 미리 알려 주기만 한다. 문구는 `UtilBoardSanctionMessages`.
 */

/** 제재 대화창 덧붙임 글의 문제 — 너무 김 · 기타인데 5자 미만 */
export type BoardSanctionTextProblem = 'TOO_LONG' | 'OTHER_TOO_SHORT';

/** 제재 대화창 제출 결과의 갈래(오류 코드로 가른다) */
export type BoardSanctionOutcome = 'LOGIN' | 'DAILY_LIMIT' | 'TARGET_PROTECTED' | 'ALREADY_RESTRICTED' | 'VALIDATION' | 'FORBIDDEN' | 'OTHER';

const RESTRICT_DAYS: Readonly<Record<string, number>> = { RESTRICT_1: 1, RESTRICT_3: 3, RESTRICT_7: 7 };

/** 널 문자를 빼고 앞뒤 공백을 뗀 글 */
function cleanText(text: string): string {
    // eslint-disable-next-line no-control-regex
    return text.replace(/\u0000/g, '').trim();
}

export default class UtilBoardSanction {
    /** 덧붙임 최대 글자 수 */
    static readonly TEXT_MAX = 200;
    /** 사유가 기타(`OTHER`)일 때 덧붙임 최소 글자 수 */
    static readonly OTHER_TEXT_MIN = 5;
    /** 해제 사유 최대 글자 수(결정 11 — 1~200자) */
    static readonly LIFT_REASON_MAX = 200;
    /**
     * 제재 내역 화면 주소
     * @param {string} slug - 게시판 slug.
     * @returns {string} `/boards/{slug}/sanctions`.
     */
    static listPath(slug: string): string {
        return `/boards/${encodeURIComponent(slug)}/sanctions`;
    }

    /**
     * 쓰기 제한 단계인가
     * @param {string} kind - 단계.
     * @returns {boolean} 쓰기 제한(1·3·7일)이면 true.
     */
    static isRestrict(kind: string): boolean {
        return kind in RESTRICT_DAYS;
    }

    /**
     * 쓰기 제한 날 수
     * @param {string} kind - 단계.
     * @returns {number | null} 1·3·7, 경고·모르는 값은 null.
     */
    static daysOf(kind: string): number | null {
        return RESTRICT_DAYS[kind] ?? null;
    }

    /**
     * 권장 단계(서버 규칙과 같다 — 계약 결정 2) — 유효한(무효 아닌) 제재 수 0 → 경고, 1 → 1일, 2 → 3일, 3 이상 → 7일
     * @param {number} validCount - 그 게시판에서 받은 유효한 제재 수(경고 + 쓰기 제한).
     * @returns {BoardSanctionKind} 권장 단계.
     */
    static recommendedOf(validCount: number): BoardSanctionKind {
        const n = Number.isFinite(validCount) ? Math.max(0, Math.floor(validCount)) : 0;
        return BOARD_SANCTION_KINDS[Math.min(n, BOARD_SANCTION_KINDS.length - 1)]!;
    }

    /**
     * 고를 수 있는 단계인가 — 진행 중인 쓰기 제한이 있으면 경고만
     * @param {BoardSanctionKind} kind - 단계.
     * @param {boolean} restricted - 진행 중인 쓰기 제한이 있다.
     * @returns {boolean} 고를 수 있으면 true.
     */
    static kindAllowed(kind: BoardSanctionKind, restricted: boolean): boolean {
        return !restricted || !UtilBoardSanction.isRestrict(kind);
    }

    /**
     * 알려진 사유 코드인가
     * @param {unknown} value - 값.
     * @returns {boolean} 다섯 코드 중 하나면 true.
     */
    static isReasonCode(value: unknown): value is BoardSanctionReasonCode {
        return typeof value === 'string' && (BOARD_SANCTION_REASONS as readonly string[]).includes(value);
    }

    /**
     * 덧붙임 글자 수(앞뒤 공백 뺀 유니코드 글자)
     * @param {string} text - 덧붙임.
     * @returns {number} 글자 수.
     */
    static textLength(text: string): number {
        return Array.from(cleanText(text)).length;
    }

    /**
     * 덧붙임 검사 — 0~200자, 기타면 5자 이상
     * @param {string} text - 덧붙임.
     * @param {BoardSanctionReasonCode | null} reasonCode - 고른 사유.
     * @returns {BoardSanctionTextProblem | null} 문제. 괜찮으면 null.
     */
    static textProblem(text: string, reasonCode: BoardSanctionReasonCode | null): BoardSanctionTextProblem | null {
        const n = UtilBoardSanction.textLength(text);
        if (n > UtilBoardSanction.TEXT_MAX) return 'TOO_LONG';
        if (reasonCode === 'OTHER' && n < UtilBoardSanction.OTHER_TEXT_MIN) return 'OTHER_TOO_SHORT';
        return null;
    }

    /**
     * 보낼 덧붙임 — 앞뒤 공백을 떼고, 비면 null
     * @param {string} text - 덧붙임.
     * @returns {string | null} 보낼 값.
     */
    static textBody(text: string): string | null {
        const value = cleanText(text);
        return value ? value : null;
    }

    /**
     * 해제 사유 검사 — 1~200자(결정 11)
     * @param {string} text - 사유.
     * @returns {'EMPTY' | 'TOO_LONG' | null} 문제. 괜찮으면 null.
     */
    static liftReasonProblem(text: string): 'EMPTY' | 'TOO_LONG' | null {
        const n = UtilBoardSanction.textLength(text);
        if (n === 0) return 'EMPTY';
        if (n > UtilBoardSanction.LIFT_REASON_MAX) return 'TOO_LONG';
        return null;
    }

    /**
     * 주소의 범위 값 — 모르면 전체
     * @param {unknown} value - 쿼리 값.
     * @returns {BoardSanctionScope} 범위.
     */
    static scopeOf(value: unknown): BoardSanctionScope {
        const raw = Array.isArray(value) ? value[0] : value;
        return raw === 'ACTIVE' || raw === 'ENDED' ? raw : 'ALL';
    }

    /**
     * 주소의 쪽 번호 — 1 이상 정수, 아니면 1
     * @param {unknown} value - 쿼리 값.
     * @returns {number} 쪽.
     */
    static pageOf(value: unknown): number {
        const raw = Array.isArray(value) ? value[0] : value;
        const n = typeof raw === 'string' ? Number.parseInt(raw, 10) : Number.NaN;
        return Number.isInteger(n) && n > 0 ? n : 1;
    }

    /**
     * 종류 태그 색 — 경고 노랑, 쓰기 제한 빨강
     * @param {string} kind - 단계.
     * @returns {SanctionTone} 색.
     */
    static kindTone(kind: string): SanctionTone {
        return kind === 'WARNING' ? 'amber' : 'red';
    }

    /**
     * 상태 태그 색 — 유효는 종류 색, 해제 초록, 끝남·무효 회색
     * @param {string} kind - 단계.
     * @param {string} status - 상태.
     * @returns {SanctionTone} 색.
     */
    static statusTone(kind: string, status: string): SanctionTone {
        if (status === 'ACTIVE') return UtilBoardSanction.kindTone(kind);
        if (status === 'LIFTED') return 'green';
        return 'gray';
    }

    /**
     * 쓰기 막힘 오류(`403 BOARD_WRITE_RESTRICTED` + `until`)에서 안내 값 — 그 오류가 아니면 null
     * @param {unknown} e - 잡은 오류.
     * @returns {BoardWriteRestriction | null} 안내에 쓸 값(사유·번호는 오류에 없을 수 있다).
     */
    static restrictionFromError(e: unknown): BoardWriteRestriction | null {
        if (!(e instanceof ApiError) || e.code !== 'BOARD_WRITE_RESTRICTED') return null;
        const until = e.extraText('until');
        if (!until) return null;
        return { boardSanctionId: e.extraText('boardSanctionId') ?? '', until, reasonCode: e.extraText('reasonCode') ?? '' };
    }

    /**
     * 제재 대화창 제출 실패의 갈래 — 문구가 아니라 코드로
     * @param {unknown} e - 잡은 오류.
     * @returns {BoardSanctionOutcome} 갈래.
     */
    static outcomeOf(e: unknown): BoardSanctionOutcome {
        if (!(e instanceof ApiError)) return 'OTHER';
        if (e.code === 'UNAUTHENTICATED' || e.status === 401) return 'LOGIN';
        if (e.code === 'BOARD_STAFF_DAILY_LIMIT') return 'DAILY_LIMIT';
        if (e.code === 'BOARD_STAFF_TARGET_PROTECTED') return 'TARGET_PROTECTED';
        if (e.code === 'BOARD_STAFF_ALREADY_RESTRICTED') return 'ALREADY_RESTRICTED';
        if (e.code === 'VALIDATION') return 'VALIDATION';
        if (e.status === 403) return 'FORBIDDEN';
        return 'OTHER';
    }

    /**
     * 한도 초과 오류의 한도 — `limit`·`used`·`resetsAt` extras
     * @param {unknown} e - 잡은 오류.
     * @returns {{ limit: number; used: number; resetsAt: string | null } | null} 값. 읽을 수 없으면 null.
     */
    static limitOf(e: unknown): { limit: number; used: number; resetsAt: string | null } | null {
        if (!(e instanceof ApiError) || e.code !== 'BOARD_STAFF_DAILY_LIMIT') return null;
        const limit = Number(e.extraText('limit'));
        const used = Number(e.extraText('used'));
        if (!Number.isFinite(limit) || !Number.isFinite(used)) return null;
        return { limit, used, resetsAt: e.extraText('resetsAt') ?? null };
    }
}
