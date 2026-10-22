import type { DisplayValues } from '#layers/display-time/utils/util-time';
import type { MyDisplayResponse } from '~/composables/display/api/useDisplayApi.types';
import UtilDisplaySettings from '~/utils/util-display-settings';
import UtilInstallState from '~/utils/util-install-state';

/**
 * 표시 값(시간대·시간 형식·날짜 형식)을 서버에서 받고 바꾸는 방법 — 앱 시작 플러그인(`plugins/display.client.ts`)과 개인 설정 화면("시간 표시")이 쓴다.
 * 서버와 말하는 것은 `useDisplayApi` 다(frontend-guide 3장). **효과값 자체의 상태와 화면 반영은 공유 레이어 `@roboyam/display-time` 의 `useDisplay()` 가 든다** —
 * 여기는 서버 값을 거기에 채우고(`setDisplay`) "이 값이 누구 것인지"(`owner`)·받기 상태(`phase`)를 든다. 시각을 그리는 컴포넌트는 이것이 아니라 `useDisplayTime()` 을 쓴다.
 *
 * **층**: 개인 값(사용자 선택값) → 사이트 기본값 → 시스템 폴백. 합치는 것은 서버 한 곳이고 화면은 효과값 하나만 받는다(브라우저 시간대는 쓰지 않는다).
 * **사이트 기본값(`GET /api/site/info` 의 `displayDefaults`, `useSiteInfo`)은 개인 값이 없을 때의 폴백으로만** 쓴다 — 첫 값(기억값이 없을 때)과 서버 효과값이 개인 값이 아닐 때.
 * 서버(`/api/display/effective`·`/api/me/display`)가 같은 사이트 정보 사본에서 기본값을 합치므로 결과는 서버 값과 같고, 효과값을 받기 전 첫 화면을 사이트 값으로 맞추는 몫이 크다.
 * 개인 값이 있으면 개인 값이 이긴다(`UtilDisplaySettings`). 사이트 정보를 못 받았으면 서버 값(못 받으면 기억값 → 시스템 폴백)을 그대로 쓴다.
 *
 * **처음 값은 기억값이다(깜빡임 방지)**: 이 브라우저에 마지막으로 받은 효과값(localStorage `roboyam:display` — 값 + 받은 사람 id)으로 **동기로** 시작한다(`follow()` 의 앞부분).
 * 받은 사람이 지금 사람과 다르면(다른 계정·비로그인 ↔ 로그인) 버리고 시스템 폴백으로 시작한다 — 다른 사람의 기억값을 쓰지 않는다. 서버 값이 오면 그것으로 바꾸고 다시 기억한다.
 *
 * **서버에서 받는 때**(`plugins/display.client.ts` 가 건다): 앱이 시작할 때(로그인 상태가 정해진 뒤) · 로그인한 사람이 바뀔 때(로그인·로그아웃·다른 계정).
 * 개인 값을 저장·되돌린 직후는 다시 묻지 않고 **응답의 `effective` 를 바로** 적용한다(`saveOwn`·`resetOwn`·`loadMine`).
 * 설치 전(`503`)에는 묻지 않는다. **받지 못해도(실패·서버에 API 가 없는 `404`·깨진 응답) 던지지 않고** 지금 값(기억값 → 폴백)을 그대로 둔다 — 화면은 지금과 똑같이 그려진다.
 *
 * ⚠ 컴포저블은 setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다) — 안에서 필요한 상태는 부를 때 미리 잡아 둔다.
 */

export type DisplayPhase = 'idle' | 'loading' | 'ready' | 'error';

/** 이 브라우저에 기억한 글자 — 없거나 저장소가 막혀 있으면 null */
function readStoredRaw(): string | null {
    if (typeof window === 'undefined') return null;
    try {
        return window.localStorage.getItem(UtilDisplaySettings.STORAGE_KEY);
    } catch {
        return null;
    }
}

/** 효과값을 이 브라우저에 기억한다. 저장소가 막혀 있어도 앱은 그대로 뜬다(이번 방문 동안은 메모리에서 쓴다) */
function writeStored(values: DisplayValues, userId: string | null): void {
    try {
        window.localStorage.setItem(UtilDisplaySettings.STORAGE_KEY, UtilDisplaySettings.serialize(values, userId));
    } catch {
        /* 사생활 보호 모드 등 */
    }
}

/** 서버에서 받은 순번 — 새 조회·저장이 시작되면 앞 요청의 늦은 응답은 버린다(사람이 바뀐 뒤 앞 사람의 응답이 와도 쓰지 않는다) */
let loadSeq = 0;

/**
 * @returns `effective` 지금 쓸 값 · `serviceZoneId` 서비스 시간대(서비스 하루의 기준 — "한국 시간 기준" 덧말의 재료) · `phase` 받기 상태 ·
 *          `load()` 서버 효과값 받기 · `follow()` 사람이 정해졌거나 바뀐 때의 한 번(값 정리 + 받기) ·
 *          `loadMine()`·`saveOwn(values)`·`resetOwn()` 개인 값 읽기·저장·되돌리기(셋 다 응답의 효과값을 바로 적용하고 `{ own, defaults, effective }` 를 돌려준다 — 실패하면 `ApiError` 를 던진다).
 */
export function useDisplaySettings() {
    // ⚠ Nuxt 문맥은 부르는 순간(setup)에만 잡힌다 — 요청이 끝난 뒤(await 뒤)에는 쓸 수 없으니 필요한 것은 여기서 꺼내 둔다
    const api = useDisplayApi();
    const display = useDisplay();
    // 사이트 표시 기본값 — 앱 시작 플러그인(`02.site-info`)이 먼저 받아 둔다. `load()` 는 같은 호출이 아직 오는 중이면 기다린다
    const siteInfo = useSiteInfo();
    const siteState = useSiteInfoState();
    /** 지금 아는 사이트 표시 기본값 — 모르면 null */
    const siteDefaults = (): DisplayValues | null => siteState.value?.displayDefaults ?? null;
    /** 지금 값이 누구 것인가 — `undefined` 는 아직 아무도 배선하지 않음(첫 `follow()` 가 기억값을 읽게 한다) */
    const owner = useState<string | null | undefined>(UtilDisplaySettings.OWNER_KEY, () => undefined);
    // 상태만 읽는다(`useAuth()`·`useInstallState()` 를 부르면 서버 호출 준비까지 딸려 온다) — 필요한 칸만 적은 최소 모양
    const user = useState<{ id: string } | null>(AUTH_USER_STATE_KEY);
    const install = useState<{ phase: string }>(UtilInstallState.STATE_KEY);
    const phase = useState<DisplayPhase>('display-phase', () => 'idle');

    const userId = (): string | null => user.value?.id ?? null;

    /** 값과 주인을 바꾸고 이 브라우저에 기억한다 */
    function apply(values: DisplayValues, id: string | null): void {
        display.setDisplay(values);
        owner.value = id;
        writeStored(values, id);
    }

    /** 서버에서 효과값을 받아 바꾼다(비로그인도). 못 받으면 던지지 않고 지금 값을 그대로 둔다 */
    function load(): Promise<void> {
        const mine = ++loadSeq;
        const id = userId();
        phase.value = 'loading';
        // 사이트 정보 호출(`ensureLoaded`)은 던지지 않는다 — 못 받으면 사이트 기본값 없이 서버 값을 그대로 쓴다
        return Promise.all([api.effective(), siteInfo.ensureLoaded()])
            .then(([res]) => {
                if (mine !== loadSeq) return;
                const values = UtilDisplaySettings.fromEffectiveResponse(res, siteDefaults());
                if (!values) {
                    phase.value = 'error';
                    return;
                }
                display.setServiceZone(UtilDisplaySettings.serviceZoneOf(res));
                apply(values, id);
                phase.value = 'ready';
            })
            .catch(() => {
                // 서버에 API 가 없는 404·네트워크 실패 — 지금 있는 값(기억값 또는 시스템 폴백)을 그대로 쓴다
                if (mine === loadSeq) phase.value = 'error';
            });
    }

    /**
     * 로그인한 사람이 정해졌거나 바뀌었을 때 한 번 — 다른 사람 것이 된 값을 정리하고(`UtilDisplaySettings.refreshPlan`) 서버에서 받는다.
     * 설치 전이면 받지 않는다.
     */
    function follow(): Promise<void> {
        const plan = UtilDisplaySettings.refreshPlan({ values: display.values.value, owner: owner.value }, userId(), readStoredRaw(), install.value?.phase ?? 'unknown', siteDefaults());
        display.setDisplay(plan.wiring.values);
        owner.value = plan.wiring.owner;
        loadSeq++; // 앞 사람(또는 앞 시작)의 늦은 응답은 쓰지 않는다
        return plan.load ? load() : Promise.resolve();
    }

    /** 개인 설정 응답의 효과값을 바로 적용한다 — 다시 묻지 않는다 */
    function applyMine(res: MyDisplayResponse): void {
        const values = UtilDisplaySettings.fromMyResponse(res, siteDefaults());
        if (!values) return;
        loadSeq++; // 아직 오는 중인 효과값 응답이 이 값을 덮어쓰지 않게
        apply(values, userId());
        phase.value = 'ready';
    }

    /** 내 개인 값·기본값·효과값을 읽는다(행을 만들지 않는다) */
    async function loadMine(): Promise<MyDisplayResponse> {
        const res = await api.me();
        applyMine(res);
        return res;
    }

    /** 개인 값을 저장한다(세 칸 모두) — 저장 직후 화면에 바로 반영한다 */
    async function saveOwn(values: DisplayValues): Promise<MyDisplayResponse> {
        const res = await api.saveMe({ values });
        applyMine(res);
        return res;
    }

    /** 기본값으로 되돌린다(개인 값 삭제) — 되돌린 직후 화면에 바로 반영한다 */
    async function resetOwn(): Promise<MyDisplayResponse> {
        const res = await api.resetMe();
        applyMine(res);
        return res;
    }

    return { effective: display.values, serviceZoneId: display.serviceZoneId, phase, load, follow, loadMine, saveOwn, resetOwn };
}
