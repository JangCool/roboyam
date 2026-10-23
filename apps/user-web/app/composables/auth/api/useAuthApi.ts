import {
    mockCheckResetToken,
    mockEmailAvailable,
    mockForgotPassword,
    mockNicknameAvailable,
    mockResetPassword,
    mockSignup,
    mockVerifyEmail,
} from '~/composables/auth/api/mock/auth-mock';
import type {
    EmailAvailability,
    EmailVerifyResult,
    LoginRequest,
    MeResponse,
    NicknameAvailability,
    PasswordResetCheck,
    PasswordResetRequest,
    SignupRequest,
    SocialPending,
    SocialProvider,
    SocialSignupRequest,
} from '~/composables/auth/api/useAuthApi.types';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 가입·인증 API(`/api/auth/**`) — 서버 주소 문자열이 있는 **유일한 곳**이다 (frontend-guide 2장). 내 소셜 연결(`/api/me/social/**`)은 `useMeSocialApi`, 내 계정(`/api/me/account` 등)은 `useAccountApi`.
 * 화면(.vue)과 다른 컴포저블은 여기 함수만 부른다. 상태는 없다(화면이 쓰는 상태는 `useAuth`).
 *
 * 규약: `docs/features/login-contract.md`(「소셜 로그인」 절 포함) · `docs/features/member-account-contract.md` · community `docs/api-auth.md`. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 * 소셜 로그인 시작만 요청이 아니라 **페이지 이동**이다(서버가 공급자로 302 하고, 끝나면 정해진 화면 경로로 되돌려 보낸다).
 *
 * 실제 서버: me · providers · login · logout · 인증 메일 다시 보내기 · 소셜 로그인(시작·가입 대기·가입 마무리).
 * ⚠ MOCK: 이메일 가입·이메일/닉네임 확인·메일 링크 확인·비밀번호 찾기·재설정은 서버에 API 가 없어 `./mock/auth-mock.ts` 를 부른다(모양은 community `docs/api-auth.md` 그대로).
 * 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    me: '/api/auth/me',
    providers: '/api/auth/providers',
    login: '/api/auth/login',
    logout: '/api/auth/logout',
    emailResend: '/api/auth/email/resend',
    signup: '/api/auth/signup',
    emailAvailable: '/api/auth/email-available',
    nicknameAvailable: '/api/auth/nickname-available',
    emailVerify: '/api/auth/email/verify',
    passwordForgot: '/api/auth/password/forgot',
    passwordResetCheck: '/api/auth/password/reset/check',
    passwordReset: '/api/auth/password/reset',
    /** 소셜 로그인 시작 — 요청이 아니라 페이지 이동이다 */
    socialStart: (providerId: string) => `/api/auth/social/${encodeURIComponent(providerId)}/start`,
    socialPending: '/api/auth/social/pending',
    socialComplete: '/api/auth/social/complete',
};

export function useAuthApi() {
    // refreshCsrf: CSRF 쿠키를 다시 받는 호출 — 이 컴포저블이 주소를 안다
    const client = useApiClient({ refreshCsrf: () => client.get<MeResponse>(PATH.me) });

    return {
        /** 로그인 상태. 항상 200 이고, 이 호출이 CSRF 쿠키를 내려준다. */
        me: () => client.get<MeResponse>(PATH.me),

        providers: async (): Promise<SocialProvider[]> => (await client.get<{ providers: SocialProvider[] }>(PATH.providers)).providers,

        login: (body: LoginRequest) => client.post<MeResponse>(PATH.login, body),

        logout: () => client.post<void>(PATH.logout),

        /** 인증 메일 다시 보내기 — 로그인 필요. 202, 본문 없음 */
        resendVerification: () => client.post<void>(PATH.emailResend),

        /**
         * 이메일 가입 — `201` 로 `me` 와 같은 모양(가입 즉시 로그인, 인증 메일이 나간다).
         * `400 VALIDATION`(`fields` — 이메일·비밀번호·닉네임·동의 위반을 한 번에) · `409 EMAIL_TAKEN` · `409 NICKNAME_TAKEN`
         */
        signup: (body: SignupRequest): Promise<MeResponse> =>
            // MOCK:
            mockSignup(body),
        // REAL: client.post<MeResponse>(PATH.signup, body),

        /** 이메일 중복 확인 — `{ available }`. 형식이 틀리면 `400 VALIDATION` */
        emailAvailable: (email: string, signal?: AbortSignal): Promise<EmailAvailability> =>
            // MOCK:
            mockEmailAvailable(email, signal),
        // REAL: client.get<EmailAvailability>(PATH.emailAvailable, { query: { email }, signal }),

        /** 닉네임 확인 — `{ available, reason: LENGTH|CHARS|DUPLICATE|RESERVED|null }` */
        nicknameAvailable: (nickname: string, signal?: AbortSignal): Promise<NicknameAvailability> =>
            // MOCK:
            mockNicknameAvailable(nickname, signal),
        // REAL: client.get<NicknameAvailability>(PATH.nicknameAvailable, { query: { nickname }, signal }),

        /**
         * 메일 링크 확인(로그인 없이) — 인증 링크와 이메일 변경 링크가 같은 주소다. `{ verified, changed }`.
         * `400 TOKEN_INVALID` · `410 TOKEN_EXPIRED` · `409 EMAIL_TAKEN`(변경 링크에서만)
         */
        verifyEmail: (token: string): Promise<EmailVerifyResult> =>
            // MOCK:
            mockVerifyEmail(token),
        // REAL: client.post<EmailVerifyResult>(PATH.emailVerify, { token }),

        /** 비밀번호 재설정 메일 요청 — 가입 여부와 관계없이 늘 `202`(계정이 있는지 알리지 않는다). 형식 오류만 `400 VALIDATION` */
        forgotPassword: (email: string): Promise<void> =>
            // MOCK:
            mockForgotPassword(email),
        // REAL: client.post<void>(PATH.passwordForgot, { email }),

        /** 재설정 링크가 아직 쓸 수 있는가 — `{ valid: true }` · `400 TOKEN_INVALID` · `410 TOKEN_EXPIRED` */
        checkResetToken: (token: string): Promise<PasswordResetCheck> =>
            // MOCK:
            mockCheckResetToken(token),
        // REAL: client.get<PasswordResetCheck>(PATH.passwordResetCheck, { query: { token } }),

        /**
         * 새 비밀번호 저장 — `204`. 성공하면 그 계정의 **모든 세션이 끝난다**.
         * `400 VALIDATION`(`fields.newPassword`) · `400 TOKEN_INVALID` · `410 TOKEN_EXPIRED` · `400 SAME_PASSWORD`
         */
        resetPassword: (body: PasswordResetRequest): Promise<void> =>
            // MOCK:
            mockResetPassword(body),
        // REAL: client.post<void>(PATH.passwordReset, body),

        /**
         * 소셜 로그인 화면으로 이동한다(서버가 결과에 따라 화면 주소로 되돌려 보낸다).
         * `next`(로그인 뒤 돌아갈 곳)는 서버 규칙에 맞는 같은 사이트 상대 경로일 때만 넘긴다 — 아니면 붙이지 않는다(서버가 400 JSON 으로 거절하면 빈 화면이 된다).
         */
        startSocialLogin: (providerId: string, next?: unknown): void => {
            const safe = UtilRedirect.socialNext(next);
            window.location.assign(PATH.socialStart(providerId) + (safe ? `?next=${encodeURIComponent(safe)}` : ''));
        },

        /** 이어가는 소셜 가입 — 없거나 만료면 `404 NO_PENDING` */
        socialPending: () => client.get<SocialPending>(PATH.socialPending),

        /**
         * 소셜 가입 마무리(닉네임 + 약관 동의 3개) — `201` 로 `me` 와 같은 모양(가입 즉시 로그인).
         * `400 VALIDATION`(`fields`) · `409 NICKNAME_TAKEN` · `409 EMAIL_TAKEN`·`SOCIAL_ALREADY_REGISTERED` · `404 NO_PENDING` · `429 SOCIAL_RATE_LIMITED`
         */
        completeSocialSignup: (body: SocialSignupRequest) => client.post<MeResponse>(PATH.socialComplete, body),
    };
}
