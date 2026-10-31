<template>
    <!--
        기간 고르기 달력(§6 「언제 비었는지가 첫 질문」) — `DatePicker inline` 범위 고르기. 날짜마다 남은 칸 점(초록 = 신청 가능 · 노랑 = 자리 적음 · 꽉 찬 날은 고를 수 없음).
        값은 달력 날짜 글자(`YYYY-MM-DD`, 끝 포함)이고 시간대로 바꾸지 않는다. 저장 때 서버가 다시 판정한다. 광고 신청·핫딜 노출이 같이 쓴다.
    -->
    <div class="flex flex-col gap-2" data-testid="business-period-calendar">
        <div class="w-full max-w-sm self-center sm:self-start">
            <DatePicker
                :model-value="pickerValue"
                selection-mode="range"
                inline
                :min-date="minAsDate"
                :max-date="maxAsDate"
                :disabled-dates="fullDates"
                :manual-input="false"
                :aria-label="t('business.ad.calendarLabel')"
                class="w-full"
                :pt="{ panel: { class: 'w-full min-w-0' } }"
                @update:model-value="onPick"
            >
                <template #date="{ date }">
                    <span class="flex flex-col items-center leading-none">
                        <span>{{ date.day }}</span>
                        <span v-if="dotOf(date)" class="mt-0.5 size-1 rounded-full" :class="dotOf(date)" aria-hidden="true" />
                    </span>
                </template>
            </DatePicker>
        </div>
        <ul class="m-0 flex list-none flex-wrap gap-x-3 gap-y-1 p-0 text-xs text-muted-color" :aria-label="t('business.ad.calendarLabel')">
            <li class="flex items-center gap-1"><span class="size-2 rounded-full bg-green-500" aria-hidden="true" />{{ t('business.ad.legendOpen') }}</li>
            <li class="flex items-center gap-1"><span class="size-2 rounded-full bg-amber-500" aria-hidden="true" />{{ t('business.ad.legendFew') }}</li>
            <li class="flex items-center gap-1"><span class="size-2 rounded-full bg-surface-400" aria-hidden="true" />{{ t('business.ad.legendFull') }}</li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { AvailabilityDay } from '~/composables/business/api/useBusinessApi.types';
import type { PeriodValue } from '~/components/business/BusinessPeriodCalendar.types';
import UtilBusiness from '~/utils/util-business';

const model = defineModel<PeriodValue>({ required: true });

interface Props {
    days: AvailabilityDay[];
    /** 고를 수 있는 가장 이른 날 */
    min: string;
    /** 고를 수 있는 가장 늦은 날 */
    max: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();

const byDate = computed(() => new Map(props.days.map((d) => [d.date, d])));

function toDate(ymd: string): Date {
    return new Date(+ymd.slice(0, 4), +ymd.slice(5, 7) - 1, +ymd.slice(8, 10));
}

function toYmd(d: Date): string {
    return UtilBusiness.todayYmd(d.getTime());
}

const minAsDate = computed(() => toDate(props.min));
const maxAsDate = computed(() => toDate(props.max));
const fullDates = computed(() => props.days.filter((d) => d.remaining <= 0).map((d) => toDate(d.date)));

const pickerValue = computed<Date[] | null>(() => {
    if (!model.value.start) return null;
    return model.value.end ? [toDate(model.value.start), toDate(model.value.end)] : [toDate(model.value.start)];
});

function onPick(value: unknown): void {
    const list = Array.isArray(value) ? (value as (Date | null)[]) : [];
    const start = list[0] ? toYmd(list[0]) : '';
    const end = list[1] ? toYmd(list[1]) : '';
    model.value = { start, end };
}

/** PrimeVue 날짜 칸 값(month 는 0부터) → 남은 칸 점 색 */
function dotOf(date: { day: number; month: number; year: number }): string {
    const ymd = `${date.year}-${String(date.month + 1).padStart(2, '0')}-${String(date.day).padStart(2, '0')}`;
    const day = byDate.value.get(ymd);
    if (!day) return '';
    if (day.remaining <= 0) return 'bg-surface-400';
    return day.remaining <= Math.max(1, Math.floor(day.capacity / 3)) ? 'bg-amber-500' : 'bg-green-500';
}
</script>
