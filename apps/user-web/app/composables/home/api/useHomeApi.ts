import { mockLatest, mockPopular } from '~/composables/home/api/mock/home-mock';
import type { PostFeedPage } from '~/composables/board/api/usePostApi.types';
import type { LatestQuery, PopularListResponse, PopularQuery } from '~/composables/home/api/useHomeApi.types';

/**
 * 최신글 전체·인기글 전체 API — 서버 주소 문자열(`/api/home/…`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다.
 * 계약 후보: `docs/features/member-activity-contract.md`(모양은 community `docs/api-home.md`·`docs/api-popular.md`).
 *
 *  - `GET /api/home/latest?size&cursor` → `PostFeedPage` — 켜진 모든 게시판의 최신글, 커서 더보기. 틀린 커서 `400 VALIDATION fields.cursor`
 *  - `GET /api/home/popular?period&page&size` → `PopularListResponse` — 기간 탭(`REALTIME`·`YESTERDAY`·`WEEK`)의 순위, 쪽 번호. `400 VALIDATION fields.period|page|size`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/home-mock.ts` 를 부른다 — 목업은 실제 공개 게시판 API(`useBoardApi`)로 글을 모아 흉내 낸다.
 * 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    latest: '/api/home/latest',
    popular: '/api/home/popular',
};

export function useHomeApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업은 실제 게시판 목록·글 목록을 모은다
    const boardApi = useBoardApi();
    const source = { boards: () => boardApi.boards(), posts: boardApi.posts };
    void PATH;

    return {
        /** 최신글 전체 — 커서로 이어 받는다 */
        latest: (query: LatestQuery, signal?: AbortSignal): Promise<PostFeedPage> =>
            // MOCK:
            (void signal, mockLatest(source, query)),
        // REAL: client.get<PostFeedPage>(PATH.latest, { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 인기글 전체 — 기간의 순위를 쪽 번호로 */
        popular: (query: PopularQuery, signal?: AbortSignal): Promise<PopularListResponse> =>
            // MOCK:
            (void signal, mockPopular(source, query)),
        // REAL: client.get<PopularListResponse>(PATH.popular, { query: { period: query.period, page: query.page, size: query.size }, signal }),
    };
}
