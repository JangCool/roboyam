/**
 * 공구 Q&A 관리자 API(`/api/admin/group-buy-qna…`)가 주고받는 모양 — 계약 `commerce-groupbuy-contract.md` 「Q&A 규칙」·결정 35·60(2b-6, 서버 연결 2026-10-29).
 * 관리자는 비밀글 본문도 본다 — 목록 조회·상세 열기는 서버가 개인정보 열람 감사(`GROUPBUY_QNA_VIEW`, 2년)를 남긴다.
 */
import type { GroupBuyMember, GroupBuyPage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

export type GroupBuyQuestionStatus = 'OPEN' | 'ANSWERED';

/** 탭 — 답변 대기 · 답변 완료 · 숨김(숨긴 것은 대기·완료 탭에서 빠진다) · 전체. 작성자가 지운 것은 「전체」에만 */
export type GroupBuyQnaTab = 'OPEN' | 'ANSWERED' | 'HIDDEN' | 'ALL';
export const GROUP_BUY_QNA_TABS: readonly GroupBuyQnaTab[] = ['OPEN', 'ANSWERED', 'HIDDEN', 'ALL'];

/** `GET /api/admin/group-buy-qna?tab=&overdue=&q=&page=&size=` — 대기 탭은 오래 기다린 것 먼저, 그 밖은 새 글 먼저 */
export interface GroupBuyQnaQuery {
    tab?: GroupBuyQnaTab;
    /** 답변 기한(2영업일 — 결정 60) 지난 것만 */
    overdue?: boolean;
    /** 공구 제목 · 닉네임 · 회원 번호 · 공구 번호 */
    q?: string;
    page: number;
    size: number;
}

export interface GroupBuyQnaRow {
    id: string;
    groupBuy: { id: string; title: string; roundNo: number };
    member: GroupBuyMember;
    /** 본문 앞부분(관리자는 비밀글도 본다) */
    preview: string;
    secret: boolean;
    /** 개인정보가 보여 서버가 비밀글로 바꿨다 */
    forcedSecret: boolean;
    status: GroupBuyQuestionStatus;
    hidden: boolean;
    /** 작성자가 지웠다(보관 기간 동안 남는다) */
    deleted: boolean;
    /** 열린 신고 수 */
    reportCount: number;
    createdAt: string;
    answeredAt: string | null;
    /** 답변 대기면 작성 뒤 지난 영업일 */
    elapsedBusinessDays: number;
    /** 답변 기한(2영업일) 지남 */
    overdue: boolean;
}

export interface GroupBuyQnaListResponse extends GroupBuyPage<GroupBuyQnaRow> {
    counts: Record<Exclude<GroupBuyQnaTab, 'ALL'>, number>;
    /** 기한 지난 답변 대기 수 */
    overdueCount: number;
}

export interface GroupBuyQnaOperator {
    id: string;
    name: string;
}

export interface GroupBuyAnswerRevision {
    id: string;
    /** 고치기 전 내용 */
    body: string;
    /** 그 내용을 쓴 시각 */
    writtenAt: string;
    writtenBy: GroupBuyQnaOperator;
    /** 고친 시각 · 고친 운영자 */
    replacedAt: string;
    replacedBy: GroupBuyQnaOperator;
}

/** `GET /api/admin/group-buy-qna/{id}` */
export interface GroupBuyQnaDetail extends GroupBuyQnaRow {
    body: string;
    /** 작성자가 고친 시각 */
    updatedAt: string | null;
    deletedAt: string | null;
    /** 파기 예정일(마지막 답변일 — 없으면 작성일 — 부터 3년) */
    retainUntil: string;
    answer: {
        body: string;
        answeredAt: string;
        answeredBy: GroupBuyQnaOperator;
        updatedAt: string | null;
        updatedBy: GroupBuyQnaOperator | null;
    } | null;
    /** 답변 수정 이력 — 새 것 먼저 */
    revisions: GroupBuyAnswerRevision[];
    hiddenInfo: { reason: string; hiddenAt: string; hiddenBy: GroupBuyQnaOperator } | null;
    /** 열린 신고 — 사유별 수 */
    reports: { reason: string; count: number }[];
}

/** `PUT /api/admin/group-buy-qna/{id}/answer` — 처음이면 답변, 있으면 수정(이전 내용은 이력으로) */
export interface GroupBuyAnswerRequest {
    /** 1~2,000자(가정) */
    body: string;
}
