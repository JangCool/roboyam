<template>
    <!--
        한 건의 광고 성과 조각(신청 건 상세 「성과」 칸 안) — 합계 3칸(노출 · 클릭 · 클릭률) + 「{시각} 기준 · 오늘은 집계 중」 + 집계 기준 안내 +
        날짜별 추이(막대 = 노출 왼쪽 축 · 선 = 클릭 오른쪽 축 — `BusinessStatsChart`(ECharts), 정지·내림 띠·오늘 빗금 + 범례) + 멈춘 구간 + 자리별 표 + 소재 버전별 표.
        자리·버전이 한 줄뿐이면 표 대신 한 줄 문구. 로딩(뼈대) · 숫자 모두 0(빈 결과) · 오류(다시 시도)를 다 그린다. 서버 호출은 화면이 한다(`useBusinessApi.stats`).
    -->
    <div class="flex flex-col gap-4" data-testid="business-ad-stats" :data-stats-status="status">
        <div v-if="status === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div class="grid grid-cols-3 gap-2">
                <div v-for="n in 3" :key="n" class="h-16 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            </div>
            <div class="h-52 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error' || !stats" :title="t('business.stats.loadFailed')" :description="error" retry @retry="emit('retry')" />

        <template v-else>
            <!-- 합계 -->
            <dl class="m-0 grid grid-cols-3 gap-2" data-testid="business-ad-stats-totals">
                <div v-for="tile in tiles" :key="tile.key" class="flex min-w-0 flex-col gap-0.5 rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-800">
                    <dt class="text-xs text-muted-color">{{ tile.label }}</dt>
                    <dd class="m-0 truncate text-lg font-bold tabular-nums">{{ tile.value }}</dd>
                </div>
            </dl>
            <div class="-mt-2 flex flex-col gap-1">
                <p class="m-0 text-xs tabular-nums text-muted-color" data-testid="business-ad-stats-asof">
                    {{ t('business.stats.asOf', { at: time.full(stats.asOf) }) }}<template v-if="counting"> · {{ t('business.stats.todayCounting') }}</template>
                </p>
                <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('business.stats.countRule') }}</p>
            </div>

            <Message v-if="stats.exposureStop" severity="warn" size="small" :closable="false" data-testid="business-ad-stats-stop">{{ t(`business.stats.exposureStopNote.${stats.exposureStop}`) }}</Message>

            <EmptyState v-if="empty" :title="t('business.stats.empty')" :description="t('business.stats.emptyText')" icon="pi pi-chart-bar" data-testid="business-ad-stats-empty" />

            <template v-else>
                <!-- 날짜별 추이 -->
                <div class="flex flex-col gap-2">
                    <h3 class="m-0 text-sm font-semibold">{{ t('business.stats.trendTitle') }}</h3>
                    <BusinessStatsChart :days="stats.days" :today="counting ? today : ''" />
                </div>

                <!-- 멈춘 구간 -->
                <div v-if="stats.downtimes.length" class="flex flex-col gap-1" data-testid="business-ad-stats-downtimes">
                    <h3 class="m-0 text-sm font-semibold">{{ t('business.stats.downtimesTitle') }}</h3>
                    <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs">
                        <li v-for="(dt, i) in stats.downtimes" :key="i" class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                            <PointShopTag :tone="dt.cause === 'ADVERTISER_PAUSE' ? 'warn' : 'danger'">{{ t(`business.stats.downtimeCause.${dt.cause}`) }}</PointShopTag>
                            <span class="tabular-nums text-muted-color">{{ dt.to ? `${time.shortDateTime(dt.from)} ~ ${time.shortDateTime(dt.to)}` : t('business.stats.downtimeOngoing', { from: time.shortDateTime(dt.from) }) }}</span>
                        </li>
                    </ul>
                </div>

                <!-- 자리별 -->
                <div class="flex flex-col gap-1.5" data-testid="business-ad-stats-placements">
                    <h3 class="m-0 text-sm font-semibold">{{ t('business.stats.byPlacementTitle') }}</h3>
                    <p v-if="stats.byPlacement.length === 1" class="m-0 text-sm">{{ t('business.stats.placementOne', { name: t(`ad.statPlacement.${stats.byPlacement[0]!.placement}`) }) }}</p>
                    <div v-else class="overflow-x-auto">
                        <table class="stats-table">
                            <thead>
                                <tr>
                                    <th scope="col" class="text-left">{{ t('business.stats.placement') }}</th>
                                    <th scope="col">{{ t('business.stats.impressions') }}</th>
                                    <th scope="col">{{ t('business.stats.clicks') }}</th>
                                    <th scope="col">{{ t('business.stats.ctr') }}</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="row in stats.byPlacement" :key="row.placement">
                                    <th scope="row" class="text-left font-normal">{{ t(`ad.statPlacement.${row.placement}`) }}</th>
                                    <td>{{ UtilAdStats.count(row.impressions) }}</td>
                                    <td>{{ UtilAdStats.count(row.clicks) }}</td>
                                    <td>{{ UtilAdStats.ctrText(row) }}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <!-- 소재 버전별 -->
                <div v-if="stats.byVersion.length" class="flex flex-col gap-1.5" data-testid="business-ad-stats-versions">
                    <h3 class="m-0 text-sm font-semibold">{{ t('business.stats.byVersionTitle') }}</h3>
                    <p v-if="stats.byVersion.length === 1" class="m-0 text-sm">{{ t('business.stats.versionOne', { n: stats.byVersion[0]!.version }) }}</p>
                    <div v-else class="overflow-x-auto">
                        <table class="stats-table">
                            <thead>
                                <tr>
                                    <th scope="col" class="text-left">{{ t('business.stats.version') }}</th>
                                    <th scope="col">{{ t('business.stats.impressions') }}</th>
                                    <th scope="col">{{ t('business.stats.clicks') }}</th>
                                    <th scope="col">{{ t('business.stats.ctr') }}</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="row in stats.byVersion" :key="row.version">
                                    <th scope="row" class="text-left font-normal">
                                        <span class="inline-flex flex-wrap items-center gap-1.5">
                                            {{ t('business.stats.versionN', { n: row.version }) }}
                                            <PointShopTag v-if="row.current" tone="primary">{{ t('business.stats.versionCurrent') }}</PointShopTag>
                                        </span>
                                        <span v-if="row.approvedAt" class="block text-[0.6875rem] tabular-nums text-muted-color">{{ t('business.stats.approvedAt', { at: time.date(row.approvedAt) }) }}</span>
                                    </th>
                                    <td>{{ UtilAdStats.count(row.impressions) }}</td>
                                    <td>{{ UtilAdStats.count(row.clicks) }}</td>
                                    <td>{{ UtilAdStats.ctrText(row) }}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { AdApplicationStats } from '~/composables/business/api/useBusinessApi.types';
import UtilAdStats from '~/utils/util-ad-stats';

interface Props {
    stats: AdApplicationStats | null;
    status: 'loading' | 'ready' | 'error';
    /** 오류 문구(서버 `message`) */
    error?: string;
    /** 오늘(한국 날짜) — 기간 끝이 오늘이면 「오늘은 집계 중」 */
    today?: string;
}

const props = withDefaults(defineProps<Props>(), { error: '', today: '' });
const emit = defineEmits<{ retry: [] }>();

const { t } = useI18n();
const time = useDisplayTime();

const counting = computed(() => Boolean(props.stats && props.today && props.stats.range.to === props.today));
const empty = computed(() => !props.stats || (props.stats.totals.impressions === 0 && props.stats.totals.clicks === 0));
const tiles = computed(() => {
    const c = props.stats?.totals ?? { impressions: 0, clicks: 0 };
    return [
        { key: 'impressions', label: t('business.stats.impressions'), value: UtilAdStats.count(c.impressions) },
        { key: 'clicks', label: t('business.stats.clicks'), value: UtilAdStats.count(c.clicks) },
        { key: 'ctr', label: t('business.stats.ctr'), value: UtilAdStats.ctrText(c) },
    ];
});
</script>

<style scoped>
.stats-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.8125rem;
    font-variant-numeric: tabular-nums;
}
.stats-table th,
.stats-table td {
    padding: 0.375rem 0.5rem;
    border-bottom: 1px solid var(--hairline);
    text-align: right;
    white-space: nowrap;
}
.stats-table thead th {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--p-text-muted-color);
}
.stats-table th:first-child,
.stats-table td:first-child {
    padding-left: 0;
    white-space: normal;
}
.stats-table th:last-child,
.stats-table td:last-child {
    padding-right: 0;
}
.stats-table .text-left {
    text-align: left;
}
</style>
