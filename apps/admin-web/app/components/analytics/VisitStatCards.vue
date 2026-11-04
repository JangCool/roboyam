<template>
    <!--
        「방문자 통계」 쪽의 숫자 카드 한 줄 — 이름(+ ⓘ 툴팁) · 큰 숫자 · 덧글자. 요약 카드(`VisitSummaryStrip`)와 같은 모양에서 증감 칩·미니 추이를 뺐다(검색어·봇·크롤러·정보주체 요청).
        `items` 가 없으면(처음 읽는 중) 같은 칸 수만큼 Skeleton. 숫자 색은 `tone`(주의 노랑 · 문제 빨강).
    -->
    <dl class="m-0 grid grid-cols-2 gap-2" :class="count >= 4 ? 'lg:grid-cols-4' : count === 3 ? 'sm:grid-cols-3' : ''">
        <template v-if="items">
            <div v-for="c in items" :key="c.key" class="flex min-w-0 flex-col gap-1 rounded-xl border border-(--line) bg-(--p-content-background) px-3.5 pt-3 pb-2.5">
                <dt class="flex items-center gap-1 text-xs text-muted-color">
                    <span class="truncate">{{ c.label }}</span>
                    <button
                        v-if="c.help"
                        v-tooltip.top="c.help"
                        type="button"
                        class="inline-grid size-5 shrink-0 place-items-center rounded text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="`${t('visitAnalytics.helpOf', { name: c.label })} — ${c.help}`"
                    >
                        <i class="pi pi-info-circle text-[11px]" aria-hidden="true" />
                    </button>
                </dt>
                <dd class="m-0 flex min-w-0 flex-col gap-0.5">
                    <span class="truncate text-[22px] leading-tight font-semibold tabular-nums" :class="toneClass(c.tone)">{{ c.value }}</span>
                    <span class="truncate text-[12px] text-muted-color tabular-nums">{{ c.sub || ' ' }}</span>
                </dd>
            </div>
        </template>
        <template v-else>
            <div v-for="n in skeletons" :key="n" class="flex flex-col gap-2 rounded-xl border border-(--line) bg-(--p-content-background) px-3.5 py-3">
                <Skeleton height="0.75rem" width="50%" />
                <Skeleton height="1.75rem" width="60%" />
            </div>
        </template>
    </dl>
</template>

<script setup lang="ts">
import type { VisitStatCard } from '~/components/analytics/VisitStatCards.types';

interface Props {
    /** 카드 — null 이면 처음 읽는 중 */
    items?: VisitStatCard[] | null;
    /** 읽는 중에 보일 칸 수 */
    skeletons?: number;
}

const props = withDefaults(defineProps<Props>(), { items: null, skeletons: 4 });

const { t } = useI18n();

const count = computed(() => props.items?.length ?? props.skeletons);

function toneClass(tone: VisitStatCard['tone']): string {
    if (tone === 'danger') return 'text-red-600 dark:text-red-400';
    if (tone === 'warn') return 'text-amber-700 dark:text-amber-400';
    return '';
}
</script>
