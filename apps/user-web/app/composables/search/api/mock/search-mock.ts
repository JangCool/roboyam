// MOCK: 통합 검색(`GET /api/search`)의 목업 — `useSearchApi.ts` 가 부른다. 서버가 붙으면 `search/api/mock/` 와 함께 지운다.
// 「게시판 하나 + 범위 전체 + 최신순 + 한 단어」 검색은 목업이 아니라 실제 게시판 안 검색(`GET /api/boards/{slug}/posts?q`)으로 간다(`useSearchApi`).
// 그 밖(모든 게시판·범위·오래된순·여러 단어)은 단어마다·게시판마다 실제 게시판 안 검색을 불러 모은 뒤 흉내 낸다(`common/mock/board-feed-mock.ts`).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   search-fail     검색 실패(500)            search-timeout   3초를 넘김(503 SEARCH_TIMEOUT)
//   search-empty    결과가 없다               search-more-fail 「더보기」만 실패
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 서버의 게시판 안 검색은 제목·내용·작성자를 함께 본다 → 범위 `title` 은 제목에, `author` 는 닉네임에 단어가 모두 든 글만 남긴다.
//     `body` 는 걸러 낼 수 없어(목록에 본문이 없다) 서버 결과 중 제목·닉네임으로는 걸리지 않은 글만 남긴다. `snippet` 은 늘 null(목록에 본문이 없다).
//   · 게시판마다 앞 2쪽만 모은다. 쪽 번호는 20개씩 최대 10쪽, 커서는 모은 목록 안의 자리.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardFeedSource } from '~/composables/common/mock/board-feed-mock';
import { mockBoardFeed, mockCursorStart } from '~/composables/common/mock/board-feed-mock';
import type { SearchRequest, SearchResponse, SearchRow } from '~/composables/search/api/useSearchApi.types';
import UtilSearchRules from '~/utils/util-search-rules';

const MAX_PAGE = 10;

export async function mockSearch(source: BoardFeedSource, request: SearchRequest): Promise<SearchResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('search-fail') || (request.cursor && flags.has('search-more-fail'))) throw MOCK_ERRORS.internal();
    if (flags.has('search-timeout')) {
        throw new ApiError({ status: 503, code: 'SEARCH_TIMEOUT', message: '검색이 너무 오래 걸려 멈췄어요. 검색어를 더 구체적으로 입력해 주세요.' });
    }
    const query = UtilSearchRules.normalize(request.q);
    const problem = UtilSearchRules.check(query);
    if (problem) {
        const message = problem === 'TOO_LONG' ? '검색어는 50자 이하로 입력해 주세요.' : '검색어는 단어마다 2글자 이상 입력해 주세요.';
        throw MOCK_ERRORS.validation({ q: message });
    }
    const tokens = UtilSearchRules.distinctWords(query);
    const scope = request.scope ?? 'all';

    // 게시판 안 검색은 검색어를 통째로 찾는다 — 단어마다 따로 불러 모두에 걸린 글만 남긴다(AND)
    const feeds = await Promise.all(tokens.map((token) => mockBoardFeed(source, token)));
    const boards = feeds[0]?.boards ?? [];
    if (request.board && !boards.some((b) => b.slug === request.board)) {
        throw MOCK_ERRORS.validation({ board: '검색할 수 없는 게시판이에요.' });
    }
    const inAll = (id: string) => feeds.every((feed) => feed.rows.some((row) => row.id === id));
    let rows = flags.has('search-empty') ? [] : (feeds[0]?.rows ?? []).filter((row) => (!request.board || row.board.slug === request.board) && inAll(row.id));
    if (scope === 'title') rows = rows.filter((row) => UtilSearchRules.containsAll(row.title, tokens));
    else if (scope === 'author') rows = rows.filter((row) => UtilSearchRules.containsAll(row.author.name, tokens));
    else if (scope === 'body') rows = rows.filter((row) => !UtilSearchRules.containsAll(row.title, tokens) && !UtilSearchRules.containsAll(row.author.name, tokens));
    if (request.order === 'oldest') rows = [...rows].reverse();

    const per = Math.min(50, Math.max(1, request.size ?? 20));
    const withSnippet = (list: typeof rows): SearchRow[] => list.map((row) => ({ ...row, snippet: null }));
    const base = { query, tokens, scope, board: request.board ?? null };

    if (request.page !== undefined) {
        const page = Math.min(MAX_PAGE, Math.max(1, request.page));
        const totalPages = Math.min(MAX_PAGE, Math.ceil(rows.length / per));
        const end = page * per;
        const hasNext = end < rows.length;
        return mockClone({ ...base, items: withSnippet(rows.slice((page - 1) * per, end)), page, totalPages, hasNext, nextCursor: hasNext ? String(end) : null });
    }
    const start = mockCursorStart(request.cursor);
    const end = start + per;
    const hasNext = end < rows.length;
    return mockClone({ ...base, items: withSnippet(rows.slice(start, end)), page: null, totalPages: null, hasNext, nextCursor: hasNext ? String(end) : null });
}
