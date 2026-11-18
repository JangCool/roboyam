import type { SiteLogoHolidayOption } from '~/composables/site/api/useSiteLogoLibraryApi.types';

/**
 * 로고 보이기 규칙이 쓰는 「오늘」과 쉬는 날 표 — 로고 화면(`SiteLogoLibrarySettings`)과 일반 화면의 로고 줄(`SiteLogoSummaryRow`)이 같이 쓴다.
 * 쉬는 날은 휴일 관리의 표(`useHolidayApi().year`, 올해 + 다음 해)를 이름별 날짜로 묶는다. 못 받거나 비면 빈 목록이고 `holidayFallback` 을 켠다(화면이 안내한다).
 * 공유 상태가 아니다 — 부르는 화면마다 따로 받는다(값은 그 화면의 `ref`).
 */
export function useSiteLogoHolidays() {
    const holidayApi = useHolidayApi();
    const time = useDisplayTime();

    /** 오늘(표시 시간대의 달력 날짜 `YYYY-MM-DD`) */
    const today = computed(() => {
        const p = time.value.parts(new Date().toISOString());
        return p ? `${p.y}-${String(p.mo).padStart(2, '0')}-${String(p.d).padStart(2, '0')}` : '';
    });

    const holidays = ref<SiteLogoHolidayOption[]>([]);
    const holidayFallback = ref(false);

    async function loadHolidays(): Promise<void> {
        const thisYear = Number(today.value.slice(0, 4)) || new Date().getFullYear();
        const results = await Promise.allSettled([holidayApi.year(thisYear), holidayApi.year(thisYear + 1)]);
        const byName = new Map<string, string[]>();
        for (const result of results) {
            if (result.status !== 'fulfilled') continue;
            for (const row of result.value.rows) byName.set(row.name, [...(byName.get(row.name) ?? []), row.date]);
        }
        if (byName.size === 0) {
            holidays.value = [];
            holidayFallback.value = true;
            return;
        }
        holidays.value = [...byName.entries()].map(([name, dates]) => ({ name, dates: [...dates].sort() })).sort((a, b) => (a.dates[0] ?? '').localeCompare(b.dates[0] ?? ''));
        holidayFallback.value = false;
    }

    return { today, holidays, holidayFallback, loadHolidays };
}
