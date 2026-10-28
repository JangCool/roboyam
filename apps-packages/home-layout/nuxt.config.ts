/**
 * 홈 가젯 배치 공유 레이어 — user-web(공개 홈이 배치대로 그린다) · admin-web(홈 배치 편집기)이 `extends: ['@roboyam/home-layout']` 로 쓴다.
 * 순수 함수(`utils/util-home-layout.ts`)만 있다 — 서버를 부르지 않고 화면 부품·문구도 없다. 사용법은 `.claude/guides/frontend/layers.md`.
 *
 * 레이어는 앱을 모른다 — 이 폴더의 코드는 `~/`(앱 경로)를 쓰지 않는다. 앱은 `#layers/home-layout/utils/util-home-layout` 로 명시 import 한다.
 */
export default defineNuxtConfig({
    $meta: { name: 'home-layout' },
});
