<template>
    <!--
        다음식 메뉴 카드(데스크톱 전용) — 흰 카드 안에 두 줄.
          GNB(높이 70): 최상위 항목(홈·공동구매 …)은 링크 탭, 최상위 그룹은 이름 탭(단추). 그룹 탭을 누르면 그 그룹을 "고른 그룹"으로 만들어 아래 LNB 에 직속 칸을 보인다(이동하지 않는다).
            운영자가 그 폴더에 「누르면 가는 곳」을 골랐으면(`group.path`) 그룹 탭은 링크다 — 그곳으로 가고, 간 뒤에도 그 그룹을 연다(`useAfterMenuNavigate`). 사이드 그룹 탭도 같다.
          LNB(높이 55): 고른 그룹의 직속 항목 + 하위 그룹 드롭다운(`AppTopMenuDropdown`). 고른 그룹이 없으면 줄 자체가 없다.
          `inlineGroups`(운영자 설정 「상단 메뉴 그룹 표시」 > 테마 기본, 로보얌 기본): GNB 는 최상위 항목만, LNB 에 막대 몫의 그룹을 모두 「그룹 이름(작은 글자) + 칸」으로 이어 놓는다(고르는 단추 없음, 사용자 결정 2026-10-28).
          모양 값(`inlineGroups`·`sideTabs`·`compact`·`description`)은 셸이 `useMenuStyle` 로 해석해 넘긴다 — 이 부품은 테마 선언을 읽지 않는다.
        활성 탭(검정 + 글자 폭 2px 밑줄)은 지금 있는 곳 기준 — 최상위 항목이면 그 탭, 그룹 안이면 그 그룹 탭. 고른 그룹 기본값은 활성 그룹이고 주소가 바뀌면 다시 활성 그룹으로 돌아간다.
        메뉴 구성·활성 판정·로딩/오류는 다른 메뉴와 같은 `useMenuItems('bar')` 다(섞인 배치면 상단 띠 그룹만, 최상위 항목이 「로고 옆」이면 GNB 에 없다 — `UtilMenu.split`). 로딩·오류 안내는 사이드가 없을 때만 여기서 그린다. 넘치면 옆으로 밀고 활성 칸을 가운데로 끌어온다. 모바일은 테마 공통 셸이 맡는다(md 미만에서 숨는다).
        넘칠 때의 표시(`overflow`)는 줄마다 공통 `AppScrollRow` 가 그린다. 칸(높이 40)이 줄 가운데에 놓여 줄 안 아래 여백(GNB 15px — 활성 밑줄 아래로도 13px, LNB 7.5px)이 남으므로
        얇은 스크롤은 그 여백 안 바닥 7px(`--scroll-reserve`)에 그려 줄 높이(70·55)가 그대로다. 화살표 단추는 카드 바탕(`--card-bg`)에서 사라진다.
        `full`(상단 메뉴 폭 「화면 전체」): 카드가 창 폭 흰 띠가 되고(반경 0 — `main.css` 의 `.shell-card[data-menu-width='FULL']`) GNB/LNB 사이 선도 창 폭이다. 칸은 줄마다 본문 폭 상자 안에 두어 본문 폭일 때와 같은 자리에서 시작한다.
    -->
    <nav :aria-label="t('menu.navLabel')" class="shell-card max-md:hidden" :data-menu-width="full ? 'FULL' : undefined" data-testid="daum-menu">
        <div v-if="showGnb" :class="rowClass">
            <AppScrollRow ref="gnbRow" :mode="overflow" :scroller-class="`flex ${compact ? 'h-[56px]' : 'h-[70px]'} items-center whitespace-nowrap px-1.5`">
                <AppMenuBarState v-if="ownsState" :state="state" :loading="loading" class="px-3" @reload="reload" />

                <ul v-if="showEntries" class="m-0 flex list-none items-center p-0">
                    <!-- 사이드 그룹 탭(`sideTabs`, 로보얌) — 최상위 항목과 메뉴 관리 차례대로 섞어 놓고(`orderStyle`), 누르면 그 그룹만 사이드 칸에 보인다(다시 누르면 닫는다) -->
                    <li
                        v-for="group in sideTabs ? sidePick.groups.value : []"
                        :key="`side-${group.key}`"
                        :class="UtilMenu.dividerClass(group.group?.divider)"
                        :style="orderStyle(group.key)"
                    >
                        <!-- 그룹에 「누르면 가는 곳」이 있으면 탭은 링크 — 그곳으로 가고, 간 뒤에도 이 그룹을 사이드 칸에 연다 -->
                        <AppMenuLink
                            v-if="group.group?.path"
                            :node="{ path: group.group.path, newTab: group.group.newTab === true, extra: group.group.extra ?? null }"
                            :class="gnbClass(group.key === underlineKey)"
                            :data-active="group.key === underlineKey ? 'true' : undefined"
                            :data-testid="`daum-menu-side-${group.key}`"
                            @click="afterNavigate(group.group, () => sidePick.open(group.key))"
                        >
                            <span :class="gnbLabelClass(group.key === underlineKey)">{{ localizedName(group.title ?? '', group.titleEn) }}</span>
                        </AppMenuLink>
                        <button
                            v-else
                            type="button"
                            class="cursor-pointer"
                            :class="gnbClass(group.key === underlineKey)"
                            :data-active="group.key === underlineKey ? 'true' : undefined"
                            :aria-expanded="group.key === sidePick.picked.value"
                            :data-testid="`daum-menu-side-${group.key}`"
                            @click="sidePick.pick(group.key)"
                        >
                            <span :class="gnbLabelClass(group.key === underlineKey)">{{ localizedName(group.title ?? '', group.titleEn) }}</span>
                        </button>
                    </li>
                    <!-- 칸 옆 구분선(운영자가 메뉴 구성에서 정한다 — 예: 공동구매 왼쪽) -->
                    <li v-for="tab in tabs" :key="tab.key" :class="UtilMenu.dividerClass(tab.kind === 'item' ? tab.row.node.divider : groupDivider(tab.key))" :style="orderStyle(tab.key)">
                        <AppMenuLink
                            v-if="tab.kind === 'item'"
                            :node="tab.row.node"
                            :class="gnbClass(tab.key === underlineKey)"
                            :data-active="tab.key === underlineKey ? 'true' : undefined"
                            :aria-current="tab.row.active ? 'page' : undefined"
                        >
                            <i v-if="tab.row.color" class="inline-block size-2 shrink-0 rounded-full" :style="UtilBoard.dotStyle(tab.row.color)" aria-hidden="true" data-testid="menu-dot-color" />
                            <span :class="gnbLabelClass(tab.key === underlineKey)">{{ localizedName(tab.row.node.label, tab.row.node.nameEn) }}</span>
                        </AppMenuLink>
                        <!-- 그룹 이름 탭 — 「누르면 가는 곳」이 있으면 링크(간 뒤에도 이 그룹을 LNB 에 연다), 없으면 고르기 단추 -->
                        <AppMenuLink
                            v-else-if="tab.section.group?.path"
                            :node="{ path: tab.section.group.path, newTab: tab.section.group.newTab === true, extra: tab.section.group.extra ?? null }"
                            :class="gnbClass(tab.key === activeTabKey || tab.key === selectedKey)"
                            :data-active="tab.key === activeTabKey ? 'true' : undefined"
                            :data-testid="`daum-menu-group-${tab.key}`"
                            @click="afterNavigate(tab.section.group, () => (selectedKey = tab.key))"
                        >
                            <span :class="gnbLabelClass(tab.key === activeTabKey)">{{ localizedName(tab.section.title, tab.section.titleEn) }}</span>
                        </AppMenuLink>
                        <button
                            v-else
                            type="button"
                            class="cursor-pointer"
                            :class="gnbClass(tab.key === activeTabKey || tab.key === selectedKey)"
                            :data-active="tab.key === activeTabKey ? 'true' : undefined"
                            :aria-expanded="tab.key === selectedKey"
                            :aria-controls="tab.key === selectedKey ? lnbId : undefined"
                            :data-testid="`daum-menu-group-${tab.key}`"
                            @click="selectedKey = tab.key"
                        >
                            <span :class="gnbLabelClass(tab.key === activeTabKey)">{{ localizedName(tab.section.title, tab.section.titleEn) }}</span>
                        </button>
                    </li>
                </ul>
            </AppScrollRow>
        </div>

        <!--
            메뉴 설명 줄(`description` — 운영자 설정 「메뉴 설명 줄」 > 테마 기본, 로보얌 기본 켜짐) — 지금 있는 곳이 최상위 항목(인기글·최신글·공지 …)이면 둘째 줄이 그 메뉴의 한 줄 설명(`useMenuDescription`). 높이는 둘째 줄과 같다.
            그룹 이어 놓기(`inlineGroups`)면 주제 줄 대신, 그룹 탭 방식이면 그룹 탭을 눌러 고르기 전까지(고르면 그 그룹 줄).
        -->
        <div v-if="showDesc && shownDesc" :class="showGnb ? 'border-t border-hairline' : ''" data-testid="daum-menu-desc">
            <div :class="rowClass">
                <p class="m-0 flex items-center gap-2 overflow-hidden px-[18px]" :class="compact ? 'h-[46px] text-sm' : 'h-[55px] text-base'">
                    <span class="sr-only">{{ t('menu.descLabel') }}</span>
                    <span class="shrink-0 font-bold text-subtab-active">{{ shownDesc.title }}</span>
                    <span class="truncate text-subtab">{{ shownDesc.text }}</span>
                </p>
            </div>
        </div>

        <!-- 그룹을 둘째 줄에 이어 놓는 방식(`inlineGroups`, 로보얌) — 그룹마다 「이름(작은 글자) + 칸」, 그룹 사이 세로 선. 첫 줄이 있으면 사이에 머리카락 선 -->
        <div v-else-if="inlineGroups && showEntries && groupSections.length" :class="showGnb ? 'border-t border-hairline' : ''">
            <div :class="rowClass">
                <AppScrollRow ref="lnbRow" :mode="overflow" :scroller-class="`flex ${compact ? 'h-[46px]' : 'h-[55px]'} items-center whitespace-nowrap px-1.5`" data-testid="daum-menu-lnb">
                    <section
                        v-for="(section, index) in groupSections"
                        :key="section.key"
                        :aria-labelledby="`${lnbId}-${section.key}`"
                        class="flex shrink-0 items-center"
                        :class="index > 0 ? 'ml-2 border-l border-hairline pl-2' : ''"
                    >
                        <h2 :id="`${lnbId}-${section.key}`" class="m-0 pl-4 pr-0.5 text-xs font-normal text-muted-color">
                            <AppMenuGroupLabel v-if="section.group" :group="section.group" />
                            <template v-else>{{ localizedName(section.title, section.titleEn) }}</template>
                        </h2>
                        <ul class="m-0 flex list-none items-center p-0">
                            <li v-for="entry in section.entries" :key="entry.key">
                                <AppMenuLink
                                    v-if="entry.kind === 'item'"
                                    :node="entry.row.node"
                                    :class="lnbClass(entry.row.active)"
                                    :data-active="entry.row.active ? 'true' : undefined"
                                    :aria-current="entry.row.active ? 'page' : undefined"
                                >
                                    <i v-if="entry.row.color" class="inline-block size-2 shrink-0 rounded-full" :style="UtilBoard.dotStyle(entry.row.color)" aria-hidden="true" data-testid="menu-dot-color" />
                                    <span>{{ localizedName(entry.row.node.label, entry.row.node.nameEn) }}</span>
                                    <small v-if="entry.row.notice" class="shrink-0 text-xs font-normal text-muted-color">{{ t('menu.investmentNotice') }}</small>
                                </AppMenuLink>
                                <AppTopMenuDropdown v-else :entry="entry" :button-class="lnbClass(entry.active)" />
                            </li>
                        </ul>
                    </section>
                </AppScrollRow>
            </div>
        </div>

        <!-- LNB — 고른 그룹의 직속 칸. GNB 와의 사이에 머리카락 선 -->
        <div v-else-if="!inlineGroups && showEntries && selected" class="border-t border-hairline">
            <div :class="rowClass">
                <AppScrollRow ref="lnbRow" :mode="overflow" :scroller-class="`flex ${compact ? 'h-[46px]' : 'h-[55px]'} items-center whitespace-nowrap px-1.5`">
                    <ul :id="lnbId" class="m-0 flex list-none items-center p-0" :aria-label="t('shell.subMenuLabel', { title: localizedName(selected.title, selected.titleEn) })" data-testid="daum-menu-lnb">
                        <li v-for="entry in selected.entries" :key="entry.key">
                            <AppMenuLink
                                v-if="entry.kind === 'item'"
                                :node="entry.row.node"
                                :class="lnbClass(entry.row.active)"
                                :data-active="entry.row.active ? 'true' : undefined"
                                :aria-current="entry.row.active ? 'page' : undefined"
                            >
                                <i v-if="entry.row.color" class="inline-block size-2 shrink-0 rounded-full" :style="UtilBoard.dotStyle(entry.row.color)" aria-hidden="true" data-testid="menu-dot-color" />
                                <span>{{ localizedName(entry.row.node.label, entry.row.node.nameEn) }}</span>
                                <small v-if="entry.row.notice" class="shrink-0 text-xs font-normal text-muted-color">{{ t('menu.investmentNotice') }}</small>
                            </AppMenuLink>
                            <!-- 활성 표시(`data-active`)는 드롭다운 단추가 스스로 단다 -->
                            <AppTopMenuDropdown v-else :entry="entry" :button-class="lnbClass(entry.active)" />
                        </li>
                    </ul>
                </AppScrollRow>
            </div>
        </div>
    </nav>
</template>

<script setup lang="ts">
import type { MenuBarSection, MenuItemRow } from '~/utils/util-menu';
import type { MenuOverflow } from '~/utils/util-menu-position';
import UtilBoard from '~/utils/util-board';
import UtilMenu from '~/utils/util-menu';

/** GNB 탭 하나 — 최상위 항목(링크) 또는 최상위 그룹(고르는 단추) */
type GnbTab = { kind: 'item'; key: string; row: MenuItemRow } | { kind: 'group'; key: string; section: MenuBarSection & { title: string } };

interface Props {
    /** 창 좌우 끝까지 닿는 흰 띠로 그리는가(상단 메뉴 폭 「화면 전체」) — 아니면 본문 폭 카드 */
    full?: boolean;
    /** 최상위 그룹을 둘째 줄에 「이름 + 칸」으로 이어 놓는가(운영자 설정 「상단 메뉴 그룹 표시」 `INLINE` > 테마 기본, 셸이 넘긴다) — 아니면 첫 줄 그룹 이름 탭 + 고른 그룹의 둘째 줄 */
    inlineGroups?: boolean;
    /** 줄이 넘칠 때의 표시(운영자 설정 > 테마 기본, 셸이 넘긴다) — 얇은 스크롤 · 양끝 화살표 단추 */
    overflow?: MenuOverflow;
    /** 사이드 몫 그룹을 첫 줄 탭으로 올리고 누른 그룹만 사이드 칸에 보이는가(운영자 설정 「사이드 그룹」 `PICK` > 테마 기본, 섞인 배치일 때만 셸이 켠다) */
    sideTabs?: boolean;
    /**
     * 한 단계 작게(운영자 설정 「상단 메뉴 크기」 `COMPACT` > 테마 기본): GNB 줄 56·탭 36·15px·좌우 14 / LNB 줄 46·칸 32·14px·좌우 12.
     * 칸이 줄 가운데라 아래 여백(GNB 10 — 활성 밑줄 아래로도 8, LNB 7)이 남아 얇은 스크롤 예약 자리(7px)와 겹치지 않는다.
     */
    compact?: boolean;
    /** 최상위 항목(인기글·최신글·공지 …)에 있을 때 둘째 줄에 메뉴 설명을 보이는가(운영자 설정 「메뉴 설명 줄」 > 테마 기본) */
    description?: boolean;
}
const props = withDefaults(defineProps<Props>(), { full: false, inlineGroups: false, overflow: 'SCROLLBAR', sideTabs: false, compact: false, description: false });

/** 사이드 그룹 고르기 — 사이드 칸(`AppSideMenu`)과 같은 상태 */
const sidePick = useSideGroupPick();
/** 연결이 있는 그룹 탭 — 이동한 뒤 그 그룹을 연다 */
const { afterNavigate } = useAfterMenuNavigate();
/** 지금 있는 최상위 항목(인기글·최신글·공지 …)의 설명 — 설명 줄이 켜져 있으면(`description`) 둘째 줄이 이것 */
const { description: menuDesc } = useMenuDescription();
/** 설명 줄 자리에 놓을 설명 — 꺼져 있거나 최상위 항목이 아니면 null */
const shownDesc = computed(() => (props.description ? menuDesc.value : null));

const { t } = useI18n();
const route = useRoute();

/** 줄(GNB·LNB) 안쪽 상자 — 화면 전체 폭이면 본문 폭 상자(셸 머리와 같은 `max-w` + 좌우 1rem)로 칸 시작을 본문 열에 맞춘다 */
const rowClass = computed(() => (props.full ? 'mx-auto w-full max-w-[var(--content-max)] px-4' : ''));
const { bar, state, ownsState, activeId, loading, reload } = useMenuItems('bar');
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();
const lnbId = useId();
/** GNB·LNB 줄(`AppScrollRow`)과 그 안의 스크롤 상자 — 활성 칸 가운데로 끌어오기가 쓴다 */
const { row: gnbRow, scroller: gnbScroller } = useScrollRowRef();
const { row: lnbRow, scroller: lnbScroller } = useScrollRowRef();

/** 칸을 그릴 수 있는가 — 받는 중·빈 메뉴는 상태 자리만 */
const showEntries = computed(() => state.value !== 'loading' && state.value !== 'empty');

/** 구역 안에 지금 있는 곳이 있나 — 항목이면 그 줄, 하위 그룹이면 드롭다운 안 */
function sectionActive(section: MenuBarSection): boolean {
    return section.entries.some((entry) => (entry.kind === 'item' ? entry.row.active : entry.active));
}

/** 제목 있는 구역(최상위 그룹)인가 */
function isGroup(section: MenuBarSection): section is MenuBarSection & { title: string } {
    return section.title !== null;
}

/**
 * GNB 탭 — 제목 없는 구역(최상위 항목)은 항목마다 탭 하나, 최상위 그룹은 그룹 이름 탭 하나. 제목 없는 구역에는 항목만 있다(`UtilMenu.barSections`).
 * 그룹을 둘째 줄에 이어 놓는 방식(`inlineGroups`)이면 그룹 탭은 없고 최상위 항목만 남는다.
 */
const tabs = computed<GnbTab[]>(() =>
    bar.value.flatMap<GnbTab>((section) =>
        isGroup(section)
            ? props.inlineGroups
                ? []
                : [{ kind: 'group', key: section.key, section }]
            : section.entries.flatMap<GnbTab>((entry) => (entry.kind === 'item' ? [{ kind: 'item', key: entry.key, row: entry.row }] : [])),
    ),
);

/** 그룹 이름 탭(다음식)의 구분선 — 막대 구역에는 그룹 노드가 없어 메뉴 트리에서 같은 ID 의 최상위 그룹을 찾는다 */
const { nodes: menuNodes } = useMenu();
function groupDivider(key: string) {
    return menuNodes.value.find((node) => node.id === key)?.divider ?? null;
}

/** 첫 줄 칸의 차례 — 최상위 항목·그룹 탭·사이드 그룹 탭을 메뉴 트리 차례 그대로 섞는다(사용자 결정 2026-10-29: 차례의 주인은 메뉴 관리) */
const topOrder = computed(() => new Map(menuNodes.value.map((node, index) => [node.id, index])));
function orderStyle(key: string): { order: number } {
    return { order: topOrder.value.get(key) ?? 0 };
}

/** 둘째 줄에 이어 놓을 그룹들(`inlineGroups`) — 막대 몫의 최상위 그룹 전부 */
const groupSections = computed(() => bar.value.filter(isGroup));

/** 첫 줄(GNB)을 그리는가 — 그룹을 이어 놓는 방식에서 최상위 항목이 하나도 없으면(「로고 옆」) 첫 줄 없이 둘째 줄만. 상태 안내(받는 중·오류)는 첫 줄이 맡는다 */
const showGnb = computed(
    () => !props.inlineGroups || tabs.value.length > 0 || (props.sideTabs && sidePick.groups.value.length > 0) || (ownsState.value && !showEntries.value),
);

/** 지금 있는 곳이 든 최상위 그룹 — 없으면(최상위 항목·메뉴 밖 화면) null */
const activeGroupKey = computed(() => bar.value.find((section) => isGroup(section) && sectionActive(section))?.key ?? null);

/** 밑줄을 그을 GNB 탭 — 활성 항목이 최상위 항목이면 그 탭, 그룹 안이면 그 그룹 탭 */
/**
 * 첫 줄에서 밑줄을 그을 칸 하나(사용자 결정 2026-10-28 — 같은 줄에서 밑줄은 하나만) — 사이드 그룹 탭이 있으면 고른 그룹 → 지금 있는 곳이 든 사이드 그룹, 그다음 활성 탭.
 * 예: 홈에서 「소모임」을 누르면 밑줄이 홈에서 소모임으로 옮겨 간다.
 */
const underlineKey = computed<string | null>(() => (props.sideTabs ? (sidePick.picked.value ?? sidePick.activeKey.value) : null) ?? activeTabKey.value);

const activeTabKey = computed<string | null>(() => {
    for (const section of bar.value) {
        if (!sectionActive(section)) continue;
        if (isGroup(section)) return section.key;
        return section.entries.find((entry) => entry.kind === 'item' && entry.row.active)?.key ?? null;
    }
    return null;
});

/** 막대에 그룹이 하나뿐이면 그 그룹 — 섞인 배치에서 띠 그룹이 하나이고 지금 있는 곳이 사이드 쪽이어도 LNB 가 비지 않게 기본으로 연다 */
const soleGroupKey = computed(() => {
    const groups = bar.value.filter(isGroup);
    return groups.length === 1 ? groups[0]!.key : null;
});

/**
 * 고른 그룹(LNB 에 보일 그룹) — 기본값은 활성 그룹(없으면 하나뿐인 그룹), 주소가 바뀌면 다시 기본값으로.
 * 메뉴 설명 줄이 보일 자리(최상위 항목에 있고 설명 줄이 켜짐)면 하나뿐인 그룹을 기본으로 열지 않는다 — 설명 줄을 보이고, 그룹 탭을 누르면 그 그룹 줄.
 */
const selectedKey = ref<string | null>(null);
watch(
    [() => route.path, activeGroupKey, soleGroupKey, () => shownDesc.value !== null],
    () => (selectedKey.value = activeGroupKey.value ?? (shownDesc.value ? null : soleGroupKey.value)),
    { immediate: true },
);

/** 고른 그룹의 구역 — 메뉴가 다시 와서 그 그룹이 없어졌으면 null(LNB 를 숨긴다) */
const selected = computed(() => bar.value.find((section): section is MenuBarSection & { title: string } => isGroup(section) && section.key === selectedKey.value) ?? null);

/** 둘째 줄이 메뉴 설명인가 — 그룹 이어 놓기면 주제 줄 대신, 그룹 탭 방식이면 고른 그룹이 없을 때 */
const showDesc = computed(() => showEntries.value && shownDesc.value !== null && (props.inlineGroups || !selected.value));

/** GNB 탭 — 17px 굵게 · 좌우 18px · 줄 높이 40. 활성·고른 탭은 진한 글자 */
function gnbClass(strong: boolean): string {
    return [
        props.compact
            ? 'flex h-9 shrink-0 items-center gap-1.5 px-3.5 text-[0.9375rem] font-bold leading-9 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary'
            : 'flex h-10 shrink-0 items-center gap-1.5 px-[18px] text-[17px] font-bold leading-10 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
        strong ? 'text-tab-active' : 'text-tab hover:text-tab-active',
    ].join(' ');
}

/** GNB 탭 글자 — 활성이면 글자 폭만큼의 2px 밑줄(글자 아래) */
function gnbLabelClass(active: boolean): string {
    return active ? 'relative after:absolute after:inset-x-0 after:-bottom-0.5 after:h-0.5 after:bg-tab-active' : '';
}

/** LNB 칸 — 16px 굵게 · 좌우 16px. 활성은 본문 글자색 */
function lnbClass(active: boolean): string {
    return [
        props.compact
            ? 'flex h-8 shrink-0 items-center gap-1.5 px-3 text-sm font-bold focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary'
            : 'flex h-10 shrink-0 items-center gap-1.5 px-4 text-base font-bold focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
        active ? 'text-subtab-active' : 'text-subtab hover:text-subtab-active',
    ].join(' ');
}

// 활성 칸이 화면 밖이면 가운데로 끌어온다(`AppTopMenu` 와 같은 방식) — GNB 는 활성 탭, LNB 는 활성 항목·드롭다운
useCenterActive(gnbScroller, [activeTabKey, state], { onMount: true });
useCenterActive(lnbScroller, [activeId, selectedKey], { onMount: true });
</script>
