<template>
    <!--
        모바일 메뉴 두 줄(운영자 설정 「모바일 상단 메뉴」 `MENU` > 테마 기본 — 로보얌 기본, 사용자 결정 2026-10-28) — 운영자가 고른 「모바일 탭」 대신 데스크톱 메뉴와 같은 구조로 그린다.
          첫 줄: 최상위 항목(홈·인기글·최신글·공지 …)은 링크, 사이드 몫 그룹(커뮤니티·소모임 …)은 이름 단추 — 데스크톱 첫 줄과 같다. 구분선(`divider`)도 데스크톱처럼 긋는다.
          둘째 줄: 기본은 상단 띠 그룹(주제 …)을 「그룹 이름(작은 글자) + 칸」으로 — 데스크톱 둘째 줄과 같다(홈에서도 주제가 보인다).
            커뮤니티·소모임 단추를 누르거나 그 그룹 안에 있으면 둘째 줄이 그 그룹의 칸으로 바뀐다(데스크톱에서는 오른쪽 사이드에 뜨는 것 — 모바일은 사이드가 없어 둘째 줄이 대신한다). 같은 단추를 다시 누르면 주제로 돌아간다.
        고른 그룹 상태는 데스크톱 사이드와 같은 `useSideGroupPick` 이다. 상단 띠·사이드 나누기는 데스크톱과 같은 `useMenuItems('bar' | 'side')`(`UtilMenu.split`).
        두 줄 모두 옆으로 밀고 활성 칸을 가운데로 끌어온다. 겉모양은 테마 CSS(`app-top-tabs` 고리 클래스)가 정한다.
    -->
    <nav v-if="visible" :aria-label="t('menu.tabsLabel')" class="app-top-tabs border-b border-surface bg-surface-0 text-sm dark:bg-surface-900" data-testid="mobile-menu-rows">
        <div ref="firstRow" class="no-scrollbar flex overflow-x-auto whitespace-nowrap px-2">
            <template v-if="state === 'loading'">
                <span v-for="n in 5" :key="n" class="mx-2 my-3 h-3.5 w-12 animate-pulse rounded bg-surface-200 dark:bg-surface-800" aria-hidden="true" />
            </template>
            <template v-else>
                <template v-for="tab in tabs" :key="tab.key">
                    <span v-if="tab.divider === 'BEFORE'" class="mx-1 my-auto h-4 w-px shrink-0 bg-hairline-strong" aria-hidden="true" />
                    <AppMenuLink
                        v-if="tab.kind === 'item'"
                        :node="tab.row.node"
                        :data-active="tab.key === underlineKey ? 'true' : undefined"
                        :class="tabClass(tab.key === underlineKey)"
                        :aria-current="tab.row.active ? 'page' : undefined"
                    >
                        {{ localizedName(tab.row.node.label, tab.row.node.nameEn) }}
                    </AppMenuLink>
                    <button
                        v-else
                        type="button"
                        :data-active="tab.key === underlineKey ? 'true' : undefined"
                        :class="tabClass(tab.key === underlineKey)"
                        :aria-expanded="tab.key === sidePick.picked.value"
                        :aria-controls="rowId"
                        @click="sidePick.pick(tab.key)"
                    >
                        {{ localizedName(tab.title, tab.titleEn) }}
                    </button>
                    <span v-if="tab.divider === 'AFTER'" class="mx-1 my-auto h-4 w-px shrink-0 bg-hairline-strong" aria-hidden="true" />
                </template>
                <button v-if="state === 'error'" type="button" class="shrink-0 px-2 py-2.5 text-xs text-muted-color underline focus-visible:outline-2 focus-visible:outline-primary" @click="reload">
                    {{ t('menu.tabsRetry') }}
                </button>
            </template>
        </div>

        <!-- 메뉴 설명 줄 — 고른 그룹이 없고 지금 있는 곳이 최상위 항목(인기글·최신글·공지 …)이면 주제 대신 그 메뉴의 한 줄 설명(데스크톱과 같다, `useMenuDescription`) -->
        <p v-if="!sidePick.current.value && showDesc && menuDesc" class="app-top-tabs-sub m-0 flex items-center gap-2 overflow-hidden border-t border-hairline px-4 py-2 text-[0.8125rem]" data-testid="mobile-menu-desc">
            <span class="sr-only">{{ t('menu.descLabel') }}</span>
            <span class="shrink-0 font-semibold text-color">{{ menuDesc.title }}</span>
            <span class="truncate text-muted-color">{{ menuDesc.text }}</span>
        </p>

        <!-- 둘째 줄 — 고른 사이드 그룹이 있으면 그 그룹, 없으면 상단 띠 그룹(주제 …). 첫 줄과의 사이에 머리카락 선 -->
        <div v-else-if="secondGroups.length" :id="rowId" ref="secondRow" class="app-top-tabs-sub no-scrollbar flex items-center overflow-x-auto whitespace-nowrap border-t border-hairline px-2">
            <section
                v-for="(group, index) in secondGroups"
                :key="group.key"
                :aria-labelledby="`${rowId}-${group.key}`"
                class="flex shrink-0 items-center"
                :class="index > 0 ? 'ml-1 border-l border-hairline pl-1' : ''"
            >
                <h2 :id="`${rowId}-${group.key}`" class="m-0 shrink-0 pl-2 pr-1 text-xs font-normal text-muted-color">{{ localizedName(group.title, group.titleEn) }}</h2>
                <ul class="m-0 flex list-none items-center p-0">
                    <li v-for="row in group.items" :key="row.key">
                        <AppMenuLink
                            :node="row.node"
                            :data-active="row.active ? 'true' : undefined"
                            :class="subClass(row.active)"
                            :aria-current="row.active ? 'page' : undefined"
                        >
                            <i v-if="row.color" class="inline-block size-1.5 shrink-0 rounded-full" :style="UtilBoard.dotStyle(row.color)" aria-hidden="true" />
                            <span>{{ localizedName(row.node.label, row.node.nameEn) }}</span>
                        </AppMenuLink>
                    </li>
                </ul>
            </section>
        </div>
    </nav>
</template>

<script setup lang="ts">
import type { MenuDivider } from '~/composables/menu/api/useMenuApi.types';
import type { MenuBarSection, MenuItemRow, MenuSection } from '~/utils/util-menu';
import UtilBoard from '~/utils/util-board';

/** 첫 줄 칸 — 최상위 항목(링크) 또는 사이드 몫 그룹(고르는 단추) */
type FirstTab =
    | { kind: 'item'; key: string; row: MenuItemRow; divider: MenuDivider | null }
    | { kind: 'group'; key: string; title: string; titleEn: string | null; divider: MenuDivider | null };

/** 둘째 줄의 그룹 하나 — 이름과 평평하게 편 칸 */
interface SecondGroup {
    key: string;
    title: string;
    titleEn: string | null;
    items: MenuItemRow[];
}

const { t } = useI18n();
// 전체 트리 — 최상위 항목(홈·인기글 …)과 로딩·오류 상태
const { bar: allBar, state, activeId, reload } = useMenuItems();
// 데스크톱과 같은 나누기 — 상단 띠 그룹(주제 …)
const { bar: barSections } = useMenuItems('bar');
// 사이드 몫 그룹(커뮤니티·소모임 …)과 고른 그룹 — 데스크톱 사이드와 같은 상태
const sidePick = useSideGroupPick();
/** 지금 있는 최상위 항목(인기글·최신글·공지 …)의 설명 — 설명 줄이 켜져 있고(운영자 설정 「메뉴 설명 줄」 > 테마 기본, `useMenuStyle`) 고른 그룹이 없으면 둘째 줄이 주제 대신 이것 */
const { description: menuDesc } = useMenuDescription();
const { menuDescription: showDesc } = useMenuStyle();
const { nodes } = useMenu();
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();
const rowId = useId();
const firstRow = ref<HTMLElement | null>(null);
const secondRow = ref<HTMLElement | null>(null);

/** 그룹의 구분선 — 메뉴 트리에서 같은 ID 의 최상위 그룹을 찾는다 */
function groupDivider(key: string): MenuDivider | null {
    return nodes.value.find((node) => node.id === key)?.divider ?? null;
}

/** 첫 줄 — 최상위 항목(그룹 밖 항목)을 먼저, 그 뒤에 사이드 몫 그룹 이름. 상단 띠 그룹(주제)은 둘째 줄에만 둔다(데스크톱과 같게) */
const tabs = computed<FirstTab[]>(() => {
    const items = allBar.value
        .filter((section) => section.title === null)
        .flatMap<FirstTab>((section) =>
            section.entries.flatMap<FirstTab>((entry) => (entry.kind === 'item' ? [{ kind: 'item', key: entry.key, row: entry.row, divider: entry.row.node.divider ?? null }] : [])),
        );
    const groups = sidePick.groups.value.map<FirstTab>((section) => ({ kind: 'group', key: section.key, title: section.title ?? '', titleEn: section.titleEn, divider: groupDivider(section.key) }));
    return [...items, ...groups];
});

/**
 * 첫 줄에서 밑줄을 그을 칸 하나(같은 줄에서 밑줄은 하나만 — 데스크톱과 같다) — 고른 사이드 그룹 → 지금 있는 곳이 든 사이드 그룹 → 지금 있는 최상위 항목.
 * 예: 홈에서 「소모임」을 누르면 밑줄이 홈에서 소모임으로 옮겨 간다.
 */
const underlineKey = computed<string | null>(
    () => sidePick.picked.value ?? sidePick.activeKey.value ?? tabs.value.find((tab) => tab.kind === 'item' && tab.row.active)?.key ?? null,
);

/** 막대 구역(상단 띠 그룹) → 둘째 줄 그룹 — 직속 항목과 하위 그룹(드롭다운)의 항목을 차례대로 편다 */
function fromBar(section: MenuBarSection): SecondGroup {
    return {
        key: section.key,
        title: section.title ?? '',
        titleEn: section.titleEn,
        items: section.entries.flatMap((entry) => (entry.kind === 'item' ? [entry.row] : entry.blocks.flatMap((block) => block.items))),
    };
}

/** 세로 구역(사이드 그룹) → 둘째 줄 그룹 */
function fromSide(section: MenuSection): SecondGroup {
    return { key: section.key, title: section.title ?? '', titleEn: section.titleEn, items: section.blocks.flatMap((block) => block.items) };
}

/** 둘째 줄 — 고른 사이드 그룹(커뮤니티·소모임)이 있으면 그 그룹 하나, 없으면 상단 띠 그룹(주제 …) 모두 */
const secondGroups = computed<SecondGroup[]>(() => {
    const picked = sidePick.current.value;
    if (picked) return [fromSide(picked)];
    return barSections.value.filter((section) => section.title !== null).map(fromBar);
});

const visible = computed(() => state.value === 'loading' || state.value === 'error' || tabs.value.length > 0 || secondGroups.value.length > 0);

/** 첫 줄 칸 — 활성·고른 칸은 진한 글자 + 밑줄(테마 CSS 가 색을 정한다). 단추는 손가락 포인터. 눌리는 면 가로 최소 32px */
function tabClass(active: boolean, picked = false): string {
    return [
        'min-w-8 shrink-0 cursor-pointer border-b-2 px-2 py-2.5 text-center focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
        active || picked ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color',
    ].join(' ');
}

/** 둘째 줄 칸 — 작은 글자, 활성은 진하게 */
function subClass(active: boolean): string {
    return [
        'flex shrink-0 items-center gap-1 px-2 py-2 text-[0.8125rem] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
        active ? 'font-semibold text-color' : 'text-muted-color',
    ].join(' ');
}

// 활성 칸이 화면 밖이면 가운데로 끌어온다(줄만 민다 — `useCenterActive`)
useCenterActive(firstRow, [activeId, () => tabs.value.length, state]);
useCenterActive(secondRow, [activeId, () => sidePick.picked.value]);
</script>
