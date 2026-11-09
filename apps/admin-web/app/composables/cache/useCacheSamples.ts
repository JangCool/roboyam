import type { CacheSampleKind, CacheUsageView } from '~/composables/cache/api/useCacheAdminApi.types';
import UtilCache from '~/utils/util-cache';
import UtilPointShop from '~/utils/util-point-shop';

/** 예시 하나의 상태 — 아직 안 물어봄 · 고르는 중 · 있음 · 없음(데이터 없음) · 못 고름(권한 없음·오류) */
export type CacheSampleStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

export interface CacheSampleState {
    status: CacheSampleStatus;
    /** 기존 관리자 API 로 받은 예시 번호들(최신 것부터) — 「다른 예시」는 다음 것으로 */
    ids: string[];
    /** 쓰는 예시 차례 — -1 이면 서버가 준 `sampleId`(없으면 받은 첫 예시) */
    index: number;
    /** 운영자가 직접 넣은 번호 — 있으면 이것이 먼저 */
    manual: string | null;
}

/** 한 번에 받아 둘 예시 수 — 「다른 예시」로 넘겨 볼 만큼 */
const SAMPLE_SIZE = 10;

/**
 * 캐시 미리보기의 예시 고르기 — 글·상품 하나를 열어야 보이는 곳(쓰이는 곳의 `sample` 규칙)에 넣을 번호.
 * **먼저 서버가 준 `sample.sampleId`** 를 쓴다. 서버가 못 골랐거나(null) 운영자가 「다른 예시」를 누를 때만 기존 관리자 API 로 최신 예시들을 받는다:
 *   - 핫딜 글(`HOTDEAL_POST`): `useHotdealApi().list({ view: 'ACTIVE', page: 0, size })` — 진행 중인 최근 글(최신 등록순)의 `postId` · 권한 `hotdeal.read`
 *   - 포인트 상점 상품(`POINT_SHOP_ITEM`): `usePointShopAdminApi().items({ excludeKind: 'PROFILE_AVATAR' })` — `active` 이고 판매 중(`UtilPointShop.saleState` = `ON_SALE`)인 품목의 `id` · 권한 `pointshop.read`
 *   - 공동구매(`GROUP_BUY`): `useGroupBuyAdminApi().list({ status: 'RECRUITING', page: 0, size })` — 모집 중인 공구의 `id` · 권한 `commerce.product.read`
 * 예시를 못 찾으면(데이터 없음·권한 없음·오류) 줄에 「예시가 없습니다 — 번호를 직접 넣어 보기」를 보인다(`status`). 화면 하나가 만들어 대화상자·서랍에 같이 넘긴다.
 * 돌려주는 값은 `reactive` 다 — 템플릿에서 `.value` 없이 읽는다.
 */
export function useCacheSamples() {
    const hotdeals = useHotdealApi();
    const pointShop = usePointShopAdminApi();
    const groupBuys = useGroupBuyAdminApi();

    const blank = (): CacheSampleState => ({ status: 'idle', ids: [], index: -1, manual: null });
    const state = reactive<Record<CacheSampleKind, CacheSampleState>>({ HOTDEAL_POST: blank(), POINT_SHOP_ITEM: blank(), GROUP_BUY: blank() });
    const pending = new Map<CacheSampleKind, Promise<void>>();

    async function fetchIds(kind: CacheSampleKind): Promise<string[]> {
        if (kind === 'HOTDEAL_POST') return (await hotdeals.list({ view: 'ACTIVE', page: 0, size: SAMPLE_SIZE })).items.map((row) => row.postId);
        if (kind === 'POINT_SHOP_ITEM') {
            const now = Date.now();
            return (await pointShop.items({ excludeKind: 'PROFILE_AVATAR' })).items
                .filter((item) => item.active && UtilPointShop.saleState(item, now) === 'ON_SALE')
                .slice(0, SAMPLE_SIZE)
                .map((item) => item.id);
        }
        return (await groupBuys.list({ status: 'RECRUITING', page: 0, size: SAMPLE_SIZE })).items.map((row) => row.id);
    }

    /** 처음 물을 때 한 번 고른다(같은 종류를 동시에 물으면 한 번만) */
    function ensure(kind: CacheSampleKind): Promise<void> {
        const s = state[kind];
        if (s.status !== 'idle' && s.status !== 'error') return pending.get(kind) ?? Promise.resolve();
        const run = (async () => {
            s.status = 'loading';
            try {
                s.ids = await fetchIds(kind);
                s.status = s.ids.length ? 'ready' : 'empty';
            } catch {
                // 권한 없음(403)·오류 — 번호를 직접 넣어 볼 수 있다
                s.ids = [];
                s.status = 'error';
            } finally {
                pending.delete(kind);
            }
        })();
        pending.set(kind, run);
        return run;
    }

    /** 이 줄에 쓸 예시 번호 — 직접 넣은 번호 → 「다른 예시」로 고른 것 → 서버가 준 `sampleId` → 받은 첫 예시. 없으면 null */
    function idFor(usage: CacheUsageView): string | null {
        if (!usage.sample) return null;
        const s = state[usage.sample.kind];
        return s.manual ?? (s.index >= 0 ? s.ids[s.index] : undefined) ?? usage.sample.sampleId ?? s.ids[0] ?? null;
    }

    /** 줄의 미리보기 경로 — 고정 경로, 또는 예시 경로 틀 + 번호(서버가 준 `sampleId` → 직접 넣은 번호 → 고른 예시). 열 수 없으면 null */
    function pathFor(usage: CacheUsageView): string | null {
        if (usage.path) return usage.path;
        if (!usage.sample) return null;
        const id = idFor(usage);
        return id ? UtilCache.fillTemplate(usage.sample.template, id) : null;
    }

    /** 줄의 경로를 정한다 — 예시가 필요하면 먼저 고른다 */
    async function resolve(usage: CacheUsageView): Promise<string | null> {
        if (usage.sample && !usage.sample.sampleId) await ensure(usage.sample.kind);
        return pathFor(usage);
    }

    /** 「다른 예시」 — 직접 넣은 번호를 풀고, 기존 관리자 API 로 받은 예시(처음이면 받는다) 중 다음 것으로 */
    async function next(kind: CacheSampleKind): Promise<void> {
        await ensure(kind);
        const s = state[kind];
        s.manual = null;
        if (s.ids.length) s.index = (s.index + 1) % s.ids.length;
    }

    /** 번호를 직접 넣는다(빈 값이면 푼다) */
    function setManual(kind: CacheSampleKind, id: string | null): void {
        state[kind].manual = id && UtilCache.validSampleId(id) ? id.trim() : null;
    }

    return reactive({ state, ensure, idFor, pathFor, resolve, next, setManual });
}

export type CacheSamples = ReturnType<typeof useCacheSamples>;
