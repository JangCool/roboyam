import type { MenuSection } from '~/utils/util-menu';

/**
 * 사이드 그룹 고르기(운영자 설정 「사이드 그룹」 `PICK` > 테마 기본 — 로보얌 기본, 사용자 결정 2026-10-28. 켤지는 셸이 `useMenuStyle` 로 정한다) — 사이드 몫의 최상위 그룹(커뮤니티·소모임 …)을 상단 메뉴 첫 줄에 탭으로 올리고,
 * 탭을 누르면 그 그룹 하나만 오른쪽(또는 왼쪽) 사이드 메뉴에 보인다. 메뉴 막대(`DaumMenuBar`)와 사이드 칸(`AppSideMenu`)이 **같은 상태**를 본다(`useState`).
 *
 * - 고른 그룹 기본값 = 지금 있는 곳이 든 사이드 그룹(없으면 아무것도 고르지 않음 → 사이드 칸이 없다). 주소가 바뀌면 다시 기본값으로.
 * - 같은 탭을 다시 누르면 닫는다. 그룹에 「누르면 가는 곳」이 있으면 탭은 링크이고, 이동한 뒤 그 그룹을 연다(`open`).
 * 컴포저블은 setup 에서 부른다.
 *
 * @returns `groups` 사이드 몫의 최상위 그룹 구역 · `activeKey` 지금 있는 곳이 든 그룹 · `picked` 고른 그룹 키 · `current` 고른 그룹 구역(없으면 null) · `pick(key)` 고르기/닫기.
 */
export function useSideGroupPick() {
    const route = useRoute();
    const { sections } = useMenuItems('side');
    const picked = useState<string | null>('side-group-pick', () => null);

    /** 사이드 몫의 최상위 그룹(제목 있는 구역) */
    const groups = computed<MenuSection[]>(() => sections.value.filter((section) => section.group !== null));

    /** 지금 있는 곳이 든 그룹 — 접힌 그룹 안이면 `useMenuItems` 가 먼저 펼쳐 둔다 */
    const activeKey = computed<string | null>(
        () => groups.value.find((section) => section.blocks.some((block) => block.items.some((row) => row.active)))?.key ?? null,
    );

    watch([() => route.path, activeKey], () => (picked.value = activeKey.value), { immediate: true });

    /** 고른 그룹 구역 — 메뉴가 다시 와서 그 그룹이 없어졌으면 null */
    const current = computed<MenuSection | null>(() => groups.value.find((section) => section.key === picked.value) ?? null);

    function pick(key: string): void {
        picked.value = picked.value === key ? null : key;
    }

    /** 닫지 않고 연다 — 연결이 있는 그룹 탭을 눌러 이동한 뒤(`useAfterMenuNavigate`) */
    function open(key: string): void {
        picked.value = key;
    }

    return { groups, activeKey, picked, current, pick, open };
}
