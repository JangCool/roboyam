import { fileURLToPath } from 'node:url';

/**
 * 차트 공유 레이어 — user-web(사업자 광고 성과) · admin-web(운영자 광고 성과)이 `extends: ['@roboyam/chart']` 로 쓴다.
 * Apache ECharts(`echarts` — 이 패키지의 의존, 래퍼 없이 `echarts/core` 에서 필요한 것만 등록)로 그린다. 사용법은 `.claude/guides/frontend/layers.md`.
 *
 * 레이어는 앱을 모른다 — 서버를 부르지 않고 앱의 i18n 키도 모른다. 화면 문구·숫자·날짜 글자는 앱이 번역·서식해 props 로 넘긴다.
 * 이 폴더의 코드는 `~/`(앱 경로)를 쓰지 않는다. 모양은 컴포넌트 안 scoped CSS 로만 그린다(Tailwind 클래스를 쓰지 않아 앱 `main.css` 에 `@source` 가 필요 없다).
 */
export default defineNuxtConfig({
    $meta: { name: 'chart' },

    // 이 레이어의 컴포넌트는 폴더명을 이름에 붙이지 않고, `.ts` 파일을 컴포넌트로 잡지 않는다(`.vue` 만)
    components: [{ path: fileURLToPath(new URL('./components', import.meta.url)), pathPrefix: false, extensions: ['vue'] }],
});
