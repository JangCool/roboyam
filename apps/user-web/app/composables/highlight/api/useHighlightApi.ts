import type { AchuListResponse, AchuQuery } from '~/composables/highlight/api/useHighlightApi.types';

/**
 * 「아추!」(아이로빗 추천 — 운영자가 고른 홈 하이라이트) 공개 API — 서버 주소 문자열(`/api/highlights`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다.
 * 계약: `docs/features/achu-page-contract.md`.
 *
 *  - `GET /api/highlights?period&page&size` → `AchuListResponse` — 기간(`DAY`·`WEEK`·`MONTH`)에 고른 홈 하이라이트, 쪽 번호. `400 VALIDATION fields.period|page|size`
 */
const PATH = {
    list: '/api/highlights',
};

export function useHighlightApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 기간에 고른 아추! — 쪽 번호로 */
        list: (query: AchuQuery, signal?: AbortSignal): Promise<AchuListResponse> =>
            client.get<AchuListResponse>(PATH.list, { query: { period: query.period, page: query.page, size: query.size }, signal }),
    };
}
