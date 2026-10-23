/**
 * 내 제재·이의제기 API 가 주고받는 모양 — **서버에 아직 없다**(목업). 모양은 community `docs/api-sanction.md` 2절(회원 쪽) 그대로이고,
 * 게시판 한정 제재(`boardSanctions`·`BOARD_SANCTION` — community 게시판 지킴이 기능)는 로보얌에 없어 뺐다. 계약 후보: `docs/features/member-activity-contract.md`.
 * 로그인 정보의 요약(`restriction`)은 `useAuthApi.types.ts` 의 `Restriction` 이다(이미 서버에 있다).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { AccountStatus } from '~/composables/auth/api/useAuthApi.types';

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
    /** 이의제기 — 없으면 null. null 이고 무효가 아니면 접수할 수 있다 */
    appeal: AppealView | null;
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
}

/** `POST /api/me/appeals` 요청 — 사유 10~500자 */
export interface AppealRequest {
    sanctionId: string;
    reason: string;
}

/** `POST /api/me/appeals` 응답(201) */
export interface AppealCreated {
    id: string;
    no: number;
    status: 'PENDING';
}
