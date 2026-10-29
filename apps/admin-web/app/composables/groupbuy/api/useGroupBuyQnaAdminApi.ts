import { mockQnaAdminDetail, mockQnaAdminList, mockQnaAnswer, mockQnaHide, mockQnaUnhide } from '~/composables/groupbuy/api/mock/groupbuy-qna-admin-mock';
import type { GroupBuyAnswerRequest, GroupBuyQnaDetail, GroupBuyQnaListResponse, GroupBuyQnaQuery } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';

/**
 * 공구 Q&A 관리자 API — 서버 주소 문자열(`/api/admin/group-buy-qna…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 「Q&A 규칙」·결정 35·60(2b-6, 화면 확정 전 가정).
 * 권한: 조회 `commerce.order.read` · 답변·답변 수정·숨김·숨김 풀기 `commerce.order.manage`.
 * 감사: 목록 조회·상세 열기 `GROUPBUY_QNA_VIEW`(개인정보 열람, 2년) · 숨김·숨김 풀기 `GROUPBUY_QNA_HIDE` · 답변 수정 `GROUPBUY_QNA_ANSWER_EDIT`.
 *   - `GET  /api/admin/group-buy-qna?tab=&overdue=&q=&page=&size=` → `GroupBuyQnaListResponse`
 *   - `GET  /api/admin/group-buy-qna/{id}` → `GroupBuyQnaDetail` · `404 GROUP_BUY_QUESTION_NOT_FOUND`
 *   - `PUT  /api/admin/group-buy-qna/{id}/answer` `{ body }` → `GroupBuyQnaDetail`(처음이면 답변 + 작성자 앱 알림, 있으면 수정 + 이력) · `400 fields.body` · `409 GROUP_BUY_QUESTION_DELETED`
 *   - `POST /api/admin/group-buy-qna/{id}/hide` `{ reason }`(5~200자) → `GroupBuyQnaDetail`(열린 신고도 처리됨) · `POST …/{id}/unhide` `{ reason }` → `GroupBuyQnaDetail`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/groupbuy-qna-admin-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/group-buy-qna',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    sub: (id: string, tail: string) => `${PATH.one(id)}/${tail}`,
};

export function useGroupBuyQnaAdminApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        list: (query: GroupBuyQnaQuery): Promise<GroupBuyQnaListResponse> =>
            // MOCK:
            mockQnaAdminList(query),
        // REAL: client.get<GroupBuyQnaListResponse>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<GroupBuyQnaDetail> =>
            // MOCK:
            mockQnaAdminDetail(id),
        // REAL: client.get<GroupBuyQnaDetail>(PATH.one(id)),

        answer: (id: string, body: GroupBuyAnswerRequest): Promise<GroupBuyQnaDetail> =>
            // MOCK:
            mockQnaAnswer(id, body),
        // REAL: client.put<GroupBuyQnaDetail>(PATH.sub(id, 'answer'), body),

        hide: (id: string, reason: string): Promise<GroupBuyQnaDetail> =>
            // MOCK:
            mockQnaHide(id, reason),
        // REAL: client.post<GroupBuyQnaDetail>(PATH.sub(id, 'hide'), { reason }),

        unhide: (id: string, reason: string): Promise<GroupBuyQnaDetail> =>
            // MOCK:
            mockQnaUnhide(id, reason),
        // REAL: client.post<GroupBuyQnaDetail>(PATH.sub(id, 'unhide'), { reason }),
    };
}
