<template>
    <!-- 신청 막힘 안내 — 승인되지 않았거나 재심사 중(F7)이면 광고·핫딜 신청 화면 대신 보인다. 막는 것은 서버(409 `BUSINESS_NOT_APPROVED`·`BUSINESS_RE_REVIEW`) -->
    <div class="panel-card flex flex-col items-center gap-3 px-5 py-10 text-center" data-testid="business-gate">
        <div class="grid size-12 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
            <i class="pi pi-lock text-lg" />
        </div>
        <p class="m-0 text-sm">{{ t(`business.gate.${reason}`) }}</p>
        <Button as="router-link" :to="UtilBusiness.CENTER_PATH" :label="t('business.gate.toCenter')" severity="secondary" variant="outlined" size="small" />
    </div>
</template>

<script setup lang="ts">
import type { BusinessProfileStatus } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

interface Props {
    status: BusinessProfileStatus;
    reReview?: boolean;
}

const props = withDefaults(defineProps<Props>(), { reReview: false });

const { t } = useI18n();
const reason = computed(() => (props.status === 'APPROVED' && props.reReview ? 'RE_REVIEW' : props.status));
</script>
