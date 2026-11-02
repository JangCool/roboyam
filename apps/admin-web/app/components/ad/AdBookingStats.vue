<template>
    <!--
        한 건의 성과 조각 — 일정표 서랍 「성과」 탭이 쓴다(사업자 쪽 신청 건 상세 「성과」 칸과 같은 내용, user-web 은 따로 그린다).
        기간(건 기간 전체 — 서버 기본) · asOf · 합계 3칸 · 집계 기준 안내 · 날짜별 차트(정지·내림 띠, 오늘 빗금) · 멈춘 구간 · 자리별 · 소재 버전별(한 줄뿐이면 표 대신 한 줄 문구).
        서버는 부르지 않는다 — 부모가 받아 넘기고, 다시 시도는 `retry`. 시작 전 안내는 부르는 쪽(서랍)이 한다.
    -->
    <ErrorState v-if="error" :title="t('ad.statLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
    <div v-else-if="!stats" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
        <div class="grid grid-cols-3 gap-2"><Skeleton v-for="n in 3" :key="n" height="3.75rem" /></div>
        <Skeleton height="13rem" />
        <Skeleton height="1rem" width="60%" />
    </div>
    <div v-else class="flex min-w-0 flex-col gap-4">
        <p class="m-0 flex flex-wrap items-center gap-x-2 text-[12px] text-muted-color tabular-nums">
            <span>{{ t('ad.statRange', { from: time.serviceDate(stats.range.from), to: time.serviceDate(stats.range.to) }) }}</span>
            <span aria-hidden="true">·</span>
            <span>{{ t('ad.statAsOf', { at: time.shortDateTime(stats.asOf) }) }}</span>
        </p>

        <EmptyState v-if="empty" icon="pi pi-chart-bar" :title="t('ad.statEmpty')" :description="t('ad.statCountRule')" />
        <template v-else>
            <AdStatsTotals :counts="stats.totals" />
            <p class="m-0 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color"><i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />{{ t('ad.statCountRule') }}</p>

            <section class="flex flex-col gap-2" :aria-labelledby="`${idBase}-daily`">
                <h3 :id="`${idBase}-daily`" class="m-0 text-[13px] font-semibold">{{ t('ad.statDaily') }}</h3>
                <AdStatsChart :days="stats.days" />
            </section>

            <section v-if="stats.downtimes.length" class="flex flex-col gap-1.5" :aria-labelledby="`${idBase}-down`">
                <h3 :id="`${idBase}-down`" class="m-0 text-[13px] font-semibold">{{ t('ad.statDowntimes') }}</h3>
                <ul class="m-0 flex list-none flex-col gap-1 p-0 text-[12px]">
                    <li v-for="d in stats.downtimes" :key="d.from" class="flex flex-wrap items-center gap-x-2">
                        <span class="inline-block size-2 shrink-0 rounded-full" :class="d.cause === 'ADVERTISER_PAUSE' ? 'bg-amber-500' : 'bg-red-500'" aria-hidden="true" />
                        <span class="tabular-nums">{{ time.shortDateTime(d.from) }} ~ {{ d.to ? time.shortDateTime(d.to) : t('ad.statDowntimeOngoing') }}</span>
                        <span class="text-muted-color">{{ t(`bizApplication.takedownCause.${d.cause}`) }}</span>
                    </li>
                </ul>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${idBase}-place`">
                <h3 :id="`${idBase}-place`" class="m-0 text-[13px] font-semibold">{{ t('ad.statByPlacement') }}</h3>
                <p v-if="placementRows.length === 1" class="m-0 text-[13px]">{{ t('ad.statOnePlacement', { place: placementRows[0]!.label }) }}</p>
                <div v-else class="min-w-0 overflow-hidden rounded-lg border border-(--line)"><AdStatsTable :rows="placementRows" :label-header="t('ad.statCol.placement')" show-share /></div>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${idBase}-ver`">
                <h3 :id="`${idBase}-ver`" class="m-0 text-[13px] font-semibold">{{ t('ad.statByVersion') }}</h3>
                <p v-if="versionRows.length === 1" class="m-0 text-[13px]">{{ t('ad.statOneVersion', { n: stats.byVersion[0]!.version }) }}</p>
                <div v-else class="min-w-0 overflow-hidden rounded-lg border border-(--line)"><AdStatsTable :rows="versionRows" :label-header="t('ad.statCol.version')" /></div>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { AdStatsTableRow } from '~/components/ad/AdStatsTable.types';
import type { AdApplicationStats } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAdStats from '~/utils/util-ad-stats';

interface Props {
    stats?: AdApplicationStats | null;
    loading?: boolean;
    error?: ApiError | null;
}

const props = withDefaults(defineProps<Props>(), { stats: null, loading: false, error: null });
const emit = defineEmits<{ retry: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const idBase = useId();

const empty = computed(() => Boolean(props.stats && UtilAdStats.isEmpty(props.stats.totals)));
const placementRows = computed<AdStatsTableRow[]>(() => (props.stats?.byPlacement ?? []).map((p) => ({ key: p.placement, label: t(`ad.statPlacement.${p.placement}`), impressions: p.impressions, clicks: p.clicks })));
const versionRows = computed<AdStatsTableRow[]>(() =>
    (props.stats?.byVersion ?? []).map((v) => ({
        key: String(v.version),
        label: t('ad.versionN', { n: v.version }),
        sub: v.approvedAt ? t('ad.statApprovedOn', { at: time.value.shortDateTime(v.approvedAt) }) : t('ad.notReviewedYet'),
        tag: v.current ? t('ad.statVersionCurrent') : undefined,
        impressions: v.impressions,
        clicks: v.clicks,
    })),
);
</script>
