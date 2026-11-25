import type { SiteSearchVerificationRequest, SiteSearchVerificationResponse } from '~/composables/site/api/useSiteSearchVerificationApi.types';

/**
 * 검색 엔진 소유 확인 코드 API — 서버 주소 문자열(`/api/admin/site/search-verification`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 값은 `SiteSearchVerificationSettings` 의 `ref`).
 *
 * 계약(서버 `SiteSearchVerificationController`):
 *   - `GET /api/admin/site/search-verification` → 200 `{ naver|null, google|null, bing|null }`  (`site.read`) · 설정 행이 없으면 404
 *   - `PUT /api/admin/site/search-verification` 같은 모양 → 200 저장된 모양  (`site.manage`)
 *       · 빈 값·공백만은 null(그 태그를 넣지 않는다). 값은 영문·숫자·`-`·`_` 1~100자 — 400 VALIDATION(`fields.naver|google|bing`) · 403 PERMISSION_DENIED
 *       · 바뀐 때만 감사 기록 `SITE_SEARCH_VERIFICATION_UPDATE`
 * 공개 사이트는 서버(`IndexHtmlRenderer`)가 첫 HTML `<head>` 에 값이 있는 것만 `<meta name="naver-site-verification|google-site-verification|msvalidate.01" content="…">` 로 넣는다.
 * 저장하면 사이트 설정 캐시(`site.info`)가 갱신돼 모든 api 서버가 몇 초(늦어도 사본 유지 시간) 안에 새 값을 쓴다.
 */
const PATH = {
    searchVerification: '/api/admin/site/search-verification',
};

export function useSiteSearchVerificationApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 저장된 소유 확인 코드 셋 */
        get: (): Promise<SiteSearchVerificationResponse> => client.get<SiteSearchVerificationResponse>(PATH.searchVerification),

        /** 셋을 한꺼번에 저장한다 — 저장된 모양을 돌려준다 */
        save: (body: SiteSearchVerificationRequest): Promise<SiteSearchVerificationResponse> => client.put<SiteSearchVerificationResponse>(PATH.searchVerification, body),
    };
}
