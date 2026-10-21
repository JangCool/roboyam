<template>
    <!--
        기본 테마의 데스크톱 셸 — 상단 바 + 왼쪽 메뉴 + 본문 + 푸터. 루트가 여러 개(헤더·본문 줄·푸터)라 레이아웃의 세로 flex 컬럼에 그대로 이어 붙는다.
        props·slots 계약은 `ThemeShell.types.ts`. 메뉴 알맹이(`AppMenuList`)는 공통 셸 부품이고 여기서는 어디에 놓느냐만 정한다.
    -->
    <AppHeader :class="inner ? 'max-md:hidden' : ''" @open-menu="emit('open-menu')" />
    <slot name="after-header" />

    <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-1 items-start">
        <!-- 사이드 메뉴(데스크톱) — 본문 앞. 모바일은 안 보인다(aside 가 md 미만에서 숨는다) -->
        <AppSideMenu v-if="sideMenu" />
        <main id="main-content" class="min-w-0 flex-1 px-4 py-4 md:px-6 md:py-6" :class="mainClass">
            <slot />
        </main>
    </div>

    <AppFooter class="max-md:hidden" />
</template>

<script setup lang="ts">
import type { ThemeShellProps } from '~/components/themes/ThemeShell.types';

interface Props extends ThemeShellProps {}
const props = withDefaults(defineProps<Props>(), { inner: false, sideMenu: true });
const emit = defineEmits<{ (e: 'open-menu'): void }>();

/** 본문과 메뉴 사이의 구분선 — 메뉴가 있는 쪽(왼쪽) 가장자리에 긋는다 */
const mainClass = computed(() => (props.sideMenu ? 'md:min-h-[calc(100dvh-6rem)] md:border-l md:border-surface' : ''));
</script>
