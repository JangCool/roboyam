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
 * (화면 문구는 쓰는 화면 조각이 가져온다 — `@roboyam/i18n` 의 `buildtime/business-messages.ts`. 첫 화면 틀(머리글·메뉴 등)이 쓰는 업무는 엔트리의 정적 의존이라 저절로 `modulepreload` 된다.)
 */
export default function spaSlim(_options, nuxt) {
    const primevue = nuxt.options.runtimeConfig.public.primevue;
    if (primevue) {
        const { usePrimeVue, options } = primevue;
        nuxt.options.runtimeConfig.public.primevue = { usePrimeVue, options };
    }

    nuxt.hook('build:manifest', (manifest) => {
        for (const chunk of Object.values(manifest)) {
            chunk.prefetch = false;
        }
    });
}
