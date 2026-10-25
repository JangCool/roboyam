/**
 * 포트원 설정 API 의 요청·응답 모양(계약 「0-4」 — 서버 `PortOneSettingsView`·`PortOneFieldStateView`·`PortOneIdentitySaveRequest`).
 * 값(상점 ID·API 비밀키·채널 키)은 **절대 내려오지 않는다** — 설정됐는지와 마지막 변경 시각·변경한 사람만. 서버는 `SecretEncryptor` 로 암호화해 DB 에 둔다(소셜 로그인 설정과 같다).
 * 0단계는 본인인증 칸만, 2a 단계에 결제 칸(결제 채널 키·웹훅 시크릿 등)이 다른 카드로 더해진다.
 */

/** 지금 쓰는 본인인증 구현 — 서버 배포 설정(`roboyam.identity.provider`)이 정하고 화면에서 바꿀 수 없다. `FAKE` 는 local·시험 전용(dev·prod 에서는 서버가 뜨지 않는다) */
export type PortoneIdentityProvider = 'PORTONE' | 'FAKE';

/** 본인인증 카드의 칸 — 상점 ID · API 비밀키(V2 API Secret) · 본인인증 채널 키 */
export type PortoneIdentityFieldKey = 'storeId' | 'apiSecret' | 'identityChannelKey';

/** 칸 하나의 상태 — 값은 없다 */
export interface PortoneFieldState {
    key: PortoneIdentityFieldKey;
    configured: boolean;
    /** 마지막으로 저장·지운 순간. 한 번도 없으면 null */
    updatedAt: string | null;
    /** 마지막으로 바꾼 운영자 이름 */
    updatedBy: string | null;
}

/** `GET /api/admin/portone-settings` */
export interface PortoneSettingsView {
    identityProvider: PortoneIdentityProvider;
    /** false 면 서버에 암호화 키(`ROBOYAM_CRYPTO_KEY`)가 없어 저장할 수 없다(지우기는 된다) */
    cryptoKeyConfigured: boolean;
    fields: PortoneFieldState[];
    /** 포트원 본인인증을 쓸 수 있는가 — 세 칸이 모두 설정됐다 */
    identityReady: boolean;
}

/** `PUT /api/admin/portone-settings/identity` — 보낸 칸만 바꾼다(빈 칸은 보내지 않는다) */
export type PortoneIdentitySaveRequest = Partial<Record<PortoneIdentityFieldKey, string>>;

/** 본인인증 카드의 칸 차례 */
export const PORTONE_IDENTITY_FIELDS: readonly PortoneIdentityFieldKey[] = ['storeId', 'apiSecret', 'identityChannelKey'];
