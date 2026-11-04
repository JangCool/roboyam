<template>
    <!--
        유입 경로 — 오늘 방문을 유입처 분류(검색 · SNS · 직접 · 다른 사이트 · 캠페인)별 수 + 비율 막대(오늘 방문 전체 대비), 아래 상위 유입 도메인 5.
        통계 수집 `referrers` 가 만든 오늘 집계를 읽는다. 수집이 꺼져 있으면 틀이 「수집이 꺼져 있어요」. 바로 가기 = 「방문자 통계 › 유입 경로」의 오늘.
        값은 묶음 응답의 `REFERRERS`(`useDashboardGadgetApi`).
    -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :disabled="state.disabled.value"
        :collect="state.collect"
        :more="moreLink"
        :empty="!v?.total"
        empty-icon="pi pi-directions"
        :empty-title="t('dashboard.referrersNone')"
        @refresh="state.reload"
    >
        <div v-if="v" class="flex flex-col gap-3">
            <p class="m-0 text-[12px] text-muted-color">{{ t('dashboard.referrersTotal', { n: v.total.toLocaleString(locale) }) }}</p>
            <ul class="m-0 flex list-none flex-col gap-2.5 p-0">
                <li v-for="g in groups" :key="g.group" class="flex flex-col gap-1 text-[13px]">
                    <span class="flex items-center gap-2">
                        <span class="min-w-0 flex-1 truncate">{{ groupName(g.group) }}</span>
                        <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ t('dashboard.referrersCount', { n: g.visits.toLocaleString(locale), rate: g.rate }) }}</span>
                    </span>
                    <span class="h-1 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
                        <span class="block h-full rounded-full bg-primary" :style="{ width: `${g.rate}%` }" />
                    </span>
                </li>
            </ul>
            <template v-if="v.domains.length">
                <p class="m-0 text-[12px] text-muted-color">{{ t('dashboard.referrersDomains') }}</p>
                <ol class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                    <li v-for="(d, i) in v.domains" :key="d.domain" class="flex items-center gap-2 py-1.5 text-[13px]">
                        <span class="w-5 shrink-0 text-center text-[11px] font-semibold text-muted-color tabular-nums">{{ i + 1 }}</span>
                        <span class="min-w-0 flex-1 truncate" :title="d.domain">{{ d.domain }}</span>
                        <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ d.visits.toLocaleString(locale) }}</span>
                    </li>
                </ol>
            </template>
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardReferrerGroup } from '~/composables/dashboard/api/useDashboardGadgetApi.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, te, locale } = useI18n();
const state = useDashboardKindData('REFERRERS', () => props.gadget);
const v = computed(() => state.data.value);
/** 바로 가기 — 방문 분석 화면의 오늘(날짜는 그릴 때 계산) */
const moreLink = computed(() => UtilVisitAnalytics.link('TODAY', UtilVisitAnalytics.PAGES.referrers));

/** 분류별 수 + 오늘 방문 전체 대비 비율(%, 반올림) — 서버 차례 그대로 */
const groups = computed(() => {
    const d = v.value;
    if (!d) return [];
    const total = Math.max(1, d.total);
    return d.groups.map((g) => ({ ...g, rate: Math.round((g.visits / total) * 100) }));
});

/** 분류 이름 — 모르는 분류는 코드 그대로 */
function groupName(group: DashboardReferrerGroup | string): string {
    const key = `dashboard.referrerGroup.${group}`;
    return te(key) ? t(key) : group;
}
</script>
