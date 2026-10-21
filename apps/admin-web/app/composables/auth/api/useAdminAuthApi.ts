import { mockAdminLogin, mockAdminLogout, mockAdminMe } from '~/composables/auth/api/mock/admin-auth-mock';
import type { AdminLoginRequest, AdminMeResponse } from '~/composables/auth/api/useAdminAuthApi.types';

/**
 * 관리자 로그인 API — 서버 주소 문자열(`/api/admin/auth/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장).
 * 화면(.vue)과 다른 컴포저블은 여기 함수만 부른다. 상태는 없다(화면이 쓰는 상태는 `useAdminAuth`).
 *
 * 후보 계약(확정 전): 참고 community `docs/api-admin-auth.md`. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET  /api/admin/auth/me`     항상 200. `{ authenticated, user, siteName }`. 이 호출이 CSRF 쿠키를 내려준다.
 *   - `POST /api/admin/auth/login`  `{ email, password }` → 200 me 와 같은 모양 · 401 INVALID_CREDENTIALS(없는 계정·틀린 비밀번호·운영진이 아닌 회원) ·
 *                                   429 LOGIN_LOCKED(+`retryAfterSeconds`) · 400 VALIDATION(+`fields`)
 *   - `POST /api/admin/auth/logout` → 204 (비로그인이어도 204)
 *   세션은 서버가 준 HttpOnly 쿠키(`ADMIN_SESSION`, 마지막 활동 뒤 2시간)가 들고 있다 — 화면은 토큰을 다루지 않는다.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/admin-auth-mock.ts` 의 목업을 부른다(목업 계정·스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/auth',
    me: () => `${PATH.root}/me`,
    login: () => `${PATH.root}/login`,
    logout: () => `${PATH.root}/logout`,
};

export function useAdminAuthApi() {
    // REAL: refreshCsrf: CSRF 쿠키를 다시 받는 호출 — 이 컴포저블이 주소를 안다
    // REAL: const client = useApiClient({ refreshCsrf: () => client.get<AdminMeResponse>(PATH.me()) });
    void PATH;

    return {
        /** 로그인 상태. 항상 200 이고, 이 호출이 CSRF 쿠키를 내려준다. 비로그인에도 사이트 이름(`siteName`)을 준다. */
        me: (): Promise<AdminMeResponse> =>
            // MOCK:
            mockAdminMe(),
        // REAL: client.get<AdminMeResponse>(PATH.me()),

        /** 로그인. 실패하면 `ApiError` 를 던진다(잘못된 정보 401 · 잠금 429 · 입력 400 …). */
        login: (body: AdminLoginRequest): Promise<AdminMeResponse> =>
            // MOCK:
            mockAdminLogin(body),
        // REAL: client.post<AdminMeResponse>(PATH.login(), body),

        /** 로그아웃. 204, 본문 없음 */
        logout: (): Promise<void> =>
            // MOCK:
            mockAdminLogout(),
        // REAL: client.post<void>(PATH.logout()),
    };
}
