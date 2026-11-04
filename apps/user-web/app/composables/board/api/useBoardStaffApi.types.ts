/**
 * 게시판 매니저·스텝 도구 API 가 주고받는 모양 — 게시판 제재(`docs/features/board-sanction-contract.md` 「API 후보 > 공개」).
 * 서버가 아직 없어 이름은 후보다(서버 DTO 가 생기면 그 이름으로 맞춘다). 게시판 응답의 `viewer.staffTools`·`viewer.writeRestriction` 모양도 여기 둔다(`useBoardApi.types.ts` 의 `BoardViewer` 가 쓴다).
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

/** 제재 내역 범위 거르기 — 전체 · 진행 중(`ACTIVE`) · 끝남(`ENDED`·`LIFTED`·`VOIDED`) */
export type BoardSanctionScope = 'ALL' | 'ACTIVE' | 'ENDED';

/** 오늘(한국 시간 0시 기준) 처리자 1명·게시판 1개의 제재 한도 — 경고 + 쓰기 제한 합산, 해제·무효로 돌려받지 않는다 */
export interface BoardStaffQuota {
    limit: number;
    used: number;
    remaining: number;
}

/**
 * `GET /api/boards/{slug}` 의 `viewer.staffTools` — 그 게시판 매니저·스텝 본인에게만 온다(그 밖에는 null).
 * `manager` 가 참이면 매니저(그 게시판 제재 전체를 보고 해제할 수 있다), 거짓이면 스텝(내가 준 것만 본다).
 */
export interface BoardStaffTools {
    sanction: BoardStaffQuota;
    /** 한도가 다시 채워지는 시각(다음 한국 시간 0시) */
    resetsAt: string;
    manager: boolean;
}

/** `GET /api/boards/{slug}` 의 `viewer.writeRestriction` — 이 게시판에서 진행 중인 쓰기 제한(없으면 null). 처리자는 오지 않는다 */
export interface BoardWriteRestriction {
    boardSanctionId: string;
    /** 제한이 끝나는 시각 */
    until: string;
    reasonCode: BoardSanctionReasonCode | (string & {});
}

/** 제재 대상 회원 — 닉네임은 읽을 때의 값 */
export interface BoardSanctionTarget {
    userId: string;
    nickname: string;
}

/**
 * `GET /api/board-staff/sanctions/preview?boardId=&userId=` — 제재 대화창을 열 때.
 * 받을 수 없는 대상(본인·같은 게시판 매니저·스텝·탈퇴·소모임 글)은 `409 BOARD_STAFF_TARGET_PROTECTED`(「제재할 수 없는 회원이에요」 하나로 통일).
 * 매니저·스텝이 아니면 `403 BOARD_STAFF_FORBIDDEN`(후보).
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
 * 매니저·스텝이 아니면 `404 BOARD_STAFF_NOT_FOUND`(후보 — 화면은 404 안내).
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

/** `POST /api/board-staff/sanctions/{id}/lift` 요청 — 사유 1~200자(가정). 응답은 바뀐 줄(`BoardStaffSanctionRow`) */
export interface BoardSanctionLiftRequest {
    reason: string;
}
