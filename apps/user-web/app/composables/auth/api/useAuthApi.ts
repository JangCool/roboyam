import type { LoginRequest, MeResponse, SocialPending, SocialProvider, SocialSignupRequest } from '~/composables/auth/api/useAuthApi.types';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 가입·인증 API(`/api/auth/**`) — 서버 주소 문자열이 있는 **유일한 곳**이다 (frontend-guide 2장). 내 소셜 연결(`/api/me/social/**`)은 `useMeSocialApi`.
 * 화면(.vue)과 다른 컴포저블은 여기 함수만 부른다. 상태는 없다(화면이 쓰는 상태는 `useAuth`).
 *
 * 규약: `docs/features/login-contract.md`(「소셜 로그인」 절 포함) · community `docs/api-auth.md`. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 * 모두 실제 서버 호출이다. 소셜 로그인 시작만 요청이 아니라 **페이지 이동**이다(서버가 공급자로 302 하고, 끝나면 정해진 화면 경로로 되돌려 보낸다).
 *
 * ⚠ 단순 버전 — 원본(community)에서 이 화면 범위에 필요 없는 이메일 가입·이메일 확인·비밀번호 재설정 메서드를 뺐다. 해당 화면을 만들 때 원본에서 다시 가져온다.
 */
const PATH = {
    me: '/api/auth/me',
    providers: '/api/auth/providers',
    login: '/api/auth/login',
    logout: '/api/auth/logout',
    emailResend: '/api/auth/email/resend',
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
