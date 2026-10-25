<template>
    <!-- 제재·이의제기 요약 표 — 이름과 값을 한 줄씩. `tone` 이 있는 줄은 값을 색 태그로 그린다. 사유처럼 줄바꿈이 있는 글은 그대로 두 줄로(글자로만 그린다) -->
    <dl v-if="rows.length" class="m-0 overflow-hidden rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" data-testid="sanction-summary">
        <div v-for="row in rows" :key="row.label" class="flex items-baseline gap-3 border-b border-surface px-3.5 py-2.5 text-sm last:border-b-0">
            <dt class="w-20 shrink-0 text-muted-color">{{ row.label }}</dt>
            <dd class="m-0 min-w-0 flex-1 whitespace-pre-line break-words [overflow-wrap:anywhere]">
                <span v-if="row.tone" class="inline-flex items-center justify-center rounded px-1.5 py-1 text-xs font-semibold leading-none" :class="UtilSanction.toneClass(row.tone)">
                    <span class="relative top-[-0.09em]">{{ row.value }}</span>
                </span>
                <template v-else>{{ row.value }}</template>
            </dd>
        </div>
    </dl>
</template>

<script setup lang="ts">
import UtilSanction from '~/utils/util-sanction';
import type { SanctionSummaryRow } from '~/utils/util-sanction-messages';

/** 요약 표 — 줄 목록(`UtilSanctionMessages.summaryRows`·`appealTargetRows`·`appealRows`)을 받아 그리기만 한다. 제재 안내·이의제기 두 화면이 쓴다 */
interface Props {
    rows: SanctionSummaryRow[];
}

withDefaults(defineProps<Props>(), {});
</script>
