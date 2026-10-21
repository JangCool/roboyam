import UtilAdminMenu, { type AdminMenuMode, type AdminMenuNode } from '~/utils/util-admin-menu';

/**
 * 지금 보일 메뉴의 모드(일반 메뉴 · 설정 메뉴)와 그 모드의 메뉴 — 사이드바·상단 메뉴·모바일 드로어가 같은 값을 읽는다(hamonica3 의 `useAppMenu`). 서버와 말하지 않는다.
 * 메뉴 정의는 `utils/util-admin-menu.ts` 한 곳(노드의 `mode` 칸)이고, 검색(`AppSearch`)은 모드와 무관하게 모든 메뉴를 찾는다 — 결과로 이동하면 모드는 주소를 따라 바뀐다.
 *
 * 모드 판정 규칙(`mode`):
 *   1. 지금 주소가 속한 메뉴 노드가 있으면 그 노드의 모드(새로고침·주소 직접 입력에도 맞는다).
 *   2. 어느 메뉴에도 속하지 않는 주소(`/me` 등)는 **마지막으로 있던 모드**를 유지한다 — 마지막 모드는 `setMode` 로 고른 값이거나 셸(`layouts/default.vue`)이 주소를 따라 적어 둔 값이다(`follow`).
 *   처음이면 일반 메뉴.
 * ⚠ 마지막 모드를 따라 적는 일(`follow`)은 셸에서 **한 번만** 건다 — 이 컴포저블은 부품마다 불리므로 여기서 `watch` 를 달면 부품 수만큼 쌓이고 서로의 값을 되돌린다(hamonica3 에서 겪은 문제).
 */
export function useAppMenu() {
    const route = useRoute();
    const { can } = useAdminPermission();

    /** 마지막으로 있던 모드 — 메뉴에 없는 주소에서 유지할 값 */
    const last = useState<AdminMenuMode>('admin-menu-mode', () => 'normal');

    /** 지금 보일 모드 */
    const mode = computed<AdminMenuMode>(() => UtilAdminMenu.modeOfPath(route.path) ?? last.value);
    const isSettings = computed(() => mode.value === 'settings');

    /** 지금 모드의 메뉴 — 권한이 없는 항목은 뺀다 */
    const nodes = computed<AdminMenuNode[]>(() => UtilAdminMenu.visible(can, mode.value));

    /** 설정 메뉴에 이 운영자가 열 수 있는 항목이 있는가 — 없으면 프로필 메뉴의 「설정」을 숨긴다(편의일 뿐 막는 것은 서버다) */
    const settingsNodes = computed<AdminMenuNode[]>(() => UtilAdminMenu.visible(can, 'settings'));
    const hasSettings = computed(() => settingsNodes.value.length > 0);
    /** 설정 메뉴의 첫 화면 — 「일반」을 열 수 있으면 「일반」, 아니면 열 수 있는 첫 항목 */
    const settingsHome = computed(() => UtilAdminMenu.firstTo(settingsNodes.value));

    /** 사용자가 고른 모드를 기억한다 */
    function setMode(next: AdminMenuMode): void {
        last.value = next;
    }

    /** 주소가 속한 모드를 마지막 모드로 적는다 — 셸이 주소가 바뀔 때 부른다 */
    function follow(path: string): void {
        const found = UtilAdminMenu.modeOfPath(path);
        if (found) last.value = found;
    }

    /** 설정 메뉴로 들어간다 — 첫 화면(일반)으로 이동한다 */
    async function openSettings(): Promise<void> {
        const to = settingsHome.value;
        if (!to) return;
        setMode('settings');
        await navigateTo(to);
    }

    /** 일반 메뉴로 돌아간다 — 일반 메뉴의 첫 화면(대시보드)으로 이동한다 */
    async function backToMenu(): Promise<void> {
        setMode('normal');
        await navigateTo('/');
    }

    return { mode, isSettings, nodes, hasSettings, settingsHome, setMode, follow, openSettings, backToMenu };
}
