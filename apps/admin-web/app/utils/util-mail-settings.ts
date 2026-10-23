import type { MailSecurity, MailSettingsSaveRequest, MailSettingsView } from '~/composables/mail/api/useMailSettingsApi.types';
import UtilValidation from '~/utils/util-validation';

/** 메일 서버 입력칸 이름 — 서버 오류 `fields` 키와 같다 */
export type MailSettingsField = 'host' | 'port' | 'security' | 'username' | 'password' | 'fromAddress' | 'senderName' | 'subjectPrefix';

/** 칸별 오류 문구 */
export type MailSettingsFieldErrors = Partial<Record<MailSettingsField, string>>;

/** 메일 서버 입력값 — 포트는 입력 중에 글자일 수 있어 문자열로 든다. 비밀번호는 늘 빈 칸에서 시작한다(서버가 주지 않는다) */
export interface MailSettingsForm {
    host: string;
    port: string;
    security: MailSecurity;
    username: string;
    password: string;
    fromAddress: string;
    senderName: string;
    subjectPrefix: string;
}

/**
 * 입력칸의 문제 — 비어 있음 · 너무 김 · 형식(공백·이메일) · 범위 밖 · 제어 문자 · 줄바꿈 ·
 * 짝이 아님(비밀번호만 있음) · 다시 입력(저장된 비밀번호를 쓸 수 없는 변경)
 */
export type MailSettingsProblem = 'REQUIRED' | 'LENGTH' | 'FORMAT' | 'RANGE' | 'CONTROL' | 'NEWLINE' | 'PAIR' | 'REENTER';
export type MailSettingsProblems = Partial<Record<MailSettingsField, MailSettingsProblem>>;

/** 보안 방식별 보통 쓰는 포트 */
const DEFAULT_PORTS: Record<MailSecurity, string> = { NONE: '25', STARTTLS: '587', SSL: '465' };

/** 공백(유니코드 공백 포함)·제어 문자 — 서버 `MailSettingsService.normalize` 의 서버 주소 검사와 같다 */
const SPACE_OR_CONTROL = /[\s\p{Zs}\p{Cc}]/u;
const CONTROL = /\p{Cc}/u;
const NEWLINE = /[\r\n]/;

/**
 * 메일 서버 설정 화면(`/settings/mail`)의 순수 로직 — 입력값·입력 규칙·바뀐 칸·요청 만들기.
 * 입력 규칙은 서버 `MailSettingsService.normalize`(shared — 설치 마법사와 같은 검사)와 `MailSettingsAdminService.save`(server/admin — 비밀번호 유지 규칙)를 따른다.
 * 화면 검사는 미리 알려 주는 용도이고 판정은 서버다:
 *  · 서버 주소 1~255자, 공백·제어 문자 없음 · 포트 1~65535 · 보내는 사람 이메일 형식(필수) · 보안 방식 `NONE`·`STARTTLS`·`SSL`
 *  · 사용자 이름 0~255자(제어 문자 없음), 비밀번호 0~500자(제어 문자 없음). 비밀번호만 있고 사용자 이름이 없으면 `fields.username`
 *  · 사용자 이름이 있는데 비밀번호가 비어 있으면 — 저장된 비밀번호가 있고 **사용자 이름·서버 주소(대소문자 무시)·보안 방식이 그대로일 때만** 유지, 아니면 `fields.password`(다시 입력)
 *  · 보내는 이름 1~50자 · 제목 머리말 1~30자, 둘 다 줄바꿈 없음(선택 — 비우면 null)
 * 문구는 `util-mail-settings-messages.ts` 에 있다.
 */
export default class UtilMailSettings {
    /** 보안 방식 선택지 — 화면에 보이는 순서 */
    static readonly SECURITIES: readonly MailSecurity[] = ['NONE', 'STARTTLS', 'SSL'];
    static readonly HOST_MAX = 255;
    static readonly PORT_MIN = 1;
    static readonly PORT_MAX = 65535;
    static readonly USERNAME_MAX = 255;
    static readonly PASSWORD_MAX = 500;
    static readonly SENDER_NAME_MAX = 50;
    static readonly SUBJECT_PREFIX_MAX = 30;

    /**
     * 서버 값으로 만든 처음 입력값. 설정이 없으면(설치 때 건너뜀) 빈 칸 + STARTTLS·587(요즘 메일 서버가 가장 많이 쓰는 방식).
     * @param view - 서버가 준 설정.
     * @returns 입력값(비밀번호는 늘 빈 칸).
     */
    static initialForm(view: MailSettingsView): MailSettingsForm {
        if (!view.configured) {
            return { host: '', port: DEFAULT_PORTS.STARTTLS, security: 'STARTTLS', username: '', password: '', fromAddress: '', senderName: '', subjectPrefix: '' };
        }
        const security = view.security ?? 'NONE';
        return {
            host: view.host ?? '',
            port: view.port == null ? DEFAULT_PORTS[security] : String(view.port),
            security,
            username: view.username ?? '',
            password: '',
            fromAddress: view.fromAddress ?? '',
            senderName: view.senderName ?? '',
            subjectPrefix: view.subjectPrefix ?? '',
        };
    }

    /**
     * 보안 방식을 바꿀 때의 포트 — 비어 있거나 다른 방식의 기본 포트 그대로면 새 방식의 기본 포트로, 직접 고친 포트는 그대로(user-web 설치 마법사와 같다).
     * @param currentPort - 지금 포트 입력값.
     * @param next - 새 보안 방식.
     * @returns 새 포트 입력값.
     */
    static portAfterSecurityChange(currentPort: string, next: MailSecurity): string {
        const port = currentPort.trim();
        return !port || Object.values(DEFAULT_PORTS).includes(port) ? DEFAULT_PORTS[next] : currentPort;
    }

    /**
     * 서버 값과 다른 칸 수 — 비밀번호는 칸이 비어 있지 않으면 바꾸려는 것이다. 앞뒤 공백만 다른 것은 같은 값으로 본다(서버가 자른다).
     * @param form - 지금 입력값.
     * @param view - 서버가 준 설정.
     * @returns 바뀐 칸 수.
     */
    static changes(form: MailSettingsForm, view: MailSettingsView): number {
        const base = UtilMailSettings.initialForm(view);
        const keys = ['host', 'port', 'username', 'fromAddress', 'senderName', 'subjectPrefix'] as const;
        let count = keys.filter((key) => form[key].trim() !== base[key].trim()).length;
        if (form.security !== base.security) count += 1;
        if (form.password !== '') count += 1;
        return count;
    }

    /**
     * 비밀번호를 비워 저장하면 저장된 비밀번호를 그대로 쓸 수 있는가 — 저장된 것이 있고 사용자 이름·서버 주소(대소문자 무시)·보안 방식이 그대로일 때만(서버 `sameTarget`).
     * @param form - 지금 입력값.
     * @param view - 서버가 준 설정.
     * @returns 그대로 쓸 수 있으면 true.
     */
    static canKeepPassword(form: MailSettingsForm, view: MailSettingsView): boolean {
        if (!view.configured || !view.passwordSet) return false;
        return form.username.trim() === (view.username ?? '') && form.host.trim().toLowerCase() === (view.host ?? '').toLowerCase() && form.security === (view.security ?? 'NONE');
    }

    /**
     * 사용자 이름을 지워 저장된 비밀번호도 지워지게 되는가(인증 없이 보냄).
     * @param form - 지금 입력값.
     * @param view - 서버가 준 설정.
     * @returns 저장된 비밀번호가 있는데 사용자 이름을 비웠으면 true.
     */
    static clearsPassword(form: MailSettingsForm, view: MailSettingsView): boolean {
        return view.passwordSet && form.username.trim() === '';
    }

    /**
     * 저장 전에 입력을 미리 검사한다.
     * @param form - 입력값.
     * @param view - 서버가 준 설정(저장된 비밀번호를 쓸 수 있는지).
     * @returns 문제가 있는 칸과 까닭. 비어 있으면 통과.
     */
    static validate(form: MailSettingsForm, view: MailSettingsView): MailSettingsProblems {
        const problems: MailSettingsProblems = {};

        const host = form.host.trim();
        if (!host) problems.host = 'REQUIRED';
        else if (host.length > UtilMailSettings.HOST_MAX) problems.host = 'LENGTH';
        else if (SPACE_OR_CONTROL.test(host)) problems.host = 'FORMAT';

        const port = form.port.trim();
        if (!port) problems.port = 'REQUIRED';
        else if (!/^\d+$/.test(port) || Number(port) < UtilMailSettings.PORT_MIN || Number(port) > UtilMailSettings.PORT_MAX) problems.port = 'RANGE';

        const from = UtilValidation.email(form.fromAddress);
        if (from) problems.fromAddress = from;

        const username = form.username.trim();
        const password = form.password;
        if (username.length > UtilMailSettings.USERNAME_MAX) problems.username = 'LENGTH';
        else if (CONTROL.test(username)) problems.username = 'CONTROL';
        if (password.length > UtilMailSettings.PASSWORD_MAX) problems.password = 'LENGTH';
        else if (CONTROL.test(password)) problems.password = 'CONTROL';
        if (!username && password.trim() !== '') problems.username ??= 'PAIR';
        if (username && password.trim() === '' && !UtilMailSettings.canKeepPassword(form, view)) problems.password ??= view.passwordSet ? 'REENTER' : 'REQUIRED';

        const senderName = form.senderName.trim();
        if (senderName.length > UtilMailSettings.SENDER_NAME_MAX) problems.senderName = 'LENGTH';
        else if (NEWLINE.test(senderName)) problems.senderName = 'NEWLINE';
        const subjectPrefix = form.subjectPrefix.trim();
        if (subjectPrefix.length > UtilMailSettings.SUBJECT_PREFIX_MAX) problems.subjectPrefix = 'LENGTH';
        else if (NEWLINE.test(subjectPrefix)) problems.subjectPrefix = 'NEWLINE';
        return problems;
    }

    /**
     * 서버로 보낼 요청 — 비밀번호를 비웠으면 **빼서 보낸다**(저장된 값 유지). 사용자 이름이 비면 null(인증 없이 — 서버가 저장된 비밀번호도 지운다).
     * @param form - 입력값.
     * @returns 앞뒤 공백을 자른 요청 본문.
     */
    static toRequest(form: MailSettingsForm): MailSettingsSaveRequest {
        const username = form.username.trim();
        const request: MailSettingsSaveRequest = {
            host: form.host.trim(),
            port: Number(form.port.trim()),
            security: form.security,
            username: username || null,
            fromAddress: form.fromAddress.trim(),
            senderName: form.senderName.trim() || null,
            subjectPrefix: form.subjectPrefix.trim() || null,
        };
        if (username && form.password !== '') request.password = form.password;
        return request;
    }
}
