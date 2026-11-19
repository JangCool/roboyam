import type { AchuListResponse, AchuQuery } from '~/composables/highlight/api/useHighlightApi.types';
import { achuListMock } from '~/composables/highlight/api/mock/achu-mock';

/**
 * 「아추!」(아이로빗 추천 — 운영자가 고른 하이라이트) 공개 API — 서버 주소 문자열(`/api/highlights`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다.
 * 계약: `docs/features/achu-page-contract.md`(화면 확정 전 — 서버 없음).
 *
 *  - `GET /api/highlights?period&page&size` → `AchuListResponse` — 기간(`DAY`·`WEEK`·`MONTH`)에 고른 하이라이트, 쪽 번호. `400 VALIDATION fields.period|page|size`
 *
 * ⚠ MOCK — 서버가 없어 실제 최신글(`GET /api/home/latest`)을 받아 하이라이트처럼 고른 시각을 붙여 돌려준다(`mock/achu-mock.ts`).
 */
const PATH = {
    list: '/api/highlights',
};

export function useHighlightApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 실제 최신글을 재료로 쓴다 — 서버가 붙으면 이 줄을 지운다
    const home = useHomeApi();

    return {
        /** 기간에 고른 아추! — 쪽 번호로 */
        list: (query: AchuQuery, signal?: AbortSignal): Promise<AchuListResponse> =>
            // MOCK: 실제 최신글로 만든 목업
            achuListMock(query, (size) => home.latest({ size }, signal)),
        // REAL: list: (query: AchuQuery, signal?: AbortSignal): Promise<AchuListResponse> => client.get<AchuListResponse>(PATH.list, { query: { period: query.period, page: query.page, size: query.size }, signal }),
    };
}

// MOCK: REAL 줄이 쓰는 값 — 목업 동안 쓰지 않는 경고를 막는다(서버가 붙으면 지운다)
void PATH;
