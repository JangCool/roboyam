import type { ReportReason } from '~/composables/report/api/useReportApi.types';

/**
 * 공구 리뷰(회원) API 가 주고받는 모양 — 계약 `commerce-groupbuy-contract.md` 「리뷰 규칙」(2b-6, 가정 — 사용자 확인 대기).
 * 리뷰는 상품(상품 마스터)마다 모인다 — 같은 상품의 다른 차수 공구 리뷰도 함께 오고 `roundNo` 로 「n차 구매」를 붙인다.
 */

/** 정렬 — 최신 · 별점 높은 · 별점 낮은 · 사진 먼저 */
export type GroupBuyReviewSort = 'LATEST' | 'RATING_HIGH' | 'RATING_LOW' | 'PHOTO';
export const GROUP_BUY_REVIEW_SORTS: readonly GroupBuyReviewSort[] = ['LATEST', 'RATING_HIGH', 'RATING_LOW', 'PHOTO'];

export interface GroupBuyReviewPhoto {
    id: string;
    url: string;
}

/** 평균 별점 · 리뷰 수(숨긴 리뷰는 세지 않는다 — 가정) */
export interface GroupBuyReviewStats {
    /** 소수 첫째 자리, 리뷰가 없으면 null */
    average: number | null;
    count: number;
}

export interface GroupBuyReviewSummary extends GroupBuyReviewStats {
    /** 별점별 수 — 키 1~5 */
    distribution: Record<1 | 2 | 3 | 4 | 5, number>;
    photoCount: number;
    /** 사진 모아보기 — 최근 사진 리뷰의 사진(최대 12장) */
    photos: { reviewId: string; url: string }[];
}

export interface GroupBuyReview {
    id: string;
    /** 가린 닉네임, 탈퇴한 회원이면 null */
    nickname: string | null;
    mine: boolean;
    rating: 1 | 2 | 3 | 4 | 5;
    /** 숨긴 남의 리뷰는 목록에 오지 않는다. 내 숨긴 리뷰는 본문과 숨김 사유가 온다 */
    body: string;
    photos: GroupBuyReviewPhoto[];
    /** 산 단품 이름(주문 줄 사본) */
    skuName: string;
    /** 산 공구의 차수 */
    roundNo: number;
    createdAt: string;
    updatedAt: string | null;
    /** 운영자가 숨겼다(작성자에게만 온다) */
    hidden: { reasonCode: GroupBuyReviewHideReason; memo: string | null } | null;
    /** 판매자 공식 답글 하나 */
    reply: { body: string; repliedAt: string; edited: boolean } | null;
    actions: {
        /** 작성 30일 안의 작성자 */
        edit: boolean;
        delete: boolean;
        /** 로그인한 다른 회원 */
        report: boolean;
    };
}

/** 숨김 사유 코드 — 욕설 · 개인정보 · 광고 · 무관한 내용 · 기타(낮은 별점은 사유가 될 수 없다) */
export type GroupBuyReviewHideReason = 'ABUSE' | 'PRIVACY' | 'AD' | 'IRRELEVANT' | 'OTHER';

/** `GET /api/group-buys/{id}/reviews?sort=&photo=&page=` */
export interface GroupBuyReviewPage {
    summary: GroupBuyReviewSummary;
    items: GroupBuyReview[];
    /** 이 조건(사진 리뷰만 등)의 수 */
    total: number;
    page: number;
    hasMore: boolean;
}

/** 내 주문의 줄마다 리뷰 상태 — 쓸 수 있음 · 썼음 · 기한 지남 · 대상 아님(배송완료 전·모두 취소·반품) */
export type GroupBuyReviewTargetState = 'WRITABLE' | 'WRITTEN' | 'EXPIRED' | 'NONE';

/** `GET /api/me/orders/{orderNo}/reviews` — 주문 줄마다 */
export interface GroupBuyReviewTarget {
    itemId: string;
    state: GroupBuyReviewTargetState;
    /** 쓸 수 있는 마지막 날(배송완료일 + 90일, 한국 날짜) */
    writableUntil: string | null;
    reviewId: string | null;
}

/** `GET /api/me/group-buy-reviews?page=` 의 한 줄 */
export interface MyGroupBuyReview extends GroupBuyReview {
    groupBuy: { id: string; title: string };
    orderNo: string;
    /** 고칠 수 있는 마지막 시각(작성 + 30일) */
    editableUntil: string;
}

export interface MyGroupBuyReviewPage {
    items: MyGroupBuyReview[];
    total: number;
    page: number;
    hasMore: boolean;
}

/** 쓰기·고치기 — 별점 필수, 글 10~1,000자, 사진 5장까지(먼저 올리고 ID 로 붙인다) */
export interface GroupBuyReviewRequest {
    rating: number;
    body: string;
    photoIds: string[];
}

/** 리뷰 신고 — 공용 신고(`POST /api/reports`, 대상 `GROUP_BUY_REVIEW`) */
export interface GroupBuyReviewReport {
    reason: ReportReason;
    detail: string | null;
}
