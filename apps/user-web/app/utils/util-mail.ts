import { ApiError } from '~/composables/common/useApiClient';
import type { MailInput, MailSecurity } from '~/composables/install/api/useInstallApi.types';
import UtilValidation from '~/utils/util-validation';

/** 메일 입력칸 이름 — 서버 오류의 `fields` 키와 같다(`mail.` 접두어는 마법사 요청일 때만 붙는다) */
export type MailField = 'host' | 'port' | 'security' | 'username' | 'password' | 'from';

/** 메일 입력칸별 오류 문구 */
export type MailFieldErrors = Partial<Record<MailField, string>>;

/**
 * 메일 서버(설치 마법사 4단계 · 운영자 설정 화면)의 순수 로직 — 입력값 모양·입력 규칙·요청 만들기·서버 오류 가르기.
 * 입력 규칙은 docs/api-install.md 의 `MailInput` 설명과 같다: host 1~255 · port 1~65535 · from 이메일 형식 · username·password 는 선택(둘 다 비우면 인증 없이).
 * (서버가 거절할 것을 화면이 미리 알려 주는 용도이고 판정은 서버.) 화면에 보이는 문구는 `util-mail-messages.ts` 에 있다.
 */

/** 메일 입력칸의 값 — 포트는 입력 중에 글자일 수 있어 문자열로 든다 */
export interface MailForm {
    host: string;
    port: string;
    security: MailSecurity;
    username: string;
    password: string;
    from: string;
}

/** 입력칸의 문제 — 비어 있음 · 너무 김 · 형식 · 범위 밖 · 아이디와 비밀번호가 짝이 아님 */
export type MailProblem = 'REQUIRED' | 'LENGTH' | 'FORMAT' | 'RANGE' | 'PAIR';
export type MailProblems = Partial<Record<MailField, MailProblem>>;

/** 검사 옵션 */
export interface MailValidateOptions {
    /** 비밀번호가 이미 등록돼 있다 — 설정 화면에서 비밀번호 칸을 비워 두면 기존 값을 유지한다 */
    passwordRegistered?: boolean;
    /** 인증 정보(아이디·비밀번호)를 받을 수 없다 — 암호화 키가 없어 비밀번호를 저장하지 못할 때. 이때는 짝 검사를 하지 않는다 */
    credentialsLocked?: boolean;
}

/** 연결 확인의 진행 — 아직 안 함 · 하는 중 · 연결됨 · 실패. 입력을 고치면 다시 `idle` 이 된다 */
export type MailCheckStatus = 'idle' | 'checking' | 'ok' | 'fail';

/** 연결 확인 결과 — 화면에 보일 글자까지 */
export interface MailCheckState {
    status: MailCheckStatus;
    text: string;
}

/** 보안 방식별 보통 쓰는 포트 */
const DEFAULT_PORTS: Record<MailSecurity, string> = { NONE: '25', STARTTLS: '587', SSL: '465' };

export default class UtilMail {
    /** 보안 방식 선택지 — 화면에 보이는 순서 */
    static readonly SECURITIES: readonly MailSecurity[] = ['NONE', 'STARTTLS', 'SSL'];
    /** 서버 주소 최대 글자 수 */
    static readonly HOST_MAX = 255;
    static readonly PORT_MIN = 1;
    static readonly PORT_MAX = 65535;

    /**
     * 연결 확인을 아직 안 한 상태.
     * @returns {MailCheckState} `idle` 과 빈 글자.
     */
    static idleCheck(): MailCheckState {
        return { status: 'idle', text: '' };
    }

    /**
     * 처음 보여 줄 입력값 — STARTTLS·587 로 시작한다(요즘 메일 서버가 가장 많이 쓰는 방식).
     * @returns {MailForm} 비어 있는 입력값.
     */
    static initialForm(): MailForm {
        return { host: '', port: DEFAULT_PORTS.STARTTLS, security: 'STARTTLS', username: '', password: '', from: '' };
    }

    /**
     * 설치 마법사 4단계의 처음 입력값 — 시험용 메일 수신함(Mailpit, SMTP `localhost:1035`)을 **모든 빌드(개발·운영)에서** 미리 채워 연결 확인을 바로 해 볼 수 있게 한다(사용자 지시).
     * Mailpit 은 인증이 없고 연결 확인은 메일도 보내지 않아 아이디·비밀번호는 비운다(암호화 키가 없어도 된다).
     * 운영 서버에서는 설치하는 사람이 실제 메일 서버 값으로 고치거나 건너뛴다.
     * @returns {MailForm} 시험 서버 값.
     */
    static installForm(): MailForm {
        return { host: 'localhost', port: '1035', security: 'NONE', username: '', password: '', from: 'no-reply@admin.local' };
    }

    /**
     * 보안 방식에 보통 쓰는 포트.
     * @param {MailSecurity} security - 보안 방식.
     * @returns {string} 25(없음) · 587(STARTTLS) · 465(SSL).
     */
    static defaultPort(security: MailSecurity): string {
        return DEFAULT_PORTS[security];
    }

    /**
     * 보안 방식을 바꿀 때 포트를 어떻게 할까 — 비어 있거나 다른 방식의 기본 포트 그대로면 새 방식의 기본 포트로, 직접 바꾼 포트는 그대로 둔다.
     * @param {string} currentPort - 지금 입력된 포트.
     * @param {MailSecurity} next - 새 보안 방식.
     * @returns {string} 새 포트 입력값.
     */
    static portAfterSecurityChange(currentPort: string, next: MailSecurity): string {
        const port = currentPort.trim();
        const isDefault = Object.values(DEFAULT_PORTS).includes(port);
        return !port || isDefault ? DEFAULT_PORTS[next] : currentPort;
    }

    /**
     * 입력을 미리 검사한다. 서버 주소·포트·보내는 주소는 앞뒤 공백을 잘라서 본다.
     * 인증은 아이디와 비밀번호가 짝이다 — 한쪽만 있으면 문제다(둘 다 비우면 인증 없이). 이미 등록된 비밀번호가 있으면(`passwordRegistered`) 비밀번호는 비워 둘 수 있다.
     * @param {MailForm} form - 입력값.
     * @param {MailValidateOptions} options - 비밀번호가 이미 등록돼 있는가·인증 정보를 받을 수 없는가.
     * @returns {MailProblems} 문제가 있는 칸과 까닭. 비어 있으면 통과.
     */
    static validate(form: MailForm, options: MailValidateOptions = {}): MailProblems {
        const problems: MailProblems = {};

        const host = form.host.trim();
        if (!host) problems.host = 'REQUIRED';
        else if (host.length > UtilMail.HOST_MAX) problems.host = 'LENGTH';
        else if (/\s/.test(host)) problems.host = 'FORMAT';

        const port = form.port.trim();
        if (!port) problems.port = 'REQUIRED';
        else if (!/^\d+$/.test(port)) problems.port = 'FORMAT';
        else if (Number(port) < UtilMail.PORT_MIN || Number(port) > UtilMail.PORT_MAX) problems.port = 'RANGE';

        const from = UtilValidation.email(form.from);
        if (from) problems.from = from;

        if (!options.credentialsLocked) {
            const hasUser = form.username.trim() !== '';
            const hasPassword = form.password !== '';
            if (hasUser && !hasPassword && !options.passwordRegistered) problems.password = 'PAIR';
            if (!hasUser && hasPassword) problems.username = 'PAIR';
        }
        return problems;
    }

    /**
     * 서버로 보낼 요청 — 아이디·비밀번호를 비웠으면 **빼서 보낸다**(인증 없이 · 설정 화면에서는 기존 비밀번호 유지). 포트는 숫자로 바꾼다.
     * @param {MailForm} form - 입력값.
     * @returns {MailInput} 앞뒤 공백을 자른 요청 본문.
     */
    static toInput(form: MailForm): MailInput {
        const input: MailInput = { host: form.host.trim(), port: Number(form.port.trim()), from: form.from.trim(), security: form.security };
        const username = form.username.trim();
        if (username) input.username = username;
        if (form.password !== '') input.password = form.password;
        return input;
    }

    /**
     * 입력 오류(`400 VALIDATION`)의 칸별 서버 문구. 마법사 요청이면 키가 `mail.host` 꼴이고 설정 화면·연결 확인 요청이면 `host` 꼴이다 — 둘 다 읽는다.
     * @param {unknown} e - 잡은 오류.
     * @returns {MailFieldErrors | null} 칸별 서버 문구. 이 칸들에 대한 문구가 없으면 null(그때는 다른 방법으로 알린다).
     */
    static fieldErrorsOf(e: unknown): MailFieldErrors | null {
        if (!(e instanceof ApiError) || e.code !== 'VALIDATION') return null;
        const out: MailFieldErrors = {};
        for (const field of ['host', 'port', 'security', 'username', 'password', 'from'] as const) {
            const message = e.field(field) ?? e.field(`mail.${field}`);
            if (message) out[field] = message;
        }
        return Object.keys(out).length ? out : null;
    }
}
