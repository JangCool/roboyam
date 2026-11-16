import UtilSiteLogo from '~/utils/util-site-logo';
import UtilSiteName from '~/utils/util-site-name';

/**
 * 사이트 이름과 로고를 읽는다(바꾸지 않는다). 값은 앱 시작 때 `GET /api/site/info` 로 받아 사이트 정보 상태(`useSiteInfo`)에 둔 것이다 — 받기 전·못 받았을 때는 null.
 * @returns `siteName` 지금 사이트 이름 또는 null — 탭 제목이 쓴다(모르면 이름을 붙이지 않는다) ·
 *          `siteLabel` 글자로 꼭 보여야 하는 곳(로고·설명 문구)이 쓰는 이름 — 모르면 `UtilSiteName.DEFAULT` ·
 *          `siteLogos` 로고 4칸(언어 × 바탕, 서버 `logos` — 칸마다 이미지 주소 또는 SVG data URI, 그대로 `<img src>`, `v-html` 금지). 빈 칸은 null 이고 네 칸이 모두 비면 이름 글자만 보인다
 *            (기본 그림으로 대신 채우지 않는다). 고르기·대체 순서는 `UtilSiteLogo.resolve`, 그리는 곳은 `SiteBrand` ·
 *          `nameInLogo` 그림 로고에 사이트 이름이 들어 있는가(서버 `logoNameIncluded`) — 켜져 있으면 로고 옆 이름 글자를 붙이지 않는다(그림의 `alt` 가 이름).
 *
 * 이름은 서버가 이스케이프하지 않은 원문이므로 글자는 텍스트 보간(`{{ }}`)으로만 그린다.
 */
export function useSiteName() {
    const state = useSiteInfoState();
    const siteName = computed(() => state.value?.siteName ?? null);
    const siteLabel = computed(() => UtilSiteName.label(siteName.value));
    const siteLogos = computed(() => state.value?.logos ?? UtilSiteLogo.EMPTY);
    const nameInLogo = computed(() => state.value?.logoNameIncluded ?? false);
    return { siteName, siteLabel, siteLogos, nameInLogo };
}
