<template>
    <!--
        신청 건 상태 딱지 — 「예약 · 진행 중 · 종료」는 SCHEDULED 안의 시각 단계로 가른다. 모양은 공통 딱지(`PointShopTag`).
        내려진 건(`TAKEN_DOWN`)은 까닭(`takedownCause`)이 광고주 요청이면 「일시 정지」(정지한 채 끝 날짜가 지났으면 「정지한 채 끝남」)·「중단」, 아니면 「내려짐」.
    -->
    <PointShopTag :tone="UtilBusiness.statusTone(status, phase, cause, pausedEnded)" data-testid="business-status-tag">{{ t(`business.status.${UtilBusiness.statusKey(status, phase, cause, pausedEnded)}`) }}</PointShopTag>
</template>

<script setup lang="ts">
import type { BizApplicationStatus, BizSchedulePhase, BizTakedownCause } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

interface Props {
    status: BizApplicationStatus;
    phase?: BizSchedulePhase | null;
    /** 내림 까닭(옛 응답은 없음) */
    cause?: BizTakedownCause | null;
    /** 끝 날짜 — 정지한 채 끝났는지 가른다(없으면 가르지 않는다) */
    endDate?: string;
}

const props = withDefaults(defineProps<Props>(), { phase: null, cause: null, endDate: '' });

const { t } = useI18n();

const pausedEnded = computed(() => Boolean(props.endDate) && UtilBusiness.pauseEnded(props.status, props.cause, props.endDate, Date.now()));
</script>
