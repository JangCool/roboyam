import { fileURLToPath } from 'node:url';

/**
 * 서식 에디터(TipTap) 공유 레이어 — user-web 이 `extends: ['@roboyam/rich-editor']` 로 쓴다(관리자 앱은 메일 템플릿 편집 때 같은 방법으로 붙인다). 사용법은 `.claude/guides/frontend/layers.md`.
 * community `layers/rich-editor`(도구 모음·서식·드래그 핸들·드롭존·`rich-text.css`)와 `frontend/app/editor/*`(이미지·올리기 자리·유튜브·첨부 수)·`components/write/RichEditor.vue` 를 옮겼다. 이모티콘은 로보얌에 없어 가져오지 않았다.
 *
 * 서버를 부르지 않는다: 이미지 올리기는 쓰는 앱이 함수(`RichEditor` 의 `upload` 속성)로 넘긴다.
 * 레이어는 앱을 모른다 — 이 폴더의 코드는 `~/`(앱 경로)를 쓰지 않고 `#layers/rich-editor/…` 또는 상대 경로만 쓴다.
 *
 * `@tiptap/*` 는 이 패키지의 `dependencies` 다 — 레이어 파일이 자기 `node_modules` 에서 찾으므로 community 처럼 앱 tsconfig 에 경로를 잇지 않아도 된다(그 처리는 레이어가 패키지가 아닐 때의 것).
 */
export default defineNuxtConfig({
    $meta: { name: 'rich-editor' },

    // 에디터 문구를 화면 언어로 — i18n 레이어에 의존한다
    extends: ['@roboyam/i18n'],

    // 이 레이어의 문구 설정 파일(`i18n/i18n.config.ts`)을 모듈에 알린다 — `i18n` 키가 있는 레이어만 모듈이 읽는다
    i18n: { vueI18n: 'i18n.config.ts' },

    // 컴포저블은 업무 폴더(composables/rich-editor/…)로 나눈다. `*.types.ts`·`mock/` 의 제외는 앱의 `imports:dirs` 훅이 한다
    imports: { dirs: ['composables/**'] },

    // 컴포넌트는 폴더명을 이름에 붙이지 않고, `.ts` 파일을 컴포넌트로 잡지 않는다(`.vue` 만)
    components: [{ path: fileURLToPath(new URL('./components', import.meta.url)), pathPrefix: false, extensions: ['vue'] }],

    // 본문 모양 — 에디터 안과 글 보기(`v-html`)가 같은 전역 클래스(`.rich-text`·`.rt-*`)를 쓴다
    css: [fileURLToPath(new URL('./assets/css/rich-text.css', import.meta.url))],
});
