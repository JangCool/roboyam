<template>
    <!--
        기본 테마의 데스크톱 셸 — 상단 바 + 메뉴 + 본문 + 푸터. 루트가 여러 개(헤더·본문 줄·푸터)라 레이아웃의 세로 flex 컬럼에 그대로 이어 붙는다.
        메뉴 위치(`menuPosition`, 레이아웃이 정한다):
          LEFT  사이드 메뉴가 본문 왼쪽(테마 기본값) · RIGHT 같은 사이드 메뉴가 본문 오른쪽 — 본문과의 구분선은 메뉴 쪽 가장자리에
          TOP   상단 바 아래 가로 메뉴(`AppTopMenu`, 상단 바 아래에 붙는다) — 사이드 메뉴 없음
          TOP_LEFT·TOP_RIGHT  가로 메뉴(운영자가 고른 상단 띠 그룹) + 사이드 메뉴(나머지 그룹) — 나누기는 `UtilMenu.split`
        최상위 항목(`topItems`): MENU(테마 기본값) = 메뉴 안 · LOGO = 주 색상 바의 로고 옆 흰 글자 링크 줄(`AppLogoLinks`, 데스크톱만).
        `sideMenu=false`(글쓰기)는 사이드 메뉴만 숨긴다(상단 메뉴는 그대로).
        상단 메뉴 폭(`menuWidth`)은 이 셸에서 모양이 같다 — `AppTopMenu` 의 바탕·아래 선이 이미 창 폭이고 칸은 본문 폭 상자 안에서 시작한다.
        메뉴 넘침 표시(`menuOverflow`)는 가로 메뉴 줄(`AppTopMenu`·`AppLogoLinks`)에 그대로 전한다(얇은 스크롤이면 `AppTopMenu` 가 예약 자리만큼 높아 사이드 메뉴의 붙는 높이도 `barOverflow` 로 맞춘다).
        props·slots 계약은 `ThemeShell.types.ts`. 메뉴 알맹이(`AppMenuList`·`AppTopMenu`·`AppLogoLinks`)는 공통 셸 부품이고 여기서는 어디에 놓느냐만 정한다.
    -->
    <AppHeader :class="inner ? 'max-md:hidden' : ''" @open-menu="emit('open-menu')">
        <template #links>
            <AppLogoLinks v-if="topItems === 'LOGO'" :overflow="menuOverflow" class="ml-3 max-md:hidden" />
        </template>
    </AppHeader>
    <AppTopMenu v-if="hasBar" :overflow="menuOverflow" />
    <slot name="after-header" />

    <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-1 items-start">
        <!-- 사이드 메뉴(데스크톱) — 문서 순서는 본문 앞, 놓이는 쪽은 `side`. 모바일은 안 보인다(aside 가 md 미만에서 숨는다) -->
        <AppSideMenu v-if="side" :side="side" :with-bar="hasBar" :bar-overflow="menuOverflow" class="pb-6" />
        <main id="main-content" class="min-w-0 flex-1 px-4 py-4 md:px-6 md:py-6" :class="mainClass">
            <slot />
        </main>
    </div>

    <AppFooter class="max-md:hidden" />
</template>

<script setup lang="ts">
import type { ThemeShellProps } from '~/components/themes/ThemeShell.types';
import UtilMenuPosition from '~/utils/util-menu-position';

interface Props extends ThemeShellProps {}
const props = withDefaults(defineProps<Props>(), { inner: false, sideMenu: true });
const emit = defineEmits<{ (e: 'open-menu'): void }>();

/** 상단 가로 메뉴가 있는 배치인가 */
const hasBar = computed(() => UtilMenuPosition.hasBar(props.menuPosition));
/** 사이드 메뉴의 쪽 — 사이드가 없는 배치이거나 페이지가 메뉴를 껐으면 null */
const side = computed(() => (props.sideMenu ? UtilMenuPosition.sideOf(props.menuPosition) : null));

/** 본문과 메뉴 사이의 구분선 — 메뉴가 있는 쪽 가장자리에 긋는다 */
const mainClass = computed(() => {
    if (!side.value) return '';
    return `md:min-h-[calc(100dvh-6rem)] md:border-surface ${side.value === 'RIGHT' ? 'md:border-r' : 'md:border-l'}`;
});
</script>
