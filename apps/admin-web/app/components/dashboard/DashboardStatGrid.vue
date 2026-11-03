<template>
    <!-- 숫자 카드 격자 — 가젯 폭에 맞춰 칸 수가 바뀐다(최소 폭 `min` rem, `cols` 를 주면 그 열 수로 고정). 한 줄의 카드는 같은 높이로 선다(링크 줄이 같은 높이에). `row` 면 한 줄 행을 촘촘히 -->
    <ul class="m-0 grid list-none p-0" :class="row ? 'gap-2' : 'gap-3'" :style="{ gridTemplateColumns: cols ? `repeat(${cols}, minmax(0, 1fr))` : `repeat(auto-fill, minmax(min(100%, ${min}rem), 1fr))` }">
        <li v-for="s in stats" :key="s.key" class="min-w-0">
            <DashboardStatCard :label="s.label" :count="s.count" :note="s.note" :icon="s.icon" :tone="s.tone" :to="s.to" :links="s.links" :compact="compact" :row="row" />
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { DashboardStat } from '~/components/dashboard/DashboardStatCard.types';

interface Props {
    stats: DashboardStat[];
    /** 카드 최소 폭(rem) */
    min?: number;
    /** 열 수 고정(주면 `min` 은 쓰지 않는다) */
    cols?: number;
    /** 설명 자리를 잡지 않는다 */
    compact?: boolean;
    /** 한 줄 행 모양 */
    row?: boolean;
}

withDefaults(defineProps<Props>(), { min: 13, cols: undefined, compact: false, row: false });
</script>
