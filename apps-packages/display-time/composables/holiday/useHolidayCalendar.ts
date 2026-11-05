import type { DatePickerMonthChangeEvent, DatePickerPassThroughMethodOptions, DatePickerYearChangeEvent } from 'primevue/datepicker';
import UtilDatePicker from '#layers/display-time/utils/util-date-picker';
import UtilHoliday from '#layers/display-time/utils/util-holiday';
import UtilHolidayMessages from '#layers/display-time/utils/util-holiday-messages';

/**
 * PrimeVue `DatePicker` 에 쉬는 날 표시를 붙이는 한 겹 — `AppDatePicker`·`AppDateTimePicker`·앱이 직접 쓰는 `DatePicker`(inline 달력 등)가 같이 쓴다.
 *  - `pt` — 날짜 칸(`dayCell`)에 `title`(이름)·`aria-label`(「2026년 9월 25일, 추석」), 날짜 글자(`day`)에 색 클래스(`UtilHoliday.cellClass`). DatePicker 의 `:pt` 에 펼쳐 넣는다.
 *  - `onShow` · `onMonthChange` · `onYearChange` — 보이는 달의 해를 받아 둔다(`@show`·`@month-change`·`@year-change` 에 건다). inline 달력은 `immediate` 로 처음에 받는다.
 *  - `offDates` — 쉬는 날 `Date` 목록(PrimeVue `disabledDates` 용, 「쉬는 날 고르지 못하게」).
 * 받지 못하면 표시만 빠지고 달력은 그대로다(`useHolidays`).
 */

export interface HolidayCalendarOptions {
    /** 달력을 열 때 보일 만한 날들(값·최소·최대 등) — 그 달의 해를 받는다. 오늘은 늘 더한다 */
    anchors?: () => readonly (Date | null | undefined)[];
    /** 처음 그릴 때 바로 받는다(inline 달력 — 「열기」가 없다) */
    immediate?: boolean;
}

export function useHolidayCalendar(options: HolidayCalendarOptions = {}) {
    const { t } = useI18n();
    const holidays = useHolidays();

    function yearsOf(date: Date | null | undefined): number[] {
        if (!date || Number.isNaN(date.getTime())) return [];
        return UtilHoliday.yearsForMonth(date.getFullYear(), date.getMonth() + 1);
    }

    function onShow(): void {
        const dates = [...(options.anchors?.() ?? []), new Date()];
        holidays.ensure(dates.flatMap(yearsOf));
    }

    function onMonthChange(event: DatePickerMonthChangeEvent | DatePickerYearChangeEvent): void {
        holidays.ensure(UtilHoliday.yearsForMonth(event.year, event.month));
    }

    /** DatePicker `pt` 의 날짜 칸 두 곳 — 칸 표시는 그릴 때마다 상태에서 읽으므로 받은 뒤 저절로 다시 그려진다 */
    const pt = {
        dayCell: (opts: DatePickerPassThroughMethodOptions) => {
            const date = opts.context?.date;
            if (!date) return {};
            const mark = holidays.markOf(UtilHoliday.ymd(date.year, date.month, date.day));
            if (!UtilHoliday.isOff(mark)) return {};
            return { title: UtilHolidayMessages.title(t, mark), 'aria-label': UtilHolidayMessages.ariaLabel(t, date.year, date.month + 1, date.day, mark) };
        },
        day: (opts: DatePickerPassThroughMethodOptions) => {
            const date = opts.context?.date;
            if (!date) return {};
            const cls = UtilHoliday.cellClass(holidays.markOf(UtilHoliday.ymd(date.year, date.month, date.day)));
            return cls ? { class: cls } : {};
        },
    };

    /** 쉬는 날 `Date` 목록(그 날 0시, 브라우저 시간대 — 달력 날짜) */
    const offDates = computed<Date[]>(() => holidays.offDates.value.map((ymd) => UtilDatePicker.toCalendarDate(ymd)).filter((d): d is Date => d !== null));

    if (options.immediate) onMounted(onShow);

    return { pt, offDates, onShow, onMonthChange, onYearChange: onMonthChange };
}
