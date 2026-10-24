import type { SearchRequest, SearchResponse } from '~/composables/search/api/useSearchApi.types';

/**
 * 통합 검색 API — 서버 주소 문자열(`/api/search`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다.
 * 계약: `docs/features/member-activity-contract.md`. 서버 `SearchController`.
 *
 *  - `GET /api/search?q&scope&board&order&size&page|cursor` → `SearchResponse`
 *    오류: `400 VALIDATION` `fields.q|scope|board|order|page|cursor`(없는·꺼진 게시판도 `fields.board`) · `503 SEARCH_TIMEOUT`
 *
 * 서버 연결됨(2026-10-24).
 */
const PATH = {
    search: '/api/search',
};

export function useSearchApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 검색 — 쪽 번호(`page`)나 커서(`cursor`) 하나로 */
        search: (request: SearchRequest, signal?: AbortSignal): Promise<SearchResponse> =>
            client.get<SearchResponse>(PATH.search, {
                query: { q: request.q, scope: request.scope, board: request.board, order: request.order, size: request.size, page: request.page, cursor: request.cursor },
                signal,
            }),
    };
}
