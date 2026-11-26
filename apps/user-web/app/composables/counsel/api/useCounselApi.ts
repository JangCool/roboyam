import type { CounselPickRequest, CounselRanking, PostCounselView } from '~/composables/counsel/api/useCounselApi.types';

/**
 * AI 연애상담소 API(연애·결혼 게시판 「AI 상담소」 말머리 — `docs/features/ai-love-counsel-contract.md`) — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다. **서버 후보**(아직 서버 없음).
 *
 *  - `GET /api/posts/{id}/counsel` → `PostCounselView`(상담 상태 · AI 상담사 셋과 그 답 · 고른 답 · 내가 고를 수 있는가). 상담 글이 아니면 404 `COUNSEL_NOT_FOUND`
 *  - `PUT /api/posts/{id}/counsel/pick` `{ commentId }` → `PostCounselView` — 사연 쓴 사람만(403 `COUNSEL_PICK_FORBIDDEN`), 하나만(409 `COUNSEL_ALREADY_PICKED`),
 *      위험 사연은 안 됨(409 `COUNSEL_PICK_UNAVAILABLE`), 내 답은 안 됨(409 `COUNSEL_PICK_OWN`). 다시 고르기는 미확정
 *  - `GET /api/boards/{slug}/counsel/ranking` → `CounselRanking`(이번 주 「가장 도움 된 조언」 기준 — 사람 회원 한 줄 + AI 상담사 한 명씩)
 * 회원 답변은 일반 댓글 API(`useCommentApi`) 그대로다. 상담 글 표시는 말머리 `counsel`·글 줄 `counsel`(게시판·글 API 응답의 칸).
 *
 * ⚠ MOCK — 개발 서버(`import.meta.dev`)면 `mock/counsel-mock.ts` 가 답한다(실제 호출 줄은 그 아래에 그대로 있다). 운영 빌드에는 분기째 빠진다.
 */
const PATH = {
    counsel: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/counsel`,
    pick: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/counsel/pick`,
    ranking: (slug: string) => `/api/boards/${encodeURIComponent(slug)}/counsel/ranking`,
};

export function useCounselApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 상담 글의 AI 상담 칸 */
        post: async (postId: string, signal?: AbortSignal): Promise<PostCounselView> => {
            if (import.meta.dev) return (await import('~/composables/counsel/api/mock/counsel-mock')).default.counsel(postId); // MOCK:
            return client.get<PostCounselView>(PATH.counsel(postId), { signal }); // REAL:
        },

        /** 「이 조언 고르기」 — AI 상담사 답이든 회원 답이든 댓글 ID 하나 */
        pick: async (postId: string, commentId: string): Promise<PostCounselView> => {
            if (import.meta.dev) return (await import('~/composables/counsel/api/mock/counsel-mock')).default.pick(postId, commentId); // MOCK:
            return client.put<PostCounselView>(PATH.pick(postId), { commentId } satisfies CounselPickRequest); // REAL:
        },

        /** 이번 주 상담 순위 */
        ranking: async (slug: string, signal?: AbortSignal): Promise<CounselRanking> => {
            if (import.meta.dev) return (await import('~/composables/counsel/api/mock/counsel-mock')).default.ranking(slug); // MOCK:
            return client.get<CounselRanking>(PATH.ranking(slug), { signal }); // REAL:
        },
    };
}
