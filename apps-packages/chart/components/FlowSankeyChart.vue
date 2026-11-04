<template>
    <!--
        이동 흐름 그림(Apache ECharts sankey — `echarts/core` 에 sankey·툴팁·SVG 렌더러만 등록). 「들어온 곳 → 기준 → 나간 곳」 세 열.
        왼쪽 열 = 들어온 곳(글자는 칸 왼쪽), 가운데 = 기준(이름은 차트 밖 — 앱이 머리에 그린다), 오른쪽 열 = 나간 곳(글자는 칸 오른쪽). 차례는 넘긴 차례 그대로(배치 반복 0).
        칸 모양: 페이지(들어온 쪽 흐린 글자색 · 나간 쪽 주 색상) · 바깥(「바로 들어옴」 — 주황) · 그 밖(상위 밖 묶음 — 회색). 선은 양 끝 색의 그라데이션.
        마우스를 올리면 칸·선 툴팁(이름 · 경로 · 이동 횟수), 누를 수 있는 칸(`selectable`)을 누르면 `select(key)` — 앱이 그 페이지를 기준으로 바꾼다.
        스크린 리더: 차트 상자는 `role="img"` + 앱이 넘긴 요약 `aria-label`, 칸별 숫자는 숨긴 표(키보드로 기준을 바꾸는 길은 앱이 따로 둔다 — 순위 표·찾기).
        색은 테마 CSS 변수를 읽어 넣고 `<html>` 속성·테마 `<style>` 이 바뀌면 다시 칠한다(`StatsTrendChart` 와 같은 방식). 서버를 부르지 않는다.
    -->
    <div class="flow-sankey">
        <div ref="box" class="flow-sankey__box" :style="{ height: `${boxHeight}px` }" role="img" :aria-label="labels.summary" data-testid="flow-sankey-chart" />
        <span ref="probe" class="flow-sankey__probe" aria-hidden="true" />

        <table class="flow-sankey__sr">
            <caption>{{ labels.tableCaption }}</caption>
            <thead>
                <tr>
                    <th scope="col">{{ labels.direction }}</th>
                    <th scope="col">{{ labels.page }}</th>
                    <th scope="col">{{ labels.moves }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="e in incoming" :key="`in-${e.key}`">
                    <td>{{ labels.incoming }}</td>
                    <th scope="row">{{ e.detail ?? e.label }}</th>
                    <td>{{ fmt.number(e.value) }}</td>
                </tr>
                <tr v-for="e in outgoing" :key="`out-${e.key}`">
                    <td>{{ labels.outgoing }}</td>
                    <th scope="row">{{ e.detail ?? e.label }}</th>
                    <td>{{ fmt.number(e.value) }}</td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import { type SankeySeriesOption, SankeyChart } from 'echarts/charts';
import { type TooltipComponentOption, TooltipComponent } from 'echarts/components';
import { type ComposeOption, type ECharts, init, use } from 'echarts/core';
import { SVGRenderer } from 'echarts/renderers';
import type { FlowSankeyCenter, FlowSankeyEnd, FlowSankeyFormat, FlowSankeyLabels, FlowSankeyTone } from './FlowSankeyChart.types';

use([SankeyChart, TooltipComponent, SVGRenderer]);

type ChartOption = ComposeOption<SankeySeriesOption | TooltipComponentOption>;

interface Props {
    center: FlowSankeyCenter;
    /** 들어온 곳(위에서 아래 차례) */
    incoming?: FlowSankeyEnd[];
    /** 나간 곳(위에서 아래 차례) */
    outgoing?: FlowSankeyEnd[];
    labels: FlowSankeyLabels;
    format?: Partial<FlowSankeyFormat>;
    /** 차트 높이(px) — 0 이면 칸 수로 정한다(최소 220) */
    height?: number;
}

const props = withDefaults(defineProps<Props>(), { incoming: () => [], outgoing: () => [], format: () => ({}), height: 0 });

const emit = defineEmits<{ (e: 'select', key: string): void }>();

const DEFAULT_FORMAT: FlowSankeyFormat = { number: (n) => n.toLocaleString('en-US') };
const fmt = computed<FlowSankeyFormat>(() => ({ ...DEFAULT_FORMAT, ...props.format }));

const ins = computed(() => props.incoming.filter((e) => e.value > 0));
const outs = computed(() => props.outgoing.filter((e) => e.value > 0));
const boxHeight = computed(() => props.height || Math.max(220, Math.max(ins.value.length, outs.value.length, 1) * 46 + 24));

/* ── 색(테마 CSS 변수 → 실제 색) ── */
interface Palette {
    primary: string;
    text: string;
    muted: string;
    line: string;
    bg: string;
    entry: string;
    rest: string;
    font: string;
}

const box = ref<HTMLElement | null>(null);
const probe = ref<HTMLElement | null>(null);

/** 숨긴 칸에 `color: <식>` 을 걸어 실제 색을 읽는다 — `var()`·`light-dark()`·`color-mix()` 가 다 풀린다 */
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
        text: resolveColor('var(--p-text-color, #334155)'),
        muted: resolveColor('var(--p-text-muted-color, #64748b)'),
        line: resolveColor('var(--p-content-border-color, #e2e8f0)'),
        bg: resolveColor('var(--p-content-background, #ffffff)'),
        entry: resolveColor('light-dark(var(--p-amber-500, #f59e0b), var(--p-amber-400, #fbbf24))'),
        rest: resolveColor('light-dark(var(--p-surface-300, #cbd5e1), var(--p-surface-600, #475569))'),
        font: box.value ? getComputedStyle(box.value).fontFamily : 'sans-serif',
    };
}

/* ── 옵션 ── */
interface NodeInfo {
    key: string;
    label: string;
    detail: string;
    side: 'in' | 'center' | 'out';
    value: number;
    selectable: boolean;
}

function escapeHtml(s: string): string {
    return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c);
}

/** 그린 칸 — 이름(`in:0` · `center` · `out:0`) → 정보. 툴팁·누르기가 읽는다 */
let nodeMap = new Map<string, NodeInfo>();

function toneColor(c: Palette, tone: FlowSankeyTone | undefined, side: 'in' | 'out'): string {
    if (tone === 'entry') return c.entry;
    if (tone === 'rest') return c.rest;
    return side === 'in' ? c.muted : c.primary;
}

function option(c: Palette): ChartOption {
    const width = box.value?.clientWidth ?? 600;
    // 양옆 글자 자리 — 좁은 화면은 110px, 넓으면 폭의 26%(최대 220px)
    const side = Math.round(Math.min(220, Math.max(110, width * 0.26)));
    const map = new Map<string, NodeInfo>();
    const inTotal = ins.value.reduce((a, e) => a + e.value, 0);
    const outTotal = outs.value.reduce((a, e) => a + e.value, 0);

    const nodes: NonNullable<SankeySeriesOption['data']> = [];
    ins.value.forEach((e, i) => {
        const name = `in:${i}`;
        map.set(name, { key: e.key, label: e.label, detail: e.detail ?? e.label, side: 'in', value: e.value, selectable: e.selectable ?? false });
        nodes.push({ name, depth: 0, itemStyle: { color: toneColor(c, e.tone, 'in') }, label: { position: 'left' } });
    });
    map.set('center', { key: props.center.key, label: props.center.label, detail: props.center.detail ?? props.center.label, side: 'center', value: Math.max(inTotal, outTotal), selectable: false });
    nodes.push({ name: 'center', depth: 1, itemStyle: { color: c.primary }, label: { show: false } });
    outs.value.forEach((e, i) => {
        const name = `out:${i}`;
        map.set(name, { key: e.key, label: e.label, detail: e.detail ?? e.label, side: 'out', value: e.value, selectable: e.selectable ?? false });
        nodes.push({ name, depth: 2, itemStyle: { color: toneColor(c, e.tone, 'out') }, label: { position: 'right' } });
    });
    nodeMap = map;

    const links: NonNullable<SankeySeriesOption['links']> = [
        ...ins.value.map((e, i) => ({ source: `in:${i}`, target: 'center', value: e.value })),
        ...outs.value.map((e, i) => ({ source: 'center', target: `out:${i}`, value: e.value })),
    ];

    const num = fmt.value.number;
    return {
        animation: motionOk(),
        textStyle: { fontFamily: c.font },
        tooltip: {
            trigger: 'item',
            confine: true,
            backgroundColor: c.bg,
            borderColor: c.line,
            borderWidth: 1,
            padding: [8, 10],
            textStyle: { color: c.text, fontSize: 12, fontFamily: c.font },
            extraCssText: 'max-width:20rem;white-space:normal;border-radius:8px;box-shadow:0 4px 12px rgb(0 0 0 / 0.12);',
            formatter: (params: unknown) => {
                const p = params as { dataType?: string; name?: string; data?: { source?: string; target?: string; value?: number } };
                const row = (label: string, value: string) =>
                    `<div style="display:flex;justify-content:space-between;gap:12px;font-variant-numeric:tabular-nums"><span style="color:${c.muted}">${escapeHtml(label)}</span><span>${escapeHtml(value)}</span></div>`;
                if (p.dataType === 'edge') {
                    const from = nodeMap.get(p.data?.source ?? '');
                    const to = nodeMap.get(p.data?.target ?? '');
                    if (!from || !to) return '';
                    return `<div style="font-weight:600;margin-bottom:2px">${escapeHtml(from.label)} → ${escapeHtml(to.label)}</div>${row(props.labels.movesTip, num(p.data?.value ?? 0))}`;
                }
                const n = nodeMap.get(p.name ?? '');
                if (!n) return '';
                const detail = n.detail !== n.label ? `<div style="color:${c.muted};font-size:11px;word-break:break-all">${escapeHtml(n.detail)}</div>` : '';
                const hint = n.selectable ? `<div style="margin-top:4px;color:${c.primary};font-size:11px">${escapeHtml(props.labels.selectHint)}</div>` : '';
                return `<div style="font-weight:600">${escapeHtml(n.label)}</div>${detail}${row(props.labels.movesTip, num(n.value))}${hint}`;
            },
        },
        series: [
            {
                type: 'sankey',
                left: side,
                right: side,
                top: 8,
                bottom: 8,
                nodeWidth: 10,
                nodeGap: 12,
                nodeAlign: 'justify',
                layoutIterations: 0,
                draggable: false,
                emphasis: { focus: 'adjacency' },
                label: {
                    color: c.text,
                    fontSize: 12,
                    fontFamily: c.font,
                    width: side - 14,
                    overflow: 'truncate',
                    formatter: (params: unknown) => {
                        const n = nodeMap.get((params as { name?: string }).name ?? '');
                        return n ? `${n.label}\n{n|${num(n.value)}}` : '';
                    },
                    rich: { n: { color: c.muted, fontSize: 11, fontFamily: c.font, padding: [2, 0, 0, 0] } },
                },
                itemStyle: { borderWidth: 0, borderRadius: 2 },
                lineStyle: { color: 'gradient', opacity: 0.28, curveness: 0.5 },
                data: nodes,
                links,
            },
        ],
    };
}

function motionOk(): boolean {
    return !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

/* ── 그리기 ── */
let chart: ECharts | null = null;

function render(): void {
    if (!chart) return;
    // 칸 수·이름이 바뀌면 통째로 갈아 끼운다(합치면 지난 칸이 남는다)
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
let lastWidth = 0;

onMounted(() => {
    if (!box.value) return;
    chart = init(box.value, null, { renderer: 'svg' });
    chart.on('click', (params) => {
        const p = params as { dataType?: string; name?: string };
        if (p.dataType !== 'node') return;
        const n = nodeMap.get(p.name ?? '');
        if (n?.selectable) emit('select', n.key);
    });
    render();
    lastWidth = box.value.clientWidth;
    // 폭이 바뀌면 양옆 글자 자리도 바뀌어 옵션을 다시 만든다
    resizeObserver = new ResizeObserver(() => {
        chart?.resize();
        const w = box.value?.clientWidth ?? 0;
        if (w !== lastWidth) {
            lastWidth = w;
            scheduleRender();
        }
    });
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
    () => [props.center, props.incoming, props.outgoing, props.labels, props.format],
    () => render(),
);
watch(boxHeight, () => nextTick(() => chart?.resize()));
</script>

<style scoped>
.flow-sankey {
    position: relative;
    min-width: 0;
}
.flow-sankey__box {
    width: 100%;
}
.flow-sankey__probe {
    position: absolute;
    width: 0;
    height: 0;
    overflow: hidden;
    visibility: hidden;
    pointer-events: none;
}
.flow-sankey__sr {
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
