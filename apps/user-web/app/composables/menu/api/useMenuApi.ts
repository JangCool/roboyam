import type { MenuResponse } from '~/composables/menu/api/useMenuApi.types';

/**
 * 메뉴 API — 서버 주소 문자열(`/api/menu`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다(상태는 `useMenu`).
 *
 * 규약: `docs/features/menu-contract.md`(서버 `PublicMenuController`). `GET /api/menu`(그룹 `set` 기본 `main`)는 로그인 없이도 부를 수 있고(세션이 있으면 그 사람 기준으로 거른다)
 * 응답은 사람마다 달라 서버가 `Cache-Control: no-store` 로 준다(브라우저·중간 캐시에 남기지 않는다).
 * 화면이 다루는 오류 코드는 없다 — 서버 장애(`5xx`)·네트워크 끊김·모양이 맞지 않는 응답이면 `useMenu` 가 기본 메뉴(폴백)로 그린다.
 * 받은 모양은 한 번 더 `UtilMenu.parse` 로 읽는다.
 *
 * 서버 연결됨(2026-10-22).
 */
const PATH = {
    menu: '/api/menu',
};

export function useMenuApi() {
    // CSRF 쿠키를 다시 받는 호출 — 읽기뿐이라 쓸 일은 없지만 다른 API 컴포저블과 같은 모양으로 둔다. setup 문맥에서 미리 잡아 둔다
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 지금 보는 사람 기준으로 걸러지고 정렬된 메뉴 트리(`main` 그룹) */
        menu: (signal?: AbortSignal): Promise<MenuResponse> => client.get<MenuResponse>(PATH.menu, { signal }),
    };
}
