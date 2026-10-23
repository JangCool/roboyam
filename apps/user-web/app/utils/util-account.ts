import UtilValidation from '~/utils/util-validation';

/**
 * 계정 흐름(비밀번호 재설정·변경·이메일 변경) 화면의 판단 — 상태도 요청도 없는 순수 함수만 둔다. 문구는 `util-account-messages.ts`.
 * 원본(community) `utils/util-account.ts` 에서 이 화면들이 쓰는 것만 옮겼다(소셜 연동 결과 읽기는 계정 화면이 따로 한다).
 * 규약: community `docs/api-account.md`. 화면 검사는 편의이고 판정은 늘 서버가 한다(새 비밀번호 규칙 = 서버 `UserPolicy.passwordViolation`).
 */

/** 규칙 한 줄의 상태 — 아직 입력 전 · 충족 · 어긋남 */
export type RuleState = 'idle' | 'ok' | 'no';

/** 비밀번호 확인 칸의 상태 */
export type PasswordConfirmState = 'idle' | 'match' | 'mismatch';

export default class UtilAccount {
    /** 비밀번호를 여러 번 틀려 잠겼을 때 서버가 시간을 안 줬으면 쓰는 값(로그인 잠금과 같은 10분) */
    static readonly DEFAULT_LOCK_SECONDS = 600;
    /** 인증·재설정 메일을 다시 보낼 수 있을 때까지(서버가 시간을 안 줬을 때) */
    static readonly DEFAULT_RESEND_SECONDS = 60;

    /**
     * 기다릴 시간(초) — 서버가 준 값이 있으면 그것, 없거나 0 이하면 기본값
     * @param {number | undefined} seconds - 서버가 준 `retryAfterSeconds`.
     * @param {number} fallback - 서버가 안 줬을 때 쓸 초.
     * @returns {number} 기다릴 시간(초).
     */
    static waitSeconds(seconds: number | undefined, fallback: number = UtilAccount.DEFAULT_LOCK_SECONDS): number {
        return typeof seconds === 'number' && seconds > 0 ? seconds : fallback;
    }

    /**
     * 남은 시간을 분으로 — 올림하고 1분 아래로는 내려가지 않는다(안내 문구가 초마다 바뀌지 않게)
     * @param {number} seconds - 남은 시간(초).
     * @returns {number} 남은 분(1 이상).
     */
    static minutes(seconds: number): number {
        return Math.max(1, Math.ceil(seconds / 60));
    }

    /**
     * 새 비밀번호가 "현재 비밀번호와 다르게" 규칙을 지켰는지 — 둘 중 하나라도 비었으면 아직 모른다
     * @param {string} current - 입력한 현재 비밀번호.
     * @param {string} next - 입력한 새 비밀번호.
     * @returns {RuleState} 입력 전이면 `idle`, 다르면 `ok`, 같으면 `no`.
     */
    static differentFromCurrent(current: string, next: string): RuleState {
        if (!current || !next) return 'idle';
        return current === next ? 'no' : 'ok';
    }

    /**
     * 비밀번호 확인 칸이 비밀번호와 같은지
     * @param {string} password - 비밀번호.
     * @param {string} confirm - 비밀번호 확인.
     * @returns {PasswordConfirmState} 확인 칸이 비었으면 `idle`, 같으면 `match`, 다르면 `mismatch`.
     */
    static confirmState(password: string, confirm: string): PasswordConfirmState {
        if (!confirm) return 'idle';
        return confirm === password ? 'match' : 'mismatch';
    }

    /**
     * 비밀번호 변경을 보내도 되는지 — 현재 비밀번호가 있고, 새 비밀번호가 가입과 같은 규칙을 지키고, 현재와 다르고, 확인 칸과 같을 때
     * @param {string} current - 입력한 현재 비밀번호.
     * @param {string} next - 입력한 새 비밀번호.
     * @param {string} confirm - 입력한 새 비밀번호 확인.
     * @returns {boolean} 보내도 되면 true.
     */
    static canChangePassword(current: string, next: string, confirm: string): boolean {
        return (
            current.length > 0 &&
            UtilValidation.password(next).valid &&
            UtilAccount.differentFromCurrent(current, next) === 'ok' &&
            UtilAccount.confirmState(next, confirm) === 'match'
        );
    }
}
