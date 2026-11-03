<template>
    <!-- 광고 성과 — 어제·7일 노출·클릭·클릭률 요약 + 7일 노출 미니 막대(배치가 만든 광고 일별 집계만 읽는다). 누르면 광고 성과 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/ads/performance" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <table class="w-full border-collapse text-[13px] tabular-nums">
                <thead>
                    <tr class="text-[11px] text-muted-color">
                        <th scope="col" class="py-1 text-left font-normal"><span class="sr-only">{{ t('dashboard.adPeriod') }}</span></th>
                        <th scope="col" class="py-1 text-right font-normal">{{ t('ad.statImpressions') }}</th>
                        <th scope="col" class="py-1 text-right font-normal">{{ t('ad.statClicks') }}</th>
                        <th scope="col" class="py-1 text-right font-normal">{{ t('ad.statCtr') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in rows" :key="row.key" class="border-t border-(--line)">
                        <th scope="row" class="py-1.5 text-left font-normal text-muted-color">{{ row.label }}</th>
                        <td class="py-1.5 text-right">{{ UtilDashboardMessages.compact(row.c.impressions, locale) }}</td>
                        <td class="py-1.5 text-right">{{ UtilDashboardMessages.num(row.c.clicks, locale) }}</td>
                        <td class="py-1.5 text-right">{{ UtilDashboardMessages.ctr(row.c, locale) }}</td>
                    </tr>
                </tbody>
            </table>
            <DashboardMiniBars :days="impressionDays" :summary="t('dashboard.adTrendSummary', { days: impressionDays.length })" />
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardMessages from '~/utils/util-dashboard-messages';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const state = useDashboardKindData('AD_PERFORMANCE', () => props.gadget);
const v = computed(() => state.data.value);

const rows = computed(() => {
    const d = v.value;
    if (!d) return [];
    return [
        { key: 'yesterday', label: t('dashboard.adYesterday'), c: d.yesterday },
        { key: 'week', label: t('dashboard.adWeek'), c: d.week },
    ];
});
const impressionDays = computed(() => v.value?.days.map((d) => ({ date: d.date, count: d.impressions })) ?? []);
</script>
