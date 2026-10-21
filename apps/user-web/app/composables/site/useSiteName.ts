import UtilSiteName from '~/utils/util-site-name';

/**
 * 사이트 이름과 로고를 읽는다(바꾸지 않는다). 서버에서 받기 전에는 null.
 * @returns `siteName` 지금 사이트 이름 또는 null — 탭 제목이 쓴다(모르면 이름을 붙이지 않는다) ·
 *          `siteLabel` 글자로 꼭 보여야 하는 곳(로고·설명 문구)이 쓰는 이름 — 모르면 `UtilSiteName.DEFAULT` ·
 *          `siteLogoUrl` 운영자가 관리자 설정 「일반」에서 올린 로고 — 이미지 주소 또는 SVG data URI(그대로 `<img src>`). 없으면 null(이름 글자만 보인다).
 *
 * ⚠ 단순 버전 — 원본(community)은 `GET /api/site/terms` 로 받은 값을 `useState` 와 localStorage 에 두는 `useSiteTerms.ts` 안에 있다.
 * 설치 전에는 서버 값이 없으므로 상태를 만들어 두기만 하고 채우지 않는다(항상 기본 이름·로고 없음). 홈 화면을 만들 때 원본의 로더를 가져와 이 상태를 채운다.
 * 로고도 같은 응답이 `logoUrl` 로 줄 후보다(관리자 일반 설정의 로고 계약 — `useSiteSettingsApi.ts` 머리말) — 비로그인에도 열려야 한다.
 */
export function useSiteName() {
    const state = useState<string | null>('site-name', () => null);
    const logoState = useState<string | null>('site-logo-url', () => null);
    const siteName = computed(() => state.value);
    const siteLabel = computed(() => UtilSiteName.label(state.value));
    const siteLogoUrl = computed(() => logoState.value);
    return { siteName, siteLabel, siteLogoUrl };
}
