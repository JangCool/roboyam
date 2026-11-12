import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';

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
 * 개발 서버에서는 아래 `/api` 프록시가 api 서버(`API_ORIGIN`)로 이어 준다. 설치 마법사(`useInstallApi`)·로그인과 소셜 로그인(`useAuthApi`·`useMeSocialApi`)·사이트 정보(`useSiteInfoApi`)·메뉴(`useMenuApi`)·게시판(`useBoardApi`·`usePostApi`·`useCommentApi`·`useReportApi`·에디터 이미지 `useImageApi`)·
 * 표시 값(`useDisplayApi`)·테마(`useThemeApi`)·회원 알림(`useNotificationApi`)·포인트 요약(`usePointApi().points`)은 이 프록시로 실제 서버를 부른다.
 * `// MOCK:` 인 곳(서버에 API 가 없다 — `docs/features/member-mypage-contract.md`): 내 프로필(`useProfileApi`) · 포인트 내역(`usePointApi().ledger`) · 내 알림 설정(`useNotificationSettingsApi`).
 * 계정 흐름(`docs/features/member-account-contract.md`): 이메일 가입·이메일/닉네임 확인·메일 링크 확인·비밀번호 찾기·재설정(`useAuthApi` 의 해당 메서드) · 내 계정·비밀번호 변경·이메일 변경(`useAccountApi`).
 * 회원 활동·둘러보기(`docs/features/member-activity-contract.md`): 최신·인기(`useHomeApi`)·통합 검색(`useSearchApi`, 게시판 하나·한 단어 검색만 실제)·공개 프로필(`useUserProfileApi`)·등급(`useGradeApi`)·제재(`useSanctionApi`)도 목업이다.
 * 포인트 상점(`docs/features/commerce-stage1-contract.md`): 품목·구매(`usePointShopApi`)·내 아이템·장착·환불(`useMyItemsApi`)·프로필 아바타(`useAvatarApi`)·프로필의 닉네임 변경권(`useProfileApi`)도 실제 서버다 — 구매·환불은 머리글 `Idempotency-Key` 를 보낸다.
 * 소셜 로그인 시작·콜백도 `/api/auth/social/**` 라 같은 프록시를 탄다 — 공급자에 등록하는 콜백 주소는 사이트 공개 주소(`site_settings.public_url`) 기준이므로 로컬에서 시험하려면 공개 주소를 이 개발 서버 주소로 맞춘다.
 * 소모임(`docs/features/group-contract.md` 1단계): 목록·분류·상세·가입·내 소모임·개설 신청·모임장 관리(`useGroupApi`)는 처음부터 실제 서버다(목업 없음).
 * 소통·개인 설정(`docs/features/member-social-contract.md`): 쪽지(`useDmApi` — 헤더 쪽지 아이콘·메뉴·햄버거·`/me` 의 「쪽지함」)·차단(`useBlockApi`)·이용자 메모(`useMemoApi`)·닉네임 그림(`useNicknameImageApi`)은 실제 서버, 보기 설정(`useViewSettingsApi`)은 목업이다(옛 성인 확인 `useAdultApi` 는 본인인증으로 바뀌어 지웠다 — adult-content-contract).
 *
 * 원본(community)과 달라진 점 — 마법사에 필요 없는 것을 뺐다: palettes/home-gadget css(보기 설정 — PrimeVue 글자 크기 `text-scale.css` 는 다시 옮겨 왔다)(display-time·rich-editor 레이어는 공유 패키지 `@irobit/display-time`·`@irobit/rich-editor` 로 가져왔다 — tiptap 은 rich-editor 패키지의 의존이고 `rich-text.css` 도 그 레이어가 넣는다),
 * 사이트 아이콘·웹 앱 매니페스트 링크, 원본의 소셜 로그인 프록시(`/oauth2`·`/login/oauth2` — irobit 은 `/api/auth/social/**` 를 쓴다).
 */
const API_ORIGIN = process.env.API_ORIGIN ?? 'http://localhost:8910';

export default defineNuxtConfig({
    // 두 앱이 같이 쓰는 공유 레이어(apps-packages/*) — 패키지 이름으로 extends 한다. 규약은 .claude/guides/frontend/layers.md
    // 가젯 배치(`@irobit/home-layout`)는 공개 홈(user-web)과 홈 배치 편집기(admin-web)가 같이 쓴다
    // 차트(`@irobit/chart` — ECharts)는 광고 성과 날짜별 추이가 쓴다
    // 방문 신호(`@irobit/analytics` — 방문자 ID·세션·`POST /api/visits`)는 이 앱만 쓴다. 켜기·미리보기 빼기·화면 종류는 `plugins/04.analytics.client.ts`
    extends: ['@irobit/display-time', '@irobit/rich-editor', '@irobit/i18n', '@irobit/home-layout', '@irobit/chart', '@irobit/analytics'],
    ssr: false,
    /*
     * 첫 화면 틀(`app/spa-loading-template.html`, srcDir 기준) — SPA 라 JS 를 받아 화면을 그릴 때까지 빈 흰 화면이던 자리에 공통 겉모양(상단 바 · 본문 카드 · 모바일 하단 탭)을 먼저 보인다
     * (사용자 결정 2026-11-13 「첫 로딩 체감 줄이기」). generate 가 그 HTML 을 index.html 의 <body> 에 넣고(`#__nuxt-loader`, 화면 위를 덮는 고정 층),
     * 앱이 그려지면(`app:suspense:resolve`) Nuxt 가 지운다 — 지우는 코드는 따로 없다. 모든 사용자에게 내려가므로 그 파일에는 긴 주석을 두지 않는다(index.html gzip 약 +2KB).
     *  - Vue·CSS 가 오기 전이라 그 파일 안의 색·크기만 쓴다(Tailwind·PrimeVue 변수 없음). 화면마다 다른 모양은 따라 그리지 않는다.
     *  - 맞춰 둔 값: 상단 바 높이 48px(`AppHeader` h-12) · 모바일 상단 탭 줄 40px · 본문 최대 폭 1168px(아이로빗·다음식 테마 `--content-max`) ·
     *    바탕 #f2f4f7 / 다크 #17181b · 카드 #fff / #202125(main.css 아이로빗 테마 값). 사이트 전체 틀을 바꾸면 그 숫자도 맞춘다. 다른 테마·색은 1~2초 뒤 진짜 화면으로 바뀐다.
     *  - 다크 판정은 `plugins/view-theme.client.ts` 와 같은 규칙(이 브라우저의 보기 설정 `irobit:view-settings` 의 theme — 자동이면 휴대폰 폭은 라이트, 그 밖은 기기 설정)으로
     *    `<html data-color-scheme>` 을 미리 건다 — 진짜 화면이 뜰 때 바탕이 번쩍 바뀌지 않는다. 플러그인이 같은 값을 다시 건다. 규칙을 바꾸면 두 곳을 같이 고친다.
     */
    spaLoadingTemplate: 'spa-loading-template.html',
    compatibilityDate: '2026-08-01',
    devtools: { enabled: true },

    runtimeConfig: {
        public: {
            /**
             * 관리자 미리보기(`?adminPreview=1`, 관리자 테마 화면의 iframe)가 메시지를 받을 관리자 주소들(쉼표로) — 이 주소에서 온 메시지만 받는다(`plugins/03.admin-preview.client.ts`).
             * 환경 변수 `NUXT_PUBLIC_ADMIN_ORIGINS`. 기본은 로컬 관리자 개발 서버. 빌드한 화면(`$production` — jar·운영 이미지)은 비워 두고, 공개 사이트 서버(api)가 `?adminPreview=1`
             * 응답 머리에 넣는 `<meta name="irobit-admin-origin">`(서버 환경 값 `ADMIN_URL`)을 쓴다 — 같은 응답에 frame-ancestors 도 그 주소로 붙는다(site-theme-layout-contract 「운영 배포」).
             */
            adminOrigins: 'http://localhost:3200,http://localhost:3201',
        },
    },

    $production: {
        runtimeConfig: { public: { adminOrigins: '' } },
    },

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

    // 오픈소스 라이선스 수집(`scripts/collect-licenses.mjs`)이 빌드할 때만(`IROBIT_OSS_SCAN`) — 번들에 들어간 바깥 패키지 폴더를 적는 모듈을 더한다(docs/features/oss-licenses-contract.md)
    // SPA 를 가볍게(`scripts/nuxt/spa-slim.mjs` — PrimeVue 실행 설정의 목록 빼기·미리 받기 끄기) — PrimeVue 모듈 뒤에 둔다
    modules: ['@primevue/nuxt-module', fileURLToPath(new URL('../../scripts/nuxt/spa-slim.mjs', import.meta.url)), ...(process.env.IROBIT_OSS_SCAN ? [fileURLToPath(new URL('../../scripts/oss-licenses/nuxt-bundle-scan.mjs', import.meta.url))] : [])],

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

    // text-scale.css(PrimeVue 위젯 글자 크기)는 보기 설정이 `<html>` 에 거는 `--text-scale` 을 본다(층 밖 — PrimeVue 변수를 이긴다)
    css: ['~/assets/css/main.css', '~/assets/css/text-scale.css', '~/assets/css/palettes.css', 'primeicons/primeicons.css'],

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
