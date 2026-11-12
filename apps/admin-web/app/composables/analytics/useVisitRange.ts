import type { RouteLocationRaw } from 'vue-router';
import type { VisitRange } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilVisitAnalytics, { type VisitRangePreset } from '~/utils/util-visit-analytics';

/** 이 브라우저에 기억하는 마지막 기간 — 빠른 선택이면 그 종류(내일 열어도 「최근 7일」), 직접 고른 기간이면 날짜 */
const STORE_KEY = 'irobit:admin:visit-analytics:range';

type Remembered = { preset: VisitRangePreset } | { from: string; to: string };

function readRemembered(): VisitRange | null {
    if (!import.meta.client) return null;
    try {
        const raw = window.localStorage.getItem(STORE_KEY);
        if (!raw) return null;
        const v = JSON.parse(raw) as Partial<{ preset: string; from: string; to: string }>;
        if (v.preset && (UtilVisitAnalytics.PRESETS as readonly string[]).includes(v.preset)) return UtilVisitAnalytics.presetRange(v.preset as VisitRangePreset);
        if (typeof v.from === 'string' && typeof v.to === 'string') return UtilVisitAnalytics.filterOf({ from: v.from, to: v.to });
    } catch {
        /* 못 읽으면 기본 기간 */
    }
    return null;
}

function remember(range: VisitRange): void {
    if (!import.meta.client) return;
    const preset = UtilVisitAnalytics.presetOf(range);
    const value: Remembered = preset ? { preset } : { from: range.from, to: range.to };
    try {
        window.localStorage.setItem(STORE_KEY, JSON.stringify(value));
    } catch {
        /* 못 적으면 이번 방문에서만 주소로 이어 간다 */
    }
}

/**
 * 「방문자 통계」 쪽들이 같이 쓰는 기간 — **주소 쿼리 `from`·`to` 가 정본**이고, 쪽을 옮겨도 이어지게:
 *   1) 쪽 안 링크(「자세히 →」·서랍의 「이동 흐름에서 보기」)는 `linkTo` 로 지금 기간을 실어 보낸다.
 *   2) 메뉴처럼 기간 없이 들어오면 `ensureQuery` 가 이 브라우저에 기억한 마지막 기간(빠른 선택은 종류로 — 상대 기간)을 주소에 적는다. 없으면 기본(최근 7일).
 * 고른 기간은 `apply` 가 기억한다(localStorage — 막혀 있으면 조용히 건너뛴다). 요청 세부는 모른다.
 */
export function useVisitRange() {
    const route = useRoute();
    const router = useRouter();

    const range = computed<VisitRange>(() => UtilVisitAnalytics.filterOf(route.query));
    /** 길이(90일) 때문에 시작 날짜를 당겼나 — 안내 한 줄 */
    const clamped = useState('visit-range-clamped', () => false);

    /** 기간을 바꾼다(맞바꾸기·오늘까지·90일 자르기) — 주소에 적고 기억한다 */
    function apply(next: VisitRange): void {
        const { range: fixed, clamped: cut } = UtilVisitAnalytics.normalize(next.from, next.to);
        clamped.value = cut;
        remember(fixed);
        if (fixed.from === range.value.from && fixed.to === range.value.to && route.query.from) return;
        void router.replace({ query: { ...route.query, ...UtilVisitAnalytics.queryOf(fixed) } });
    }

    /**
     * 쪽에 들어왔을 때 — 주소에 기간이 없으면 기억한 기간(없으면 기본)을, 있으면 고친 값을 주소에 적는다.
     * @returns 주소를 바꿨으면 그 이동이 끝날 때까지 기다린다.
     */
    async function ensureQuery(): Promise<void> {
        const from = typeof route.query.from === 'string' ? route.query.from : '';
        const to = typeof route.query.to === 'string' ? route.query.to : '';
        let target: VisitRange;
        if (from && to) {
            const n = UtilVisitAnalytics.normalize(from, to);
            clamped.value = n.clamped;
            target = range.value;
        } else {
            clamped.value = false;
            target = readRemembered() ?? range.value;
        }
        remember(target);
        if (route.query.from === target.from && route.query.to === target.to) return;
        await router.replace({ query: { ...route.query, ...UtilVisitAnalytics.queryOf(target) } });
    }

    /**
     * 다른 쪽으로 가는 주소 — 지금 기간을 싣는다.
     * @param {string} path - 쪽 주소.
     * @param {Record<string, string>} [extra] - 더 실을 쿼리(`path` 등).
     */
    function linkTo(path: string, extra: Record<string, string> = {}): RouteLocationRaw {
        return { path, query: { ...UtilVisitAnalytics.queryOf(range.value), ...extra } };
    }

    /**
     * 쪽의 읽기 시점 — 들어오면(주소에 기간을 적은 뒤) 한 번, 기간이 바뀌면 다시 `load`. 볼 수 없으면(`allowed` false) 부르지 않는다.
     * setup 에서 부른다(안에서 `onMounted`·`watch` 를 건다).
     */
    function onRange(load: () => void, allowed: Ref<boolean> | ComputedRef<boolean>): void {
        const self = route.path;
        let ready = false;
        watch(
            () => `${range.value.from}|${range.value.to}`,
            () => {
                if (!ready || route.path !== self || !allowed.value) return;
                load();
            },
        );
        onMounted(async () => {
            if (!allowed.value) return;
            await ensureQuery();
            ready = true;
            load();
        });
    }

    return { range, clamped, apply, ensureQuery, linkTo, onRange };
}

/**
 * 「방문자 통계」 쪽의 읽기 한 칸 — 값 · 읽는 중 · 오류와, 늦게 온 앞 요청을 버리는 순번. 요청은 넘긴 함수가 한다(요청 세부는 모른다).
 * @param fetcher - 읽는 함수.
 */
export function useVisitLoad<T, A extends unknown[] = []>(fetcher: (...args: A) => Promise<T>) {
    const toApiError = useApiErrorOf();
    const data = ref<T | null>(null) as Ref<T | null>;
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    let seq = 0;

    /** 읽는다 — 실패하면 값을 비우고 오류. 성공하면 받은 값 */
    async function load(...args: A): Promise<T | null> {
        const mine = ++seq;
        loading.value = true;
        error.value = null;
        try {
            const found = await fetcher(...args);
            if (mine !== seq) return null;
            data.value = found;
            return found;
        } catch (e) {
            if (mine !== seq) return null;
            data.value = null;
            error.value = toApiError(e);
            return null;
        } finally {
            if (mine === seq) loading.value = false;
        }
    }

    /** 값을 비운다(수집이 꺼진 구획) — 늦게 오는 앞 요청도 버린다 */
    function clear(): void {
        seq++;
        data.value = null;
        error.value = null;
        loading.value = false;
    }

    return { data, loading, error, load, clear };
}
