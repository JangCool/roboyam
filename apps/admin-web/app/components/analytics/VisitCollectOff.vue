<template>
    <!--
        방문 분석 구획의 「수집이 꺼져 있어요」 — 대시보드 가젯 틀(`DashboardGadgetFrame`)의 수집 꺼짐과 같은 문구·모양.
        방문 수집 전체(`enabled`)가 꺼졌으면 그 스위치로, 아니면 그 항목으로 가는 설정 링크(`site.manage` 일 때만 — 없으면 요청하라는 설명). 막는 것은 서버다.
    -->
    <EmptyState icon="pi pi-power-off" :title="t('dashboard.disabledTitle')" :description="body">
        <NuxtLink v-if="canManage" :to="UtilAnalytics.link(target)" class="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
            <i class="pi pi-cog text-xs" aria-hidden="true" />{{ t('dashboard.disabledLink') }}
        </NuxtLink>
    </EmptyState>
</template>

<script setup lang="ts">
import type { AnalyticsSwitch } from '~/composables/analytics/api/useAnalyticsSettingsApi.types';
import UtilAnalytics from '~/utils/util-analytics';

interface Props {
    /** 이 구획의 데이터를 만드는 「통계 수집」 항목 */
    item: AnalyticsSwitch;
    /** 방문 수집 전체가 켜져 있나 — 꺼져 있으면 설명·링크가 전체 스위치를 가리킨다 */
    enabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), { enabled: true });

const { t } = useI18n();
const { can } = useAdminPermission();

const canManage = computed(() => can('site.manage'));
const target = computed<AnalyticsSwitch>(() => (props.enabled ? props.item : 'enabled'));
const body = computed(() => {
    const item = t(`analytics.label.${target.value}`);
    return canManage.value ? t('dashboard.disabledBody', { item }) : t('dashboard.disabledBodyAsk', { item });
});
</script>
