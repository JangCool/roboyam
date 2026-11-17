import { fileURLToPath } from 'node:url';

/**
 * 회원 칭호 그림 공유 레이어 — user-web(작성자 줄·공개 프로필·내 칭호·명예의 전당) · admin-web(칭호 관리·회원 상세)이 `extends: ['@irobit/member-title']` 로 쓴다.
 * 그림은 확정 시안 `docs/features/member-title-icons-mockup.html` 의 12×12 픽셀 지도와 색표를 그대로 옮겼다(`utils/util-title-icon.ts`) — 등급 도트와 같은 방식, 이미지 파일 없음.
 * 계약: `docs/features/member-title-contract.md` 「아이콘」. 사용법은 `.claude/guides/frontend/layers.md`.
 *
 * 레이어는 앱을 모른다 — 서버를 부르지 않고 앱의 i18n 키도 모른다(칭호 이름은 앱이 `label` 로 넘긴다). 이 폴더의 코드는 `~/`(앱 경로)를 쓰지 않는다.
 * 모양은 컴포넌트 안 scoped CSS 로만 그린다(Tailwind 클래스를 쓰지 않아 앱 `main.css` 에 `@source` 가 필요 없다). 칩(알약)은 앱마다 다크 모드 판정이 달라 앱 쪽 `TitleChip` 이 그린다.
 */
export default defineNuxtConfig({
    $meta: { name: 'member-title' },

    // 이 레이어의 컴포넌트는 폴더명을 이름에 붙이지 않고, `.ts` 파일을 컴포넌트로 잡지 않는다(`.vue` 만)
    components: [{ path: fileURLToPath(new URL('./components', import.meta.url)), pathPrefix: false, extensions: ['vue'] }],
});
