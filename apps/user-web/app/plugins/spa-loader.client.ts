/**
 * 첫 화면 틀(`app/spa-loading-template.html` → `#__nuxt-loader`)을 **진짜 화면이 붙는 순간**(`app:mounted`) 지운다.
 *
 * Nuxt 기본은 첫 화면의 비동기 준비(Suspense — 화면의 데이터 요청까지)가 다 끝난 뒤(`app:suspense:resolve`)에 지운다. 그러면 상단 바·메뉴가 이미 그려졌는데도
 * 틀이 그 위를 덮고 있어, 예전(상단 바가 먼저 뜨고 본문이 따라옴)보다 느리게 느껴졌다(사용자 2026-11-13). 틀은 JS 를 받는 동안의 빈 화면만 대신한다.
 * 틀이 늦게(0.4초 뒤) 나타나는 것은 그 파일의 CSS 몫이다 — 빠른 회선·재방문에서는 틀이 아예 보이지 않는다.
 */
export default defineNuxtPlugin((nuxtApp) => {
    nuxtApp.hook('app:mounted', () => {
        document.getElementById('__nuxt-loader')?.remove();
    });
});
