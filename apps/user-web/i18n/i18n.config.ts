/**
 * user-web 의 화면 문구 — 업무 파일(`locales/{언어}/{업무}.ts`)을 `locales/{언어}/index.ts` 가 모은다. 업무 파일을 늘리면 두 언어의 `index.ts` 에 한 줄씩 더한다.
 * 로캘 목록·언어 기본값·공통 문구(`common`)는 공유 레이어 `@roboyam/i18n` 이 정하고, 모듈이 레이어와 이 앱의 `messages` 를 깊게 합친다. 규약은 `.claude/guides/frontend/i18n.md`.
 * (`import.meta.glob` 자동 수집은 쓰지 않는다 — 이 파일은 Nitro 서버에서도 번들되는데 glob 은 Vite 전용이라 서버가 500 이 된다.)
 */
import ko from './locales/ko';
import en from './locales/en';

export default defineI18nConfig(() => ({
    messages: { ko, en },
}));
