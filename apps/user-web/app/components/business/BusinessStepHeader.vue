<template>
    <!--
        단계 머리(PrimeVue `Stepper` + `StepList`, linear — 단계 이동은 아래 [이전]·[다음]으로만). 모바일은 단계 이름을 숨기고 「2/5단계 · 기간」 한 줄을 보인다(가로 넘침 방지).
        본문은 부르는 쪽이 지금 단계만 그린다. 사업자 신청·광고 신청·핫딜 신청이 같이 쓴다.
    -->
    <div class="flex flex-col gap-1" data-testid="business-step-header">
        <Stepper :value="current" linear class="min-w-0">
            <StepList class="min-w-0">
                <Step v-for="(name, index) in steps" :key="name" :value="index + 1" class="min-w-0">
                    <span class="hidden truncate sm:inline">{{ name }}</span>
                </Step>
            </StepList>
        </Stepper>
        <p class="m-0 text-sm font-semibold sm:hidden" aria-live="polite">{{ t('business.apply.stepOf', { current, total: steps.length, name: steps[current - 1] ?? '' }) }}</p>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 단계 이름(차례대로) */
    steps: string[];
    /** 지금 단계(1부터) */
    current: number;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
