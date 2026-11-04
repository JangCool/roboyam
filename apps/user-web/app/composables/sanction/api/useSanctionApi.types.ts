/**
 * 내 제재·이의제기 API 가 주고받는 모양 — 서버 `server/api` `com.community.sanction.dto`(`MySanctionsView`·`MySanctionView`·`MyAppealView`·`AppealRequest`·`AppealCreatedView`)와 같은 칸 이름. 모양의 근거는 community `docs/api-sanction.md` 2절(회원 쪽)이고,
 * 게시판 제재(`boardSanctions` — 그 게시판 매니저·스텝이 준 것, 서버 `MyBoardSanctionView`·`MyBoardRefView`)는 `docs/features/board-sanction-contract.md`. 계약 후보: `docs/features/member-activity-contract.md`.
 * 로그인 정보의 요약(`restriction`)은 `useAuthApi.types.ts` 의 `Restriction` 이다(이미 서버에 있다).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { AccountStatus } from '~/composables/auth/api/useAuthApi.types';
import type { BoardSanctionKind, BoardSanctionReasonCode } from '~/composables/board/api/useBoardStaffApi.types';

/** 제재 종류 — 경고 · 정지 3일 · 7일 · 30일 · 영구 정지(단계는 한 칸씩 오른다: 경고 → 경고 → 3일 → 7일 → 30일 → 영구) */
export type SanctionKind = 'WARNING' | 'SUSPEND_3' | 'SUSPEND_7' | 'SUSPEND_30' | 'BAN';

/** 제재 상태 — 유효(정지는 진행 중) · 기간이 끝남 · 운영자가 풀어 줌 · 이의제기 인용으로 무효(경고 횟수에서도 빠진다) */
export type SanctionStatus = 'ACTIVE' | 'ENDED' | 'LIFTED' | 'VOIDED';

/** 이의제기 상태 — 검토 중 · 인용 · 기각 */
export type AppealStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface AppealView {
    id: string;
    /** 접수번호(`#번호`) */
    no: number;
    status: AppealStatus;
    reason: string;
    /** 운영팀 답변 — 검토 중이면 null */
    answer: string | null;
    createdAt: string;
    answeredAt: string | null;
}

export interface MySanction {
    id: string;
    no: number;
    kind: SanctionKind | (string & {});
    status: SanctionStatus;
    /** 운영자가 적은 사유(회원에게 전달되는 글 — 글자로만 그린다) */
    reason: string;
    /** 경고일 때 유효한 경고 중 몇 번째(1·2), 그 밖에는 null */
    warningNo: number | null;
    startsAt: string;
    /** 기간 정지만 — 그 밖에는 null */
    endsAt: string | null;
    createdAt: string;
    /** 경고의 「확인했어요」 여부 — 정지·영구 정지는 늘 true */
    acknowledged: boolean;
    /** 이의제기 — 없으면 null. null 이고 무효가 아니고 마감 전이면 접수할 수 있다 */
    appeal: AppealView | null;
    /**
     * 이의제기 마감 — 제재 시작(`startsAt`) + 30일(로보얌 결정, `member-sanction-contract.md` 결정 3). 무효(`VOIDED`)면 null.
     * 지나면 접수가 `409 APPEAL_EXPIRED` 다(이미 접수한 이의제기의 처리와는 상관없다).
     */
    appealDeadline: string | null;
}

/**
 * 내가 받은 게시판 제재 하나(`docs/features/board-sanction-contract.md` — 그 게시판의 매니저·스텝이 준 경고·쓰기 제한 1·3·7일).
 * **처리자 신원은 없다**(결정 7). 사이트 제재와 단계·횟수를 따로 센다. 이의제기는 사이트 제재와 같은 규칙(1번 · 시작 후 30일 · 10~500자).
 */
export interface MyBoardSanction {
    id: string;
    no: number;
    /** 제재가 적용된 게시판 — `nameEn` 은 선택 영문명 */
    board: { id: string; slug: string; name: string; nameEn: string | null };
    kind: BoardSanctionKind | (string & {});
    status: SanctionStatus;
    reasonCode: BoardSanctionReasonCode | (string & {});
    /** 처리자가 덧붙인 글(없으면 null — 글자로만 그린다) */
    reasonText: string | null;
    startsAt: string;
    /** 쓰기 제한만 — 경고는 null */
    endsAt: string | null;
    createdAt: string;
    /** 경고의 「확인했어요」 여부 — 쓰기 제한은 늘 true */
    acknowledged: boolean;
    appeal: AppealView | null;
    /** 이의제기 마감(시작 + 30일), 무효면 null */
    appealDeadline: string | null;
}

/** `GET /api/me/sanctions` — 로그인 필요(정지 중·인증 전에도). 읽기 전에 서버가 지난 정지를 먼저 끝낸다 */
export interface MySanctions {
    status: AccountStatus;
    /** 유효한 경고 횟수(0~2, 인용으로 빠진 것 제외) */
    warningCount: number;
    /** 진행 중인 정지·영구 정지 — 없으면 null */
    current: MySanction | null;
    /** 이력 — 최근 것이 위, 최대 50개, 무효 포함 */
    items: MySanction[];
    /**
     * 게시판 제재 이력 — 최근 것이 위, 최대 50개, 무효 포함(board-sanction-contract — 처리자 없음)
     */
    boardSanctions: MyBoardSanction[];
}

/**
 * `POST /api/me/appeals` 요청 — 사유 10~500자. 사이트 제재(`sanctionId`)와 게시판 제재(`boardSanctionId`) 중 **하나만** 보낸다(board-sanction-contract 결정 4). 둘 다 보내면 `400 fields.boardSanctionId`.
 */
export type AppealRequest = { sanctionId: string; reason: string } | { boardSanctionId: string; reason: string };

/** `POST /api/me/appeals` 응답(201) */
export interface AppealCreated {
    id: string;
    no: number;
    status: 'PENDING';
}
