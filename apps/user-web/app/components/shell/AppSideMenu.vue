<template>
    <!--
        데스크톱 사이드 메뉴 칸 — 본문 옆 세로 메뉴(`AppMenuList` 의 `side` 몫 — 배치대로 나눈 그룹, `UtilMenu.split`). 항상 펼쳐져 있고 길면 안에서 스크롤한다. 모바일(md 미만)은 숨는다.
        모든 테마 셸이 사이드가 있는 배치(`LEFT`·`RIGHT`·`TOP_LEFT`·`TOP_RIGHT`)에서 같이 쓴다 — 쪽은 `side` 로 정하고(flex `order`, 문서 순서는 본문 앞 그대로), 겉모양(카드 면·여백)은 셸이 `class` 로 준다.
        창 위에 붙는 높이는 셸의 붙는 헤더 높이(`--shell-header-h`) + 사이 간격(`--side-menu-gap`), 붙는 상단 막대도 있으면(`withBar`) 그 높이(`--shell-bar-h`, 넘침 표시가 얇은 스크롤이면 예약 자리 `--shell-bar-reserve` 도)를 더한다(`main.css`, 테마가 채운다).
        나눈 몫이 비었으면(섞인 배치에서 그룹을 모두 상단에 올렸다) 칸을 그리지 않는다. `side-menu`·`data-side` 는 테마의 2단계 CSS(활성 줄 막대 등)가 잡는 자리다.
    -->
    <aside
        v-if="visible"
        class="side-menu sticky hidden w-44 shrink-0 self-start overflow-y-auto md:block"
        :class="side === 'RIGHT' ? 'order-last' : 'order-first'"
        :data-side="side"
        :data-with-bar="withBar ? 'true' : undefined"
        :data-bar-overflow="withBar ? barOverflow : undefined"
        data-testid="side-menu"
    >
        <AppMenuList scope="side" :only-group="pickedOnly ? (sidePick.current.value?.key ?? null) : undefined" />
    </aside>
</template>

<script setup lang="ts">
import type { MenuOverflow } from '~/utils/util-menu-position';

interface Props {
    /** 본문의 어느 쪽에 놓나 */
    side?: 'LEFT' | 'RIGHT';
    /** 위에 창에 붙는 상단 메뉴 막대도 있나(섞인 배치) — 붙는 높이에 막대 높이를 더한다 */
    withBar?: boolean;
    /** 그 막대의 넘침 표시 — `SCROLLBAR` 면 막대가 줄 아래 예약 자리만큼 높아(`--shell-bar-reserve`, 테마가 채운다) 붙는 높이에 그만큼 더한다 */
    barOverflow?: MenuOverflow;
    /** 상단 탭에서 고른 사이드 그룹 하나만 그리는가(운영자 설정 「사이드 그룹」 `PICK` > 테마 기본 — 셸이 `useMenuStyle` 로 정해 넘긴다, 상태는 `useSideGroupPick`) — 고른 그룹이 없으면 칸이 없다 */
    pickedOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), { side: 'LEFT', withBar: false, barOverflow: 'SCROLLBAR', pickedOnly: false });

const { sections, state, ownsState } = useMenuItems('side');
const sidePick = useSideGroupPick();
/** 그릴 그룹이 있거나(고른 그룹만이면 고른 것이 있을 때), 이 칸이 맡은 로딩·오류 안내가 있을 때만 */
const visible = computed(() =>
    props.pickedOnly ? sidePick.current.value !== null : sections.value.length > 0 || (ownsState.value && state.value !== 'ready'),
);
</script>
