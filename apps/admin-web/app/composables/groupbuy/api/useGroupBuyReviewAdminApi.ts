import { mockReviewAdminDetail, mockReviewAdminList, mockReviewHide, mockReviewReply, mockReviewUnhide } from '~/composables/groupbuy/api/mock/groupbuy-review-admin-mock';
import type { GroupBuyReviewDetail, GroupBuyReviewHideRequest, GroupBuyReviewListResponse, GroupBuyReviewQuery } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';

/**
 * 공구 리뷰 관리자 API — 서버 주소 문자열(`/api/admin/group-buy-reviews…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 「리뷰 규칙」(2b-6, 가정 — 사용자 확인 대기).
 * 권한(가정 — Q&A 와 같게): 조회 `commerce.order.read` · 답글·숨김·숨김 풀기 `commerce.order.manage`. 감사: 숨김·숨김 풀기 `GROUPBUY_REVIEW_HIDE`(사유 코드·메모) · 답글 수정 `GROUPBUY_REVIEW_REPLY_EDIT`.
 *   - `GET  /api/admin/group-buy-reviews?tab=&rating=&photo=&noReply=&q=&page=&size=` → `GroupBuyReviewListResponse`
 *   - `GET  /api/admin/group-buy-reviews/{id}` → `GroupBuyReviewDetail` · `404 GROUP_BUY_REVIEW_NOT_FOUND`
 *   - `PUT  /api/admin/group-buy-reviews/{id}/reply` `{ body }` → `GroupBuyReviewDetail`(처음이면 작성자 앱 알림) · `409 GROUP_BUY_REVIEW_DELETED`
 *   - `POST /api/admin/group-buy-reviews/{id}/hide` `{ reasonCode, memo }` → `GroupBuyReviewDetail`(열린 신고도 처리됨 — 낮은 별점은 사유 코드에 없다)
 *   - `POST /api/admin/group-buy-reviews/{id}/unhide` `{ reason }`(5~200자) → `GroupBuyReviewDetail`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/groupbuy-review-admin-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/group-buy-reviews',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    sub: (id: string, tail: string) => `${PATH.one(id)}/${tail}`,
};

export function useGroupBuyReviewAdminApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        list: (query: GroupBuyReviewQuery): Promise<GroupBuyReviewListResponse> =>
            // MOCK:
            mockReviewAdminList(query),
        // REAL: client.get<GroupBuyReviewListResponse>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<GroupBuyReviewDetail> =>
            // MOCK:
            mockReviewAdminDetail(id),
        // REAL: client.get<GroupBuyReviewDetail>(PATH.one(id)),

        reply: (id: string, body: string): Promise<GroupBuyReviewDetail> =>
            // MOCK:
            mockReviewReply(id, body),
        // REAL: client.put<GroupBuyReviewDetail>(PATH.sub(id, 'reply'), { body }),

        hide: (id: string, body: GroupBuyReviewHideRequest): Promise<GroupBuyReviewDetail> =>
            // MOCK:
            mockReviewHide(id, body),
        // REAL: client.post<GroupBuyReviewDetail>(PATH.sub(id, 'hide'), body),

        unhide: (id: string, reason: string): Promise<GroupBuyReviewDetail> =>
            // MOCK:
            mockReviewUnhide(id, reason),
        // REAL: client.post<GroupBuyReviewDetail>(PATH.sub(id, 'unhide'), { reason }),
    };
}
