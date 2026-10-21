/**
 * 앱이 시작할 때 설치 상태를 먼저 **한 번** 받는다 (`useInstallState().ensureLoaded()` → `GET /api/system/installation/status`).
 *
 * - 파일 이름의 `00.` 은 순서다 — 로그인 상태를 받는 `auth.client.ts` 보다 먼저 돈다. 설치 전에는 `/api/auth/me` 가 `503 NOT_INSTALLED` 라 로그인 상태를 물을 필요가 없다.
 * - **CSRF 쿠키(`XSRF-TOKEN`)도 이 호출에서 내려온다**(설치 전에는 `me` 가 막혀 있다). 설치 후에는 `auth.client.ts` 의 `me` 가 한 번 더 내려준다.
 * - 서버에 닿지 못해도 앱은 뜬다 — 상태를 `failed` 로 두고 화면이 오류 안내를 보인다(앱을 못 쓰게 막지 않는다).
 */
export default defineNuxtPlugin(async () => {
    await useInstallState().ensureLoaded();
});
