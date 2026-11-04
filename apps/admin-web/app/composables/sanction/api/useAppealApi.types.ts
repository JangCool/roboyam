import type { AppealBoardSanctionBrief, BoardSanctionLine } from '~/composables/sanction/api/useBoardSanctionApi.types';
import type { AppealStatus, SanctionActorRef, SanctionKind, SanctionLine, SanctionStatus, SanctionUserRef } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 관리자 이의제기 API(`/api/admin/appeals…`)가 주고받는 모양 — 서버 `com.community.sanction.dto`(`AppealItemView`·`AppealListView`·`AppealDetailView`·`AppealSummaryView`·`AppealAnswerRequest`)와 같은 칸 이름. 계약 `docs/features/member-sanction-contract.md` 정책 6,
 * 모양은 community `docs/api-sanction.md` 4절. 목록·상세는 `sanction.read`, 인용·기각은 `appeal.manage`.
 * 게시판 제재 이의제기(`docs/features/board-sanction-contract.md` 결정 4 — 같은 표·같은 화면·같은 규칙): `kind`(SITE · BOARD)는 늘 온다. 게시판 제재 이의제기는 `sanction` 이 null 이고
 * `boardSanction`·`boardHistory` 가 있다. 사이트 제재 이의제기는 `boardSanction` 이 null · `boardHistory` 가 빈 배열이다.
 */

/** 이의제기 대상 — 사이트 제재 · 게시판 제재 */
export type AppealKind = 'SITE' | 'BOARD';

/** 상태 거르기 — 대기(기본) · 인용 · 기각 · 전체 */
export type AppealStatusFilter = AppealStatus | 'ALL';

/** 정렬 — 오래된 순(기본, 접수 차례대로 처리) · 최신 순 */
export type AppealSort = 'oldest' | 'newest';

/** 목록 한 줄의 제재 요약 */
export interface AppealSanctionBrief {
    id: string;
    no: number;
    kind: SanctionKind;
    status: SanctionStatus;
    warningNo: number | null;
    startsAt: string;
    endsAt: string | null;
    /** 부여한 운영자(처리자 칸) — 지워진 운영자면 null */
    issuedBy: SanctionActorRef | null;
}

export interface AppealItem {
    id: string;
    /** 접수번호(짧은 순번) */
    no: number;
    status: AppealStatus;
    user: SanctionUserRef;
    kind: AppealKind;
    /** 사이트 제재 이의제기의 대상 — 게시판 제재 이의제기는 null */
    sanction: AppealSanctionBrief | null;
    /** 게시판 제재 이의제기의 대상(게시판 이름 · 처리자 닉네임 + 매니저/스텝) — 사이트 제재 이의제기는 null */
    boardSanction: AppealBoardSanctionBrief | null;
    createdAt: string;
    answeredAt: string | null;
}

/** `GET /api/admin/appeals?status=&sort=&page=&size=` — `counts` 는 거르기와 무관한 전체 건수 */
export interface AppealListView {
    counts: { pending: number; accepted: number; rejected: number };
    total: number;
    page: number;
    size: number;
    items: AppealItem[];
}

export interface AppealQuery {
    /** 생략(`ALL`) = 전체 */
    status?: AppealStatusFilter;
    sort?: AppealSort;
    /** 0부터 */
    page?: number;
    size?: number;
}

/** `GET /api/admin/appeals/summary` — 메뉴 뱃지·대시보드용 */
export interface AppealSummary {
    pending: number;
}

/** `GET /api/admin/appeals/{id}` — 없으면 `404 APPEAL_NOT_FOUND` */
export interface AppealDetail {
    id: string;
    no: number;
    status: AppealStatus;
    /** 회원이 쓴 이의 사유 전문(10~500자 — 글자로만 그린다) */
    reason: string;
    createdAt: string;
    /** 운영자 답변 — 대기 중이면 null */
    answer: string | null;
    answeredAt: string | null;
    answeredBy: SanctionActorRef | null;
    user: SanctionUserRef;
    kind: AppealKind;
    /** 대상 사이트 제재(근거 신고 사건은 `sanction.reportCase`) — 게시판 제재 이의제기는 null */
    sanction: SanctionLine | null;
    /** 그 회원의 사이트 제재 전체(현황 `preview.history` 와 같다 — 빈 배열일 수 있다) */
    history: SanctionLine[];
    /** 대상 게시판 제재 — 인용하면 VOIDED(진행 중 쓰기 제한은 즉시 풀림, 반복 검토 수에서 빠짐). 사이트 제재 이의제기는 null */
    boardSanction: BoardSanctionLine | null;
    /** 그 회원의 게시판 제재 이력(모든 게시판, 최근 것이 위) — 사이트 제재 이의제기는 빈 배열 */
    boardHistory: BoardSanctionLine[];
}

/**
 * `POST /api/admin/appeals/{id}/accept|reject` → `200 AppealDetail`(community 는 204 — 화면이 다시 받지 않게 새 상세를 돌려받는 쪽을 후보로 둔다).
 * 인용: 이의제기 ACCEPTED · 제재 VOIDED(경고 횟수에서 빠짐) · 진행 중 정지면 즉시 풀림. 기각: 제재 유지. 둘 다 회원 알림·메일 `APPEAL_ANSWERED`.
 * 400 `VALIDATION`(`fields.answer` 1~500자) · 404 `APPEAL_NOT_FOUND` · 409 `APPEAL_CLOSED`(이미 처리됨 — 행 잠금으로 한 번만)
 */
export interface AppealAnswerRequest {
    /** 1~500자 — 회원에게 그대로 전달된다 */
    answer: string;
}
