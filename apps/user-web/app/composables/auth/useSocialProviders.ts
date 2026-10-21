import type { SocialProvider } from '~/composables/auth/api/useAuthApi.types';

/**
 * 쓸 수 있는 소셜 로그인 목록 — 서버가 키가 설정된 것만 준다(`useAuthApi().providers()`).
 * 빈 배열이면 소셜 영역 자체를 숨긴다. 한 번 받으면 다시 묻지 않는다.
 *
 * 목록을 못 받아도(서버 오류) 화면은 이메일로 계속할 수 있어야 하므로 던지지 않고 빈 목록으로 둔다.
 * 실패한 것은 기억하지 않는다 — 다음에 이 화면에 오면 다시 시도한다.
 */
export function useSocialProviders() {
    const api = useAuthApi();

    /** null 은 "아직 안 물어봄" */
    const providers = useState<SocialProvider[] | null>('auth-providers', () => null);

    async function ensure(): Promise<SocialProvider[]> {
        if (providers.value !== null) return providers.value;
        try {
            providers.value = await api.providers();
        } catch {
            return [];
        }
        return providers.value;
    }

    return { providers, ensure };
}
