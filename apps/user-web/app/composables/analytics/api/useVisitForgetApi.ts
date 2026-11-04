import type { VisitForgetRequestBody, VisitForgetResponse } from '~/composables/analytics/api/useVisitForgetApi.types';

/**
 * 방문 기록 삭제 요청 API(정보주체 요청) — 서버 주소 문자열(`/api/visits/forget`)이 있는 **유일한 곳**이다. 상태는 없다.
 *  - `POST /api/visits/forget` `{ visitorId }` → 202 `VisitForgetResponse`(접수 — `ApiResponse{ data }`) · 400 VALIDATION(`fields.visitorId`)
 *    · 429 `VISIT_FORGET_RATE_LIMITED`(IP 당 1시간 5번, `Retry-After` → `retryAfterSeconds`) · 503 `INDEX_KEY_MISSING`(확인용 키 없음). 로그인·CSRF 없음.
 *
 * 방문 신호 자체(`POST /api/visits`)는 `@roboyam/analytics` 의 `util-visit-transport` 가 보내지만(응답을 쓰지 않는 단방향 신호라 레이어 규약의 예외), 이 요청은 그쪽을 따르지 않고
 * **앱의 API 클라이언트**를 거친다 — 화면이 응답(요청 번호·접수 시각)을 쓰고, 오류를 `ApiError`(서버 문구·`fields`·`retryAfterSeconds`)로 받아야 하며, 서버 문구가 화면 언어(`Accept-Language`)로 와야 해서다.
 * 클라이언트가 붙이는 CSRF 머리글은 이 주소에서 서버가 보지 않아 해가 없다(그래서 `refreshCsrf` 도 두지 않는다).
 */
const PATH = {
    forget: '/api/visits/forget',
};

export function useVisitForgetApi() {
    const client = useApiClient();

    return {
        /** 이 브라우저의 방문 기록을 지워 달라고 접수한다 */
        forget: (body: VisitForgetRequestBody): Promise<VisitForgetResponse> => client.post<VisitForgetResponse>(PATH.forget, { ...body }),
    };
}
