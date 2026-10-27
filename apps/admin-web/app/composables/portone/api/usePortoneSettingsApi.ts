import { mockRemovePayment, mockSavePayment, mockWithPayment } from '~/composables/portone/api/mock/portone-payment-mock';
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
 *      · 오류: `400 VALIDATION`(`fields.<칸>` — 빈 값·500자 초과, 웹훅 비밀키는 `whsec_` 형식) · `503 CRYPTO_KEY_MISSING`
 *  - `DELETE /api/admin/portone-settings/{group}/{key}` → 204  (`site.secret`, 암호화 키 없어도 된다) · `404 PORTONE_FIELD_NOT_FOUND`
 *      · 지운 칸은 `configured:false` 가 되고 바꾼 시각·사람은 남는다.
 *
 * ⚠ MOCK(2a-1): 지금 서버(0-4)는 `…/identity` 하나가 공통 두 칸과 본인인증 칸을 다 받고, 결제 칸·`paymentGateway`·`paymentReady` 가 없다.
 *   그래서 공통 카드는 `…/identity` 로 보내고(실제 서버 — 본인인증 화면은 깨지지 않는다), 결제 카드만 `./mock/portone-payment-mock.ts` 를 부른다.
 *   2a-3 이 서버를 나누면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/portone-settings',
    group: (group: 'common' | 'identity' | 'payment') => `${PATH.root}/${group}`,
    field: (group: 'common' | 'identity' | 'payment', key: string) => `${PATH.root}/${group}/${encodeURIComponent(key)}`,
};

export function usePortoneSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 결제 칸의 「바꾼 사람」 — 서버가 붙으면 지운다
    const { user } = useAdminAuth();
    const operator = () => user.value?.nickname ?? '—';

    /** MOCK: 서버 응답에 결제 칸을 덧붙인다 */
    const view = async (request: Promise<PortoneSettingsView>): Promise<PortoneSettingsView> => mockWithPayment(await request);

    return {
        get: (): Promise<PortoneSettingsView> =>
            // MOCK:
            view(client.get<PortoneSettingsView>(PATH.root)),
        // REAL: client.get<PortoneSettingsView>(PATH.root),

        /** 공통 칸(상점 ID·API 비밀키) 저장 — 값은 이 호출의 인자로만 지나가고 어디에도 남기지 않는다 */
        saveCommon: (body: PortoneCommonSaveRequest): Promise<PortoneSettingsView> =>
            // MOCK: 지금 서버는 `…/identity` 가 공통 칸도 받는다
            view(client.put<PortoneSettingsView>(PATH.group('identity'), body)),
        // REAL: client.put<PortoneSettingsView>(PATH.group('common'), body),

        removeCommonField: (key: PortoneCommonFieldKey): Promise<void> =>
            // MOCK:
            client.del<void>(PATH.field('identity', key)),
        // REAL: client.del<void>(PATH.field('common', key)),

        /** 본인인증 칸(본인인증 채널 키) 저장 */
        saveIdentity: (body: PortoneIdentitySaveRequest): Promise<PortoneSettingsView> =>
            // MOCK:
            view(client.put<PortoneSettingsView>(PATH.group('identity'), body)),
        // REAL: client.put<PortoneSettingsView>(PATH.group('identity'), body),

        removeIdentityField: (key: PortoneIdentityFieldKey): Promise<void> => client.del<void>(PATH.field('identity', key)),

        /** 결제 칸(결제 채널 키·웹훅 비밀키) 저장 */
        savePayment: async (body: PortonePaymentSaveRequest): Promise<PortoneSettingsView> => {
            // MOCK: 암호화 키 여부는 실제 서버 값을 본다
            const current = await client.get<PortoneSettingsView>(PATH.root);
            await mockSavePayment(body, current.cryptoKeyConfigured, operator());
            return view(client.get<PortoneSettingsView>(PATH.root));
            // REAL: return client.put<PortoneSettingsView>(PATH.group('payment'), body);
        },

        removePaymentField: (key: PortonePaymentFieldKey): Promise<void> =>
            // MOCK:
            mockRemovePayment(key, operator()),
        // REAL: client.del<void>(PATH.field('payment', key)),
    };
}
