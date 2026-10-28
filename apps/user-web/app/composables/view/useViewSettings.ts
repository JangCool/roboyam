import type { ViewSettings } from '~/composables/view/api/useViewSettingsApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilViewSettings from '~/utils/util-view-settings';

/**
 * 보기 설정(야간 모드·글자 크기·댓글 정렬) — 화면 전체가 공유하는 상태와 바꾸는 방법(community `useSettings`). 서버와 말하는 것은 `useViewSettingsApi`.
 *
 *  - 이 브라우저(`localStorage roboyam:view-settings`)에 사본을 둔다 — 첫 그림 전에 야간 모드를 걸려면 서버를 기다릴 수 없다. 막혀 있어도 앱은 뜨고 이번 방문 동안 메모리에서 쓴다.
 *  - 비로그인: 서버를 부르지 않는다(`load`·`ensureLoaded`·`update` 모두 `user` 를 먼저 본다 — 서버는 401) — 이 브라우저 사본만 쓴다.
 *  - 로그인: **서버 값이 이긴다.** 앱 시작·로그인 직후(`plugins/view-settings.client.ts`)에 받아 상태와 사본을 갱신한다. 서버에 저장한 적이 없으면 기본값(AUTO·100·OLDEST)이 와서
 *    사본도 기본값으로 덮인다(계정의 값이 이 브라우저 사본보다 앞선다). 로그아웃해도 이 브라우저의 마지막 값은 남긴다.
 *  - **바꾸기(`update`)는 낙관적**이다 — 먼저 반영하고 저장하다 실패하면 바꾼 칸만 되돌리고 던진다. 저장은 한 줄로 세워 그 순간의 값 전체를 보낸다(`PUT` 은 통째 교체).
 * `phase` 의 `idle` 은 아직 안 물어봄, `ready` 는 서버 값을 받음.
 */
export type ViewSettingsPhase = 'idle' | 'loading' | 'ready' | 'error';

function writeStored(settings: ViewSettings): boolean {
    try {
        window.localStorage.setItem(UtilViewSettings.STORAGE_KEY, JSON.stringify(settings));
        return true;
    } catch {
        return false;
    }
}

function readStored(): ViewSettings | null {
    if (typeof window === 'undefined') return null;
    try {
        return UtilViewSettings.parseStored(window.localStorage.getItem(UtilViewSettings.STORAGE_KEY));
    } catch {
        return null;
    }
}

function settingsState() {
    return useState<ViewSettings>('view-settings', () => readStored() ?? UtilViewSettings.defaults());
}

/** 값만 읽는다(가볍다 — 서버 호출 준비를 하지 않는다). 플러그인·댓글 목록이 쓴다 */
export function useViewSettingsValues(): ComputedRef<ViewSettings> {
    const state = settingsState();
    return computed(() => state.value);
}

let inflight: Promise<void> | null = null;
let loadSeq = 0;
let epoch = 0;
let confirmed: ViewSettings | null = null;
let saveChain: Promise<unknown> = Promise.resolve();

export function useViewSettings() {
    const api = useViewSettingsApi();
    const { user } = useAuth();
    const t = useTranslate();
    const state = settingsState();
    const phase = useState<ViewSettingsPhase>('view-settings-phase', () => 'idle');
    const errorMessage = useState<string>('view-settings-error', () => '');

    const settings = computed(() => state.value);

    /** 서버 값을 받는다 — 실패하면 `phase=error`(던지지 않는다) */
    function load(): Promise<void> {
        if (!user.value) return Promise.resolve();
        const mine = ++loadSeq;
        phase.value = 'loading';
        errorMessage.value = '';
        const run: Promise<void> = api
            .get()
            .then((res) => {
                if (mine !== loadSeq) return;
                // 서버에 아직 없는 칸(`menuPosition`)은 이 브라우저 사본의 값을 둔다 — 서버가 칸을 주면 그 값이 이긴다
                const fromServer: Partial<ViewSettings> = res;
                const next = UtilViewSettings.sanitize({ ...fromServer, menuPosition: fromServer.menuPosition ?? state.value.menuPosition });
                confirmed = next;
                state.value = next;
                writeStored(next);
                phase.value = 'ready';
            })
            .catch((e: unknown) => {
                if (mine !== loadSeq) return;
                errorMessage.value = UtilBoardMessages.of(t, e);
                phase.value = 'error';
            })
            .finally(() => {
                if (inflight === run) inflight = null;
            });
        inflight = run;
        return run;
    }

    /** 이미 받았으면 다시 묻지 않는다 */
    async function ensureLoaded(): Promise<void> {
        if (!user.value) return;
        if (phase.value === 'idle') return load();
        if (phase.value === 'loading' && inflight) await inflight;
    }

    /** 서버와의 연결 상태만 비운다(로그아웃·다른 계정) — 화면 값과 사본은 남긴다 */
    function reset(): void {
        epoch++;
        loadSeq++;
        inflight = null;
        confirmed = null;
        phase.value = 'idle';
        errorMessage.value = '';
    }

    async function sendLatest(): Promise<void> {
        const target = state.value;
        if (confirmed && UtilViewSettings.isSame(target, confirmed)) return;
        const mine = epoch;
        try {
            const saved = UtilViewSettings.sanitize(await api.update(target));
            if (mine !== epoch) return;
            confirmed = saved;
            if (UtilViewSettings.isSame(state.value, target)) {
                state.value = saved;
                writeStored(saved);
            }
        } catch (e) {
            if (mine === epoch) {
                state.value = UtilViewSettings.revert(state.value, target, confirmed ?? target);
                writeStored(state.value);
            }
            throw e;
        }
    }

    function save(): Promise<void> {
        const run = saveChain.then(sendLatest);
        saveChain = run.catch(() => undefined);
        return run;
    }

    /**
     * 바꾼다 — 바꿀 칸만 준다. 화면에 먼저 반영하고 저장한다. 실패하면 바꾼 칸을 되돌리고 `ApiError` 를 던진다.
     * @returns 어디에 저장됐나 — 서버 · 이 기기 · 이번 방문 동안만
     */
    async function update(partial: Partial<ViewSettings>): Promise<'server' | 'device' | 'memory'> {
        const before = state.value;
        const next = UtilViewSettings.merge(before, partial);
        if (UtilViewSettings.isSame(before, next)) return user.value ? 'server' : 'device';
        state.value = next;
        const persisted = writeStored(next);
        if (!user.value) return persisted ? 'device' : 'memory';
        confirmed ??= before;
        await save();
        return 'server';
    }

    return { settings, phase, errorMessage, load, ensureLoaded, reset, update };
}
