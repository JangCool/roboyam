import tailwindcss from '@tailwindcss/vite';
import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

/**
 * 관리자 화면은 Nuxt SPA 다 (`ssr: false`) — user-web 과 같은 방식이다.
 *
 * 운영에는 Node 런타임이 없다. `pnpm generate` 가 만든 정적 파일을 관리자 서버(`server/admin`, Spring Boot)가
 * jar 안에서 서빙하므로 화면과 API 가 같은 출처다 (CORS 설정 불필요).
 * 그래서 화면 코드는 `/api/admin/...` 만 부르면 되고 **local · dev · prod 에서 같은 코드**가 된다.
 *
 * 환경별로 다른 것은 개발 서버(로컬)가 API 를 어디로 잇느냐뿐이다.
 *   API_ORIGIN  기본 http://localhost:8920 (local 프로파일의 admin 서버)
 *   NUXT_PORT   기본 3200
 *
 * 인증·역할·역할 유형·운영자·초대·등급 설정·회원 조회 API 는 실제 서버(`/api/admin/**`)를 부르고 아래 `/api` 프록시가 그 요청을 잇는다. 아직 서버가 없는 영역(사이트 설정·내 정보·감사·배치·알림)은 `// MOCK:` 이라 프록시를 타지 않는다.
 * 세션 쿠키(`ADMIN_SESSION`)·CSRF 쿠키(`XSRF-TOKEN`)는 프록시를 지나 개발 서버 주소(`localhost:NUXT_PORT`)의 쿠키로 저장된다(쿠키는 포트를 가르지 않는다).
 */
const API_ORIGIN = process.env.API_ORIGIN ?? 'http://localhost:8920';

/** 강조색이 파랑이라 PrimeVue 기본(에메랄드) 대신 파랑을 쓴다 — user-web 과 같다. */
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
        /* 보더 선 색 — 하모니카3 기준(`main.css` 의 `--line` · `--line-strong` 와 같다): 면 경계 라이트 surface-200 · 다크 surface-800, 입력 칸 경계 라이트 300 · 다크 700 */
        content: { borderColor: 'light-dark({surface.200}, {surface.800})' },
        /* 입력 칸 — 경계색은 위와 같고, 글자 크기는 하모니카3 기준(small 을 13px 로 한 단계만 줄여 앱 기본으로 쓴다 — 아래 `primevue.options.pt` 가 입력 칸 전부에 small 을 기본으로 건다) */
        formField: {
            borderColor: 'light-dark({surface.300}, {surface.700})',
            sm: { fontSize: '0.8125rem', paddingX: '0.625rem', paddingY: '0.4rem' },
        },
        /* 흐린 글자(`text-muted-color`)의 라이트 색을 한 단계 진하게 — 근거는 user-web 의 같은 설정 */
        text: {
            mutedColor: 'light-dark({surface.600}, {surface.400})',
            hoverMutedColor: 'light-dark({surface.700}, {surface.300})',
        },
    },
    components: {
        /* 안내 상자 Message 의 경고(warn) 글자색 — 연한 노랑 바탕에서 4.5:1 을 넘기게 한 단계 진하게(user-web 과 같다) */
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
        port: Number(process.env.NUXT_PORT ?? 3200),
    },

    nitro: {
        /*
         * 빌드 산출물을 admin 서버의 정적 자원 자리로 바로 내보낸다(`:server:admin:processResources` 가 그대로 jar 에 담는다).
         * 이 앱은 `apps/admin-web` 이라 저장소 루트까지 두 단계 위다.
         *
         * ⚠ 이 자리는 빌드가 만드는 곳이라 저장소에 넣지 않는다(.gitignore).
         * ⚠ `pnpm generate` 로 만든다. `nuxt build` 는 SPA 에 필요 없는 서버 번들까지 만든다.
         */
        output: {
            publicDir: '../../server/admin/src/main/resources/static',
        },

        /*
         * 개발 전용 API 프록시. 운영에는 관여하지 않는다.
         * ⚠ target 에 `/api` 까지 붙인다. 프록시는 매칭된 접두어를 떼고 보내므로 안 붙이면 백엔드에 `/admin/auth/me` 로 도착해 404 가 난다.
         */
        devProxy: {
            '/api': {
                target: API_ORIGIN + '/api',
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
    // ⚠ `dirs` 에 '!composables/**/*.types.ts' 같은 부정 글롭을 적어도 듣지 않는다(Nuxt 가 srcDir 기준 경로로 바꾸므로). 제외는 아래 `hooks` 의 `imports:dirs` 에서 한다.
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
                    darkModeSelector: '[data-theme="dark"]',
                    cssLayer: {
                        name: 'primevue',
                        order: 'theme, base, primevue',
                    },
                },
            },
            ripple: true,
            /*
             * 입력 칸의 기본 크기를 small 로 내린다(하모니카3 와 같다). PrimeVue 에는 전역 size 설정이 없고 size 프롭이 `p-*-sm` 클래스를 붙이는 방식이라
             * pass-through 로 그 클래스를 기본에 얹는다 — 화면마다 size="small" 을 반복하지 않아도 새 화면에 자동으로 적용된다. 크게 쓰려면 size="large" 를 명시한다.
             * ⚠ 여기 없는 컴포넌트는 PrimeVue 기본 크기로 나온다 — 크기가 있는 입력 컴포넌트를 새로 쓰면 함께 등록한다. 버튼·스위치·체크 등은 글자 크기와 무관해 건드리지 않는다.
             */
            pt: {
                inputtext: { root: { class: 'p-inputtext-sm' } },
                textarea: { root: { class: 'p-textarea-sm' } },
                select: { root: { class: 'p-select-sm' } },
                multiselect: { root: { class: 'p-multiselect-sm' } },
                inputchips: { root: { class: 'p-inputchips-sm' } },
                datepicker: { pcInputText: { root: { class: 'p-inputtext-sm' } } },
                inputnumber: { pcInputText: { root: { class: 'p-inputtext-sm' } } },
                autocomplete: { pcInputText: { root: { class: 'p-inputtext-sm' } } },
                password: { pcInputText: { root: { class: 'p-inputtext-sm' } } },
                listbox: { root: { class: 'p-listbox-sm' } },
            },
        },
        components: { prefix: '' },
    },

    typescript: { strict: true, typeCheck: false },

    app: {
        head: {
            // 기본 제목 — 사이트 이름을 알기 전(앱이 막 열렸을 때)의 이름. 이름을 받으면 app.vue 가 "쪽 제목 · 이름 관리자" 로 바꾼다
            title: '관리자',
            htmlAttrs: { lang: 'ko' },
            meta: [
                { name: 'viewport', content: 'width=device-width, initial-scale=1' },
                // 관리자 화면은 검색에 나오지 않게 한다(서버도 X-Robots-Tag 를 준다)
                { name: 'robots', content: 'noindex, nofollow' },
            ],
        },
    },
});
