import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilTime from '#layers/display-time/utils/util-time';
import { ApiError } from '~/composables/common/useApiClient';
import UtilJjal, { type JjalPrepareProblem } from '~/utils/util-jjal';

/** 올리기 속도 한도(서버 `JjalRateLimitedException`·`JjalDailyLimitException` — `429`, `retryAfterSeconds`) */
const RATE_CODES: ReadonlySet<string> = new Set(['JJAL_RATE_LIMITED', 'JJAL_DAILY_LIMIT']);
/** 글·댓글 저장 때의 짤 오류(서버 `JjalGuard` — 게시판 칸 꺼짐 `422` · 내 보관함에 없음·내려간 짤 `409`, 최상위 `jjalId`) */
const BODY_CODES: ReadonlySet<string> = new Set(['JJAL_NOT_ALLOWED', 'JJAL_NOT_IN_BOX', 'JJAL_BLOCKED']);

/**
 * 내 짤 문구 중 계산이 낀 것 — 서버 오류 → 문구, 브라우저 줄이기 문제 → 문구. 상수 문구는 컴포넌트가 `t('jjal.…')` 로 부른다.
 * 서버 오류 문구는 서버가 준다(`messages_*.properties` 의 `error.JJAL_*` · 이메일 미인증은 짤 문구 `error.EMAIL_NOT_VERIFIED.jjal`) — 화면은 남은 시간 같은 덧붙임만 한다.
 */
export default class UtilJjalMessages {
    /**
     * 서버 오류 문구 — 서버가 준 문구가 있으면 그대로(가공하지 않는다), 없거나 네트워크 오류면 일반 문구.
     * 올리기 속도 한도(`429 JJAL_RATE_LIMITED`·`JJAL_DAILY_LIMIT`)는 서버 문구 뒤에 다시 올릴 수 있는 때(`retryAfterSeconds`)를 붙인다.
     * multipart 한도를 넘은 파일(`413 FILE_TOO_LARGE` — 일반 문구)은 짤 상한 문구로 바꾼다.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} error - 잡은 오류.
     * @param {string} [language] - 남은 시간 말의 언어(표시 값 `language`).
     * @returns {string} 보일 문구.
     */
    static error(t: Translate, error: unknown, language?: string): string {
        if (!(error instanceof ApiError)) return t('jjal.failed');
        if (error.code === 'FILE_TOO_LARGE') return t('jjal.fileTooLarge', { max: UtilJjal.size(UtilJjal.MAX_BYTES) });
        if (!error.message) return t('jjal.failed');
        if (RATE_CODES.has(error.code) && typeof error.retryAfterSeconds === 'number' && error.retryAfterSeconds > 0) {
            return `${error.message} ${t('jjal.retryIn', { time: UtilTime.duration(error.retryAfterSeconds, language) })}`;
        }
        return error.message;
    }

    /**
     * 글·댓글 저장이 짤 때문에 거절됐으면 본문 칸 아래에 보일 문구(서버 문구 그대로) — 아니면 null. 로보티콘 `UtilEmoticonMessages.bodyError` 옆에서 부른다.
     * @param {unknown} error - 저장에서 잡은 오류.
     * @returns {string | null} 본문 칸 문구.
     */
    static bodyError(error: unknown): string | null {
        if (!(error instanceof ApiError) || !BODY_CODES.has(error.code)) return null;
        return error.message || null;
    }

    /**
     * 브라우저 줄이기 문제 → 문구.
     * @param {Translate} t - 번역 함수.
     * @param {JjalPrepareProblem} problem - 문제.
     * @param {{ frames?: number; durationMs?: number }} detail - 움짤 길이·장 수(있으면 문구에 넣는다).
     * @returns {string} 보일 문구.
     */
    static prepare(t: Translate, problem: JjalPrepareProblem, detail: { frames?: number; durationMs?: number } = {}): string {
        switch (problem) {
            case 'UNSUPPORTED':
                return t('jjal.problem.unsupported');
            case 'WEBP_ANIMATED_UNSUPPORTED':
                return t('jjal.problem.webpAnimated');
            case 'TOO_LONG':
                return t('jjal.problem.tooLong', { seconds: ((detail.durationMs ?? 0) / 1000).toFixed(1), max: UtilJjal.MAX_DURATION_MS / 1000 });
            case 'TOO_MANY_FRAMES':
                return t('jjal.problem.tooManyFrames', { frames: detail.frames ?? 0, max: UtilJjal.MAX_FRAMES });
            case 'RESULT_TOO_BIG':
                return t('jjal.problem.resultTooBig', { max: UtilJjal.size(UtilJjal.MAX_BYTES) });
            case 'SOURCE_TOO_BIG':
                return t('jjal.problem.sourceTooBig', { max: UtilJjal.size(UtilJjal.SOURCE_MAX_BYTES) });
            default:
                return t('jjal.problem.broken');
        }
    }

    /**
     * 줄인 결과 한 줄 — 「240×240 · 180KB」, 움짤이면 「· 움짤 24장 · 2.4초」를 더한다.
     * @param {Translate} t - 번역 함수.
     * @param {{ width: number; height: number; bytes: number; animated: boolean; frames: number; durationMs: number }} r - 줄인 결과.
     * @returns {string} 한 줄.
     */
    static prepared(t: Translate, r: { width: number; height: number; bytes: number; animated: boolean; frames: number; durationMs: number }): string {
        const base = t('jjal.preparedInfo', { width: r.width, height: r.height, size: UtilJjal.size(r.bytes) });
        return r.animated ? `${base} · ${t('jjal.preparedAnimated', { frames: r.frames, seconds: (r.durationMs / 1000).toFixed(1) })}` : base;
    }
}
