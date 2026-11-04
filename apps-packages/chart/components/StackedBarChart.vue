<template>
    <!--
        쌓은 막대(Apache ECharts — `echarts/core` 에 막대·격자·툴팁·SVG 렌더러만 등록). 칸(날짜 등)마다 계열을 아래부터 쌓는다(넘긴 차례 = 아래부터).
        색은 계열마다 앱이 준 CSS 식을 풀어 쓴다 — 앱의 다른 막대·범례와 같은 색. 집계 중인 칸(`current`)은 흐리게.
        마우스를 올리면 그 칸 툴팁(칸 이름 · 계열별 값 · 합). 범례는 HTML(색 점 + 이름). 스크린 리더: 요약 `aria-label` + 숨긴 표.
        색은 테마 CSS 변수를 읽어 넣고 `<html>` 속성·테마 `<style>` 이 바뀌면 다시 칠한다(`StatsTrendChart` 와 같은 방식). 서버를 부르지 않는다.
    -->
    <div class="stacked-bar">
        <div ref="box" class="stacked-bar__box" :style="{ height: `${height}px` }" role="img" :aria-label="labels.summary" data-testid="stacked-bar-chart" />
        <span ref="probe" class="stacked-bar__probe" aria-hidden="true" />
        <ul class="stacked-bar__legend" :aria-label="labels.legend">
            <li v-for="s in series" :key="s.key"><span class="stacked-bar__dot" :style="{ background: s.color }" aria-hidden="true" />{{ s.name }}</li>
        </ul>

        <table class="stacked-bar__sr">
            <caption>{{ labels.tableCaption }}</caption>
            <thead>
                <tr>
                    <th scope="col">{{ labels.category }}</th>
                    <th v-for="s in series" :key="s.key" scope="col">{{ s.name }}</th>
                    <th scope="col">{{ labels.total }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="(cat, i) in categories" :key="cat">
                    <th scope="row">{{ fmt.category(cat) }}<template v-if="cat === current"> ({{ labels.current }})</template></th>
                    <td v-for="s in series" :key="s.key">{{ fmt.number(s.values[i] ?? 0) }}</td>
                    <td>{{ fmt.number(totalAt(i)) }}</td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import { type BarSeriesOption, BarChart } from 'echarts/charts';
import { type GridComponentOption, GridComponent, type TooltipComponentOption, TooltipComponent } from 'echarts/components';
import { type ComposeOption, type ECharts, init, use } from 'echarts/core';
import { SVGRenderer } from 'echarts/renderers';
import type { StackedBarFormat, StackedBarLabels, StackedBarSeries } from './StackedBarChart.types';

use([BarChart, GridComponent, TooltipComponent, SVGRenderer]);

type ChartOption = ComposeOption<BarSeriesOption | GridComponentOption | TooltipComponentOption>;

interface Props {
    /** 칸 키(오래된 차례) */
    categories: string[];
    series: StackedBarSeries[];
    labels: StackedBarLabels;
    format?: Partial<StackedBarFormat>;
    /** 집계 중인 칸 키 — 흐리게 */
    current?: string;
    height?: number;
}

const props = withDefaults(defineProps<Props>(), { format: () => ({}), current: '', height: 240 });

const DEFAULT_FORMAT: StackedBarFormat = {
    number: (n) => n.toLocaleString('en-US'),
    axis: (n) => new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(n),
    category: (k) => k,
    shortCategory: (k) => k,
};
const fmt = computed<StackedBarFormat>(() => ({ ...DEFAULT_FORMAT, ...props.format }));

function totalAt(i: number): number {
    return props.series.reduce((a, s) => a + (s.values[i] ?? 0), 0);
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

function escapeHtml(s: string): string {
    return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c);
}

function option(): ChartOption {
    const text = resolveColor('var(--p-text-color, #334155)');
    const muted = resolveColor('var(--p-text-muted-color, #64748b)');
    const line = resolveColor('var(--p-content-border-color, #e2e8f0)');
    const bg = resolveColor('var(--p-content-background, #ffffff)');
    const shade = resolveColor('var(--p-content-hover-background, #f1f5f9)');
    const font = box.value ? getComputedStyle(box.value).fontFamily : 'sans-serif';
    const colors = props.series.map((s) => resolveColor(s.color));
    const f = fmt.value;
    const axisText = { color: muted, fontSize: 10, fontFamily: font };
    const last = props.series.length - 1;
    return {
        animation: !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
        textStyle: { fontFamily: font },
        grid: { left: 4, right: 4, top: 12, bottom: 4, containLabel: true },
        tooltip: {
            trigger: 'axis',
            confine: true,
            axisPointer: { type: 'shadow', shadowStyle: { color: shade, opacity: 0.7 } },
            backgroundColor: bg,
            borderColor: line,
            borderWidth: 1,
            padding: [8, 10],
            textStyle: { color: text, fontSize: 12, fontFamily: font },
            extraCssText: 'min-width:10rem;border-radius:8px;box-shadow:0 4px 12px rgb(0 0 0 / 0.12);',
            formatter: (params: unknown) => {
                const first = (Array.isArray(params) ? params[0] : params) as { dataIndex?: number } | undefined;
                const i = first?.dataIndex;
                if (typeof i !== 'number') return '';
                const cat = props.categories[i] ?? '';
                const now = cat === props.current ? `<span style="font-weight:400;color:${muted}"> · ${escapeHtml(props.labels.current)}</span>` : '';
                const rows = [...props.series]
                    .map((s, si) => ({ s, si }))
                    .reverse()
                    .map(
                        ({ s, si }) =>
                            `<div style="display:flex;justify-content:space-between;gap:12px;font-variant-numeric:tabular-nums"><span style="display:inline-flex;align-items:center;gap:6px;color:${muted}"><span style="width:8px;height:8px;border-radius:2px;background:${colors[si]}"></span>${escapeHtml(s.name)}</span><span>${escapeHtml(f.number(s.values[i] ?? 0))}</span></div>`,
                    )
                    .join('');
                return `<div style="font-weight:600;margin-bottom:2px;font-variant-numeric:tabular-nums">${escapeHtml(f.category(cat))}${now}</div>${rows}<div style="display:flex;justify-content:space-between;gap:12px;margin-top:2px;padding-top:2px;border-top:1px solid ${line};font-variant-numeric:tabular-nums"><span style="color:${muted}">${escapeHtml(props.labels.total)}</span><span style="font-weight:600">${escapeHtml(f.number(totalAt(i)))}</span></div>`;
            },
        },
        xAxis: {
            type: 'category',
            data: props.categories,
            axisLine: { lineStyle: { color: line } },
            axisTick: { show: false },
            axisLabel: { ...axisText, hideOverlap: true, formatter: (v: string) => f.shortCategory(v) },
        },
        yAxis: {
            type: 'value',
            min: 0,
            minInterval: 1,
            splitNumber: 4,
            axisLabel: { ...axisText, formatter: (v: number) => f.axis(v) },
            splitLine: { lineStyle: { color: line, type: [2, 3] } },
        },
        series: props.series.map((s, si) => ({
            type: 'bar',
            name: s.name,
            stack: 'all',
            barCategoryGap: '38%',
            barMaxWidth: 28,
            itemStyle: { color: colors[si], borderRadius: si === last ? [2, 2, 0, 0] : 0 },
            emphasis: { disabled: true },
            data: s.values.map((v, i) => (props.categories[i] === props.current ? { value: v, itemStyle: { opacity: 0.5 } } : v)),
        })),
    };
}

let chart: ECharts | null = null;
function render(): void {
    if (!chart) return;
    chart.setOption(option(), { notMerge: true });
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
    () => [props.categories, props.series, props.labels, props.format, props.current],
    () => render(),
);
watch(
    () => props.height,
    () => nextTick(() => chart?.resize()),
);
</script>

<style scoped>
.stacked-bar {
    position: relative;
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 0.5rem;
}
.stacked-bar__box {
    width: 100%;
}
.stacked-bar__probe {
    position: absolute;
    width: 0;
    height: 0;
    overflow: hidden;
    visibility: hidden;
    pointer-events: none;
}
.stacked-bar__legend {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 1rem;
    margin: 0;
    padding: 0;
    list-style: none;
    font-size: 0.75rem;
    color: var(--p-text-muted-color);
}
.stacked-bar__legend li {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
}
.stacked-bar__dot {
    display: inline-block;
    width: 0.625rem;
    height: 0.625rem;
    border-radius: 2px;
}
.stacked-bar__sr {
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
