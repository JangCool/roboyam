import type { TokenProblem } from '~/composables/account/api/useAdminAccountApi.types';

/**
 * 운영자 계정 비밀번호 흐름(초대 수락 · 비밀번호 재설정)의 순수 규칙. 후보 계약이라 코드는 확정 전이다(`useAdminAccountApi.ts` 머리 주석).
 */

/** 초대 링크가 쓸 수 없는 이유를 가르는 서버 오류 코드 */
const INVITATION_CODES: Readonly<Record<string, TokenProblem>> = {
    INVITATION_NOT_FOUND: 'invalid',
    INVITATION_EXPIRED: 'expired',
    INVITATION_USED: 'used',
    INVITATION_CANCELED: 'canceled',
};

export default class UtilAccount {
    /** 토큰이 이보다 길면 쓸 수 없는 값으로 보고 서버에 보내지 않는다(주소에 토큰이 없는 것과 같이 다룬다) */
    static readonly TOKEN_MAX = 512;
    /** 승인번호 자릿수의 기본값 — 서버가 요청 응답(`codeLength`)으로 알려 주면 그 값을 쓴다 */
    static readonly CERT_CODE_LENGTH = 6;

    /**
     * 주소 쿼리 `token` 에서 토큰 문자열을 꺼냅니다. 없거나 비었거나 배열·너무 긴 값이면 null.
     * @param {unknown} value - `route.query.token`(배열일 수도 있다).
     * @returns {string | null} 쓸 수 있는 토큰, 없으면 null.
     */
    static token(value: unknown): string | null {
        const raw = Array.isArray(value) ? value[0] : value;
        if (typeof raw !== 'string') return null;
        const token = raw.trim();
        return token && token.length <= UtilAccount.TOKEN_MAX ? token : null;
    }

    /**
     * 초대 확인·수락이 던진 오류 코드를 "초대를 쓸 수 없는 이유"로 바꿉니다. 그 밖의 코드(네트워크·서버 오류·입력 오류)는 null.
     * @param {string} code - `ApiError.code`.
     * @returns {TokenProblem | null} `invalid` · `expired` · `used` · `canceled`, 해당 없으면 null.
     */
    static invitationProblem(code: string): TokenProblem | null {
        return INVITATION_CODES[code] ?? null;
    }

    /**
     * 승인번호 때문에 거절됐는가 — 틀림·만료·이미 씀·너무 많이 틀림·받은 적 없음(`CERT_CODE_*`). 승인번호 칸 아래에 서버 문구를 보인다.
     * @param {string} code - `ApiError.code`.
     * @returns {boolean} 승인번호 오류이면 true.
     */
    static isCertCodeError(code: string): boolean {
        return code.startsWith('CERT_CODE_');
    }

    /**
     * 다시 받아야 하는 승인번호 오류인가 — 입력해 둔 번호가 더는 쓸 수 없다(만료·이미 씀·소진·받은 적 없음). 틀렸을 뿐이면 false.
     * @param {string} code - `ApiError.code`.
     * @returns {boolean} 새 승인번호를 받아야 하면 true.
     */
    static needsNewCertCode(code: string): boolean {
        return UtilAccount.isCertCodeError(code) && code !== 'CERT_CODE_INVALID';
    }

    /**
     * 승인번호 입력이 정해진 자릿수의 숫자인지 봅니다.
     * @param {string} value - 입력한 승인번호.
     * @param {number} length - 자릿수.
     * @returns {boolean} 숫자로만 이뤄진 `length` 자리이면 true.
     */
    static isCertCode(value: string, length: number): boolean {
        return new RegExp(`^[0-9]{${length}}$`).test(value);
    }
}
