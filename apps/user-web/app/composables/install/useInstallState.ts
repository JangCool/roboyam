import type { InstallPhase, InstallState } from '~/composables/install/useInstallState.types';
import UtilInstallState from '~/utils/util-install-state';

/**
 * 앱이 아는 설치 상태 — 화면 전체가 공유하는 상태 하나(`useState`). 서버와 말하는 것은 `useInstallApi` 다(frontend-guide 3장).
 *
 * 앱이 뜰 때 `GET /api/system/installation/status` 를 **한 번** 묻고(`plugins/00.installation.client.ts`) 상태에 둔다.
 *  - `pending` 설치 전 — 전역 라우트 미들웨어(`install.global.ts`)가 모든 주소를 `/install` 로 보낸다.
 *  - `installed` 설치 후 — **다시 묻지 않는다.** `/install` 은 홈으로 보낸다.
 *  - `failed` 물었는데 답을 못 받았다 — 앱을 막지 않고 오류 안내만 보인다(`InstallStatusBanner`·`/install` 의 다시 시도). 저절로 다시 묻지 않는다.
 *  - `unknown` 아직 안 물어봄.
 *
 * 다른 곳이 `503 NOT_INSTALLED` 를 받으면(`useApiClient`) 같은 상태(`pending`)로 바꾼다. `authorized` 는 이 세션이 설치 토큰을 맞혔는가(새로고침해도 이어진다).
 */

/** 진행 중인 `status` 호출 — 동시에 여러 곳이 물어도 한 번만 보낸다 */
let inflight: Promise<InstallPhase> | null = null;

export function useInstallState() {
    const api = useInstallApi();

    const state = useState<InstallState>(UtilInstallState.STATE_KEY, () => ({ phase: 'unknown', authorized: false }));

    const phase = computed(() => state.value.phase);
    const authorized = computed(() => state.value.authorized);

    /** 서버에 설치 상태를 묻는다. 서버에 닿지 못하면 `failed` 로 두고 던지지 않는다 */
    async function refresh(): Promise<InstallPhase> {
        if (inflight) return inflight;
        inflight = api
            .status()
            .then((res): InstallPhase => {
                state.value = { phase: res.installed ? 'installed' : 'pending', authorized: res.installed ? false : res.authorized };
                return state.value.phase;
            })
            .catch((): InstallPhase => {
                state.value = { phase: 'failed', authorized: false };
                return 'failed';
            })
            .finally(() => {
                inflight = null;
            });
        return inflight;
    }

    /** 이미 알면 다시 묻지 않는다(`failed` 도 마찬가지 — 다시 묻는 것은 `refresh()`). 라우트 가드가 이동마다 부른다 */
    async function ensureLoaded(): Promise<InstallPhase> {
        if (state.value.phase !== 'unknown') return state.value.phase;
        return refresh();
    }

    /** 설치가 끝났다고 알게 됐을 때(`409 ALREADY_INSTALLED`·설치 완료) */
    function markInstalled(): void {
        state.value = { phase: 'installed', authorized: false };
    }

    /** 설치 전이라고 알게 됐을 때(`503 NOT_INSTALLED`) */
    function markPending(): void {
        state.value = { phase: 'pending', authorized: state.value.authorized };
    }

    /** 설치 토큰을 맞혔다/인증이 풀렸다 */
    function setAuthorized(value: boolean): void {
        state.value = { ...state.value, authorized: value };
    }

    return { state, phase, authorized, refresh, ensureLoaded, markInstalled, markPending, setAuthorized };
}
