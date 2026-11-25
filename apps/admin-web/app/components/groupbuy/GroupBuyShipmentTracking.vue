<template>
    <!--
        주문 서랍 「배송」 발송 줄 하나의 스마트택배 추적(계약 결정 90) — 현재 단계 · 마지막/다음 조회 · 오늘 조회 수/한도 · 실패 횟수·오류 · 자동 배송완료 보류 사유 · 이력(최신 위, 많으면 접기).
        서버는 부르지 않는다 — [지금 조회]는 주문 단위라 서랍 「배송」 머리에 있다.
    -->
    <div class="flex flex-col gap-1.5">
        <p v-if="!tracking.available" class="m-0 text-xs text-muted-color">{{ t('groupBuy.trkNotAvailable') }}</p>
        <template v-else>
            <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
                <Tag :value="levelText" :severity="levelSeverity" :icon="tracking.level === 6 ? 'pi pi-check-circle' : 'pi pi-map-marker'" />
                <span class="text-muted-color">{{ tracking.lastCheckedAt ? t('groupBuy.trkLastChecked', { at: time.shortDateTime(tracking.lastCheckedAt) }) : t('groupBuy.trkNeverChecked') }}</span>
                <template v-if="tracking.nextCheckAt && !deliveredAt && !tracking.complete">
                    <span class="text-muted-color" aria-hidden="true">·</span>
                    <span class="text-muted-color">{{ t('groupBuy.trkNextCheck', { at: time.shortDateTime(tracking.nextCheckAt) }) }}</span>
                </template>
                <span class="text-muted-color" aria-hidden="true">·</span>
                <span class="tabular-nums" :class="tracking.todayCount >= tracking.dailyLimit ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">{{ t('groupBuy.trkToday', { count: tracking.todayCount, limit: tracking.dailyLimit }) }}</span>
            </div>

            <p v-if="tracking.failCount > 0 || tracking.errorCode" class="m-0 text-xs text-red-600 dark:text-red-400">
                {{ t('groupBuy.trkFailed', { count: tracking.failCount, reason: errorText(tracking.errorCode) }) }}
            </p>

            <Message v-if="tracking.holdReason" severity="warn" :closable="false" size="small">
                {{ t(`groupBuy.trkHold.${tracking.holdReason}`) }}<template v-if="tracking.completedAt"> {{ t('groupBuy.trkHoldAt', { at: time.full(tracking.completedAt) }) }}</template>
            </Message>

            <!-- 이력 — 최신 위 -->
            <ol v-if="events.length" class="m-0 flex list-none flex-col p-0">
                <li v-for="(e, i) in shownEvents" :key="i" class="relative flex gap-2.5 pb-2 text-xs last:pb-0">
                    <span v-if="i < shownEvents.length - 1" class="absolute left-[4px] top-3.5 bottom-0 w-px bg-(--line)" aria-hidden="true" />
                    <span class="mt-1 size-[9px] shrink-0 rounded-full" :class="i === 0 ? 'bg-primary' : 'bg-surface-400'" aria-hidden="true" />
                    <div class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-2">
                        <span class="font-medium">{{ e.kind || t(`groupBuy.trkLevel.${UtilGroupBuy.trackingLevelKey(e.level)}`) }}</span>
                        <span v-if="e.where" class="text-muted-color">{{ e.where }}</span>
                        <span class="ml-auto text-[11px] tabular-nums text-muted-color">{{ time.shortDateTime(e.time) }}</span>
                    </div>
                </li>
            </ol>
            <p v-else-if="tracking.lastCheckedAt" class="m-0 text-xs text-muted-color">{{ t('groupBuy.trkNoEvents') }}</p>
            <div v-if="events.length > FOLD">
                <Button type="button" :label="expanded ? t('groupBuy.trkFold') : t('groupBuy.trkShowAll', { count: events.length })" :icon="expanded ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" size="small" severity="secondary" variant="text" class="px-1! py-0!" @click="expanded = !expanded" />
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyShipmentTracking } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    tracking: GroupBuyShipmentTracking;
    /** 이 발송 줄의 배송완료 시각(있으면 다음 조회를 보이지 않는다) */
    deliveredAt?: string | null;
}

const props = withDefaults(defineProps<Props>(), { deliveredAt: null });

/** 접었을 때 보이는 이력 수 */
const FOLD = 5;

const { t, te } = useI18n();
const time = useDisplayTime();
const expanded = ref(false);

/** 최신 위 — 서버 차례와 상관없이 시각으로 */
const events = computed(() => [...props.tracking.events].sort((a, b) => Date.parse(b.time) - Date.parse(a.time)));
const shownEvents = computed(() => (expanded.value ? events.value : events.value.slice(0, FOLD)));

const levelText = computed(() => (props.tracking.level === null ? t('groupBuy.trkLevelUnknown') : t(`groupBuy.trkLevel.${UtilGroupBuy.trackingLevelKey(props.tracking.level)}`)));
const levelSeverity = computed(() => UtilGroupBuy.trackingLevelSeverity(props.tracking.level));

/** 오류 코드 → 글자(모르는 코드는 그대로) */
function errorText(code: string | null): string {
    if (!code) return '—';
    const key = `groupBuy.trkError.${code}`;
    return te(key) ? t(key) : code;
}
</script>
