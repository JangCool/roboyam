import type {
    DebateItem,
    DebateOpinion,
    DebateOpinionListView,
    DebateOpinionQuery,
    DebatePinRequest,
    DebatePinRequestListView,
    DebateSaveRequest,
} from '~/composables/content/api/useDebateApi.types';

/**
 * 글 토론 API — 서버 주소(`/api/admin/debates/{postId}…`)가 있는 **유일한 곳**이다. 상태는 없다. 찬반 토론은 게시글 기능이라 모두 **글 번호 하나**로 다룬다.
 * 계약: `docs/features/post-debate-contract.md` 「API · 관리자」 + 2026-10-24 범위 변경(사용자 결정): 토론 목록 화면·목록/글 찾기 API 는 만들지 않고,
 * 글 번호로 쓰는 설정 대화창·의견 서랍을 하이라이트 화면과 (다음 작업) 게시글 관리가 같이 쓴다. 조회 `post.read`, 바꾸기 `post.manage`.
 *   - `GET /api/admin/debates/{postId}` → 한 줄(닫혀 있으면 404 `DEBATE_NOT_FOUND`)
 *   - `PUT /{postId} { enabled, neutralEnabled, stanceChangeAllowed }` → 200 한 줄 · 닫으면 204(운영자는 투표가 있어도 닫힌다)
 *   - `GET /{postId}/opinions?stance=&page=&size=` · `POST|DELETE /{postId}/opinions/{commentId}/pin|hide` → 200 바뀐 의견 · 404 `DEBATE_OPINION_NOT_FOUND`
 *   - `GET /{postId}/pin-requests`(`post.read`) · `POST /{postId}/pin-requests/{requestId}/approve` · `POST …/reject { reason? }` → 200 요청(처리 뒤)
 *     · 404 `PIN_REQUEST_NOT_FOUND` · 409 `PIN_REQUEST_DECIDED` · 409 `OPINION_UNAVAILABLE` · 400 `fields.reason`
 */
const ROOT = '/api/admin/debates';
const PATH = {
    one: (postId: string) => `${ROOT}/${encodeURIComponent(postId)}`,
    opinions: (postId: string) => `${ROOT}/${encodeURIComponent(postId)}/opinions`,
    pin: (postId: string, commentId: string) => `${ROOT}/${encodeURIComponent(postId)}/opinions/${encodeURIComponent(commentId)}/pin`,
    hide: (postId: string, commentId: string) => `${ROOT}/${encodeURIComponent(postId)}/opinions/${encodeURIComponent(commentId)}/hide`,
    pinRequests: (postId: string) => `${ROOT}/${encodeURIComponent(postId)}/pin-requests`,
    pinRequest: (postId: string, requestId: string, action: 'approve' | 'reject') => `${ROOT}/${encodeURIComponent(postId)}/pin-requests/${encodeURIComponent(requestId)}/${action}`,
};

export function useDebateApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 그 글의 열린 토론 — 닫혀 있으면 404 `DEBATE_NOT_FOUND` */
        get: (postId: string): Promise<DebateItem> => client.get<DebateItem>(PATH.one(postId)),

        /** 열기·설정 — 200 한 줄. 닫기(`enabled false`)는 204 라 null */
        save: async (postId: string, body: DebateSaveRequest): Promise<DebateItem | null> => (await client.put<DebateItem | undefined>(PATH.one(postId), body)) ?? null,

        opinions: (postId: string, query: DebateOpinionQuery): Promise<DebateOpinionListView> =>
            client.get<DebateOpinionListView>(PATH.opinions(postId), { query: { page: query.page, size: query.size, ...(query.stance ? { stance: query.stance } : {}) } }),

        pin: (postId: string, commentId: string): Promise<DebateOpinion> => client.post<DebateOpinion>(PATH.pin(postId, commentId)),
        unpin: (postId: string, commentId: string): Promise<DebateOpinion> => client.del<DebateOpinion>(PATH.pin(postId, commentId)),
        hide: (postId: string, commentId: string): Promise<DebateOpinion> => client.post<DebateOpinion>(PATH.hide(postId, commentId)),
        unhide: (postId: string, commentId: string): Promise<DebateOpinion> => client.del<DebateOpinion>(PATH.hide(postId, commentId)),

        /** 대기 중인 의견 고정 요청(오래된 것부터) */
        pinRequests: (postId: string): Promise<DebatePinRequestListView> => client.get<DebatePinRequestListView>(PATH.pinRequests(postId)),
        /** 고정 요청 승인 — 그 의견을 고정한다 */
        approvePinRequest: (postId: string, requestId: string): Promise<DebatePinRequest> => client.post<DebatePinRequest>(PATH.pinRequest(postId, requestId, 'approve')),
        /** 고정 요청 거절 — 사유(선택)는 요청한 회원에게 알림 본문으로 간다 */
        rejectPinRequest: (postId: string, requestId: string, reason: string | null): Promise<DebatePinRequest> =>
            client.post<DebatePinRequest>(PATH.pinRequest(postId, requestId, 'reject'), { reason }),
    };
}
