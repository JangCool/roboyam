/**
 * 포트원 설정 API 의 요청·응답 모양(계약 「0-4」 — 서버 `PortOneSettingsView`·`PortOneFieldStateView`·`PortOneIdentitySaveRequest`, 2a 결정 14 로 묶음이 셋이 된다).
 * 값(상점 ID·API 비밀키·채널 키·웹훅 비밀키)은 **절대 내려오지 않는다** — 설정됐는지와 마지막 변경 시각·변경한 사람만. 서버는 `SecretEncryptor` 로 암호화해 DB 에 둔다(소셜 로그인 설정과 같다).
 * 칸 묶음: 공통(상점 ID·API 비밀키) · 본인인증(본인인증 채널 키) · 결제(결제 채널 키·웹훅 비밀키 — 2a). 키 문자열은 서버 `PortOneSettingKey` 와 같다.
 * 결제 칸(`paymentGateway`·`paymentReady`·결제 두 칸의 상태)은 2a-3 서버가 2a-1 화면이 정한 모양 그대로 준다.
 */

/** 지금 쓰는 본인인증 구현 — 서버 배포 설정(`roboyam.identity.provider`)이 정하고 화면에서 바꿀 수 없다. `FAKE` 는 local·시험 전용(dev·prod 에서는 서버가 뜨지 않는다) */
export type PortoneIdentityProvider = 'PORTONE' | 'FAKE';
/** 지금 쓰는 결제 게이트웨이 — 배포 설정 `roboyam.payment.gateway`(공통 portone, local fake). `FAKE` 면 dev·prod 에서 서버가 뜨지 않는다(2a 결정 1) */
export type PortonePaymentGateway = 'PORTONE' | 'FAKE';

/** 공통 카드의 칸 — 상점 ID · API 비밀키(V2 API Secret). 본인인증·결제가 같이 쓴다 */
export type PortoneCommonFieldKey = 'storeId' | 'apiSecret';
/** 본인인증 카드의 칸 */
export type PortoneIdentityFieldKey = 'identityChannelKey';
/** 결제 카드의 칸 — 결제 채널 키(통합 결제창 하나, 2a 결정 15) · 웹훅 비밀키(`whsec_…`, Standard Webhooks 서명 검증) */
export type PortonePaymentFieldKey = 'paymentChannelKey' | 'webhookSecret';
export type PortoneFieldKey = PortoneCommonFieldKey | PortoneIdentityFieldKey | PortonePaymentFieldKey;
/** 카드(서버 저장 묶음) — `PUT /api/admin/portone-settings/{group}` */
export type PortoneFieldGroup = 'common' | 'identity' | 'payment';

/** 칸 하나의 상태 — 값은 없다 */
export interface PortoneFieldState {
    key: PortoneFieldKey;
    configured: boolean;
    /** 마지막으로 저장·지운 순간. 한 번도 없으면 null */
    updatedAt: string | null;
    /** 마지막으로 바꾼 운영자 이름 */
    updatedBy: string | null;
}

/** `GET /api/admin/portone-settings` */
export interface PortoneSettingsView {
    identityProvider: PortoneIdentityProvider;
    paymentGateway: PortonePaymentGateway;
    /** false 면 서버에 암호화 키(`ROBOYAM_CRYPTO_KEY`)가 없어 저장할 수 없다(지우기는 된다) */
    cryptoKeyConfigured: boolean;
    fields: PortoneFieldState[];
    /** 포트원 본인인증을 쓸 수 있는가 — 상점 ID · API 비밀키 · 본인인증 채널 키 */
    identityReady: boolean;
    /** 포트원 결제를 쓸 수 있는가 — 상점 ID · API 비밀키 · 결제 채널 키 · 웹훅 비밀키 + 암호화 키 */
    paymentReady: boolean;
}

/** 저장 — 보낸 칸만 바꾼다(빈 칸은 보내지 않는다) */
export type PortoneCommonSaveRequest = Partial<Record<PortoneCommonFieldKey, string>>;
export type PortoneIdentitySaveRequest = Partial<Record<PortoneIdentityFieldKey, string>>;
export type PortonePaymentSaveRequest = Partial<Record<PortonePaymentFieldKey, string>>;

/** 카드마다 칸 차례 */
export const PORTONE_COMMON_FIELDS: readonly PortoneCommonFieldKey[] = ['storeId', 'apiSecret'];
export const PORTONE_IDENTITY_FIELDS: readonly PortoneIdentityFieldKey[] = ['identityChannelKey'];
export const PORTONE_PAYMENT_FIELDS: readonly PortonePaymentFieldKey[] = ['paymentChannelKey', 'webhookSecret'];
export const PORTONE_FIELDS_BY_GROUP: Readonly<Record<PortoneFieldGroup, readonly PortoneFieldKey[]>> = {
    common: PORTONE_COMMON_FIELDS,
    identity: PORTONE_IDENTITY_FIELDS,
    payment: PORTONE_PAYMENT_FIELDS,
};
