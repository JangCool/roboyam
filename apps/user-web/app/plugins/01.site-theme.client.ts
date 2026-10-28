/**
 * 앱이 뜨기 전에 사이트 테마를 받아 루트(`<html>`)에 `data-theme` 로 붙인다 (`useTheme().ensureLoaded()`) — 테마별 CSS 변수·`data-theme` 기준 CSS 가 첫 화면부터 맞는다.
 *
 * - 파일 이름의 `01.` 은 순서다 — 설치 상태(`00.installation`) 다음, 로그인 상태(`auth.client.ts`)보다 먼저 돈다. 플러그인이 끝나야 앱이 뜨므로 깜빡임이 없다.
 * - 못 받거나 모르는 테마여도 기본 테마로 붙는다(`useTheme`) — 앱은 뜬다.
 * - 값이 바뀌면(`theme`) 속성도 따라 바뀐다.
 * - 다크 모드 판정은 별도 속성 `html[data-color-scheme='dark']` 다(`assets/css/main.css`·PrimeVue `darkModeSelector`) — `data-theme` 는 사이트 테마만 쓴다.
 * - 해석된 메뉴 모양(운영자 > 테마 기본, `useMenuStyle`) 중 CSS 가 맡는 값도 루트에 붙인다 — 테마 이름이 아니라 설정값에 CSS 를 묶는다(`main.css` 끝):
 *   `data-mobile-header="plain|brand"`(모바일 상단 바 모양) · `data-sticky-menu="on|off"`(모바일 메뉴 붙이기).
 */
export default defineNuxtPlugin(async () => {
    const { theme, ensureLoaded } = useTheme();
    await ensureLoaded();
    const { plainMobileHeader, stickyMenu } = useMenuStyle();

    const root = document.documentElement;
    watch(
        theme,
        (value) => {
            root.setAttribute('data-theme', value);
        },
        { immediate: true },
    );
    watch(
        [plainMobileHeader, stickyMenu],
        ([plain, sticky]) => {
            root.setAttribute('data-mobile-header', plain ? 'plain' : 'brand');
            root.setAttribute('data-sticky-menu', sticky ? 'on' : 'off');
        },
        { immediate: true },
    );
});
