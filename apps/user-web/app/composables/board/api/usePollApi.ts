import type { PollView } from '~/composables/board/api/usePollApi.types';

/**
 * 게시글 투표 API — 서버 주소(`/api/posts/{id}/poll…`)가 있는 유일한 곳. 상태는 없다. 계약: `docs/features/post-poll-contract.md` 「API」.
 * 투표를 붙이고·고치고·빼는 것은 글 API(`POST /api/posts`·`PUT /api/posts/{id}` 본문 `poll`)이다.
 *
 *  - `GET /api/posts/{postId}/poll` → `PollView`(조회수를 올리지 않는다) · 없으면 `404 POLL_NOT_FOUND`
 *  - `POST /api/posts/{postId}/poll/votes { optionIds }` → 200 `PollView` — 단일 = 정확히 1개, 복수 = 1개 이상, 중복·남의 선택지 `400 fields.optionIds`.
 *      같은 선택 다시 = 200(멱등). 이미 투표했고 다른 선택: `changeAllowed` 면 바꾼다, 아니면 `409 ALREADY_VOTED`. 마감 `409 POLL_CLOSED`. 글쓴이도 투표할 수 있다.
 *  - `DELETE /api/posts/{postId}/poll` → 204 — 글쓴이는 첫 표 전만(`409 POLL_LOCKED`), 다른 사람 `403`.
 */
const PATH = {
    poll: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/poll`,
    votes: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/poll/votes`,
};

export function usePollApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        // REAL:
        poll: (postId: string): Promise<PollView> => client.get<PollView>(PATH.poll(postId)),

        // REAL:
        vote: (postId: string, optionIds: string[]): Promise<PollView> => client.post<PollView>(PATH.votes(postId), { optionIds }),

        // REAL:
        remove: (postId: string): Promise<void> => client.del<void>(PATH.poll(postId)),
    };
}
