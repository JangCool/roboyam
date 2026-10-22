import tailwindcss from '@tailwindcss/vite';
import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

/**
 * 화면은 Nuxt SPA 다 (`ssr: false`).
 *
 * 운영에는 Node 런타임이 없다. `pnpm generate` 가 만든 정적 파일을 Spring Boot 가
 * jar 안에서 서빙하므로 화면과 API 가 같은 출처다 (CORS 설정 불필요).
 * 그래서 화면 코드는 `/api/...` 만 부르면 되고 **local · dev · prod 에서 같은 코드**가 된다 —
 * baseURL 을 환경마다 바꾸는 방식은 어느 한쪽에서만 되는 코드를 만든다.
 *
 * 환경별로 다른 것은 개발 서버(로컬)가 API 를 어디로 잇느냐뿐이다.
 *   API_ORIGIN  기본 http://localhost:8910 (local 프로파일의 api)
 *   NUXT_PORT   기본 3100
 *
 * 개발 서버에서는 아래 `/api` 프록시가 api 서버(`API_ORIGIN`)로 이어 준다. 설치 마법사(`useInstallApi`)·게시판(`useBoardApi`·`usePostApi`·`useCommentApi`·`useReportApi`)은 이 프록시로 실제 서버를 부르고, 로그인·셸은 아직 목업이다.
 *
 * 원본(community)과 달라진 점 — 마법사에 필요 없는 것을 뺐다: `extends` 레이어 중 rich-editor(display-time 은 공유 패키지 `@roboyam/display-time` 으로 가져왔다), tiptap, palettes/text-scale/home-gadget css(보기 설정),
 * 사이트 아이콘·웹 앱 매니페스트 링크, 소셜 로그인 프록시(`/oauth2`·`/login/oauth2`).
 */
const API_ORIGIN = process.env.API_ORIGIN ?? 'http://localhost:8910';

/** 강조색이 파랑이라 PrimeVue 기본(에메랄드) 대신 파랑을 쓴다. */
const RoboyamPreset = definePreset(Aura, {
    semantic: {
        primary: {
            50: '{blue.50}',
            100: '{blue.100}',
            200: '{blue.200}',
            300: '{blue.300}',
            400: '{blue.400}',
            500: '{blue.500}',
            600: '{blue.600}',
            700: '{blue.700}',
            800: '{blue.800}',
            900: '{blue.900}',
            950: '{blue.950}',
        },
        /*
         * 흐린 글자(`text-muted-color` — 날짜·조회·시각·배지 등 11~13px 보조 글자)의 라이트 색.
         * 기본 surface.500 은 옅은 회색 면 위에서 4.5:1 미만이 되는 곳이 있어, 라이트만 한 단계 진한 surface.600 으로 올린다.
         * `light-dark()` 의 뒤 값(다크)은 Aura 기본(surface.400 · hover surface.300)과 같다.
         */
        text: {
            mutedColor: 'light-dark({surface.600}, {surface.400})',
            hoverMutedColor: 'light-dark({surface.700}, {surface.300})',
        },
    },
    components: {
        /*
         * 안내 상자 Message 의 경고(warn) 글자색 — 기본 yellow.600 은 연한 노랑 바탕 위에서 2.84:1(4.5 미만)이다.
         * 라이트만 한 단계 진한 yellow.700 으로 → 바탕 위 4.76:1. 다크는 Aura 기본(yellow.500)과 같다.
         */
        message: {
            warn: {
                color: 'light-dark({yellow.700}, {yellow.500})',
                outlined: { color: 'light-dark({yellow.800}, {yellow.500})' },
                simple: { color: 'light-dark({yellow.800}, {yellow.500})' },
            },
        },
    },
});

export default defineNuxtConfig({
    // 두 앱이 같이 쓰는 공유 레이어(apps-packages/*) — 패키지 이름으로 extends 한다. 규약은 .claude/guides/frontend/layers.md
    extends: ['@roboyam/display-time', '@roboyam/i18n'],
    ssr: false,
    compatibilityDate: '2026-08-01',
    devtools: { enabled: true },

    devServer: {
        port: Number(process.env.NUXT_PORT ?? 3100),
    },

    nitro: {
        /*
         * 빌드 산출물을 api 의 정적 자원 자리로 바로 내보낸다.
         * :server:api:processResources 가 그대로 jar 에 담는다 — 중간에 복사하는 단계를 두지 않는다.
         * 이 앱은 `apps/user-web` 이라 저장소 루트까지 두 단계 위다.
         *
         * ⚠ 이 자리는 빌드가 만드는 곳이라 저장소에 넣지 않는다(.gitignore).
         * ⚠ `pnpm generate` 로 만든다. `nuxt build` 는 SPA 에 필요 없는 서버 번들까지 만든다.
         */
        output: {
            publicDir: '../../server/api/src/main/resources/static',
        },

        /*
         * 개발 전용 API 프록시. 운영에는 관여하지 않는다.
         * ⚠ target 에 `/api` 까지 붙인다. 프록시는 매칭된 접두어를 떼고 보내므로 안 붙이면
         *   백엔드에 `/system/info` 로 도착해 404 가 난다.
         */
        devProxy: {
            '/api': {
                target: API_ORIGIN + '/api',
                changeOrigin: true,
            },
            // 사이트 설정 이미지(`/media/**` — 올린 로고·아이콘, 기본 아이콘은 `/media/default/**`)와 웹 앱 매니페스트 — 같은 api 서버가 서빙한다. 마찬가지로 target 에 접두어를 붙인다.
            '/media': {
                target: API_ORIGIN + '/media',
                changeOrigin: true,
            },
            '/manifest.webmanifest': {
                target: API_ORIGIN + '/manifest.webmanifest',
                changeOrigin: true,
            },
        },
    },

    modules: ['@primevue/nuxt-module'],

    // 화면 문구 — 모듈과 로캘 목록은 `@roboyam/i18n` 레이어가 정한다. 이 앱의 문구 설정 파일(`i18n/i18n.config.ts`)만 알린다(`i18n` 키가 있는 레이어·앱만 모듈이 읽는다)
    i18n: { vueI18n: 'i18n.config.ts' },

    // 폴더명을 컴포넌트 이름에 붙이지 않는다
    components: [{ path: '~/components', pathPrefix: false, extensions: ['vue'] }],

    // 컴포저블은 업무 폴더(composables/<업무>/…, API 는 <업무>/api/, 공용은 common/)로 나눈다 — 자동 임포트가 하위 폴더까지 훑게 한다.
    // ⚠ `dirs` 에 '!composables/**/*.types.ts' 같은 부정 글롭을 적어도 듣지 않는다 — Nuxt 가 각 항목을 srcDir 기준 경로로 바꾸므로 `!` 가 파일 이름 앞에 붙은 경로가 된다.
    // 그래서 제외는 아래 `hooks` 의 `imports:dirs` 에서 (경로를 푼 뒤) 한다.
    imports: { dirs: ['composables/**'] },

    hooks: {
        // 타입 파일(*.types.ts)과 목업(mock/)의 export 는 자동 임포트로 올리지 않는다(이름 충돌·목업이 전역으로 새는 것을 막는다). 필요한 곳은 명시적으로 import 한다.
        // 앱뿐 아니라 extends 한 레이어(apps-packages/*)의 `composables/**` 항목에도 똑같이 건다 — 훅은 경로가 풀린 전체 목록을 받는다. (레이어 쪽 훅은 앱의 같은 이름 훅에 가려져 돌지 않는다)
        'imports:dirs': (dirs: string[]) => {
            for (const dir of [...dirs]) {
                const glob = dir.replaceAll('\\', '/');
                if (!glob.endsWith('/composables/**')) continue;
                const root = glob.slice(0, -'/**'.length);
                dirs.push(`!${root}/**/*.types.ts`, `!${root}/**/mock/**`);
            }
        },
    },

    // Tailwind 4 는 설정 파일이 없다 (CSS-first — main.css 의 @theme)
    vite: { plugins: [tailwindcss()] },

    css: ['~/assets/css/main.css', 'primeicons/primeicons.css'],

    primevue: {
        options: {
            theme: {
                preset: RoboyamPreset,
                options: {
                    darkModeSelector: '[data-color-scheme="dark"]',
                    cssLayer: {
                        name: 'primevue',
                        order: 'theme, base, primevue',
                    },
                },
            },
            ripple: true,
        },
        components: { prefix: '' },
    },

    typescript: { strict: true, typeCheck: false },

    app: {
        head: {
            // 기본 제목을 두지 않는다 — 운영에서는 서버가 초기 `index.html` 의 `<head>`(제목·소개·탭 아이콘·manifest·링크 미리보기)를 사이트 설정으로 채우는데,
            // 여기 기본 제목이 있으면 화면이 뜰 때 그것을 덮어쓴다. 쪽 제목은 각 화면(`useSeoMeta`)과 `app.vue` 의 `titleTemplate` 이 정한다.
            htmlAttrs: { lang: 'ko' },
            meta: [{ name: 'viewport', content: 'width=device-width, initial-scale=1' }],
        },
    },
});
