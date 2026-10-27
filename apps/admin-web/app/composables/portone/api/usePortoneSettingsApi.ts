import type {
    PortoneCommonFieldKey,
    PortoneCommonSaveRequest,
    PortoneIdentityFieldKey,
    PortoneIdentitySaveRequest,
    PortonePaymentFieldKey,
    PortonePaymentSaveRequest,
    PortoneSettingsView,
} from '~/composables/portone/api/usePortoneSettingsApi.types';

/**
 * 포트원 설정 API — 서버 주소 문자열(`/api/admin/portone-settings…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-foundation-contract.md` 「0-4」 + `commerce-payment-contract.md` 결정 14(서버 `PortOneSettingsController` — `SecretEncryptor` 암호화). 응답은 `no-store`.
 *
 *  - `GET /api/admin/portone-settings` → `PortoneSettingsView`  (`site.read` — 값은 오지 않는다)
 *  - `PUT /api/admin/portone-settings/{common|identity|payment}` 보낸 칸만 → 같은 모양  (`site.secret`, 감사 — 칸 이름만)
 *      · 공통 = 상점 ID·API 비밀키, 본인인증 = 본인인증 채널 키, 결제 = 결제 채널 키·웹훅 비밀키
 *      · 오류: `400 VALIDATION`(`fields.<칸>` — 빈 값·500자 초과, 웹훅 비밀키는 `whsec_` 형식) · `503 CRYPTO_KEY_MISSING`
 *  - `DELETE /api/admin/portone-settings/{group}/{key}` → 204  (`site.secret`, 암호화 키 없어도 된다) · `404 PORTONE_FIELD_NOT_FOUND`
 *      · 지운 칸은 `configured:false` 가 되고 바꾼 시각·사람은 남는다.
 */
const PATH = {
    root: '/api/admin/portone-settings',
    group: (group: 'common' | 'identity' | 'payment') => `${PATH.root}/${group}`,
    field: (group: 'common' | 'identity' | 'payment', key: string) => `${PATH.root}/${group}/${encodeURIComponent(key)}`,
};

export function usePortoneSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        get: (): Promise<PortoneSettingsView> => client.get<PortoneSettingsView>(PATH.root),

        /** 공통 칸(상점 ID·API 비밀키) 저장 — 값은 이 호출의 인자로만 지나가고 어디에도 남기지 않는다 */
        saveCommon: (body: PortoneCommonSaveRequest): Promise<PortoneSettingsView> => client.put<PortoneSettingsView>(PATH.group('common'), body),

        removeCommonField: (key: PortoneCommonFieldKey): Promise<void> => client.del<void>(PATH.field('common', key)),

        /** 본인인증 칸(본인인증 채널 키) 저장 */
        saveIdentity: (body: PortoneIdentitySaveRequest): Promise<PortoneSettingsView> => client.put<PortoneSettingsView>(PATH.group('identity'), body),

        removeIdentityField: (key: PortoneIdentityFieldKey): Promise<void> => client.del<void>(PATH.field('identity', key)),

        /** 결제 칸(결제 채널 키·웹훅 비밀키) 저장 */
        savePayment: (body: PortonePaymentSaveRequest): Promise<PortoneSettingsView> => client.put<PortoneSettingsView>(PATH.group('payment'), body),

        removePaymentField: (key: PortonePaymentFieldKey): Promise<void> => client.del<void>(PATH.field('payment', key)),
    };
}
