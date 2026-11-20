import type { Translate } from '#layers/i18n/utils/util-i18n';
import { ApiError } from '~/composables/common/useApiClient';
import UtilJjal, { type JjalPrepareProblem } from '~/utils/util-jjal';

/**
 * 내 짤 문구 중 계산이 낀 것 — 서버 오류 → 문구, 브라우저 줄이기 문제 → 문구. 상수 문구는 컴포넌트가 `t('jjal.…')` 로 부른다.
 */
export default class UtilJjalMessages {
    /**
     * 서버 오류 문구 — 서버가 준 문구가 있으면 그대로(가공하지 않는다), 없거나 네트워크 오류면 일반 문구.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} error - 잡은 오류.
     * @returns {string} 보일 문구.
     */
    static error(t: Translate, error: unknown): string {
        if (error instanceof ApiError && error.message) return error.message;
        return t('jjal.failed');
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
