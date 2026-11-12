import type { SiteAboutResponse } from '~/composables/site/api/useSiteAboutApi.types';

/**
 * 공개 사이트 소개 API — 서버 주소 문자열(`/api/site/about`)이 있는 **유일한 곳**이다. 상태는 없다(쓰는 곳은 `/about` 페이지 하나라 그 페이지의 `ref`).
 * 비로그인도 받는다. 본문은 서버가 정화한 HTML 이다(관리자 설정 › 일반 「사이트 소개」에서 정한다). 소개가 있는지는 사이트 정보(`GET /api/site/info`)의 `about` 이 알려 준다(푸터 링크).
 */
const PATH = {
    about: '/api/site/about',
};

export function useSiteAboutApi() {
    const client = useApiClient();

    return {
        /** 사이트 소개 본문 — 없으면 `html: null` */
        about: (signal?: AbortSignal): Promise<SiteAboutResponse> => client.get<SiteAboutResponse>(PATH.about, { signal }),
    };
}
