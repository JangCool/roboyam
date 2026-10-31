<template>
    <!--
        광고 일정표 달력 — 한 달을 크게(하모니카3 일정 화면의 월 보기와 같은 짜임). 02-ad.md 7-1 ③.
        · 칸마다 「남은 칸 n/한도」 알약(초록 여유 · 주황 거의 · 빨강 꽉 참)과 그날 걸친 광고 건 이름(상태 색 점)을 보인다.
        · 광고 건을 누르면 `open`(상세 서랍), 칸의 빈 곳을 누르면 `pick`(그 날짜로 수동 등록 시작 — 부모가 권한을 본다).
        · 좁은 화면은 칸이 작아 이름 대신 남은 칸 숫자와 점만 보인다.
        서버가 저장 때 다시 판정하므로 남은 칸은 안내다. 날짜 계산은 서비스 시간대(KST) 기준.
    -->
    <div class="flex min-w-0 flex-col gap-2">
        <div class="flex flex-wrap items-center gap-2">
            <Button v-tooltip.top="t('ad.prevMonth')" icon="pi pi-chevron-left" text size="small" severity="secondary" :aria-label="t('ad.prevMonth')" @click="move(-1)" />
            <Button v-tooltip.top="t('ad.nextMonth')" icon="pi pi-chevron-right" text size="small" severity="secondary" :aria-label="t('ad.nextMonth')" @click="move(1)" />
            <Button :label="t('ad.today')" size="small" severity="secondary" outlined @click="goToday" />
            <span class="ml-1 text-[15px] font-semibold">{{ title }}</span>
            <i v-if="loading" class="pi pi-spin pi-spinner text-[12px] text-muted-color" aria-hidden="true" />
            <span class="flex-1" />
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-muted-color">
                <span class="inline-flex items-center gap-1.5">
                    {{ t('ad.legendSlots') }}
                    <span class="rounded-full bg-emerald-100 px-1.5 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">{{ t('ad.legendFree') }}</span>
                    <span class="rounded-full bg-amber-100 px-1.5 text-amber-800 dark:bg-amber-950 dark:text-amber-300">{{ t('ad.legendTight') }}</span>
                    <span class="rounded-full bg-red-100 px-1.5 text-red-700 dark:bg-red-950 dark:text-red-300">{{ t('ad.legendFull') }}</span>
                </span>
                <span class="inline-flex flex-wrap items-center gap-2">
                    <span v-for="s in LEGEND_STATUSES" :key="s" class="inline-flex items-center gap-1"><span class="size-2 rounded-full" :class="dotClass(s)" aria-hidden="true" />{{ t(`ad.status.${s}`) }}</span>
                </span>
            </div>
        </div>

        <div class="min-w-0 overflow-hidden rounded-lg border border-(--line)">
            <div class="grid grid-cols-7 border-b border-(--line) bg-(--p-content-hover-background)">
                <span v-for="(w, i) in weekdays" :key="i" class="py-1.5 text-center text-[11.5px] font-medium" :class="i === 0 ? 'text-red-500' : i === 6 ? 'text-blue-500' : 'text-muted-color'">{{ w }}</span>
            </div>
            <div class="grid grid-cols-7">
                <div
                    v-for="(d, i) in cells"
                    :key="d.ymd"
                    class="flex min-w-0 flex-col overflow-hidden border-(--line) p-1"
                    :class="[
                        i % 7 !== 6 ? 'border-r' : '',
                        i < cells.length - 7 ? 'border-b' : '',
                        d.inMonth ? '' : 'bg-(--p-content-hover-background)/60',
                        canPick && !d.past ? 'cursor-pointer hover:bg-(--p-content-hover-background)' : '',
                    ]"
                    :style="{ minHeight: compact ? '56px' : '112px' }"
                    :title="canPick && !d.past ? t('ad.pickDayHint') : undefined"
                    @click="onPick(d)"
                >
                    <div class="mb-1 flex items-center gap-1">
                        <span
                            class="flex size-6 shrink-0 items-center justify-center rounded-full text-[12px] tabular-nums"
                            :class="[d.ymd === today ? 'bg-(--p-primary-color) font-semibold text-(--p-primary-contrast-color)' : d.inMonth ? '' : 'text-muted-color', d.past && d.ymd !== today ? 'opacity-60' : '']"
                        >{{ d.day }}</span>
                        <span class="flex-1" />
                        <span
                            v-if="d.slot"
                            class="rounded-full px-1.5 py-px text-[10.5px] font-semibold tabular-nums"
                            :class="pillClass(d.slot)"
                            :title="t('ad.dayTitle', { remaining: d.slot.remaining, limit: d.slot.limit })"
                        >{{ compact ? d.slot.remaining : `${d.slot.remaining}/${d.slot.limit}` }}</span>
                    </div>

                    <div v-if="compact" class="flex flex-wrap items-center justify-center gap-0.5">
                        <span v-for="b in d.bookings.slice(0, 4)" :key="b.id" class="size-1.5 rounded-full" :class="dotClass(b.status)" />
                    </div>
                    <div v-else class="flex min-h-0 flex-1 flex-col gap-0.5">
                        <button
                            v-for="b in d.bookings.slice(0, MAX)"
                            :key="b.id"
                            type="button"
                            class="flex min-w-0 items-center gap-1 rounded px-1 py-0.5 text-left text-[11.5px] hover:bg-(--p-highlight-background)"
                            :class="b.status === 'TAKEN_DOWN' ? 'line-through opacity-70' : b.status === 'ENDED' ? 'opacity-60' : ''"
                            :title="`${b.advertiser.name} · ${t(`ad.status.${b.status}`)}`"
                            @click.stop="emit('open', b.id)"
                        >
                            <span class="size-1.5 shrink-0 rounded-full" :class="dotClass(b.status)" aria-hidden="true" />
                            <span class="truncate">{{ b.advertiser.name }}</span>
                        </button>
                        <span v-if="d.bookings.length > MAX" class="px-1 text-[10.5px] text-muted-color">{{ t('ad.moreN', { n: d.bookings.length - MAX }) }}</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { AdBookingRow, AdBookingStatus, AdDaySlot } from '~/composables/ad/api/useAdApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    /** 보는 달 `YYYY-MM` */
    month: string;
    days: AdDaySlot[];
    /** 이 유형의 광고 건(취소 건 제외) — 칸에 이름으로 그린다 */
    bookings: AdBookingRow[];
    loading?: boolean;
    /** 좁은 화면 — 이름 대신 점 */
    compact?: boolean;
    /** 빈 곳을 눌러 수동 등록을 시작할 수 있나(권한) */
    canPick?: boolean;
}

interface Cell {
    ymd: string;
    day: number;
    inMonth: boolean;
    past: boolean;
    slot: AdDaySlot | undefined;
    bookings: AdBookingRow[];
}

const props = withDefaults(defineProps<Props>(), { loading: false, compact: false, canPick: false });
const emit = defineEmits<{ month: [month: string]; open: [id: string]; pick: [ymd: string] }>();

const { t, locale } = useI18n();
const MAX = 3;
const LEGEND_STATUSES: readonly AdBookingStatus[] = ['RUNNING', 'SCHEDULED', 'AWAITING_PAYMENT', 'SUBMITTED', 'TAKEN_DOWN', 'ENDED'];
const today = computed(() => UtilBizApplication.kstToday());

const weekdays = computed(() => {
    const fmt = new Intl.DateTimeFormat(locale.value, { weekday: 'short', timeZone: 'UTC' });
    // 2026-03-01 은 일요일
    return Array.from({ length: 7 }, (_, i) => fmt.format(new Date(Date.UTC(2026, 2, 1 + i))));
});

const title = computed(() => {
    const [y, m] = props.month.split('-').map(Number);
    return t('ad.monthTitle', { year: y, month: m });
});

const byDate = computed(() => new Map(props.days.map((d) => [d.date, d])));

/** 6주 칸 — 그 달 1일이 든 주의 일요일부터 */
const cells = computed<Cell[]>(() => {
    const [y, m] = props.month.split('-').map(Number);
    const firstDow = new Date(Date.UTC(y!, m! - 1, 1)).getUTCDay();
    const start = Date.UTC(y!, m! - 1, 1 - firstDow);
    return Array.from({ length: 42 }, (_, i) => {
        const at = new Date(start + i * 86_400_000);
        const ymd = at.toISOString().slice(0, 10);
        const from = Date.parse(UtilBizApplication.kstMidnight(ymd));
        const to = Date.parse(UtilBizApplication.kstMidnight(UtilBizApplication.addDays(ymd, 1)));
        return {
            ymd,
            day: at.getUTCDate(),
            inMonth: at.getUTCMonth() === m! - 1,
            past: ymd < today.value,
            slot: byDate.value.get(ymd),
            // 반열림 [시작, 끝) 이 그날 [00:00, 다음 날 00:00) 과 겹치면 그날에 걸친다
            bookings: props.bookings.filter((b) => Date.parse(b.period.startAt) < to && Date.parse(b.period.endAt) > from),
        };
    });
});

function pillClass(slot: AdDaySlot): string {
    if (slot.remaining <= 0) return 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300';
    if (slot.remaining === 1 || slot.remaining / slot.limit <= 0.25) return 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300';
    return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300';
}

function dotClass(status: AdBookingStatus): string {
    switch (status) {
        case 'RUNNING':
            return 'bg-emerald-500';
        case 'SCHEDULED':
            return 'bg-blue-500';
        case 'SUBMITTED':
            return 'bg-violet-500';
        case 'AWAITING_PAYMENT':
        case 'AWAITING_DEPOSIT':
            return 'bg-amber-500';
        case 'TAKEN_DOWN':
            return 'bg-red-500';
        default:
            return 'bg-slate-400';
    }
}

function move(delta: number): void {
    const [y, m] = props.month.split('-').map(Number);
    const at = new Date(Date.UTC(y!, m! - 1 + delta, 1));
    emit('month', at.toISOString().slice(0, 7));
}

function goToday(): void {
    const month = today.value.slice(0, 7);
    if (month !== props.month) emit('month', month);
}

function onPick(d: Cell): void {
    if (props.canPick && !d.past) emit('pick', d.ymd);
}
</script>
