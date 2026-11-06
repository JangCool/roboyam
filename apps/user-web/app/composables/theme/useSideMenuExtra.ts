import type { RouteLocationRaw } from 'vue-router';

/** 사이드 메뉴 아래에 화면이 덧붙이는 구역 한 줄 */
export interface SideMenuExtraItem {
    key: string;
    label: string;
    to: RouteLocationRaw;
    active: boolean;
}

/** 사이드 메뉴 아래에 화면이 덧붙이는 구역 — 제목 + 줄들 */
export interface SideMenuExtra {
    title: string;
    items: SideMenuExtraItem[];
}

/**
 * 화면이 사이드 메뉴 칸(`AppSideMenu`) 아래에 자기 구역을 덧붙인다(예: 소모임 상세의 게시글·일정·멤버·채팅 — 탭 대신, 2026-11-06 사용자). 셸과 화면이 **같은 상태**를 본다(`useState`).
 * 사이드 칸은 데스크톱(md 이상)이고 사이드가 있는 배치에만 있으므로, 화면은 `hostShown` 이 아니거나 모바일이면 같은 링크를 자기 안에 그려야 한다(갈 길이 없어지지 않게).
 * 컴포저블은 setup 에서 부른다.
 *
 * @returns `extra` 덧붙인 구역(없으면 null) · `hostShown` 사이드 칸이 그려져 있나(셸이 정한다) · `use(source)` 화면이 구역을 걸고, 화면을 떠나면 걷는다.
 */
export function useSideMenuExtra() {
    const extra = useState<SideMenuExtra | null>('side-menu-extra', () => null);
    const hostShown = useState<boolean>('side-menu-extra-host', () => false);

    /** 화면이 구역을 건다 — 값이 바뀌면 따라가고, 화면을 떠나면 걷는다 */
    function use(source: () => SideMenuExtra | null): void {
        watch(source, (value) => (extra.value = value), { immediate: true, deep: true });
        onBeforeUnmount(() => (extra.value = null));
    }

    return { extra, hostShown, use };
}
