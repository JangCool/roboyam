<template>
    <!--
        신청 막힘 안내 — 승인되지 않았거나 재심사 중(F7)이면 광고·핫딜 신청 화면 대신 보인다. 막는 것은 서버(409 `BUSINESS_NOT_APPROVED`·`BUSINESS_RE_REVIEW`).
        신원은 승인인데 그 서비스만 정지면(`serviceSuspension` — 2026-11-02, 서버는 409 `BUSINESS_SERVICE_SUSPENDED`) 그 서비스 안내 + 사유.
    -->
    <div class="panel-card flex flex-col items-center gap-3 px-5 py-10 text-center" :data-testid="serviceSuspension ? `business-gate-service-${serviceSuspension.service}` : 'business-gate'">
        <div class="grid size-12 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
            <i class="pi pi-lock text-lg" />
        </div>
        <p class="m-0 text-sm">{{ t(`business.gate.${reason}`) }}</p>
        <p v-if="serviceSuspension?.suspendReason" class="m-0 max-w-md whitespace-pre-line text-xs leading-relaxed text-muted-color">
            {{ t('business.home.reason') }} · {{ serviceSuspension.suspendReason }}
        </p>
        <Button as="router-link" :to="UtilBusiness.CENTER_PATH" :label="t('business.gate.toCenter')" severity="secondary" variant="outlined" size="small" />
    </div>
</template>

<script setup lang="ts">
import type { BusinessProfileService, BusinessProfileStatus } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

interface Props {
    status: BusinessProfileStatus;
    reReview?: boolean;
    /** 신원은 승인인데 이 화면의 서비스만 정지 — 그 줄(사유 포함) */
    serviceSuspension?: BusinessProfileService | null;
}

const props = withDefaults(defineProps<Props>(), { reReview: false, serviceSuspension: null });

const { t } = useI18n();
const reason = computed(() => {
    if (props.status === 'APPROVED' && props.reReview) return 'RE_REVIEW';
    if (props.status === 'APPROVED' && props.serviceSuspension) return `SERVICE_SUSPENDED.${props.serviceSuspension.service}`;
    return props.status;
});
</script>
