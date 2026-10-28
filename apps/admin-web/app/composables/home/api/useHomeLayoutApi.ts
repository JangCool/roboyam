import { mockHomeLayout, mockHomeLayoutDefaults, mockHomeLayoutThemes, mockSaveHomeLayout } from '~/composables/home/api/mock/home-layout-mock';
import type { HomeLayoutBody, HomeLayoutSaveRequest, HomeLayoutThemesResponse, HomeLayoutView } from '~/composables/home/api/useHomeLayoutApi.types';

/**
 * 관리자 홈 배치 API — 서버 주소 문자열(`/api/admin/home-layouts`)이 있는 **유일한 곳**이다. 상태는 없다. 모양·가정은 `useHomeLayoutApi.types.ts`.
 *   - `GET /api/admin/home-layouts` → 200 `{ themes }`  (`site.read`) — 배치를 고칠 수 있는 테마(보일 순서)
 *   - `GET /api/admin/home-layouts/{theme}` → 200 `HomeLayoutView`  (`site.read`) — 저장한 배치, 없으면 테마 기본 배치(`updatedAt: null`)
 *   - `GET /api/admin/home-layouts/{theme}/defaults` → 200 `HomeLayoutBody`  (`site.read`) — 테마 기본 배치(초기화용, 저장하지 않는다)
 *   - `PUT /api/admin/home-layouts/{theme}` `HomeLayoutSaveRequest` → 200 `HomeLayoutView`  (`site.manage`) · 400 VALIDATION(`fields.layout` 범위·겹침, `fields.gadgets` 종류·개수 한도)
 *       빠진 기존 가젯은 지우고, 모르는 ID(`new-<n>`·기본 배치 ID)는 종류별 기본 설정으로 새로 만든다. 공개 홈(`GET /api/home/gadgets`)은 서버 사본 뒤에 따라온다.
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/home-layout-mock.ts`(메모리)를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    themes: '/api/admin/home-layouts',
    layout: (theme: string) => `/api/admin/home-layouts/${encodeURIComponent(theme)}`,
    defaults: (theme: string) => `/api/admin/home-layouts/${encodeURIComponent(theme)}/defaults`,
};

export function useHomeLayoutApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 배치를 고칠 수 있는 테마 */
        themes: (): Promise<HomeLayoutThemesResponse> =>
            // MOCK:
            mockHomeLayoutThemes(),
        // REAL: client.get<HomeLayoutThemesResponse>(PATH.themes),

        /** 테마 하나의 배치 */
        get: (theme: string): Promise<HomeLayoutView> =>
            // MOCK:
            mockHomeLayout(theme),
        // REAL: client.get<HomeLayoutView>(PATH.layout(theme)),

        /** 테마 기본 배치(저장하지 않는다) */
        defaults: (theme: string): Promise<HomeLayoutBody> =>
            // MOCK:
            mockHomeLayoutDefaults(theme),
        // REAL: client.get<HomeLayoutBody>(PATH.defaults(theme)),

        /** 배치 저장 */
        save: (theme: string, body: HomeLayoutSaveRequest): Promise<HomeLayoutView> =>
            // MOCK:
            mockSaveHomeLayout(theme, body),
        // REAL: client.put<HomeLayoutView>(PATH.layout(theme), body),
    };
}
