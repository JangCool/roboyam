<template>
    <!--
        Stepper 아래 [이전]·[다음] 줄(§6 — 처음 하는 긴 입력은 단계당 한 화면, 모바일은 하단 고정 [다음]).
        모바일(md 미만)은 창 아래에 붙는 줄(하단 탭 위를 덮는다 — 공구 상세 구매 줄과 같은 방식) + 그만큼의 빈 자리, 데스크톱은 본문 맨 아래 일반 줄이다.
        마지막 단계의 [다음] 자리에는 `nextLabel`(예: 심사 신청)을 준다. 사업자 신청·광고 신청·핫딜 신청이 같이 쓴다.
    -->
    <div>
        <div class="h-20 md:hidden" aria-hidden="true" />
        <div
            class="fixed inset-x-0 bottom-0 z-40 flex items-center gap-2 border-t border-hairline bg-card px-4 pb-[calc(0.625rem+env(safe-area-inset-bottom))] pt-2.5 shadow-[0_-4px_16px_rgb(0_0_0/0.06)] md:static md:z-auto md:border-0 md:bg-transparent md:p-0 md:shadow-none"
            data-testid="business-step-nav"
        >
            <Button v-if="canPrev" type="button" :label="t('common.prev')" icon="pi pi-angle-left" severity="secondary" variant="outlined" :disabled="busy" @click="emit('prev')" />
            <span class="ml-auto text-xs tabular-nums text-muted-color md:hidden">{{ step }}/{{ total }}</span>
            <Button type="button" :label="nextLabel || t('common.next')" :icon="nextLabel ? undefined : 'pi pi-angle-right'" icon-pos="right" :loading="busy" class="min-w-28 md:ml-auto" data-testid="business-step-next" @click="emit('next')" />
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    step: number;
    total: number;
    canPrev?: boolean;
    /** 마지막 단계 단추 글자 — 비우면 「다음」 */
    nextLabel?: string;
    busy?: boolean;
}

withDefaults(defineProps<Props>(), { canPrev: false, nextLabel: '', busy: false });
const emit = defineEmits<{ prev: []; next: [] }>();

const { t } = useI18n();
</script>
