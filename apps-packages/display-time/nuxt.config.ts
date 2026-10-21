import { fileURLToPath } from 'node:url';

/**
 * 표시 값(언어·시간대·시간 형식·날짜 형식) 공유 레이어 — user-web · admin-web 이 `extends: ['@roboyam/display-time']` 로 쓴다.
 * 서버를 부르지 않는다: 값을 채우는 일(`useDisplay().setDisplay`)은 쓰는 앱이 한다. 사용법은 `.claude/guides/frontend/layers.md`.
 *
 * 레이어는 앱을 모른다 — 이 폴더의 코드는 `~/`(앱 경로)를 쓰지 않고 `#layers/display-time/…` 또는 상대 경로만 쓴다.
 */
export default defineNuxtConfig({
    $meta: { name: 'display-time' },

    // 표시 값의 언어를 화면 문구(i18n 로캘)와 PrimeVue 로캘에 먹이므로 i18n 레이어에 의존한다. 이 의존 덕에 display-time 을 extends 하면 i18n 도 따라온다
    extends: ['@roboyam/i18n'],

    // 이 레이어의 문구 설정 파일(`i18n/i18n.config.ts`)을 모듈에 알린다 — `i18n` 키가 있는 레이어만 모듈이 읽는다. 파일 이름을 적어 두면 못 찾을 때 경고가 나온다(조용히 건너뛰지 않는다)
    i18n: { vueI18n: 'i18n.config.ts' },

    // 컴포저블은 업무 폴더(composables/display/…)로 나눈다 — 자동 임포트가 하위 폴더까지 훑게 한다.
    // `*.types.ts`·`mock/` 의 제외는 앱의 `imports:dirs` 훅이 한다(모든 `composables/**` 항목에 적용된다 — 앱의 nuxt.config.ts 참고)
    imports: { dirs: ['composables/**'] },

    // 이 레이어의 컴포넌트는 폴더명을 이름에 붙이지 않고, `.ts` 파일을 컴포넌트로 잡지 않는다(`.vue` 만)
    components: [{ path: fileURLToPath(new URL('./components', import.meta.url)), pathPrefix: false, extensions: ['vue'] }],
});
