import UtilViewSettings from '~/utils/util-view-settings';

/**
 * 보기 설정의 야간 모드·글자 크기를 `<html>` 에 건다 — `data-color-scheme`(`light`·`dark`: `main.css` 의 `dark:` 변형과 PrimeVue `darkModeSelector` 가 본다)과
 * `--text-scale`(1 = 100% — `main.css` 의 Tailwind 글자 크기 토큰과 `text-scale.css` 의 PrimeVue 글자 크기 토큰이 곱한다). `data-theme` 는 사이트 테마 몫이라 건드리지 않는다(`themes.md`).
 *
 *  - **깜빡임 방지**: `order: -20` 으로 서버에 묻는 다른 플러그인보다 먼저 돈다 — 값은 이 브라우저에 저장해 둔 것을 동기로 읽는다(`useViewSettingsValues`).
 *    서버 값(로그인한 사람의 설정)은 `view-settings.client.ts` 가 받아 상태를 바꾸고, 아래 `watch` 가 그때 다시 건다.
 *  - 야간 모드 `AUTO`(기본)는 기기 설정을 따르고, 기기 설정이 바뀌면 곧바로 따라간다.
 */
export default defineNuxtPlugin({
    name: 'view-theme',
    order: -20,
    setup() {
        const values = useViewSettingsValues();
        const query = window.matchMedia('(prefers-color-scheme: dark)');

        const apply = () => {
            const html = document.documentElement;
            html.setAttribute('data-color-scheme', UtilViewSettings.resolveScheme(values.value.theme, query.matches));
            html.style.setProperty(UtilViewSettings.TEXT_SCALE_VAR, String(values.value.textScale / 100));
        };
        apply();
        query.addEventListener('change', apply);
        watch(values, apply, { deep: true });
    },
});
