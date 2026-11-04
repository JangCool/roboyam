/**
 * 게시판 매니저·스텝 도구 API 가 주고받는 모양 — 게시판 제재(`docs/features/board-sanction-contract.md` 「API 후보 > 공개」).
 * 서버 `server/api` `com.community.boardstaff.dto`(`BoardSanctionPreviewView`·`BoardSanctionIssuedView`·`BoardStaffSanctionPageView`·`BoardStaffSanctionRowView`·`BoardStaffToolsView`·`BoardWriteRestrictionView` …)와 같은 칸 이름. 게시판 응답의 `viewer.staffTools`·`viewer.writeRestriction`·`viewer.pendingWarnings` 모양도 여기 둔다(`useBoardApi.types.ts` 의 `BoardViewer` 가 쓴다).
 * **처리자 신원**(`issuer`)은 그 게시판 매니저에게만 온다 — 스텝 목록·받은 회원 쪽 응답에는 없다(계약 결정 7).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */

/** 게시판 제재 단계 — 경고 · 쓰기 제한 1일 · 3일 · 7일(처리자가 자유롭게 고른다 — 권장은 안내뿐, 계약 결정 2) */
export type BoardSanctionKind = 'WARNING' | 'RESTRICT_1' | 'RESTRICT_3' | 'RESTRICT_7';

/** 화면이 그리는 단계 차례 */
export const BOARD_SANCTION_KINDS: readonly BoardSanctionKind[] = ['WARNING', 'RESTRICT_1', 'RESTRICT_3', 'RESTRICT_7'];

/** 사유 코드 — 스팸·광고 · 비방·인신공격 · 투자 권유 · 게시판 주제와 맞지 않음 · 기타(기타는 덧붙임 5자 이상 필수) */
export type BoardSanctionReasonCode = 'SPAM' | 'ABUSE' | 'INVESTMENT' | 'OFF_TOPIC' | 'OTHER';

/** 화면이 그리는 사유 차례 */
export const BOARD_SANCTION_REASONS: readonly BoardSanctionReasonCode[] = ['SPAM', 'ABUSE', 'INVESTMENT', 'OFF_TOPIC', 'OTHER'];

/** 상태 — 유효(경고는 늘, 쓰기 제한은 끝 전) · 기간 끝 · 매니저·운영자 해제 · 무효(이의 인용·운영자 무효) */
export type BoardSanctionStatus = 'ACTIVE' | 'ENDED' | 'LIFTED' | 'VOIDED';

/**
 * 제재 내역 범위 거르기 — 전체 · 진행 중(`ACTIVE` = 진행 중인 쓰기 제한만, 경고 제외 — 결정 11) · 끝남(`ENDED`·`LIFTED`·`VOIDED`).
 * 서버 기준: 유효한 경고(`ACTIVE`)는 「전체」에만 나온다(「끝남」에도 넣지 않는다)
 */
export type BoardSanctionScope = 'ALL' | 'ACTIVE' | 'ENDED';

/** 오늘(한국 시간 0시 기준) 처리자 1명·게시판 1개의 제재 한도 — 경고 + 쓰기 제한 합산, 해제·무효로 돌려받지 않는다 */
export interface BoardStaffQuota {
    limit: number;
    used: number;
    remaining: number;
}

/**
 * 그 게시판에서 지금 보는 사람이 할 수 있는 처리 — 역할 권한(`board.post.hide`·`board.post.delete`·`board.comment.delete`·`board.sanction.issue`)과
 * `restore` = 매니저 + `board.staff-action.restore`(board-staff-action-contract 「API > 공개」). 감추는 것은 편의이고 막는 것은 서버다.
 */
export interface BoardStaffAbilities {
    postHide: boolean;
    postDelete: boolean;
    commentDelete: boolean;
    sanction: boolean;
    restore: boolean;
}

/**
 * `GET /api/boards/{slug}` 의 `viewer.staffTools` — 그 게시판 매니저·스텝이고 계정 정상·인증이며 네 처리 권한 중 하나라도 있으면 온다(그 밖에는 null).
 * `manager` 가 참이면 매니저(그 게시판 처리·제재 전체를 보고 되돌릴 수 있다), 거짓이면 스텝(내가 한 것만 본다).
 * 한도: `post` = 글 숨기기 + 글 삭제 합산 · `comment` = 댓글 삭제 · `sanction` = 제재(경고 + 쓰기 제한). 모두 한국 시간 0시 기준, 되돌려도 돌려받지 않는다.
 */
export interface BoardStaffTools {
    manager: boolean;
    abilities: BoardStaffAbilities;
    post: BoardStaffQuota;
    comment: BoardStaffQuota;
    sanction: BoardStaffQuota;
    /** 한도가 다시 채워지는 시각(다음 한국 시간 0시) */
    resetsAt: string;
}

/** `GET /api/boards/{slug}` 의 `viewer.writeRestriction` — 이 게시판에서 진행 중인 쓰기 제한(없으면 null). 처리자는 오지 않는다 */
export interface BoardWriteRestriction {
    boardSanctionId: string;
    /** 제한이 끝나는 시각 */
    until: string;
    reasonCode: BoardSanctionReasonCode | (string & {});
}

/** `GET /api/boards/{slug}` 의 `viewer.pendingWarnings` 한 줄 — 보는 내가 이 게시판에서 받고 아직 확인하지 않은 경고(최근 것이 위, 최대 10). 처리자는 오지 않는다 */
export interface BoardPendingWarning {
    boardSanctionId: string;
    reasonCode: BoardSanctionReasonCode | (string & {});
    /** 받은 시각 */
    issuedAt: string;
}

/** 제재 대상 회원 — 닉네임은 읽을 때의 값 */
export interface BoardSanctionTarget {
    userId: string;
    nickname: string;
}

/**
 * `GET /api/board-staff/sanctions/preview?boardId=&userId=` — 제재 대화창을 열 때.
 * 받을 수 없는 대상(본인·같은 게시판 매니저·스텝·탈퇴·소모임 글)은 `409 BOARD_STAFF_TARGET_PROTECTED`(「제재할 수 없는 회원이에요」 하나로 통일).
 * 매니저·스텝이 아니면 `403 BOARD_STAFF_FORBIDDEN`(역할 권한 `board.sanction.issue` 없음·계정 정상·인증 아님도 같다).
 */
export interface BoardSanctionPreview {
    target: BoardSanctionTarget;
    /** 그 게시판에서 받은 제재 수(무효 제외) — 경고 · 쓰기 제한 */
    counts: { warnings: number; restrictions: number };
    /** 이 게시판에서 진행 중인 쓰기 제한 — 있으면 쓰기 제한 단계는 고를 수 없다(경고만) */
    activeRestriction: { boardSanctionId: string; until: string } | null;
    /** 권장 단계(유효한 제재 수로 계산: 경고 → 1일 → 3일 → 7일, 그 뒤 7일) — 안내일 뿐 막지 않는다 */
    recommended: BoardSanctionKind;
    quota: BoardStaffQuota;
    resetsAt: string;
}

/** `POST /api/board-staff/sanctions` 요청 */
export interface BoardSanctionIssueRequest {
    boardId: string;
    userId: string;
    kind: BoardSanctionKind;
    reasonCode: BoardSanctionReasonCode;
    /** 덧붙임 0~200자 — 비면 null, `OTHER` 이면 5자 이상 필수(`400 VALIDATION fields.reasonText`) */
    reasonText: string | null;
    /** 계기 글(선택) */
    postId: string | null;
    /** 계기 댓글(선택) — 댓글 ⋯ 메뉴에서 열었을 때 */
    commentId: string | null;
}

/**
 * `POST /api/board-staff/sanctions` 응답(201) — 남은 한도를 함께 준다.
 * 거절: 한도 초과 `409 BOARD_STAFF_DAILY_LIMIT`(`limit`·`used`·`resetsAt`) · 대상 불가 `409 BOARD_STAFF_TARGET_PROTECTED` · 이미 제한 중 `409 BOARD_STAFF_ALREADY_RESTRICTED` · 입력 `400 VALIDATION`.
 */
export interface BoardSanctionIssued {
    id: string;
    no: number;
    kind: BoardSanctionKind;
    startsAt: string;
    /** 쓰기 제한만 — 경고는 null */
    endsAt: string | null;
    quota: BoardStaffQuota;
    resetsAt: string;
}

/** 처리자 — 그 게시판 매니저가 보는 목록에만 온다 */
export interface BoardSanctionIssuer {
    nickname: string;
    role: 'MANAGER' | 'STAFF';
}

/** 제재 내역 한 줄 */
export interface BoardStaffSanctionRow {
    id: string;
    no: number;
    target: BoardSanctionTarget;
    kind: BoardSanctionKind | (string & {});
    reasonCode: BoardSanctionReasonCode | (string & {});
    reasonText: string | null;
    status: BoardSanctionStatus;
    startsAt: string;
    endsAt: string | null;
    createdAt: string;
    /** 매니저에게만(스텝이 보면 null) */
    issuer: BoardSanctionIssuer | null;
    /** 해제했으면 해제 사유·시각(매니저·운영자) */
    lift: { reason: string; at: string } | null;
    /** 계기 글·댓글 — 없거나 지워졌으면 null */
    source: { postId: string; commentId: string | null; title: string | null } | null;
    /** 지금 보는 사람이 [해제]할 수 있다(매니저 · 진행 중인 쓰기 제한) */
    canLift: boolean;
}

/**
 * `GET /api/board-staff/boards/{boardId}/sanctions?scope=&page=` — 매니저 = 그 게시판 전체(처리자 포함), 스텝 = 내가 준 것만(처리자 칸 없음).
 * 매니저·스텝이 아니면 `404 BOARD_STAFF_NOT_FOUND`(자리가 없거나 계정이 정상이 아님 — 화면은 404 안내. 역할 권한이 없어도 자리만 있으면 본다).
 */
export interface BoardStaffSanctionPage {
    manager: boolean;
    items: BoardStaffSanctionRow[];
    page: number;
    totalPages: number;
    totalCount: number;
}

export interface BoardStaffSanctionQuery {
    scope: BoardSanctionScope;
    page: number;
}

/** `POST /api/board-staff/sanctions/{id}/lift` 요청 — 사유 1~200자(결정 11, `400 fields.reason`). 응답은 바뀐 줄(`BoardStaffSanctionRow`) */
export interface BoardSanctionLiftRequest {
    reason: string;
}

/* ── 스텝 처리(글 숨기기·글 삭제·댓글 삭제 — docs/features/board-staff-action-contract.md 「API > 공개」) ── */

/** 스텝 처리 사유 코드 — 제재와 같은 넷(기타 `OTHER` 는 제재에만 → `400 fields.reasonCode`). 자유 입력은 없다 */
export type BoardStaffActionReasonCode = 'SPAM' | 'ABUSE' | 'INVESTMENT' | 'OFF_TOPIC';

/** 화면이 그리는 사유 차례 */
export const BOARD_STAFF_ACTION_REASONS: readonly BoardStaffActionReasonCode[] = ['SPAM', 'ABUSE', 'INVESTMENT', 'OFF_TOPIC'];

/** 처리 종류 — 글 숨기기 · 글 삭제 · 댓글 삭제(댓글 숨기기는 없다) */
export type BoardStaffActionKind = 'POST_HIDE' | 'POST_DELETE' | 'COMMENT_DELETE';

/** 처리 내역 종류 거르기 — 전체 · 글(숨기기 + 삭제) · 댓글 */
export type BoardStaffActionFilter = 'ALL' | 'POST' | 'COMMENT';

/** `POST /api/board-staff/posts/{postId}/hide|delete` 요청 */
export interface BoardStaffPostActionRequest {
    reasonCode: BoardStaffActionReasonCode;
}

/** `POST /api/board-staff/comments/{commentId}/delete` 요청 — `postId` 필수(그 글의 댓글이 아니면 `404 COMMENT_NOT_FOUND`) */
export interface BoardStaffCommentDeleteRequest {
    postId: string;
    reasonCode: BoardStaffActionReasonCode;
}

/**
 * 처리 응답(200) — 그 종류의 남은 한도(글 처리면 글 한도, 댓글 삭제면 댓글 한도)와 다시 채워지는 시각.
 * 거절(판정 순서): 401 → 404 `POST_NOT_FOUND`·`COMMENT_NOT_FOUND` → 403 `BOARD_STAFF_FORBIDDEN` → 409 `BOARD_STAFF_TARGET_PROTECTED`
 * → 409 `POST_STATE_INVALID`·`COMMENT_STATE_INVALID` → 409 `BOARD_STAFF_DAILY_LIMIT`(본문 최상위 `kind` POST|COMMENT·`limit`·`used`·`resetsAt`).
 */
export interface BoardStaffActionDone {
    quota: BoardStaffQuota;
    resetsAt: string;
}

/** 처리 내역 한 줄 — 처리자(`actor`)는 그 게시판 매니저에게만 온다(스텝이 보면 null) */
export interface BoardStaffActionRow {
    id: string;
    kind: BoardStaffActionKind | (string & {});
    reasonCode: BoardStaffActionReasonCode | (string & {});
    /** 대상 — 제목·댓글 앞 40자는 처리 때 스냅숏 */
    target: { postId: string; commentId: string | null; title: string; excerpt: string | null };
    /** 대상 작성자 — 회원 닉네임 또는 비회원 이름 */
    targetAuthor: { nickname: string };
    actor: { nickname: string; role: 'MANAGER' | 'STAFF' } | null;
    createdAt: string;
    /** 되돌렸으면 그 시각(되돌린 사람·사유는 공개 쪽에 없다) */
    restored: { at: string } | null;
    /** 지금 보는 사람이 [되돌리기] 할 수 있다(매니저 + `board.staff-action.restore` · 아직 되돌리지 않음) */
    canRestore: boolean;
}

/**
 * `GET /api/board-staff/boards/{boardId}/actions?kind=&page=`(1부터, 20개) — 매니저 = 그 게시판 전체, 스텝 = 본인 것만.
 * 매니저·스텝이 아니면 `404 BOARD_STAFF_NOT_FOUND`. 게시판 제재 기록은 넣지 않는다(제재 내역이 따로 있다).
 */
export interface BoardStaffActionPage {
    manager: boolean;
    items: BoardStaffActionRow[];
    page: number;
    totalPages: number;
    totalCount: number;
}

export interface BoardStaffActionQuery {
    kind: BoardStaffActionFilter;
    page: number;
}

/**
 * `POST /api/board-staff/actions/{id}/restore` 요청 — 사유 1~200자(`400 fields.reason`). 응답은 바뀐 줄(`BoardStaffActionRow`).
 * 매니저만(`403 BOARD_STAFF_FORBIDDEN`) · 이미 되돌림 `409 BOARD_STAFF_ACTION_RESTORED` · 글 상태가 달라짐 `409 POST_STATE_INVALID`(파기 `POST_PURGED`). 작성자에게 알리지 않는다.
 */
export interface BoardStaffActionRestoreRequest {
    reason: string;
}
