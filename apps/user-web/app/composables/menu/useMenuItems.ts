import type { MenuGroupNode } from '~/composables/menu/api/useMenuApi.types';
import type { MenuCollapseMemory, MenuContext } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';

/**
 * 메뉴 구성 — 햄버거·사이드 메뉴(세로 `AppMenuList`)와 상단 메뉴(가로 `AppTopMenu`)·모바일 상단 탭(`AppTopTabs`)이 **같이 쓴다**(복붙하지 않는다).
 * 트리는 `useMenu` 의 공유 상태, 지금 보는 곳은 주소(`route.path`)와 `useCurrentBoard` 가 알려 준다.
 * 어떤 항목을 밝히는지·구역을 어떻게 나누는지는 `UtilMenu` 가 안다 — 여기는 그 순수 함수에 공유 상태를 이어 주고 **접힘 기억**만 든다.
 * 컴포저블은 setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */

/** 이 브라우저에 남긴 접힘 기억. 없거나 읽을 수 없으면 빈 기억 */
function readStored(): MenuCollapseMemory {
    if (typeof window === 'undefined') return {};
    try {
        return UtilMenu.parseCollapse(window.localStorage.getItem(UtilMenu.COLLAPSE_STORAGE_KEY));
    } catch {
        return {};
    }
}

/** 접힘 기억을 이 브라우저에 남긴다. 저장소가 막혀 있어도 이번 방문 동안은 메모리에서 쓴다 */
function writeStored(memory: MenuCollapseMemory): void {
    try {
        window.localStorage.setItem(UtilMenu.COLLAPSE_STORAGE_KEY, UtilMenu.serializeCollapse(memory));
    } catch {
        /* 사생활 보호 모드 등 — 이번 방문 동안은 메모리에서 쓴다 */
    }
}

export function useMenuItems() {
    const route = useRoute();
    const { slug: currentSlug } = useCurrentBoard();
    const { nodes, mobileTabIds, state, source, loading, reload } = useMenu();

    /** 접힘 기억(노드 ID → 접힘). 세로 메뉴가 두 군데(사이드·햄버거)에 떠 있어도 같은 상태를 본다 */
    const memory = useState<MenuCollapseMemory>('menu-collapse', () => readStored());

    /** 지금 있는 곳으로 밝힐 항목 하나 — 모든 항목 중 주소가 가장 길게 맞는 것 */
    const activeId = computed(() => UtilMenu.activeId(nodes.value, route.path, currentSlug.value));

    const context = computed<MenuContext>(() => ({
        activeId: activeId.value,
        memory: memory.value,
    }));

    /** 세로 메뉴(왼쪽·오른쪽·햄버거)의 구역 */
    const sections = computed(() => UtilMenu.sections(nodes.value, context.value));
    /** 상단 가로 막대의 구역 */
    const bar = computed(() => UtilMenu.barSections(nodes.value, context.value));
    /** 모바일 상단 탭의 항목 */
    const tabs = computed(() => UtilMenu.mobileTabs(nodes.value, mobileTabIds.value));

    /** 그룹 제목을 눌러 접거나 편다 — 기억에 남겨 다음 방문에도 그대로다 */
    function toggle(group: MenuGroupNode): void {
        const next: MenuCollapseMemory = { ...memory.value, [group.id]: !UtilMenu.isCollapsed(group, memory.value) };
        memory.value = next;
        writeStored(next);
    }

    // 지금 있는 곳이 접힌 그룹 안이면 그 조상 그룹을 펼친다(S-3 M-3) — 활성 항목이 바뀌거나 트리가 새로 올 때.
    // 사용자가 일부러 접은 직후에는 활성 항목이 그대로라 다시 펴지 않는다
    watch(
        [activeId, nodes],
        () => {
            const next = UtilMenu.expandForActive(nodes.value, activeId.value, memory.value);
            if (next) {
                memory.value = next;
                writeStored(next);
            }
        },
        { immediate: true },
    );

    return { sections, bar, tabs, activeId, state, source, loading, reload, toggle };
}
