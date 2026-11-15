import type { TipsOverview, TipsPostPage, TipsPostQuery } from '~/composables/tips/api/useTipsApi.types';
import UtilTips from '~/utils/util-tips';

/**
 * 팁·강좌·사용기 묶음 API — 서버 주소 문자열(`/api/tips…`)이 있는 **유일한 곳**이다. 상태는 없다. 화면은 `pages/tips.vue`.
 * 서버 `server/api` `tips.controller.TipsController`(2026-11-07 연결, 비회원 허용). 계약 `docs/features/tips-contract.md` 「서버 구현 결과」.
 *
 *  - `GET /api/tips?board=` 묶음 머리 몫(칩 게시판 · 헤드라인 ≤5 · 많이 본 글 ≤5 · 많이 달린 태그 ≤10)
 *  - `GET /api/tips/posts?board=&tag=&page=` 최신 글 한 쪽(20개, 본문 요약 포함 — 서버가 두 게시판을 최신순으로 합친다, `tag` 면 그 태그 글만)
 *
 * 태그(post-tags-contract)는 서버 실제 값이다(목업 걷어냄 2026-11-15). 옛 서버라 칸이 없으면 빈 값으로 읽는다(`UtilTips.overviewOf`·`postPageOf`).
 */
const PATH = {
    overview: '/api/tips',
    posts: '/api/tips/posts',
};

export function useTipsApi() {
    const client = useApiClient();

    return {
        /** 묶음 머리 몫 — 빈 `board` 는 전체 */
        overview: async (board: string, signal?: AbortSignal): Promise<TipsOverview> => UtilTips.overviewOf(await client.get<TipsOverview>(PATH.overview, { query: { board: board || undefined }, signal })),

        /** 최신 글 한 쪽 — 빈 `board`·`tag` 는 보내지 않는다 */
        posts: async (query: TipsPostQuery, signal?: AbortSignal): Promise<TipsPostPage> =>
            UtilTips.postPageOf(await client.get<TipsPostPage>(PATH.posts, { query: { board: query.board || undefined, tag: query.tag || undefined, page: query.page }, signal })),
    };
}
