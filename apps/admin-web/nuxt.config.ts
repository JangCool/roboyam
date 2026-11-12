import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';

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
 * 인증·역할·역할 유형·운영자·초대·등급 설정·회원 조회·배치·사이트 설정·내 정보·감사 로그·게시판 관리(목록·설정·기본 설정 하위 화면·매니저/스텝·개인정보 보유기간)·메뉴 관리(공개 사이트 메뉴·관리자 사이트 메뉴) API 는 실제 서버(`/api/admin/**`)를 부르고 아래 `/api` 프록시가 그 요청을 잇는다. 알림(종 알림 서랍, 알림 이벤트 관리, 알림 도구, 조직 구성(팀), 내 정보의 이벤트별 알림)·포인트(포인트 내역·포인트 정책·수동 지급/차감)·포인트 상점(품목·아바타 묶음 변경·구매 내역·예외 환불·설정)도 실제 서버를 부른다.
 * 알림 템플릿·문구(`/settings/notification-templates/**`·`/settings/notification-messages/**`)와 회원 이벤트(`/settings/notifications/member`)·소모임(목록·개설 승인·분류·설정)도 실제 서버를 부른다 — 지금 목업으로 남은 영역은 커머스 2b-1(공구 묶음 `/group-buys`·`/group-buy-*`, 커머스 설정의 2b 구획, 대시보드 커머스 칸 — `?mock=` 스위치는 각 목업 파일 머리)이다. 새 화면을 목업으로 시작할 때는 `composables/<업무>/api/mock/` 에 두고 아래 훅이 자동 임포트에서 뺀다.
 * 사이트 설정의 이미지 주소(로고·아이콘·링크 미리보기 이미지)는 `/media/…` 상대 주소다 — 운영은 같은 도메인의 서버가 서빙하고, 개발 서버는 아래 `/media` 프록시가 같은 admin 서버로 잇는다.
 * 세션 쿠키(`ADMIN_SESSION`)·CSRF 쿠키(`XSRF-TOKEN`)는 프록시를 지나 개발 서버 주소(`localhost:NUXT_PORT`)의 쿠키로 저장된다(쿠키는 포트를 가르지 않는다).
 */
const API_ORIGIN = process.env.API_ORIGIN ?? 'http://localhost:8920';

export default defineNuxtConfig({
    // 두 앱이 같이 쓰는 공유 레이어(apps-packages/*) — 패키지 이름으로 extends 한다. 규약은 .claude/guides/frontend/layers.md
    // 가젯 배치(`@irobit/home-layout`)는 공개 홈(user-web)과 홈 배치 편집기(admin-web)가 같이 쓴다
    // 서식 에디터(`@irobit/rich-editor`)는 메일 템플릿 편집이 쓴다(간단 수준)
    // 차트(`@irobit/chart` — ECharts)는 광고 성과 날짜별 추이와 방문 분석(추이 · 이동 흐름 sankey)이 쓴다
    extends: ['@irobit/display-time', '@irobit/rich-editor', '@irobit/i18n', '@irobit/home-layout', '@irobit/chart'],
    ssr: false,
    compatibilityDate: '2026-08-01',
    devtools: { enabled: true },

    devServer: {
        port: Number(process.env.NUXT_PORT ?? 3200),
    },

    runtimeConfig: {
        public: {
            /**
             * 공개 사이트(user-web) 주소 — 테마 화면 「실제 화면」이 iframe 으로 `{주소}/?adminPreview=1` 을 띄운다(`ThemeLivePreview`).
             * 환경 변수 `NUXT_PUBLIC_USER_WEB_ORIGIN`. 기본은 로컬 공개 사이트 개발 서버. 빌드한 화면(`$production` — jar·운영 이미지)은 비워 두고 테마 응답의 사이트 공개 주소를 쓴다
             * (`useUserWebOrigin` — 운영 주소를 화면 빌드에 박지 않는다. site-theme-layout-contract 「운영 배포」).
             */
            userWebOrigin: 'http://localhost:3100',
        },
    },

    $production: {
        runtimeConfig: { public: { userWebOrigin: '' } },
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
            // 사이트 설정 이미지(`/media/**`, 기본 아이콘·OG 는 `/media/default/**`) — 같은 admin 서버가 서빙한다. 마찬가지로 target 에 `/media` 를 붙인다.
            '/media': {
                target: API_ORIGIN + '/media',
                changeOrigin: true,
            },
        },
    },

    // 오픈소스 라이선스 수집(`scripts/collect-licenses.mjs`)이 빌드할 때만(`IROBIT_OSS_SCAN`) — 번들에 들어간 바깥 패키지 폴더를 적는 모듈을 더한다(docs/features/oss-licenses-contract.md)
    // SPA 를 가볍게(`scripts/nuxt/spa-slim.mjs` — PrimeVue 실행 설정의 목록 빼기·미리 받기 끄기) — PrimeVue 모듈 뒤에 둔다
    modules: ['@primevue/nuxt-module', fileURLToPath(new URL('../../scripts/nuxt/spa-slim.mjs', import.meta.url)), ...(process.env.IROBIT_OSS_SCAN ? [fileURLToPath(new URL('../../scripts/oss-licenses/nuxt-bundle-scan.mjs', import.meta.url))] : [])],

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

    // Tabler Icons(MIT, `@tabler/icons` 3.49.0) — 패키지 `exports` 가 `icons/*` 만 열어 두어 목록 파일(`tabler-nodes-outline.json`·`icons.json`)을 이 별칭으로 연다.
    // 가젯 제목 아이콘 고르기(`utils/util-icon-catalog.ts`)가 고르기 창을 열 때만 `?raw` 로 늦게 읽는다(관리자 화면만 — 공개 사이트는 고른 아이콘의 path 만 저장해 그린다)
    alias: { '#tabler-icons': fileURLToPath(new URL('./node_modules/@tabler/icons', import.meta.url)) },

    css: ['~/assets/css/main.css', 'primeicons/primeicons.css'],

    primevue: {
        // SPA 라 컴포넌트 스타일을 HTML 에 미리 넣지 않는다 — 켜 두면 빌드가 등록된 모든 컴포넌트(안 쓰는 DataTable·Galleria 까지)의 스타일 약 49만 자를 index.html 에 박는다.
        // 화면이 뜰 때 PrimeVue 가 쓰는 컴포넌트의 스타일만 넣는다(`scripts/nuxt/spa-slim.mjs`)
        loadStyles: false,
        // 테마(프리셋·다크 모드·CSS 층)는 `app/theme/primevue-theme.ts` 를 화면 번들로 불러온다 — `options.theme` 에 두면 Aura 전체 토큰(약 11만 자)이 실행 설정으로 index.html 에 박힌다
        importTheme: { as: 'IrobitTheme', from: fileURLToPath(new URL('./app/theme/primevue-theme.ts', import.meta.url)) },
        options: {
            // PrimeUI 라이선스(PrimeVue 5) — PrimeVue 가 설치되는 순간 키를 확인하므로 app.vue 가 아니라 여기서 넘긴다(늦게 넘기면 「Invalid PrimeUI License」 배너).
            // 값은 이 앱의 .env.development(dev)·.env.production(generate) 의 VITE_PRIMEUI_LICENSE_KEY — nuxt.config 를 읽기 전에 --dotenv 파일이 process.env 에 들어온다.
            // primevue 5.0.2 의 PrimeVueConfiguration 타입(nuxt-module 이 쓰는 primevue/config)에 license 가 빠져 있어 펼침으로 넘긴다 — 실행 코드(@primevue/core setup)는 options.license 를 읽는다
            ...({ license: process.env.VITE_PRIMEUI_LICENSE_KEY || undefined } as Record<string, unknown>),
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
