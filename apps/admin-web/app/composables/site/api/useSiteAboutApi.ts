import type { SiteAboutAdminResponse, SiteAboutRequest } from '~/composables/site/api/useSiteAboutApi.types';

/**
 * 사이트 소개 관리 API — 서버 주소 문자열(`/api/admin/site/about`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 값은 `SiteAboutSettings` 의 `ref`).
 *
 * 계약:
 *   - `GET /api/admin/site/about` → 200 `{ html|null, updatedAt|null }`  (`site.read`)
 *   - `PUT /api/admin/site/about` `{ html|null }` → 200 같은 모양  (`site.manage`)
 *       · 서버가 HTML 을 정화해 저장하고 정화한 본문을 돌려준다. 빈 값은 null(소개 없음 — 공개 사이트 정보의 `about` 이 false 가 되어 푸터 링크가 숨는다)
 *       · 400 VALIDATION(`fields.html` — 20000자 초과) · 403 PERMISSION_DENIED
 * 공개 사이트는 `GET /api/site/about`(user-web `/about`)으로 같은 본문을 읽는다.
 */
const PATH = {
    about: '/api/admin/site/about',
};

export function useSiteAboutApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 사이트 소개(정화된 HTML·저장 시각) */
        get: (): Promise<SiteAboutAdminResponse> => client.get<SiteAboutAdminResponse>(PATH.about),

        /** 사이트 소개를 저장한다 — 정화된 본문을 돌려준다. `html: null` 은 소개를 지운다 */
        save: (body: SiteAboutRequest): Promise<SiteAboutAdminResponse> => client.put<SiteAboutAdminResponse>(PATH.about, body),
    };
}
