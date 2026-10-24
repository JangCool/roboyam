import type {
    AdminPostBoardOptionsView,
    AdminPostDetail,
    AdminPostListView,
    AdminPostQuery,
    PostBulkRequest,
    PostBulkResult,
    PostHideRequest,
    PostMoveRequest,
    PostReasonRequest,
} from '~/composables/post/api/usePostApi.types';

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
 * 쓰기는 CSRF(`useApiClient` 가 붙인다). 응답은 `Cache-Control: no-store`.
 */
const ROOT = '/api/admin/posts';
const PATH = {
    list: ROOT,
    boardOptions: `${ROOT}/board-options`,
    bulk: `${ROOT}/bulk`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    act: (id: string, action: 'hide' | 'unhide' | 'delete' | 'restore' | 'move' | 'notice' | 'pin' | 'unpin') => `${ROOT}/${encodeURIComponent(id)}/${action}`,
};

export function usePostApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 글 목록 — 빈 조건은 보내지 않는다 */
        list: (query: AdminPostQuery, signal?: AbortSignal): Promise<AdminPostListView> =>
            client.get<AdminPostListView>(PATH.list, {
                query: {
                    q: query.q || undefined,
                    searchField: query.q ? query.searchField : undefined,
                    board: query.board || undefined,
                    prefix: query.prefix || undefined,
                    createdFrom: query.createdFrom || undefined,
                    createdTo: query.createdTo || undefined,
                    state: query.state && query.state !== 'ALL' ? query.state : undefined,
                    sort: query.sort,
                    page: query.page,
                    size: query.size,
                },
                signal,
            }),

        /** 켜진 게시판·말머리(필터·이동 대화창) */
        boardOptions: (): Promise<AdminPostBoardOptionsView> => client.get<AdminPostBoardOptionsView>(PATH.boardOptions),

        /** 글 하나 — 본문(지운 글도 파기 전까지)·조치 기록 */
        get: (id: string): Promise<AdminPostDetail> => client.get<AdminPostDetail>(PATH.one(id)),

        hide: (id: string, body: PostHideRequest): Promise<void> => client.post<void>(PATH.act(id, 'hide'), body),
        unhide: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'unhide'), body),
        remove: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'delete'), body),
        restore: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'restore'), body),
        move: (id: string, body: PostMoveRequest): Promise<void> => client.post<void>(PATH.act(id, 'move'), body),
        notice: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'notice'), body),
        pin: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'pin'), body),
        unpin: (id: string, body: PostReasonRequest): Promise<void> => client.post<void>(PATH.act(id, 'unpin'), body),

        /** 일괄 숨김·숨김 해제·삭제 — 글마다 따로 처리하고 실패한 글만 까닭과 함께 */
        bulk: (body: PostBulkRequest): Promise<PostBulkResult> => client.post<PostBulkResult>(PATH.bulk, body),
    };
}
