import UtilSiteName from '~/utils/util-site-name';

/**
 * 사이트 이름과 로고를 읽는다(바꾸지 않는다). 값은 앱 시작 때 `GET /api/site/info` 로 받아 사이트 정보 상태(`useSiteInfo`)에 둔 것이다 — 받기 전·못 받았을 때는 null.
 * @returns `siteName` 지금 사이트 이름 또는 null — 탭 제목이 쓴다(모르면 이름을 붙이지 않는다) ·
 *          `siteLabel` 글자로 꼭 보여야 하는 곳(로고·설명 문구)이 쓰는 이름 — 모르면 `UtilSiteName.DEFAULT` ·
 *          `siteLogoUrl` 운영자가 관리자 설정 「일반」에서 올린 로고 — 이미지 주소 또는 SVG data URI(그대로 `<img src>`, `v-html` 금지). 없으면 null(이름 글자만 보인다).
 *
 * 이름은 서버가 이스케이프하지 않은 원문이므로 글자는 텍스트 보간(`{{ }}`)으로만 그린다.
 */
export function useSiteName() {
    const state = useSiteInfoState();
    const siteName = computed(() => state.value?.siteName ?? null);
    const siteLabel = computed(() => UtilSiteName.label(siteName.value));
    const siteLogoUrl = computed(() => state.value?.logoUrl ?? null);
    return { siteName, siteLabel, siteLogoUrl };
}
