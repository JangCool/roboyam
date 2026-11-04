/**
 * 방문 분석 신호 공유 레이어 — user-web 이 `extends: ['@roboyam/analytics']` 로 쓴다(`docs/features/site-analytics-contract.md` 「옮기기 쉽게」).
 * 방문자 ID·세션(`utils/util-visit-store.ts`) · 신호 규칙(`utils/util-visit-signal.ts`) · 보내기(`utils/util-visit-transport.ts`) · 화면 이동 따라가기(`composables/analytics/useVisitTracking.ts`).
 * 나중에 수집 위치·도구(다른 서버·외부 분석 도구)를 바꿀 때 이 레이어만 고친다 — 보내는 곳은 `utils/util-visit-transport.ts` 한 파일이다.
 *
 * 레이어는 앱을 모른다 — 이 폴더의 코드는 `~/`(앱 경로)를 쓰지 않는다. 수집을 켤지(사이트 정보의 스위치)·뺄 창(관리자 미리보기)·화면 종류 분류는 앱이 `startVisitTracking` 에 넘긴다.
 * 앱은 `#layers/analytics/utils/…` 로 명시 import 한다.
 */
export default defineNuxtConfig({
    $meta: { name: 'analytics' },

    // 컴포저블은 업무 폴더(composables/analytics/…)로 나눈다 — 자동 임포트가 하위 폴더까지 훑게 한다.
    // `*.types.ts`·`mock/` 의 제외는 앱의 `imports:dirs` 훅이 한다(앱의 nuxt.config.ts 참고)
    imports: { dirs: ['composables/**'] },
});
