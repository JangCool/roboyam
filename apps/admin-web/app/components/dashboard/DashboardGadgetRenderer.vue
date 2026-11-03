<template>
    <!-- 가젯 종류 → 그 종류의 부품(`components/dashboard/gadget/Dashboard<Kind>Gadget.vue`). 제목은 설정 제목, 없으면 종류 이름 -->
    <component :is="COMPONENTS[gadget.type]" :gadget="gadget" :title="title" />
</template>

<script setup lang="ts">
import type { Component } from 'vue';
import type { DashboardGadgetItem, DashboardGadgetType } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import DashboardAdPerformanceGadget from '~/components/dashboard/gadget/DashboardAdPerformanceGadget.vue';
import DashboardAdScheduleGadget from '~/components/dashboard/gadget/DashboardAdScheduleGadget.vue';
import DashboardAuditRecentGadget from '~/components/dashboard/gadget/DashboardAuditRecentGadget.vue';
import DashboardBatchHealthGadget from '~/components/dashboard/gadget/DashboardBatchHealthGadget.vue';
import DashboardCommerceGadget from '~/components/dashboard/gadget/DashboardCommerceGadget.vue';
import DashboardGroupBuyProgressGadget from '~/components/dashboard/gadget/DashboardGroupBuyProgressGadget.vue';
import DashboardGroupBuyQnaGadget from '~/components/dashboard/gadget/DashboardGroupBuyQnaGadget.vue';
import DashboardGroupRequestsGadget from '~/components/dashboard/gadget/DashboardGroupRequestsGadget.vue';
import DashboardHotdealStatusGadget from '~/components/dashboard/gadget/DashboardHotdealStatusGadget.vue';
import DashboardMemberStatsGadget from '~/components/dashboard/gadget/DashboardMemberStatsGadget.vue';
import DashboardMemoGadget from '~/components/dashboard/gadget/DashboardMemoGadget.vue';
import DashboardNicknameImagesGadget from '~/components/dashboard/gadget/DashboardNicknameImagesGadget.vue';
import DashboardOperatorSecurityGadget from '~/components/dashboard/gadget/DashboardOperatorSecurityGadget.vue';
import DashboardPointShopGadget from '~/components/dashboard/gadget/DashboardPointShopGadget.vue';
import DashboardPointSummaryGadget from '~/components/dashboard/gadget/DashboardPointSummaryGadget.vue';
import DashboardPopularNowGadget from '~/components/dashboard/gadget/DashboardPopularNowGadget.vue';
import DashboardPostActivityGadget from '~/components/dashboard/gadget/DashboardPostActivityGadget.vue';
import DashboardReconcileGadget from '~/components/dashboard/gadget/DashboardReconcileGadget.vue';
import DashboardReportsGadget from '~/components/dashboard/gadget/DashboardReportsGadget.vue';
import DashboardSalesSummaryGadget from '~/components/dashboard/gadget/DashboardSalesSummaryGadget.vue';
import DashboardShortcutsGadget from '~/components/dashboard/gadget/DashboardShortcutsGadget.vue';
import DashboardSignupTrendGadget from '~/components/dashboard/gadget/DashboardSignupTrendGadget.vue';
import DashboardTodoGadget from '~/components/dashboard/gadget/DashboardTodoGadget.vue';
import DashboardVisitorsGadget from '~/components/dashboard/gadget/DashboardVisitorsGadget.vue';
import DashboardTodayScheduleGadget from '~/components/dashboard/gadget/DashboardTodayScheduleGadget.vue';
import DashboardOrderTrendGadget from '~/components/dashboard/gadget/DashboardOrderTrendGadget.vue';
import DashboardRecentSignupsGadget from '~/components/dashboard/gadget/DashboardRecentSignupsGadget.vue';
import DashboardRecentPostsGadget from '~/components/dashboard/gadget/DashboardRecentPostsGadget.vue';
import DashboardBoardRankingGadget from '~/components/dashboard/gadget/DashboardBoardRankingGadget.vue';
import DashboardSanctionsGadget from '~/components/dashboard/gadget/DashboardSanctionsGadget.vue';
import DashboardHotdealTopGadget from '~/components/dashboard/gadget/DashboardHotdealTopGadget.vue';
import DashboardNotificationHealthGadget from '~/components/dashboard/gadget/DashboardNotificationHealthGadget.vue';

interface Props {
    gadget: DashboardGadgetItem;
}

const props = withDefaults(defineProps<Props>(), {});

const COMPONENTS: Record<DashboardGadgetType, Component> = {
    TODO: DashboardTodoGadget,
    COMMERCE_TODO: DashboardCommerceGadget,
    REPORTS: DashboardReportsGadget,
    POST_ACTIVITY: DashboardPostActivityGadget,
    POPULAR_NOW: DashboardPopularNowGadget,
    GROUP_REQUESTS: DashboardGroupRequestsGadget,
    MEMBER_STATS: DashboardMemberStatsGadget,
    SIGNUP_TREND: DashboardSignupTrendGadget,
    NICKNAME_IMAGES: DashboardNicknameImagesGadget,
    HOTDEAL_STATUS: DashboardHotdealStatusGadget,
    AD_PERFORMANCE: DashboardAdPerformanceGadget,
    AD_SCHEDULE: DashboardAdScheduleGadget,
    GROUPBUY_PROGRESS: DashboardGroupBuyProgressGadget,
    GROUPBUY_QNA: DashboardGroupBuyQnaGadget,
    SALES_SUMMARY: DashboardSalesSummaryGadget,
    RECONCILE: DashboardReconcileGadget,
    POINT_SUMMARY: DashboardPointSummaryGadget,
    POINTSHOP: DashboardPointShopGadget,
    BATCH_HEALTH: DashboardBatchHealthGadget,
    AUDIT_RECENT: DashboardAuditRecentGadget,
    OPERATOR_SECURITY: DashboardOperatorSecurityGadget,
    MEMO: DashboardMemoGadget,
    SHORTCUTS: DashboardShortcutsGadget,
    VISITORS: DashboardVisitorsGadget,
    TODAY_SCHEDULE: DashboardTodayScheduleGadget,
    ORDER_TREND: DashboardOrderTrendGadget,
    RECENT_SIGNUPS: DashboardRecentSignupsGadget,
    RECENT_POSTS: DashboardRecentPostsGadget,
    BOARD_RANKING: DashboardBoardRankingGadget,
    SANCTIONS: DashboardSanctionsGadget,
    HOTDEAL_TOP: DashboardHotdealTopGadget,
    NOTIFICATION_HEALTH: DashboardNotificationHealthGadget,
};

const { t } = useI18n();
const title = computed(() => props.gadget.config.title || t(`dashboard.kind.${props.gadget.type}`));
</script>
