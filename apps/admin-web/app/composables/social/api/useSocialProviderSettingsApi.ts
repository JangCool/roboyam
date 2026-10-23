import type {
    SocialProviderCode,
    SocialProviderSaveRequest,
    SocialProviderSettingsView,
    SocialProviderSettingView,
    SocialSettingHistoryPage,
    SocialSettingHistoryQuery,
} from '~/composables/social/api/useSocialProviderSettingsApi.types';

/**
 * 소셜 로그인 공급자 설정 API — 서버 주소 문자열(`/api/admin/social-providers…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 계약(서버 `SocialProviderSettingsController`, `docs/features/login-contract.md` 「관리자 소셜 로그인 설정」): 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET /api/admin/social-providers` → 200 `{ cryptoKeyConfigured, publicUrlConfigured, providers: [카카오·네이버·구글] }`  (`site.read`)
 *   - `PUT /api/admin/social-providers/{provider}` `{ clientId, clientSecret?, enabled }` → 200 그 공급자 항목  (`site.secret`)
 *       · 검사 순서: 권한(403) → 공급자(404 `PROVIDER_NOT_FOUND`) → 암호화 키(503 `CRYPTO_KEY_MISSING`) → 입력(400 `VALIDATION` `fields.clientId`·`clientSecret`·`enabled` 한꺼번에)
 *       · 시크릿을 빼거나 비우면 저장된 값을 유지한다(저장된 것이 없거나 지금 키로 풀 수 없으면 `fields.clientSecret`). 공개 주소가 없으면 켜기를 `fields.enabled` 로 거절한다
 *       · 바뀐 칸이 없으면 서버가 아무것도 쓰지 않는다(이력·감사 없음)
 *   - `DELETE /api/admin/social-providers/{provider}` → 204(없는 설정도 204) · 404 `PROVIDER_NOT_FOUND`  (`site.secret`, 암호화 키 없어도 된다)
 *   - `GET /api/admin/social-providers/history?provider&page&size` → 200 `{ items, total, page, size }`  (`site.read`) — 최신이 위, provider 생략 = 전체
 * 공개 서버는 캐시 없이 요청마다 DB 를 읽어 저장 직후부터 새 값을 쓴다.
 */
const PATH = {
    root: '/api/admin/social-providers',
    provider: (provider: SocialProviderCode) => `${PATH.root}/${encodeURIComponent(provider)}`,
    history: () => `${PATH.root}/history`,
};

export function useSocialProviderSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (): Promise<SocialProviderSettingsView> => client.get<SocialProviderSettingsView>(PATH.root),

        /** 등록·수정 — 시크릿은 이 호출의 인자로만 지나가고 어디에도 남기지 않는다 */
        save: (provider: SocialProviderCode, body: SocialProviderSaveRequest): Promise<SocialProviderSettingView> =>
            client.put<SocialProviderSettingView>(PATH.provider(provider), body),

        remove: (provider: SocialProviderCode): Promise<void> => client.del<void>(PATH.provider(provider)),

        history: (query: SocialSettingHistoryQuery): Promise<SocialSettingHistoryPage> =>
            client.get<SocialSettingHistoryPage>(PATH.history(), { query: { provider: query.provider || undefined, page: query.page, size: query.size } }),
    };
}
