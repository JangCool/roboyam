import type { CommenterList, DebateStance, StanceBody } from '~/composables/board/api/useDebateApi.types';

/**
 * 하이라이트 토론 API — 서버 주소(`/api/posts/{id}/stance`·`/api/posts/{id}/commenters`)가 있는 유일한 곳. 상태는 없다. 서버 `debate.controller.DebateController`.
 * 댓글 목록의 구분(`kind`)·쓰기의 입장(`stance`)은 댓글 API(`useCommentApi`)에 있다.
 *
 *  - `PUT /api/posts/{postId}/stance { stance }` → 200 `{ stance }` — 로그인 필수(401). 글 읽기 규칙(404 POST_NOT_FOUND · 403 POST_HIDDEN · 403 BOARD_INACTIVE) → 댓글 쓰기 자격(댓글 쓰기와 같은 403)
 *      → 400 `fields.stance` → 409 `DEBATE_NOT_OPEN`(토론이 닫힘) → 409 `STANCE_NOT_SET`(정한 적 없음). 같은 값이면 그대로(멱등). 바꾼 뒤 쓰는 댓글부터 새 배지.
 *  - `GET /api/posts/{postId}/commenters?q=` → 200 `{ items: [{ author, parentId, replyToId }] }` — 로그인 필수(401). 이 글에 댓글을 쓴 회원 중 나·차단한 회원·탈퇴·비회원 제외, 닉네임에 `q`(20자), 최근 순 10명.
 */
const PATH = {
    stance: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/stance`,
    commenters: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/commenters`,
};

export function useDebateApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 입장 바꾸기 — 이미 정한 회원만 */
        changeStance: (postId: string, stance: DebateStance): Promise<StanceBody> => client.put<StanceBody>(PATH.stance(postId), { stance }),

        /** @ 지정 후보 — `q` 가 비면 최근에 쓴 회원들 */
        commenters: (postId: string, q: string, signal?: AbortSignal): Promise<CommenterList> => client.get<CommenterList>(PATH.commenters(postId), { query: { q }, signal }),
    };
}
