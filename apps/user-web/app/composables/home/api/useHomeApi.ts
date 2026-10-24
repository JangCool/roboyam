import type { PostFeedPage } from '~/composables/board/api/usePostApi.types';
import type { LatestQuery, PopularListResponse, PopularQuery } from '~/composables/home/api/useHomeApi.types';

/**
 * 최신글 전체·인기글 전체 API — 서버 주소 문자열(`/api/home/…`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다.
 * 계약: `docs/features/member-activity-contract.md`. 서버 `HomeController`.
 *
 *  - `GET /api/home/latest?size&cursor` → `PostFeedPage` — 켜진 모든 게시판의 최신글, 커서 더보기. 틀린 커서 `400 VALIDATION fields.cursor`
 *  - `GET /api/home/popular?period&page&size` → `PopularListResponse` — 기간 탭(`REALTIME`·`YESTERDAY`·`WEEK`)의 순위, 쪽 번호. `400 VALIDATION fields.period|page|size`
 *
 * 서버 연결됨(2026-10-24).
 */
const PATH = {
    latest: '/api/home/latest',
    popular: '/api/home/popular',
};

export function useHomeApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 최신글 전체 — 커서로 이어 받는다 */
        latest: (query: LatestQuery, signal?: AbortSignal): Promise<PostFeedPage> =>
            client.get<PostFeedPage>(PATH.latest, { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 인기글 전체 — 기간의 순위를 쪽 번호로 */
        popular: (query: PopularQuery, signal?: AbortSignal): Promise<PopularListResponse> =>
            client.get<PopularListResponse>(PATH.popular, { query: { period: query.period, page: query.page, size: query.size }, signal }),
    };
}
