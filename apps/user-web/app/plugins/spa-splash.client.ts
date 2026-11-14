import UtilBootSplash from '~/utils/util-boot-splash';

/**
 * 첫 로딩 스플래시를 넘겨받아, 첫 화면이 다 그려지면 한 번에 걷는다 — 규칙은 `UtilBootSplash`.
 * 다른 플러그인보다 먼저(`order: -30`) 돌아 Nuxt 기본 지우기(`app:suspense:resolve`)보다 먼저 요소 이름을 바꾼다.
 *  - 재기 시작: 레이아웃·화면 조각까지 그려진 때(`app:suspense:resolve`). 앱이 붙는 때(`app:mounted`)는 화면 조각을 아직 받는 중일 수 있다.
 *  - 안전망: 앱이 붙은 뒤 `UtilBootSplash.SAFETY_MS` 가 지나면 무엇이든 걷는다.
 */
export default defineNuxtPlugin({
    name: 'spa-splash',
    order: -30,
    setup(nuxtApp) {
        const el = UtilBootSplash.claim();
        if (!el) return;
        nuxtApp.hooks.hookOnce('app:suspense:resolve', () => UtilBootSplash.hideWhenSettled(el));
        nuxtApp.hooks.hookOnce('app:mounted', () => {
            window.setTimeout(() => UtilBootSplash.hide(el), UtilBootSplash.SAFETY_MS);
        });
    },
});
