<template>
    <!--
        데스크톱 사이드 메뉴 칸 — 본문 옆 세로 메뉴(`AppMenuList` 의 `side` 몫 — 배치대로 나눈 그룹, `UtilMenu.split`). 항상 펼쳐져 있고 길면 메뉴 카드 안에서 스크롤한다. 모바일(md 미만)은 숨는다.
        모든 테마 셸이 사이드가 있는 배치(`LEFT`·`RIGHT`·`TOP_LEFT`·`TOP_RIGHT`)에서 같이 쓴다 — 쪽은 `side` 로 정하고(flex `order`, 문서 순서는 본문 앞 그대로), 겉모양은 셸이 준다 — 카드 면·안쪽 여백은 `cardClass`(메뉴·덧붙인 구역이 각자 카드), 바깥 여백은 `class`(칸 전체).
        창 위에 붙는 높이는 셸의 붙는 헤더 높이(`--shell-header-h`) + 사이 간격(`--side-menu-gap`), 붙는 상단 막대도 있으면(`withBar`) 그 높이(`--shell-bar-h`, 넘침 표시가 얇은 스크롤이면 예약 자리 `--shell-bar-reserve` 도)를 더한다(`main.css`, 테마가 채운다).
        나눈 몫이 비었으면(섞인 배치에서 그룹을 모두 상단에 올렸다) 칸을 그리지 않는다 — 화면이 덧붙인 구역(`useSideMenuExtra`)이 있으면 그것만 그린다(메뉴 카드 아래 따로 떨어진 카드). `side-menu`·`data-side` 는 테마의 2단계 CSS(활성 줄 막대 등)가 잡는 자리다.
    -->
    <aside
        v-if="menuShown || extra"
        class="side-menu sticky hidden w-44 shrink-0 flex-col self-start md:flex"
        :class="side === 'RIGHT' ? 'order-last' : 'order-first'"
        :data-side="side"
        :data-with-bar="withBar ? 'true' : undefined"
        :data-bar-overflow="withBar ? barOverflow : undefined"
        data-testid="side-menu"
        data-cache-area="menu.public"
    >
        <!-- 길면 메뉴 카드 안에서 스크롤한다(칸이 스크롤하면 카드 테두리·그림자가 잘린다) -->
        <div v-if="menuShown" class="min-h-0 overflow-y-auto" :class="cardClass">
            <AppMenuList scope="side" :only-group="pickedOnly ? (sidePick.current.value?.key ?? null) : undefined" />
        </div>
        <!-- 화면이 덧붙인 구역(`useSideMenuExtra` — 예: 소모임 상세의 글·일정·멤버·채팅) — 메뉴 카드 아래 따로 떨어진 카드(2026-11-06 사용자) -->
        <nav v-if="extra" :aria-label="extra.title" class="shrink-0 text-sm" :class="[cardClass, menuShown ? 'mt-4' : '']" data-testid="side-menu-extra">
            <h2 class="truncate px-4 pb-1 pt-4 text-xs font-normal text-muted-color">{{ extra.title }}</h2>
            <ul class="m-0 list-none p-0">
                <li v-for="item in extra.items" :key="item.key">
                    <NuxtLink :to="item.to" replace :class="UtilMenu.listItemClass(item.active)" :aria-current="item.active ? 'page' : undefined" :data-testid="`side-menu-extra-${item.key}`">
                        <span class="truncate">{{ item.label }}</span>
                    </NuxtLink>
                </li>
            </ul>
        </nav>
    </aside>
</template>

<script setup lang="ts">
import type { MenuOverflow } from '~/utils/util-menu-position';
import UtilMenu from '~/utils/util-menu';

interface Props {
    /** 본문의 어느 쪽에 놓나 */
    side?: 'LEFT' | 'RIGHT';
    /** 위에 창에 붙는 상단 메뉴 막대도 있나(섞인 배치) — 붙는 높이에 막대 높이를 더한다 */
    withBar?: boolean;
    /** 그 막대의 넘침 표시 — `SCROLLBAR` 면 막대가 줄 아래 예약 자리만큼 높아(`--shell-bar-reserve`, 테마가 채운다) 붙는 높이에 그만큼 더한다 */
    barOverflow?: MenuOverflow;
    /** 상단 탭에서 고른 사이드 그룹 하나만 그리는가(운영자 설정 「사이드 그룹」 `PICK` > 테마 기본 — 셸이 `useMenuStyle` 로 정해 넘긴다, 상태는 `useSideGroupPick`) — 고른 그룹이 없으면 칸이 없다 */
    pickedOnly?: boolean;
    /** 카드 모양(셸이 준다 — 예: `shell-card py-2`) — 메뉴와 화면이 덧붙인 구역이 각자 이 모양의 카드가 된다. 바깥 여백은 `class` 로(칸 전체) */
    cardClass?: string;
}

const props = withDefaults(defineProps<Props>(), { side: 'LEFT', withBar: false, barOverflow: 'SCROLLBAR', pickedOnly: false, cardClass: '' });

const { sections, state, ownsState } = useMenuItems('side');
const sidePick = useSideGroupPick();
/** 화면이 덧붙인 구역 — 이 칸이 있다고 화면에 알린다(없으면 화면이 같은 링크를 자기 안에 그린다). setup 에서 알려 서버 렌더에도 맞는다 */
const { extra, hostShown } = useSideMenuExtra();
hostShown.value = true;
onUnmounted(() => (hostShown.value = false));
/** 메뉴 카드 — 그릴 그룹이 있거나(고른 그룹만이면 고른 것이 있을 때), 이 칸이 맡은 로딩·오류 안내가 있을 때만. 칸은 이것이나 덧붙인 구역이 있을 때 */
const menuShown = computed(() => (props.pickedOnly ? sidePick.current.value !== null : sections.value.length > 0 || (ownsState.value && state.value !== 'ready')));
</script>
