import type { GroupBuyQnaPage, GroupBuyQuestion, GroupBuyQuestionReport, GroupBuyQuestionRequest } from '~/composables/groupbuy/api/useGroupBuyQnaApi.types';

/**
 * 공구 Q&A(회원) API — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다. 계약 `commerce-groupbuy-contract.md` 「Q&A 규칙」(2b-6, 서버 연결 2026-10-29).
 *  - `GET    /api/group-buys/{id}/questions?mine=&page=` → `GroupBuyQnaPage`(로그인 없이 — 로그인했으면 내 비밀글 본문·할 수 있는 일이 그 회원 기준)
 *  - `POST   /api/group-buys/{id}/questions` `{ body, secret }` → `GroupBuyQuestion`(개인정보가 보이면 `secret=true`·`forcedSecret=true`)
 *      · `400 VALIDATION fields.body`(1~1,000자) · `401` · `403 ACCOUNT_RESTRICTED`(정지) · `409 GROUP_BUY_QNA_CLOSED`(중단된 공구) · `429 GROUP_BUY_QUESTION_TOO_FAST`
 *  - `PUT    /api/group-buy-questions/{qid}` `{ body, secret }` → `GroupBuyQuestion` · `409 GROUP_BUY_QUESTION_ANSWERED`(답변 뒤) · `404 GROUP_BUY_QUESTION_NOT_FOUND`
 *  - `POST   /api/group-buy-questions/{qid}/secret` → `GroupBuyQuestion`(공개 → 비밀글, 되돌리지 않는다)
 *  - `DELETE /api/group-buy-questions/{qid}` → 204(지워도 서버는 보관 기간 동안 감춘 채 남긴다)
 *  - 신고: 공용 `POST /api/reports` `{ targetType: 'GROUP_BUY_QUESTION', targetId, reason, detail }`(`useReportApi`)
 */
const PATH = {
    list: (id: string) => `/api/group-buys/${id}/questions`,
    one: (qid: string) => `/api/group-buy-questions/${qid}`,
    secret: (qid: string) => `/api/group-buy-questions/${qid}/secret`,
};

export function useGroupBuyQnaApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const reportApi = useReportApi();

    return {
        list: (groupBuyId: string, mine: boolean, page = 0): Promise<GroupBuyQnaPage> =>
            client.get<GroupBuyQnaPage>(PATH.list(groupBuyId), { query: { mine: mine || undefined, page } }),

        ask: (groupBuyId: string, body: GroupBuyQuestionRequest): Promise<GroupBuyQuestion> =>
            client.post<GroupBuyQuestion>(PATH.list(groupBuyId), body),

        update: (questionId: string, body: GroupBuyQuestionRequest): Promise<GroupBuyQuestion> =>
            client.put<GroupBuyQuestion>(PATH.one(questionId), body),

        makeSecret: (questionId: string): Promise<GroupBuyQuestion> =>
            client.post<GroupBuyQuestion>(PATH.secret(questionId)),

        remove: (questionId: string): Promise<void> =>
            client.del<void>(PATH.one(questionId)),

        report: (questionId: string, body: GroupBuyQuestionReport): Promise<void> =>
            reportApi.report({ targetType: 'GROUP_BUY_QUESTION', targetId: questionId, ...body }),
    };
}
