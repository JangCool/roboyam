/**
 * 그룹 이름 링크(운영자가 폴더에 「누르면 가는 곳」을 골랐을 때)를 눌러 이동한 **뒤에** 할 일을 건다 — 상단 첫 줄의 그룹 탭이 이동하면서 그 그룹 하위 메뉴도 열어 두게 한다.
 * 고른 그룹은 주소가 바뀔 때 「지금 있는 곳이 든 그룹」으로 다시 맞춰지므로(`useSideGroupPick`·`DaumMenuBar`), 그 맞춤이 끝난 다음 틱에 할 일을 한다.
 * 운영자가 「이동만」(`linkClick: 'NAVIGATE'`)을 골랐거나 사이트 밖 주소·새 탭이면 하지 않는다. 이미 그 주소면 바로 한다. 이동이 막혀 주소가 안 바뀌면 3초 뒤 걸어 둔 것을 푼다.
 * 컴포저블은 setup 에서 부른다.
 *
 * @returns `afterNavigate(target, run)` — `target` 은 그룹의 `path`·`newTab`·`linkClick`.
 */
export function useAfterMenuNavigate() {
    const route = useRoute();
    let release: (() => void) | null = null;

    function afterNavigate(target: { path?: string | null; newTab?: boolean; linkClick?: 'NAVIGATE_OPEN' | 'NAVIGATE' }, run: () => void): void {
        release?.();
        const path = target.path;
        if (!path || target.newTab || target.linkClick === 'NAVIGATE' || !path.startsWith('/')) return;
        if (route.path === path.split(/[?#]/)[0]) {
            run();
            return;
        }
        const stop = watch(
            () => route.path,
            () => {
                done();
                nextTick(run);
            },
        );
        const timer = setTimeout(() => done(), 3000);
        function done() {
            stop();
            clearTimeout(timer);
            release = null;
        }
        release = done;
    }

    onBeforeUnmount(() => release?.());

    return { afterNavigate };
}
