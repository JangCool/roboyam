import type { MeSocialResponse, SocialLinkStartResponse } from '~/composables/auth/api/useAuthApi.types';

/**
 * 내 소셜 연결 API(`/api/me/social/**`) — 로그인이 필요하고 쓰기는 CSRF 헤더가 필요하다(`useApiClient` 가 붙인다). 상태는 없다.
 * 규약: `docs/features/login-contract.md` 「소셜 로그인」. 오류는 모두 `ApiError` 로 던져진다.
 */
const PATH = {
    list: '/api/me/social',
    link: (providerId: string) => `/api/me/social/${encodeURIComponent(providerId)}/link`,
    unlink: (providerId: string) => `/api/me/social/${encodeURIComponent(providerId)}`,
};

export function useMeSocialApi() {
    const client = useApiClient();

    return {
        /** 공급자 3종(카카오·네이버·구글 순)의 연결 상태 */
        list: () => client.get<MeSocialResponse>(PATH.list),

        /** 연결 시작 — 공급자 인가 주소(`redirectUrl`)를 받는다. `404 PROVIDER_NOT_FOUND` · `409 SOCIAL_ALREADY_LINKED` · `429` */
        startLink: (providerId: string) => client.post<SocialLinkStartResponse>(PATH.link(providerId)),

        /**
         * 연결 시작이 준 공급자 인가 주소로 페이지를 옮긴다 — `http(s)://` 주소일 때만. 옮겼으면 true.
         * 끝나면 서버가 `/settings/account?linked=…`(또는 `link=taken|failed`)로 되돌려 보낸다.
         */
        follow: (result: SocialLinkStartResponse | undefined): boolean => {
            const url = result?.redirectUrl;
            if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) return false;
            window.location.href = url;
            return true;
        },

        /** 연결 해제 — `204` · `404 SOCIAL_NOT_LINKED` · `409 SOCIAL_LAST_LOGIN_METHOD`(비밀번호 없는 회원의 마지막 연결) */
        unlink: (providerId: string) => client.del<void>(PATH.unlink(providerId)),
    };
}
