import type { TipsOverview, TipsPostPage, TipsPostQuery } from '~/composables/tips/api/useTipsApi.types';
import type { TipsOverviewWire, TipsPostPageWire } from '~/composables/tips/api/mock/tips-mock'; // MOCK:
import TipsMock from '~/composables/tips/api/mock/tips-mock'; // MOCK:

/**
 * 팁·강좌·사용기 묶음 API — 서버 주소 문자열(`/api/tips…`)이 있는 **유일한 곳**이다. 상태는 없다. 화면은 `pages/tips.vue`.
 * 서버 `server/api` `tips.controller.TipsController`(2026-11-07 연결, 비회원 허용). 계약 `docs/features/tips-contract.md` 「서버 구현 결과」.
 *
 *  - `GET /api/tips?board=` 묶음 머리 몫(칩 게시판 · 헤드라인 ≤5 · 많이 본 글 ≤5)
 *  - `GET /api/tips/posts?board=&page=` 최신 글 한 쪽(20개, 본문 요약 포함 — 서버가 두 게시판을 최신순으로 합친다)
 *
 * ⚠ MOCK: 태그만 서버에 없어 목업(`mock/tips-mock.ts`)이 응답에 붙인다. 태그가 정해지면 MOCK 줄을 지우고 REAL 줄을 살린다.
 */
const PATH = {
    overview: '/api/tips',
    posts: '/api/tips/posts',
};

export function useTipsApi() {
    const client = useApiClient();

    return {
        /** 묶음 머리 몫 — 빈 `board` 는 전체 */
        overview: async (board: string, signal?: AbortSignal): Promise<TipsOverview> =>
            TipsMock.overview(await client.get<TipsOverviewWire>(PATH.overview, { query: { board: board || undefined }, signal }), board), // MOCK: 태그를 붙인다
        // REAL: client.get<TipsOverview>(PATH.overview, { query: { board: board || undefined }, signal }),

        /** 최신 글 한 쪽 */
        posts: async (query: TipsPostQuery, signal?: AbortSignal): Promise<TipsPostPage> =>
            TipsMock.posts(await client.get<TipsPostPageWire>(PATH.posts, { query: { board: query.board || undefined, page: query.page }, signal })), // MOCK: 태그를 붙인다
        // REAL: client.get<TipsPostPage>(PATH.posts, { query: { board: query.board || undefined, page: query.page }, signal }),
    };
}
