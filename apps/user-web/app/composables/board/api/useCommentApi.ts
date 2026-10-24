import type { CommentKind } from '~/composables/board/api/useDebateApi.types';
import type {
    CommentDeleteRequest,
    CommentLikeResult,
    CommentListResponse,
    CommentSort,
    CommentUpdateRequest,
    CommentView,
    CommentWriteRequest,
} from '~/composables/board/api/useCommentApi.types';

/**
 * 댓글 API — 서버 주소(`/api/posts/{id}/comments`·`/api/comments/{id}`)가 있는 유일한 곳. 상태는 없다(화면 상태는 `usePostComments`).
 * 규약: `docs/features/board-public-contract.md`.
 *
 *  - `GET /api/posts/{id}/comments?sort=OLDEST|LATEST&kind=ALL|GENERAL|PRO|CON` 원댓글 + 답글(1단계) + 베스트 + 보이는 수 + 하이라이트 토론 칸(`debate`). 비회원이 보면 최근 N분 댓글은 `state: DELAYED`(본문 없음, 같은 IP 의 비회원에게는 바로 보인다)
 *      `kind` 는 원댓글의 입장으로 가른다(답글은 원댓글을 따라간다), 그 밖 값은 400 `fields.kind`
 *  - `POST /api/posts/{id}/comments` 쓰기(비회원은 `guest` 칸, 토론 입장은 `stance`) → 새 댓글(쓴 사람에게 늘 `VISIBLE`, `stance` 배지 포함). 토론 오류 409 `DEBATE_NOT_OPEN`·`STANCE_LOCKED`
 *  - `PUT /api/comments/{id}` 고치기 · `POST /api/comments/{id}/delete` 지우기(204) — 비회원 댓글은 본문에 `password`
 *  - `POST /api/comments/{id}/like` 추천(취소 없음) → `{ likeCount, liked }`
 * 오류: `400 VALIDATION` + `fields`(`content`·`guest.*`·`parentId` — 답글 대상 오류 포함), 금지어 `400 BLOCKED_WORD`, 간격 `429 COMMENT_TOO_FAST`, 하루 한도 `429 COMMENT_DAILY_LIMIT`,
 * 비밀번호 `403 PASSWORD_MISMATCH`·`429 PASSWORD_ATTEMPTS_EXCEEDED`, 없는 댓글 `404 COMMENT_NOT_FOUND`, 숨긴 댓글 `403 COMMENT_HIDDEN`, 로그인 필요 `401 UNAUTHENTICATED`, 쓸 수 없음 `403 {WriteBlockedReason}`.
 *
 * 서버 연결됨(2026-10-22).
 */
const PATH = {
    list: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/comments`,
    comment: (id: string) => `/api/comments/${encodeURIComponent(id)}`,
    remove: (id: string) => `/api/comments/${encodeURIComponent(id)}/delete`,
    like: (id: string) => `/api/comments/${encodeURIComponent(id)}/like`,
};

export function useCommentApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (postId: string, sort: CommentSort, kind: CommentKind, signal?: AbortSignal): Promise<CommentListResponse> =>
            client.get<CommentListResponse>(PATH.list(postId), { query: { sort, ...(kind === 'ALL' ? {} : { kind }) }, signal }),

        create: (postId: string, body: CommentWriteRequest): Promise<CommentView> => client.post<CommentView>(PATH.list(postId), body),

        update: (id: string, body: CommentUpdateRequest): Promise<CommentView> => client.put<CommentView>(PATH.comment(id), body),

        remove: (id: string, body: CommentDeleteRequest): Promise<void> => client.post<void>(PATH.remove(id), body),

        like: (id: string): Promise<CommentLikeResult> => client.post<CommentLikeResult>(PATH.like(id)),
    };
}
