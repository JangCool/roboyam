import type { BoardSanctionBoardRef, BoardStaffRole } from '~/composables/sanction/api/useBoardSanctionApi.types';

/**
 * 관리자 스텝 처리 감독 API(`/api/admin/board-staff-actions…`)가 주고받는 모양 — 계약 `docs/features/board-staff-action-contract.md` 「API > 관리자」 그대로.
 * 게시판 매니저·스텝이 맡은 게시판에서 한 글 숨기기·글 삭제·댓글 삭제(게시판 제재 `kind SANCTION` 은 이 목록에 없다 — 게시판 제재 화면이 따로 있다).
 * **처리자 신원은 관리자 응답에만 있다**(정책 8). ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */

/** 처리 종류 — 글 숨기기 · 글 삭제 · 댓글 삭제(댓글 숨기기는 없다) */
export type BoardStaffActionKind = 'POST_HIDE' | 'POST_DELETE' | 'COMMENT_DELETE';

/** 되돌림 거르기 — 전체 · 되돌리지 않은 것 · 되돌린 것 */
export type BoardStaffActionRestoredFilter = 'ALL' | 'OPEN' | 'RESTORED';

/** 사유 코드 — 스팸·광고 · 비방·인신공격 · 투자 권유 · 게시판 주제와 맞지 않음(`OTHER` 는 제재에만, 자유 입력 없음) */
export type BoardStaffActionReasonCode = 'SPAM' | 'ABUSE' | 'INVESTMENT' | 'OFF_TOPIC';

/** 대상의 지금 상태 */
export type BoardStaffActionTargetState = 'PUBLIC' | 'HIDDEN' | 'DELETED';

/** 처리한 매니저·스텝(회원) 또는 운영자 회원 계정(`role` OPERATOR, 닉네임 = 그 회원 닉네임) */
export interface BoardStaffActionActor {
    id: string;
    nickname: string | null;
    role: BoardStaffRole;
    /** 운영자 회원 계정 줄이면 그 운영자의 지금 이름 — 그 밖 null */
    operatorName: string | null;
}

/** 대상 작성자 — 회원(닉네임) 또는 비회원(이름) */
export interface BoardStaffActionTargetAuthor {
    kind: 'MEMBER' | 'GUEST';
    id: string | null;
    name: string | null;
}

/** 처리 대상 — 제목·댓글 앞부분은 처리 때 스냅숏 */
export interface BoardStaffActionTarget {
    postId: string;
    /** 댓글 삭제면 댓글 번호, 글 처리면 null */
    commentId: string | null;
    /** 글 제목(처리 때 스냅숏) */
    title: string | null;
    /** 댓글 앞 40자(처리 때 스냅숏) — 글 처리면 null */
    excerpt: string | null;
    author: BoardStaffActionTargetAuthor;
    state: BoardStaffActionTargetState;
}

/** 되돌림 — 그 게시판 매니저 또는 운영자 */
export interface BoardStaffActionRestored {
    at: string;
    by: { kind: 'MANAGER' | 'OPERATOR'; name: string | null };
    reason: string | null;
}

/** 처리 한 줄 — 목록·상세·되돌리기 응답이 같은 모양 */
export interface AdminBoardStaffAction {
    id: string;
    board: BoardSanctionBoardRef;
    kind: BoardStaffActionKind;
    reasonCode: BoardStaffActionReasonCode | string;
    actor: BoardStaffActionActor;
    target: BoardStaffActionTarget;
    createdAt: string;
    restored: BoardStaffActionRestored | null;
}

/** `GET /api/admin/board-staff-actions` 조건 — 빈 값은 보내지 않는다 */
export interface BoardStaffActionQuery {
    /** 게시판 ID */
    board?: string;
    kind?: BoardStaffActionKind | 'ALL';
    restored?: BoardStaffActionRestoredFilter;
    /** 처리자 닉네임 */
    q?: string;
    /** 처리 시각 시작 순간(UTC ISO — 사이트 시간대 그날 00:00) */
    from?: string;
    /** 처리 시각 끝 순간(UTC ISO — 사이트 시간대 그날 끝) */
    to?: string;
    /** 0부터(관리자 목록 관례) */
    page?: number;
    /** 기본 20, 최대 50 */
    size?: number;
}

/** `GET /api/admin/board-staff-actions` — `boards` 는 게시판 거르기 고르개 */
export interface BoardStaffActionListView {
    total: number;
    page: number;
    size: number;
    boards: BoardSanctionBoardRef[];
    items: AdminBoardStaffAction[];
}

/**
 * `POST /api/admin/board-staff-actions/{id}/restore { reason }` (`keeper-action.restore`) → `200 AdminBoardStaffAction`. 감사 `BOARD_STAFF_ACTION_RESTORE`.
 * 400 `fields.reason`(1~200자) · 409 `BOARD_STAFF_ACTION_RESTORED`(이미 되돌림) · `POST_STATE_INVALID`(글 상태가 달라짐) · `POST_PURGED`(파기)
 */
export interface BoardStaffActionRestoreRequest {
    reason: string;
}
