<template>
    <!--
        사업자 정지 대화상자(H2 · F4) — 영향 건수를 보고 걸린 건의 처리를 고른 뒤 사유 코드 + 글로 정지한다.
        기본값: 결제·입금 대기 = 자동 취소 · 시작 전 예약 = 전액 환불 · 진행 중 = 긴급 내림 + 일할 환불 심사. 광고·핫딜 공통 규칙이다.
        `service` 를 주면 서비스만 정지(2026-11-02) — 같은 선택지·사유 코드를 그 서비스 건에만 적용한다(제목·설명·단추 글만 다르다).
        서버는 부르지 않는다 — 고른 값은 `submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="header" :style="{ width: 'min(38rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ service ? t('business.svcSuspendBody', { service: t(`business.service.${service}`) }) : t('business.suspendBody') }}</p>

            <Message :severity="totalImpact ? 'warn' : 'secondary'" :closable="false">
                <span class="text-[13px]">{{ totalImpact ? t('business.impactSummary', { pending: impact.awaitingPayment, scheduled: impact.scheduled, running: impact.running }) : t('business.impactNone') }}</span>
            </Message>

            <fieldset v-for="group in groups" :key="group.key" class="m-0 flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5" :disabled="busy">
                <legend class="px-1 text-[13px] font-semibold">{{ group.title }} <span class="font-normal tabular-nums text-muted-color">{{ t('business.countN', { n: group.count }) }}</span></legend>
                <div v-for="opt in group.options" :key="opt.value" class="flex items-start gap-2">
                    <RadioButton v-model="choice[group.key]" :input-id="`${formId}-${group.key}-${opt.value}`" :name="`${formId}-${group.key}`" :value="opt.value" />
                    <label :for="`${formId}-${group.key}-${opt.value}`" class="text-[13px] leading-snug">
                        {{ opt.label }}<span v-if="opt.default" class="ml-1 text-[11px] text-muted-color">{{ t('business.defaultChoice') }}</span>
                    </label>
                </div>
            </fieldset>

            <LabeledField :label="t('business.suspendCodeLabel')" :error="submitted && !code ? t('bizApplication.reject.codeRequired') : ''">
                <template #default="{ id, describedBy, invalid }">
                    <Select v-model="code" :input-id="id" :options="codeOptions" option-label="label" option-value="value" :placeholder="t('bizApplication.reject.codePlaceholder')" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <LabeledField :label="t('bizApplication.reason.label')" :hint="t('bizApplication.reason.hint', { min: REASON.min, max: REASON.max })" :error="textError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="text" rows="3" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="service ? t('business.actSvcSuspend') : t('business.actSuspend')" severity="danger" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type {
    BusinessService,
    BusinessSuspendCode,
    BusinessSuspendImpact,
    BusinessSuspendRequest,
    SuspendPendingAction,
    SuspendRunningAction,
    SuspendScheduledAction,
} from '~/composables/business/api/useBusinessApi.types';
import { BUSINESS_SUSPEND_CODES } from '~/composables/business/api/useBusinessApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    visible: boolean;
    name: string;
    /** 영향 건수 — 서비스 정지면 그 서비스 건만 */
    impact: BusinessSuspendImpact;
    /** 있으면 서비스만 정지(신원은 그대로) — 제목·설명·단추 글만 바뀌고 고르는 값은 같다 */
    service?: BusinessService | null;
    busy?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { service: null, busy: false, error: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: BusinessSuspendRequest] }>();

const { t } = useI18n();
const formId = useId();
const REASON = UtilBizApplication.REASON;

const choice = reactive<{ pending: SuspendPendingAction; scheduled: SuspendScheduledAction; running: SuspendRunningAction }>({ pending: 'CANCEL', scheduled: 'REFUND_FULL', running: 'TAKEDOWN_PRORATE' });
const code = ref<BusinessSuspendCode | null>(null);
const text = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        Object.assign(choice, { pending: 'CANCEL', scheduled: 'REFUND_FULL', running: 'TAKEDOWN_PRORATE' });
        code.value = null;
        text.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const header = computed(() => (props.service ? t('business.svcSuspendTitle', { name: props.name, service: t(`business.service.${props.service}`) }) : t('business.suspendTitle', { name: props.name })));
const totalImpact = computed(() => props.impact.awaitingPayment + props.impact.scheduled + props.impact.running);
const codeOptions = computed(() => BUSINESS_SUSPEND_CODES.map((c) => ({ value: c, label: t(`business.suspendCode.${c}`) })));

const groups = computed(() => [
    {
        key: 'pending' as const,
        title: t('business.impactPending'),
        count: props.impact.awaitingPayment,
        options: [
            { value: 'CANCEL', label: t('business.optPendingCancel'), default: true },
            { value: 'KEEP', label: t('business.optPendingKeep'), default: false },
        ],
    },
    {
        key: 'scheduled' as const,
        title: t('business.impactScheduled'),
        count: props.impact.scheduled,
        options: [
            { value: 'REFUND_FULL', label: t('business.optScheduledRefund'), default: true },
            { value: 'HOLD', label: t('business.optScheduledHold'), default: false },
        ],
    },
    {
        key: 'running' as const,
        title: t('business.impactRunning'),
        count: props.impact.running,
        options: [
            { value: 'TAKEDOWN_PRORATE', label: t('business.optRunningProrate'), default: true },
            { value: 'TAKEDOWN_ONLY', label: t('business.optRunningOnly'), default: false },
        ],
    },
]);

const textError = computed(() => {
    const problem = UtilBizApplication.reasonProblem(text.value);
    return problem && (submitted.value || problem === 'TOO_LONG') ? t(`bizApplication.reason.${problem}`, { min: REASON.min, max: REASON.max }) : '';
});

function submit(): void {
    submitted.value = true;
    if (props.busy || !code.value || UtilBizApplication.reasonProblem(text.value)) return;
    emit('submit', { code: code.value, text: text.value.trim(), pendingAction: choice.pending, scheduledAction: choice.scheduled, runningAction: choice.running });
}
</script>
