<template>
    <!--
        신청 건 승인 대화상자(★-1·★-2 · F6) — 승인하는 순간 금액이 확정되고 자리를 잡는다.
        ① 결제 방식(온라인 / 수동 — 사업자가 고른 값이 기본) ② 기간(시작이 지났으면 「같은 길이로 뒤로 옮기기」 — 그대로는 승인 불가) + 남은 자리
        ③ 금액(요금표 계산값 → 공급가 조정 시 사유 필수, 부가세 10%) ④ 기한(온라인 = 결제 기한 72시간 / 수동 = 입금 기한, 기본 7일).
        기간을 바꾸면 `period` 를 올리고 부모가 남은 자리·요금(`availability`)을 다시 받아 내려 준다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('bizApplication.approveTitle', { no: detail.no })" :style="{ width: 'min(40rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-m`" class="text-sm font-medium">① {{ t('bizApplication.methodLabel') }}</span>
                <SelectButton v-model="method" :options="methodOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${formId}-m`" :disabled="busy" />
                <small class="text-xs text-muted-color">{{ method === 'ONLINE' ? t('bizApplication.methodOnlineHelp') : t('bizApplication.methodManualHelp') }}<template v-if="method !== detail.preferredMethod"> · {{ t('bizApplication.methodChanged') }}</template></small>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">② {{ t('bizApplication.periodLabel') }}</span>
                <div class="flex flex-wrap items-center gap-2 text-[13px] tabular-nums">
                    {{ UtilBizApplicationMessages.period(t, time, period) }}
                    <span class="text-muted-color">· {{ t('bizApplication.daysN', { n: UtilBizApplication.days(period) }) }}</span>
                    <Tag v-if="moved" :value="t('bizApplication.periodMoved')" severity="info" />
                </div>
                <Message v-if="started" severity="warn" :closable="false">
                    <div class="flex flex-wrap items-center justify-between gap-2 text-[13px]">
                        <span>{{ t('bizApplication.startPassedNote') }}</span>
                        <Button type="button" :label="t('bizApplication.shiftPeriod')" icon="pi pi-arrow-right" size="small" :disabled="busy" @click="shift" />
                    </div>
                </Message>
                <small v-if="fieldErrors.period" class="text-xs text-red-600 dark:text-red-400">{{ fieldErrors.period }}</small>
                <span v-if="detail.adType" class="text-xs" :class="slotsLeft <= 0 ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">
                    <i v-if="checking" class="pi pi-spin pi-spinner mr-1 text-[11px]" aria-hidden="true" />{{ t('bizApplication.slotsLeft', { n: slotsLeft, limit: availability?.slotLimit ?? detail.slotLimit }) }}
                </span>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">③ {{ t('bizApplication.amountLabel') }}</span>
                <p class="m-0 text-xs text-muted-color tabular-nums">{{ t(quote.unit === 'DAY' ? 'bizApplication.quoteDay' : 'bizApplication.quotePost', { price: won(quote.unitPrice), units: quote.units, supply: won(quote.supply) }) }}</p>
                <LabeledField :label="t('bizApplication.supply')" :error="supplyError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputNumber v-model="supply" :input-id="id" :min="0" :max="999_999_999" :suffix="t('bizApplication.wonSuffix')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
                <p class="m-0 text-[13px] tabular-nums">{{ t('bizApplication.totalLine', { supply: won(amount.supply), vat: won(amount.vat), total: won(amount.total) }) }}</p>
                <LabeledField v-if="adjusted" :label="t('bizApplication.adjustReason')" :error="adjustError">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="adjustReason" rows="2" auto-resize maxlength="200" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">④ {{ method === 'ONLINE' ? t('bizApplication.payDeadline') : t('bizApplication.depositDeadline') }}</span>
                <p v-if="method === 'ONLINE'" class="m-0 text-[13px]">{{ t('bizApplication.payDeadlineLine', { hours: UtilBizApplication.PAYMENT_DEADLINE_HOURS, at: time.full(onlineDeadline) }) }}</p>
                <small v-if="method === 'ONLINE' && fieldErrors.deadlineAt" class="text-xs text-red-600 dark:text-red-400">{{ fieldErrors.deadlineAt }}</small>
                <LabeledField v-else :label="t('bizApplication.depositDeadlineDate')" :help="t('bizApplication.depositDeadlineHelp', { days: UtilBizApplication.DEPOSIT_DEADLINE_DAYS })" :error="fieldErrors.deadlineAt ?? (submitted && !depositDate ? t('bizApplication.depositDeadlineRequired') : '')">
                    <template #default="{ id, describedBy, invalid }">
                        <AppDatePicker v-model="depositDate" :input-id="id" :min="UtilBizApplication.kstToday()" :invalid="invalid" :described-by="describedBy" :disabled="busy" />
                    </template>
                </LabeledField>
            </div>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-bell mt-0.5 text-xs" aria-hidden="true" />{{ method === 'ONLINE' ? t('bizApplication.notifyOnline') : t('bizApplication.notifyManual') }}</p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('bizApplication.actApprove')" icon="pi pi-check" :loading="busy" :disabled="started || slotsLeft <= 0 || checking" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { BizApplicationDetail, BizApproveRequest, BizAvailability, BizPaymentMethod, BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    visible: boolean;
    detail: BizApplicationDetail;
    /** 지금 기간의 요금·남은 자리(기간을 옮기면 부모가 다시 받는다) */
    availability?: BizAvailability | null;
    checking?: boolean;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { availability: null, checking: false, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; period: [period: BizPeriod]; submit: [body: BizApproveRequest] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const formId = useId();
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

const method = ref<BizPaymentMethod>('ONLINE');
const period = ref<BizPeriod>({ startAt: '', endAt: '' });
const supply = ref<number | null>(0);
const adjustReason = ref('');
const depositDate = ref('');
const submitted = ref(false);
/** 열 때의 시각 — 결제 기한 표시용 */
const openedAt = ref(Date.now());

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        method.value = props.detail.preferredMethod;
        period.value = { ...props.detail.period };
        supply.value = props.detail.quote.supply;
        adjustReason.value = '';
        depositDate.value = UtilBizApplication.addDays(UtilBizApplication.kstToday(), UtilBizApplication.DEPOSIT_DEADLINE_DAYS);
        submitted.value = false;
        openedAt.value = Date.now();
    },
    { immediate: true },
);

const methodOptions = computed(() => (['ONLINE', 'MANUAL'] as const).map((m) => ({ value: m, label: t(`bizApplication.method.${m}`) })));
/** 요금표 계산 — 기간을 옮기면 서버가 다시 계산한 값(`availability`), 아니면 상세의 계산값 */
const quote = computed(() => props.availability?.quote ?? props.detail.quote);
const slotsLeft = computed(() => props.availability?.remainingSlots ?? props.detail.remainingSlots);
const started = computed(() => UtilBizApplication.started(period.value, openedAt.value));
const moved = computed(() => period.value.startAt !== props.detail.period.startAt);
const amount = computed(() => UtilBizApplication.amountOf(supply.value ?? 0));
const adjusted = computed(() => (supply.value ?? 0) !== quote.value.supply);
/** 온라인 결제 기한 — 72시간 뒤, 단 시작 시각을 넘지 않는다(서버: 온라인 기한은 시작 이전이어야 함 — 400 `fields.deadlineAt`) */
function onlineDeadlineFrom(base: number): string {
    return new Date(Math.min(base + UtilBizApplication.PAYMENT_DEADLINE_HOURS * 3_600_000, Date.parse(period.value.startAt) || Infinity)).toISOString();
}
const onlineDeadline = computed(() => onlineDeadlineFrom(openedAt.value));

const supplyError = computed(() => props.fieldErrors.supply ?? (submitted.value && !(supply.value && supply.value > 0) ? t('bizApplication.supplyRequired') : ''));
const adjustError = computed(() => props.fieldErrors.adjustReason ?? (submitted.value && adjusted.value && !adjustReason.value.trim() ? t('bizApplication.adjustReasonRequired') : ''));

function shift(): void {
    period.value = UtilBizApplication.shifted(period.value);
    emit('period', { ...period.value });
}

function submit(): void {
    submitted.value = true;
    if (props.busy || started.value || slotsLeft.value <= 0) return;
    if (!(supply.value && supply.value > 0) || (adjusted.value && !adjustReason.value.trim())) return;
    if (method.value === 'MANUAL' && !depositDate.value) return;
    const deadlineAt = method.value === 'ONLINE' ? onlineDeadlineFrom(Date.now()) : UtilBizApplication.kstMidnight(UtilBizApplication.addDays(depositDate.value, 1));
    emit('submit', { method: method.value, supply: supply.value, adjustReason: adjusted.value ? adjustReason.value.trim() : undefined, deadlineAt, period: { ...period.value } });
}
</script>
