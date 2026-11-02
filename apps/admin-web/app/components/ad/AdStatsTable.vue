<template>
    <!--
        광고 성과의 작은 표(유형별 · 자리별 · 소재 버전별 · 광고주 순위) — 이름 · (건 수) · 노출 · 클릭 · 클릭률 · (비중 막대). 줄 차례는 부르는 쪽(서버 = 노출 차례) 그대로.
        `selectable` 이면 이름이 단추가 되어 `select(key)` 를 낸다(광고주 이름 → 그 광고주로 거르기). 숫자 칸은 오른쪽 맞춤·고정폭 숫자.
    -->
    <DataTable :value="rows" data-key="key" size="small" scrollable class="text-[13px]">
        <Column v-if="rank" :header="t('ad.statRank')" style="width: 3rem">
            <template #body="{ index }"><span class="tabular-nums text-muted-color">{{ index + 1 }}</span></template>
        </Column>
        <Column :header="labelHeader" style="min-width: 9rem">
            <template #body="{ data }">
                <span class="flex flex-col">
                    <span class="flex items-center gap-1.5">
                        <button
                            v-if="selectable"
                            type="button"
                            class="cursor-pointer truncate border-0 bg-transparent p-0 text-left font-medium text-(--p-primary-color) hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                            @click="emit('select', data.key)"
                        >
                            {{ data.label }}
                        </button>
                        <span v-else class="truncate">{{ data.label }}</span>
                        <Tag v-if="data.tag" :value="data.tag" severity="success" />
                    </span>
                    <span v-if="data.sub" class="text-[11px] text-muted-color">{{ data.sub }}</span>
                </span>
            </template>
        </Column>
        <Column v-if="showBookings" :header="t('ad.statCol.bookings')" style="min-width: 4rem" :pt="right">
            <template #body="{ data }"><div class="text-right tabular-nums">{{ UtilAdStats.num(data.bookings ?? 0, locale) }}</div></template>
        </Column>
        <Column :header="t('ad.statImpressions')" style="min-width: 5.5rem" :pt="right">
            <template #body="{ data }"><div class="text-right tabular-nums">{{ UtilAdStats.num(data.impressions, locale) }}</div></template>
        </Column>
        <Column :header="t('ad.statClicks')" style="min-width: 4.5rem" :pt="right">
            <template #body="{ data }"><div class="text-right tabular-nums">{{ UtilAdStats.num(data.clicks, locale) }}</div></template>
        </Column>
        <Column :header="t('ad.statCtr')" style="min-width: 4.5rem" :pt="right">
            <template #body="{ data }"><div class="text-right tabular-nums">{{ UtilAdStats.ctrText(data, locale) }}</div></template>
        </Column>
        <Column v-if="showShare" :header="t('ad.statShare')" style="min-width: 8rem">
            <template #body="{ data }">
                <span class="flex items-center gap-2">
                    <span class="h-1.5 min-w-12 flex-1 overflow-hidden rounded-full bg-(--p-content-hover-background)" aria-hidden="true">
                        <span class="block h-full rounded-full bg-(--p-primary-color)" :style="{ width: `${share(data)}%` }" />
                    </span>
                    <span class="w-10 text-right text-[12px] tabular-nums">{{ Math.round(share(data)) }}%</span>
                </span>
            </template>
        </Column>
    </DataTable>
</template>

<script setup lang="ts">
import type { AdStatsTableRow } from '~/components/ad/AdStatsTable.types';
import UtilAdStats from '~/utils/util-ad-stats';

interface Props {
    rows: AdStatsTableRow[];
    labelHeader: string;
    showBookings?: boolean;
    /** 노출 비중 막대 — 분모는 `total`(없으면 줄 합) */
    showShare?: boolean;
    total?: number | null;
    rank?: boolean;
    selectable?: boolean;
}

const props = withDefaults(defineProps<Props>(), { showBookings: false, showShare: false, total: null, rank: false, selectable: false });
const emit = defineEmits<{ select: [key: string] }>();

const { t, locale } = useI18n();
const right = { columnHeaderContent: { class: 'justify-end' } };
const denominator = computed(() => props.total ?? props.rows.reduce((sum, r) => sum + r.impressions, 0));
const share = (row: AdStatsTableRow) => UtilAdStats.share(row.impressions, denominator.value);
</script>
