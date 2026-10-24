import type { AccountView, EmailChangeRequest, PasswordChangeRequest } from '~/composables/account/api/useAccountApi.types';

/**
 * 내 계정 API — 서버 주소 문자열(`/api/me/account` · `/api/me/password` · `/api/me/email-change`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 규약: `docs/features/member-account-contract.md` · community `docs/api-account.md`. 로그인 필요(401), 쓰기는 CSRF.
 * 소셜 연결·해제(`/api/me/social/**`)는 `useMeSocialApi`, 이메일 변경 링크 확인(`POST /api/auth/email/verify`)은 `useAuthApi.verifyEmail`.
 *
 *  - `GET /api/me/account` → `AccountView`
 *  - `PUT /api/me/password` `{ currentPassword, newPassword }` → `204`.
 *    오류: `401 CURRENT_PASSWORD_WRONG`(로그인 실패와 같은 횟수) · `429 LOGIN_LOCKED`(+`retryAfterSeconds`) · `400 SAME_PASSWORD` · `400 VALIDATION`(`fields.currentPassword`·`fields.newPassword`) · `409 PASSWORD_NOT_SET`
 *  - `POST /api/me/email-change` `{ newEmail, password }` → `202`.
 *    오류: `400 VALIDATION`(`fields.newEmail`·`fields.password`) · `409 EMAIL_TAKEN` · `401 CURRENT_PASSWORD_WRONG` · `429 LOGIN_LOCKED` · `429 RESEND_COOLDOWN`(+`retryAfterSeconds`) · `409 EMAIL_CHANGE_NOT_ALLOWED`
 *
 * 비밀번호 변경 성공은 이 세션의 ID 를 바꾼다(새 세션 쿠키는 응답에 실려 브라우저가 바꾼다). CSRF 는 세션과 따로인 쿠키(`XSRF-TOKEN`) 방식이고
 * `useApiClient` 가 요청마다 쿠키를 다시 읽으므로 다음 쓰기 요청도 그대로 된다(거절되면 `refreshCsrf` 로 한 번 다시 보낸다).
 */
const PATH = {
    account: '/api/me/account',
    password: '/api/me/password',
    emailChange: '/api/me/email-change',
};

export function useAccountApi() {
    const auth = useAuthApi();
    // refreshCsrf: CSRF 쿠키를 다시 받는 호출 — 로그인 상태 조회가 쿠키를 내려준다
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 계정 — 이메일·인증 여부·비밀번호 유무·변경 대기 이메일·소셜 공급자 */
        account: () => client.get<AccountView>(PATH.account),

        /** 비밀번호 변경 — 성공하면 이 기기만 로그인이 남고 다른 기기의 로그인은 서버가 끊는다 */
        changePassword: (body: PasswordChangeRequest) => client.put<void>(PATH.password, body),

        /** 이메일 변경 — 새 주소로 인증 메일을 보낸다. 링크를 누르기 전까지 지금 이메일이 그대로다 */
        requestEmailChange: (body: EmailChangeRequest) => client.post<void>(PATH.emailChange, body),
    };
}
