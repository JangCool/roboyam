import { mockSearch } from '~/composables/search/api/mock/search-mock';
import type { SearchRequest, SearchResponse } from '~/composables/search/api/useSearchApi.types';
import UtilSearchRules from '~/utils/util-search-rules';

/**
 * 통합 검색 API — 서버 주소 문자열(`/api/search`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다.
 * 계약 후보: `docs/features/member-activity-contract.md`(모양은 community `docs/api-board.md`「검색 (2-4)」).
 *
 *  - `GET /api/search?q&scope&board&order&size&page|cursor` → `SearchResponse`
 *    오류: `400 VALIDATION` `fields.q|scope|board|order|page|cursor` · `503 SEARCH_TIMEOUT`
 *
 * **실제로 되는 것**: 게시판 하나(`board`) + 범위 전체 + 최신순 + 한 단어 검색은 서버의 게시판 안 검색(`useBoardApi().posts` — `GET /api/boards/{slug}/posts?q&page`, 제목·본문·작성자, 20개씩)과 같은 뜻이라 그것을 부른다.
 * 쪽 번호만 된다(커서 더보기는 다음 쪽을 불러 이어 붙인다 — `nextCursor` 는 다음 쪽 번호 글자). 본문 조각(`snippet`)은 없다.
 * ⚠ MOCK: 그 밖(모든 게시판·범위·오래된순·여러 단어)은 `./mock/search-mock.ts` — 서버가 붙으면 `// MOCK:` 줄과 목업 import·게시판 안 검색 우회(`viaBoard`)를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    search: '/api/search',
};

/** 서버의 게시판 안 검색이 같은 결과를 내는 요청인가 — 게시판 하나 · 범위 전체 · 최신순 · 한 단어 */
function servedByBoard(request: SearchRequest): request is SearchRequest & { board: string } {
    return Boolean(request.board) && (request.scope ?? 'all') === 'all' && (request.order ?? 'latest') === 'latest' && UtilSearchRules.distinctWords(request.q).length === 1;
}

export function useSearchApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    const boardApi = useBoardApi();
    // MOCK: 목업은 실제 게시판 목록·게시판 안 검색을 모은다
    const source = { boards: () => boardApi.boards(), posts: boardApi.posts };
    void PATH;

    /** 게시판 안 검색(실제) → 통합 검색 응답 모양 */
    async function viaBoard(request: SearchRequest & { board: string }, signal?: AbortSignal): Promise<SearchResponse> {
        const query = UtilSearchRules.normalize(request.q);
        const page = request.page ?? (Number(request.cursor) > 0 ? Number(request.cursor) : 1);
        const [boards, list] = await Promise.all([boardApi.boards(signal), boardApi.posts(request.board, { prefix: '', sort: 'LATEST', q: query, page }, signal)]);
        const board = boards.find((b) => b.slug === request.board);
        const ref = { slug: request.board, name: board?.name ?? request.board, nameEn: board?.nameEn ?? null, color: board?.color ?? null };
        const items = list.items.filter((row) => !row.hiddenReason).map((row) => ({ ...row, board: ref, snippet: null }));
        const hasNext = list.page < list.totalPages;
        const paged = request.page !== undefined;
        return {
            query,
            tokens: [query],
            scope: 'all',
            board: request.board,
            items,
            page: paged ? list.page : null,
            totalPages: paged ? Math.min(10, list.totalPages) : null,
            hasNext,
            nextCursor: hasNext ? String(list.page + 1) : null,
        };
    }

    return {
        /** 검색 — 쪽 번호(`page`)나 커서(`cursor`) 하나로 */
        search: (request: SearchRequest, signal?: AbortSignal): Promise<SearchResponse> =>
            servedByBoard(request)
                ? viaBoard(request, signal)
                : // MOCK:
                  mockSearch(source, request),
        // REAL: client.get<SearchResponse>(PATH.search, { query: { q: request.q, scope: request.scope, board: request.board, order: request.order, size: request.size, page: request.page, cursor: request.cursor }, signal }),
    };
}
