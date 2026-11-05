import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { HolidayMark } from './util-holiday';

/**
 * 쉬는 날 칸의 **계산이 낀 문구** — 마우스 올림 글자(`title`)와 접근성 이름(`aria-label`). 규칙은 `UtilHoliday`, 문구 키는 레이어의 `holidayMark.*`.
 * 컴포넌트는 템플릿·`computed`·pt 함수 안에서 불러 언어가 바뀌면 다시 계산되게 한다.
 */
export default class UtilHolidayMessages {
    /**
     * 칸 이름 글자 — 공휴일은 이름 그대로, 회사 휴무일은 「(회사 휴무일)」을 붙여 「 · 」로 잇는다.
     * @param {Translate} t - 번역 함수.
     * @param {HolidayMark} mark - 칸 표시.
     * @returns {string} 쉬는 날이 아니면 ''.
     */
    static names(t: Translate, mark: HolidayMark): string {
        const parts = [...mark.holidayNames, ...mark.companyNames.filter((n) => !mark.holidayNames.includes(n)).map((name) => t('holidayMark.company', { name }))];
        if (parts.length === 0 && mark.company) parts.push(t('holidayMark.companyOnly'));
        if (parts.length === 0 && mark.holiday) parts.push(t('holidayMark.holidayOnly'));
        return parts.join(' · ');
    }

    /**
     * 마우스 올림·길게 누름 글자.
     * @param {Translate} t - 번역 함수.
     * @param {HolidayMark} mark - 칸 표시.
     * @returns {string | undefined} 쉬는 날이 아니면 undefined(속성을 달지 않는다).
     */
    static title(t: Translate, mark: HolidayMark): string | undefined {
        const text = UtilHolidayMessages.names(t, mark);
        return text === '' ? undefined : text;
    }

    /**
     * 접근성 이름 — 「2026년 9월 25일, 추석」.
     * @param {Translate} t - 번역 함수.
     * @param {number} year - 연.
     * @param {number} month1 - 월(1 = 1월).
     * @param {number} day - 일.
     * @param {HolidayMark} mark - 칸 표시.
     * @returns {string | undefined} 쉬는 날이 아니면 undefined(달력의 기본 이름을 그대로 둔다).
     */
    static ariaLabel(t: Translate, year: number, month1: number, day: number, mark: HolidayMark): string | undefined {
        const names = UtilHolidayMessages.names(t, mark);
        if (names === '') return undefined;
        return t('holidayMark.cellLabel', { year, month: month1, day, names });
    }
}
