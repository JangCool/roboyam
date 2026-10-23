import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MailSecurity, MailTestResultView } from '~/composables/mail/api/useMailSettingsApi.types';
import type { MailSettingsField, MailSettingsFieldErrors, MailSettingsProblem, MailSettingsProblems } from '~/utils/util-mail-settings';
import UtilMailSettings from '~/utils/util-mail-settings';

/**
 * 메일 서버 설정 화면의 문구 중 **계산이 끼는 것** — 문구 본문은 `i18n/locales/{ko,en}/mail.ts`, 여기는 어느 키를 고를지만 안다. `t` 를 첫 인자로 받는다.
 * 서버 `message`·`fields` 문구는 가공하지 않고 그대로 쓴다(`forms.md`). 분기는 코드(`MailSettingsProblem`·시험 발송 `reason`)로 한다.
 */

/** 칸 이름 → 「입력해 주세요」 문장 키 */
const REQUIRED_KEYS: Partial<Record<MailSettingsField, string>> = {
    host: 'mail.hostRequired',
    port: 'mail.portRequired',
    username: 'mail.usernameRequired',
    password: 'mail.passwordRequired',
    fromAddress: 'mail.fromRequired',
};

/** 시험 발송 실패 `reason` → 문구 키. 모르는 코드는 서버 `detail`(요청 언어) → 일반 문구 */
const REASON_KEYS: Record<string, string> = {
    NOT_CONFIGURED: 'mail.reasonNotConfigured',
    CRYPTO_KEY_MISSING: 'mail.reasonCryptoKeyMissing',
    CONNECT_FAILED: 'mail.reasonConnectFailed',
    AUTH_FAILED: 'mail.reasonAuthFailed',
    TLS_FAILED: 'mail.reasonTlsFailed',
    TIMEOUT: 'mail.reasonTimeout',
    REJECTED: 'mail.reasonRejected',
    FAILED: 'mail.reasonFailed',
};

export default class UtilMailSettingsMessages {
    /**
     * 보안 방식의 이름.
     * @param t - 번역 함수.
     * @param security - 보안 방식.
     * @returns 「암호화 안 함」 · STARTTLS · SSL/TLS.
     */
    static securityLabel(t: Translate, security: MailSecurity): string {
        if (security === 'NONE') return t('mail.securityNone');
        return security === 'STARTTLS' ? 'STARTTLS' : 'SSL/TLS';
    }

    /**
     * 화면이 미리 검사한 문제의 문구.
     * @param t - 번역 함수.
     * @param field - 어느 칸인지.
     * @param problem - 문제의 갈래.
     * @returns 칸 아래 한 줄.
     */
    static problem(t: Translate, field: MailSettingsField, problem: MailSettingsProblem): string {
        if (problem === 'REENTER') return t('mail.passwordReenter');
        if (problem === 'PAIR') return t('mail.pairUsername');
        if (problem === 'REQUIRED') return t(REQUIRED_KEYS[field] ?? 'common.required');
        if (problem === 'CONTROL') return t('mail.controlChars');
        if (problem === 'NEWLINE') return t('mail.noNewline');
        if (field === 'host') return problem === 'LENGTH' ? t('mail.hostLength', { max: UtilMailSettings.HOST_MAX }) : t('mail.hostSpace');
        if (field === 'port') return t('mail.portRange', { min: UtilMailSettings.PORT_MIN, max: UtilMailSettings.PORT_MAX });
        if (field === 'fromAddress') return problem === 'LENGTH' ? t('mail.fromLength') : t('mail.fromFormat');
        if (field === 'username') return t('mail.usernameLength', { max: UtilMailSettings.USERNAME_MAX });
        if (field === 'password') return t('mail.passwordLength', { max: UtilMailSettings.PASSWORD_MAX });
        if (field === 'senderName') return t('mail.senderNameLength', { max: UtilMailSettings.SENDER_NAME_MAX });
        return t('mail.subjectPrefixLength', { max: UtilMailSettings.SUBJECT_PREFIX_MAX });
    }

    /**
     * 미리 검사한 문제들을 칸별 문구로.
     * @param t - 번역 함수.
     * @param problems - 칸별 문제.
     * @returns 칸별 문구.
     */
    static fieldErrors(t: Translate, problems: MailSettingsProblems): MailSettingsFieldErrors {
        const out: MailSettingsFieldErrors = {};
        for (const [field, problem] of Object.entries(problems) as [MailSettingsField, MailSettingsProblem][]) {
            out[field] = UtilMailSettingsMessages.problem(t, field, problem);
        }
        return out;
    }

    /**
     * 시험 발송 실패의 까닭 한 줄 — `reason` 코드별 문구, 모르는 코드면 서버 `detail`, 그것도 없으면 일반 문구.
     * @param t - 번역 함수.
     * @param result - 서버의 시험 발송 응답.
     * @returns 한 줄.
     */
    static testFailure(t: Translate, result: MailTestResultView): string {
        const key = result.reason ? REASON_KEYS[result.reason] : undefined;
        if (key) return t(key);
        return result.detail || t('mail.reasonFailed');
    }
}
