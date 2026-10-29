/**
 * 공구 리뷰 관리자 API(`/api/admin/group-buy-reviews…`)가 주고받는 모양 — 계약 「리뷰 규칙」(2b-6, 가정 — 사용자 확인 대기).
 * 숨김은 사유 코드로만 — 낮은 별점은 숨길 사유가 아니다(표시광고법·공정위 소비자 리뷰 지침).
 */
import type { GroupBuyMember, GroupBuyPage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type { GroupBuyQnaOperator } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';

/** 탭 — 공개 · 숨김 · 신고 있음(공개 중 열린 신고) · 전체(작성자가 지운 것 포함) */
export type GroupBuyReviewTab = 'VISIBLE' | 'HIDDEN' | 'REPORTED' | 'ALL';
export const GROUP_BUY_REVIEW_TABS: readonly GroupBuyReviewTab[] = ['VISIBLE', 'REPORTED', 'HIDDEN', 'ALL'];

/** 숨김 사유 코드 — 욕설 · 개인정보 · 광고 · 무관한 내용 · 기타(메모 필수) */
export type GroupBuyReviewHideReason = 'ABUSE' | 'PRIVACY' | 'AD' | 'IRRELEVANT' | 'OTHER';
export const GROUP_BUY_REVIEW_HIDE_REASONS: readonly GroupBuyReviewHideReason[] = ['ABUSE', 'PRIVACY', 'AD', 'IRRELEVANT', 'OTHER'];

/** `GET /api/admin/group-buy-reviews?tab=&rating=&photo=&noReply=&q=&page=&size=` — 새 것 먼저 */
export interface GroupBuyReviewQuery {
    tab?: GroupBuyReviewTab;
    /** 별점 하나만(1~5) */
    rating?: number;
    /** 사진 있는 것만 */
    photo?: boolean;
    /** 답글 없는 것만 */
    noReply?: boolean;
    /** 상품·공구 제목 · 닉네임 · 회원 번호 · 주문 번호 */
    q?: string;
    page: number;
    size: number;
}

export interface GroupBuyReviewPhoto {
    id: string;
    url: string;
}

export interface GroupBuyReviewRow {
    id: string;
    product: { id: string; name: string };
    groupBuy: { id: string; title: string; roundNo: number };
    /** 주문 ID(주문 서랍 `/group-buy-orders?order=`) · 주문 번호 */
    orderId: string;
    orderNo: string;
    skuName: string;
    member: GroupBuyMember;
    rating: number;
    preview: string;
    photoCount: number;
    hidden: boolean;
    deleted: boolean;
    reportCount: number;
    hasReply: boolean;
    createdAt: string;
    updatedAt: string | null;
}

export interface GroupBuyReviewListResponse extends GroupBuyPage<GroupBuyReviewRow> {
    counts: Record<Exclude<GroupBuyReviewTab, 'ALL'>, number>;
}

/** 작성자 수정 이력 — 고치기 전 내용 */
export interface GroupBuyReviewRevision {
    id: string;
    rating: number;
    body: string;
    photoCount: number;
    /** 그 내용을 쓴(마지막으로 고친) 시각 */
    writtenAt: string;
    replacedAt: string;
}

/** `GET /api/admin/group-buy-reviews/{id}` */
export interface GroupBuyReviewDetail extends GroupBuyReviewRow {
    body: string;
    photos: GroupBuyReviewPhoto[];
    deletedAt: string | null;
    revisions: GroupBuyReviewRevision[];
    /** 공식 답글 하나 — 질문 답변과 같은 모양 */
    answer: {
        body: string;
        answeredAt: string;
        answeredBy: GroupBuyQnaOperator;
        updatedAt: string | null;
        updatedBy: GroupBuyQnaOperator | null;
    } | null;
    hiddenInfo: { reasonCode: GroupBuyReviewHideReason; memo: string | null; hiddenAt: string; hiddenBy: GroupBuyQnaOperator } | null;
    reports: { reason: string; count: number }[];
}

/** `POST /api/admin/group-buy-reviews/{id}/hide` — 기타는 메모 필수(5~200자), 그 밖은 메모 선택(200자까지) */
export interface GroupBuyReviewHideRequest {
    reasonCode: GroupBuyReviewHideReason;
    memo: string | null;
}
