<template>
    <!--
        사업자 상세 ② 국세청 상태조회(B1) — 계속 · 휴업 · 폐업 · 조회 불가, 조회 시각, 과세 유형, [다시 조회].
        「계속사업자」만 승인할 수 있다는 것을 카드 안에서 바로 말한다(휴·폐업 = 잠금, 조회 불가 = 사유를 적으면 승인).
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
            <p v-if="nts.message" class="m-0 text-xs">{{ nts.message }}</p>
            <p class="m-0 text-xs text-muted-color">{{ t('business.ntsCheckedAt', { at: time.full(nts.checkedAt) }) }}</p>
            <p class="m-0 text-xs font-medium">{{ t(`business.ntsRule.${UtilBusiness.approvability(nts.status)}`) }}</p>
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
