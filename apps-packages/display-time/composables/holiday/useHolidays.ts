import UtilHoliday, { type HolidayDay, type HolidayIndex, type HolidayKind, type HolidayMark } from '#layers/display-time/utils/util-holiday';

/**
 * 쉬는 날(공휴일 · 관리자 앱이면 회사 휴무일) — 모든 달력이 같이 쓰는 **해 단위 메모리 캐시**(`useState('holidays-by-year')`).
 *
 * 이 레이어는 서버를 부르지 않는다: 두 앱의 주소가 다르므로(user-web `GET /api/holidays` · admin-web `GET /api/admin/holidays/calendar`)
 * **앱이 받는 함수를 넣는다** — 앱 플러그인이 `useHolidaySource().setLoader((from, to) => api.calendar(from, to))` 를 한 번 부른다(받는 함수는 앱의 API 컴포저블 한 겹).
 *
 * - 달력은 보이는 달의 해를 `ensure([해…])` 로 청한다. 이미 받은 해·받는 중인 해는 다시 부르지 않는다(같은 해 동시 요청은 하나).
 * - 받지 못하면 그 해는 빈 값으로 두고(달력은 표시만 빠진 채 그대로 동작) 1분 동안 다시 부르지 않는다.
 * - 운영자가 회사 휴무일을 더하거나 지우면 `useHolidaySource().invalidate(해)` 로 그 해를 다시 받는다. 로그인한 사람이 바뀌면 앱이 `clear()` 한다.
 * 받는 함수와 진행 중인 요청은 모듈 안에 둔다 — 두 앱 모두 SPA(`ssr:false`)라 브라우저 탭 하나에 하나다. 받은 값만 반응형 상태다.
 *
 * ⚠ 컴포저블은 setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다) — 돌려준 함수는 나중에(이벤트 처리기 등) 불러도 된다.
 */

/** 범위(끝 포함, `YYYY-MM-DD`)의 쉬는 날을 받는 함수 — 앱이 넣는다 */
export type HolidayLoader = (from: string, to: string) => Promise<HolidayDay[]>;

/** 받은 쉬는 날을 해별로 담는 상태 키(`useState`) */
const STATE_KEY = 'holidays-by-year';

/** 받지 못한 해를 다시 부르기까지 */
const RETRY_MS = 60_000;

/** 받을 수 있는 해의 범위(잘못된 값이 서버로 가지 않게) */
const MIN_YEAR = 1900;
const MAX_YEAR = 2200;

const KINDS: readonly HolidayKind[] = ['HOLIDAY', 'COMPANY'];
const YMD = /^\d{4}-\d{2}-\d{2}$/;

/** 모듈 안의 받기 상태 — 반응형이 아니다 */
const runtime = {
    loader: null as HolidayLoader | null,
    /** 해 → 진행 중인 요청 */
    inflight: new Map<number, Promise<void>>(),
    /** 해 → 마지막으로 받지 못한 때(ms) */
    failedAt: new Map<number, number>(),
    /** 받는 함수를 바꾸거나 캐시를 비울 때마다 1씩 — 그 전에 나간 요청의 응답은 버린다 */
    generation: 0,
};

/** 서버 줄 → 쓸 수 있는 줄만(모양이 다른 줄은 버린다) */
function clean(rows: unknown, year: number): HolidayDay[] {
    if (!Array.isArray(rows)) return [];
    const prefix = `${year}-`;
    return rows.filter(
        (row): row is HolidayDay =>
            !!row &&
            typeof row === 'object' &&
            typeof (row as HolidayDay).date === 'string' &&
            YMD.test((row as HolidayDay).date) &&
            (row as HolidayDay).date.startsWith(prefix) &&
            typeof (row as HolidayDay).name === 'string' &&
            KINDS.includes((row as HolidayDay).kind),
    ).map((row) => ({ date: row.date, name: row.name, kind: row.kind }));
}

function useByYear() {
    return useState<Record<string, HolidayDay[]>>(STATE_KEY, () => ({}));
}

/**
 * 쉬는 날을 읽고 청한다 — 달력 컴포넌트가 쓴다.
 * @returns `index` 날짜별로 묶은 줄들(계산값) · `markOf(ymd)` 칸 표시 · `offDates` 쉬는 날 날짜 글자 목록(계산값) · `ensure(years)` 그 해들을 받아 둔다.
 */
export function useHolidays() {
    const byYear = useByYear();
    const index = computed<HolidayIndex>(() => UtilHoliday.index(Object.values(byYear.value).flat()));
    const offDates = computed<string[]>(() => UtilHoliday.offDates(index.value));

    function markOf(ymd: string): HolidayMark {
        return UtilHoliday.mark(index.value, ymd);
    }

    function ensure(years: readonly number[]): void {
        const loader = runtime.loader;
        if (!loader) return;
        for (const year of new Set(years)) {
            if (!Number.isInteger(year) || year < MIN_YEAR || year > MAX_YEAR) continue;
            if (Object.prototype.hasOwnProperty.call(byYear.value, String(year))) continue;
            if (runtime.inflight.has(year)) continue;
            const failed = runtime.failedAt.get(year);
            if (failed !== undefined && Date.now() - failed < RETRY_MS) continue;

            const generation = runtime.generation;
            const { from, to } = UtilHoliday.yearRange(year);
            const request = Promise.resolve()
                .then(() => loader(from, to))
                .then((rows) => {
                    if (generation !== runtime.generation) return;
                    runtime.failedAt.delete(year);
                    byYear.value = { ...byYear.value, [String(year)]: clean(rows, year) };
                })
                .catch(() => {
                    // 받지 못해도 달력은 그대로 — 표시만 빠진다
                    if (generation === runtime.generation) runtime.failedAt.set(year, Date.now());
                })
                .finally(() => {
                    if (runtime.inflight.get(year) === request) runtime.inflight.delete(year);
                });
            runtime.inflight.set(year, request);
        }
    }

    return { index, offDates, markOf, ensure };
}

/**
 * 쉬는 날을 어디서 받을지 정하고 캐시를 비운다 — 앱(플러그인·휴일 관리 화면)이 쓴다.
 * @returns `setLoader(fn)` 받는 함수를 넣는다(캐시를 비운다) · `invalidate(year)` 그 해를 버리고 다시 받는다 · `clear()` 모두 비운다(로그인한 사람이 바뀔 때).
 */
export function useHolidaySource() {
    const byYear = useByYear();
    const { ensure } = useHolidays();

    function reset(): void {
        runtime.generation += 1;
        runtime.inflight.clear();
        runtime.failedAt.clear();
    }

    function clear(): void {
        reset();
        byYear.value = {};
    }

    function setLoader(loader: HolidayLoader | null): void {
        runtime.loader = loader;
        clear();
    }

    function invalidate(year: number): void {
        const keep = Object.keys(byYear.value).filter((key) => key !== String(year));
        reset();
        byYear.value = Object.fromEntries(keep.map((key) => [key, byYear.value[key] ?? []]));
        ensure([year]);
    }

    return { setLoader, invalidate, clear };
}
