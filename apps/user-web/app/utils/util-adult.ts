import UtilTime from '#layers/display-time/utils/util-time';
import type { AdultMethod, AdultStatus } from '~/composables/adult/api/useAdultApi.types';

/** 성인 확인 화면이 보여 주는 상태 — 확인 전 · 출생연도는 있지만 아직 성인 아님 · 성인 확인됨 */
export type AdultView = 'NONE' | 'MINOR' | 'CONFIRMED';

/** 출생연도 칸의 문제 — 안 골랐음 · 만 14세 미만 · 범위 밖 */
export type BirthYearProblem = 'REQUIRED' | 'TOO_YOUNG' | 'OUT_OF_RANGE';

const MIN_AGE = 14;
const MAX_AGE = 100;
const ADULT_AGE = 19;
const METHODS: readonly AdultMethod[] = ['SELF', 'IDENTITY'];

/**
 * 성인 확인(19금 글을 볼 수 있는 자격)의 순수 규칙 — 서버 응답 읽기, 고를 수 있는 출생연도, 입력 검사, 상태 갈래(community `UtilAdult`).
 * 성인 판정은 **서버 한 곳**(한국 시간 올해 − 출생연도 ≥ 19)이 한다. 여기 `ADULT_AGE` 는 안내 문구와 「아직 성인이 아니에요」 갈래에만 쓴다.
 * ⚠ 출생연도는 개인정보 — 이 파일은 값을 어디에도 남기지 않는다. 문구는 화면이 `t('adult.*')` 로 고른다.
 */
export default class UtilAdult {
    static readonly ADULT_AGE = ADULT_AGE;
    static readonly MIN_AGE = MIN_AGE;

    /**
     * 한국 시간 기준 올해.
     * @param {number} now - 기준 시각(ms).
     * @returns {number} 올해.
     */
    static kstYear(now: number = Date.now()): number {
        return Number((UtilTime.serviceDayOf(now) ?? '').slice(0, 4)) || new Date(now).getUTCFullYear();
    }

    /**
     * 고를 수 있는 출생연도 범위 — 올해−100 ~ 올해−14.
     * @param {number} now - 기준 시각(ms).
     * @returns {{ minYear: number; maxYear: number }} 범위.
     */
    static rangeAt(now: number = Date.now()): { minYear: number; maxYear: number } {
        const year = UtilAdult.kstYear(now);
        return { minYear: year - MAX_AGE, maxYear: year - MIN_AGE };
    }

    /**
     * 서버 응답을 읽는다 — 칸이 없거나 깨져도 「확인 전」으로 읽는다.
     * @param {unknown} raw - 응답.
     * @returns {AdultStatus} 상태.
     */
    static sanitize(raw: unknown): AdultStatus {
        const root = typeof raw === 'object' && raw !== null && !Array.isArray(raw) ? (raw as Record<string, unknown>) : {};
        const fallback = UtilAdult.rangeAt();
        const year = (value: unknown, or: number): number => (typeof value === 'number' && Number.isInteger(value) && value > 1800 && value < 3000 ? value : or);
        const minYear = year(root.minYear, fallback.minYear);
        const maxYear = year(root.maxYear, fallback.maxYear);
        const birth = root.birthYear;
        return {
            birthYear: typeof birth === 'number' && Number.isInteger(birth) && birth > 1800 && birth < 3000 ? birth : null,
            adult: root.adult === true,
            method: METHODS.find((m) => m === root.method) ?? null,
            confirmedAt: typeof root.confirmedAt === 'string' && root.confirmedAt ? root.confirmedAt : null,
            minYear: Math.min(minYear, maxYear),
            maxYear: Math.max(minYear, maxYear),
        };
    }

    /**
     * 출생연도 고르기 목록 — 가장 늦은 해가 위.
     * @param {Pick<AdultStatus, 'minYear' | 'maxYear'>} range - 범위.
     * @returns {number[]} 내림차순.
     */
    static years(range: Pick<AdultStatus, 'minYear' | 'maxYear'>): number[] {
        const out: number[] = [];
        for (let y = range.maxYear; y >= range.minYear; y--) out.push(y);
        return out;
    }

    /**
     * 화면에 보일 상태 갈래.
     * @param {AdultStatus | null | undefined} status - 상태.
     * @returns {AdultView} 갈래.
     */
    static viewOf(status: AdultStatus | null | undefined): AdultView {
        if (!status) return 'NONE';
        if (status.adult) return 'CONFIRMED';
        return status.birthYear !== null ? 'MINOR' : 'NONE';
    }

    /**
     * 만 19세가 되는 해.
     * @param {number} birthYear - 출생연도.
     * @returns {number} 해.
     */
    static adultYear(birthYear: number): number {
        return birthYear + ADULT_AGE;
    }

    /**
     * 출생연도 입력 검사 — 서버(`400 fields.birthYear`)와 같은 범위.
     * @param {number | null} birthYear - 고른 해.
     * @param {Pick<AdultStatus, 'minYear' | 'maxYear'>} range - 범위.
     * @returns {BirthYearProblem | null} 문제.
     */
    static birthYearProblem(birthYear: number | null, range: Pick<AdultStatus, 'minYear' | 'maxYear'>): BirthYearProblem | null {
        if (birthYear === null || !Number.isInteger(birthYear)) return 'REQUIRED';
        if (birthYear > range.maxYear) return 'TOO_YOUNG';
        if (birthYear < range.minYear) return 'OUT_OF_RANGE';
        return null;
    }
}
