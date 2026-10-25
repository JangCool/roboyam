import type {
    CommenterList,
    DebateSettingsBody,
    DebateStance,
    DebateSummary,
    PinRequest,
    PinRequestRejectBody,
    PinRequestReviewList,
    StanceChangeResult,
} from '~/composables/board/api/useDebateApi.types';

/**
 * 게시글 찬반 토론 API — 서버 주소(`/api/posts/{id}/stance`·`debate`·`commenters`·`pin-requests`, `/api/comments/{id}/pin-request`, `/api/pin-requests/{id}/…`)가 있는 유일한 곳.
 * 상태는 없다. 서버 `debate.controller.DebateController`. 댓글 목록의 구분(`kind`)·쓰기의 의견 표시(`opinion`·`stance`)는 댓글 API(`useCommentApi`)에 있다.
 * 글쓰기와 함께 토론을 여는 것은 글 API(`POST /api/posts` 본문 `debate`). 계약: docs/features/post-debate-contract.md.
 *
 *  - `PUT /api/posts/{postId}/stance { stance }` → 200 `{ stance, moved }` — **처음 고르기(투표)도 이것**. 로그인 필수(401), 글 읽기 규칙(404·403) → 댓글 쓰기 자격(403)
 *      → 400 `fields.stance`(중립이 꺼진 토론에서 새로 중립 포함) → 409 `DEBATE_NOT_OPEN` → 409 `STANCE_CHANGE_LOCKED`(「입장 바꾸기 허용」이 꺼진 토론에서 다른 값으로 바꾸기).
 *      같은 값이면 그대로(`moved 0`). 입장 취소는 없다.
 *      바꾸면 그 글에 내가 쓴 기존 의견(원댓글·답글, 숨긴 것 포함)도 새 입장으로 함께 바뀐다(소급) — `moved` = 옮긴 의견 중 지우지 않은 것의 수.
 *  - `PUT /api/posts/{postId}/debate { enabled, neutralEnabled, stanceChangeAllowed }` → 200 `DebateSummary`(글 상세의 `debate` 와 같은 모양) — 열기·설정·닫기. 로그인 필수.
 *      글쓴이(게시판 `debateEnabled` 켜짐 + 토론이 없거나 글쓴이가 연 토론) 또는 그 게시판 매니저만, 아니면 403 `DEBATE_MANAGE_DENIED`. 글쓴이가 투표 있는 토론을 닫으면 409 `DEBATE_HAS_VOTES`.
 *  - `GET /api/posts/{postId}/commenters?q=` → `{ items }` — 로그인 필수. 이 글에 **의견**을 쓴 회원(나·차단·탈퇴·비회원 제외) 최근 순 10명.
 *  - `POST /api/comments/{commentId}/pin-request` → 201 `PinRequest` — 내 의견 원댓글만. 오류 404 `COMMENT_NOT_FOUND` · 403 `FORBIDDEN` · 409 `PIN_REQUEST_NOT_OPINION`·`OPINION_UNAVAILABLE`·
 *      `DEBATE_NOT_OPEN`·`PIN_REQUEST_OFF`(게시판이 요청을 받지 않음)·`OPINION_ALREADY_PINNED`·`PIN_REQUEST_EXISTS`
 *  - `DELETE /api/comments/{commentId}/pin-request` → 204(내 대기 요청 취소, 없어도 204)
 *  - `GET /api/posts/{postId}/pin-requests` → `{ items }` — 확인 권한자(게시판 정책의 글쓴이·게시판 매니저)만, 아니면 403 `PIN_REQUEST_FORBIDDEN`
 *  - `POST /api/pin-requests/{id}/approve` · `POST /api/pin-requests/{id}/reject { reason? }` → 200 `PinRequest`. 오류 404 `PIN_REQUEST_NOT_FOUND` · 403 `PIN_REQUEST_FORBIDDEN` ·
 *      409 `PIN_REQUEST_DECIDED`·`OPINION_UNAVAILABLE` · 400 `fields.reason`(100자 초과)
 */
const PATH = {
    debate: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/debate`,
    stance: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/stance`,
    commenters: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/commenters`,
    pinRequest: (commentId: string) => `/api/comments/${encodeURIComponent(commentId)}/pin-request`,
    pinRequests: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/pin-requests`,
    approve: (id: string) => `/api/pin-requests/${encodeURIComponent(id)}/approve`,
    reject: (id: string) => `/api/pin-requests/${encodeURIComponent(id)}/reject`,
};

export function useDebateApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 입장 고르기(투표) — 처음 고르기와 바꾸기 모두 */
        vote: (postId: string, stance: DebateStance): Promise<StanceChangeResult> => client.put<StanceChangeResult>(PATH.stance(postId), { stance }),

        /** 토론 열기·설정·닫기 — 응답은 바뀐 뒤의 토론 요약 */
        settings: (postId: string, body: DebateSettingsBody): Promise<DebateSummary> => client.put<DebateSummary>(PATH.debate(postId), body),

        /** @ 지정 후보 — `q` 가 비면 최근에 의견을 쓴 회원들 */
        commenters: (postId: string, q: string, signal?: AbortSignal): Promise<CommenterList> => client.get<CommenterList>(PATH.commenters(postId), { query: { q }, signal }),

        /** 내 의견 고정 요청 보내기 */
        requestPin: (commentId: string): Promise<PinRequest> => client.post<PinRequest>(PATH.pinRequest(commentId)),

        /** 내 대기 고정 요청 취소 */
        cancelPin: (commentId: string): Promise<void> => client.del<void>(PATH.pinRequest(commentId)),

        /** 내가 확인할 대기 고정 요청 */
        pinRequests: (postId: string): Promise<PinRequestReviewList> => client.get<PinRequestReviewList>(PATH.pinRequests(postId)),

        approvePin: (id: string): Promise<PinRequest> => client.post<PinRequest>(PATH.approve(id)),

        rejectPin: (id: string, body: PinRequestRejectBody): Promise<PinRequest> => client.post<PinRequest>(PATH.reject(id), body),
    };
}
