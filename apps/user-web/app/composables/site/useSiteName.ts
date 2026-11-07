import SiteLogoMock from '~/composables/site/api/mock/site-logo-mock';
import UtilSiteName from '~/utils/util-site-name';

/**
 * 사이트 이름과 로고를 읽는다(바꾸지 않는다). 값은 앱 시작 때 `GET /api/site/info` 로 받아 사이트 정보 상태(`useSiteInfo`)에 둔 것이다 — 받기 전·못 받았을 때는 null.
 * @returns `siteName` 지금 사이트 이름 또는 null — 탭 제목이 쓴다(모르면 이름을 붙이지 않는다) ·
 *          `siteLabel` 글자로 꼭 보여야 하는 곳(로고·설명 문구)이 쓰는 이름 — 모르면 `UtilSiteName.DEFAULT` ·
 *          `siteLogoUrl` 운영자가 관리자 설정 「일반」에서 올린 로고 — 이미지 주소 또는 SVG data URI(그대로 `<img src>`, `v-html` 금지). 없으면 null(이름 글자만 보인다) ·
 *          `siteLogos` 로고 4칸(언어 × 바탕, `UtilSiteLogo`) — 고르기·대체 순서는 `UtilSiteLogo.resolve`, 그리는 곳은 `SiteBrand` ·
 *          `nameInLogo` 그림 로고에 사이트 이름이 들어 있는가 — 켜져 있으면 로고 옆 이름 글자를 붙이지 않는다(그림의 `alt` 가 이름).
 *
 * 이름은 서버가 이스케이프하지 않은 원문이므로 글자는 텍스트 보간(`{{ }}`)으로만 그린다.
 */
export function useSiteName() {
    const state = useSiteInfoState();
    const siteName = computed(() => state.value?.siteName ?? null);
    const siteLabel = computed(() => UtilSiteName.label(siteName.value));
    const siteLogoUrl = computed(() => state.value?.logoUrl ?? null);
    // MOCK: 서버가 아직 4칸 로고·이름 포함 여부를 주지 않는다 — 목업 그림 4개(지금의 단일 로고가 있으면 ① 자리) + 이름 포함 켜짐
    const siteLogos = computed(() => SiteLogoMock.logos(siteLogoUrl.value));
    const nameInLogo = computed(() => SiteLogoMock.NAME_IN_LOGO);
    // REAL: const siteLogos = computed(() => state.value?.logos ?? UtilSiteLogo.EMPTY);
    // REAL: const nameInLogo = computed(() => state.value?.logoNameIncluded ?? false);
    return { siteName, siteLabel, siteLogoUrl, siteLogos, nameInLogo };
}
