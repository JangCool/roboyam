/**
 * vue-i18n 설정 (hamonica3 D-89) — 언어 기본값만.
 *
 * **문구는 여기 없다** — 업무별 파일(`i18n/locales/{언어}/{업무}.ts`, 레이어·앱마다)을 **쓰는 화면이 가져간다**(빌드 모듈 `build/business-messages.ts` ·
 * 실행 쪽 `runtime/business-messages.ts` · 잇는 플러그인 `plugins/business-messages.client.ts`). 여기에 `messages` 를 다시 넣으면 모든 화면 문구가 첫 화면에 실린다.
 * ⚠ 이 파일은 Nitro(서버)에서도 번들된다 — 가상 모듈·`import.meta.glob` 같은 Vite 전용 것을 import 하지 않는다(서버가 500 이 된다). `missing` 처리기도 그래서 플러그인에서 건다.
 *
 * `legacy: false` 는 Composition API 를 쓰겠다는 뜻이다 — 화면에서 `useI18n()` 으로 `t()` 를 꺼내 쓴다.
 *
 * ⚠ 번역이 빠진 키는 개발 중에 콘솔에 나와야 한다 — 조용히 키 이름이 화면에 나오면 아무도 못 알아챈다(플러그인의 `missing` 처리기가 알린다).
 * ⚠ `fallbackLocale: 'ko'` — 영어 문구가 아직 없는 키는 한국어로 보인다(키 이름이 아니라). 한국어 문구는 화면 조각이 늘 들고 온다.
 */
export default defineI18nConfig(() => ({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
}));
