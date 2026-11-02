<template>
    <!-- 성과 합계 띠 — 노출 · 클릭 · 클릭률(· 노출된 건 수). 숫자가 아직 없으면(`counts` null) 「—」 -->
    <dl class="m-0 grid gap-2" :class="bookings === null ? 'grid-cols-3' : 'grid-cols-2 sm:grid-cols-4'">
        <div v-for="c in cells" :key="c.key" class="flex min-w-0 flex-col gap-0.5 rounded-xl border border-(--line) bg-(--p-content-background) px-3 py-2.5">
            <dt class="text-xs text-muted-color">{{ c.label }}</dt>
            <dd class="m-0 truncate text-lg font-semibold tabular-nums">{{ c.value }}</dd>
        </div>
    </dl>
</template>

<script setup lang="ts">
import type { AdStatCounts } from '~/composables/ad/api/useAdApi.types';
import UtilAdStats from '~/utils/util-ad-stats';

interface Props {
    counts?: AdStatCounts | null;
    /** 노출된 건 수 — null 이면 칸을 두지 않는다(한 건 성과) */
    bookings?: number | null;
}

const props = withDefaults(defineProps<Props>(), { counts: null, bookings: null });
const { t, locale } = useI18n();

const cells = computed(() => {
    const c = props.counts;
    const out = [
        { key: 'imp', label: t('ad.statImpressions'), value: c ? UtilAdStats.num(c.impressions, locale.value) : '—' },
        { key: 'clk', label: t('ad.statClicks'), value: c ? UtilAdStats.num(c.clicks, locale.value) : '—' },
        { key: 'ctr', label: t('ad.statCtr'), value: c ? UtilAdStats.ctrText(c, locale.value) : '—' },
    ];
    if (props.bookings !== null) out.push({ key: 'bk', label: t('ad.statBookings'), value: c ? UtilAdStats.num(props.bookings, locale.value) : '—' });
    return out;
});
</script>
