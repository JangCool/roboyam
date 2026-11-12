import { fileURLToPath } from 'node:url';
import { defineNuxtConfig } from 'nuxt/config';

/**
 * 화면 문구(한국어·영어) 공유 레이어 — user-web · admin-web 이 `extends: ['@roboyam/i18n']` 로 쓴다(`@roboyam/display-time` 이 이 레이어에 의존하므로 display-time 을 쓰면 따라온다).
 * 규약은 `.claude/guides/frontend/i18n.md`.
 *
 * (hamonica3 `frontend/nuxt.config.ts` 의 i18n 블록 — D-89 — 을 옮겼다.)
 *
 * **주소로 언어를 가르지 않는다** (`strategy: 'no_prefix'`). 언어는 <b>표시 값</b>(`@roboyam/display-time` 의 `DisplayValues.language`)에서 온다 —
 * 주소에 `/en` 을 붙이면 같은 화면에 주소가 둘이 되고, "내 설정은 영어인데 주소는 한국어" 같은 상태가 생긴다.
 *
 * **브라우저 언어를 자동으로 따르지 않는다** (`detectBrowserLanguage: false`). 서버가 정한 값이 있는데 브라우저가 덮으면, 관리자가 사이트 기본값을 바꿔도 어떤 사람에게는 안 먹는다.
 *
 * **문구 파일은 `i18n/locales/{언어}/{업무}.ts`** — 언어별 한 파일이 아니라 업무별로 나눈다. 한 파일에 다 넣으면 수천 줄이 되어 무엇이 어디 있는지 못 찾는다.
 * **파일 이름이 업무 이름**(키의 첫 마디)이고 따로 목록에 등록하지 않는다 — 문구는 **쓰는 화면이 가져간다**(빌드 모듈 `build/business-messages.ts`: 코드의 `'업무.키'` 를 보고
 * 그 화면 조각에 그 업무의 한국어 문구를 싣는다. 다른 언어와 코드에서 못 찾은 키는 실행 중에 업무 단위로 받는다 — `runtime/business-messages.ts`).
 *
 * ⚠ **설정 파일 위치는 모듈이 정한다.** 각 레이어(와 앱)의 **루트 아래 `i18n/`** (`restructureDir` 기본값)이다 — `app/i18n/` 에 두면 "설정 파일을 못 찾았다"고 하고 조용히 건너뛴다.
 *    문구 파일은 레이어·앱마다 자기 루트의 `i18n/locales/{언어}/{업무}.ts` 에 두면 빌드 모듈이 모두 모은다(업무 이름은 레이어를 통틀어 하나 — 겹치면 빌드가 멈춘다).
 *    로캘 목록·이름과 언어 기본값(`i18n/i18n.config.ts`)은 이 레이어만 정한다.
 */
export default defineNuxtConfig({
    $meta: { name: 'i18n' },

    // 업무별 문구 나누기(`build/business-messages.ts`) — 문구를 쓰는 화면 조각에 싣는다(첫 화면은 첫 화면이 쓰는 업무만)
    modules: ['@nuxtjs/i18n', fileURLToPath(new URL('./build/business-messages.ts', import.meta.url))],

    i18n: {
        strategy: 'no_prefix',
        defaultLocale: 'ko',
        // 문구 파일(`file`)은 적지 않는다 — 문구는 모듈의 로캘 파일이 아니라 업무별로 쓰는 화면이 가져온다(`build/business-messages.ts`).
        // 예전에는 `i18n.config.ts` 의 `messages` 로 두 언어·모든 화면 문구를 첫 화면에서 한꺼번에 받았다(user-web 약 186KB gzip — 2026-11-12 측정).
        locales: [
            { code: 'ko', language: 'ko-KR', name: '한국어' },
            { code: 'en', language: 'en-US', name: 'English' },
        ],
        detectBrowserLanguage: false,
        // 설정 파일 이름을 적어 두면 못 찾을 때 경고가 나온다(적지 않으면 조용히 건너뛴다)
        vueI18n: 'i18n.config.ts',
    },

    // 컴포저블은 업무 폴더(composables/i18n/…)로 나눈다 — 자동 임포트가 하위 폴더까지 훑게 한다
    // (`*.types.ts`·`mock/` 의 제외는 앱의 `imports:dirs` 훅이 한다 — `.claude/guides/frontend/layers.md`)
    imports: { dirs: ['composables/**'] },
});
