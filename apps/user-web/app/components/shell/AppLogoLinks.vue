<template>
    <!--
        로고 옆 작은 링크 줄(데스크톱) — 최상위 항목(그룹 밖에 놓인 항목 — 홈·인기글 …)을 운영자가 「로고 옆」(`topItems = LOGO`)으로 정했을 때 셸이 로고 다음에 놓는다.
        데이터는 다른 메뉴와 같은 `useMenuItems('logo')`(`UtilMenu.split` 의 `logo` 몫), 링크는 `AppMenuLink`, 지금 있는 곳은 `aria-current="page"` + 굵은 글자.
        흐린 글자·진한 글자는 `text-muted-color`·`text-color` 라 흰 윗줄에서는 일반 색, 주 색상 바(`.app-nav`, 기본 테마) 위에서는 흰 글자다. 사이 구분선은 글자색을 옅게 쓴다.
        테마별 글자 크기 같은 차이는 2단계 CSS 가 `.logo-links` 로 잡는다(`main.css`). 로딩·오류 안내는 그리지 않는다(메뉴 쪽이 맡는다).
        윗줄 폭이 모자라면 옆으로 민다 — 넘칠 때의 표시(`overflow`)는 공통 `AppScrollRow` 가 그리고, 지금 있는 곳을 가운데로 끌어온다(`useCenterActive`).
        줄은 높이 40(`h-10`)으로 두고 링크(줄 높이 12)를 가운데에 놓아, 얇은 스크롤은 그 아래 여백 안 바닥 7px 에 그린다(윗줄 높이 48·56·60 안이라 윗줄은 그대로다).
        화살표 단추 그라데이션은 놓인 면의 바탕(`--scroll-fade-bg` — 주 색상 바 · 흰 윗줄 · 페이지 바탕)에서 사라진다.
    -->
    <nav v-if="rows.length" :aria-label="t('shell.quickLinks')" class="logo-links min-w-0" data-testid="logo-links">
        <AppScrollRow ref="row" :mode="overflow" scroller-class="flex h-10 items-center">
            <ul class="m-0 flex list-none items-center p-0">
                <li v-for="(item, index) in rows" :key="item.key" class="flex shrink-0 items-center">
                    <span v-if="index > 0" class="h-3 w-px bg-current opacity-25" aria-hidden="true" />
                    <AppMenuLink
                        :node="item.node"
                        class="flex items-center gap-1 rounded px-2.5 text-[13.5px] leading-3 focus-visible:outline-2 focus-visible:outline-primary"
                        :class="item.active ? 'font-semibold text-color' : 'text-muted-color hover:text-color'"
                        :data-active="item.active ? 'true' : undefined"
                        :aria-current="item.active ? 'page' : undefined"
                    >
                        {{ localizedName(item.node.label, item.node.nameEn) }}
                    </AppMenuLink>
                </li>
            </ul>
        </AppScrollRow>
    </nav>
</template>

<script setup lang="ts">
import type { MenuOverflow } from '~/utils/util-menu-position';

interface Props {
    /** 줄이 넘칠 때의 표시(운영자 설정 > 테마 기본, 셸이 넘긴다) — 얇은 스크롤 · 양끝 화살표 단추 */
    overflow?: MenuOverflow;
}
withDefaults(defineProps<Props>(), { overflow: 'SCROLLBAR' });

const { t } = useI18n();
const { bar } = useMenuItems('logo');
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();
/** 링크 줄(`AppScrollRow`)과 그 안의 스크롤 상자 — 지금 있는 곳 가운데로 끌어오기가 쓴다 */
const { row, scroller } = useScrollRowRef();

/** 링크 줄 — 로고 몫은 최상위 항목뿐이라 제목 없는 구역의 항목들이다 */
const rows = computed(() => bar.value.flatMap((section) => section.entries.flatMap((entry) => (entry.kind === 'item' ? [entry.row] : []))));

// 지금 있는 곳이 화면 밖이면 가운데로 끌어온다(다른 가로 메뉴와 같은 방식)
useCenterActive(scroller, [() => rows.value.find((item) => item.active)?.key ?? null, () => rows.value.length], { onMount: true });
</script>
