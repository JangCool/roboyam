<template>
    <!--
        광고 성과 날짜별 막대 차트(SVG — 차트 라이브러리 없음). 신청 건 상세 「성과」 칸(큰 모양)과 사업자 센터 「진행 중 광고」 줄의 최근 7일 미니 막대(`compact`)가 같이 쓴다.
        한 번에 숫자 하나(노출 또는 클릭 — 축 하나, 둘의 크기가 백 배쯤 달라 두 축을 겹치지 않는다)를 막대로 그린다. 그날 일부라도 일시 정지가 걸쳤으면 주황 띠, 내림·중단이면 빨강 띠를 막대 뒤에 깐다.
        오늘 막대는 빗금·흐리게(아직 집계 중). 큰 모양은 막대 위에 올리거나(마우스·터치) 차트에 초점을 두고 왼쪽·오른쪽 화살표로 그날 숫자 툴팁을 본다.
        스크린 리더는 `role="img"` 의 요약(aria-label)과 숨긴 표를 읽는다. 폭은 감싼 칸의 실제 폭을 재서 그린다(360px 에서도 글자가 줄지 않게).
    -->
    <div
        ref="wrap"
        class="relative w-full"
        :class="compact ? '' : 'rounded-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'"
        :style="{ height: `${height}px` }"
        :tabindex="compact ? undefined : 0"
        :role="compact ? undefined : 'group'"
        :aria-label="compact ? undefined : t('business.stats.chartKeyboard')"
        data-testid="business-stats-chart"
        @keydown="onKey"
        @focus="onFocus"
        @blur="active = null"
    >
        <svg v-if="width > 0" :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`" role="img" :aria-label="summary" class="block overflow-visible" @pointermove="onPointer" @pointerdown="onPointer" @pointerleave="active = null">
            <defs>
                <pattern :id="hatchId" patternUnits="userSpaceOnUse" width="5" height="5" patternTransform="rotate(45)">
                    <rect width="5" height="5" class="bar-fill" opacity="0.28" />
                    <line x1="1" y1="0" x2="1" y2="5" class="bar-stroke" stroke-width="2" />
                </pattern>
            </defs>

            <!-- 정지·내림 띠 -->
            <rect
                v-for="b in bands"
                :key="`band-${b.i}`"
                :x="b.x"
                :y="pad.top"
                :width="b.w"
                :height="plotH"
                :class="b.state === 'PAUSED' ? 'fill-amber-400/25 dark:fill-amber-400/20' : 'fill-red-500/18 dark:fill-red-400/20'"
            />

            <!-- 눈금(큰 모양) -->
            <g v-if="!compact" aria-hidden="true">
                <g v-for="tick in ticks" :key="`tick-${tick.v}`">
                    <line :x1="pad.left" :x2="width - pad.right" :y1="tick.y" :y2="tick.y" class="grid-line" :stroke-dasharray="tick.v === 0 ? undefined : '2 3'" />
                    <text :x="pad.left - 6" :y="tick.y" text-anchor="end" dominant-baseline="middle" class="axis-text">{{ tick.label }}</text>
                </g>
                <text v-for="l in xLabels" :key="`x-${l.i}`" :x="l.x" :y="height - 6" :text-anchor="l.anchor" class="axis-text">{{ l.label }}</text>
            </g>
            <line v-else :x1="0" :x2="width" :y1="pad.top + plotH + 0.5" :y2="pad.top + plotH + 0.5" class="grid-line" aria-hidden="true" />

            <!-- 고른 날 -->
            <rect v-if="active !== null && !compact" :x="pad.left + band * active" :y="pad.top" :width="band" :height="plotH" class="fill-surface-500/10" aria-hidden="true" />

            <!-- 막대 -->
            <path
                v-for="bar in bars"
                :key="`bar-${bar.i}`"
                :d="bar.d"
                :fill="bar.today ? `url(#${hatchId})` : undefined"
                :class="bar.today ? 'bar-stroke-only' : 'bar-fill'"
                :opacity="bar.today ? 0.85 : active !== null && active !== bar.i && !compact ? 0.55 : 1"
            />
        </svg>

        <!-- 툴팁 -->
        <div
            v-if="!compact && tip"
            class="pointer-events-none absolute top-0 z-10 min-w-36 -translate-x-1/2 rounded-md border border-hairline bg-card px-2.5 py-2 text-xs shadow-md"
            :style="{ left: `${tip.left}px` }"
            role="status"
            aria-live="polite"
            data-testid="business-stats-tooltip"
        >
            <p class="m-0 font-semibold tabular-nums">{{ tip.date }}<span v-if="tip.today" class="ml-1 font-normal text-muted-color">· {{ t('business.stats.todayCounting') }}</span></p>
            <p class="m-0 mt-1 flex justify-between gap-3 tabular-nums"><span class="text-muted-color">{{ t('business.stats.impressions') }}</span>{{ tip.impressions }}</p>
            <p class="m-0 flex justify-between gap-3 tabular-nums"><span class="text-muted-color">{{ t('business.stats.clicks') }}</span>{{ tip.clicks }}</p>
            <p class="m-0 flex justify-between gap-3 tabular-nums"><span class="text-muted-color">{{ t('business.stats.ctr') }}</span>{{ tip.ctr }}</p>
            <p v-if="tip.state !== 'SHOWING'" class="m-0 mt-1 flex items-center gap-1" :class="tip.state === 'PAUSED' ? 'text-amber-700 dark:text-amber-300' : 'text-red-600 dark:text-red-400'">
                <i :class="tip.state === 'PAUSED' ? 'pi pi-pause-circle' : 'pi pi-ban'" class="text-[0.6875rem]" aria-hidden="true" />{{ t(`business.stats.dayState.${tip.state}`) }}
            </p>
        </div>

        <!-- 스크린 리더용 표 -->
        <table v-if="!compact" class="sr-only">
            <caption>{{ summary }}</caption>
            <thead>
                <tr>
                    <th scope="col">{{ t('business.stats.date') }}</th>
                    <th scope="col">{{ t('business.stats.impressions') }}</th>
                    <th scope="col">{{ t('business.stats.clicks') }}</th>
                    <th scope="col">{{ t('business.stats.ctr') }}</th>
                    <th scope="col">{{ t('business.stats.state') }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="d in days" :key="d.date">
                    <th scope="row">{{ time.serviceDate(d.date) }}<template v-if="d.date === today"> ({{ t('business.stats.todayCounting') }})</template></th>
                    <td>{{ UtilAdStats.count(d.impressions) }}</td>
                    <td>{{ UtilAdStats.count(d.clicks) }}</td>
                    <td>{{ UtilAdStats.ctrText(d) }}</td>
                    <td>{{ t(`business.stats.dayState.${d.state}`) }}</td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import type { AdStatDay } from '~/composables/business/api/useBusinessApi.types';
import UtilAdStats from '~/utils/util-ad-stats';

/** 막대로 그릴 숫자 */
type StatsMetric = 'impressions' | 'clicks';

interface Props {
    /** 날짜별 숫자(오래된 차례, 기간 안 모든 날) */
    days: AdStatDay[];
    metric?: StatsMetric;
    /** 오늘(한국 날짜) — 이 날 막대는 빗금·흐리게. 기간 밖이면 빈 글자 */
    today?: string;
    /** 미니 막대(사업자 센터 줄) — 눈금·툴팁·초점 없음 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { metric: 'impressions', today: '', compact: false });

const { t, locale } = useI18n();
const time = useDisplayTime();
const display = useDisplayValues();
const hatchId = `stats-hatch-${useId().replace(/[^A-Za-z0-9_-]/g, '')}`;

/* ── 폭 재기 ── */
const wrap = ref<HTMLElement | null>(null);
const width = ref(0);
let observer: ResizeObserver | null = null;
onMounted(() => {
    if (!wrap.value) return;
    width.value = wrap.value.clientWidth;
    observer = new ResizeObserver((entries) => {
        const w = entries[0]?.contentRect.width ?? 0;
        if (Math.abs(w - width.value) >= 1) width.value = Math.floor(w);
    });
    observer.observe(wrap.value);
});
onBeforeUnmount(() => observer?.disconnect());

/* ── 기하 ── */
const height = computed(() => (props.compact ? 36 : 200));
const compactFmt = computed(() => new Intl.NumberFormat(locale.value, { notation: 'compact', maximumFractionDigits: 1 }));
const scale = computed(() => UtilAdStats.niceScale(Math.max(0, ...props.days.map((d) => d[props.metric]))));
/** 왼쪽 여백 = 가장 긴 눈금 글자 폭(어림 6.5px/글자) */
const pad = computed(() => {
    if (props.compact) return { top: 2, right: 0, bottom: 1, left: 0 };
    const longest = Math.max(...[0, scale.value.max].map((v) => compactFmt.value.format(v).length));
    return { top: 10, right: 4, bottom: 22, left: Math.max(28, longest * 6.5 + 10) };
});
const plotW = computed(() => Math.max(0, width.value - pad.value.left - pad.value.right));
const plotH = computed(() => height.value - pad.value.top - pad.value.bottom);
const band = computed(() => (props.days.length ? plotW.value / props.days.length : 0));
const barW = computed(() => Math.max(2, Math.min(props.compact ? 14 : 28, band.value * (props.compact ? 0.62 : 0.66))));
const yOf = (v: number) => pad.value.top + plotH.value - (scale.value.max > 0 ? (v / scale.value.max) * plotH.value : 0);
const cxOf = (i: number) => pad.value.left + band.value * (i + 0.5);

/** 위 모서리만 둥근 막대(반지름 4, 바닥은 축에 붙는다) */
const bars = computed(() =>
    props.days.flatMap((d, i) => {
        const v = d[props.metric];
        if (v <= 0) return [];
        const x0 = cxOf(i) - barW.value / 2;
        const x1 = x0 + barW.value;
        const yb = pad.value.top + plotH.value;
        const yt = Math.min(yb - 1, yOf(v));
        const r = Math.min(4, barW.value / 2, yb - yt);
        const d0 = `M${x0},${yb}L${x0},${yt + r}Q${x0},${yt} ${x0 + r},${yt}L${x1 - r},${yt}Q${x1},${yt} ${x1},${yt + r}L${x1},${yb}Z`;
        return [{ i, d: d0, today: d.date === props.today }];
    }),
);
const bands = computed(() =>
    props.days.flatMap((d, i) => (d.state === 'SHOWING' ? [] : [{ i, state: d.state, x: pad.value.left + band.value * i, w: band.value }])),
);
const ticks = computed(() => {
    const out: { v: number; y: number; label: string }[] = [];
    for (let v = 0; v <= scale.value.max; v += scale.value.step) out.push({ v, y: yOf(v), label: compactFmt.value.format(v) });
    return out;
});
/** 아래 날짜 — 글자가 겹치지 않게 몇 칸마다(오늘·마지막 날 기준으로 거꾸로 센다) */
const xLabels = computed(() => {
    const n = props.days.length;
    if (!n || band.value <= 0) return [];
    const every = Math.max(1, Math.ceil(44 / band.value));
    return props.days.flatMap((d, i) => {
        if ((n - 1 - i) % every !== 0) return [];
        const x = cxOf(i);
        const anchor: 'start' | 'middle' | 'end' = x - 22 < pad.value.left - 8 ? 'start' : x + 22 > width.value ? 'end' : 'middle';
        return [{ i, x: anchor === 'start' ? Math.max(x - barW.value / 2, 0) : anchor === 'end' ? width.value - 1 : x, anchor, label: UtilAdStats.shortDay(d.date, display.value) }];
    });
});

/* ── 요약(aria-label) ── */
const summary = computed(() => {
    const days = props.days;
    if (!days.length) return t('business.stats.empty');
    const total = days.reduce((s, d) => s + d[props.metric], 0);
    const peak = days.reduce((best, d) => (d[props.metric] > best[props.metric] ? d : best), days[0]!);
    if (props.compact) return t('business.stats.sparkSummary', { total: UtilAdStats.count(total), peak: UtilAdStats.count(peak[props.metric]) });
    return t('business.stats.chartSummary', {
        metric: t(`business.stats.${props.metric}`),
        from: time.value.serviceDate(days[0]!.date),
        to: time.value.serviceDate(days[days.length - 1]!.date),
        total: UtilAdStats.count(total),
        peakDate: time.value.serviceDate(peak.date),
        peak: UtilAdStats.count(peak[props.metric]),
    });
});

/* ── 고르기(마우스·터치·화살표) ── */
const active = ref<number | null>(null);

function onPointer(e: PointerEvent): void {
    if (props.compact || band.value <= 0) return;
    const rect = (e.currentTarget as SVGSVGElement).getBoundingClientRect();
    const i = Math.floor((e.clientX - rect.left - pad.value.left) / band.value);
    active.value = i >= 0 && i < props.days.length ? i : null;
}

function onFocus(): void {
    if (!props.compact && props.days.length) active.value = props.days.length - 1;
}

function onKey(e: KeyboardEvent): void {
    if (props.compact || !props.days.length) return;
    const last = props.days.length - 1;
    const cur = active.value ?? last;
    const next = e.key === 'ArrowLeft' ? cur - 1 : e.key === 'ArrowRight' ? cur + 1 : e.key === 'Home' ? 0 : e.key === 'End' ? last : e.key === 'Escape' ? null : undefined;
    if (next === undefined) return;
    e.preventDefault();
    active.value = next === null ? null : Math.min(last, Math.max(0, next));
}

const tip = computed(() => {
    if (active.value === null) return null;
    const d = props.days[active.value];
    if (!d) return null;
    const half = 76;
    return {
        left: Math.min(Math.max(cxOf(active.value), half), Math.max(half, width.value - half)),
        date: time.value.serviceDate(d.date),
        today: d.date === props.today,
        impressions: UtilAdStats.count(d.impressions),
        clicks: UtilAdStats.count(d.clicks),
        ctr: UtilAdStats.ctrText(d),
        state: d.state,
    };
});
</script>

<style scoped>
.bar-fill {
    fill: var(--p-primary-color);
}
.bar-stroke {
    stroke: var(--p-primary-color);
}
.bar-stroke-only {
    stroke: var(--p-primary-color);
    stroke-width: 1;
}
.grid-line {
    stroke: var(--hairline-strong);
    stroke-width: 1;
}
.axis-text {
    fill: var(--p-text-muted-color);
    font-size: 11px;
    font-variant-numeric: tabular-nums;
}
</style>
