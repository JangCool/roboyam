/**
 * 앱이 뜨기 전에 공개 사이트 정보(이름·소개·로고·아이콘·표시 기본값)를 받아 상태에 채운다 (`useSiteInfo().ensureLoaded()` → `GET /api/site/info`).
 *
 * - 파일 이름의 `02.` 는 순서다 — 설치 상태(`00.installation`)·테마(`01.site-theme`) 다음, 로그인 상태(`auth.client.ts`)·표시 값(`display.client.ts`)보다 먼저 돈다.
 *   그래서 첫 화면의 로고 자리와 표시 값의 처음 기본값이 받은 값으로 그려진다(깜빡임 최소화).
 * - **설치 전(`pending`)에는 부르지 않는다** — `/api/**` 가 `503 NOT_INSTALLED` 다. 설치가 끝나 상태가 바뀌면(`installed`) 그때 받는다.
 * - 서버가 느려도 앱 시작을 오래 막지 않는다 — 짧게(2초) 기다린 뒤 먼저 뜨고, 응답이 오면 상태가 채워져 화면이 바뀐다.
 * - 못 받아도 앱은 뜬다 — 던지지 않고 기본 이름(`UtilSiteName.DEFAULT`)·로고 없음으로 둔다. 탭 제목·아이콘·링크 미리보기는 서버가 초기 `<head>` 에 직접 채운다.
 */

/** 앱 시작을 기다려 주는 최대 시간(ms) */
const START_WAIT_MS = 2000;

export default defineNuxtPlugin(async () => {
    const install = useInstallState();
    const { ensureLoaded } = useSiteInfo();

    if (install.phase.value !== 'pending') {
        await Promise.race([ensureLoaded(), new Promise<void>((resolve) => setTimeout(resolve, START_WAIT_MS))]);
    }

    // 설치 마법사를 마치면(pending → installed) 그때 받는다. 이미 받았으면 다시 묻지 않는다
    watch(install.phase, (phase) => {
        if (phase === 'installed') void ensureLoaded();
    });
});
