import type { SiteInfoResponse } from '~/composables/site/api/useSiteInfoApi.types';

/**
 * 공개 사이트 정보 API — 서버 주소 문자열(`/api/site/info`)이 있는 **유일한 곳**이다. 상태는 없다(상태는 `useSiteInfo`).
 * 비로그인도 받는다. 서버가 20초 메모리 사본과 `Cache-Control: public, max-age=60` + `ETag` 로 내려 주므로 화면이 따로 캐시하지 않는다.
 * 설치 전에는 `503 NOT_INSTALLED` 다 — 부르는 쪽(`plugins/02.site-info.client.ts`)이 설치 상태를 먼저 보고 부른다.
 */
const PATH = {
    info: '/api/site/info',
};

export function useSiteInfoApi() {
    const client = useApiClient();

    return {
        /** 사이트 이름·소개·로고·아이콘·표시 기본값 */
        info: (signal?: AbortSignal): Promise<SiteInfoResponse> => client.get<SiteInfoResponse>(PATH.info, { signal }),
    };
}
