import UtilTime, { type DisplayValues } from './util-time';

/**
 * 날짜·시각 고르개(PrimeVue `DatePicker`)와 표시 값 사이의 **순수 규칙** — 날짜·시간 형식 패턴, 입력칸 안내 글자, 그리고 시간대 변환.
 * 상태도 요청도 없다(고르개 컴포넌트는 `AppDatePicker`·`AppDateTimePicker`, 표시 값은 `useDisplay`). 이 파일은 `vue`·`~/` 를 쓰지 않는 순수 TypeScript 다.
 *
 * ── 시간대(hamonica3 frontend-guide 10-2) ──
 * PrimeVue `DatePicker` 는 브라우저 `Date` 를 다룬다. `Date` 의 연·월·일·시·분은 **브라우저 시간대** 기준이라, 보는 사람이 고른 표시 시간대가 브라우저와 다르면
 * 아무것도 안 할 때 표에는 09:00 인데 고르개를 열면 10:00 이 뜬다. 그래서 값을 넣고 뺄 때 **벽시계를 옮겨 담는다**:
 *  - `toPickerDate(iso)` — 순간(ISO) → 표시 시간대의 벽시계 숫자(`UtilTime.parts`)를 브라우저 시간대에 그대로 담은 `Date`. **보여 주기 위한 값**이라 서버로 보내지 않는다.
 *  - `fromPickerDate(date)` — 고르개가 준 `Date` 의 연·월·일·시·분을 "표시 시간대의 그 시각"으로 읽어 순간(ISO, `UtilTime.fromWall`)으로. 서버로 보내는 값은 이것이다.
 * 날짜만 고르는 쓰임은 순간이 아니라 **달력 날짜 글자**(`YYYY-MM-DD`, 시간대 변환 없음)를 주고받는다(`toCalendarDate`·`fromCalendarDate`) — 서비스 날짜(통계 기간 등)처럼 시간대와 무관한 날짜용이다.
 * 분 단위까지만 다룬다(초는 버린다).
 */

/** PrimeVue 날짜 패턴 — `yy` 네 자리 연, `mm` 두 자리 월, `dd` 두 자리 일. 우리 코드 이름과 표기가 다르다 */
const DATE_PATTERN: Record<DisplayValues['dateFormat'], string> = { YMD_DASH: 'yy-mm-dd', YMD_DOT: 'yy.mm.dd', DMY_SLASH: 'dd/mm/yy', MDY_SLASH: 'mm/dd/yy' };

/** 입력칸이 비었을 때 보이는 글자 모양 */
const DATE_PLACEHOLDER: Record<DisplayValues['dateFormat'], string> = { YMD_DASH: 'YYYY-MM-DD', YMD_DOT: 'YYYY.MM.DD', DMY_SLASH: 'DD/MM/YYYY', MDY_SLASH: 'MM/DD/YYYY' };

const CALENDAR_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

export default class UtilDatePicker {
    /**
     * PrimeVue `DatePicker` 의 `dateFormat`.
     * @param {DisplayValues} display - 표시 값.
     * @returns {string} `yy.mm.dd` 등. 모르는 날짜 형식이면 시스템 폴백의 형식(`yy-mm-dd`).
     */
    static dateFormat(display: DisplayValues): string {
        return DATE_PATTERN[display.dateFormat] ?? DATE_PATTERN[UtilTime.FALLBACK_DISPLAY.dateFormat];
    }

    /**
     * PrimeVue `DatePicker` 의 `hourFormat`.
     * @param {DisplayValues} display - 표시 값.
     * @returns {'12' | '24'} 12시간 형식이면 `'12'`, 아니면 `'24'`.
     */
    static hourFormat(display: DisplayValues): '12' | '24' {
        return display.timeFormat === 'H12' ? '12' : '24';
    }

    /**
     * 입력칸이 비었을 때 보이는 글자 — 날짜 형식(과 시간 형식)을 따른다.
     * @param {DisplayValues} display - 표시 값.
     * @param {boolean} withTime - 시·분까지 고르는가.
     * @returns {string} `YYYY.MM.DD` · `YYYY.MM.DD HH:mm` · `MM/DD/YYYY hh:mm` 등.
     */
    static placeholder(display: DisplayValues, withTime: boolean): string {
        const date = DATE_PLACEHOLDER[display.dateFormat] ?? DATE_PLACEHOLDER[UtilTime.FALLBACK_DISPLAY.dateFormat];
        if (!withTime) return date;
        return `${date} ${UtilDatePicker.hourFormat(display) === '12' ? 'hh:mm' : 'HH:mm'}`;
    }

    /**
     * 순간(ISO) → 고르개에 넣을 `Date` — 표시 시간대의 벽시계 숫자를 브라우저 시간대에 그대로 담는다. 보여 주기 위한 값이라 서버로 보내지 않는다.
     * @param {string | null | undefined} iso - 순간(ISO-8601 UTC).
     * @param {DisplayValues} display - 표시 값(시간대를 쓴다).
     * @returns {Date | null} 고르개 값. 비었거나 읽을 수 없으면 null.
     */
    static toPickerDate(iso: string | null | undefined, display: DisplayValues): Date | null {
        const p = UtilTime.parts(iso, display);
        if (!p) return null;
        return new Date(p.y, p.mo - 1, p.d, p.h, p.mi);
    }

    /**
     * 고르개가 준 `Date` → 순간(ISO) — 연·월·일·시·분을 "표시 시간대의 그 시각"으로 읽는다. 서머타임 겹침·틈은 `UtilTime.fromWall` 이 정한다.
     * @param {Date | null | undefined} date - 고르개 값.
     * @param {DisplayValues} display - 표시 값(시간대를 쓴다).
     * @returns {string | null} 순간(ISO-8601 UTC). 비었거나 읽을 수 없으면 null.
     */
    static fromPickerDate(date: Date | null | undefined, display: DisplayValues): string | null {
        if (!date || Number.isNaN(date.getTime())) return null;
        return UtilTime.of(display).fromWall(date.getFullYear(), date.getMonth() + 1, date.getDate(), date.getHours(), date.getMinutes());
    }

    /**
     * 달력 날짜 글자(`YYYY-MM-DD`) → 고르개에 넣을 `Date`(그 날 0시, 브라우저 시간대). 시간대 변환은 없다.
     * @param {string | null | undefined} ymd - 달력 날짜 글자.
     * @returns {Date | null} 고르개 값. 비었거나 읽을 수 없는 날짜(2월 30일 등)면 null.
     */
    static toCalendarDate(ymd: string | null | undefined): Date | null {
        const m = CALENDAR_DATE.exec((ymd ?? '').trim());
        if (!m) return null;
        const y = Number(m[1]);
        const mo = Number(m[2]);
        const d = Number(m[3]);
        const date = new Date(y, mo - 1, d);
        return date.getFullYear() === y && date.getMonth() === mo - 1 && date.getDate() === d ? date : null;
    }

    /**
     * 고르개가 준 `Date` → 달력 날짜 글자(`YYYY-MM-DD`) — 달력에서 고른 날 그대로다(시간대 변환 없음).
     * @param {Date | null | undefined} date - 고르개 값.
     * @returns {string} 달력 날짜 글자. 비었으면 빈 글자.
     */
    static fromCalendarDate(date: Date | null | undefined): string {
        if (!date || Number.isNaN(date.getTime())) return '';
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }
}
