<template>
    <!--
        광고 성과 날짜별 추이 — 새 의존성 없는 SVG(저장소에 차트 라이브러리가 없다. PrimeVue Chart 는 chart.js 가 있어야 해서 쓰지 않는다).
        막대 = 노출(왼쪽 축, 주 색상) · 선 = 클릭(오른쪽 축, 글자 색 — 주 색상을 무엇으로 바꿔도 막대와 갈린다). 축이 둘이라 노출과 클릭을 한눈에 비교한다.
        그날 상태가 정지면 주황 띠, 내림·중단이면 빨강 띠를 막대 뒤에 깐다. 오늘 막대는 빗금·흐리게(「집계 중」).
        마우스를 올리거나, 차트에 초점을 두고 왼쪽·오른쪽(Home·End) 화살표로 날짜를 옮기면 그날 숫자 툴팁이 뜬다(Esc 로 닫음).
        스크린 리더: SVG 는 `role="img"` + 요약 `aria-label`, 날짜별 숫자는 숨긴 표(`sr-only`). 툴팁은 보이는 사람용이라 `aria-hidden`.
        폭은 부모를 따른다(ResizeObserver) — 날짜가 많으면 x 축 글자를 건너뛴다. 빈 숫자(모두 0)를 그릴지는 부르는 쪽이 정한다.
    -->
    <div ref="box" class="relative flex min-w-0 flex-col gap-2">
        <div
            class="relative rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            tabindex="0"
            role="group"
            :aria-label="t('ad.chartKeys')"
            @keydown="onKey"
            @focus="onFocus"
            @blur="active = null"
            @pointerleave="onLeave"
        >
            <svg :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`" role="img" :aria-label="summary" class="block overflow-visible">
                <defs>
                    <pattern :id="hatchId" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                        <rect width="6" height="6" fill="var(--p-primary-color)" opacity="0.18" />
                        <rect width="2.5" height="6" fill="var(--p-primary-color)" opacity="0.55" />
                    </pattern>
                </defs>

                <!-- 정지·내림 띠 -->
                <rect
                    v-for="b in bandRects"
                    :key="`${b.state}-${b.x}`"
                    :x="b.x"
                    :y="PAD.top"
                    :width="b.w"
                    :height="plotH"
                    :fill="b.state === 'PAUSED' ? 'var(--p-amber-400)' : 'var(--p-red-400)'"
                    :opacity="b.state === 'PAUSED' ? 0.2 : 0.16"
                />

                <!-- 눈금(왼쪽 노출 · 오른쪽 클릭) -->
                <g class="text-[10px]" fill="var(--p-text-muted-color)">
                    <template v-for="tick in ticks" :key="tick.k">
                        <line :x1="PAD.left" :x2="width - PAD.right" :y1="tick.y" :y2="tick.y" stroke="var(--line)" :stroke-dasharray="tick.k === 0 ? undefined : '2 3'" />
                        <text :x="PAD.left - 6" :y="tick.y" text-anchor="end" dominant-baseline="middle">{{ tick.left }}</text>
                        <text :x="width - PAD.right + 6" :y="tick.y" text-anchor="start" dominant-baseline="middle">{{ tick.right }}</text>
                    </template>
                </g>

                <!-- 고른 날 -->
                <rect v-if="active !== null" :x="PAD.left + slot * active" :y="PAD.top" :width="slot" :height="plotH" fill="var(--p-content-hover-background)" opacity="0.7" />

                <!-- 막대 = 노출 -->
                <rect
                    v-for="(d, i) in days"
                    :key="`bar-${d.date}`"
                    :x="cx(i) - barW / 2"
                    :y="yLeft(d.impressions)"
                    :width="barW"
                    :height="Math.max(0, PAD.top + plotH - yLeft(d.impressions))"
                    rx="2"
                    :fill="d.date === todayYmd ? `url(#${hatchId})` : 'var(--p-primary-color)'"
                    :opacity="d.date === todayYmd ? 0.75 : active === null || active === i ? 0.85 : 0.5"
                />

                <!-- 선 = 클릭 -->
                <path v-if="days.length > 1" :d="linePath" fill="none" stroke="var(--p-text-color)" stroke-width="1.75" stroke-linejoin="round" stroke-linecap="round" />
                <circle
                    v-for="(d, i) in days"
                    :key="`dot-${d.date}`"
                    :cx="cx(i)"
                    :cy="yRight(d.clicks)"
                    :r="active === i ? 4 : 2.75"
                    :fill="d.date === todayYmd ? 'var(--p-content-background)' : 'var(--p-text-color)'"
                    stroke="var(--p-text-color)"
                    stroke-width="1.5"
                />

                <!-- x 축 날짜 -->
                <g class="text-[10px]" fill="var(--p-text-muted-color)">
                    <text v-for="(d, i) in days" v-show="i % labelStep === 0 || i === days.length - 1" :key="`x-${d.date}`" :x="cx(i)" :y="height - 6" text-anchor="middle" :font-weight="d.date === todayYmd ? 600 : undefined">
                        {{ UtilAdStats.shortDay(d.date) }}
                    </text>
                </g>

                <!-- 마우스 받는 칸 -->
                <rect v-for="(d, i) in days" :key="`hit-${d.date}`" :x="PAD.left + slot * i" :y="0" :width="slot" :height="height" fill="transparent" @pointerenter="active = i" />
            </svg>

            <!-- 툴팁 -->
            <div
                v-if="activeDay"
                class="pointer-events-none absolute top-1 z-10 flex min-w-36 flex-col gap-0.5 rounded-lg border border-(--line) bg-(--p-content-background) px-2.5 py-2 text-[12px] shadow-md"
                :style="tipStyle"
                aria-hidden="true"
            >
                <span class="font-semibold">
                    {{ time.serviceDate(activeDay.date) }}
                    <span v-if="activeDay.date === todayYmd" class="font-normal text-muted-color">· {{ t('ad.chartToday') }}</span>
                </span>
                <span class="flex justify-between gap-3 tabular-nums"><span class="text-muted-color">{{ t('ad.statImpressions') }}</span>{{ UtilAdStats.num(activeDay.impressions, locale) }}</span>
                <span class="flex justify-between gap-3 tabular-nums"><span class="text-muted-color">{{ t('ad.statClicks') }}</span>{{ UtilAdStats.num(activeDay.clicks, locale) }}</span>
                <span class="flex justify-between gap-3 tabular-nums"><span class="text-muted-color">{{ t('ad.statCtr') }}</span>{{ UtilAdStats.ctrText(activeDay, locale) }}</span>
                <span v-if="activeDay.state && activeDay.state !== 'SHOWING'" :class="activeDay.state === 'PAUSED' ? 'text-amber-700 dark:text-amber-400' : 'text-red-600 dark:text-red-400'">{{ t(`ad.chartState.${activeDay.state}`) }}</span>
            </div>
        </div>

        <!-- 범례 -->
        <ul class="m-0 flex list-none flex-wrap items-center gap-x-4 gap-y-1 p-0 text-[12px] text-muted-color" :aria-label="t('ad.chartLegend')">
            <li class="inline-flex items-center gap-1.5"><span class="inline-block size-2.5 rounded-sm bg-(--p-primary-color) opacity-85" aria-hidden="true" />{{ t('ad.chartLegendImpressions') }}</li>
            <li class="inline-flex items-center gap-1.5">
                <svg width="18" height="10" aria-hidden="true"><line x1="1" y1="5" x2="17" y2="5" stroke="var(--p-text-color)" stroke-width="1.75" /><circle cx="9" cy="5" r="2.5" fill="var(--p-text-color)" /></svg>{{ t('ad.chartLegendClicks') }}
            </li>
            <li v-if="hasState.PAUSED" class="inline-flex items-center gap-1.5"><span class="inline-block size-2.5 rounded-sm bg-amber-400/40" aria-hidden="true" />{{ t('ad.chartLegendPaused') }}</li>
            <li v-if="hasState.TAKEN_DOWN" class="inline-flex items-center gap-1.5"><span class="inline-block size-2.5 rounded-sm bg-red-400/35" aria-hidden="true" />{{ t('ad.chartLegendTakenDown') }}</li>
            <li v-if="hasToday" class="inline-flex items-center gap-1.5">
                <svg width="10" height="10" aria-hidden="true"><rect width="10" height="10" rx="2" :fill="`url(#${hatchId})`" /></svg>{{ t('ad.chartLegendToday') }}
            </li>
        </ul>

        <!-- 스크린 리더용 표 -->
        <table class="sr-only">
            <caption>{{ t('ad.chartTableCaption') }}</caption>
            <thead>
                <tr>
                    <th scope="col">{{ t('ad.chartDate') }}</th>
                    <th scope="col">{{ t('ad.statImpressions') }}</th>
                    <th scope="col">{{ t('ad.statClicks') }}</th>
                    <th scope="col">{{ t('ad.statCtr') }}</th>
                    <th v-if="hasAnyState" scope="col">{{ t('ad.chartStateLabel') }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="d in days" :key="`sr-${d.date}`">
                    <th scope="row">{{ time.serviceDate(d.date) }}<template v-if="d.date === todayYmd"> ({{ t('ad.chartToday') }})</template></th>
                    <td>{{ UtilAdStats.num(d.impressions, locale) }}</td>
                    <td>{{ UtilAdStats.num(d.clicks, locale) }}</td>
                    <td>{{ UtilAdStats.ctrText(d, locale) }}</td>
                    <td v-if="hasAnyState">{{ t(`ad.chartState.${d.state ?? 'SHOWING'}`) }}</td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import type { AdStatCounts, AdStatDayState } from '~/composables/ad/api/useAdApi.types';
import UtilAdMessages from '~/utils/util-ad-messages';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBizApplication from '~/utils/util-biz-application';

/** 차트 날짜 하나 — 한 건 성과는 `state` 가 있고(띠), 운영자 합계는 없다 */
type ChartDay = AdStatCounts & { date: string; state?: AdStatDayState };

interface Props {
    /** 오래된 차례, 빈 날 없이 */
    days: ChartDay[];
    /** 오늘(`YYYY-MM-DD` KST) — 비우면 지금 시각으로 계산 */
    today?: string;
    height?: number;
}

const props = withDefaults(defineProps<Props>(), { today: '', height: 220 });

const { t, locale } = useI18n();
const time = useDisplayTime();
const hatchId = useId();

const PAD = { top: 10, right: 44, bottom: 22, left: 48 } as const;

const box = ref<HTMLElement | null>(null);
const width = ref(640);
let observer: ResizeObserver | null = null;
onMounted(() => {
    if (!box.value) return;
    width.value = Math.max(280, Math.floor(box.value.clientWidth));
    observer = new ResizeObserver(([entry]) => {
        if (entry) width.value = Math.max(280, Math.floor(entry.contentRect.width));
    });
    observer.observe(box.value);
});
onBeforeUnmount(() => observer?.disconnect());

const todayYmd = computed(() => props.today || UtilBizApplication.kstToday());
const plotW = computed(() => Math.max(1, width.value - PAD.left - PAD.right));
const plotH = computed(() => Math.max(1, props.height - PAD.top - PAD.bottom));
const slot = computed(() => plotW.value / Math.max(1, props.days.length));
const barW = computed(() => Math.max(2, Math.min(28, slot.value * 0.62)));
const leftMax = computed(() => UtilAdStats.niceMax(Math.max(0, ...props.days.map((d) => d.impressions))));
const rightMax = computed(() => UtilAdStats.niceMax(Math.max(0, ...props.days.map((d) => d.clicks))));
/** x 축 글자 간격 — 글자 하나에 44px 쯤 */
const labelStep = computed(() => Math.max(1, Math.ceil(props.days.length / Math.max(1, Math.floor(plotW.value / 44)))));

const cx = (i: number) => PAD.left + slot.value * (i + 0.5);
const yLeft = (v: number) => PAD.top + plotH.value * (1 - v / leftMax.value);
const yRight = (v: number) => PAD.top + plotH.value * (1 - v / rightMax.value);

const ticks = computed(() =>
    [0, 1, 2, 3, 4].map((k) => ({
        k,
        y: PAD.top + plotH.value * (1 - k / 4),
        left: UtilAdStats.compact((leftMax.value * k) / 4, locale.value),
        right: UtilAdStats.compact((rightMax.value * k) / 4, locale.value),
    })),
);

const linePath = computed(() => props.days.map((d, i) => `${i ? 'L' : 'M'}${cx(i).toFixed(1)},${yRight(d.clicks).toFixed(1)}`).join(' '));

const bands = computed(() => UtilAdStats.bands(props.days));
const bandRects = computed(() => bands.value.map((b) => ({ state: b.state, x: PAD.left + slot.value * b.start, w: slot.value * (b.end - b.start + 1) })));
const hasState = computed(() => ({ PAUSED: bands.value.some((b) => b.state === 'PAUSED'), TAKEN_DOWN: bands.value.some((b) => b.state === 'TAKEN_DOWN') }));
const hasAnyState = computed(() => props.days.some((d) => d.state !== undefined));
const hasToday = computed(() => props.days.some((d) => d.date === todayYmd.value));

const summary = computed(() => UtilAdMessages.chartSummary(t, props.days, locale.value, (ymd) => time.value.serviceDate(ymd)));

// ── 고른 날(툴팁) ──
const active = ref<number | null>(null);
const activeDay = computed(() => (active.value === null ? null : (props.days[active.value] ?? null)));
const tipStyle = computed(() => {
    if (active.value === null) return {};
    const tipW = 152;
    const x = cx(active.value);
    const left = Math.min(Math.max(0, x - tipW / 2), Math.max(0, width.value - tipW));
    return { left: `${left}px`, width: `${tipW}px` };
});
watch(
    () => props.days.length,
    () => (active.value = null),
);

let focused = false;
function onFocus(): void {
    focused = true;
    if (active.value === null && props.days.length) active.value = props.days.length - 1;
}
function onLeave(): void {
    if (!focused) active.value = null;
}
function onKey(event: KeyboardEvent): void {
    const n = props.days.length;
    if (!n) return;
    const cur = active.value ?? n - 1;
    let next: number | null = cur;
    if (event.key === 'ArrowLeft') next = Math.max(0, cur - 1);
    else if (event.key === 'ArrowRight') next = Math.min(n - 1, cur + 1);
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = n - 1;
    else if (event.key === 'Escape') next = null;
    else return;
    event.preventDefault();
    active.value = next;
}
watch(active, (v) => {
    if (v === null) focused = false;
});
</script>
