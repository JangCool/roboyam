/**
 * SPA 빌드를 가볍게 하는 Nuxt 모듈 — user-web · admin-web 이 `modules` 의 `@primevue/nuxt-module` **뒤에** 둔다(그 모듈이 채운 설정을 고친다).
 *
 * 두 앱은 `ssr: false` SPA 이고, `pnpm generate` 가 만든 `index.html` 을 Spring Boot 가 그대로 내보낸다(`index.html` 은 `no-cache` 라 화면을 열 때마다 받는다).
 * 2026-11-12 측정(user-web): index.html 692KB(gzip 83KB) → 3KB. 휴대폰에서 첫 화면이 느리던 원인이다.
 *
 * 1. **PrimeVue 실행 설정 줄이기** — `@primevue/nuxt-module` 은 등록한 컴포넌트·디렉티브·스타일 목록 전부를 `runtimeConfig.public.primevue` 에 넣는다.
 *    공개 실행 설정은 `index.html` 의 `window.__NUXT__.config` 로 그대로 박히므로 HTML 이 약 15만 자 커진다.
 *    실행 중(모듈의 `primevue-plugin`)에 읽는 것은 `usePrimeVue` · `options` 뿐이고 목록은 빌드할 때(자동 임포트·스타일 템플릿)만 쓰므로 둘만 남긴다.
 *    (컴포넌트 스타일을 HTML 에 넣는 것은 각 앱의 `primevue.loadStyles: false`, 테마 프리셋이 실행 설정에 실리는 것은 `primevue.importTheme` 이 막는다.)
 * 2. **미리 받기(prefetch) 끄기** — Nuxt 는 첫 화면 HTML 에 나머지 화면 조각 전부(user-web 354개, 약 1MB gzip — 편집기·차트·아이콘 SVG 포함)를
 *    `<link rel="prefetch">` 로 건다. 휴대폰에서는 첫 화면의 API·이미지 받기와 회선을 나눠 쓰므로 끈다. 화면 조각은 그 화면에 처음 들어갈 때 받는다.
 * 3. **기본 언어 문구를 첫 화면에서 같이 받기** — 문구는 언어별로 나눠 받는데(`@roboyam/i18n` 의 `locales[].file`), i18n 플러그인은 엔트리 JS 를 다 받아 실행한 **뒤에**
 *    문구 조각을 부르고 그 문구가 와야 화면을 그린다. 그 사이를 기다리지 않게 기본 언어(`ko`)의 문구 조각을 엔트리와 함께 `modulepreload` 로 건다(지연이 큰 휴대폰 회선에서 한 번 왕복이 준다).
 *    i18n 모듈이 문구 조각의 preload 를 끄는 훅보다 뒤에 돌아야 하므로 모든 모듈이 붙은 뒤(`modules:done`)에 훅을 건다.
 */

/** 첫 화면에서 같이 받을 기본 언어 문구 파일 — 레이어·앱마다 `i18n/locales/ko/index.ts` */
const DEFAULT_LOCALE_ENTRY = /(^|\/)i18n\/locales\/ko\/index\.ts$/;

export default function spaSlim(_options, nuxt) {
    const primevue = nuxt.options.runtimeConfig.public.primevue;
    if (primevue) {
        const { usePrimeVue, options } = primevue;
        nuxt.options.runtimeConfig.public.primevue = { usePrimeVue, options };
    }

    nuxt.hook('modules:done', () => {
        nuxt.hook('build:manifest', (manifest) => {
            for (const chunk of Object.values(manifest)) {
                chunk.prefetch = false;
            }

            const entry = Object.values(manifest).find((chunk) => chunk.isEntry);
            if (!entry) return;
            const preload = (key) => {
                const chunk = manifest[key];
                if (!chunk || chunk.preload) return;
                chunk.preload = true;
                for (const dep of chunk.imports ?? []) preload(dep);
            };
            for (const key of Object.keys(manifest)) {
                if (!DEFAULT_LOCALE_ENTRY.test(key.replaceAll('\\', '/'))) continue;
                preload(key);
                entry.imports = [...new Set([...(entry.imports ?? []), key])];
            }
        });
    });
}
