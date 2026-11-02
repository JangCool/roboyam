<template>
    <!--
        날짜별 추이 차트(Apache ECharts — `echarts/core` 에 막대·선·격자·툴팁·SVG 렌더러만 등록). 두 앱의 광고 성과(사업자 한 건 · 운영자 합계 · 일정표 서랍)가 같은 모양으로 쓴다.
        막대 = 노출(왼쪽 축, 주 색상) · 선 = 클릭(오른쪽 축, 글자 색 — 주 색상을 무엇으로 바꿔도 막대와 갈린다). 오늘 막대는 빗금·흐리게(집계 중).
        그날 상태가 정지면 주황 띠, 내림·중단이면 빨강 띠를 막대 뒤에 깐다(띠는 숨긴 둘째 x 축의 100% 폭 막대 — 카테고리 축의 markArea 는 칸 가운데에서 가운데까지만 덮어서).
        마우스·터치를 올리거나, 차트에 초점을 두고 왼쪽·오른쪽(Home·End) 화살표로 날짜를 옮기면 그날 툴팁(날짜 · 노출 · 클릭 · 클릭률 · 상태)이 뜬다(Esc 로 닫음, `dispatchAction showTip`).
        `compact` 는 축·눈금·툴팁·범례·초점 없는 노출 미니 막대(사업자 센터 줄).
        스크린 리더: 차트 상자는 `role="img"` + 앱이 넘긴 요약 `aria-label`(툴팁·SVG 는 그 안이라 읽히지 않는다), 날짜별 숫자는 숨긴 표.
        색은 테마 CSS 변수를 읽어 넣는다 — `<html>` 의 속성(다크 `data-color-scheme`·`data-theme`, `class`·`style`)이나 PrimeVue 테마 `<style>` 이 바뀌면 다시 칠한다.
        문구·글자 함수는 앱이 번역·서식해 넘긴다(레이어는 앱의 i18n 키를 모른다). 서버를 부르지 않는다.
    -->
    <div class="stats-trend">
        <div
            class="stats-trend__frame"
            :class="{ 'stats-trend__frame--focusable': !compact }"
            :tabindex="compact ? undefined : 0"
            :role="compact ? undefined : 'group'"
            :aria-label="compact ? undefined : labels.keys"
            data-testid="stats-trend-chart"
            @keydown="onKey"
            @focus="onFocus"
            @blur="onBlur"
        >
            <div ref="box" class="stats-trend__box" :style="{ height: `${boxHeight}px` }" role="img" :aria-label="labels.summary" />
            <span ref="probe" class="stats-trend__probe" aria-hidden="true" />
        </div>

        <!-- 범례 -->
        <ul v-if="!compact" class="stats-trend__legend" :aria-label="labels.legend">
            <li><span class="stats-trend__swatch stats-trend__swatch--bar" aria-hidden="true" />{{ labels.legendImpressions }}</li>
            <li>
                <svg width="18" height="10" aria-hidden="true"><line x1="1" y1="5" x2="17" y2="5" class="stats-trend__legend-line" /><circle cx="9" cy="5" r="2.5" class="stats-trend__legend-dot" /></svg>{{ labels.legendClicks }}
            </li>
            <li v-if="hasState.PAUSED"><span class="stats-trend__swatch stats-trend__swatch--paused" aria-hidden="true" />{{ labels.legendPaused }}</li>
            <li v-if="hasState.TAKEN_DOWN"><span class="stats-trend__swatch stats-trend__swatch--taken-down" aria-hidden="true" />{{ labels.legendTakenDown }}</li>
            <li v-if="hasToday"><span class="stats-trend__swatch stats-trend__swatch--today" aria-hidden="true" />{{ labels.legendToday }}</li>
        </ul>

        <!-- 스크린 리더용 표 -->
        <table v-if="!compact" class="stats-trend__sr">
            <caption>{{ labels.tableCaption }}</caption>
            <thead>
                <tr>
                    <th scope="col">{{ labels.date }}</th>
                    <th scope="col">{{ labels.impressions }}</th>
                    <th scope="col">{{ labels.clicks }}</th>
                    <th scope="col">{{ labels.ctr }}</th>
                    <th v-if="hasAnyState" scope="col">{{ labels.state }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="d in days" :key="d.date">
                    <th scope="row">{{ fmt.date(d.date) }}<template v-if="d.date === today"> ({{ labels.today }})</template></th>
                    <td>{{ fmt.number(d.impressions) }}</td>
                    <td>{{ fmt.number(d.clicks) }}</td>
                    <td>{{ fmt.ctr(d) }}</td>
                    <td v-if="hasAnyState">{{ labels.states[d.state ?? 'SHOWING'] }}</td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import { type BarSeriesOption, BarChart, type LineSeriesOption, LineChart } from 'echarts/charts';
import { type GridComponentOption, GridComponent, type TooltipComponentOption, TooltipComponent } from 'echarts/components';
import { type ComposeOption, type ECharts, init, use } from 'echarts/core';
import { SVGRenderer } from 'echarts/renderers';
import type { StatsTrendDay, StatsTrendDayState, StatsTrendFormat, StatsTrendLabels } from './StatsTrendChart.types';

use([BarChart, LineChart, GridComponent, TooltipComponent, SVGRenderer]);

type ChartOption = ComposeOption<BarSeriesOption | LineSeriesOption | GridComponentOption | TooltipComponentOption>;

interface Props {
    /** 날짜별 숫자(오래된 차례, 빈 날 없이) */
    days: StatsTrendDay[];
    /** 앱이 번역한 문구 */
    labels: StatsTrendLabels;
    /** 앱의 숫자·날짜 글자 함수(빠진 것은 레이어 기본) */
    format?: Partial<StatsTrendFormat>;
    /** 오늘(`YYYY-MM-DD`) — 이 날 막대는 빗금·흐리게(집계 중). 기간 밖이거나 비우면 없음 */
    today?: string;
    /** 미니 막대 — 축·툴팁·범례·초점 없음 */
    compact?: boolean;
    /** 차트 높이(px) — 0 이면 큰 모양 220 · 미니 36 */
    height?: number;
}

const props = withDefaults(defineProps<Props>(), { format: () => ({}), today: '', compact: false, height: 0 });

const DEFAULT_FORMAT: StatsTrendFormat = {
    number: (n) => n.toLocaleString('en-US'),
    axis: (n) => new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(n),
    date: (ymd) => ymd,
    shortDate: (ymd) => `${Number(ymd.slice(5, 7))}/${Number(ymd.slice(8, 10))}`,
    ctr: (c) => (c.impressions > 0 ? `${((c.clicks / c.impressions) * 100).toFixed(1)}%` : '—'),
};

const fmt = computed<StatsTrendFormat>(() => ({ ...DEFAULT_FORMAT, ...props.format }));
const boxHeight = computed(() => props.height || (props.compact ? 36 : 220));
const hasState = computed(() => ({ PAUSED: props.days.some((d) => d.state === 'PAUSED'), TAKEN_DOWN: props.days.some((d) => d.state === 'TAKEN_DOWN') }));
const hasAnyState = computed(() => props.days.some((d) => d.state !== undefined));
const hasToday = computed(() => Boolean(props.today) && props.days.some((d) => d.date === props.today));

/* ── 색(테마 CSS 변수 → 실제 색) ── */
interface Palette {
    primary: string;
    text: string;
    muted: string;
    line: string;
    bg: string;
    shade: string;
    paused: string;
    takenDown: string;
    pausedText: string;
    takenDownText: string;
    font: string;
}

const box = ref<HTMLElement | null>(null);
const probe = ref<HTMLElement | null>(null);

/** 숨긴 칸에 `color: <식>` 을 걸어 실제 색을 읽는다 — `var()`·`light-dark()`·`color-mix()` 가 다 풀린다(변수를 그대로 읽으면 식 글자가 나온다) */
function resolveColor(expr: string): string {
    const el = probe.value;
    if (!el) return '';
    el.style.color = '';
    el.style.color = expr;
    return getComputedStyle(el).color;
}

function readPalette(): Palette {
    return {
        primary: resolveColor('var(--p-primary-color, #10b981)'),
        text: resolveColor('var(--p-text-color, #334155)'),
        muted: resolveColor('var(--p-text-muted-color, #64748b)'),
        line: resolveColor('var(--p-content-border-color, #e2e8f0)'),
        bg: resolveColor('var(--p-content-background, #ffffff)'),
        shade: resolveColor('var(--p-content-hover-background, #f1f5f9)'),
        paused: resolveColor('var(--p-amber-400, #fbbf24)'),
        takenDown: resolveColor('var(--p-red-400, #f87171)'),
        pausedText: resolveColor('light-dark(var(--p-amber-700, #b45309), var(--p-amber-300, #fcd34d))'),
        takenDownText: resolveColor('light-dark(var(--p-red-600, #dc2626), var(--p-red-400, #f87171))'),
        font: box.value ? getComputedStyle(box.value).fontFamily : 'sans-serif',
    };
}

/* ── 옵션 ── */
function escapeHtml(s: string): string {
    return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c);
}

function bandSeries(c: Palette, yAxisIndex: number): BarSeriesOption {
    const color = (s: StatsTrendDayState) => (s === 'PAUSED' ? c.paused : c.takenDown);
    return {
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex,
        barWidth: '100%',
        silent: true,
        animation: false,
        z: 1,
        tooltip: { show: false },
        emphasis: { disabled: true },
        data: props.days.map((d) => (d.state && d.state !== 'SHOWING' ? { value: 1, itemStyle: { color: color(d.state), opacity: d.state === 'PAUSED' ? 0.22 : 0.18 } } : '-')),
    };
}

function barData(c: Palette): BarSeriesOption['data'] {
    return props.days.map((d) =>
        d.date === props.today
            ? { value: d.impressions, itemStyle: { opacity: 0.5, decal: { symbol: 'rect', color: c.bg, dashArrayX: [1, 0], dashArrayY: [2, 3], rotation: -Math.PI / 4 } } }
            : d.impressions,
    );
}

function tooltipHtml(i: number, c: Palette): string {
    const d = props.days[i];
    if (!d) return '';
    const f = fmt.value;
    const row = (label: string, value: string) =>
        `<div style="display:flex;justify-content:space-between;gap:12px;font-variant-numeric:tabular-nums"><span style="color:${c.muted}">${escapeHtml(label)}</span><span>${escapeHtml(value)}</span></div>`;
    const today = d.date === props.today ? `<span style="font-weight:400;color:${c.muted}"> · ${escapeHtml(props.labels.today)}</span>` : '';
    const state =
        d.state && d.state !== 'SHOWING'
            ? `<div style="margin-top:2px;color:${d.state === 'PAUSED' ? c.pausedText : c.takenDownText}">${escapeHtml(props.labels.states[d.state])}</div>`
            : '';
    return `<div style="font-weight:600;margin-bottom:2px;font-variant-numeric:tabular-nums">${escapeHtml(f.date(d.date))}${today}</div>${row(props.labels.impressions, f.number(d.impressions))}${row(props.labels.clicks, f.number(d.clicks))}${row(props.labels.ctr, f.ctr(d))}${state}`;
}

function compactOption(c: Palette): ChartOption {
    const dates = props.days.map((d) => d.date);
    return {
        animation: motionOk(),
        grid: { left: 0, right: 0, top: 2, bottom: 1 },
        tooltip: { show: false },
        xAxis: [
            { type: 'category', data: dates, axisLine: { lineStyle: { color: c.line } }, axisTick: { show: false }, axisLabel: { show: false }, axisPointer: { show: false } },
            { type: 'category', data: dates, show: false },
        ],
        yAxis: [
            { type: 'value', min: 0, show: false, splitLine: { show: false } },
            { type: 'value', min: 0, max: 1, show: false, splitLine: { show: false } },
        ],
        series: [
            bandSeries(c, 1),
            { type: 'bar', xAxisIndex: 0, yAxisIndex: 0, z: 2, silent: true, barCategoryGap: '38%', barMaxWidth: 14, itemStyle: { color: c.primary, borderRadius: [2, 2, 0, 0] }, emphasis: { disabled: true }, data: barData(c) },
        ],
    };
}

function fullOption(c: Palette): ChartOption {
    const f = fmt.value;
    const dates = props.days.map((d) => d.date);
    const axisText = { color: c.muted, fontSize: 10, fontFamily: c.font };
    return {
        animation: motionOk(),
        textStyle: { fontFamily: c.font },
        grid: { left: 4, right: 4, top: 12, bottom: 4, containLabel: true },
        tooltip: {
            trigger: 'axis',
            confine: true,
            axisPointer: { type: 'shadow', shadowStyle: { color: c.shade, opacity: 0.7 } },
            backgroundColor: c.bg,
            borderColor: c.line,
            borderWidth: 1,
            padding: [8, 10],
            textStyle: { color: c.text, fontSize: 12, fontFamily: c.font },
            extraCssText: 'min-width:9rem;border-radius:8px;box-shadow:0 4px 12px rgb(0 0 0 / 0.12);',
            formatter: (params: unknown) => {
                const first = (Array.isArray(params) ? params[0] : params) as { dataIndex?: number } | undefined;
                return typeof first?.dataIndex === 'number' ? tooltipHtml(first.dataIndex, c) : '';
            },
        },
        xAxis: [
            {
                type: 'category',
                data: dates,
                axisLine: { lineStyle: { color: c.line } },
                axisTick: { show: false },
                axisLabel: {
                    ...axisText,
                    hideOverlap: true,
                    formatter: (value: string) => (value === props.today ? `{today|${f.shortDate(value)}}` : f.shortDate(value)),
                    rich: { today: { ...axisText, fontWeight: 600 } },
                },
            },
            { type: 'category', data: dates, show: false, axisPointer: { show: false, triggerTooltip: false } },
        ],
        yAxis: [
            {
                type: 'value',
                position: 'left',
                min: 0,
                minInterval: 1,
                splitNumber: 4,
                axisLabel: { ...axisText, formatter: (v: number) => f.axis(v) },
                splitLine: { lineStyle: { color: c.line, type: [2, 3] } },
            },
            { type: 'value', position: 'right', min: 0, minInterval: 1, splitNumber: 4, alignTicks: true, axisLabel: { ...axisText, formatter: (v: number) => f.axis(v) }, splitLine: { show: false } },
            { type: 'value', min: 0, max: 1, show: false, splitLine: { show: false } },
        ],
        series: [
            bandSeries(c, 2),
            {
                type: 'bar',
                name: props.labels.impressions,
                xAxisIndex: 0,
                yAxisIndex: 0,
                z: 2,
                barCategoryGap: '38%',
                barMaxWidth: 28,
                itemStyle: { color: c.primary, opacity: 0.85, borderRadius: [2, 2, 0, 0] },
                emphasis: { disabled: true },
                data: barData(c),
            },
            {
                type: 'line',
                name: props.labels.clicks,
                xAxisIndex: 0,
                yAxisIndex: 1,
                z: 3,
                symbol: 'circle',
                symbolSize: 6,
                lineStyle: { color: c.text, width: 1.75 },
                itemStyle: { color: c.text, borderColor: c.text, borderWidth: 1.5 },
                emphasis: { disabled: true },
                data: props.days.map((d) => (d.date === props.today ? { value: d.clicks, itemStyle: { color: c.bg } } : d.clicks)),
            },
        ],
    };
}

function motionOk(): boolean {
    return !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

/* ── 그리기 ── */
let chart: ECharts | null = null;
let shownMode: boolean | null = null;
/** 키보드로 고른 날(툴팁) · 차트에 초점이 있나 */
let active: number | null = null;
let focused = false;

function render(): void {
    if (!chart) return;
    const c = readPalette();
    const option = props.compact ? compactOption(c) : fullOption(c);
    // 모양(미니 ↔ 큰)이 바뀌면 계열·축 수가 달라 통째로 갈아 끼운다. 그 밖은 합쳐서(숫자·색만 바뀌면 처음부터 다시 자라지 않는다)
    chart.setOption(option, { notMerge: shownMode !== props.compact });
    shownMode = props.compact;
    if (active !== null) showTip(active);
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
    // 다크 모드·테마·강조색 — user-web 은 html[data-color-scheme]·[data-theme], admin-web 은 html[data-theme](다크)·style(강조색 변수) 이고, PrimeVue 팔레트 바꾸기는 <head> 의 <style> 을 고친다
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
    () => [props.days, props.labels, props.format, props.today, props.compact],
    () => {
        if (active !== null && active >= props.days.length) active = null;
        render();
    },
);
watch(boxHeight, () => nextTick(() => chart?.resize()));

/* ── 키보드(화살표로 날짜 옮기기) ── */
function showTip(i: number): void {
    chart?.dispatchAction({ type: 'showTip', seriesIndex: 1, dataIndex: i });
}

function hideTip(): void {
    chart?.dispatchAction({ type: 'hideTip' });
}

function onFocus(): void {
    if (props.compact || !props.days.length) return;
    focused = true;
    active = active ?? props.days.length - 1;
    showTip(active);
}

function onBlur(): void {
    if (!focused) return;
    focused = false;
    active = null;
    hideTip();
}

function onKey(event: KeyboardEvent): void {
    if (props.compact) return;
    const n = props.days.length;
    if (!n) return;
    const cur = active ?? n - 1;
    let next: number | null;
    if (event.key === 'ArrowLeft') next = Math.max(0, cur - 1);
    else if (event.key === 'ArrowRight') next = Math.min(n - 1, cur + 1);
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = n - 1;
    else if (event.key === 'Escape') next = null;
    else return;
    event.preventDefault();
    active = next;
    if (next === null) hideTip();
    else showTip(next);
}
</script>

<style scoped>
.stats-trend {
    position: relative;
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 0.5rem;
}
.stats-trend__frame {
    position: relative;
    min-width: 0;
    border-radius: 0.5rem;
}
.stats-trend__frame--focusable:focus-visible {
    outline: 2px solid var(--p-primary-color);
    outline-offset: 2px;
}
.stats-trend__box {
    width: 100%;
}
.stats-trend__probe {
    position: absolute;
    width: 0;
    height: 0;
    overflow: hidden;
    visibility: hidden;
    pointer-events: none;
}
.stats-trend__legend {
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
.stats-trend__legend li {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
}
.stats-trend__swatch {
    display: inline-block;
    width: 0.625rem;
    height: 0.625rem;
    border-radius: 2px;
}
.stats-trend__swatch--bar {
    background: var(--p-primary-color);
    opacity: 0.85;
}
.stats-trend__swatch--paused {
    background: color-mix(in srgb, var(--p-amber-400) 40%, transparent);
}
.stats-trend__swatch--taken-down {
    background: color-mix(in srgb, var(--p-red-400) 35%, transparent);
}
.stats-trend__swatch--today {
    background: repeating-linear-gradient(-45deg, var(--p-primary-color) 0 2px, transparent 2px 4px);
    opacity: 0.6;
}
.stats-trend__legend-line {
    stroke: var(--p-text-color);
    stroke-width: 1.75;
}
.stats-trend__legend-dot {
    fill: var(--p-text-color);
}
.stats-trend__sr {
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
