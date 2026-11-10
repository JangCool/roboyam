import type {
    PostDeleteRequest,
    PostDeleteResult,
    PostDetail,
    PostLikeResult,
    PostProcessStatus,
    PostProcessStatusHistory,
    PostProcessStatusRequest,
    PostProcessStatusResult,
    PostSecretUnlockRequest,
    PostUpdateRequest,
    PostWriteRequest,
    PostWriteResult,
} from '~/composables/board/api/usePostApi.types';

import AiMemberMock from '~/composables/aimember/api/mock/ai-member-mock'; // MOCK: AI 회원 표시(author.ai·aiOnly·프로필 ai)
/**
 * 글 API — 서버 주소(`/api/posts…`)가 있는 유일한 곳. 상태는 없다. 목록은 게시판 아래라 `useBoardApi().posts`.
 * 규약: `docs/features/board-public-contract.md`.
 *
 *  - `GET /api/posts/{id}` 글 하나(조회수 증가) · 가려진 글 `403 POST_HIDDEN` · 없는 글 `404 POST_NOT_FOUND`
 *  - `POST /api/posts` 글쓰기(비회원은 `guest` 칸) → `201 { id, warnings }`
 *  - `PUT /api/posts/{id}` 고치기(비회원 글은 본문에 `password`) · 보호 중 `409 POST_PROTECTED` · 숨긴 글 `403 POST_HIDDEN`
 *  - `POST /api/posts/{id}/delete` 지우기(비회원 글은 본문에 `password` — 비밀번호를 주소에 싣지 않으려고 본문 있는 POST) → `{ scheduledAt }`(보호 중 예약 삭제면 시각)
 *  - `POST /api/posts/{id}/verify-password` 비회원 글 비밀번호 확인(204) · 틀리면 `403 PASSWORD_MISMATCH` · 여러 번 틀리면 `429 PASSWORD_ATTEMPTS_EXCEEDED`
 *  - `POST /api/posts/{id}/like` 추천(취소 없음) → `{ likeCount, liked }` · 이미 추천 `409 ALREADY_LIKED` · 내 글 `400 CANNOT_LIKE_OWN`
 * 오류: 입력 `400 VALIDATION` + `fields`(`title`·`content`·`prefixId`·`adult`·`guest.name`·`guest.password`·`guest.captchaToken`), 금지어 `400 BLOCKED_WORD`,
 * 작성 간격 `429 POST_TOO_FAST`(+`retryAfterSeconds`), 하루 한도 `429 POST_DAILY_LIMIT`, 같은 글 반복 `409 DUPLICATE_POST`, 로그인 필요 `401 UNAUTHENTICATED`, 쓸 수 없는 게시판 `403 {WriteBlockedReason}`.
 * 핫딜 게시판(`kind DEAL` — `hotdeal-contract.md`): 쓰기 본문 `deal` 필수(칸 오류 `fields."deal.*"` — 사이트 제휴 방식의 개인 제휴 링크도 `fields."deal.productUrl"`),
 * 핫딜 쓰기 자격 `403 GRADE_TOO_LOW`·`EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`, 하루 한도 `429 DEAL_DAILY_POST_LIMIT {limit}`,
 * 같은 판매처 간격 `429 DEAL_SAME_STORE_TOO_SOON {remainingMinutes, intervalMinutes}`, 끝난 딜 고치기 `409 DEAL_ENDED`.
 * 고치기 본문 `deal` 은 바꿨을 때만 싣는다(빼면 그대로 — JSON null 은 거절). 쓰기·고치기 응답 `warnings[]`(예 `DEAL_LINK_BLOCKED`).
 *
 * 서버 연결됨(2026-10-22). 핫딜 칸은 서버 1단계에 연결(2026-11-01).
 * 사업자 핫딜 글(2차 — `deal.source BUSINESS`)은 작성자 고치기·지우기를 서버가 `403 DEAL_BUSINESS_POST_LOCKED` 로 거절한다(화면은 단추를 숨김).
 * 판매자가 내린 사업자 핫딜 글(광고주 일시 정지·중단 — 서버 연결 2026-11-02, `biz-pause-contract.md`): 글 단건 조회만 404 가 아니라 `403 DEAL_SELLER_HIDDEN` + extras
 *   `{ state: PAUSED|STOPPED, boardSlug, boardName, boardNameEn, applicationId(작성자에게만 — 아니면 칸 없음) }` — 본문·딜·댓글은 싣지 않고 조회수도 올리지 않는다.
 *   댓글·추천 등 다른 글 API 는 기존 `403 POST_HIDDEN`. 화면은 안내만(`POST_HIDDEN` 과 같은 오류 길).
 *
 * 운영 게시판(버그 신고·기능 요청): 비밀글 `secret`(쓰기·고치기 본문 `secret?: boolean|null` · 글 보기 응답, 읽을 수 없으면 `403 SECRET_POST` + extras `boardSlug`)과
 *   처리 상태 `processStatus`·`processStatusAt`(글 보기 응답) · `PUT /api/posts/{id}/process-status { status }` → `{ processStatus, processStatusAt }`
 *   (오류 `409 POST_STATUS_DISABLED`·`400 fields.status`·`403 BOARD_STAFF_FORBIDDEN`, 글쓴이에게 알림 `POST_STATUS_CHANGED` 은 서버가 보낸다).
 *   서버 연결됨(2026-11-08, shared V166).
 *   2차(2026-11-08 — 서버와 동시 구현): 상태 바꾸기 본문 `note?`(0~200자, `400 fields.note`) · 글 상세 `processStatusNote`·`viewer.canChangeStatus` ·
 *   `GET /api/posts/{id}/process-status/history` → `{ items }` · 비회원 비밀글 `POST /api/posts/{id}/secret-unlock { password }`(204 — 틀리면 `403 PASSWORD_MISMATCH`,
 *   여러 번 틀리면 `429 PASSWORD_ATTEMPTS_EXCEEDED`). 잠긴 비회원 비밀글은 `403 SECRET_POST` extras `guestPost: true`.
 */
const PATH = {
    posts: '/api/posts',
    post: (id: string) => `/api/posts/${encodeURIComponent(id)}`,
    remove: (id: string) => `/api/posts/${encodeURIComponent(id)}/delete`,
    verify: (id: string) => `/api/posts/${encodeURIComponent(id)}/verify-password`,
    like: (id: string) => `/api/posts/${encodeURIComponent(id)}/like`,
    processStatus: (id: string) => `/api/posts/${encodeURIComponent(id)}/process-status`,
    processStatusHistory: (id: string) => `/api/posts/${encodeURIComponent(id)}/process-status/history`,
    secretUnlock: (id: string) => `/api/posts/${encodeURIComponent(id)}/secret-unlock`,
};

export function usePostApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        post: async (id: string, signal?: AbortSignal): Promise<PostDetail> => AiMemberMock.authors(await client.get<PostDetail>(PATH.post(id), { signal })), // MOCK: 작성자에게 author.ai 를 붙인다
        // REAL: post: (id: string, signal?: AbortSignal): Promise<PostDetail> => client.get<PostDetail>(PATH.post(id), { signal }),

        create: (body: PostWriteRequest): Promise<PostWriteResult> => client.post<PostWriteResult>(PATH.posts, body),

        update: (id: string, body: PostUpdateRequest): Promise<PostDetail> => client.put<PostDetail>(PATH.post(id), body),

        /** 처리 상태 바꾸기(그 게시판 매니저·스텝·운영자 회원 계정만) — 서버가 글쓴이에게 알림을 보낸다 */
        changeProcessStatus: (id: string, status: PostProcessStatus, note?: string): Promise<PostProcessStatusResult> =>
            client.put<PostProcessStatusResult>(PATH.processStatus(id), { status, ...(note ? { note } : {}) } satisfies PostProcessStatusRequest),

        /** 처리 상태 이력(글을 읽을 수 있는 사람) */
        processStatusHistory: (id: string, signal?: AbortSignal): Promise<PostProcessStatusHistory> => client.get<PostProcessStatusHistory>(PATH.processStatusHistory(id), { signal }),

        /** 비회원 비밀글을 글 비밀번호로 연다(204) — 뒤이어 글을 다시 받는다 */
        secretUnlock: (id: string, password: string): Promise<void> => client.post<void>(PATH.secretUnlock(id), { password } satisfies PostSecretUnlockRequest),

        remove: (id: string, body: PostDeleteRequest): Promise<PostDeleteResult> => client.post<PostDeleteResult>(PATH.remove(id), body),

        verifyPassword: (id: string, password: string): Promise<void> => client.post<void>(PATH.verify(id), { password }),

        like: (id: string): Promise<PostLikeResult> => client.post<PostLikeResult>(PATH.like(id)),
    };
}
