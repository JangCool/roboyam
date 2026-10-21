import type { SocialProviderCode } from '~/composables/install/api/useInstallApi.types';


/**
 * 지금 어느 키가 쓰이는가 —
 *  - `SETTINGS` 설정 화면에 등록한 값(환경 변수보다 먼저 쓰인다)
 *  - `ENV` 환경 변수의 키(설정 화면에는 없다)
 *  - `NONE` 등록 안 됨
 */
export type SocialSource = 'SETTINGS' | 'ENV' | 'NONE';

/** 공급자 하나의 설정 — `providers` 의 원소이자 `PUT` 의 응답 */
export interface SocialProviderSettingView {
    provider: SocialProviderCode;
    /** 화면에 보일 이름("네이버") — 서버가 준 그대로 쓴다 */
    label: string;
    /** 각 사 개발자 콘솔에 등록할 콜백 주소 */
    callbackUrl: string;
    source: SocialSource;
    /** **지금 로그인 화면에 버튼이 나오는가**(등록돼 있고 켜져 있음). `SETTINGS` 이면서 false 면 "등록은 했지만 꺼 둠" */
    enabled: boolean;
    /** 공개값이라 보여 준다. 등록 안 됐으면 null */
    clientId: string | null;
    /** 시크릿이 등록돼 있는가 — 값은 주지 않는다 */
    secretSet: boolean;
    /** ISO-8601 — 설정 화면에서 저장한 시각(`ENV`·`NONE` 이면 null) */
    updatedAt: string | null;
}

/** `PUT /api/admin/social-providers/{provider}` 의 요청 */
export interface SocialProviderSaveRequest {
    clientId: string;
    /** 처음 등록할 때는 필수. 이미 `SETTINGS` 로 등록돼 있으면 **생략해 기존 값을 유지**한다 */
    clientSecret?: string;
    enabled: boolean;
}

/** 입력칸 이름 — 서버 오류의 `fields` 키와 같다 */
export type SocialField = 'clientId' | 'clientSecret';

/** 입력칸별 오류 문구 */
export type SocialFieldErrors = Partial<Record<SocialField, string>>;

/**
 * 소셜 로그인 키 입력의 순수 로직 — 입력 규칙·요청 만들기. **설치 마법사의 소셜 단계**가 쓴다(`util-install.ts`).
 * 입력 규칙은 docs/api-admin-social.md 의 `PUT` 요청 설명과 같다(서버가 거절할 것을 화면이 미리 알려 주는 용도이고 판정은 서버).
 * 화면에 보이는 문구는 `util-admin-social-messages.ts` 에 있다.
 *
 * 운영자 설정 화면(`/social-login`)은 관리자 앱(`admin/`)으로 옮겼다. 그쪽 `UtilAdminSocial` 은 같은 규칙에 상태 배지·응답 합치기를 더한 것이다.
 */

/** 카드의 입력값 — 시크릿은 서버가 주지 않으므로 늘 빈 칸에서 시작한다 */
export interface SocialForm {
    clientId: string;
    clientSecret: string;
    enabled: boolean;
}

/** 입력칸의 문제 — 비어 있음 · 너무 김 · 공백이 들어 있음 */
export type SocialProblem = 'REQUIRED' | 'LENGTH' | 'SPACE';
export type SocialProblems = Partial<Record<SocialField, SocialProblem>>;

/** 앞뒤 공백을 자른 값에 대해 비어 있음·길이·가운데 공백을 본다. 문제 없으면 null */
function problemOf(value: string, max: number, required: boolean): SocialProblem | null {
    const v = value.trim();
    if (!v) return required ? 'REQUIRED' : null;
    if (v.length > max) return 'LENGTH';
    if (/\s/.test(v)) return 'SPACE';
    return null;
}

export default class UtilAdminSocial {
    /** 클라이언트 ID 최대 글자 수(앞뒤 공백을 자른 뒤) */
    static readonly CLIENT_ID_MAX = 200;
    /** 클라이언트 시크릿 최대 글자 수(앞뒤 공백을 자른 뒤) */
    static readonly CLIENT_SECRET_MAX = 500;

    /**
     * 시크릿을 꼭 입력해야 하는가 — 처음 등록할 때(설정 화면 값이 아직 없을 때)다. 이미 등록돼 있으면 비워 두어 기존 값을 유지할 수 있다.
     * @param {SocialProviderSettingView} setting - 공급자 한 곳의 설정.
     * @returns {boolean} 필수이면 true.
     */
    static secretRequired(setting: SocialProviderSettingView): boolean {
        return setting.source !== 'SETTINGS';
    }

    /**
     * 카드가 처음 보여 줄 입력값. 설정 화면에 등록된 것만 채운다(환경 변수 값은 채우지 않는다 — 저장하면 그 값을 덮어쓰게 되므로).
     * 시크릿은 서버가 주지 않으니 늘 빈 칸이고, 켜짐은 처음 등록할 때 켜 둔 채 시작한다.
     * @param {SocialProviderSettingView} setting - 공급자 한 곳의 설정.
     * @returns {SocialForm} 입력값.
     */
    static initialForm(setting: SocialProviderSettingView): SocialForm {
        const registered = setting.source === 'SETTINGS';
        return { clientId: registered ? (setting.clientId ?? '') : '', clientSecret: '', enabled: registered ? setting.enabled : true };
    }

    /**
     * 저장 전에 입력을 미리 검사한다. 앞뒤 공백은 잘라서 본다(서버도 그렇게 한다).
     * @param {SocialForm} form - 입력값.
     * @param {SocialProviderSettingView} setting - 공급자 한 곳의 설정(시크릿이 필수인지 알려 준다).
     * @returns {SocialProblems} 문제가 있는 칸과 까닭. 비어 있으면 통과.
     */
    static validate(form: SocialForm, setting: SocialProviderSettingView): SocialProblems {
        const problems: SocialProblems = {};
        const id = problemOf(form.clientId, UtilAdminSocial.CLIENT_ID_MAX, true);
        if (id) problems.clientId = id;
        const secret = problemOf(form.clientSecret, UtilAdminSocial.CLIENT_SECRET_MAX, UtilAdminSocial.secretRequired(setting));
        if (secret) problems.clientSecret = secret;
        return problems;
    }

    /**
     * 서버로 보낼 요청. 시크릿을 비워 두었으면 **빼서 보낸다**(기존 값 유지).
     * @param {SocialForm} form - 입력값.
     * @returns {SocialProviderSaveRequest} 앞뒤 공백을 자른 요청 본문.
     */
    static toRequest(form: SocialForm): SocialProviderSaveRequest {
        const clientId = form.clientId.trim();
        const clientSecret = form.clientSecret.trim();
        return clientSecret ? { clientId, clientSecret, enabled: form.enabled } : { clientId, enabled: form.enabled };
    }
}
