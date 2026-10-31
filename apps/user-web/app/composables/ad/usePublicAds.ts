import type { AdImpressionItem, PublicAd, PublicAdPlacements, PublicAdType } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import UtilAd from '~/utils/util-ad';

/**
 * 공개 광고의 공유 상태 — 유형별 노출 후보 사본 · 가젯이 아닌 자리 설정(글 중간) · 노출 모아 보내기 · 클릭. 서버 요청 세부는 `usePublicAdApi`.
 * 홈 가젯·가젯 페이지·게시판 레이아웃(`useAdGadgets`)·글 중간·핫딜 노출이 **같은 길**로 후보를 받는다(광고는 홈 응답과 따로 사본 캐시라서).
 *
 *  - 후보 사본: 키(유형, 핫딜 노출은 `유형:게시판[:말머리]`) → 후보. `undefined` = 아직 안 받음(자리를 비율 틀로 잡는다), `[]` = 없음(자리를 접는다).
 *    한 번 받은 키는 **20초**(서버 사본과 같은 수명) 동안 다시 묻지 않는다 — 화면을 옮겨 다시 부르면 그 뒤에 새로 받는다(내림·되돌리기 반영 최대 20초 + α).
 *    같은 틱에 여러 자리가 부르면 한 번에 묻는다(`GET /api/ads?types=A,B,…`). 받기 실패는 오류 화면이 아니라 「광고 없음」(자리를 조용히 접는다 — 본문을 막지 않는다).
 *  - 노출: 자리 컴포넌트(`AdSlot`·`AdTextLines`·`AdHotdealPlacement`)가 화면에 절반 이상 보였을 때 한 번 부른다 → 1초 모아 `POST /api/ads/impressions`, 창을 숨기면 남은 것을 `sendBeacon`.
 *  - 클릭: 링크를 누를 때 `sendBeacon`(이동은 막지 않는다 — `href` 는 광고 주소 그대로).
 * ⚠ setup 에서 부른다(`await` 뒤·콜백 안에서는 Nuxt 문맥이 없다).
 */

/** 후보 사본 수명 — 서버 사본(20초)과 같다 */
const TTL_MS = 20_000;
/** 노출 모아 보내기 간격 */
const FLUSH_MS = 1_000;

interface PublicAdsState {
    ads: Record<string, PublicAd[]>;
    loadedAt: Record<string, number>;
    /** undefined = 아직 안 물어봄, null = 못 받음(자리 없음으로 본다) */
    placements: PublicAdPlacements | null | undefined;
    placementsAt: number;
}

type PublicAdApi = ReturnType<typeof usePublicAdApi>;

/* 모듈 상태 — 화면이 아니라 요청 묶기·노출 대기열이라 `useState` 에 두지 않는다(SPA 한 창에 하나) */
let pending = new Set<PublicAdType>();
let batch: Promise<void> | null = null;
const inflight = new Map<string, Promise<void>>();
let placementsRun: Promise<void> | null = null;
let queue: AdImpressionItem[] = [];
let timer: ReturnType<typeof setTimeout> | null = null;
let tracker: PublicAdApi | null = null;
let hideHooked = false;

function flush(): void {
    timer = null;
    const items = queue;
    queue = [];
    if (items.length && tracker) tracker.impressions({ items }).catch(() => undefined);
}

/** 창을 숨기면(탭 전환·닫기) 남은 노출을 `sendBeacon` 으로 — 한 번만 건다 */
function hookHide(): void {
    if (hideHooked || typeof document === 'undefined') return;
    hideHooked = true;
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'hidden' || !queue.length || !tracker) return;
        if (timer) clearTimeout(timer);
        timer = null;
        const items = queue;
        queue = [];
        if (!tracker.impressionsBeacon({ items })) tracker.impressions({ items }).catch(() => undefined);
    });
}

/** 핫딜 노출 사본 키 — 홈 상단은 게시판마다, 말머리 상단은 게시판·말머리마다 */
function hotdealKey(board: string, prefix: string): string {
    return prefix ? `HOTDEAL_CATEGORY:${board}:${prefix}` : `HOTDEAL_TOP:${board}`;
}

export function usePublicAds() {
    const api = usePublicAdApi();
    const state = useState<PublicAdsState>('public-ads', () => ({ ads: {}, loadedAt: {}, placements: undefined, placementsAt: 0 }));

    function fresh(key: string): boolean {
        const at = state.value.loadedAt[key];
        return at !== undefined && Date.now() - at < TTL_MS;
    }

    function store(entries: readonly [string, PublicAd[]][]): void {
        const now = Date.now();
        const ads = { ...state.value.ads };
        const loadedAt = { ...state.value.loadedAt };
        for (const [key, list] of entries) {
            ads[key] = list;
            loadedAt[key] = now;
        }
        state.value = { ...state.value, ads, loadedAt };
    }

    /** 한 번 묻고 받은 후보를 키별로 넣는다 — 실패는 「없음」으로(자리를 접는다) */
    async function fetchInto(keys: readonly { key: string; type: PublicAdType }[], query: Parameters<PublicAdApi['list']>[0]): Promise<void> {
        let ads: PublicAd[] = [];
        try {
            ads = UtilAd.readAds(await api.list(query));
        } catch (e) {
            console.warn('[ad] 광고 후보를 받지 못했어요 — 자리를 접어요', e);
        }
        store(keys.map(({ key, type }) => [key, ads.filter((ad) => ad.type === type)]));
    }

    /**
     * 유형별 후보를 받아 둔다(20초 안에 받은 유형은 건너뛴다). 같은 틱에 부른 유형은 한 번에 묻는다. 핫딜 노출은 `ensureHotdeal`.
     * @param {readonly PublicAdType[]} types - 유형.
     * @returns {Promise<void>} 다 받았을 때(실패해도 끝난다).
     */
    function ensure(types: readonly PublicAdType[]): Promise<void> {
        const waits: Promise<void>[] = [];
        let queued = false;
        for (const type of types) {
            if (type === 'HOTDEAL_TOP' || type === 'HOTDEAL_CATEGORY') continue;
            const running = inflight.get(type);
            if (running) waits.push(running);
            else if (!fresh(type)) {
                pending.add(type);
                queued = true;
            }
        }
        if (queued) {
            batch ??= Promise.resolve().then(() => {
                const list = UtilAd.TYPES.filter((t) => pending.has(t));
                pending = new Set();
                batch = null;
                const run = fetchInto(
                    list.map((type) => ({ key: type, type })),
                    { types: list },
                );
                for (const type of list) inflight.set(type, run);
                return run.finally(() => list.forEach((type) => inflight.delete(type)));
            });
            waits.push(batch);
        }
        return Promise.all(waits).then(() => undefined);
    }

    /**
     * 핫딜 노출 후보 — 말머리를 고르지 않았으면 홈 상단(`HOTDEAL_TOP`), 골랐으면 그 말머리 상단(`HOTDEAL_CATEGORY`)
     * @param {string} board - 핫딜 게시판 slug.
     * @param {string} prefix - 고른 말머리 ID(없으면 빈 글자).
     * @returns {Promise<void>} 다 받았을 때.
     */
    function ensureHotdeal(board: string, prefix: string): Promise<void> {
        const key = hotdealKey(board, prefix);
        const running = inflight.get(key);
        if (running) return running;
        if (fresh(key)) return Promise.resolve();
        const type: PublicAdType = prefix ? 'HOTDEAL_CATEGORY' : 'HOTDEAL_TOP';
        const run = fetchInto([{ key, type }], { types: [type], board, prefix: prefix || undefined }).finally(() => inflight.delete(key));
        inflight.set(key, run);
        return run;
    }

    /**
     * 받아 둔 후보
     * @param {PublicAdType} type - 유형(핫딜 노출 제외).
     * @returns {PublicAd[] | undefined} 후보. 아직 안 받았으면 undefined.
     */
    function candidates(type: PublicAdType): PublicAd[] | undefined {
        return state.value.ads[type];
    }

    /** 받아 둔 핫딜 노출 후보(아직 안 받았으면 undefined) */
    function hotdealCandidates(board: string, prefix: string): PublicAd[] | undefined {
        return state.value.ads[hotdealKey(board, prefix)];
    }

    /** 가젯이 아닌 자리 설정 — undefined 아직 · null 못 받음 */
    const placements = computed(() => state.value.placements);

    /** 자리 설정을 받아 둔다(20초 안이면 건너뛴다). 실패하면 null(글 중간 광고 없음) */
    function ensurePlacements(): Promise<void> {
        if (placementsRun) return placementsRun;
        if (state.value.placements !== undefined && Date.now() - state.value.placementsAt < TTL_MS) return Promise.resolve();
        placementsRun = api
            .placements()
            .then((p) => {
                state.value = { ...state.value, placements: p, placementsAt: Date.now() };
            })
            .catch((e) => {
                console.warn('[ad] 광고 자리 설정을 받지 못했어요', e);
                state.value = { ...state.value, placements: null, placementsAt: Date.now() };
            })
            .finally(() => {
                placementsRun = null;
            });
        return placementsRun;
    }

    /**
     * 노출 한 번 — 자리 컴포넌트가 화면에 보였을 때 부른다(같은 자리에서 두 번 부르지 않는 것은 부르는 쪽 몫). 1초 모아 보낸다
     * @param {PublicAd} ad - 보인 광고.
     * @param {AdPlacement} placement - 어디서.
     */
    function impression(ad: PublicAd, placement: AdPlacement): void {
        tracker = api;
        queue.push({ adId: ad.id, version: ad.version, placement });
        timer ??= setTimeout(flush, FLUSH_MS);
        hookHide();
    }

    /**
     * 클릭 — 링크를 누를 때. 이동은 막지 않는다(`sendBeacon`)
     * @param {PublicAd} ad - 누른 광고.
     * @param {AdPlacement} placement - 어디서.
     */
    function click(ad: PublicAd, placement: AdPlacement): void {
        api.click(ad.id, { version: ad.version, placement });
    }

    return { ensure, ensureHotdeal, candidates, hotdealCandidates, placements, ensurePlacements, impression, click };
}
