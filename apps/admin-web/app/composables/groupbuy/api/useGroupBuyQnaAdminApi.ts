import type { GroupBuyAnswerRequest, GroupBuyQnaDetail, GroupBuyQnaListResponse, GroupBuyQnaQuery } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';

/**
 * 공구 Q&A 관리자 API — 서버 주소 문자열(`/api/admin/group-buy-qna…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 「Q&A 규칙」·결정 35·60(2b-6, 서버 연결 2026-10-29).
 * 권한: 조회 `commerce.order.read` · 답변·답변 수정·숨김·숨김 풀기 `commerce.order.manage`.
 * 감사: 목록 조회·상세 열기 `GROUPBUY_QNA_VIEW`(개인정보 열람, 2년) · 숨김·숨김 풀기 `GROUPBUY_QNA_HIDE` · 답변 수정 `GROUPBUY_QNA_ANSWER_EDIT`.
 *   - `GET  /api/admin/group-buy-qna?tab=&overdue=&q=&page=&size=` → `GroupBuyQnaListResponse`
 *   - `GET  /api/admin/group-buy-qna/{id}` → `GroupBuyQnaDetail` · `404 GROUP_BUY_QUESTION_NOT_FOUND`
 *   - `PUT  /api/admin/group-buy-qna/{id}/answer` `{ body }` → `GroupBuyQnaDetail`(처음이면 답변 + 작성자 앱 알림, 있으면 수정 + 이력) · `400 fields.body` · `409 GROUP_BUY_QUESTION_DELETED`
 *   - `POST /api/admin/group-buy-qna/{id}/hide` `{ reason }`(5~200자) → `GroupBuyQnaDetail`(열린 신고도 처리됨) · `POST …/{id}/unhide` `{ reason }` → `GroupBuyQnaDetail`
 */
const PATH = {
    root: '/api/admin/group-buy-qna',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    sub: (id: string, tail: string) => `${PATH.one(id)}/${tail}`,
};

export function useGroupBuyQnaAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: GroupBuyQnaQuery): Promise<GroupBuyQnaListResponse> =>
            client.get<GroupBuyQnaListResponse>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<GroupBuyQnaDetail> =>
            client.get<GroupBuyQnaDetail>(PATH.one(id)),

        answer: (id: string, body: GroupBuyAnswerRequest): Promise<GroupBuyQnaDetail> =>
            client.put<GroupBuyQnaDetail>(PATH.sub(id, 'answer'), body),

        hide: (id: string, reason: string): Promise<GroupBuyQnaDetail> =>
            client.post<GroupBuyQnaDetail>(PATH.sub(id, 'hide'), { reason }),

        unhide: (id: string, reason: string): Promise<GroupBuyQnaDetail> =>
            client.post<GroupBuyQnaDetail>(PATH.sub(id, 'unhide'), { reason }),
    };
}
