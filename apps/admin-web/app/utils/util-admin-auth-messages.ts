import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { EmailProblem } from '~/utils/util-validation';

/**
 * 오류 코드 → 화면 문구, 그리고 로그인 입력 안내. **문구 본문은 `i18n/locales/{ko,en}/error.ts`·`login.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약).
 *
 * **서버 `message` 가 있으면 그것을 먼저 쓴다** (`useApiClient` 가 그렇게 채운다).
 * 여기 있는 것은 서버 문구가 없을 때(네트워크 끊김·본문 없는 403/5xx 등)와, 화면이 미리 알려 주는 입력 안내(클라이언트 검사)의 문구다. 분기는 문구가 아니라 `code` 로 한다.
 */

/** `i18n/locales/{ko,en}/error.ts` 에 문구가 있는 서버 오류 코드 — 모르는 코드는 HTTP 상태로 고른다 */
const CODES: ReadonlySet<string> = new Set([
    'VALIDATION',
    'INVALID_CREDENTIALS',
    'LOGIN_LOCKED',
    'UNAUTHENTICATED',
    'FORBIDDEN',
    'PERMISSION_DENIED',
    'CSRF_INVALID',
    'NOT_FOUND',
    'NOT_INSTALLED',
    'INTERNAL',
    'NETWORK',
    'SERVER_ERROR',
]);

/** HTTP 상태 → `error.*` 키(코드가 없을 때) */
const BY_STATUS: Record<number, string> = {
    0: 'error.NETWORK',
    401: 'error.UNAUTHENTICATED',
    403: 'error.status403',
    404: 'error.status404',
    429: 'error.status429',
};

export default class UtilAdminAuthMessages {
    /**
     * 서버가 문구를 안 줬을 때의 한 줄
     * @param {Translate} t - 번역 함수(`useI18n().t` 또는 `useTranslate()`).
     * @param {number} status - HTTP 상태 코드(네트워크가 끊겼으면 0).
     * @param {string} code - 서버 오류 코드(`NETWORK` 등).
     * @returns {string} 코드, 상태, 일반 문구 순으로 고른 한 줄.
     */
    static fallback(t: Translate, status: number, code: string): string {
        if (CODES.has(code)) return t(`error.${code}`);
        const byStatus = BY_STATUS[status];
        if (byStatus) return t(byStatus);
        return status >= 500 ? t('error.SERVER_ERROR') : t('error.generic');
    }

    /**
     * 화면이 미리 검사한 이메일 문제를 문구로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {EmailProblem} problem - 미리 검사한 이메일 문제.
     * @returns {string} 화면에 보일 이메일 안내 한 줄.
     */
    static emailProblem(t: Translate, problem: EmailProblem): string {
        if (problem === 'REQUIRED') return t('login.emailRequired');
        if (problem === 'LENGTH') return t('login.emailLength');
        return t('login.emailFormat');
    }
}
