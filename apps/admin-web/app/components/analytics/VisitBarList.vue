<template>
    <!--
        이름 + 숫자 + 비율 막대 목록 — 「방문자 통계」의 순위·분류·요일 평균·들어온/나간 곳이 같은 모양으로 쓴다.
        줄 = (순위) · (꼬리표) · 이름(+ 작은 덧글자) · 숫자(오른쪽, tabular-nums) · 비율, 아래 얇은 막대(`total` 대비 — 없으면 가장 큰 값 대비).
        `color` 를 주면 막대·앞 점이 그 색(유입 분류). `selectable` 이면 이름이 단추(`select(key)`). 강조 줄(`highlight`)은 숫자가 진하다.
    -->
    <ol class="m-0 flex list-none flex-col gap-2.5 p-0">
        <li v-for="(r, i) in rows" :key="r.key" class="flex min-w-0 flex-col gap-1 text-[13px]">
            <span class="flex min-w-0 items-center gap-2">
                <span v-if="rank" class="w-5 shrink-0 text-center text-[12px] font-semibold tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">{{ i + 1 }}</span>
                <span v-if="r.color" class="size-2 shrink-0 rounded-full" :style="{ background: r.color }" aria-hidden="true" />
                <span v-if="r.tag" class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none whitespace-nowrap dark:bg-surface-800">{{ r.tag }}</span>
                <span class="flex min-w-0 flex-1 flex-col">
                    <button
                        v-if="selectable && r.selectable !== false"
                        type="button"
                        class="min-w-0 cursor-pointer truncate rounded text-left hover:text-primary focus-visible:outline-2 focus-visible:outline-primary"
                        :class="r.mono ? 'font-mono text-[12px]' : ''"
                        :title="r.title ?? r.label"
                        @click="emit('select', r.key)"
                    >
                        {{ r.label }}
                    </button>
                    <span v-else class="min-w-0 truncate" :class="r.mono ? 'font-mono text-[12px]' : ''" :title="r.title ?? r.label">{{ r.label }}</span>
                    <span v-if="r.sub" class="truncate font-mono text-[11px] text-muted-color">{{ r.sub }}</span>
                </span>
                <span class="shrink-0 text-right tabular-nums" :class="r.highlight ? 'font-semibold' : ''">{{ r.valueText ?? num(r.value) }}</span>
                <span v-if="showRate" class="w-12 shrink-0 text-right text-[12px] text-muted-color tabular-nums">{{ UtilVisitAnalytics.percent(rate(r.value), locale) }}</span>
            </span>
            <span class="h-1.5 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" :class="rank ? 'ml-7' : ''" aria-hidden="true">
                <span class="block h-full rounded-full" :class="r.color ? '' : 'bg-primary'" :style="{ width: `${barWidth(r.value)}%`, background: r.color || undefined, opacity: r.muted ? 0.45 : undefined }" />
            </span>
        </li>
    </ol>
</template>

<script setup lang="ts">
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

interface Props {
    rows: VisitBarRow[];
    /** 비율의 분모 — 0 이면 가장 큰 값 대비 막대, 비율 글자 없음 */
    total?: number;
    rank?: boolean;
    /** 비율 글자를 보이나(`total` 이 있을 때) */
    rate?: boolean;
    selectable?: boolean;
}

const props = withDefaults(defineProps<Props>(), { total: 0, rank: false, rate: true, selectable: false });

const emit = defineEmits<{ (e: 'select', key: string): void }>();

const { locale } = useI18n();

const showRate = computed(() => props.rate && props.total > 0);
const max = computed(() => Math.max(1, ...props.rows.map((r) => r.value)));

function num(n: number): string {
    return UtilVisitAnalytics.num(n, locale.value);
}

function rate(v: number): number {
    return UtilVisitAnalytics.share(v, props.total);
}

function barWidth(v: number): number {
    return props.total > 0 ? rate(v) : UtilVisitAnalytics.share(v, max.value);
}
</script>
