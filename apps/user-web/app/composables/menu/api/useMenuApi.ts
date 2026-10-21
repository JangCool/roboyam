import { mockMenu } from '~/composables/menu/api/mock/menu-mock';
import type { MenuResponse } from '~/composables/menu/api/useMenuApi.types';

/**
 * 메뉴 API — 서버 주소 문자열(`/api/menu`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다(상태는 `useMenu`).
 *
 * 규약: community `docs/api-menu.md` 5절. 로그인 없이도 부를 수 있고(세션이 있으면 그 사람 기준) 응답은 사람마다 다르다(`Cache-Control: private, no-cache`).
 * 오류 코드는 없다 — 서버 장애(`5xx`)나 네트워크 끊김이면 `useMenu` 가 기본 메뉴(폴백)로 그린다.
 * 받은 모양은 한 번 더 `UtilMenu.parse` 로 읽는다.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/menu-mock.ts` 의 목업을 부른다(로그인했는지에 따라 "내 소모임" 줄이 달라진다). 서버가 붙으면 `// MOCK:` 줄을 `// REAL:` 줄로 바꾼다.
 */
const PATH = {
    menu: '/api/menu',
};

export function useMenuApi() {
    // REAL: refreshCsrf: CSRF 쿠키를 다시 받는 호출 — 읽기뿐이라 쓸 일은 없지만 다른 API 컴포저블과 같은 모양으로 둔다
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;
    // MOCK: 로그인 여부에 따라 응답이 달라지는 것을 흉내 낸다
    const { user } = useAuth();

    return {
        /** 지금 보는 사람 기준으로 걸러지고 정렬된 메뉴 트리 */
        menu: (signal?: AbortSignal): Promise<MenuResponse> =>
            // MOCK:
            mockMenu(user.value !== null, signal),
        // REAL: client.get<MenuResponse>(PATH.menu, { signal }),
    };
}
