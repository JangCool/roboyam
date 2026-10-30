<template>
    <!-- 신청 건 상태 딱지 — 「예약 · 진행 중 · 종료」는 SCHEDULED 안의 시각 단계로 가른다. 모양은 공통 딱지(`PointShopTag`) -->
    <PointShopTag :tone="UtilBusiness.statusTone(status, phase)" data-testid="business-status-tag">{{ t(`business.status.${UtilBusiness.statusKey(status, phase)}`) }}</PointShopTag>
</template>

<script setup lang="ts">
import type { BizApplicationStatus, BizSchedulePhase } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

interface Props {
    status: BizApplicationStatus;
    phase?: BizSchedulePhase | null;
}

withDefaults(defineProps<Props>(), { phase: null });

const { t } = useI18n();
</script>
