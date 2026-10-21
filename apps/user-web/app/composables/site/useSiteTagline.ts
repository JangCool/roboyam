/**
 * 사이트 슬로건(로고 밑 한 줄 소개)을 읽는다(바꾸지 않는다). 서버에서 받기 전에는 null.
 * @returns `tagline` 지금 슬로건 또는 null — 로고 밑 줄이 쓴다. **null 이면 그 줄을 그리지 않는다**(기본 문구를 두지 않는다).
 *
 * ⚠ 단순 버전 — `useSiteName` 과 같다. 설치 전에는 서버 값이 없으므로 항상 null 이다.
 */
export function useSiteTagline() {
    const state = useState<string | null>('site-tagline', () => null);
    const tagline = computed(() => state.value);
    return { tagline };
}
