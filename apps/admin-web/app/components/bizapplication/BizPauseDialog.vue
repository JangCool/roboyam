<template>
    <!--
        운영자가 광고주 대신 일시 정지 · 다시 켜기 · 중단(환불 심사)하는 대화상자 — 세 동작이 같이 쓴다(일시 정지·중단 목업 2026-11-02, `BizReasonDialog` 짜임).
        위에서 아래로: 안내(동작별) · 동작별 칸(정지: 몇 번째 정지·「{끝}까지 다시 켜지 않으면 환불 없이 종료」 / 다시 켜기: 연장 없음 / 중단: 예상 환불액 · 계산 기준 · 핫딜 글 게시비 제외 · 「되돌릴 수 없어요」)
          · 요청 경로(전화·메일·기타 — 필수) · 메모(1~200자 — 필수, 관리자에게만 보인다) · 감사 안내 한 줄.
        서버는 부르지 않는다 — `submit` 으로 올리고 저장·토스트는 부모 몫이다. 열 때마다 칸을 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t(`bizApplication.pause.dialogTitle.${action}`, { no })" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t(`bizApplication.pause.dialogBody.${action}`) }}</p>

            <!-- 정지: 몇 번째 · 끝나면 환불 없이 종료 -->
            <Message v-if="action === 'pause'" severity="warn" :closable="false">
                <span class="flex flex-col gap-0.5 text-[13px]">
                    <span v-if="pauseCount">{{ t('bizApplication.pause.nthPause', { n: pauseCount.next, limit: pauseCount.limit }) }}</span>
                    <span>{{ t('bizApplication.pause.noRefundIfEnds', { end: time.full(endAt) }) }}</span>
                </span>
            </Message>
            <Message v-else-if="action === 'resume'" severity="info" :closable="false">
                <span class="text-[13px]">{{ t('bizApplication.pause.resumeNote', { end: time.full(endAt) }) }}</span>
            </Message>

            <!-- 중단: 예상 환불액(명세 일할 식) · 기준 시각 · 게시비 제외 · 되돌릴 수 없음 -->
            <template v-if="action === 'stop' && refund">
                <dl class="m-0 grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('bizApplication.pause.expectedRefund') }}</dt>
                    <dd class="m-0 font-semibold tabular-nums">{{ refund.amount > 0 ? won(refund.amount) : t('bizApplication.pause.noRefund') }}</dd>
                    <dt class="text-muted-color">{{ t('bizApplication.pause.refundBasis') }}</dt>
                    <dd class="m-0 tabular-nums">
                        {{ refund.fromPause ? t('bizApplication.pause.fromPausedAt', { at: time.shortDateTime(refund.from) }) : t('bizApplication.pause.fromNow') }}
                        <span v-if="refund.amount > 0" class="block text-[11px] text-muted-color">{{ t('bizApplication.pause.refundDays', { total: refund.totalDays, used: refund.usedDays, base: won(refund.base) }) }}</span>
                    </dd>
                </dl>
                <p v-if="refund.dealPost" class="m-0 text-xs text-muted-color">{{ t('bizApplication.pause.postFeeExcluded') }}</p>
                <p class="m-0 text-xs text-muted-color">{{ t('bizApplication.pause.refundEstimateNote') }}</p>
                <Message severity="error" :closable="false"><span class="text-[13px]">{{ t('bizApplication.pause.irreversible') }}</span></Message>
            </template>

            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-ch`" class="text-sm font-medium">{{ t('bizApplication.pause.channelLabel') }}</span>
                <SelectButton v-model="channel" :options="channelOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${formId}-ch`" :invalid="Boolean(channelError)" :disabled="busy" />
                <small v-if="channelError" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ channelError }}</small>
            </div>

            <LabeledField :label="t('bizApplication.pause.memoLabel')" :hint="t('bizApplication.reason.hint', { min: MEMO.min, max: MEMO.max })" :help="t('bizApplication.pause.memoHelp')" :error="memoError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="memo" rows="3" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="memo.trim().length > MEMO.max ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ memo.trim().length }}/{{ MEMO.max }}</small>
                </template>
            </LabeledField>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />
                {{ t('bizApplication.pause.audit') }}
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t(`bizApplication.pause.act.${action}`)" :severity="action === 'stop' ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { BizPauseActionRequest, BizPauseChannel } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_PAUSE_CHANNELS } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import type { BizPauseAction, BizPauseRefundEstimate } from '~/components/bizapplication/BizPauseDialog.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    visible: boolean;
    action: BizPauseAction;
    /** 신청 건 번호(제목) */
    no: string;
    /** 기간 끝(반열림 끝 시각) */
    endAt: string;
    /** 정지: 이번이 몇 번째인가 */
    pauseCount?: { next: number; limit: number } | null;
    /** 중단: 예상 환불 */
    refund?: BizPauseRefundEstimate | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 칸에 준 오류(`channel`·`memo`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { pauseCount: null, refund: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: BizPauseActionRequest] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const formId = useId();
const MEMO = UtilBizApplication.PAUSE_MEMO;
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);
const channelOptions = computed(() => BIZ_PAUSE_CHANNELS.map((c) => ({ value: c, label: t(`bizApplication.pauseChannelShort.${c}`) })));

const channel = ref<BizPauseChannel | null>(null);
const memo = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        channel.value = null;
        memo.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const channelError = computed(() => (submitted.value && !channel.value ? t('bizApplication.pause.channelRequired') : (props.fieldErrors.channel ?? '')));
const memoError = computed(() => {
    const problem = UtilBizApplication.reasonProblem(memo.value, MEMO.min);
    if (problem === 'TOO_LONG') return t('bizApplication.pause.memoTooLong', { max: MEMO.max });
    if (problem && submitted.value) return t('bizApplication.pause.memoRequired');
    return props.fieldErrors.memo ?? '';
});

function submit(): void {
    submitted.value = true;
    if (props.busy || !channel.value || UtilBizApplication.reasonProblem(memo.value, MEMO.min)) return;
    emit('submit', { channel: channel.value, memo: memo.value.trim() });
}
</script>
