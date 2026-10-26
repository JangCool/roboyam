import type { PortoneIdentityFieldKey, PortoneIdentitySaveRequest, PortoneSettingsView } from '~/composables/portone/api/usePortoneSettingsApi.types';

/**
 * 포트원 설정 API — 서버 주소 문자열(`/api/admin/portone-settings…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-foundation-contract.md` 「0-4」(서버 `PortOneSettingsController` — `SecretEncryptor` 암호화, 소셜 로그인 설정과 같은 방식). 응답은 `no-store`.
 *
 *  - `GET /api/admin/portone-settings` → `{ identityProvider, cryptoKeyConfigured, identityReady, fields: [{ key, configured, updatedAt, updatedBy }] }`  (`site.read` — 값은 오지 않는다)
 *  - `PUT /api/admin/portone-settings/identity` `{ storeId?, apiSecret?, identityChannelKey? }` → 같은 모양  (`site.secret`, 보낸 칸만 바꾼다, 감사)
 *      · 오류: `400 VALIDATION`(`fields.storeId|apiSecret|identityChannelKey` — 빈 값·500자 초과, 아무 칸도 없으면 칸 없이) · `503 CRYPTO_KEY_MISSING`
 *  - `DELETE /api/admin/portone-settings/identity/{key}` → 204  (`site.secret`, 암호화 키 없어도 된다) · `404 PORTONE_FIELD_NOT_FOUND`(모르는 칸·이미 비어 있는 칸)
 *      · 지운 칸은 `configured:false` 가 되고 바꾼 시각·사람은 남는다.
 * 2a 단계에 결제 칸이 `…/payment` 로 더해질 자리다.
 */
const PATH = {
    root: '/api/admin/portone-settings',
    identity: () => `${PATH.root}/identity`,
    identityField: (key: PortoneIdentityFieldKey) => `${PATH.root}/identity/${encodeURIComponent(key)}`,
};

export function usePortoneSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        get: (): Promise<PortoneSettingsView> => client.get<PortoneSettingsView>(PATH.root),

        /** 본인인증 칸 저장 — 값은 이 호출의 인자로만 지나가고 어디에도 남기지 않는다 */
        saveIdentity: (body: PortoneIdentitySaveRequest): Promise<PortoneSettingsView> => client.put<PortoneSettingsView>(PATH.identity(), body),

        removeIdentityField: (key: PortoneIdentityFieldKey): Promise<void> => client.del<void>(PATH.identityField(key)),
    };
}
