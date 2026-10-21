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
 * 파일을 늘리면 두 언어의 `locales/{언어}/index.ts` 에 한 줄씩 더한다(파일 이름이 키의 첫 마디). `import.meta.glob` 자동 수집은 쓰지 않는다 — Nitro 서버 번들에서 변환되지 않아 서버가 500 이 된다.
 *
 * ⚠ **설정 파일 위치는 모듈이 정한다.** 각 레이어(와 앱)의 **루트 아래 `i18n/`** (`restructureDir` 기본값)이다 — `app/i18n/` 에 두면 "설정 파일을 못 찾았다"고 하고 조용히 건너뛴다.
 *    레이어마다 자기 `i18n/i18n.config.ts`(문구)를 두면 모듈이 모든 레이어의 것을 읽어 `messages` 를 깊게 합친다(앱 → 레이어 순으로 모두). 로캘 목록(`locales`)과 언어 기본값은 이 레이어만 정한다.
 */
export default defineNuxtConfig({
    $meta: { name: 'i18n' },

    modules: ['@nuxtjs/i18n'],

    i18n: {
        strategy: 'no_prefix',
        defaultLocale: 'ko',
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
