import type { HomeLayoutBody, HomeLayoutSaveRequest, HomeLayoutView } from '~/composables/home/api/useHomeLayoutApi.types';

/**
 * 관리자 홈 배치 API — 서버 주소 문자열(`/api/admin/home-layouts`)이 있는 **유일한 곳**이다. 상태는 없다. 모양·가정은 `useHomeLayoutApi.types.ts`.
 * 계약: `docs/features/site-theme-layout-contract.md` 「API」(서버 `HomeLayoutController`).
 *   - `GET /api/admin/home-layouts/{theme}` → 200 `HomeLayoutView`  (`site.read`) — 저장한 배치, 없으면 테마 기본 배치(`updatedAt: null`) · 404 모르는 테마
 *   - `GET /api/admin/home-layouts/{theme}/defaults` → 200 `HomeLayoutBody`  (`site.read`) — 테마 기본 배치(초기화용, 저장하지 않는다)
 *   - `PUT /api/admin/home-layouts/{theme}` `HomeLayoutSaveRequest` → 200 `HomeLayoutView`  (`site.manage`) · 400 VALIDATION(`fields.layout.columns` · `fields.gadgets`(30개 한도) ·
 *       `fields.gadgets[i].id|type|title` · `fields.gadgets[i].config.count|sort`(공구 가젯 개수 1~10 · 정렬 3종) · `fields.layout.items[i]`(없는 가젯·두 번째 자리·격자 밖·종류별 최소 크기 미만) ·
 *       `fields.layout.items`(자리 없는 가젯·겹침))
 *       빠진 기존 가젯은 지우고, 기본 배치 ID(`seed-<n>`)는 그 시드 설정으로, 모르는 ID(`new-<n>`)는 종류별 처음 값으로 새로 만든다. 서버가 위로 당겨 저장한다.
 *       공구 가젯의 `config`(개수·정렬)는 보내면 그 값으로 바꾸고, 없으면 그대로 둔다(다른 종류의 `config` 는 무시).
 *       공개 홈(`GET /api/home/gadgets`)은 요청마다 읽어 바로 따라온다.
 */
const PATH = {
    layout: (theme: string) => `/api/admin/home-layouts/${encodeURIComponent(theme)}`,
    defaults: (theme: string) => `/api/admin/home-layouts/${encodeURIComponent(theme)}/defaults`,
};

export function useHomeLayoutApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 테마 하나의 배치 */
        get: (theme: string): Promise<HomeLayoutView> => client.get<HomeLayoutView>(PATH.layout(theme)),

        /** 테마 기본 배치(저장하지 않는다) */
        defaults: (theme: string): Promise<HomeLayoutBody> => client.get<HomeLayoutBody>(PATH.defaults(theme)),

        /** 배치 저장 */
        save: (theme: string, body: HomeLayoutSaveRequest): Promise<HomeLayoutView> => client.put<HomeLayoutView>(PATH.layout(theme), body),
    };
}
