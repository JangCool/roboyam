<template>
    <!--
        「이번 주 상담 순위」(ai-love-counsel-contract 「화면」 1) — 연애·결혼 목록에서 「AI 상담소」 말머리를 골랐을 때 말머리 줄과 정렬·검색 줄 사이.
        사연 쓴 사람이 「가장 도움 된 조언」으로 고른 수 기준(서버 집계 — 이번 주 월~일). 사람 회원은 한 줄(「사람 회원」), AI 상담사는 한 명씩(이름 + AI 배지 「AI · 모델」).
        줄마다 순위 · 이름 · 비율 막대 · 비율. 막대 색: 사람 회원 = 주 색, AI = 보라(AI 배지와 같은 계열) — 글자(이름·%)가 늘 함께 있다(색만으로 가르지 않는다).
    -->
    <section :aria-labelledby="headingId" class="mt-2 rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-800/60" data-testid="counsel-ranking">
        <div class="flex flex-wrap items-baseline justify-between gap-x-2 gap-y-0.5">
            <h2 :id="headingId" class="m-0 flex items-center gap-1.5 text-sm font-semibold">
                <i class="pi pi-trophy text-xs text-amber-600 dark:text-amber-400" aria-hidden="true" />{{ t('counsel.rankingTitle') }}
            </h2>
            <span v-if="ranking" class="text-[0.6875rem] text-muted-color tabular-nums">{{ t('counsel.rankingBasis', { from: shortDate(ranking.weekStart), to: shortDate(ranking.weekEnd) }) }}</span>
        </div>

        <div v-if="state === 'loading'" class="mt-2 flex flex-col gap-2" role="status" :aria-label="t('counsel.rankingLoading')">
            <div v-for="n in 4" :key="n" class="h-4 animate-pulse rounded bg-surface-200 dark:bg-surface-700" />
        </div>
        <p v-else-if="state === 'error'" class="m-0 mt-1.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-color" role="alert">
            {{ t('counsel.rankingFailed') }}
            <Button type="button" :label="t('common.retry')" size="small" variant="text" @click="$emit('retry')" />
        </p>
        <p v-else-if="ranking && ranking.totalPicks === 0" class="m-0 mt-1.5 text-xs text-muted-color">{{ t('counsel.rankingEmpty') }}</p>
        <ol v-else-if="ranking" class="m-0 mt-2 flex list-none flex-col gap-1.5 p-0">
            <li
                v-for="r in ranking.rows"
                :key="r.kind === 'HUMAN' ? 'human' : (r.name ?? r.rank)"
                class="grid grid-cols-[2rem_minmax(0,1fr)_minmax(3.5rem,38%)_2.5rem] items-center gap-2 text-xs"
                :data-rank-kind="r.kind"
            >
                <span class="sr-only">{{ t('counsel.rankRowAria', { rank: r.rank, name: rowName(r), percent: r.percent }) }}</span>
                <span class="font-semibold tabular-nums" :class="r.rank === 1 ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'" aria-hidden="true">{{ t('counsel.rankN', { n: r.rank }) }}</span>
                <span class="flex min-w-0 items-center gap-1.5" aria-hidden="true">
                    <span class="truncate font-medium" :class="r.kind === 'HUMAN' ? 'text-color' : ''">{{ rowName(r) }}</span>
                    <AiBadge v-if="r.ai" :provider="r.ai.provider" :model="r.ai.model" plain />
                </span>
                <span class="h-2 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" aria-hidden="true">
                    <span class="block h-full rounded-full" :class="r.kind === 'HUMAN' ? 'bg-primary' : 'bg-violet-500 dark:bg-violet-400'" :style="{ width: `${r.percent}%` }" />
                </span>
                <span class="text-right font-semibold tabular-nums" aria-hidden="true">{{ r.percent }}%</span>
            </li>
        </ol>
    </section>
</template>

<script setup lang="ts">
import type { CounselRanking, CounselRankRow } from '~/composables/counsel/api/useCounselApi.types';

interface Props {
    ranking: CounselRanking | null;
    state: 'loading' | 'ready' | 'error';
}

withDefaults(defineProps<Props>(), {});

defineEmits<{ retry: [] }>();

const { t } = useI18n();
const headingId = useId();

function rowName(r: CounselRankRow): string {
    return r.kind === 'HUMAN' ? t('counsel.rankHuman') : (r.name ?? '');
}

/** `YYYY-MM-DD` → `M/D`(언어와 상관없는 숫자 표기) */
function shortDate(day: string): string {
    const [, m, d] = day.split('-');
    return `${Number(m)}/${Number(d)}`;
}
</script>
