/**
 * 하단 탭 「메뉴」 서랍의 열림 상태(공유) — 하단 탭 단추가 열고(`toggle`) 밝히며, 서랍(`AppMenuDrawer`)이 주소가 바뀌면 닫는다.
 */
export function useMenuDrawer() {
    const open = useState('menu-drawer-open', () => false);
    function toggle(): void {
        open.value = !open.value;
    }
    return { open, toggle };
}
