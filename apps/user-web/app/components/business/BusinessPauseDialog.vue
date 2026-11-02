<template>
    <!--
        일시 정지 확인 창(광고주 요청 — biz-pause-contract) — 바로 멈춤 · 자리는 계속 내 몫 · {끝 날}까지 언제든 승인 없이 다시 켬(지금 승인된 소재 그대로) · 멈춘 시간만큼 기간이 늘지 않음 ·
        **멈춘 채 기간이 끝나면 환불 없이 종료** · 이번 뒤 남는 횟수. 핫딜 글이면 목록·검색·가젯에서 빠지고 주소로 오면 「판매자가 잠시 내린 딜이에요」만 보이며 노출 자리도 같이 멈춘다.
        신청 건 상세와 글 보기 딜 카드가 같이 쓴다. 보내기는 부르는 쪽이 한다.
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :closable="!busy"
        :close-on-escape="!busy"
        :header="t('business.pause.confirmTitle')"
        :style="{ width: 'min(28rem, calc(100vw - 2rem))' }"
        data-testid="business-pause-dialog"
        @update:visible="(v: boolean) => !v && emit('close')"
    >
        <div class="flex flex-col gap-3 text-sm">
            <ul class="m-0 flex list-none flex-col gap-2 p-0">
                <li class="flex items-start gap-2"><i class="pi pi-pause-circle mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.confirmNow') }}</li>
                <li class="flex items-start gap-2"><i class="pi pi-replay mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.confirmResume', { end: endLabel }) }}</li>
                <li class="flex items-start gap-2"><i class="pi pi-calendar-times mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.confirmNoExtend') }}</li>
                <li v-if="deal" class="flex items-start gap-2" data-testid="business-pause-dialog-deal"><i class="pi pi-eye-slash mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.confirmDeal') }}</li>
            </ul>
            <Message severity="warn" size="small" :closable="false">{{ t('business.pause.confirmEndWarn') }}</Message>
            <p class="m-0 text-xs text-muted-color" data-testid="business-pause-dialog-left">{{ t('business.pause.confirmLeft', { n: left }) }}</p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </div>
        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('close')" />
            <Button type="button" :label="t('business.pause.pause')" icon="pi pi-pause" severity="warn" :loading="busy" data-testid="business-pause-submit" @click="emit('submit')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
interface Props {
    visible: boolean;
    /** 다시 켤 수 있는 마지막 날(표시 글자 — 끝 날짜) */
    endLabel: string;
    /** 이번에 멈추면 남는 정지 횟수 */
    left: number;
    /** 사업자 핫딜 글 — 목록에서 빠지는 안내를 더한다 */
    deal?: boolean;
    busy?: boolean;
    error?: string;
}

withDefaults(defineProps<Props>(), { deal: false, busy: false, error: '' });
const emit = defineEmits<{ submit: []; close: [] }>();

const { t } = useI18n();
</script>
