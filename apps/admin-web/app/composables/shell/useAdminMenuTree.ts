import type { AdminMenuConfigNodeDto } from '~/composables/menu/api/useMenuApi.types';
import UtilAdminMenu, { type AdminMenuMode, type AdminMenuNode } from '~/utils/util-admin-menu';

/**
 * 관리자 셸이 그리는 메뉴 — DB 의 관리자 메뉴 구성(메뉴 관리 › 관리자 메뉴, `GET /api/admin/admin-menu/shell`)을 `UtilAdminMenu.fromConfig` 로 바꿔 앱 전체가 하나를 들고 있는다(`useState`).
 *  · 셸(`layouts/default.vue`)이 뜰 때 한 번 `load` 한다. 받기 전에는 빈 메뉴(엉뚱한 메뉴가 잠깐 보였다 바뀌지 않게), **받지 못하면 메뉴 정의(`UtilAdminMenu.ITEMS`)**로 그린다.
 *  · 메뉴 편집 화면이 저장하면 `apply` 로 바로 바꿔 끼운다(새로고침 없이 사이드바가 따라온다).
 * 권한으로 거르는 일은 쓰는 쪽이 `UtilAdminMenu.visible(can, mode, nodes)` 로 한다.
 */
export function useAdminMenuTree() {
    const live = useState<AdminMenuNode[] | null>('admin-menu-live', () => null);
    const failed = useState<boolean>('admin-menu-live-failed', () => false);
    const api = useMenuApi();

    /** 지금 그릴 메뉴 */
    const nodes = computed<readonly AdminMenuNode[]>(() => live.value ?? (failed.value ? UtilAdminMenu.ITEMS : []));

    /** 서버 구성을 받아 바꿔 끼운다 — 실패하면 메뉴 정의로 그린다(다시 부르면 다시 받는다) */
    async function load(): Promise<void> {
        try {
            apply((await api.adminMenuShell()).modes);
            failed.value = false;
        } catch {
            failed.value = true;
        }
    }

    /** 받은 구성(두 모드)으로 바꿔 끼운다 */
    function apply(modes: Record<AdminMenuMode, AdminMenuConfigNodeDto[]>): void {
        live.value = UtilAdminMenu.fromConfig(modes);
    }

    return { nodes, load, apply };
}
