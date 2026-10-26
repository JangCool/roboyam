// MOCK: 포트원 설정의 **결제 칸**(결제 채널 키 · 웹훅 비밀키)과 `paymentGateway`·`paymentReady` 의 메모리 목업 — 2a-1 화면 목업.
// 공통(상점 ID·API 비밀키)·본인인증 칸은 실제 서버(0-4)를 그대로 부른다. 서버가 결제 칸을 주면(2a-3) 이 폴더(`portone/api/mock/`)째 지운다.
// 메모리 상태는 새로고침하면 처음으로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   payment-portone   결제 게이트웨이를 포트원으로 보인다(기본은 본인인증 구현과 같게 — local 은 가짜)
//   payment-fake      결제 게이트웨이를 가짜로 보인다
//   payment-set       결제 두 칸이 처음부터 설정돼 있다
//   payment-save-fail 결제 칸 저장이 500

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { PortoneFieldState, PortonePaymentFieldKey, PortonePaymentSaveRequest, PortoneSettingsView } from '~/composables/portone/api/usePortoneSettingsApi.types';
import { PORTONE_PAYMENT_FIELDS } from '~/composables/portone/api/usePortoneSettingsApi.types';

type ServerView = Omit<PortoneSettingsView, 'paymentGateway' | 'paymentReady'> & Partial<Pick<PortoneSettingsView, 'paymentGateway' | 'paymentReady'>>;

let state: Record<PortonePaymentFieldKey, PortoneFieldState> | null = null;

function fields(): Record<PortonePaymentFieldKey, PortoneFieldState> {
    if (state) return state;
    const preset = mockFlags().has('payment-set');
    const at = preset ? new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString() : null;
    state = {
        paymentChannelKey: { key: 'paymentChannelKey', configured: preset, updatedAt: at, updatedBy: preset ? '김운영' : null },
        webhookSecret: { key: 'webhookSecret', configured: preset, updatedAt: at, updatedBy: preset ? '김운영' : null },
    };
    return state;
}

/** 실제 서버가 준 모양(공통·본인인증 칸)에 결제 칸·게이트웨이·준비 여부를 덧붙인다 */
export function mockWithPayment(view: ServerView): PortoneSettingsView {
    const flags = mockFlags();
    const payment = fields();
    const others = view.fields.filter((f) => !(PORTONE_PAYMENT_FIELDS as readonly string[]).includes(f.key));
    const configured = (key: string) => others.some((f) => f.key === key && f.configured);
    const gateway = flags.has('payment-portone') ? 'PORTONE' : flags.has('payment-fake') ? 'FAKE' : view.identityProvider;
    return {
        ...view,
        paymentGateway: gateway,
        fields: [...others, ...PORTONE_PAYMENT_FIELDS.map((key) => ({ ...payment[key] }))],
        paymentReady: view.cryptoKeyConfigured && configured('storeId') && configured('apiSecret') && PORTONE_PAYMENT_FIELDS.every((key) => payment[key].configured),
    };
}

/** `PUT /api/admin/portone-settings/payment` 흉내 — 칸 검증은 서버(0-4)와 같다(빈 값·500자 초과·공백). 웹훅 비밀키는 `whsec_` 로 시작해야 한다 */
export async function mockSavePayment(body: PortonePaymentSaveRequest, cryptoKeyConfigured: boolean, operator: string): Promise<void> {
    await mockDelay();
    if (mockFlags().has('payment-save-fail')) throw MOCK_ERRORS.internal();
    if (!cryptoKeyConfigured) throw new ApiError({ status: 503, code: 'CRYPTO_KEY_MISSING', message: '서버에 암호화 키가 없어 저장할 수 없어요.' });
    const problems: Record<string, string> = {};
    for (const [key, value] of Object.entries(body)) {
        if (!value) problems[key] = '값을 입력해 주세요.';
        else if (value.length > 500) problems[key] = '500자까지 넣을 수 있어요.';
        else if (key === 'webhookSecret' && !value.startsWith('whsec_')) problems[key] = '웹훅 비밀키는 whsec_ 로 시작해요.';
    }
    if (Object.keys(problems).length) throw MOCK_ERRORS.validation(problems);
    const now = new Date().toISOString();
    for (const key of Object.keys(body) as PortonePaymentFieldKey[]) fields()[key] = { key, configured: true, updatedAt: now, updatedBy: operator };
}

/** `DELETE /api/admin/portone-settings/payment/{key}` 흉내 */
export async function mockRemovePayment(key: PortonePaymentFieldKey, operator: string): Promise<void> {
    await mockDelay();
    const field = fields()[key];
    if (!field?.configured) throw new ApiError({ status: 404, code: 'PORTONE_FIELD_NOT_FOUND', message: '이미 비어 있는 칸이에요.' });
    fields()[key] = { key, configured: false, updatedAt: new Date().toISOString(), updatedBy: operator };
}
