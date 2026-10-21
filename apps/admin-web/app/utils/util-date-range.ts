/**
 * 기간 입력의 빠른 선택(「오늘 · 1주일 · 1개월 · 3개월 · 6개월 · 1년」) 계산 — 순수 함수다. 날짜는 달력 글자(`YYYY-MM-DD`)이고 시간대 변환은 하지 않는다.
 * "오늘"은 부르는 쪽이 표시(사이트) 시간대로 구해 넘긴다.
 */
export type DateRangePresetKind = 'TODAY' | 'WEEK' | 'MONTH1' | 'MONTH3' | 'MONTH6' | 'YEAR';

export interface DateRangeValue {
    from: string;
    to: string;
}

const MONTHS: Record<Exclude<DateRangePresetKind, 'TODAY' | 'WEEK'>, number> = { MONTH1: 1, MONTH3: 3, MONTH6: 6, YEAR: 12 };

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

function ymdOf(date: Date): string {
    return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export default class UtilDateRange {
    /** 보여 주는 차례 */
    static readonly PRESETS: readonly DateRangePresetKind[] = ['TODAY', 'WEEK', 'MONTH1', 'MONTH3', 'MONTH6', 'YEAR'];

    /**
     * 오늘 기준으로 채울 기간 — `오늘`은 오늘~오늘, 나머지는 (오늘 − 기간)~오늘. 달 수는 말일을 넘지 않게 맞춘다(3월 31일 − 1개월 = 2월 말일).
     * @param {DateRangePresetKind} kind - 빠른 선택 종류.
     * @param {string} today - 오늘(`YYYY-MM-DD`, 표시 시간대의 날짜).
     * @returns {DateRangeValue | null} 시작·끝. `today` 를 읽을 수 없으면 null.
     */
    static fill(kind: DateRangePresetKind, today: string): DateRangeValue | null {
        const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(today);
        if (!m) return null;
        const y = Number(m[1]);
        const mo = Number(m[2]) - 1;
        const d = Number(m[3]);
        if (kind === 'TODAY') return { from: today, to: today };
        if (kind === 'WEEK') return { from: ymdOf(new Date(Date.UTC(y, mo, d - 7))), to: today };
        const target = mo - MONTHS[kind];
        const lastDay = new Date(Date.UTC(y, target + 1, 0)).getUTCDate();
        return { from: ymdOf(new Date(Date.UTC(y, target, Math.min(d, lastDay)))), to: today };
    }
}
