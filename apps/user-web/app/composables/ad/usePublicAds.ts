import type { AdImpressionItem, PublicAd, PublicAdPlacements, PublicAdType } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement, WingContext } from '~/composables/ad/usePublicAds.types';
import UtilAd from '~/utils/util-ad';

/**
 * 공개 광고 공유 상태 — 유형별 후보 사본 · 자리 설정 · 노출 모아 보내기 · 클릭. 홈·가젯 페이지·게시판 레이아웃·글 보기·핫딜이 같은 길로 받는다(광고는 홈 응답과 따로 사본 캐시).
 * 받기 실패는 「광고 없음」으로 본다(본문을 막지 않는다).
 */

/** 서버 사본(20초)과 같은 수명 */
const TTL_MS = 20_000;
const FLUSH_MS = 1_000;

interface PublicAdsState {
    ads: Record<string, PublicAd[]>;
    loadedAt: Record<string, number>;
    /** undefined = 아직, null = 못 받음 */
    placements: PublicAdPlacements | null | undefined;
    placementsAt: number;
    /** 게시판 화면·글 보기가 알려 준 날개 맥락(주소만으로는 소모임 글·숨김 게시판인지 모른다) */
    wingContext: WingContext | null;
}

type PublicAdApi = ReturnType<typeof usePublicAdApi>;

/* 화면 상태가 아니라 요청 묶기·노출 대기열이라 모듈에 둔다(SPA 한 창에 하나) */
/** 게시판 slug(가젯·홈·가젯 페이지의 날개는 빈 글자) → 이번 틱에 물을 유형 */
let pending = new Map<string, Set<PublicAdType>>();
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

/** 창을 숨기면 남은 노출을 `sendBeacon` 으로 */
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

function adKey(type: PublicAdType, board: string): string {
    return board ? `${type}@${board}` : type;
}

function hotdealKey(board: string, prefix: string): string {
    return prefix ? `HOTDEAL_CATEGORY:${board}:${prefix}` : `HOTDEAL_TOP:${board}`;
}

export function usePublicAds() {
    const api = usePublicAdApi();
    const state = useState<PublicAdsState>('public-ads', () => ({ ads: {}, loadedAt: {}, placements: undefined, placementsAt: 0, wingContext: null }));

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
     * 20초 안에 받은 유형은 건너뛰고, 같은 틱에 부른 유형은 게시판마다 한 번에 묻는다.
     * `board` 는 게시판 맥락(목록 한 줄·글 보기, 게시판 화면·글 보기의 날개)만 넘긴다 — 서버가 그 게시판에 광고를 안 내면 빈 목록이라 사본도 게시판마다 따로 둔다.
     */
    function ensure(types: readonly PublicAdType[], board = ''): Promise<void> {
        const waits: Promise<void>[] = [];
        let queued = false;
        for (const type of types) {
            if (type === 'HOTDEAL_TOP' || type === 'HOTDEAL_CATEGORY') continue;
            const key = adKey(type, board);
            const running = inflight.get(key);
            if (running) waits.push(running);
            else if (!fresh(key)) {
                const set = pending.get(board) ?? new Set<PublicAdType>();
                set.add(type);
                pending.set(board, set);
                queued = true;
            }
        }
        if (queued) {
            batch ??= Promise.resolve().then(() => {
                const groups = [...pending.entries()];
                pending = new Map();
                batch = null;
                const runs = groups.map(([b, set]) => {
                    const list = UtilAd.TYPES.filter((t) => set.has(t));
                    const keys = list.map((type) => ({ key: adKey(type, b), type }));
                    const run = fetchInto(keys, { types: list, board: b || undefined });
                    for (const { key } of keys) inflight.set(key, run);
                    return run.finally(() => keys.forEach(({ key }) => inflight.delete(key)));
                });
                return Promise.all(runs).then(() => undefined);
            });
            waits.push(batch);
        }
        return Promise.all(waits).then(() => undefined);
    }

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

    /** undefined = 아직 안 받음(자리를 비율 틀로 잡는다), [] = 없음(자리를 접는다) */
    function candidates(type: PublicAdType, board = ''): PublicAd[] | undefined {
        return state.value.ads[adKey(type, board)];
    }

    function hotdealCandidates(board: string, prefix: string): PublicAd[] | undefined {
        return state.value.ads[hotdealKey(board, prefix)];
    }

    const placements = computed(() => state.value.placements);
    /** 19금 게시판·19금 글에도 광고를 낼지 — 자리 설정을 아직 못 받았거나 못 받으면 끔 */
    const adultEnabled = computed(() => state.value.placements?.adult?.enabled === true);

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

    /** 같은 자리에서 두 번 부르지 않는 것은 부르는 쪽 몫 */
    function impression(ad: PublicAd, placement: AdPlacement): void {
        tracker = api;
        queue.push({ adId: ad.id, version: ad.version, placement });
        timer ??= setTimeout(flush, FLUSH_MS);
        hookHide();
    }

    function click(ad: PublicAd, placement: AdPlacement): void {
        api.click(ad.id, { version: ad.version, placement });
    }

    /** 게시판 화면은 게시판을, 글 보기는 글·게시판을 받은 뒤 부른다 — 날개(`AdWings`)가 이 맥락으로 그릴지·어느 `board` 로 부를지 정한다 */
    function setWingContext(next: WingContext): void {
        const cur = state.value.wingContext;
        if (cur && cur.path === next.path && cur.board === next.board && cur.adsHidden === next.adsHidden && cur.adult === next.adult && cur.group === next.group && (cur.secret === true) === (next.secret === true)) return;
        state.value = { ...state.value, wingContext: { ...next } };
    }

    /** 이 주소에 알려 준 날개 맥락 — 없으면 null(글 보기는 글을 받기 전이라 그리지 않고, 게시판 화면은 서버 판정에 맡긴다) */
    function wingContextOf(path: string): WingContext | null {
        const cur = state.value.wingContext;
        return cur !== null && cur.path === path ? cur : null;
    }

    return { ensure, ensureHotdeal, candidates, hotdealCandidates, placements, adultEnabled, ensurePlacements, impression, click, setWingContext, wingContextOf };
}
