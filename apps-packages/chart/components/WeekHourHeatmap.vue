<template>
    <!--
        요일 × 시 히트맵(Apache ECharts heatmap — `echarts/core` 에 heatmap·격자·툴팁·visualMap(연속)·SVG 렌더러만 등록). 7줄(요일) × 24열(시).
        색은 값이 클수록 주 색상에 가깝다(바탕 → 주 색상, 연속 visualMap — 범례 띠는 HTML 로 따로 그린다). 값이 없는 칸(null)은 테두리만.
        마우스를 올리면 칸 툴팁(요일 · 시 · 값). `compact` 는 축 글자를 줄이고 툴팁만 남긴 작은 모양(개요의 미리보기).
        스크린 리더: 차트 상자는 `role="img"` + 앱이 넘긴 요약 `aria-label`, 칸 값은 숨긴 표(요일 줄 × 시 열).
        색은 테마 CSS 변수를 읽어 넣고 `<html>` 속성·테마 `<style>` 이 바뀌면 다시 칠한다(`StatsTrendChart` 와 같은 방식). 서버를 부르지 않는다.
    -->
    <div class="week-hour">
        <div ref="box" class="week-hour__box" :style="{ height: `${boxHeight}px` }" role="img" :aria-label="labels.summary" data-testid="week-hour-heatmap" />
        <span ref="probe" class="week-hour__probe" aria-hidden="true" />
        <div v-if="!compact" class="week-hour__legend" aria-hidden="true">
            <span>{{ labels.low }}</span>
            <span class="week-hour__ramp" />
            <span>{{ labels.high }}</span>
        </div>

        <table class="week-hour__sr">
            <caption>{{ labels.tableCaption }}</caption>
            <thead>
                <tr>
                    <td />
                    <th v-for="(c, i) in labels.cols" :key="i" scope="col">{{ c }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="(r, ri) in labels.rows" :key="ri">
                    <th scope="row">{{ r }}</th>
                    <td v-for="(_, ci) in labels.cols" :key="ci">{{ cellText(ri, ci) }}</td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import { type HeatmapSeriesOption, HeatmapChart } from 'echarts/charts';
import { type GridComponentOption, GridComponent, type TooltipComponentOption, TooltipComponent, type VisualMapComponentOption, VisualMapContinuousComponent } from 'echarts/components';
import { type ComposeOption, type ECharts, init, use } from 'echarts/core';
import { SVGRenderer } from 'echarts/renderers';
import type { WeekHourCell, WeekHourFormat, WeekHourLabels } from './WeekHourHeatmap.types';

use([HeatmapChart, GridComponent, TooltipComponent, VisualMapContinuousComponent, SVGRenderer]);

type ChartOption = ComposeOption<HeatmapSeriesOption | GridComponentOption | TooltipComponentOption | VisualMapComponentOption>;

interface Props {
    cells: WeekHourCell[];
    labels: WeekHourLabels;
    format?: Partial<WeekHourFormat>;
    compact?: boolean;
    /** 높이(px) — 0 이면 큰 모양 260 · 작은 모양 150 */
    height?: number;
}

const props = withDefaults(defineProps<Props>(), { format: () => ({}), compact: false, height: 0 });

const DEFAULT_FORMAT: WeekHourFormat = { number: (n) => n.toLocaleString('en-US', { maximumFractionDigits: 1 }) };
const fmt = computed<WeekHourFormat>(() => ({ ...DEFAULT_FORMAT, ...props.format }));
const boxHeight = computed(() => props.height || (props.compact ? 150 : 260));

const byKey = computed(() => new Map(props.cells.map((c) => [`${c.row}:${c.col}`, c.value])));
function cellText(row: number, col: number): string {
    const v = byKey.value.get(`${row}:${col}`);
    return v === null || v === undefined ? props.labels.none : fmt.value.number(v);
}

/* ── 색 ── */
interface Palette {
    primary: string;
    low: string;
    text: string;
    muted: string;
    line: string;
    bg: string;
    font: string;
}

const box = ref<HTMLElement | null>(null);
const probe = ref<HTMLElement | null>(null);

function resolveColor(expr: string): string {
    const el = probe.value;
    if (!el) return '';
    el.style.color = '';
    el.style.color = expr;
    return getComputedStyle(el).color;
}

function readPalette(): Palette {
    return {
        primary: resolveColor('var(--p-primary-color, #3b82f6)'),
        low: resolveColor('color-mix(in srgb, var(--p-primary-color, #3b82f6) 8%, var(--p-content-background, #ffffff))'),
        text: resolveColor('var(--p-text-color, #334155)'),
        muted: resolveColor('var(--p-text-muted-color, #64748b)'),
        line: resolveColor('var(--p-content-border-color, #e2e8f0)'),
        bg: resolveColor('var(--p-content-background, #ffffff)'),
        font: box.value ? getComputedStyle(box.value).fontFamily : 'sans-serif',
    };
}

function escapeHtml(s: string): string {
    return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c);
}

function option(c: Palette): ChartOption {
    const values = props.cells.map((x) => x.value).filter((v): v is number => v !== null);
    const max = Math.max(1, ...values);
    const axisText = { color: c.muted, fontSize: props.compact ? 9 : 10, fontFamily: c.font };
    const rows = props.labels.rows;
    return {
        animation: false,
        textStyle: { fontFamily: c.font },
        grid: props.compact ? { left: 4, right: 4, top: 4, bottom: 4, containLabel: true } : { left: 4, right: 8, top: 6, bottom: 4, containLabel: true },
        tooltip: {
            trigger: 'item',
            confine: true,
            backgroundColor: c.bg,
            borderColor: c.line,
            borderWidth: 1,
            padding: [6, 10],
            textStyle: { color: c.text, fontSize: 12, fontFamily: c.font },
            extraCssText: 'border-radius:8px;box-shadow:0 4px 12px rgb(0 0 0 / 0.12);',
            formatter: (params: unknown) => {
                const p = params as { data?: { value?: [number, number, number | string] } };
                const [col, row, v] = p.data?.value ?? [0, 0, '-'];
                const head = `${escapeHtml(rows[row] ?? '')} ${escapeHtml(props.labels.cols[col] ?? '')}`;
                const value = typeof v === 'number' ? fmt.value.number(v) : props.labels.none;
                return `<div style="font-weight:600;margin-bottom:2px;font-variant-numeric:tabular-nums">${head}</div><div style="display:flex;justify-content:space-between;gap:12px;font-variant-numeric:tabular-nums"><span style="color:${c.muted}">${escapeHtml(props.labels.value)}</span><span>${escapeHtml(value)}</span></div>`;
            },
        },
        xAxis: {
            type: 'category',
            data: props.labels.cols,
            splitArea: { show: false },
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { ...axisText, interval: props.compact ? 5 : 2 },
        },
        yAxis: {
            type: 'category',
            data: rows,
            inverse: true,
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: axisText,
        },
        visualMap: {
            type: 'continuous',
            show: false,
            min: 0,
            max,
            dimension: 2,
            inRange: { color: [c.low, c.primary] },
            outOfRange: { color: c.bg },
        },
        series: [
            {
                type: 'heatmap',
                itemStyle: { borderColor: c.bg, borderWidth: props.compact ? 1 : 2, borderRadius: props.compact ? 2 : 3 },
                emphasis: { itemStyle: { borderColor: c.text, borderWidth: 1 } },
                data: props.cells.map((x) =>
                    x.value === null ? { value: [x.col, x.row, '-'] as [number, number, string], itemStyle: { color: c.bg, borderColor: c.line, borderWidth: 1 } } : { value: [x.col, x.row, x.value] as [number, number, number] },
                ),
            },
        ],
    };
}

let chart: ECharts | null = null;
function render(): void {
    if (!chart) return;
    chart.setOption(option(readPalette()), { notMerge: true });
}

let frame = 0;
function scheduleRender(): void {
    if (frame) return;
    frame = requestAnimationFrame(() => {
        frame = 0;
        render();
    });
}

let resizeObserver: ResizeObserver | null = null;
let themeObserver: MutationObserver | null = null;

onMounted(() => {
    if (!box.value) return;
    chart = init(box.value, null, { renderer: 'svg' });
    render();
    resizeObserver = new ResizeObserver(() => chart?.resize());
    resizeObserver.observe(box.value);
    themeObserver = new MutationObserver((records) => {
        if (records.some((r) => r.type === 'attributes' || isStyleNode(r.target) || Array.from(r.addedNodes).some(isStyleNode))) scheduleRender();
    });
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style', 'data-theme', 'data-color-scheme'] });
    themeObserver.observe(document.head, { childList: true, subtree: true, characterData: true });
});

function isStyleNode(node: Node): boolean {
    const el = node.nodeType === Node.TEXT_NODE ? node.parentNode : node;
    return el instanceof HTMLStyleElement;
}

onBeforeUnmount(() => {
    resizeObserver?.disconnect();
    themeObserver?.disconnect();
    if (frame) cancelAnimationFrame(frame);
    chart?.dispose();
    chart = null;
});

watch(
    () => [props.cells, props.labels, props.format, props.compact],
    () => render(),
);
watch(boxHeight, () => nextTick(() => chart?.resize()));
</script>

<style scoped>
.week-hour {
    position: relative;
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 0.5rem;
}
.week-hour__box {
    width: 100%;
}
.week-hour__probe {
    position: absolute;
    width: 0;
    height: 0;
    overflow: hidden;
    visibility: hidden;
    pointer-events: none;
}
.week-hour__legend {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 0.5rem;
    font-size: 0.75rem;
    color: var(--p-text-muted-color);
}
.week-hour__ramp {
    display: inline-block;
    width: 6rem;
    height: 0.5rem;
    border-radius: 999px;
    background: linear-gradient(90deg, color-mix(in srgb, var(--p-primary-color) 8%, var(--p-content-background)), var(--p-primary-color));
}
.week-hour__sr {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
}
</style>
