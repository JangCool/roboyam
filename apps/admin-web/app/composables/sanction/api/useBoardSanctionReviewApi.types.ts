import type { BoardSanctionBoardRef, BoardSanctionLine } from '~/composables/sanction/api/useBoardSanctionApi.types';
import type { SanctionActorRef, SanctionKind, SanctionUserRef } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 관리자 게시판 제재 반복 검토 API(`/api/admin/board-sanction-reviews…`)가 주고받는 모양 — 계약 `docs/features/board-sanction-contract.md` 결정 3.
 * 같은 회원 · 같은 게시판 · 최근 7일(롤링) · VOIDED 아닌 제재 3건 이상이면 서버가 검토 건을 연다((회원, 게시판)당 열린 건 하나, 마지막으로 닫힌 검토의 `closedAt` 이전 제재는 다시 세지 않는다).
 * 서버 DTO 후보 이름: `BoardSanctionReviewItemView`·`BoardSanctionReviewListView`·`BoardSanctionReviewDetailView`·`BoardSanctionReviewSummaryView`·`BoardSanctionReviewCloseRequest`.
 */

export type BoardSanctionReviewStatus = 'OPEN' | 'CLOSED';

/** 닫은 결과 — 그대로 둠 · 사이트 제재 부여 · 게시판 제재 무효 */
export type BoardSanctionReviewResult = 'KEPT' | 'SITE_SANCTIONED' | 'VOIDED';

/** 반복 검토 한 줄 */
export interface BoardSanctionReviewItem {
    id: string;
    /** 사람이 읽는 번호 */
    no: number;
    status: BoardSanctionReviewStatus;
    user: SanctionUserRef;
    board: BoardSanctionBoardRef;
    /** 열린 건은 지금 기준, 닫힌 건은 닫은 때 기준 — 이 게시판에서 최근 7일 안 VOIDED 아닌 제재 수 */
    recentCount: number;
    /** 같은 기준으로 이 회원이 게시판 제재를 받은 게시판 수 — 2 이상이면 「여러 게시판」 배지 */
    recentBoardCount: number;
    openedAt: string;
    closedAt: string | null;
    result: BoardSanctionReviewResult | null;
}

/** `GET /api/admin/board-sanction-reviews?status=&page=&size=` (`sanction.read`) — `counts` 는 거르기와 무관한 전체 건수. 열린 건은 오래된 것이 위, 닫힌 건은 최근 닫은 것이 위 */
export interface BoardSanctionReviewListView {
    counts: { open: number; closed: number };
    total: number;
    page: number;
    size: number;
    items: BoardSanctionReviewItem[];
}

export interface BoardSanctionReviewQuery {
    status?: BoardSanctionReviewStatus;
    /** 0부터 */
    page?: number;
    size?: number;
}

/** `GET /api/admin/board-sanction-reviews/summary` — 탭 배지·메뉴 배지·알림용 */
export interface BoardSanctionReviewSummary {
    open: number;
}

/** 다른 게시판에서 받은 게시판 제재 요약 */
export interface BoardSanctionOtherBoard {
    board: BoardSanctionBoardRef;
    /** 최근 7일 안 VOIDED 아닌 제재 수 */
    recentCount: number;
    /** VOIDED 아닌 전체 제재 수 */
    total: number;
    /** 진행 중인 쓰기 제한의 끝 — 없으면 null */
    activeUntil: string | null;
}

/** 닫을 때 부여한 사이트 제재 */
export interface BoardSanctionReviewSiteSanction {
    id: string;
    no: number;
    kind: SanctionKind;
}

/** `GET /api/admin/board-sanction-reviews/{id}` (`sanction.read`) — 없으면 `404 BOARD_SANCTION_REVIEW_NOT_FOUND` */
export interface BoardSanctionReviewDetail extends BoardSanctionReviewItem {
    /** 이 게시판에서 받은 최근 제재(처리자 포함, 최근 것이 위, 최대 20) */
    sanctions: BoardSanctionLine[];
    /** 이 검토가 센 제재 — 「게시판 제재 무효」로 고를 수 있는 것(그중 VOIDED 아닌 것) */
    reviewSanctionIds: string[];
    otherBoards: BoardSanctionOtherBoard[];
    closedBy: SanctionActorRef | null;
    memo: string | null;
    /** 결과가 SITE_SANCTIONED 면 부여한 사이트 제재 */
    siteSanction: BoardSanctionReviewSiteSanction | null;
    /** 결과가 VOIDED 면 무효로 한 제재 */
    voidedSanctionIds: string[];
}

/**
 * `POST /api/admin/board-sanction-reviews/{id}/close` (`sanction.manage`) → `200 BoardSanctionReviewDetail`.
 *  - `KEPT`: 그대로 둔다.
 *  - `SITE_SANCTIONED`: 화면이 먼저 기존 사이트 제재 대화창(`POST /api/admin/sanctions`)으로 부여하고, 받은 제재 번호를 `siteSanctionId` 로 보낸다(서버는 그 회원의 제재인지 본다).
 *  - `VOIDED`: `voidSanctionIds`(이 검토가 센 제재 중, 1개 이상)를 VOIDED 로 하고 닫는다(같은 트랜잭션).
 * 400 `fields.memo`(0~200자) · `fields.siteSanctionId` · `fields.voidSanctionIds` · 404 `BOARD_SANCTION_REVIEW_NOT_FOUND` · 409 `BOARD_SANCTION_REVIEW_CLOSED`(이미 닫힘 — 행 잠금으로 한 번만)
 */
export interface BoardSanctionReviewCloseRequest {
    result: BoardSanctionReviewResult;
    /** 0~200자 — 운영자끼리만 본다(회원·매니저에게 가지 않는다). 비었으면 null */
    memo: string | null;
    siteSanctionId: string | null;
    voidSanctionIds: string[];
}
