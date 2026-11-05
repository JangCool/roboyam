import type { SanctionActorRef, SanctionAppealRef, SanctionScope, SanctionSearchField, SanctionSort, SanctionStatus, SanctionUserRef } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 관리자 게시판 제재 API(`/api/admin/board-sanctions…`)가 주고받는 모양 — 계약 `docs/features/board-sanction-contract.md`(「규칙」·「API 후보 > 관리자」).
 * 서버 `server/admin` `com.community.sanction.dto`(`BoardSanctionLineView`·`BoardSanctionListView`·`BoardSanctionDetailView`·`BoardSanctionReasonRequest`, 회원 상세 요약 `MemberBoardSanctionSummaryView`)와 같은 칸 이름.
 * 게시판 제재는 매니저·스텝이 맡은 게시판에서 준 경고·쓰기 제한(1·3·7일)이다 — 사이트 제재(`useSanctionApi.types.ts`)와 단계·횟수를 따로 센다.
 * **처리자(매니저·스텝) 신원은 관리자 응답에만 있다**(정책 7). ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */

/** 단계 — 경고 · 쓰기 제한 1·3·7일(처리자가 자유롭게 고른다, 권장 단계는 화면 설명일 뿐) */
export type BoardSanctionKind = 'WARNING' | 'RESTRICT_1' | 'RESTRICT_3' | 'RESTRICT_7';

/** 상태 — 사이트 제재와 같은 값: 유효·진행 중 · 기간 끝 · 해제(매니저·운영자, 권장 계산엔 남음) · 무효(이의 인용·운영자 — 권장·반복 검토 수에서 빠짐) */
export type BoardSanctionStatus = SanctionStatus;

/** 사유 코드 — 스팸·광고 · 비방·인신공격 · 투자 권유 · 게시판 주제와 맞지 않음 · 기타(덧붙임 5자 이상 필수) */
export type BoardSanctionReasonCode = 'SPAM' | 'ABUSE' | 'INVESTMENT' | 'OFF_TOPIC' | 'OTHER';

/** 처리자의 자리 — 그 게시판 매니저 · 스텝 · 운영자 회원 계정(`OPERATOR` — operator-member-account-contract, 서버 연결됨 2026-11-05) */
export type BoardStaffRole = 'MANAGER' | 'STAFF' | 'OPERATOR';

/** 무효가 된 까닭 — 운영자 직접 · 반복 검토 닫기(`VOIDED`) · 이의제기 인용 */
export type BoardSanctionVoidSource = 'OPERATOR' | 'REVIEW' | 'APPEAL';

/** 게시판 — 이름은 화면 언어에 맞춰 `nameEn` 을 고른다(`useLocalizedName`) */
export interface BoardSanctionBoardRef {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
}

/** 준 사람 — 그 게시판의 매니저·스텝(회원) 또는 운영자 회원 계정(`role` OPERATOR, 닉네임 = 그 회원 닉네임). 탈퇴했으면 닉네임이 null 일 수 있다 */
export interface BoardSanctionIssuer {
    id: string;
    nickname: string | null;
    role: BoardStaffRole;
    /** 운영자 회원 계정 줄이면 그 운영자의 지금 이름 — 그 밖 null */
    operatorName: string | null;
}

/** 해제한 사람 — 그 게시판 매니저(회원) 또는 운영자 */
export interface BoardSanctionLiftActor {
    type: 'MANAGER' | 'OPERATOR';
    id: string;
    /** 매니저는 닉네임, 운영자는 이름 — 지워졌으면 null */
    name: string | null;
}

/** 계기 글·댓글 — 지워졌어도 번호는 남는다. 댓글이면 글 번호와 댓글 번호를 함께 준다 */
export interface BoardSanctionTriggerRef {
    type: 'POST' | 'COMMENT';
    postId: string;
    commentId: string | null;
    /** 글 제목(지워졌으면 null) */
    title: string | null;
}

/** 게시판 제재 한 줄 — 목록·상세·회원 이력·반복 검토·이의제기 상세가 같이 쓴다 */
export interface BoardSanctionLine {
    id: string;
    /** 사람이 읽는 번호(사이트 제재 번호와 따로 센다) */
    no: number;
    kind: BoardSanctionKind;
    status: BoardSanctionStatus;
    board: BoardSanctionBoardRef;
    user: SanctionUserRef;
    reasonCode: BoardSanctionReasonCode;
    /** 덧붙임 0~200자(`OTHER` 이면 5자 이상) — 회원에게 그대로 전달된다, 글자로만 그린다 */
    reasonText: string | null;
    /** 준 매니저·스텝 — 관리자 응답에만 있다 */
    issuedBy: BoardSanctionIssuer | null;
    startsAt: string;
    /** 쓰기 제한만 — 경고는 null */
    endsAt: string | null;
    createdAt: string;
    trigger: BoardSanctionTriggerRef | null;
    liftedBy: BoardSanctionLiftActor | null;
    liftedAt: string | null;
    liftReason: string | null;
    voidedAt: string | null;
    voidedBy: SanctionActorRef | null;
    voidSource: BoardSanctionVoidSource | null;
    voidReason: string | null;
    /** 기간이 끝난 시각 */
    endedAt: string | null;
    /** 경고를 회원이 확인한 시각(공개 `/sanction` 의 [확인했어요]) — 쓰기 제한·미확인은 null */
    acknowledgedAt: string | null;
    appeal: SanctionAppealRef | null;
    /** 이의제기 마감(시작 + 30일) — 무효면 null */
    appealDeadline: string | null;
}

/** `GET /api/admin/board-sanctions` 조건 — 빈 값은 보내지 않는다. 범위 `ACTIVE` = 진행 중인 쓰기 제한만(경고 제외 — 결정 11), `CLOSED` = 기간 끝·해제·무효 */
export interface BoardSanctionQuery {
    scope?: SanctionScope;
    boardId?: string;
    kind?: BoardSanctionKind;
    searchField?: SanctionSearchField;
    q?: string;
    /** 부여일 시작 순간(UTC ISO — 사이트 시간대 그날 00:00) */
    from?: string;
    to?: string;
    userId?: string;
    sort?: SanctionSort;
    /** 0부터 */
    page?: number;
    /** 기본 20, 최대 50 */
    size?: number;
}

/** `GET /api/admin/board-sanctions` — `counts` 는 거르기와 무관한 전체 건수, `boards` 는 게시판 거르기 고르개(게시판 제재가 한 건이라도 있는 게시판, 이름 차례) */
export interface BoardSanctionListView {
    counts: { all: number; active: number; closed: number };
    total: number;
    page: number;
    size: number;
    items: BoardSanctionLine[];
    boards: BoardSanctionBoardRef[];
}

/** `GET /api/admin/board-sanctions/{id}` — 제재 하나 + 그 회원의 게시판 제재 이력(모든 게시판, 최근 것이 위, 최대 30개). 없으면 `404 BOARD_SANCTION_NOT_FOUND` */
export interface BoardSanctionDetail {
    sanction: BoardSanctionLine;
    history: BoardSanctionLine[];
}

/**
 * `POST /api/admin/board-sanctions/{id}/lift { reason }` (`sanction.manage`) → `200 BoardSanctionDetail` — 진행 중 쓰기 제한만(LIFTED, 권장 계산엔 남는다).
 * 400 `fields.reason`(1~200자 — 결정 11) · 404 `BOARD_SANCTION_NOT_FOUND` · 409 `BOARD_SANCTION_NOT_LIFTABLE`(경고·이미 끝남·해제·무효)
 */
export interface BoardSanctionLiftRequest {
    reason: string;
}

/**
 * `POST /api/admin/board-sanctions/{id}/void { reason }` (`sanction.manage`) → `200 BoardSanctionDetail` — VOIDED(진행 중 쓰기 제한은 바로 풀리고, 반복 검토·권장 수에서 빠진다).
 * 400 `fields.reason`(1~200자 — 결정 11) · 404 `BOARD_SANCTION_NOT_FOUND` · 409 `BOARD_SANCTION_ALREADY_VOIDED`. 대기 중 이의제기가 있으면 같은 트랜잭션에서 「인용」으로 닫는다(결정 10).
 */
export interface BoardSanctionVoidRequest {
    reason: string;
}

/** 회원 상세 「게시판 제재」 구획의 게시판 하나 */
export interface MemberBoardSanctionBoard {
    board: BoardSanctionBoardRef;
    /** 무효 아닌 경고 수 */
    warningCount: number;
    /** 무효 아닌 쓰기 제한 수 */
    restrictCount: number;
    /** 진행 중인 쓰기 제한의 끝 — 없으면 null */
    activeUntil: string | null;
}

/** 회원 상세(`GET /api/admin/members/{id}`)의 `boardSanctions` — 게시판별 건수 + 최근 이력(최대 5) */
export interface MemberBoardSanctionSummary {
    /** 무효 아닌 게시판 제재 수(모든 게시판) */
    total: number;
    /** 진행 중인 쓰기 제한 수 */
    active: number;
    /** 열린 반복 검토 수 */
    openReviews: number;
    boards: MemberBoardSanctionBoard[];
    recent: BoardSanctionLine[];
}

/** 이의제기 줄의 게시판 제재 요약(`AppealItem.boardSanction`) */
export interface AppealBoardSanctionBrief {
    id: string;
    no: number;
    kind: BoardSanctionKind;
    status: BoardSanctionStatus;
    board: BoardSanctionBoardRef;
    issuedBy: BoardSanctionIssuer | null;
    startsAt: string;
    endsAt: string | null;
}
