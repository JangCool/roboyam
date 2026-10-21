/**
 * 사이트 슬로건(로고 밑 한 줄 소개)을 읽는다(바꾸지 않는다). 값은 `GET /api/site/info` 의 `tagline` 이다(`useSiteInfo`) — 받기 전·못 받았을 때·운영자가 비웠을 때는 null.
 * @returns `tagline` 지금 슬로건 또는 null — 로고 밑 줄이 쓴다. **null 이면 그 줄을 그리지 않는다**(기본 문구를 두지 않는다). 글자는 텍스트 보간으로만 그린다.
 */
export function useSiteTagline() {
    const state = useSiteInfoState();
    const tagline = computed(() => state.value?.tagline ?? null);
    return { tagline };
}
