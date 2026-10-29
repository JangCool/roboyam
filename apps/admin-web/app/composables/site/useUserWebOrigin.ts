/**
 * 공개 사이트(user-web) origin — 테마 화면 「실제 화면」 iframe(`ThemeLivePreview`)과 가젯 페이지 링크(`GadgetPagePicker`)가 쓴다.
 *
 * - `runtimeConfig.public.userWebOrigin`(env `NUXT_PUBLIC_USER_WEB_ORIGIN`)이 있으면 그것 — 개발 서버 기본은 로컬 공개 사이트(3100).
 * - 빌드한 화면(운영 jar)은 그 값이 비어 있다(`nuxt.config` 의 `$production`) — 테마 응답의 사이트 공개 주소(`SiteThemeResponse.publicUrl`)를 테마 화면이 넣는다(`setFromServer`).
 *   화면 빌드에 운영 주소를 박지 않으므로 이미지는 환경마다 한 벌이다.
 * - 둘 다 없으면 빈 문자열 — 「실제 화면」은 띄우지 않고 안내만 보인다.
 */
export function useUserWebOrigin() {
    const configured = toOrigin(useRuntimeConfig().public.userWebOrigin);
    const fromServer = useState<string>('user-web-origin', () => '');
    const origin = computed(() => configured || fromServer.value);

    /** 서버가 준 공개 주소(없으면 null)를 기억한다 — origin(스킴·호스트·포트)만 */
    function setFromServer(url: string | null | undefined): void {
        fromServer.value = toOrigin(url);
    }

    return { origin, setFromServer };
}

/** `http(s)://호스트[:포트][/…]` → origin. 그 밖은 빈 문자열 */
function toOrigin(value: unknown): string {
    if (typeof value !== 'string' || !value.trim()) return '';
    try {
        const url = new URL(value.trim());
        return url.protocol === 'http:' || url.protocol === 'https:' ? url.origin : '';
    } catch {
        return '';
    }
}
