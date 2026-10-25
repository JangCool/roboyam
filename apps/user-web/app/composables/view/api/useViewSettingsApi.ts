import type { ViewSettings } from '~/composables/view/api/useViewSettingsApi.types';

/**
 * 보기 설정 API — 서버 주소 문자열(`/api/me/settings`)이 있는 **유일한 곳**이다. 상태는 없다(화면 상태는 `useViewSettings`).
 * 계약: `docs/features/member-social-contract.md` 「보기 설정」(서버 `server/api` `com.community.viewsetting`, 둘 다 `Cache-Control: no-store`).
 *  - `GET` → `ViewSettings`(저장한 적이 없어도 기본값 AUTO·100·OLDEST 로 200 — 행을 만들지 않는다)
 *  - `PUT` 세 칸 모두 → 저장된 `ViewSettings` · 빠진 칸·틀린 값은 한꺼번에 `400 VALIDATION fields.theme|textScale|commentOrder`(저장 없음).
 *    CSRF(`403 CSRF_INVALID` 면 `useApiClient` 가 쿠키를 다시 받아 한 번 다시 보낸다).
 *  - 로그인 필요(`401 UNAUTHENTICATED`) — **비로그인이면 부르지 않는다**(`useViewSettings` 가 이 브라우저 사본만 쓴다).
 * 서버는 값만 저장하고 **적용은 화면이 한다**(야간 모드·글자 크기는 `<html>` 에, 댓글 정렬은 댓글 목록 요청의 `sort`).
 *
 * 서버 연결됨(2026-10-24).
 */
const PATH = {
    settings: '/api/me/settings',
};

export function useViewSettingsApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 보기 설정 */
        get: (signal?: AbortSignal): Promise<ViewSettings> => client.get<ViewSettings>(PATH.settings, { signal }),

        /** 보기 설정 바꾸기 — 세 칸 모두 보낸다 */
        update: (settings: ViewSettings): Promise<ViewSettings> => client.put<ViewSettings>(PATH.settings, settings),
    };
}
