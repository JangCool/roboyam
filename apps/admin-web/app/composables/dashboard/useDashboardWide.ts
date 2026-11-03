/**
 * 대시보드를 운영자 구성대로(12칸) 그릴 만큼 넓은가 — lg(1024px) 이상. 좁으면 한 열로 쌓고 화면 편집을 막는다.
 * 격자(`DashboardGrid`)와 페이지(편집 단추)가 같은 경계를 쓴다. 부른 곳이 사라지면 감시도 푼다.
 */
const LG_QUERY = '(min-width: 1024px)';

export function useDashboardWide(): Readonly<Ref<boolean>> {
    const wide = ref(import.meta.client ? window.matchMedia(LG_QUERY).matches : true);
    let mq: MediaQueryList | null = null;
    const onChange = (e: MediaQueryListEvent) => {
        wide.value = e.matches;
    };
    onMounted(() => {
        mq = window.matchMedia(LG_QUERY);
        wide.value = mq.matches;
        mq.addEventListener('change', onChange);
    });
    onBeforeUnmount(() => mq?.removeEventListener('change', onChange));
    return readonly(wide);
}
