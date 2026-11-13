<template>
    <!--
        팁·강좌·사용기 「많이 본 글」 가젯(`TIPS_POPULAR` — `/tips` 배치에만, tips-contract 「/tips 레이아웃 편집」) — 조회순 1~5(서버: 최근 30일 조회순, 모자라면 기간 밖).
        데이터는 화면이 나눠 주는 `useTipsShared`(머리 몫 `GET /api/tips`) — `/tips` 밖이면 그리지 않는다. 머리 몫 오류는 「이번 주 추천」이 다시 시도 단추와 함께 알린다(여기는 한 줄만).
    -->
    <GadgetFrame v-if="tips" :gadget="gadget" :empty="false">
        <div class="min-w-0" :aria-busy="tips.overviewStatus.value === 'loading'" data-testid="tips-most-viewed">
            <div v-if="tips.overviewStatus.value === 'loading'" class="flex flex-col gap-3 py-1" aria-hidden="true">
                <div v-for="n in 5" :key="n" class="h-4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-3/4'" />
            </div>
            <p v-else-if="tips.overviewStatus.value === 'error'" class="m-0 py-2 text-sm text-muted-color">{{ t('tips.loadFailed') }}</p>
            <p v-else-if="rows.length === 0" class="m-0 py-2 text-sm text-muted-color">{{ t('tips.mostViewedEmpty') }}</p>
            <ol v-else class="-mt-2.5 m-0 list-none p-0">
                <li v-for="(row, i) in rows" :key="row.id" class="flex items-start gap-2.5 border-b border-surface last:border-b-0">
                    <span class="w-4 shrink-0 pt-2.5 text-sm font-bold leading-snug tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">
                        <span aria-hidden="true">{{ i + 1 }}</span><span class="sr-only">{{ t('feed.rank', { n: i + 1 }) }}</span>
                    </span>
                    <FoldedRow :reason="tips.foldOf(row)" kind="post" class="min-w-0 flex-1 py-2.5 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${row.id}`" class="group block min-w-0 flex-1 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="tips-most-viewed-row">
                            <p class="line-clamp-2 break-words text-sm leading-snug group-hover:underline">{{ row.title }}</p>
                            <p class="mt-0.5 text-xs text-muted-color">{{ t('board.viewsShort', { n: row.viewCount }) }}</p>
                        </NuxtLink>
                    </FoldedRow>
                </li>
            </ol>
        </div>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';

interface Props {
    gadget: Gadget;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const tips = useTipsShared();

const rows = computed(() => tips?.mostViewed.value ?? []);
</script>
