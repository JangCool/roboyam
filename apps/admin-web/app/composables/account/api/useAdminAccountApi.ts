import { mockInvitationAccept, mockInvitationCheck, mockPasswordChange, mockPasswordReset, mockPasswordResetCode } from '~/composables/account/api/mock/account-mock';
import type {
    InvitationAcceptRequest,
    InvitationCheckResponse,
    PasswordChangeRequest,
    PasswordResetCodeRequest,
    PasswordResetCodeResponse,
    PasswordResetRequest,
} from '~/composables/account/api/useAdminAccountApi.types';

/**
 * 운영자 계정 비밀번호 흐름 API — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *
 * 후보 계약(확정 전, 화면 확정 뒤 서버와 맞춘다). 앞의 넷은 로그인 전에 부르는 공개 API(`@AdminPublic`)라 세션이 없다.
 * 초대 토큰은 로그에 남지 않게 **URL이 아니라 요청 본문**으로 보낸다(메일 링크 `{ADMIN_URL}/invitation?token=…` 의 `token` 값 그대로, 유효 72시간).
 *   - `POST /api/admin/auth/invitation/check`           `{ token }` → 200 `{ email, roles: [{ roleId, roleName }], expiresAt }`
 *       · 404 INVITATION_NOT_FOUND(모르는 토큰) · 410 INVITATION_EXPIRED(유효 72시간 지남) · 409 INVITATION_USED(이미 수락) · 410 INVITATION_CANCELED(취소됨)
 *   - `POST /api/admin/auth/invitation/accept`          `{ token, nickname, password }` → 204(계정이 만들어진다. 로그인은 하지 않은 채 — 화면이 로그인으로 보낸다)
 *       · 위 토큰 오류가 그대로 날 수 있다(확인과 수락 사이에 만료·취소) · 400 VALIDATION(`fields.nickname|password`) · 409 NICKNAME_TAKEN
 *   - `POST /api/admin/auth/password-reset/code`        `{ email }` → 200 `{ codeLength: 6, expiresInMinutes: 10, cooldownSeconds: 60 }` **가입 여부와 무관하게 늘 같은 응답**(계정 존재를 알리지 않는다. 가입된 운영자일 때만 승인번호 메일을 보낸다)
 *       · 다시 받기도 같은 경로(새 번호가 가고 먼저 받은 번호는 못 쓴다) · 400 VALIDATION(`fields.email`, 형식만) · 429 TOO_MANY_REQUESTS(+`retryAfterSeconds`)
 *   - `POST /api/admin/auth/password-reset`             `{ email, certCode, newPassword }` → 204(그 운영자의 모든 세션을 끝낸다)
 *       · 400 VALIDATION(`fields.certCode|newPassword`) · 승인번호 오류(모두 400, `message` 는 서버 문구): CERT_CODE_INVALID(틀림, "N번 더 시도할 수 있어요") · CERT_CODE_EXPIRED(10분 지남) ·
 *         CERT_CODE_USED(이미 씀) · CERT_CODE_EXHAUSTED(5번 틀려 소진) · CERT_CODE_NOT_FOUND(받은 적 없음). 화면은 `code` 앞머리 `CERT_CODE_` 로 묶어 승인번호 칸 아래에 서버 문구를 그대로 보인다.
 *       · hamonica3 는 이 성공 뒤 **곧바로 로그인시킨다**(응답이 로그인한 사용자). 여기서는 204 로 두고 화면이 로그인으로 보낸다 — 자동 로그인 여부는 확정 필요.
 *   - `PUT  /api/admin/profile/password`                `{ currentPassword, newPassword }` → 204(**다른 기기의 세션만** 끝내고 지금 세션은 유지한다 — 가정) · 400 VALIDATION(`fields.currentPassword|newPassword`)
 *       · 400 INVALID_CURRENT_PASSWORD(현재 비밀번호가 틀림). ⚠ 401 이 아니다 — 공통 클라이언트는 로그인 상태에서의 401 UNAUTHENTICATED 를 "세션이 끝났다"로 보고 로그인으로 보낸다.
 * 비밀번호 규칙은 공개 사이트 가입 규칙과 같다(8~64자 · UTF-8 72바이트 이하 · 영문과 숫자 포함, 서버 `UserPolicy`) — hamonica3(8~30자 · 영문+숫자+특수문자 `@#$%^&+=!` · 공백 불가)와 다르다. 이름(닉네임)은 내 정보 닉네임 규칙과 같다(2~12자).
 * 감사 기록(비밀번호 변경·재설정·초대 수락)과 메일(승인번호) 문구는 서버 몫이다.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/account-mock.ts` 를 부른다(주소 스위치 `?mock=` 는 그 파일 머리 주석). 서버가 붙으면 `// MOCK:` 줄과 위 import 를 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    invitationCheck: () => '/api/admin/auth/invitation/check',
    invitationAccept: () => '/api/admin/auth/invitation/accept',
    resetCode: () => '/api/admin/auth/password-reset/code',
    reset: () => '/api/admin/auth/password-reset',
    passwordChange: () => '/api/admin/profile/password',
};

export function useAdminAccountApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 초대 링크의 토큰을 확인한다 — 유효하면 초대받은 이메일과 받을 역할 */
        checkInvitation: (token: string): Promise<InvitationCheckResponse> =>
            // MOCK:
            mockInvitationCheck(token),
        // REAL: client.post<InvitationCheckResponse>(PATH.invitationCheck(), { token }),

        /** 이름과 비밀번호를 정해 계정을 만든다. 204 */
        acceptInvitation: (body: InvitationAcceptRequest): Promise<void> =>
            // MOCK:
            mockInvitationAccept(body),
        // REAL: client.post<void>(PATH.invitationAccept(), body),

        /** 비밀번호 재설정 승인번호를 메일로 보낸다(다시 받기도 같다) — 가입 여부와 무관하게 늘 같은 응답 */
        requestPasswordResetCode: (body: PasswordResetCodeRequest): Promise<PasswordResetCodeResponse> =>
            // MOCK:
            mockPasswordResetCode(body),
        // REAL: client.post<PasswordResetCodeResponse>(PATH.resetCode(), body),

        /** 승인번호와 새 비밀번호를 함께 보내 비밀번호를 바꾼다. 204 */
        resetPassword: (body: PasswordResetRequest): Promise<void> =>
            // MOCK:
            mockPasswordReset(body),
        // REAL: client.post<void>(PATH.reset(), body),

        /** 로그인한 내가 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. 204 */
        changePassword: (body: PasswordChangeRequest): Promise<void> =>
            // MOCK:
            mockPasswordChange(body),
        // REAL: client.put<void>(PATH.passwordChange(), body),
    };
}
