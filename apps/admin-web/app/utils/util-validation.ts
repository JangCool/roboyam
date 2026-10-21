/**
 * 입력 검사 — 서버가 거절할 것을 화면이 미리 알려 주는 용도이고, 판정은 늘 서버가 한다.
 * 규칙은 공개 사이트 로그인과 같다(이메일 형식 254자 이하, 비밀번호는 비어 있지 않음 — 로그인은 비밀번호 형식을 검사하지 않는다.
 * 규칙이 바뀌기 전에 만든 계정이 막히면 안 된다). 관리자 로그인 서버 규칙은 `POST /api/admin/auth/login` 의 입력 검증(`400 VALIDATION`).
 *
 * **새 비밀번호를 정하는 화면**(초대 수락·비밀번호 재설정·비밀번호 변경)은 `password()` 로 공개 사이트 가입 규칙(8~64자 · UTF-8 72바이트 이하 · 영문과 숫자 포함, 서버 `UserPolicy`)을 미리 알려 준다.
 * 공개 사이트(`apps/user-web`)의 같은 유틸을 옮겨 적은 것이다 — 규칙이 바뀌면 두 곳을 함께 고친다(공유 레이어로 올리는 것은 후속).
 */

/** 실용적인 형식 검사 — 골뱅이 하나, 도메인에 점. 최종 판정은 서버. */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** 한글(완성형)·영문·숫자·밑줄, 그리고 글자 사이의 공백 한 칸 (앞뒤·연속 공백 불가 — 서버 `UserPolicy.NICKNAME_CHARS` 와 같다) */
const NICKNAME_CHARS = /^[가-힣A-Za-z0-9_]+( [가-힣A-Za-z0-9_]+)*$/;

export type EmailProblem = 'REQUIRED' | 'FORMAT' | 'LENGTH';
export type NicknameProblem = 'REQUIRED' | 'CHARS' | 'LENGTH';
/** 새 비밀번호의 첫 문제 — 비어 있음 · 글자 수 · UTF-8 바이트 · 영문과 숫자 */
export type PasswordProblem = 'REQUIRED' | 'LENGTH' | 'BYTES' | 'MIX';

export interface PasswordCheck {
    /** 8~64자 */
    length: boolean;
    /** UTF-8 72바이트 이하 (BCrypt 한도 — 한글은 글자당 3바이트라 24자 안팎) */
    bytes: boolean;
    /** 영문과 숫자를 모두 포함 */
    letterAndDigit: boolean;
    /** 모두 만족 */
    valid: boolean;
}

export default class UtilValidation {
    static readonly EMAIL_MAX = 254;
    /** 새 비밀번호 글자 수 — 공개 사이트 가입 규칙과 같다 */
    static readonly PASSWORD_MIN = 8;
    static readonly PASSWORD_MAX = 64;
    /** 서버 `UserPolicy.PASSWORD_MAX_BYTES` — UTF-8 바이트 한도 */
    static readonly PASSWORD_MAX_BYTES = 72;
    /** 서버 `UserPolicy.NICKNAME_MIN_LENGTH` · `NICKNAME_MAX_LENGTH` — 닉네임 2~12자 */
    static readonly NICKNAME_MIN = 2;
    static readonly NICKNAME_MAX = 12;

    /**
     * 이메일이 문제 없으면 null
     * @param {string} value - 입력한 이메일.
     * @returns {EmailProblem | null} 문제 없으면 null, 있으면 `REQUIRED` · `LENGTH` · `FORMAT`.
     */
    static email(value: string): EmailProblem | null {
        const v = value.trim();
        if (!v) return 'REQUIRED';
        if (v.length > UtilValidation.EMAIL_MAX) return 'LENGTH';
        if (!EMAIL_PATTERN.test(v)) return 'FORMAT';
        return null;
    }

    /**
     * 닉네임 형식 — 글자 수 등 화면에서 미리 알 수 있는 문제만. 중복(`409 NICKNAME_TAKEN`)은 서버만 안다. 문제 없으면 null.
     * 서버 규칙(`UserPolicy.nicknameViolation`)과 같다: 2~12자, 한글 완성형·영문·숫자·밑줄, 글자 사이의 공백 한 칸(앞뒤·연속 공백 불가).
     * @param {string} value - 입력한 닉네임(앞뒤를 자르지 않은 그대로 — 서버가 앞뒤 공백을 허용하지 않는다).
     * @returns {NicknameProblem | null} 문제 없으면 null, 있으면 `REQUIRED` · `CHARS` · `LENGTH`.
     */
    static nickname(value: string): NicknameProblem | null {
        if (!value) return 'REQUIRED';
        // 허용하지 않는 글자가 섞였으면 글자 수보다 그것이 먼저 고칠 일이다
        if (!NICKNAME_CHARS.test(value)) return 'CHARS';
        const length = Array.from(value).length;
        if (length < UtilValidation.NICKNAME_MIN || length > UtilValidation.NICKNAME_MAX) return 'LENGTH';
        return null;
    }

    /**
     * 새 비밀번호가 규칙을 지켰는지 항목별로 봅니다.
     * @param {string} value - 입력한 비밀번호.
     * @returns {PasswordCheck} 길이·UTF-8 바이트·영문과 숫자 포함·모두 만족 여부.
     */
    static password(value: string): PasswordCheck {
        const length = value.length >= UtilValidation.PASSWORD_MIN && value.length <= UtilValidation.PASSWORD_MAX;
        const bytes = UtilValidation.utf8Bytes(value) <= UtilValidation.PASSWORD_MAX_BYTES;
        const letterAndDigit = /[A-Za-z]/.test(value) && /\d/.test(value);
        return { length, bytes, letterAndDigit, valid: length && bytes && letterAndDigit };
    }

    /**
     * 새 비밀번호의 첫 문제 하나 — 칸 아래에 한 줄로 보일 때 쓴다(규칙 목록은 `password()` 의 항목별 결과를 쓴다). 문제 없으면 null.
     * @param {string} value - 입력한 비밀번호.
     * @returns {PasswordProblem | null} 문제 없으면 null, 있으면 `REQUIRED` · `LENGTH` · `BYTES` · `MIX`.
     */
    static passwordProblem(value: string): PasswordProblem | null {
        if (!value) return 'REQUIRED';
        const check = UtilValidation.password(value);
        if (!check.length) return 'LENGTH';
        if (!check.bytes) return 'BYTES';
        if (!check.letterAndDigit) return 'MIX';
        return null;
    }

    /**
     * UTF-8 로 인코딩했을 때의 바이트 수 (영문·숫자 1, 한글 3).
     * @param {string} value - 입력한 글.
     * @returns {number} 바이트 수.
     */
    static utf8Bytes(value: string): number {
        return new TextEncoder().encode(value).length;
    }
}
