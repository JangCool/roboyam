import type { ApiError } from '~/composables/common/useApiClient';
import type {
    DashboardDataType,
    DashboardGadgetDataMap,
    DashboardGadgetQuery,
    DashboardGadgetRequest,
    DashboardGadgetResult,
    DashboardGadgetsResponse,
} from '~/composables/dashboard/api/useDashboardGadgetApi.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardLayout from '~/utils/util-dashboard-layout';

/** 받기의 계기 — 처음 그릴 때(`mount`, 묶어 부를 수 있다) · 새로 고침 단추·자동 새로 고침·설정 변경(`reload`, 그 가젯만) */
type DashboardLoadMode = 'mount' | 'reload';

/**
 * 대시보드 가젯 하나의 데이터 받기 — 처음 그릴 때 한 번, 새로 고침 단추, 자동 새로 고침(설정 `refresh` 초, 0 = 끔)을 한 곳에서 한다. 가젯마다 따로 쓰는 상태라 `useState` 를 두지 않는다.
 *  - 자동 새로 고침은 탭이 안 보이면 멈추고, 다시 보일 때 주기가 지났으면 바로 한 번 받는다(서버 부담 — 배치된 가젯만, 최소 1분).
 *  - 받는 중 다시 부르면 앞의 결과는 버린다. 실패해도 이미 받은 값은 둔다(화면은 값이 없을 때만 오류 화면, 있으면 머리에 경고).
 *  - `403` 은 오류가 아니라 「권한 없음」(`forbidden`). 결과의 `data` 가 null 이면 `disabled` 일 때 「수집 꺼짐」(`disabled` — 통계 수집 설정에서 꺼짐), `unavailable` 일 때 「준비 중」(`unavailable`), 아니면 「권한 없음」.
 *  - `deps` 가 바뀌면(목록 개수·기간 설정) 다시 받는다.
 *
 * @param fetcher - 받기 — 결과 값과 센 시각(`generatedAt`, ISO). 인자는 받기의 계기.
 * @param options.refresh - 자동 새로 고침(초) — 읽는 순간의 값.
 * @param options.deps - 바뀌면 다시 받을 값(설정 칸).
 */
export function useDashboardGadgetData<T>(fetcher: (mode: DashboardLoadMode) => Promise<DashboardGadgetResult<T>>, options: { refresh: () => number; deps?: () => unknown }) {
    const toApiError = useApiErrorOf();

    const data = shallowRef<T | null>(null);
    const at = ref<string | null>(null);
    const loading = ref(false);
    const error = shallowRef<ApiError | null>(null);
    const forbidden = ref(false);
    const unavailable = ref(false);
    const disabled = ref(false);

    let seq = 0;
    let timer: ReturnType<typeof setInterval> | null = null;
    let lastLoaded = 0;

    async function run(mode: DashboardLoadMode): Promise<void> {
        const mine = ++seq;
        loading.value = true;
        error.value = null;
        try {
            const res = await fetcher(mode);
            if (mine !== seq) return;
            if (res.data === null) {
                disabled.value = !!res.disabled;
                unavailable.value = !res.disabled && !!res.unavailable;
                forbidden.value = !res.disabled && !res.unavailable;
                return;
            }
            data.value = res.data;
            at.value = res.generatedAt;
            forbidden.value = false;
            unavailable.value = false;
            disabled.value = false;
        } catch (e) {
            if (mine !== seq) return;
            const failure = toApiError(e);
            if (failure.status === 403) {
                forbidden.value = true;
                unavailable.value = false;
                disabled.value = false;
            } else error.value = failure;
        } finally {
            if (mine === seq) {
                loading.value = false;
                lastLoaded = Date.now();
            }
        }
    }

    /** 이 가젯만 다시 받는다 */
    function reload(): Promise<void> {
        return run('reload');
    }

    function stopTimer(): void {
        if (timer) clearInterval(timer);
        timer = null;
    }

    function startTimer(): void {
        stopTimer();
        const seconds = options.refresh();
        if (!seconds || (typeof document !== 'undefined' && document.hidden)) return;
        timer = setInterval(() => {
            if (!loading.value) void reload();
        }, seconds * 1000);
    }

    function onVisibility(): void {
        if (document.hidden) {
            stopTimer();
            return;
        }
        const seconds = options.refresh();
        if (seconds && Date.now() - lastLoaded >= seconds * 1000 && !loading.value) void reload();
        startTimer();
    }

    watch(options.refresh, startTimer);
    if (options.deps) watch(options.deps, () => void reload());

    onMounted(() => {
        void run('mount');
        startTimer();
        document.addEventListener('visibilitychange', onVisibility);
    });
    onBeforeUnmount(() => {
        stopTimer();
        seq += 1;
        document.removeEventListener('visibilitychange', onVisibility);
    });

    return { data, at, loading, error, forbidden, unavailable, disabled, reload };
}

/* ── 묶어 부르기 ──
 * 같은 틱에 그려진 가젯들(화면을 처음 그릴 때 · 구성을 저장한 뒤)의 첫 받기를 짧게 모아 `GET /api/admin/dashboard/gadgets` 한 번으로 부른다.
 * 종류마다 질의 값(limit·days)이 하나뿐이라, 같은 종류가 다른 질의로 겹치면 다음 묶음으로 나눈다(대개 한 화면 = 한 요청).
 * 묶음 전체가 실패하면(500·네트워크) 그 묶음의 가젯이 모두 같은 오류를 받는다 — 각자 [다시 시도]는 그 종류만 부른다.
 * 묶음에 한 종류만 들었는데 권한이 없으면 서버가 403 을 준다 → 「권한 없음」.
 */

type GadgetApi = ReturnType<typeof useDashboardGadgetApi>;

interface Pending {
    api: GadgetApi;
    request: DashboardGadgetRequest;
    resolve: (res: DashboardGadgetsResponse) => void;
    reject: (e: unknown) => void;
}

/** 모으는 시간(ms) — 같은 틱의 마운트를 넉넉히 덮을 만큼만 */
const BATCH_WAIT_MS = 16;

let pending: Pending[] = [];
let flushTimer: ReturnType<typeof setTimeout> | null = null;

function queryKey(q: DashboardGadgetQuery): string {
    return `${q.limit ?? ''}|${q.days ?? ''}`;
}

function flush(): void {
    flushTimer = null;
    const waiting = pending;
    pending = [];
    const groups: Pending[][] = [];
    for (const p of waiting) {
        const group = groups.find((g) => g.every((o) => o.request.type !== p.request.type || queryKey(o.request.query) === queryKey(p.request.query)));
        if (group) group.push(p);
        else groups.push([p]);
    }
    for (const group of groups) {
        const requests: DashboardGadgetRequest[] = [];
        for (const p of group) if (!requests.some((r) => r.type === p.request.type)) requests.push(p.request);
        group[0]!.api.load(requests).then(
            (res) => group.forEach((p) => p.resolve(res)),
            (e: unknown) => group.forEach((p) => p.reject(e)),
        );
    }
}

/** 이 요청을 다음 묶음에 싣는다 — 묶음 응답 전체를 돌려준다 */
function loadBatched(api: GadgetApi, request: DashboardGadgetRequest): Promise<DashboardGadgetsResponse> {
    return new Promise((resolve, reject) => {
        pending.push({ api, request, resolve, reject });
        flushTimer ??= setTimeout(flush, BATCH_WAIT_MS);
    });
}

/** 묶음 응답에서 한 종류를 꺼낸다 — 값이 null 이면 「수집 꺼짐」(`disabled` 에 있으면 — 서버가 아직 이 칸을 안 주면 빈 배열) · 「준비 중」(`unavailable` 에 있으면)이거나 권한 없음 */
function pick<K extends DashboardDataType>(res: DashboardGadgetsResponse, type: K): DashboardGadgetResult<DashboardGadgetDataMap[K]> {
    const data = (res.gadgets[type] ?? null) as DashboardGadgetDataMap[K] | null;
    return { generatedAt: res.generatedAt, data, unavailable: data === null && (res.unavailable ?? []).includes(type), disabled: data === null && (res.disabled ?? []).includes(type) };
}

/**
 * 가젯 숫자·목록 API(`useDashboardGadgetApi`) 한 종류를 받는다 — 목록 개수(`limit`, 기본 5)·기간(`days`, 기본 7)은 그 가젯 설정에서 읽고, 바뀌면 다시 받는다.
 * 처음 받기는 같은 틱의 다른 가젯과 묶어 한 번에 부르고, 새로 고침·자동 새로 고침·설정 변경은 이 종류만 부른다.
 * `collect` = 이 종류의 데이터를 만드는 「통계 수집」 항목(없으면 undefined) — 틀(`DashboardGadgetFrame`)의 「수집 꺼짐」 링크.
 * @param type - 종류.
 * @param gadget - 가젯(설정을 읽는 순간의 값).
 */
export function useDashboardKindData<K extends DashboardDataType>(type: K, gadget: () => DashboardGadgetItem) {
    const api = useDashboardGadgetApi();
    const spec = UtilDashboardLayout.spec(type);
    const query = (): DashboardGadgetQuery => ({
        limit: spec.options.includes('limit') ? (gadget().config.limit ?? 5) : undefined,
        days: spec.options.includes('days') ? (gadget().config.days ?? 7) : undefined,
    });
    const state = useDashboardGadgetData<DashboardGadgetDataMap[K]>(
        async (mode) => {
            const request: DashboardGadgetRequest = { type, query: query() };
            const res = mode === 'mount' ? await loadBatched(api, request) : await api.load([request]);
            return pick(res, type);
        },
        {
            refresh: () => gadget().config.refresh ?? 0,
            deps: () => queryKey(query()),
        },
    );
    return { ...state, collect: spec.collect };
}
