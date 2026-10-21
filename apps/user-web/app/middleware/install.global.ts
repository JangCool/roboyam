import UtilInstallState from '~/utils/util-install-state';

/**
 * 설치 상태 가드 — 모든 화면 이동에 걸린다(`.global`).
 *
 *  - **설치 전**(`pending`)이면 어느 주소든 `/install` 로 보낸다(`/install` 자신은 그대로). 그 밖의 `/api/**` 는 서버가 `503 NOT_INSTALLED` 로 막고 있다.
 *  - **설치 후**(`installed`)에 `/install` 로 오면 홈으로 보낸다(서버도 마법사 API 를 닫는다 — `409 ALREADY_INSTALLED`).
 *  - 상태를 못 받았으면(`failed`) 막지 않는다 — 화면이 오류 안내를 보인다(`InstallStatusBanner`).
 *
 * 상태는 앱이 뜰 때 한 번 받아 두므로(`plugins/00.installation.client.ts`) 이동마다 서버를 부르지 않는다.
 * **이것은 편의이지 보안이 아니다** — 진짜로 막는 것은 서버다. 정적 파일(JS·CSS·이미지)은 라우터를 거치지 않아 영향이 없다.
 */
export default defineNuxtRouteMiddleware(async (to) => {
    // ⚠ await 앞에서 컴포저블을 부른다 — 뒤에서는 Nuxt 문맥이 없다
    const { ensureLoaded } = useInstallState();
    const phase = await ensureLoaded();

    if (phase === 'pending' && !UtilInstallState.isInstallPath(to.path)) return navigateTo(UtilInstallState.PATH, { replace: true });
    if (phase === 'installed' && UtilInstallState.isInstallPath(to.path)) return navigateTo('/', { replace: true });
});
