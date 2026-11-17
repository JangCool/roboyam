/**
 * 화면 조각(chunk)을 묶는 Nuxt 모듈 — user-web 이 `modules` 의 `@primevue/nuxt-module` **뒤에** 둔다(그 모듈이 등록한 부품 목록을 고친다).
 *
 * 번들러(rolldown) 기본값은 「같이 쓰는 화면 조합」마다 조각을 따로 만든다. 화면이 200쪽이라 조합이 많아 2KB 가 안 되는 조각이 수백 개 생기고,
 * 파일마다 따로 압축하니 겹치는 내용을 못 줄인다. 어느 화면이든 받는 것끼리 묶어 파일 수와 전송 크기를 줄인다.
 * 2026-11-17 측정(운영 빌드, 로그인 상태, JS·CSS gzip): 홈 466 → 422KB(파일 208 → 37개) · 글 상세 765 → 723KB · 게시판 목록 470 → 429KB.
 * 화면 하나를 고쳐 다시 배포했을 때 홈에서 다시 받는 양은 135 → 49KB(엔트리만 이름이 바뀐다 — vendor·shell 은 그대로 캐시에서 쓴다).
 *
 * 1. **묶음 네 개** (`build.rolldownOptions.output.codeSplitting.groups` — 운영 빌드의 화면 쪽에만 건다. 개발 서버는 조각을 만들지 않는다)
 *    - `vendor`  어느 화면이든 쓰는 바깥 라이브러리(Vue·라우터·i18n·unhead·PrimeVue 코어). 앱 코드를 가져오지 않아 배포해도 이름이 안 바뀐다.
 *                ⚠ `nuxt`·`@nuxtjs` 는 넣지 않는다 — Nuxt 실행 코드는 경로표(화면 조각 이름 목록)를 가져오므로 같이 묶으면 배포마다 이름이 바뀐다.
 *    - `editor`  글 편집기(tiptap·prosemirror·yjs) · `chart` 차트(ECharts). 쓰는 화면에서만 받는다.
 *                ⚠ 무거운 라이브러리는 꼭 제 묶음에 가둔다. 안 가두면 번들러가 여러 조각이 같이 쓰는 작은 도우미 함수를 그 조각에 놓는 수가 있고,
 *                  그러면 도우미 한 줄 때문에 모든 화면이 그 조각을 통째로 받는다(실험에서 ECharts 525KB 가 홈에 딸려 왔다).
 *    - `shell`   레이아웃 · 상단 바·메뉴·푸터(`components/shell`) · 테마 셸(`components/themes`) 과 거기 딸린 모듈.
 *    묶음은 잡힌 모듈이 정적으로 가져오는 모듈까지 함께 담는다(기본값 `includeDependenciesRecursively`). 끄면 조각끼리 실행 순서가 꼬여 화면이 안 뜬다.
 *    홈 가젯 묶음·「여러 화면이 같이 쓰는 코드」 묶음은 실험에서 글 상세가 오히려 커져 두지 않는다.
 *
 * 2. **PrimeVue 부품 전역 등록 끄기** — `@primevue/nuxt-module` 은 부품 184개를 전부 전역(`global: true`)으로 등록해,
 *    엔트리에 부품마다 「받아 오는 코드」와 조각 이름이 실린다. 템플릿의 `<Button>`·`<LazyMenu>` 는 Nuxt 자동 임포트가 빌드 때 풀어 주므로 전역 등록이 필요 없다.
 *    ⚠ 이름 글자로 PrimeVue 부품을 찾는 코드(`resolveComponent('Button')` · `<component :is="'Button'">`)는 전역 등록이 있어야 한다 — 쓰지 말고 부품을 import 해서 넘긴다.
 *
 * 새 무거운 바깥 라이브러리(수십 KB 이상)를 들이면 `editor`·`chart` 처럼 묶음을 하나 더한다. 넣은 뒤에는 홈이 받는 파일 수·크기를 다시 잰다(`.claude/guides/frontend/first-load.md`).
 */

// 모듈 주소는 Windows(`\`)·그 밖(`/`) 둘 다 온다. pnpm 은 `node_modules/.pnpm/<이름@판>/node_modules/<이름>/` 꼴이라 마지막 `node_modules/<이름>/` 만 본다
const SEP = '[\\\\/]';
const pkg = (names) => new RegExp(`node_modules${SEP}(?:${names.join('|')})${SEP}`);

const VENDOR = pkg([
    'vue',
    '@vue',
    'vue-router',
    'vue-i18n',
    '@intlify',
    'unhead',
    '@unhead',
    'ofetch',
    'ufo',
    'hookable',
    'defu',
    'destr',
    'ohash',
    'devalue',
    'unctx',
    'scule',
    'klona',
    'cookie-es',
    'perfect-debounce',
    `@primevue${SEP}core`,
    `@primeuix${SEP}(?:styled|utils)`,
    '@primeui',
]);
const EDITOR = pkg(['@tiptap', 'prosemirror-[a-z-]+', 'yjs', 'lib0', 'y-protocols', 'linkifyjs', 'orderedmap', 'rope-sequence', 'w3c-keyname', '@floating-ui']);
const CHART = pkg(['echarts', 'zrender', 'vue-echarts', 'tslib']);
const SHELL = new RegExp(`${SEP}app${SEP}(?:layouts${SEP}|components${SEP}(?:shell|themes)${SEP})`);

const CODE_SPLITTING = {
    groups: [
        { name: 'vendor', test: VENDOR, priority: 30 },
        { name: 'editor', test: EDITOR, priority: 28 },
        { name: 'chart', test: CHART, priority: 28 },
        { name: 'shell', test: SHELL, priority: 20 },
    ],
};

export default function chunkGroups(_options, nuxt) {
    nuxt.hook('vite:extendConfig', (config, env) => {
        if (!env?.isClient) return;
        // 서버 쪽 설정과 같은 객체를 나눠 쓰므로 고치지 않고 새로 만든다
        const rolldownOptions = config.build?.rolldownOptions ?? {};
        const output = rolldownOptions.output ?? {};
        config.build = {
            ...config.build,
            rolldownOptions: {
                ...rolldownOptions,
                output: Array.isArray(output) ? output.map((one) => ({ ...one, codeSplitting: CODE_SPLITTING })) : { ...output, codeSplitting: CODE_SPLITTING },
            },
        };
    });

    nuxt.hook('components:extend', (components) => {
        for (const component of components) {
            if (component.global && /(?:^|[\\/])primevue[\\/]/.test(component.filePath)) {
                component.global = false;
            }
        }
    });
}
