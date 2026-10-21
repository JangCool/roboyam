import type {
    InvitationAcceptRequest,
    InvitationCheckResponse,
    PasswordChangeRequest,
    PasswordResetCodeRequest,
    PasswordResetCodeResponse,
    PasswordResetRequest,
} from '~/composables/account/api/useAdminAccountApi.types';
import type { AdminMeResponse } from '~/composables/auth/api/useAdminAuthApi.types';

/**
 * 운영자 계정 비밀번호 흐름 API — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *
 * 계약(서버 `AdminAccountController` · `ProfileController#password`, `docs/features/roles-contract.md` 「5단계 구현 결과」). 앞의 넷은 로그인 전에 부르는 공개 API(`@AdminPublic`)라 세션이 없고,
 * 쓰기 요청이라 CSRF 헤더가 필요하다(쿠키는 앱이 뜰 때 `plugins/auth.client.ts` 의 `GET me` 가 받아 두고, 모자라면 `useApiClient` 가 `refreshCsrf` 로 다시 받아 한 번 더 보낸다).
 * 초대 토큰은 로그에 남지 않게 **URL이 아니라 요청 본문**으로 보낸다(메일 링크 `{ADMIN_URL}/invitation?token=…` 의 `token` 값 그대로, 유효 72시간).
 *   - `POST /api/admin/auth/invitation/check`           `{ token }` → 200 `{ email, roles: [{ roleId, roleName }], expiresAt }`
 *       · 404 INVITATION_NOT_FOUND(모르는·빈·**취소된** 토큰 — 취소하면 행이 지워진다) · 410 INVITATION_EXPIRED(유효 72시간 지남) · 409 INVITATION_USED(이미 수락, 만료와 무관)
 *   - `POST /api/admin/auth/invitation/accept`          `{ token, nickname, password }` → 204(계정이 만들어진다. **로그인시키지 않는다** — 화면이 로그인으로 보낸다)
 *       · 위 토큰 오류가 그대로 날 수 있다(확인과 수락 사이에 만료·취소) · 400 VALIDATION(`fields.nickname|password`) · 409 NICKNAME_TAKEN(대소문자 무시)
 *       · 409 OPERATOR_EMAIL_DUPLICATED(그 사이 같은 이메일 운영자가 생김 — 칸에 붙지 않는 오류, 서버 문구를 폼 위쪽에)
 *   - `POST /api/admin/auth/password-reset/code`        `{ email }` → 200 `{ codeLength: 6, expiresInMinutes: 10, cooldownSeconds: 60 }` **가입 여부와 무관하게 늘 같은 응답**(계정 존재를 알리지 않는다. 가입된 운영자일 때만 승인번호 메일이 간다)
 *       · 다시 받기도 같은 경로(새 번호가 가고 먼저 받은 번호는 못 쓴다) · 400 VALIDATION(`fields.email`, 형식만) · 429 TOO_MANY_REQUESTS(+`retryAfterSeconds`, `Retry-After` 헤더)
 *   - `POST /api/admin/auth/password-reset`             `{ email, certCode, newPassword }` → **200 + 로그인된 `me`**(`POST /login` 과 같은 모양, 새 `ADMIN_SESSION` 쿠키 — 그 운영자의 옛 세션은 모두 끝난다)
 *       · 400 VALIDATION(`fields.email|certCode|newPassword` — 승인번호 **형식**(숫자 6자리)이 틀리면 CERT_CODE_INVALID 가 아니라 `fields.certCode`)
 *       · 승인번호 오류(모두 400, `message` 는 서버 문구): CERT_CODE_INVALID(틀림, 문구에 "N번 더") · CERT_CODE_EXPIRED(10분 지남) · CERT_CODE_USED(이미 씀) · CERT_CODE_EXHAUSTED(5번 틀려 소진) · CERT_CODE_NOT_FOUND(받은 적 없음).
 *         화면은 `code` 앞머리 `CERT_CODE_` 로 묶어 승인번호 칸 아래에 서버 문구를 그대로 보인다.
 *       · 403 PASSWORD_RESET_NOT_ALLOWED(승인번호는 맞는데 그 사이 계정이 잠금 등) — `CERT_CODE_` 묶음이 아니다. 서버 문구를 폼 위쪽에.
 *   - `PUT  /api/admin/profile/password`                `{ currentPassword, newPassword }` → 204(**다른 기기의 세션만** 끝내고 지금 세션은 유지한다)
 *       · 400 VALIDATION(`fields.currentPassword|newPassword` — 새 비밀번호가 현재와 같으면 `fields.newPassword`) · 400 INVALID_CURRENT_PASSWORD(현재 비밀번호가 틀림) · 401 UNAUTHENTICATED(비로그인).
 *         ⚠ 현재 비밀번호 틀림이 401 이 아니다 — 공통 클라이언트는 로그인 상태에서의 401 UNAUTHENTICATED 를 "세션이 끝났다"로 보고 로그인으로 보낸다.
 * 비밀번호 규칙은 공개 사이트 가입 규칙과 같다(8~64자 · UTF-8 72바이트 이하 · 영문과 숫자 포함, 서버 `UserPolicy`). 이름(닉네임)은 내 정보 닉네임 규칙과 같다(2~12자).
 * 감사 기록(비밀번호 변경·재설정·초대 수락)과 메일(승인번호) 문구는 서버 몫이다. 서버 `message` 는 요청 언어(`Accept-Language`)로 온다.
 */
const PATH = {
    invitationCheck: () => '/api/admin/auth/invitation/check',
    invitationAccept: () => '/api/admin/auth/invitation/accept',
    resetCode: () => '/api/admin/auth/password-reset/code',
    reset: () => '/api/admin/auth/password-reset',
    passwordChange: () => '/api/admin/profile/password',
};

export function useAdminAccountApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 초대 링크의 토큰을 확인한다 — 유효하면 초대받은 이메일과 받을 역할 */
        checkInvitation: (token: string): Promise<InvitationCheckResponse> => client.post<InvitationCheckResponse>(PATH.invitationCheck(), { token }),

        /** 이름과 비밀번호를 정해 계정을 만든다. 204 — 로그인시키지 않는다 */
        acceptInvitation: (body: InvitationAcceptRequest): Promise<void> => client.post<void>(PATH.invitationAccept(), body),

        /** 비밀번호 재설정 승인번호를 메일로 보낸다(다시 받기도 같다) — 가입 여부와 무관하게 늘 같은 응답 */
        requestPasswordResetCode: (body: PasswordResetCodeRequest): Promise<PasswordResetCodeResponse> => client.post<PasswordResetCodeResponse>(PATH.resetCode(), body),

        /** 승인번호와 새 비밀번호를 함께 보내 비밀번호를 바꾼다. 200 + 로그인된 `me`(서버가 곧바로 로그인시킨다) */
        resetPassword: (body: PasswordResetRequest): Promise<AdminMeResponse> => client.post<AdminMeResponse>(PATH.reset(), body),

        /** 로그인한 내가 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. 204 */
        changePassword: (body: PasswordChangeRequest): Promise<void> => client.put<void>(PATH.passwordChange(), body),
    };
}
