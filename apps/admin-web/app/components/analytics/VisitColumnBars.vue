<template>
    <!--
        세로 막대 묶음(HTML) — 시간대별 평균(24칸)·실시간 분당 페이지뷰(30칸)가 같은 모양으로 쓴다. 칸이 많고 값만 비교하면 돼서 차트 라이브러리 없이 그린다.
        가장 큰 칸은 진한 주 색상, 집계 중 칸(`current`)은 빗금·흐리게, 값 없는 칸(null)은 바닥 선만. 아래 칸 이름은 `labelEvery` 칸마다.
        마우스를 올리면 `title`(칸 이름 · 값). 스크린 리더: 요약 `aria-label` + 숨긴 목록.
    -->
    <div class="flex min-w-0 flex-col gap-1">
        <div class="flex items-end gap-[2px]" :style="{ height: `${height}px` }" role="img" :aria-label="summary">
            <span
                v-for="c in items"
                :key="c.key"
                class="min-w-0 flex-1 rounded-t-[2px] transition-[height]"
                :class="barClass(c)"
                :style="{ height: c.value === null ? '1px' : `${Math.max(2, (c.value / max) * 100)}%` }"
                :title="`${c.label} · ${c.value === null ? noneText : format(c.value)}${c.key === current ? ` · ${currentText}` : ''}`"
            />
        </div>
        <div class="flex gap-[2px] text-[10px] text-muted-color tabular-nums" aria-hidden="true">
            <span v-for="(c, i) in items" :key="c.key" class="min-w-0 flex-1 overflow-visible text-left whitespace-nowrap">{{ i % labelEvery === 0 ? c.short ?? c.label : '' }}</span>
        </div>
        <ul class="sr-only">
            <li v-for="c in items" :key="c.key">{{ c.label }}: {{ c.value === null ? noneText : format(c.value) }}</li>
        </ul>
    </div>
</template>

<script setup lang="ts">
interface ColumnItem {
    key: string;
    /** 칸 이름(툴팁·숨긴 목록) */
    label: string;
    /** 축 글자(없으면 `label`) */
    short?: string;
    value: number | null;
}

interface Props {
    items: ColumnItem[];
    summary: string;
    format: (n: number) => string;
    /** 집계 중인 칸 키 */
    current?: string;
    currentText?: string;
    noneText?: string;
    labelEvery?: number;
    height?: number;
}

const props = withDefaults(defineProps<Props>(), { current: '', currentText: '', noneText: '—', labelEvery: 3, height: 120 });

const max = computed(() => Math.max(1, ...props.items.map((c) => c.value ?? 0)));
const peak = computed(() => props.items.reduce((best, c) => (c.value !== null && c.value > (best?.value ?? -1) ? c : best), null as ColumnItem | null)?.key ?? '');

function barClass(c: ColumnItem): string {
    if (c.value === null) return 'bg-surface-200 dark:bg-surface-700';
    if (c.key === props.current) return 'visit-col--current bg-primary opacity-50';
    return c.key === peak.value ? 'bg-primary' : 'bg-primary opacity-55';
}
</script>

<style scoped>
.visit-col--current {
    background-image: repeating-linear-gradient(-45deg, transparent 0 2px, var(--p-content-background) 2px 4px);
}
</style>
