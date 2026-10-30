<template>
    <!-- 신청 건 단계 표시 — 신청 → 심사 → 결제 → 진행 → 종료. 지난 단계는 체크, 지금 단계는 강조, 반려·취소·기한 지남은 지금 자리에 「여기서 멈춤」 -->
    <ol class="m-0 grid list-none grid-cols-5 gap-1 p-0" :aria-label="t('business.detail.stageLabel')" data-testid="business-stage-track">
        <li v-for="(stage, index) in STAGES" :key="stage" class="flex min-w-0 flex-col items-center gap-1 text-center" :aria-current="index === currentIndex ? 'step' : undefined">
            <div class="flex w-full items-center">
                <span class="h-0.5 flex-1" :class="index === 0 ? 'bg-transparent' : index <= currentIndex ? 'bg-primary' : 'bg-surface-200 dark:bg-surface-700'" aria-hidden="true" />
                <span class="grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold" :class="dotClass(index)" aria-hidden="true">
                    <i v-if="index < currentIndex" class="pi pi-check text-[0.625rem]" />
                    <i v-else-if="index === currentIndex && stopped" class="pi pi-times text-[0.625rem]" />
                    <template v-else>{{ index + 1 }}</template>
                </span>
                <span class="h-0.5 flex-1" :class="index === STAGES.length - 1 ? 'bg-transparent' : index < currentIndex ? 'bg-primary' : 'bg-surface-200 dark:bg-surface-700'" aria-hidden="true" />
            </div>
            <span class="w-full truncate text-[0.6875rem] sm:text-xs" :class="index === currentIndex ? 'font-bold text-color' : 'text-muted-color'">{{ t(`business.detail.stage.${stage}`) }}</span>
            <span v-if="index === currentIndex" class="sr-only">{{ stopped ? t('business.detail.stageStopped') : t('business.detail.stageCurrent') }}</span>
        </li>
    </ol>
</template>

<script setup lang="ts">
import type { BizApplicationStatus, BizSchedulePhase } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness, { type BizStage } from '~/utils/util-business';

interface Props {
    status: BizApplicationStatus;
    phase?: BizSchedulePhase | null;
    /** 결제(입금)까지 마쳤나 — 취소 건의 멈춘 자리를 가른다 */
    paid?: boolean;
}

const props = withDefaults(defineProps<Props>(), { phase: null, paid: false });

const { t } = useI18n();
const STAGES: readonly BizStage[] = ['SUBMIT', 'REVIEW', 'PAYMENT', 'RUN', 'END'];

const stage = computed(() => UtilBusiness.stageOf(props.status, props.phase, props.paid));
const currentIndex = computed(() => STAGES.indexOf(stage.value.current));
const stopped = computed(() => stage.value.stopped);

function dotClass(index: number): string {
    if (index < currentIndex.value) return 'bg-primary text-primary-contrast';
    if (index === currentIndex.value) return stopped.value ? 'bg-red-600 text-white' : 'bg-primary text-primary-contrast ring-4 ring-primary/20';
    return 'bg-surface-200 text-muted-color dark:bg-surface-700';
}
</script>
