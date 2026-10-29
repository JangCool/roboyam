import { mockMyReviews, mockReviewCreate, mockReviewList, mockReviewPhoto, mockReviewRemove, mockReviewReport, mockReviewTargets, mockReviewUpdate } from '~/composables/groupbuy/api/mock/groupbuy-review-mock';
import type { MyGroupBuyOrderDetail } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type {
    GroupBuyReviewPage,
    GroupBuyReviewPhoto,
    GroupBuyReviewReport,
    GroupBuyReviewRequest,
    GroupBuyReviewSort,
    GroupBuyReviewTarget,
    MyGroupBuyReview,
    MyGroupBuyReviewPage,
} from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';

/**
 * 공구 리뷰(회원) API — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다. 계약 「리뷰 규칙」(2b-6, 가정 — 사용자 확인 대기).
 *  - `GET    /api/group-buys/{id}/reviews?sort=&photo=&page=` → `GroupBuyReviewPage`(로그인 없이 — 같은 상품의 다른 차수 리뷰 포함, 숨긴 리뷰는 작성자에게만)
 *  - `GET    /api/me/orders/{orderNo}/reviews` → `GroupBuyReviewTarget[]`(주문 줄마다 쓸 수 있는지)
 *  - `POST   /api/me/orders/{orderNo}/items/{itemId}/review` `{ rating, body, photoIds }` → `MyGroupBuyReview`
 *      · `400 VALIDATION fields.rating|body|photoIds` · `409 GROUP_BUY_REVIEW_EXISTS|GROUP_BUY_REVIEW_NOT_WRITABLE`(배송완료 전·90일 지남·모두 취소·반품)
 *  - `PUT    /api/me/group-buy-reviews/{id}` → `MyGroupBuyReview` · `409 GROUP_BUY_REVIEW_EDIT_EXPIRED`(30일) — 이전 내용은 수정 이력으로
 *  - `DELETE /api/me/group-buy-reviews/{id}` → 204 · `GET /api/me/group-buy-reviews?page=` → `MyGroupBuyReviewPage`
 *  - `POST   /api/me/group-buy-reviews/photos`(multipart `file`) → `GroupBuyReviewPhoto`(반품 요청 사진과 같은 방식 — 붙지 않으면 24시간 뒤 정리)
 *  - 신고: 공용 `POST /api/reports` `{ targetType: 'GROUP_BUY_REVIEW', … }`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/groupbuy-review-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    list: (id: string) => `/api/group-buys/${id}/reviews`,
    targets: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/reviews`,
    create: (orderNo: string, itemId: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/items/${encodeURIComponent(itemId)}/review`,
    mine: '/api/me/group-buy-reviews',
    one: (id: string) => `/api/me/group-buy-reviews/${encodeURIComponent(id)}`,
    photos: '/api/me/group-buy-reviews/photos',
};

export function useGroupBuyReviewApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // REAL: const reportApi = useReportApi();
    // MOCK: 목업은 로그인 여부로 「내 리뷰」를 정한다
    const { user } = useAuth();
    void PATH;

    return {
        list: (groupBuyId: string, sort: GroupBuyReviewSort, photoOnly: boolean, page = 0): Promise<GroupBuyReviewPage> =>
            // MOCK:
            (void groupBuyId, mockReviewList(!!user.value, sort, photoOnly, page)),
        // REAL: client.get<GroupBuyReviewPage>(PATH.list(groupBuyId), { query: { sort, photo: photoOnly || undefined, page } }),

        targets: (order: MyGroupBuyOrderDetail): Promise<GroupBuyReviewTarget[]> =>
            // MOCK:
            mockReviewTargets(order),
        // REAL: client.get<GroupBuyReviewTarget[]>(PATH.targets(order.orderNo)),

        create: (order: MyGroupBuyOrderDetail, itemId: string, body: GroupBuyReviewRequest): Promise<MyGroupBuyReview> =>
            // MOCK:
            mockReviewCreate(order, itemId, body),
        // REAL: client.post<MyGroupBuyReview>(PATH.create(order.orderNo, itemId), body),

        update: (id: string, body: GroupBuyReviewRequest): Promise<MyGroupBuyReview> =>
            // MOCK:
            mockReviewUpdate(id, body),
        // REAL: client.put<MyGroupBuyReview>(PATH.one(id), body),

        remove: (id: string): Promise<void> =>
            // MOCK:
            mockReviewRemove(id),
        // REAL: client.del<void>(PATH.one(id)),

        mine: (page = 0): Promise<MyGroupBuyReviewPage> =>
            // MOCK:
            mockMyReviews(page),
        // REAL: client.get<MyGroupBuyReviewPage>(PATH.mine, { query: { page } }),

        uploadPhoto: (file: File): Promise<GroupBuyReviewPhoto> =>
            // MOCK:
            mockReviewPhoto(file),
        // REAL: client.upload<GroupBuyReviewPhoto>(PATH.photos, (() => { const form = new FormData(); form.append('file', file); return form; })()),

        report: (id: string, body: GroupBuyReviewReport): Promise<void> =>
            // MOCK:
            mockReviewReport(id, body),
        // REAL: reportApi.report({ targetType: 'GROUP_BUY_REVIEW', targetId: id, ...body }),
    };
}
