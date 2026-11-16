import type {
    AdminPostBoardOptionsView,
    AdminPostDetail,
    AdminPostListView,
    AdminPostQuery,
    PostBulkRequest,
    PostBulkResult,
    PostHideRequest,
    PostMoveRequest,
    PostPollDeleteRequest,
    PostReasonRequest,
    PostProcessStatus,
    PostProcessStatusHistory,
    PostProcessStatusRequest,
    PostProcessStatusResult,
    AdminPostThumbnailRequest,
    PostThumbnailUploadResult,
} from '~/composables/post/api/usePostApi.types';
// MOCK: 우수 글 선정·취소와 줄의 `featured`(point-board-tier-contract) — 서버가 생기면 이 줄을 지운다
import { mockFeature, withFeaturedDetail, withFeaturedList } from '~/composables/post/api/mock/post-feature-mock';

/**
 * 게시글 관리 API — 서버 주소(`/api/admin/posts…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약: `docs/features/post-admin-contract.md` 「API」.
 *   - `GET  /api/admin/posts?searchField=TITLE|AUTHOR|ID&q=&board=&prefix=&createdFrom=&createdTo=&state=&sort=&page=&size=` (`post.read`) → `AdminPostListView`(`counts` 는 상태 조건만 뺀 건수)
 *       · 조건이 틀리면 400 `fields.q`·`fields.searchField`·`fields.createdFrom`·`fields.createdTo`
 *   - `GET  /board-options` (`post.read`) → 켜진 게시판·말머리
 *   - `GET  /{id}` (`post.read`) → 글 + 본문 + 조치 기록 · 404 `POST_NOT_FOUND`
 *   - `POST /{id}/hide|unhide|delete|restore|move|notice|pin|unpin` (`post.manage`, 복구는 `post.restore`) → 204
 *       · 사유 1~200자 필수(400 `fields.reason`) · 상태가 맞지 않음 409 `POST_STATE_INVALID` · 고정 한도 409 `PIN_LIMIT` · 파기된 글 복구 409 `POST_PURGED`
 *       · 이동: 같은 자리·켜진 말머리가 아님 400 `fields.prefixId` · 꺼진 게시판 400 `fields.boardId`
 *   - `POST /bulk { action, ids(1~50), reason, notifyAuthor }` (`post.manage`, DELETE 는 `post.bulk-delete` 도) → 200 `{ done, failed: [{id, code}] }` — 글마다 따로
 *   - `DELETE /{id}/poll { reason }` (`post.manage`, 표가 있어도) → 204 — 글의 투표 지우기(post-poll-contract). 감사 `POST_POLL_DELETE`, 조치 기록 `POLL_DELETED`
 *   - 우수 글(point-board-tier-contract — 후보, ⚠ MOCK): `POST /{id}/feature` 선정 · `DELETE /{id}/feature` 취소 (`post.feature` 후보) → 204. 목록·상세 줄 `featured`.
 *   - `PUT /{id}/process-status { status }` → `{ processStatus, processStatusAt }` — 운영 게시판 처리 상태 바꾸기 · 꺼진 게시판 409 `POST_STATUS_DISABLED` · 400 `fields.status` · 403
 *   운영 게시판: 목록·상세 줄 `secret`·`processStatus`, 목록 쿼리 `processStatus`. 서버 연결됨(2026-11-08, shared V166 — 감사 `POST_PROCESS_STATUS`)
 *   2차(2026-11-08 — 서버와 동시 구현): 바꾸기 본문 `note?`(0~200자, 400 `fields.note`) · 상세 줄 `processStatusNote` ·
 *   `GET /{id}/process-status/history` (`post.read`) → `{ items: [{ fromStatus, toStatus, note, changedAt, actor, actorName }] }`
 *   대표 썸네일(list-thumbnail-contract 「2단계」 — 서버 연결 2026-11-13, shared V206): 상세 `thumbnail` 칸(비공개 소모임·원문 파기 글 null) ·
 *   `PUT /{id}/thumbnail { mode, imageId?, fileId? }`(`post.manage`) → 204 · 400 `fields.mode|imageId|fileId` · 404 `POST_NOT_FOUND`. 그림은 파일 API(`purpose=POST_THUMBNAIL`) 두 걸음.
 * 쓰기는 CSRF(`useApiClient` 가 붙인다). 응답은 `Cache-Control: no-store`.
 */
const ROOT = '/api/admin/posts';
const PATH = {
    list: ROOT,
    boardOptions: `${ROOT}/board-options`,
    bulk: `${ROOT}/bulk`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    act: (id: string, action: 'hide' | 'unhide' | 'delete' | 'restore' | 'move' | 'notice' | 'pin' | 'unpin') => `${ROOT}/${encodeURIComponent(id)}/${action}`,
    poll: (id: string) => `${ROOT}/${encodeURIComponent(id)}/poll`,
    processStatus: (id: string) => `${ROOT}/${encodeURIComponent(id)}/process-status`,
    processStatusHistory: (id: string) => `${ROOT}/${encodeURIComponent(id)}/process-status/history`,
    /** 대표 썸네일 정하기 */
    thumbnail: (id: string) => `${ROOT}/${encodeURIComponent(id)}/thumbnail`,
    /** 우수 글 선정·취소(point-board-tier-contract 후보) — 목업 단계라 아직 부르지 않는다(REAL 줄이 쓴다) */
    feature: (id: string) => `${ROOT}/${encodeURIComponent(id)}/feature`,
};

export function usePostApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    /** 대표 썸네일 그림 올리기 — 바이트는 파일 API 한 곳으로 */
    const files = useFileApi();

    return {
        list: async (query: AdminPostQuery, signal?: AbortSignal): Promise<AdminPostListView> => {
            const view = await client.get<AdminPostListView>(PATH.list, {
                query: {
                    q: query.q || undefined,
                    searchField: query.q ? query.searchField : undefined,
                    board: query.board || undefined,
                    prefix: query.prefix || undefined,
                    processStatus: query.processStatus || undefined,
                    createdFrom: query.createdFrom || undefined,
                    createdTo: query.createdTo || undefined,
                    state: query.state && query.state !== 'ALL' ? query.state : undefined,
                    sort: query.sort,
                    page: query.page,
                    size: query.size,
                },
                signal,
            });
            // MOCK: 서버가 줄의 `featured`(우수 글)를 주기 전까지 목업 선정 목록으로 채운다
            return withFeaturedList(view);
            // REAL: return view;
        },

        /** 켜진 게시판·말머리(필터·이동 대화창) */
        boardOptions: (): Promise<AdminPostBoardOptionsView> => client.get<AdminPostBoardOptionsView>(PATH.boardOptions),

        /** 글 하나 — 본문(지운 글도 파기 전까지)·조치 기록 · 대표 썸네일 */
        get: async (id: string): Promise<AdminPostDetail> => {
            const detail = await client.get<AdminPostDetail>(PATH.one(id));
            // MOCK: 우수 글 선정 여부를 채운다
            return withFeaturedDetail(detail);
            // REAL: return detail;
        },

        /** 대표 썸네일 정하기(`post.manage`) → 204 — 운영자가 정하면 글쓴이 것보다 우선한다. `AUTO` 는 설정을 지워 자동으로 되돌린다 */
        setThumbnail: (id: string, body: AdminPostThumbnailRequest): Promise<void> => client.put<void>(PATH.thumbnail(id), body),

        /**
         * 대표 썸네일 그림 올리기 — 파일 API(`purpose=POST_THUMBNAIL`)로 올리고 받은 파일 ID 를 `setThumbnail` 의 `fileId` 로 넘긴다.
         * 서버는 파일 ID 만 주므로 미리보기는 브라우저 임시 주소(바꾸기 창이 쓰는 동안만 — 저장 뒤 서랍은 서버 주소를 다시 받는다)
         */
        async uploadThumbnail(file: File): Promise<PostThumbnailUploadResult> {
            const { fileId } = await files.upload(file, 'POST_THUMBNAIL');
            return { fileId, url: URL.createObjectURL(file) };
        },

        /** 처리 상태 바꾸기(운영 게시판 — 상태가 켜진 게시판의 글만, `post.manage`) — 서버가 글쓴이에게 알림을 보낸다 */
        changeProcessStatus: (id: string, status: PostProcessStatus, note?: string): Promise<PostProcessStatusResult> =>
            client.put<PostProcessStatusResult>(PATH.processStatus(id), { status, ...(note ? { note } : {}) } satisfies PostProcessStatusRequest),

        /** 처리 상태 이력(바꾼 사람 이름 포함) */
        processStatusHistory: (id: string, signal?: AbortSignal): Promise<PostProcessStatusHistory> => client.get<PostProcessStatusHistory>(PATH.processStatusHistory(id), { signal }),

        hide: (id: string, body: PostHideRequest): Promise<void> => client.post<void>(PATH.act(id, 'hide'), body),
        unhide: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'unhide'), body),
        remove: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'delete'), body),
        restore: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'restore'), body),
        move: (id: string, body: PostMoveRequest): Promise<void> => client.post<void>(PATH.act(id, 'move'), body),
        notice: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'notice'), body),
        pin: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'pin'), body),
        unpin: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'unpin'), body),

        /**
         * 우수 글로 선정(point-board-tier-contract — 후보 `POST /api/admin/posts/{id}/feature`, 권한 후보 `post.feature`) — 글쓴이에게 그 게시판 등급의 우수 글 금액을 보류 지급하고 홈·매거진 노출 후보가 된다.
         * 글마다 한 번(원장 유일). 공개 글만(그 밖 409 `POST_STATE_INVALID` 후보).
         */
        feature: (id: string): Promise<void> =>
            // MOCK: 지연 뒤 성공(이 탭 메모리에 선정 여부를 남긴다)
            mockFeature(id, true),
        // REAL: feature: (id: string): Promise<void> => client.post<void>(PATH.feature(id)),

        /** 우수 글 선정 취소(후보 `DELETE /api/admin/posts/{id}/feature`) — 보류 중이면 지급을 취소, 확정 뒤면 회수하지 않는다 */
        unfeature: (id: string): Promise<void> =>
            // MOCK: 지연 뒤 성공
            mockFeature(id, false),
        // REAL: unfeature: (id: string): Promise<void> => client.del<void>(PATH.feature(id)),

        // REAL:
        /** 글의 투표 지우기 — 사유를 본문에 싣는 DELETE */
        deletePoll: (id: string, body: PostPollDeleteRequest): Promise<void> => client.del<void>(PATH.poll(id), undefined, body),

        /** 일괄 숨김·숨김 해제·삭제 — 글마다 따로 처리하고 실패한 글만 까닭과 함께 */
        bulk: (body: PostBulkRequest): Promise<PostBulkResult> => client.post<PostBulkResult>(PATH.bulk, body),
    };
}
