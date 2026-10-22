import type {
    PostDeleteRequest,
    PostDeleteResult,
    PostDetail,
    PostLikeResult,
    PostUpdateRequest,
    PostWriteRequest,
    PostWriteResult,
} from '~/composables/board/api/usePostApi.types';

/**
 * 글 API — 서버 주소(`/api/posts…`)가 있는 유일한 곳. 상태는 없다. 목록은 게시판 아래라 `useBoardApi().posts`.
 * 규약: `docs/features/board-public-contract.md`.
 *
 *  - `GET /api/posts/{id}` 글 하나(조회수 증가) · 가려진 글 `403 POST_HIDDEN` · 없는 글 `404 POST_NOT_FOUND`
 *  - `POST /api/posts` 글쓰기(비회원은 `guest` 칸) → `201 { id }`
 *  - `PUT /api/posts/{id}` 고치기(비회원 글은 본문에 `password`) · 보호 중 `409 POST_PROTECTED` · 숨긴 글 `403 POST_HIDDEN`
 *  - `POST /api/posts/{id}/delete` 지우기(비회원 글은 본문에 `password` — 비밀번호를 주소에 싣지 않으려고 본문 있는 POST) → `{ scheduledAt }`(보호 중 예약 삭제면 시각)
 *  - `POST /api/posts/{id}/verify-password` 비회원 글 비밀번호 확인(204) · 틀리면 `403 PASSWORD_MISMATCH` · 여러 번 틀리면 `429 PASSWORD_ATTEMPTS_EXCEEDED`
 *  - `POST /api/posts/{id}/like` 추천(취소 없음) → `{ likeCount, liked }` · 이미 추천 `409 ALREADY_LIKED` · 내 글 `400 CANNOT_LIKE_OWN`
 * 오류: 입력 `400 VALIDATION` + `fields`(`title`·`content`·`prefixId`·`adult`·`guest.name`·`guest.password`·`guest.captchaToken`), 금지어 `400 BLOCKED_WORD`,
 * 작성 간격 `429 POST_TOO_FAST`(+`retryAfterSeconds`), 하루 한도 `429 POST_DAILY_LIMIT`, 같은 글 반복 `409 DUPLICATE_POST`, 로그인 필요 `401 UNAUTHENTICATED`, 쓸 수 없는 게시판 `403 {WriteBlockedReason}`.
 *
 * 서버 연결됨(2026-10-22).
 */
const PATH = {
    posts: '/api/posts',
    post: (id: string) => `/api/posts/${encodeURIComponent(id)}`,
    remove: (id: string) => `/api/posts/${encodeURIComponent(id)}/delete`,
    verify: (id: string) => `/api/posts/${encodeURIComponent(id)}/verify-password`,
    like: (id: string) => `/api/posts/${encodeURIComponent(id)}/like`,
};

export function usePostApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        post: (id: string, signal?: AbortSignal): Promise<PostDetail> => client.get<PostDetail>(PATH.post(id), { signal }),

        create: (body: PostWriteRequest): Promise<PostWriteResult> => client.post<PostWriteResult>(PATH.posts, body),

        update: (id: string, body: PostUpdateRequest): Promise<PostDetail> => client.put<PostDetail>(PATH.post(id), body),

        remove: (id: string, body: PostDeleteRequest): Promise<PostDeleteResult> => client.post<PostDeleteResult>(PATH.remove(id), body),

        verifyPassword: (id: string, password: string): Promise<void> => client.post<void>(PATH.verify(id), { password }),

        like: (id: string): Promise<PostLikeResult> => client.post<PostLikeResult>(PATH.like(id)),
    };
}
