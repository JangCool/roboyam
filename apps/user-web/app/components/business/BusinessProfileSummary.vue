<template>
    <!-- 사업자 정보 요약(가린 값만 — 평문은 내려오지 않는다). 사업자 센터 홈의 심사 중·승인 상태가 쓴다 -->
    <section class="panel-card p-4" :aria-labelledby="titleId" data-testid="business-profile-summary">
        <div class="mb-3 flex items-center justify-between gap-2">
            <h2 :id="titleId" class="m-0 flex items-center gap-2 text-sm font-semibold">
                {{ t('business.home.summaryTitle') }}
                <PointShopTag :tone="tone">{{ t(`business.profileStatus.${profile.status}`) }}</PointShopTag>
            </h2>
            <slot name="actions" />
        </div>
        <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
            <dt class="text-muted-color">{{ t('business.field.companyName') }}</dt>
            <dd class="m-0 break-all">{{ profile.companyName ?? '—' }}</dd>
            <dt class="text-muted-color">{{ t('business.field.bizNo') }}</dt>
            <dd class="m-0 tabular-nums">{{ profile.bizNoMasked ?? '—' }}</dd>
            <dt class="text-muted-color">{{ t('business.field.ownerName') }}</dt>
            <dd class="m-0">{{ profile.ownerName ?? '—' }}</dd>
            <dt class="text-muted-color">{{ t('business.field.phone') }}</dt>
            <dd class="m-0 tabular-nums">{{ profile.phoneMasked ?? '—' }}</dd>
            <dt class="text-muted-color">{{ t('business.field.email') }}</dt>
            <dd class="m-0 break-all">{{ profile.email ?? '—' }}</dd>
            <dt class="text-muted-color">{{ t('business.field.address') }}</dt>
            <dd class="m-0 break-words">{{ profile.address ? `${profile.address.address1} ${profile.address.address2}`.trim() : '—' }}</dd>
            <dt class="text-muted-color">{{ t('business.field.certificate') }}</dt>
            <dd class="m-0 break-all">{{ profile.certificate?.name ?? '—' }}</dd>
        </dl>
        <p v-if="profile.submittedAt" class="m-0 mt-3 text-xs text-muted-color">{{ t('business.home.submittedAt', { at: time.full(profile.submittedAt) }) }}</p>
    </section>
</template>

<script setup lang="ts">
import type { BusinessProfile } from '~/composables/business/api/useBusinessApi.types';

interface Props {
    profile: BusinessProfile;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();
const titleId = useId();

const tone = computed(() => {
    switch (props.profile.status) {
        case 'APPROVED':
            return props.profile.reReview ? 'warn' : 'success';
        case 'PENDING':
            return 'primary';
        case 'REJECTED':
        case 'SUSPENDED':
            return 'danger';
        default:
            return 'neutral';
    }
});
</script>
