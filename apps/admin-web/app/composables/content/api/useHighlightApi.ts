import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';
import type {
    HighlightCandidateQuery,
    HighlightCandidatesView,
    HighlightItem,
    HighlightListView,
    HighlightSaveRequest,
} from '~/composables/content/api/useHighlightApi.types';

/**
 * 하이라이트 API — 서버 주소(`/api/admin/highlights…`)가 있는 **유일한 곳**이다. 상태는 없다. 대표 이미지 바이트 올리기는 `useFileApi`(`purpose=HIGHLIGHT_IMAGE`).
 * 그 글의 찬반 토론 의견·고정 요청 관리는 토론 관리 API(`useDebateApi` — `/api/admin/debates/{postId}/…`)로 옮겼다(post-debate-contract).
 * 서버 `highlight.controller.HighlightController`. 계약: `docs/features/popular-highlight-contract.md` 「서버 구현 결과」. 조회 `highlight.read`, 바꾸기 `highlight.manage`.
 *   - `GET /api/admin/highlights` → `{ visibleCount, total, items }`
 *   - `GET /post-search?q=` → 지정할 수 있는 글 최대 10개(숫자 15~19자리면 글 번호, 아니면 제목 일부 2자 이상) · 400 `fields.q`
 *   - `GET /candidates?days=&minLikes=&boardId=` → 추천 많은 순 30개 · 400 `fields.days|minLikes|boardId`
 *   - `POST` → 201 한 줄 · `PUT /{id}` → 200 한 줄(모든 칸) · 400 `fields.postId|title|summary|showUntil|imageFileId` · 400 `HIGHLIGHT_POST_INVALID` · 409 `HIGHLIGHT_DUPLICATE` · 404 `HIGHLIGHT_NOT_FOUND`
 *   - `PUT /order { ids }` → 204(전체 ID 의 새 차례, 집합이 다르면 400 `fields.ids`) · `PUT /{id}/visible { visible }` → 200 한 줄 · `DELETE /{id}` → 204
 */
const PATH = {
    root: '/api/admin/highlights',
    search: '/api/admin/highlights/post-search',
    candidates: '/api/admin/highlights/candidates',
    order: '/api/admin/highlights/order',
    one: (id: string) => `/api/admin/highlights/${encodeURIComponent(id)}`,
    visible: (id: string) => `/api/admin/highlights/${encodeURIComponent(id)}/visible`,
};

export function useHighlightApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (): Promise<HighlightListView> => client.get<HighlightListView>(PATH.root),

        /** 지정할 수 있는 글 찾기 — 글 번호 또는 제목 일부 */
        searchPosts: (q: string, signal?: AbortSignal): Promise<AdminPostRef[]> => client.get<AdminPostRef[]>(PATH.search, { query: { q }, signal }),

        /** 하이라이트 후보(추천 많은 글) */
        candidates: (query: HighlightCandidateQuery): Promise<HighlightCandidatesView> =>
            client.get<HighlightCandidatesView>(PATH.candidates, { query: { days: query.days, minLikes: query.minLikes, ...(query.boardId ? { boardId: query.boardId } : {}) } }),

        create: (body: HighlightSaveRequest): Promise<HighlightItem> => client.post<HighlightItem>(PATH.root, body),

        update: (id: string, body: HighlightSaveRequest): Promise<HighlightItem> => client.put<HighlightItem>(PATH.one(id), body),

        /** 전체 ID 를 새 차례로 */
        reorder: (ids: string[]): Promise<void> => client.put<void>(PATH.order, { ids }),

        setVisible: (id: string, visible: boolean): Promise<HighlightItem> => client.put<HighlightItem>(PATH.visible(id), { visible }),

        /** 하이라이트만 지운다(글·그 글의 토론·의견·입장은 그대로, 대표 이미지 파일은 지운다) */
        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),
    };
}
