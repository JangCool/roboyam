import type { CounselPickRequest, CounselRanking, PostCounselView } from '~/composables/counsel/api/useCounselApi.types';

/**
 * AI 연애상담소 API(연애·결혼 게시판 「AI 상담소」 말머리 — `docs/features/ai-love-counsel-contract.md` 「API」) — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다.
 *
 *  - `GET /api/posts/{id}/counsel` → `PostCounselView`(상담 상태 · AI 상담사와 그 답 · 고른 답 · 내가 고를 수 있는가). 글 보기와 같은 열기 판정, 상담 글이 아니면 404 `COUNSEL_NOT_FOUND`
 *  - `PUT /api/posts/{id}/counsel/pick` `{ commentId }` → 바뀐 `PostCounselView` — 로그인·CSRF. 403 `COUNSEL_PICK_FORBIDDEN`(사연 쓴 사람 아님) ·
 *      409 `COUNSEL_PICK_UNAVAILABLE`(위험·판정 전 · 지운·숨긴 댓글 · 답글) · 409 `COUNSEL_ALREADY_PICKED`(다시 고르기 없음) · 409 `COUNSEL_PICK_OWN` · 404 `COMMENT_NOT_FOUND`
 *  - `GET /api/boards/{slug}/counsel/ranking` → `CounselRanking`(이번 주 「가장 도움 된 조언」 기준 — 사람 회원 한 줄 + AI 상담사 한 명씩). 상담소 말머리가 없으면 404 `COUNSEL_NOT_FOUND`
 * 회원 답변은 일반 댓글 API(`useCommentApi`) 그대로다(서버가 상담사 답을 그 목록·`total` 에서 뺀다). 상담 글 표시는 말머리 `counsel`·글 줄 `counsel`(게시판·글 API 응답의 칸).
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
        post: (postId: string, signal?: AbortSignal): Promise<PostCounselView> => client.get<PostCounselView>(PATH.counsel(postId), { signal }),

        /** 「이 조언 고르기」 — AI 상담사 답이든 회원 답이든 댓글 ID 하나 */
        pick: (postId: string, commentId: string): Promise<PostCounselView> => client.put<PostCounselView>(PATH.pick(postId), { commentId } satisfies CounselPickRequest),

        /** 이번 주 상담 순위 */
        ranking: (slug: string, signal?: AbortSignal): Promise<CounselRanking> => client.get<CounselRanking>(PATH.ranking(slug), { signal }),
    };
}
