/**
 * 소셜 로그인 공급자 설정 API 가 주고받는 모양 — 서버 `com.community.social.dto.*`(server/admin) 와 같은 이름이다.
 * 계약: `docs/features/login-contract.md` 「관리자 소셜 로그인 설정」. ⚠ 시크릿 값은 어떤 응답에도 없다(`secretSet` 만).
 */
import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';

/** 공급자 코드 — 목록 순서는 서버가 정한다(카카오 · 네이버 · 구글) */
export type SocialProviderCode = 'KAKAO' | 'NAVER' | 'GOOGLE';

/** 공급자 한 곳의 설정 — 목록의 원소이자 `PUT` 의 응답 (`SocialProviderSettingView`) */
export interface SocialProviderSettingView {
    provider: SocialProviderCode;
    /** 소문자 이름(`kakao`) — 공개 `GET /api/auth/providers` 의 id */
    id: string;
    /** 요청 언어의 이름(카카오/Kakao) */
    label: string;
    /** 공급자 콘솔에 등록할 콜백 주소. 사이트 공개 주소가 없으면 null */
    callbackUrl: string | null;
    /** `SETTINGS`(등록됨) · `NONE`(미등록). roboyam 에는 `ENV` 가 없다 */
    source: 'SETTINGS' | 'NONE';
    /** 저장된 켜짐 값(미등록이면 false) */
    enabled: boolean;
    /** 지금 공개 로그인 화면에 버튼이 나오는가(켜짐 + 키 + 서버 암호화 키) */
    active: boolean;
    clientId: string | null;
    /** 시크릿이 있고 지금 서버 키로 풀 수 있는가 — false 면 저장할 때 시크릿을 다시 넣어야 한다 */
    secretSet: boolean;
    /** 클라이언트 ID + 풀 수 있는 시크릿이 모두 있는가 */
    configured: boolean;
    /** 마지막 저장 시각(UTC ISO). 미등록이면 null */
    updatedAt: string | null;
    /** 마지막으로 저장한 운영자 이름. 미등록·지워진 운영자면 null */
    updatedBy: string | null;
}

/** `GET /api/admin/social-providers` (`SocialProviderSettingsView`) */
export interface SocialProviderSettingsView {
    /** false 면 서버에 암호화 키가 없어 저장할 수 없다(`PUT` 이 503 `CRYPTO_KEY_MISSING`) */
    cryptoKeyConfigured: boolean;
    /** false 면 사이트 공개 주소가 없어 콜백 주소를 만들 수 없고 켜기를 거절한다(`fields.enabled`) */
    publicUrlConfigured: boolean;
    providers: SocialProviderSettingView[];
}

/** `PUT /api/admin/social-providers/{provider}` (`SocialProviderSaveRequest`) — 시크릿을 빼면 저장된 값을 그대로 쓴다 */
export interface SocialProviderSaveRequest {
    clientId: string;
    clientSecret?: string;
    enabled: boolean;
}

/** 이력 한 줄(`SocialSettingHistoryView`) — 공급자 칸이 늘 있다 */
export interface SocialSettingHistoryView extends SettingHistoryItem {
    provider: SocialProviderCode;
    providerLabel: string;
}

export type SocialSettingHistoryPage = SettingHistoryPage<SocialSettingHistoryView>;

/** 이력 조회 조건 — 공급자를 비우면 모두 */
export interface SocialSettingHistoryQuery {
    provider?: SocialProviderCode | null;
    page: number;
    size: number;
}
