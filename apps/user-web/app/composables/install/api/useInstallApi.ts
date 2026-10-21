import type { MailCheckResult, MailInput } from '~/composables/install/api/useInstallApi.types';
import type { InstallBoardPresetsView, InstallChecksView, InstallCompleteRequest, InstallCompleteResponse, InstallationStatus, InstallTokenResponse } from '~/composables/install/api/useInstallApi.types';

/**
 * 설치 마법사 API — 서버 주소 문자열(`/api/system/installation/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장).
 * 화면(.vue)과 다른 컴포저블은 여기 함수만 부른다. 상태는 없다(앱이 아는 설치 상태는 `useInstallState`).
 *
 * 규약: `docs/features/install-contract.md` · community `docs/api-install.md`. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *  - 모든 호출은 **설치 전에도** 열려 있다(그 밖의 `/api/**` 는 `503 NOT_INSTALLED`). `status` 외에는 설치가 끝나면 `409 ALREADY_INSTALLED`.
 *  - `verify-token` 이후의 호출(`checks`·`mail-check`·`complete`)은 토큰을 맞힌 세션만 쓴다(`401 INSTALL_AUTH_REQUIRED`).
 *  - ⚠ **CSRF 쿠키는 `status` 가 내려 준다** — 설치 전에는 `/api/auth/me` 가 막혀 있어 `useAuthApi` 의 쿠키 갱신을 쓸 수 없다.
 *    CSRF 거절(`403 CSRF_INVALID`)이면 `useApiClient` 가 `status` 를 다시 불러 쿠키를 받고 한 번만 다시 보낸다.
 */
const PATH = {
    status: '/api/system/installation/status',
    verifyToken: '/api/system/installation/verify-token',
    checks: '/api/system/installation/checks',
    boardPresets: '/api/system/installation/board-presets',
    mailCheck: '/api/system/installation/mail-check',
    complete: '/api/system/installation/complete',
};

export function useInstallApi() {
    // refreshCsrf: CSRF 쿠키를 다시 받는 호출 — 설치 전에도 되는 `status`
    const client = useApiClient({ refreshCsrf: () => client.get<InstallationStatus>(PATH.status) });

    return {
        /** 설치 상태. 누구나 부를 수 있고, 이 호출이 CSRF 쿠키를 내려 준다 */
        status: (signal?: AbortSignal) => client.get<InstallationStatus>(PATH.status, { signal }),

        /** 설치 토큰 확인 — `401 INSTALL_TOKEN_INVALID` · `429 INSTALL_TOKEN_LOCKED`(+`retryAfterSeconds`). 맞으면 서버 세션에 표시된다 */
        verifyToken: (token: string) => client.post<InstallTokenResponse>(PATH.verifyToken, { token }),

        /** 설치가 돌아갈 수 있는지 점검한 결과(입력이 아니라 판정) — 토큰 인증 뒤 */
        checks: (signal?: AbortSignal) => client.get<InstallChecksView>(PATH.checks, { signal }),

        /** 고를 수 있는 기본 게시판 구성(프리셋)과 기본값 — 토큰 인증 뒤 */
        boardPresets: (signal?: AbortSignal) => client.get<InstallBoardPresetsView>(PATH.boardPresets, { signal }),

        /** 메일 서버 연결 시험 — 접속·인증까지만 해 보고 메일은 보내지 않는다 */
        mailCheck: (body: MailInput) => client.post<MailCheckResult>(PATH.mailCheck, body),

        /** 설치 실행 — 한 번의 호출로 원자적으로 끝난다(`201`). 토큰 인증이 이 순간 무효가 된다 */
        complete: (body: InstallCompleteRequest) => client.post<InstallCompleteResponse>(PATH.complete, body),
    };
}
