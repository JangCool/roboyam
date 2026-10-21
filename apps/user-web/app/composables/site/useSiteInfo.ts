import type { SiteInfo } from '~/composables/site/useSiteInfo.types';
import UtilSiteInfo from '~/utils/util-site-info';

/**
 * 사이트 정보 상태만 읽는다(바꾸지 않는다, 서버를 부르지 않는다) — `useSiteName`·`useSiteTagline`·셸이 쓴다.
 * `undefined` = 아직 안 물어봄, `null` = 못 받았거나 쓸 수 없는 응답(이름·소개·로고·아이콘 없음, 표시 기본값은 시스템 폴백).
 */
export function useSiteInfoState() {
    return useState<SiteInfo | null | undefined>(UtilSiteInfo.STATE_KEY, () => undefined);
}

/** 진행 중인 `info` 호출 — 동시에 여러 곳이 물어도 한 번만 보낸다 */
let inflight: Promise<void> | null = null;

/**
 * 공개 사이트 정보를 받아 상태에 채운다 — 앱 시작 때 플러그인(`plugins/02.site-info.client.ts`)이 한 번 부르고, 표시 값(`useDisplaySettings`)이 같은 호출을 기다린다.
 * 서버와 말하는 것은 `useSiteInfoApi` 다. 서버가 이름·아이콘·링크 미리보기를 초기 `<head>` 에 직접 채우므로(`docs/features/site-settings-contract.md`) 화면은 탭 제목(`app.vue`)과 로고 자리만 이 값을 쓴다.
 *
 * - 이미 받았으면(`null` 포함) 다시 묻지 않는다. 못 받아도 던지지 않고 `null` 로 둔다 — 앱은 뜬다(기본 이름·로고 없음).
 * - 설치 전(`503 NOT_INSTALLED`)에는 부르지 않는다 — 부르는 쪽이 설치 상태를 먼저 본다.
 *
 * @returns `ensureLoaded()` 아직 안 받았으면 받고 상태 값을 돌려준다(못 받았으면 null).
 */
export function useSiteInfo() {
    const api = useSiteInfoApi();
    const state = useSiteInfoState();

    async function ensureLoaded(): Promise<SiteInfo | null> {
        if (state.value !== undefined) return state.value;
        inflight ??= api
            .info()
            .then((res) => {
                state.value = UtilSiteInfo.fromResponse(res);
            })
            .catch(() => {
                state.value = null;
            })
            .finally(() => {
                inflight = null;
            });
        await inflight;
        return state.value ?? null;
    }

    return { ensureLoaded };
}
