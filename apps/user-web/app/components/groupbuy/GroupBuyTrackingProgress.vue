<template>
    <!--
        내 주문 상세 「배송」의 배송 진행(계약 결정 90) — 스마트택배 단계를 세 칸(집화 → 배송중 → 배송완료)으로 묶어 보인다:
        2 집화완료 → 집화 · 3 배송중·4 지점 도착·5 배송출발 → 배송중 · 6 배송 완료(또는 배송완료 처리됨) → 배송완료. 0·1·조회 전은 「택배사 조회 전」.
        「배송출발」 칸은 두지 않는다(사용자 2026-11-25 — 「배송중 다음에 배송완료」). 배송출발은 이력 줄에 그대로 보인다.
        아래에 시각·위치 이력(최신 위, 많으면 접기)과 「마지막 조회 ○○」. 서버는 부르지 않는다(받은 주문 상세만 그린다).
    -->
    <div class="flex flex-col gap-3">
        <ol class="m-0 grid list-none grid-cols-3 gap-1 p-0" :aria-label="t('groupBuy.trkProgressLabel')">
            <li v-for="(step, i) in STEPS" :key="step" class="flex min-w-0 flex-col items-center gap-1 text-center" :aria-current="i === current ? 'step' : undefined">
                <div class="flex w-full items-center">
                    <span class="h-0.5 flex-1" :class="i === 0 ? 'bg-transparent' : i <= current ? 'bg-primary' : 'bg-surface-200 dark:bg-surface-700'" aria-hidden="true" />
                    <span class="flex size-6 shrink-0 items-center justify-center rounded-full text-[11px]" :class="i < current || (i === current && done) ? 'bg-primary text-primary-contrast' : i === current ? 'border-2 border-primary bg-(--p-content-background) text-primary' : 'border border-surface bg-(--p-content-background) text-muted-color'" aria-hidden="true">
                        <i v-if="i < current || (i === current && done)" class="pi pi-check text-[10px]" />
                        <span v-else>{{ i + 1 }}</span>
                    </span>
                    <span class="h-0.5 flex-1" :class="i === STEPS.length - 1 ? 'bg-transparent' : i < current ? 'bg-primary' : 'bg-surface-200 dark:bg-surface-700'" aria-hidden="true" />
                </div>
                <span class="text-xs [overflow-wrap:anywhere]" :class="i === current ? 'font-semibold' : i < current ? '' : 'text-muted-color'">
                    {{ t(`groupBuy.trkStep.${step}`) }}<span v-if="i === current" class="sr-only"> {{ t('groupBuy.trkCurrent') }}</span>
                </span>
            </li>
        </ol>

        <p v-if="current < 0" class="m-0 text-sm text-muted-color">{{ t('groupBuy.trkBefore') }}</p>

        <!-- 이력 — 최신 위 -->
        <ol v-if="events.length" class="m-0 flex list-none flex-col p-0" :aria-label="t('groupBuy.trkHistory')">
            <li v-for="(e, i) in shownEvents" :key="i" class="relative flex gap-2.5 pb-2.5 text-sm last:pb-0">
                <span v-if="i < shownEvents.length - 1" class="absolute left-[4px] top-4 bottom-0 w-px bg-surface-200 dark:bg-surface-700" aria-hidden="true" />
                <span class="mt-1.5 size-[9px] shrink-0 rounded-full" :class="i === 0 ? 'bg-primary' : 'bg-surface-400'" aria-hidden="true" />
                <div class="flex min-w-0 flex-1 flex-col gap-0.5 sm:flex-row sm:items-baseline sm:gap-2">
                    <span class="min-w-0 [overflow-wrap:anywhere]" :class="i === 0 ? 'font-medium' : ''">{{ e.kind }}<template v-if="e.where"> <span class="text-muted-color">· {{ e.where }}</span></template></span>
                    <span class="shrink-0 text-xs tabular-nums text-muted-color sm:ml-auto">{{ time.shortDateTime(e.time) }}</span>
                </div>
            </li>
        </ol>
        <div v-if="events.length > FOLD">
            <Button type="button" :label="expanded ? t('groupBuy.trkFold') : t('groupBuy.trkShowAll', { count: events.length })" :icon="expanded ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" size="small" severity="secondary" variant="text" @click="expanded = !expanded" />
        </div>

        <p v-if="lastCheckedAt" class="m-0 text-xs text-muted-color">{{ t('groupBuy.trkLastChecked', { at: time.shortDateTime(lastCheckedAt) }) }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MyGroupBuyTrackingEvent } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';

interface Props {
    /** 스마트택배 단계(0~6, 조회 전 null) */
    level?: number | null;
    complete?: boolean;
    /** 배송완료 처리 시각(운영자 처리 포함) — 있으면 마지막 칸까지 끝난 것으로 */
    deliveredAt?: string | null;
    events?: MyGroupBuyTrackingEvent[];
    lastCheckedAt?: string | null;
}

const props = withDefaults(defineProps<Props>(), { level: null, complete: false, deliveredAt: null, events: () => [], lastCheckedAt: null });

/** 세 칸 — 키 끝마디 */
const STEPS = ['PICKED_UP', 'IN_TRANSIT', 'DELIVERED'] as const;
/** 접었을 때 보이는 이력 수 */
const FOLD = 3;

const { t } = useI18n();
const time = useDisplayTime();
const expanded = ref(false);

/** 배송 완료(택배사 완료 또는 배송완료 처리) */
const done = computed(() => !!props.deliveredAt || props.complete || props.level === 6);

/** 지금 칸(-1 = 택배사 조회 전) */
const current = computed(() => {
    if (done.value) return STEPS.length - 1;
    const level = props.level;
    if (level === 3 || level === 4 || level === 5) return 1;
    if (level === 2) return 0;
    return -1;
});

/** 최신 위 — 서버 차례와 상관없이 시각으로 */
const events = computed(() => [...props.events].sort((a, b) => Date.parse(b.time) - Date.parse(a.time)));
const shownEvents = computed(() => (expanded.value ? events.value : events.value.slice(0, FOLD)));
</script>
