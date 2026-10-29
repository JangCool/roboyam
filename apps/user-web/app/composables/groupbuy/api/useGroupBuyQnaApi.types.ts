import type { ReportReason } from '~/composables/report/api/useReportApi.types';

/**
 * 공구 Q&A(회원) API 가 주고받는 모양 — 계약 `commerce-groupbuy-contract.md` 「Q&A 규칙」(2b-6). 서버 연결 2026-10-29.
 * 볼 수 없는 것(남의 비밀글·숨긴 질문)은 서버가 본문을 비워(`null`) 보낸다 — 화면은 감추기만 한다.
 */

/** 질문 상태 — 답변 대기 · 답변 완료 */
export type GroupBuyQuestionStatus = 'OPEN' | 'ANSWERED';

/** 질문을 쓸 수 없는 까닭 — 로그인 안 함 · 이용 제한(정지) · 중단된 공구 */
export type GroupBuyAskBlock = 'LOGIN_REQUIRED' | 'RESTRICTED' | 'CLOSED';

export interface GroupBuyAnswer {
    body: string;
    answeredAt: string;
    /** 답변을 고친 적이 있다(이전 내용은 관리자만 본다) */
    edited: boolean;
}

export interface GroupBuyQuestion {
    id: string;
    /** 작성자 닉네임 — 가린 모양(서버가 가린다), 탈퇴한 회원이면 null */
    nickname: string | null;
    mine: boolean;
    /** 본문 — 볼 수 없으면(남의 비밀글·숨김) null */
    body: string | null;
    secret: boolean;
    /** 개인정보가 보여 서버가 비밀글로 바꿨다 */
    forcedSecret: boolean;
    status: GroupBuyQuestionStatus;
    /** 운영자가 숨겼다 — 작성자에게만 본문이 간다 */
    hidden: boolean;
    createdAt: string;
    /** 작성자가 고친 시각(답변 전에만 고칠 수 있다) */
    updatedAt: string | null;
    /** 답변 — 없거나 볼 수 없으면 null */
    answer: GroupBuyAnswer | null;
    /** 서버가 정한 할 수 있는 일 */
    actions: {
        /** 답변 전 작성자 */
        edit: boolean;
        /** 작성자(답변 전후) */
        delete: boolean;
        /** 답변 뒤 공개 글의 작성자 — 비밀글로만 바꿀 수 있다 */
        makeSecret: boolean;
        /** 로그인한 다른 회원, 볼 수 있는 질문 */
        report: boolean;
    };
}

/** `GET /api/group-buys/{id}/questions?mine=&page=` */
export interface GroupBuyQnaPage {
    items: GroupBuyQuestion[];
    /** 이 조건의 전체 수(`mine` 이면 내 것) */
    total: number;
    page: number;
    hasMore: boolean;
    /** 질문을 쓸 수 있나 — 아니면 `askBlock` */
    canAsk: boolean;
    askBlock: GroupBuyAskBlock | null;
    /** 앞 차수 공구(「이전 차수 문의 보기」) — 첫 차수면 null */
    previousRound: { id: string; roundNo: number } | null;
}

/** `POST /api/group-buys/{id}/questions` · `PUT /api/group-buy-questions/{qid}` */
export interface GroupBuyQuestionRequest {
    /** 1~1,000자 */
    body: string;
    secret: boolean;
}

/** 질문 신고 — 공용 신고(`POST /api/reports`, 대상 `GROUP_BUY_QUESTION`)로 보낸다 */
export interface GroupBuyQuestionReport {
    reason: ReportReason;
    detail: string | null;
}
