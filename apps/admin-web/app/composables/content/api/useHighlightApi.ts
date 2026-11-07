import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';
import type {
    HighlightCandidateQuery,
    HighlightCandidatesView,
    HighlightItem,
    HighlightListView,
    HighlightPlace,
    HighlightSaveRequest,
} from '~/composables/content/api/useHighlightApi.types';

/**
 * 하이라이트 API — 서버 주소(`/api/admin/highlights…`)가 있는 **유일한 곳**이다. 상태는 없다. 대표 이미지 바이트 올리기는 `useFileApi`(`purpose=HIGHLIGHT_IMAGE`).
 * 그 글의 찬반 토론 의견·고정 요청 관리는 토론 관리 API(`useDebateApi` — `/api/admin/debates/{postId}/…`)로 옮겼다(post-debate-contract).
 * 서버 `highlight.controller.HighlightController`. 계약: `docs/features/popular-highlight-contract.md` 「서버 구현 결과」. 조회 `highlight.read`, 바꾸기 `highlight.manage`.
 * 곳(`place` — home · tips · 가젯 페이지 키, 비우면 home)마다 목록·순서가 따로다(tips-contract 「서버 구현 결과」, V161).
 *   - `GET /api/admin/highlights?place=` → `{ place, visibleCount, total, items }`
 *   - `GET /places` → `[{ key, name, kind: HOME|TIPS|PAGE }]`(이름은 요청 언어)
 *   - `GET /post-search?q=&place=` → 그 곳에 아직 없는 지정할 수 있는 글 최대 10개(숫자 15~19자리면 글 번호, 아니면 제목 일부 2자 이상) · 400 `fields.q`
 *   - `GET /candidates?days=&minLikes=&boardId=&place=` → 그 곳에 없는 추천 많은 순 30개 · 400 `fields.days|minLikes|boardId`
 *   - `POST`(본문 `place` — 만든 뒤 못 바꿈, 없는 곳 400 `fields.place`) → 201 한 줄 · `PUT /{id}` → 200 한 줄(모든 칸) · 400 `fields.postId|title|summary|showUntil|imageFileId` · 400 `HIGHLIGHT_POST_INVALID` · 409 `HIGHLIGHT_DUPLICATE`(같은 곳 안에서만) · 404 `HIGHLIGHT_NOT_FOUND`
 *   - `PUT /order { place, ids }` → 204(그 곳 전체 ID 의 새 차례, 집합이 다르면 400 `fields.ids`) · `PUT /{id}/visible { visible }` → 200 한 줄 · `DELETE /{id}` → 204
 */
const PATH = {
    root: '/api/admin/highlights',
    places: '/api/admin/highlights/places',
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
        /** 한 곳의 목록 */
        list: (place: string): Promise<HighlightListView> => client.get<HighlightListView>(PATH.root, { query: { place } }),

        /** 하이라이트를 놓을 수 있는 곳 */
        places: (): Promise<HighlightPlace[]> => client.get<HighlightPlace[]>(PATH.places),

        /** 지정할 수 있는 글 찾기 — 글 번호 또는 제목 일부, 그 곳에 아직 없는 글 */
        searchPosts: (q: string, place: string, signal?: AbortSignal): Promise<AdminPostRef[]> => client.get<AdminPostRef[]>(PATH.search, { query: { q, place }, signal }),

        /** 하이라이트 후보(추천 많은 글) */
        candidates: (query: HighlightCandidateQuery): Promise<HighlightCandidatesView> =>
            client.get<HighlightCandidatesView>(PATH.candidates, { query: { days: query.days, minLikes: query.minLikes, place: query.place, ...(query.boardId ? { boardId: query.boardId } : {}) } }),

        create: (body: HighlightSaveRequest): Promise<HighlightItem> => client.post<HighlightItem>(PATH.root, body),

        update: (id: string, body: HighlightSaveRequest): Promise<HighlightItem> => client.put<HighlightItem>(PATH.one(id), body),

        /** 한 곳의 전체 ID 를 새 차례로 */
        reorder: (place: string, ids: string[]): Promise<void> => client.put<void>(PATH.order, { place, ids }),

        setVisible: (id: string, visible: boolean): Promise<HighlightItem> => client.put<HighlightItem>(PATH.visible(id), { visible }),

        /** 하이라이트만 지운다(글·그 글의 토론·의견·입장은 그대로, 대표 이미지 파일은 지운다) */
        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),
    };
}
