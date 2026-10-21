import type { LoginRequest, MeResponse, SocialProvider } from '~/composables/auth/api/useAuthApi.types';
import UtilProvider from '~/utils/util-provider';

/**
 * 가입·인증 API — 서버 주소 문자열(`/api/…`, `/oauth2/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장).
 * 화면(.vue)과 다른 컴포저블은 여기 함수만 부른다. 상태는 없다(화면이 쓰는 상태는 `useAuth`).
 *
 * 규약: `docs/features/login-contract.md` · community `docs/api-auth.md`. 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *
 * ⚠ 단순 버전 — 원본(community)에서 이 화면 범위(로그인·셸)에 필요 없는 가입·이메일 확인·비밀번호 재설정·소셜 가입 메서드를 뺐다. 해당 화면을 만들 때 원본에서 다시 가져온다.
 * ⚠ MOCK: 소셜 로그인 시작(`startSocialLogin`)만 아직 목업이다 — 소셜 로그인 흐름이 서버에 없다. 나머지는 실제 서버 호출이다.
 */
const PATH = {
    me: '/api/auth/me',
    providers: '/api/auth/providers',
    login: '/api/auth/login',
    logout: '/api/auth/logout',
    emailResend: '/api/auth/email/resend',
    /** 소셜 로그인 시작 — 요청이 아니라 페이지 이동이다 */
    socialStart: (providerId: string) => `/oauth2/authorization/${encodeURIComponent(providerId)}`,
};

export function useAuthApi() {
    // refreshCsrf: CSRF 쿠키를 다시 받는 호출 — 이 컴포저블이 주소를 안다
    const client = useApiClient({ refreshCsrf: () => client.get<MeResponse>(PATH.me) });
    // MOCK: 소셜 로그인은 페이지 이동 대신 토스트로 알린다. 컴포저블은 setup 에서만 부를 수 있어 여기서 미리 잡아 둔다
    const toast = useAppToast();
    const t = useTranslate();

    return {
        /** 로그인 상태. 항상 200 이고, 이 호출이 CSRF 쿠키를 내려준다. */
        me: () => client.get<MeResponse>(PATH.me),

        providers: async (): Promise<SocialProvider[]> => (await client.get<{ providers: SocialProvider[] }>(PATH.providers)).providers,

        login: (body: LoginRequest) => client.post<MeResponse>(PATH.login, body),

        logout: () => client.post<void>(PATH.logout),

        /** 인증 메일 다시 보내기 — 로그인 필요. 202, 본문 없음 */
        resendVerification: () => client.post<void>(PATH.emailResend),

        /** 소셜 로그인 화면으로 이동한다(서버가 결과에 따라 화면 주소로 되돌려 보낸다) */
        startSocialLogin: (providerId: string): void => {
            // MOCK: 이동하지 않는다 — 소셜 로그인 흐름이 서버에 아직 없다
            toast.show(t('provider.mockNotice', { provider: UtilProvider.name(t, providerId, providerId) }));
            // REAL: window.location.assign(PATH.socialStart(providerId));
        },
    };
}
