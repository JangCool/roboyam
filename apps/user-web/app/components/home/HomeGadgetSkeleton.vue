<template>
    <!-- 홈 가젯을 받는 동안의 자리 — 큰 글 한 장 + 글 줄 두 묶음. 받은 뒤 레이아웃이 크게 튀지 않게 실제와 비슷한 높이 -->
    <div class="gadget-stack" role="status" :aria-label="t('home.loading')" data-testid="home-skeleton">
        <section class="gadget-card">
            <div class="grid gap-3 py-3 md:grid-cols-[13rem_minmax(0,1fr)]">
                <div class="aspect-[16/10] animate-pulse rounded-md bg-surface-200 dark:bg-surface-800" />
                <div>
                    <div class="h-5 w-11/12 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="mt-2 h-5 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="mt-3 h-3 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
            </div>
        </section>
        <section v-for="block in 2" :key="block" class="gadget-card">
            <div class="my-3 h-4 w-24 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 5" :key="n" class="border-b border-surface py-3 last:border-b-0">
                <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
                <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
const { t } = useI18n();
</script>
