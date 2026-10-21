/**
 * CSS 미디어 쿼리가 지금 맞는지 — 화면 폭에 따라 **동작**이 달라야 할 때만 쓴다
 * (예: 페이지 번호를 모바일 5개·데스크톱 10개로 그리기, 시트를 아래에서 올릴지 가운데에 띄울지).
 * 모양만 다른 것은 Tailwind 의 `md:` 로 한다 — 그쪽이 깜빡임이 없다.
 *
 * SPA 라 서버에서 실행될 일이 없지만, `window` 가 없으면 `false` 로 둔다.
 */
export function useMediaQuery(query: string): Ref<boolean> {
    const matches = ref(false);
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return matches;

    const list = window.matchMedia(query);
    matches.value = list.matches;
    const onChange = (event: MediaQueryListEvent) => {
        matches.value = event.matches;
    };
    list.addEventListener('change', onChange);
    onScopeDispose(() => list.removeEventListener('change', onChange));
    return matches;
}

/** 데스크톱 셸(왼쪽 메뉴가 보이는 폭) — Tailwind `md`(768px) 와 같은 경계 */
export function useIsDesktop(): Ref<boolean> {
    return useMediaQuery('(min-width: 768px)');
}
