import { ApiError } from '~/composables/common/useApiClient';

/**
 * 19금 게시판 접근 설정·핀번호의 순수 규칙 — 핀번호 모양, 서버 오류 읽기, 남은 시간 글자. 계약: `docs/features/adult-board-pin-contract.md`.
 * 화면 검사는 편의이고 판정은 서버다(오류 코드 — `useAdultBoardApi` 주석).
 */
export default class UtilAdultBoard {
    static readonly PIN_LENGTH = 6;
    /** 연속 5번 틀리면 잠기는 시간(초) — 서버가 끝 시각을 안 줬을 때만 쓴다 */
    static readonly DEFAULT_LOCK_SECONDS = 600;

    /** 본인인증 화면(`/settings/identity`) 안 「19금 게시판」 구역의 `id` — 주소의 `#adult-board` 로 그 구역까지 내려간다 */
    static readonly ANCHOR = 'adult-board';
    /** 접근 설정이 있는 곳 — 본인인증 화면 안의 「19금 게시판」 구역(사용자 결정 2026-11-18). 옛 주소 `/settings/adult-board` 는 여기로 넘긴다 */
    static readonly SETTINGS_PATH = '/settings/identity#adult-board';
    /** 핀 입력 화면의 [핀번호를 잊었어요] — 그 구역이 다시 정하기 대화상자를 바로 연다 */
    static readonly RESET_PATH = '/settings/identity?reset=1#adult-board';

    /** 성인 전용 게시판을 여는 API 가 「핀 필요」라고 할 때(403) */
    static readonly CODE_PIN_REQUIRED = 'ADULT_BOARD_PIN_REQUIRED';
    static readonly CODE_PIN_WRONG = 'ADULT_BOARD_PIN_WRONG';
    static readonly CODE_PIN_LOCKED = 'ADULT_BOARD_PIN_LOCKED';
    /** 접근이 꺼져 있다(409 — 다른 기기에서 껐을 때의 핀 확인·핀 바꾸기) */
    static readonly CODE_DISABLED = 'ADULT_BOARD_DISABLED';

    /**
     * 핀번호 모양인가 — 숫자 6자리.
     * @param {string} value - 입력한 값.
     * @returns {boolean} 숫자 6자리면 true.
     */
    static isPin(value: string): boolean {
        return /^\d{6}$/.test(value);
    }

    /**
     * 잡은 오류가 「핀 필요」(`403 ADULT_BOARD_PIN_REQUIRED`)인가 — 맞으면 틀림 잠금의 끝 시각(없으면 null)을 담아 준다.
     * @param {unknown} e - 잡은 오류.
     * @returns {{ lockedUntil: string | null } | null} 핀 필요면 그 정보, 아니면 null.
     */
    static pinRequired(e: unknown): { lockedUntil: string | null } | null {
        if (!(e instanceof ApiError) || e.code !== UtilAdultBoard.CODE_PIN_REQUIRED) return null;
        return { lockedUntil: e.extraText('lockedUntil') ?? null };
    }

    /**
     * 잠금이 끝나는 시각(밀리초) — 오류의 `lockedUntil`, 없으면 `retryAfterSeconds`, 그것도 없으면 기본 10분.
     * @param {ApiError} e - `ADULT_BOARD_PIN_LOCKED` 오류.
     * @param {number} now - 지금(밀리초).
     * @returns {number} 끝나는 시각(밀리초).
     */
    static lockEndsAt(e: ApiError, now: number = Date.now()): number {
        const until = UtilAdultBoard.millis(e.extraText('lockedUntil') ?? null);
        if (until > now) return until;
        const seconds = typeof e.retryAfterSeconds === 'number' && e.retryAfterSeconds > 0 ? e.retryAfterSeconds : UtilAdultBoard.DEFAULT_LOCK_SECONDS;
        return now + seconds * 1000;
    }

    /**
     * ISO 시각을 밀리초로 — 없거나 읽을 수 없으면 0.
     * @param {string | null | undefined} iso - ISO 시각.
     * @returns {number} 밀리초(없으면 0).
     */
    static millis(iso: string | null | undefined): number {
        const ms = iso ? Date.parse(iso) : NaN;
        return Number.isFinite(ms) ? ms : 0;
    }

    /**
     * 남은 초를 `분:초` 글자로(예 `9:05`).
     * @param {number} seconds - 남은 시간(초).
     * @returns {string} `m:ss`.
     */
    static clock(seconds: number): string {
        const safe = Math.max(0, Math.floor(seconds));
        return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`;
    }

    /**
     * 남은 시간을 분으로 — 올림하고 1분 아래로는 내려가지 않는다(안내 문구가 초마다 바뀌지 않게).
     * @param {number} seconds - 남은 시간(초).
     * @returns {number} 남은 분(1 이상).
     */
    static minutes(seconds: number): number {
        return Math.max(1, Math.ceil(seconds / 60));
    }
}
