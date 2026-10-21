/**
 * vue-i18n 설정 (hamonica3 D-89) — 이 레이어의 문구(`common`)와 언어 기본값.
 *
 * **문구는 여기 없다** — 업무별 파일(`locales/{언어}/{업무}.ts`)을 `locales/{언어}/index.ts` 가 모은다. 여기에 다 적으면 한 파일이 수천 줄이 되어 무엇이 어디 있는지 못 찾는다.
 * 앱과 다른 레이어도 **자기 `i18n/i18n.config.ts`** 에서 자기 `index.ts` 를 import 해 `messages` 를 주면 모듈이 모든 설정의 `messages` 를 깊게 합친다.
 * ⚠ `import.meta.glob` 으로 자동 수집하지 않는다 — 이 설정 파일은 Vite(클라이언트)뿐 아니라 Nitro(서버)에서도 번들되는데 glob 은 Vite 전용이라, 서버에서는 변환되지 않고 남아
 *   모든 요청이 `Nuxt I18n server context has not been set up yet` 500 이 된다(Nitro 가 초기화 오류를 삼켜서 원인이 가려진다).
 *
 * `legacy: false` 는 Composition API 를 쓰겠다는 뜻이다 — 화면에서 `useI18n()` 으로 `t()` 를 꺼내 쓴다.
 *
 * ⚠ **`missingWarn` 을 끄지 않는다.** 번역이 빠진 키는 개발 중에 콘솔에 나와야 한다 — 조용히 키 이름이 화면에 나오면 아무도 못 알아챈다.
 * ⚠ `fallbackLocale: 'ko'` — 영어 문구가 아직 없는 키는 한국어로 보인다(키 이름이 아니라).
 */
import ko from './locales/ko';
import en from './locales/en';

export default defineI18nConfig(() => ({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages: { ko, en },
}));
