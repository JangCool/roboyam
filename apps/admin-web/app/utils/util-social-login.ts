import type { SocialProviderSaveRequest, SocialProviderSettingView } from '~/composables/social/api/useSocialProviderSettingsApi.types';

/** 카드의 입력칸 이름 — 서버 오류 `fields` 키와 같다 */
export type SocialField = 'clientId' | 'clientSecret' | 'enabled';

/** 칸별 오류 문구 */
export type SocialFieldErrors = Partial<Record<SocialField, string>>;

/** 카드의 입력값 — 시크릿은 서버가 주지 않으므로 늘 빈 칸에서 시작한다 */
export interface SocialForm {
    clientId: string;
    clientSecret: string;
    enabled: boolean;
}

/** 입력칸의 문제 — 비어 있음 · 너무 김 · 공백/제어 문자 */
export type SocialProblem = 'REQUIRED' | 'LENGTH' | 'SPACE';
export type SocialProblems = Partial<Record<'clientId' | 'clientSecret', SocialProblem>>;

/** 상태 딱지의 갈래 — 켜짐(버튼 보임) · 켜 뒀지만 안 보임 · 꺼 둠 · 미등록 */
export type SocialBadgeTone = 'active' | 'inactive' | 'off' | 'none';

/** 공백·제어 문자 — 서버 `SocialSettingsService` 의 검사(공백·제어 문자 없음)와 같다 */
const SPACE_OR_CONTROL = /[\s\p{Cc}]/u;

/** 앞뒤 공백을 자른 값으로 비어 있음·길이·공백을 본다. 문제 없으면 null */
function problemOf(value: string, max: number, required: boolean): SocialProblem | null {
    const v = value.trim();
    if (!v) return required ? 'REQUIRED' : null;
    if (v.length > max) return 'LENGTH';
    if (SPACE_OR_CONTROL.test(v)) return 'SPACE';
    return null;
}

/**
 * 소셜 로그인 설정 화면(`/settings/social-login`)의 순수 로직 — 상태 딱지·입력 규칙·요청 만들기. community `util-admin-social.ts` 를 roboyam 계약에 맞춘 것이다.
 * 입력 규칙은 서버 `SocialSettingsService`(shared `social/service` — 설치 마법사와 같은 검사) + `SocialProviderAdminService`(시크릿 유지 규칙)와 같다 — 화면 검사는 미리 알려 주는 용도이고 판정은 서버다.
 *  · clientId 앞뒤 공백을 자른 뒤 1~200자, 공백·제어 문자 없음(필수)
 *  · clientSecret 1~500자, 공백·제어 문자 없음. **필수 판정은 `!secretSet`** — 저장된 것이 없거나 지금 키로 풀 수 없으면 다시 넣어야 한다. 비우면 저장된 값을 그대로 쓴다
 * 문구는 `util-social-login-messages.ts` 에 있다.
 */
export default class UtilSocialLogin {
    static readonly CLIENT_ID_MAX = 200;
    static readonly CLIENT_SECRET_MAX = 500;

    /**
     * 카드 머리의 상태 딱지 갈래.
     * @param setting - 공급자 한 곳의 설정.
     * @returns 미등록 · 꺼 둠 · 켜 뒀지만 지금 버튼이 안 보임 · 켜짐(버튼 보임).
     */
    static badge(setting: SocialProviderSettingView): SocialBadgeTone {
        if (setting.source === 'NONE') return 'none';
        if (!setting.enabled) return 'off';
        return setting.active ? 'active' : 'inactive';
    }

    /**
     * 시크릿을 꼭 입력해야 하는가 — 저장된 시크릿이 없거나 지금 서버 키로 풀 수 없을 때(`secretSet` false).
     * @param setting - 공급자 한 곳의 설정.
     * @returns 필수이면 true.
     */
    static secretRequired(setting: SocialProviderSettingView): boolean {
        return !setting.secretSet;
    }

    /**
     * 카드가 처음 보여 줄 입력값. 시크릿은 늘 빈 칸이고, 처음 등록할 때는 켜 둔 채 시작한다.
     * @param setting - 공급자 한 곳의 설정.
     * @returns 입력값.
     */
    static initialForm(setting: SocialProviderSettingView): SocialForm {
        const registered = setting.source === 'SETTINGS';
        return { clientId: registered ? (setting.clientId ?? '') : '', clientSecret: '', enabled: registered ? setting.enabled : true };
    }

    /**
     * 서버 값과 다른 칸 수 — 구역 내비의 바뀜 표시와 [저장]·[되돌리기]를 켠다. 시크릿은 칸이 비어 있지 않으면 바꾸려는 것이다.
     * @param form - 지금 입력값.
     * @param setting - 공급자 한 곳의 설정.
     * @returns 바뀐 칸 수(0~3).
     */
    static changes(form: SocialForm, setting: SocialProviderSettingView): number {
        const base = UtilSocialLogin.initialForm(setting);
        return Number(form.clientId !== base.clientId) + Number(form.clientSecret !== '') + Number(form.enabled !== base.enabled);
    }

    /**
     * 저장 전에 입력을 미리 검사한다(앞뒤 공백은 잘라서 본다 — 서버도 그렇게 한다).
     * @param form - 입력값.
     * @param setting - 공급자 한 곳의 설정(시크릿 필수 여부).
     * @returns 문제가 있는 칸과 까닭. 비어 있으면 통과.
     */
    static validate(form: SocialForm, setting: SocialProviderSettingView): SocialProblems {
        const problems: SocialProblems = {};
        const id = problemOf(form.clientId, UtilSocialLogin.CLIENT_ID_MAX, true);
        if (id) problems.clientId = id;
        const secret = problemOf(form.clientSecret, UtilSocialLogin.CLIENT_SECRET_MAX, UtilSocialLogin.secretRequired(setting));
        if (secret) problems.clientSecret = secret;
        return problems;
    }

    /**
     * 서버로 보낼 요청. 시크릿을 비워 두었으면 **빼서 보낸다**(저장된 값 유지).
     * @param form - 입력값.
     * @returns 앞뒤 공백을 자른 요청 본문.
     */
    static toRequest(form: SocialForm): SocialProviderSaveRequest {
        const clientId = form.clientId.trim();
        const clientSecret = form.clientSecret.trim();
        return clientSecret ? { clientId, clientSecret, enabled: form.enabled } : { clientId, enabled: form.enabled };
    }
}
