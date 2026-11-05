/**
 * 달력 날짜 칸의 쉬는 날 표시 — **순수 규칙**(상태도 요청도 없다. 받기·캐시는 `useHolidays`, 문구는 `util-holiday-messages`).
 * 모든 달력(`AppDatePicker`·`AppDateTimePicker`·앱이 직접 그린 월 달력)이 이 한 곳의 규칙으로 칸을 칠한다.
 *
 * - 쉬는 날 한 줄(`HolidayDay`)은 서버가 준 모양 그대로다: `{ date: 'YYYY-MM-DD', name, kind }` — `HOLIDAY` 공휴일 · `COMPANY` 회사 휴무일(관리자 앱만 받는다).
 *   같은 날 줄이 둘 이상일 수 있다(어린이날 + 부처님오신날) — 이름은 「 · 」로 잇는다.
 * - 칸 표시(`HolidayMark`): 공휴일이면 빨강(`app-holiday--holiday`), 공휴일이 아닌 회사 휴무일이면 다른 색(`app-holiday--company`). 클래스 규칙은 레이어의 `assets/holiday.css`.
 * 이 파일은 `vue`·`~/` 를 쓰지 않는 순수 TypeScript 다.
 */

/** 쉬는 날 종류 — `HOLIDAY` 공휴일(공공데이터·기본값) · `COMPANY` 회사 휴무일(운영자가 넣은 날, 관리자 앱만) */
export type HolidayKind = 'HOLIDAY' | 'COMPANY';

/** 서버가 준 쉬는 날 한 줄 */
export interface HolidayDay {
    /** 달력 날짜 글자 `YYYY-MM-DD` */
    date: string;
    name: string;
    kind: HolidayKind;
}

/** 날짜 칸 하나의 표시 */
export interface HolidayMark {
    /** 공휴일인가 */
    holiday: boolean;
    /** 회사 휴무일인가 */
    company: boolean;
    /** 그날의 이름들(공휴일 먼저, 받은 차례, 겹침 없음) */
    names: string[];
    /** 공휴일 이름들 */
    holidayNames: string[];
    /** 회사 휴무일 이름들 */
    companyNames: string[];
    /** 이름을 「 · 」로 이은 글자(쉬는 날이 아니면 '') */
    label: string;
}

/** 날짜 글자 → 그날의 줄들 */
export type HolidayIndex = ReadonlyMap<string, readonly HolidayDay[]>;

/** 쉬는 날이 아닌 칸 */
const NONE: HolidayMark = Object.freeze({ holiday: false, company: false, names: [], holidayNames: [], companyNames: [], label: '' }) as HolidayMark;

/** 같은 날 이름을 잇는 글자 */
const NAME_JOIN = ' · ';

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

export default class UtilHoliday {
    /** 공휴일 칸 클래스(빨간 글씨) */
    static readonly HOLIDAY_CLASS = 'app-holiday--holiday';
    /** 회사 휴무일 칸 클래스(공휴일과 다른 색) */
    static readonly COMPANY_CLASS = 'app-holiday--company';

    /**
     * 연·월(0부터)·일 → 달력 날짜 글자. PrimeVue 날짜 칸(`{ day, month, year }` — month 는 0부터)을 그대로 넣는다.
     * @param {number} year - 연.
     * @param {number} month0 - 월(0 = 1월).
     * @param {number} day - 일.
     * @returns {string} `YYYY-MM-DD`.
     */
    static ymd(year: number, month0: number, day: number): string {
        return `${year}-${pad(month0 + 1)}-${pad(day)}`;
    }

    /**
     * 받은 줄들을 날짜별로 묶는다.
     * @param {readonly HolidayDay[]} days - 쉬는 날 줄들.
     * @returns {HolidayIndex} 날짜 글자 → 그날의 줄들(받은 차례).
     */
    static index(days: readonly HolidayDay[]): HolidayIndex {
        const out = new Map<string, HolidayDay[]>();
        for (const day of days) {
            const list = out.get(day.date);
            if (list) list.push(day);
            else out.set(day.date, [day]);
        }
        return out;
    }

    /**
     * 날짜 칸 하나의 표시.
     * @param {HolidayIndex} index - 날짜별로 묶은 줄들.
     * @param {string} ymd - 달력 날짜 글자.
     * @returns {HolidayMark} 쉬는 날이 아니면 모두 빈 값.
     */
    static mark(index: HolidayIndex, ymd: string): HolidayMark {
        const list = index.get(ymd);
        if (!list || list.length === 0) return NONE;
        const unique = (names: string[]): string[] => names.filter((n, i, all) => n.trim() !== '' && all.indexOf(n) === i);
        const holidayNames = unique(list.filter((d) => d.kind === 'HOLIDAY').map((d) => d.name));
        const companyNames = unique(list.filter((d) => d.kind === 'COMPANY').map((d) => d.name));
        const names = unique([...holidayNames, ...companyNames]);
        return {
            holiday: list.some((d) => d.kind === 'HOLIDAY'),
            company: list.some((d) => d.kind === 'COMPANY'),
            names,
            holidayNames,
            companyNames,
            label: names.join(NAME_JOIN),
        };
    }

    /**
     * 칸에 붙일 클래스 — 공휴일이 회사 휴무일보다 앞선다(둘 다면 빨강).
     * @param {HolidayMark} mark - 칸 표시.
     * @returns {string} 클래스 이름. 쉬는 날이 아니면 ''.
     */
    static cellClass(mark: HolidayMark): string {
        if (mark.holiday) return UtilHoliday.HOLIDAY_CLASS;
        if (mark.company) return UtilHoliday.COMPANY_CLASS;
        return '';
    }

    /**
     * 쉬는 날인가(공휴일 또는 회사 휴무일) — 「쉬는 날 고르지 못하게」(`disableHolidays`)가 쓴다.
     * @param {HolidayMark} mark - 칸 표시.
     * @returns {boolean} 쉬는 날이면 true.
     */
    static isOff(mark: HolidayMark): boolean {
        return mark.holiday || mark.company;
    }

    /**
     * 쉬는 날 날짜 글자 목록(겹침 없이, 날짜 차례).
     * @param {HolidayIndex} index - 날짜별로 묶은 줄들.
     * @returns {string[]} `YYYY-MM-DD` 목록.
     */
    static offDates(index: HolidayIndex): string[] {
        return [...index.keys()].filter((ymd) => UtilHoliday.isOff(UtilHoliday.mark(index, ymd))).sort();
    }

    /**
     * 한 달을 보일 때 받아야 할 해 — 그 해, 1월이면 앞 해, 12월이면 다음 해도(달력은 앞뒤 달의 날도 그린다).
     * @param {number} year - 연.
     * @param {number} month1 - 월(1 = 1월).
     * @returns {number[]} 해 목록.
     */
    static yearsForMonth(year: number, month1: number): number[] {
        if (!Number.isInteger(year) || !Number.isInteger(month1)) return [];
        if (month1 <= 1) return [year - 1, year];
        if (month1 >= 12) return [year, year + 1];
        return [year];
    }

    /**
     * 달력 날짜 글자 → 그 날이 든 달을 보일 때 받아야 할 해.
     * @param {string | null | undefined} ymd - 달력 날짜 글자(`YYYY-MM-DD` 로 시작하면 된다 — ISO 시각의 앞부분도 받는다).
     * @returns {number[]} 해 목록. 읽을 수 없으면 빈 목록.
     */
    static yearsForDate(ymd: string | null | undefined): number[] {
        const m = /^(\d{4})-(\d{2})/.exec((ymd ?? '').trim());
        return m ? UtilHoliday.yearsForMonth(Number(m[1]), Number(m[2])) : [];
    }

    /**
     * 해 하나를 받을 범위(그 해 1월 1일 ~ 12월 31일).
     * @param {number} year - 연.
     * @returns {{ from: string; to: string }} 끝 포함 범위.
     */
    static yearRange(year: number): { from: string; to: string } {
        return { from: `${year}-01-01`, to: `${year}-12-31` };
    }
}
