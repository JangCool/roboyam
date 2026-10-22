<template>
    <!-- 쪽 번호 — 모바일 5개·데스크톱 10개씩 묶음. 링크라 새 탭·뒤로 가기가 그대로 된다 -->
    <nav v-if="totalPages > 1" :aria-label="t('board.pagerLabel')" class="flex items-center justify-center gap-1 py-4 text-sm">
        <NuxtLink v-if="page > 1" :to="to(page - 1)" :class="cell(false)" :aria-label="t('board.pagePrev')">‹</NuxtLink>
        <span v-else :class="[cell(false), 'pointer-events-none opacity-30']" aria-hidden="true">‹</span>
        <NuxtLink v-for="n in numbers" :key="n" :to="to(n)" :class="cell(n === page)" :aria-current="n === page ? 'page' : undefined" :aria-label="t('board.pageN', { n })">
            {{ n }}
        </NuxtLink>
        <NuxtLink v-if="page < totalPages" :to="to(page + 1)" :class="cell(false)" :aria-label="t('board.pageNext')">›</NuxtLink>
        <span v-else :class="[cell(false), 'pointer-events-none opacity-30']" aria-hidden="true">›</span>
    </nav>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';

interface Props {
    page: number;
    totalPages: number;
    /** 그 쪽으로 가는 주소 */
    to: (page: number) => RouteLocationRaw;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const desktop = useIsDesktop();

const numbers = computed(() => {
    const size = desktop.value ? 10 : 5;
    const start = Math.floor((props.page - 1) / size) * size + 1;
    const end = Math.min(start + size - 1, props.totalPages);
    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
});

function cell(active: boolean): string {
    return [
        'grid h-8 min-w-8 place-items-center rounded-md px-2 tabular-nums focus-visible:outline-2 focus-visible:outline-primary',
        active ? 'bg-primary font-semibold text-primary-contrast' : 'text-muted-color hover:bg-surface-100 dark:hover:bg-surface-800',
    ].join(' ');
}
</script>
