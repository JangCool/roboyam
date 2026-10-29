import type { GroupBuyReviewDetail, GroupBuyReviewHideRequest, GroupBuyReviewListResponse, GroupBuyReviewQuery } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';

/**
 * 공구 리뷰 관리자 API — 서버 주소 문자열(`/api/admin/group-buy-reviews…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 「리뷰 규칙」(2b-6, 사용자 확정 2026-10-29).
 * 권한(가정 — Q&A 와 같게): 조회 `commerce.order.read` · 답글·숨김·숨김 풀기 `commerce.order.manage`. 감사: 숨김·숨김 풀기 `GROUPBUY_REVIEW_HIDE`(사유 코드·메모) · 답글 수정 `GROUPBUY_REVIEW_REPLY_EDIT`.
 *   - `GET  /api/admin/group-buy-reviews?tab=&rating=&photo=&noReply=&q=&page=&size=` → `GroupBuyReviewListResponse`
 *   - `GET  /api/admin/group-buy-reviews/{id}` → `GroupBuyReviewDetail` · `404 GROUP_BUY_REVIEW_NOT_FOUND`
 *   - `PUT  /api/admin/group-buy-reviews/{id}/reply` `{ body }` → `GroupBuyReviewDetail`(처음이면 작성자 앱 알림) · `409 GROUP_BUY_REVIEW_DELETED`
 *   - `POST /api/admin/group-buy-reviews/{id}/hide` `{ reasonCode, memo }` → `GroupBuyReviewDetail`(열린 신고도 처리됨 — 낮은 별점은 사유 코드에 없다)
 *   - `POST /api/admin/group-buy-reviews/{id}/unhide` `{ reason }`(5~200자) → `GroupBuyReviewDetail`
 */
const PATH = {
    root: '/api/admin/group-buy-reviews',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    sub: (id: string, tail: string) => `${PATH.one(id)}/${tail}`,
};

export function useGroupBuyReviewAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: GroupBuyReviewQuery): Promise<GroupBuyReviewListResponse> =>
            client.get<GroupBuyReviewListResponse>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<GroupBuyReviewDetail> =>
            client.get<GroupBuyReviewDetail>(PATH.one(id)),

        reply: (id: string, body: string): Promise<GroupBuyReviewDetail> =>
            client.put<GroupBuyReviewDetail>(PATH.sub(id, 'reply'), { body }),

        hide: (id: string, body: GroupBuyReviewHideRequest): Promise<GroupBuyReviewDetail> =>
            client.post<GroupBuyReviewDetail>(PATH.sub(id, 'hide'), body),

        unhide: (id: string, reason: string): Promise<GroupBuyReviewDetail> =>
            client.post<GroupBuyReviewDetail>(PATH.sub(id, 'unhide'), { reason }),
    };
}
