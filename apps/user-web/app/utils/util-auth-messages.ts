import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { NicknameReason, SocialFailureReason } from '~/composables/auth/api/useAuthApi.types';
import type { EmailProblem, NicknameProblem } from '~/utils/util-validation';

/**
 * 오류 코드 → 화면 문구, 그리고 가입·로그인 입력 안내. **문구 본문은 `i18n/locales/{ko,en}/error.ts`·`auth.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약).
 *
 * **서버 `message` 가 있으면 그것을 먼저 쓴다** (`useApiClient` 가 그렇게 채운다).
 * 여기 있는 것은 서버 문구가 없을 때(네트워크 끊김·본문 없는 403/5xx 등)와, 화면이 코드를 보고 미리 알려 주는 입력 안내(클라이언트 검사)의 문구다. 분기는 문구가 아니라 `code` 로 한다.
 */

/** `i18n/locales/{ko,en}/error.ts` 에 문구가 있는 서버 오류 코드 — 모르는 코드는 HTTP 상태로 고른다 */
const CODES: ReadonlySet<string> = new Set([
    'VALIDATION',
    'INVALID_CREDENTIALS',
    'LOGIN_LOCKED',
    'EMAIL_TAKEN',
    'NICKNAME_TAKEN',
    'NICKNAME_TICKET_REQUIRED',
    'UNAUTHENTICATED',
    'TOKEN_INVALID',
    'TOKEN_EXPIRED',
    'ALREADY_VERIFIED',
    'RESEND_COOLDOWN',
    'SAME_PASSWORD',
    'CURRENT_PASSWORD_WRONG',
    'PASSWORD_NOT_SET',
    'EMAIL_CHANGE_NOT_ALLOWED',
    'NO_PENDING',
    'FORBIDDEN',
    'CSRF_INVALID',
    'NOT_FOUND',
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

/** 서버가 알려 준 닉네임 불가 사유 → `auth.*` 키 */
const NICKNAME_REASON: Record<NicknameReason, string> = {
    LENGTH: 'auth.nicknameLength',
    CHARS: 'auth.nicknameChars',
    DUPLICATE: 'auth.nicknameDuplicate',
    RESERVED: 'auth.nicknameReserved',
};

/** 소셜 로그인·연결 실패 사유(`reason=`, 서버 `SocialFailure`) → `login.*` 키 */
const SOCIAL_REASON: Record<SocialFailureReason, string> = {
    cancelled: 'login.reasonCancelled',
    invalid_state: 'login.reasonInvalidState',
    expired: 'login.reasonExpired',
    provider_error: 'login.reasonProviderError',
    account_unavailable: 'login.reasonAccountUnavailable',
    session_changed: 'login.reasonSessionChanged',
    already_linked: 'login.reasonAlreadyLinked',
};

export default class UtilAuthMessages {
    /**
     * 소셜 로그인·연결이 끝나지 않은 사유(주소의 `reason` 쿼리 값)를 문구로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} reason - 주소의 `reason` 값(없거나 모르는 값일 수 있다).
     * @returns {string} 사유 한 줄. 모르는 사유면 일반 문구(`login.socialFailedBody`).
     */
    static socialReason(t: Translate, reason: unknown): string {
        const key = typeof reason === 'string' && Object.prototype.hasOwnProperty.call(SOCIAL_REASON, reason) ? SOCIAL_REASON[reason as SocialFailureReason] : null;
        return t(key ?? 'login.socialFailedBody');
    }

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
     * 서버가 알려 준 닉네임 불가 사유를 문구로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameReason | null | undefined} reason - `nickname-available` 응답의 reason. 없으면 일반 문구가 나온다.
     * @returns {string} 화면에 보일 닉네임 안내 한 줄.
     */
    static nicknameReason(t: Translate, reason: NicknameReason | null | undefined): string {
        return t(reason ? NICKNAME_REASON[reason] : 'auth.nicknameReserved');
    }

    /**
     * 화면이 미리 검사한 닉네임 문제 — 1자일 때는 시안처럼 "2자 이상"이라고 말한다
     * @param {Translate} t - 번역 함수.
     * @param {NicknameProblem} problem - 화면이 미리 검사한 닉네임 문제.
     * @param {number} length - 입력한 닉네임의 글자 수(1자일 때는 "2자 이상"이라고 말한다).
     * @returns {string} 화면에 보일 닉네임 안내 한 줄.
     */
    static nicknameProblem(t: Translate, problem: NicknameProblem, length: number): string {
        if (problem === 'CHARS') return t('auth.nicknameChars');
        if (problem === 'LENGTH') return length < 2 ? t('auth.nicknameTooShort') : t('auth.nicknameLength');
        return t('auth.nicknameRequired');
    }

    /**
     * 화면이 미리 검사한 이메일 문제를 문구로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {EmailProblem} problem - 미리 검사한 이메일 문제.
     * @returns {string} 화면에 보일 이메일 안내 한 줄.
     */
    static emailProblem(t: Translate, problem: EmailProblem): string {
        if (problem === 'REQUIRED') return t('auth.emailRequired');
        if (problem === 'LENGTH') return t('auth.emailLength');
        return t('auth.emailFormat');
    }
}
