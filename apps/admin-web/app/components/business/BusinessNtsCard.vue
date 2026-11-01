<template>
    <!--
        사업자 상세 ② 국세청 상태조회(B1) + 진위확인 — 계속 · 휴업 · 폐업 · 조회 불가, 진위 일치 · 불일치 · 미확인 · 조회 불가, 조회 시각, 과세 유형, [다시 조회].
        승인 판정을 카드 안에서 바로 말한다(진위 불일치·휴폐업 = 잠금, 조회 불가·진위 미확인 = 사유를 적으면 승인).
    -->
    <section class="flex flex-col gap-2" :aria-labelledby="headId">
        <div class="flex items-center justify-between gap-2">
            <h3 :id="headId" class="m-0 text-sm font-semibold">{{ t('business.secNts') }}</h3>
            <Button v-if="canRecheck" type="button" :label="t('business.ntsRecheck')" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :loading="busy" @click="emit('recheck')" />
        </div>
        <div class="flex flex-col gap-2 rounded-lg border px-3 py-2.5" :class="tone">
            <div class="flex flex-wrap items-center gap-2">
                <i :class="UtilBusiness.ntsIcon(nts.status)" aria-hidden="true" />
                <span class="text-sm font-semibold">{{ t(`business.nts.${nts.status}`) }}</span>
                <span v-if="nts.taxType" class="text-xs text-muted-color">· {{ nts.taxType }}</span>
                <span v-if="nts.closedOn" class="text-xs text-muted-color">· {{ t('business.ntsClosedOn', { date: time.serviceDate(nts.closedOn) }) }}</span>
            </div>
            <!-- 진위확인(번호·대표자명·개업일) — 상태와 따로 한 줄 -->
            <div class="flex flex-wrap items-center gap-2" data-testid="business-nts-validity">
                <span class="text-xs text-muted-color">{{ t('business.ntsValidityLabel') }}</span>
                <Tag :value="t(`business.ntsValidity.${nts.validity}`)" :severity="UtilBusiness.validitySeverity(nts.validity)" :icon="UtilBusiness.validityIcon(nts.validity)" />
            </div>
            <p v-if="nts.validity !== 'MATCH'" class="m-0 text-xs">{{ t(`business.ntsValidityHelp.${nts.validity}`) }}</p>
            <p v-if="nts.message" class="m-0 text-xs">{{ nts.message }}</p>
            <p class="m-0 text-xs text-muted-color">{{ t('business.ntsCheckedAt', { at: time.full(nts.checkedAt) }) }}</p>
            <p class="m-0 text-xs font-medium">{{ t(`business.ntsRule.${UtilBusiness.ntsRule(nts)}`) }}</p>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { NtsCheck } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

interface Props {
    nts: NtsCheck;
    canRecheck?: boolean;
    busy?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canRecheck: false, busy: false });
const emit = defineEmits<{ recheck: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const headId = useId();

const tone = computed(() => {
    if (props.nts.validity === 'MISMATCH') return 'border-red-300 bg-red-50/60 text-red-900 dark:border-red-800 dark:bg-red-950/30 dark:text-red-200';
    switch (props.nts.status) {
        case 'CONTINUING':
            return 'border-emerald-300 bg-emerald-50/60 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200';
        case 'PAUSED':
            return 'border-amber-300 bg-amber-50/60 text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200';
        case 'CLOSED':
            return 'border-red-300 bg-red-50/60 text-red-900 dark:border-red-800 dark:bg-red-950/30 dark:text-red-200';
        default:
            return 'border-(--line) bg-(--p-content-hover-background)';
    }
});
</script>
