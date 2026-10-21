import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MailCheckResult, MailSecurity } from '~/composables/install/api/useInstallApi.types';
import type { MailField, MailFieldErrors } from '~/utils/util-mail';
import UtilMail, { type MailProblem, type MailProblems } from '~/utils/util-mail';

/**
 * 메일 서버 입력에 보이는 문구 중 **계산이 끼는 것**(설치 마법사 4단계 · 운영자 설정 화면) — **문구 본문은 `i18n/locales/{ko,en}/mail.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약). 상수 문구는 컴포넌트가 `t('mail.xxx')` 를 바로 부른다.
 *
 * **서버 `message`·`fields` 가 있으면 그것을 먼저 쓴다**(frontend-guide 6-1) — 여기 있는 것은 입력 안내·연결 확인 결과·화면이 미리 검사한 오류의 문구다.
 * 분기는 문구가 아니라 코드(`MailProblem`·`reason`)로 한다. **서버 응답 원문·자격 증명은 어디에도 싣지 않는다**(서버도 `reason` 코드만 준다).
 */

/** 연결 확인 결과를 화면이 쓰는 모양으로 — 성공 · 실패(까닭 한 줄) */
export interface MailCheckCopy {
    ok: boolean;
    text: string;
}

/** 칸 → "입력해 주세요" 문장의 `mail.*` 키 */
const REQUIRED_KEYS: Record<MailField, string> = {
    host: 'mail.hostRequired',
    port: 'mail.portRequired',
    security: 'mail.securityRequired',
    username: 'mail.usernameRequired',
    password: 'mail.passwordRequired',
    from: 'mail.fromRequired',
};

/** 연결 확인 실패 `reason` 코드 → `mail.*` 키 */
const REASON_KEYS: Record<string, string> = {
    CONNECT_FAILED: 'mail.reasonConnectFailed',
    AUTH_FAILED: 'mail.reasonAuthFailed',
    TLS_FAILED: 'mail.reasonTlsFailed',
    TIMEOUT: 'mail.reasonTimeout',
};

export default class UtilMailMessages {
    /**
     * 비밀번호 칸 아래 안내 — 값은 다시 보여 주지 않는다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {boolean} registered - 비밀번호가 이미 등록돼 있는가(설정 화면).
     * @returns {string} 등록 상태에 맞는 안내 한 줄.
     */
    static passwordHelp(t: Translate, registered: boolean): string {
        return registered ? t('mail.passwordHelpRegistered') : t('mail.passwordHelpNew');
    }

    /**
     * 비밀번호 칸이 비어 있을 때 칸 안에 보일 글자 — 등록돼 있으면 "등록됨" 이라고 알린다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {boolean} registered - 비밀번호가 이미 등록돼 있는가(설정 화면).
     * @returns {string} 자리 표시 글자.
     */
    static passwordPlaceholder(t: Translate, registered: boolean): string {
        return registered ? t('mail.passwordPlaceholderRegistered') : '';
    }

    /**
     * 보안 방식의 이름.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {MailSecurity} security - 보안 방식.
     * @returns {string} "없음" · "STARTTLS" · "SSL/TLS".
     */
    static securityLabel(t: Translate, security: MailSecurity): string {
        if (security === 'NONE') return t('common.none');
        return security === 'STARTTLS' ? 'STARTTLS' : 'SSL/TLS';
    }

    /**
     * 보안 방식 선택지 한 줄 — 이름과 보통 쓰는 포트.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {MailSecurity} security - 보안 방식.
     * @returns {string} "STARTTLS · 보통 587" 꼴의 글자.
     */
    static securityOption(t: Translate, security: MailSecurity): string {
        return t('mail.securityOption', { label: UtilMailMessages.securityLabel(t, security), port: UtilMail.defaultPort(security) });
    }

    /**
     * 화면이 미리 검사한 입력 문제의 문구.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {MailField} field - 어느 칸인지.
     * @param {MailProblem} problem - 문제의 갈래.
     * @returns {string} 그 칸 아래에 보일 한 줄.
     */
    static problem(t: Translate, field: MailField, problem: MailProblem): string {
        if (problem === 'PAIR') return field === 'username' ? t('mail.pairUsername') : t('mail.pairPassword');
        if (problem === 'REQUIRED') return t(REQUIRED_KEYS[field]);
        if (field === 'host') return problem === 'LENGTH' ? t('mail.hostLength', { max: UtilMail.HOST_MAX }) : t('mail.hostSpace');
        if (field === 'port') return t('mail.portRange', { min: UtilMail.PORT_MIN, max: UtilMail.PORT_MAX });
        return problem === 'LENGTH' ? t('mail.fromLength') : t('mail.fromFormat');
    }

    /**
     * 미리 검사한 문제들을 칸별 문구로 바꾼다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {MailProblems} problems - 칸별 문제.
     * @returns {MailFieldErrors} 칸별 문구.
     */
    static fieldErrors(t: Translate, problems: MailProblems): MailFieldErrors {
        const out: MailFieldErrors = {};
        for (const field of ['host', 'port', 'security', 'username', 'password', 'from'] as const) {
            const problem = problems[field];
            if (problem) out[field] = UtilMailMessages.problem(t, field, problem);
        }
        return out;
    }

    /**
     * 연결 확인 결과를 사람이 읽는 문구로 — 실패는 `reason` 코드에 따른 까닭 한 줄.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {MailCheckResult} result - 서버의 연결 확인 응답.
     * @returns {MailCheckCopy} 성공 여부와 보일 글자. 모르는 `reason` 이면 일반 문구.
     */
    static checkResult(t: Translate, result: MailCheckResult): MailCheckCopy {
        if (result.ok) return { ok: true, text: t('mail.checkConnected') };
        const key = result.reason ? REASON_KEYS[result.reason] : undefined;
        return { ok: false, text: key ? t(key) : t('mail.checkFailed') };
    }
}
