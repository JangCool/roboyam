<template>
    <!--
        신청 건 승인 대화상자(★-1·★-2 · F6) — 승인하는 순간 금액이 확정되고 자리를 잡는다.
        ① 결제 방식(온라인 / 수동 — 사업자가 고른 값이 기본) ② 기간(시작이 지났으면 「같은 길이로 뒤로 옮기기」 — 그대로는 승인 불가) + 남은 자리
        ③ 금액(요금표 계산값 → 공급가 조정 시 사유 필수, 부가세 10%) ④ 기한(온라인 = 결제 기한 72시간 / 수동 = 입금 기한, 기본 7일).
        운영자 등록 건(source=OPERATOR): 결제 방식 수동 고정(서버 400 `fields.method`), 공급가 처음 값 = 등록 때 제안(`proposedSupply`).
        자기 승인(`registrant.me`, 설정 ALLOW): 경고 + 메모(1~200자, 필수) → 본문 `memo`. 설정 DENY 면 부모 패널이 [승인] 대신 안내를 보인다(서버 409 `BIZ_SELF_APPROVAL_DENIED`).
        기간을 바꾸면 `period` 를 올리고 부모가 남은 자리·요금(`availability`)을 다시 받아 내려 준다.
        체험단 건(kind TRIAL — V242 「유료 결제 연결」): 결제 방식은 둘 다 고를 수 있다(온라인은 광고주에 이어진 사업자 회원이 있어야 — 없으면 400 `fields.method`) ·
        기간은 모집 신청 기간이라 옮기지 않고 시작 지남·남은 자리 검사도 없다 · 금액 칸은 견적 그대로 잠금(다르면 400 `fields.supply`) · 기한은 비워도 된다(서버가 온라인 72시간 · 수동 7일).
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('bizApplication.approveTitle', { no: detail.no })" :style="{ width: 'min(40rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-m`" class="text-sm font-medium">① {{ t('bizApplication.methodLabel') }}</span>
                <SelectButton v-model="method" :options="methodOptions" option-label="label" option-value="value" option-disabled="disabled" :allow-empty="false" :aria-labelledby="`${formId}-m`" :disabled="busy" />
                <small class="text-xs text-muted-color">
                    <template v-if="operator">{{ t('bizApplication.methodOperatorOnly') }} · </template><template v-if="trial">{{ t('bizApplication.trial.methodHelp') }} · </template>{{ method === 'ONLINE' ? t('bizApplication.methodOnlineHelp') : t('bizApplication.methodManualHelp') }}<template v-if="method !== detail.preferredMethod"> · {{ t('bizApplication.methodChanged') }}</template>
                </small>
                <!-- 서버 400 `fields.method` — 운영자 등록 건 온라인 · 체험단 온라인인데 광고주 회원 없음(`bizapp.trial.onlineNeedsMember`) -->
                <small v-if="fieldErrors.method" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ fieldErrors.method }}</small>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">② {{ trial ? t('bizApplication.trial.periodLabel') : t('bizApplication.periodLabel') }}</span>
                <div class="flex flex-wrap items-center gap-2 text-[13px] tabular-nums">
                    <span :class="EMPH.date">{{ UtilBizApplicationMessages.period(t, time, period) }}</span>
                    <span class="text-muted-color">· {{ t('bizApplication.daysN', { n: UtilBizApplication.days(period) }) }}</span>
                    <Tag v-if="moved" :value="t('bizApplication.periodMoved')" severity="info" />
                </div>
                <small v-if="trial" class="text-xs text-muted-color">{{ t('bizApplication.trial.periodNote') }}</small>
                <Message v-else-if="started" severity="warn" :closable="false">
                    <div class="flex flex-wrap items-center justify-between gap-2 text-[13px]">
                        <span>{{ t('bizApplication.startPassedNote') }}</span>
                        <Button type="button" :label="t('bizApplication.shiftPeriod')" icon="pi pi-arrow-right" size="small" :disabled="busy" @click="shift" />
                    </div>
                </Message>
                <small v-if="fieldErrors.period" class="text-xs text-red-600 dark:text-red-400">{{ fieldErrors.period }}</small>
                <span v-if="detail.adType && !trial" class="text-xs" :class="slotsLeft <= 0 ? EMPH.refund : EMPH.ok">
                    <i v-if="checking" class="pi pi-spin pi-spinner mr-1 text-[11px]" aria-hidden="true" />{{ t('bizApplication.slotsLeft', { n: slotsLeft, limit: availability?.slotLimit ?? detail.slotLimit }) }}
                </span>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">③ {{ t('bizApplication.amountLabel') }}</span>
                <!-- 체험단 — 견적 줄(상품 · 대신 발송비)과 잠긴 금액 -->
                <template v-if="trial">
                    <p v-for="line in quote.lines ?? []" :key="line.product" class="m-0 text-xs text-muted-color tabular-nums">{{ t('bizApplication.quoteLine', { product: t(`bizApplication.product.${line.product}`), price: won(line.unitPrice), units: line.units, unit: t(`bizApplication.unit.${line.unit}`), supply: won(line.supply) }) }}</p>
                    <p class="m-0 text-xs text-muted-color">{{ prepaidTrial ? t('bizApplication.trial.amountLockedPrepaid') : t('bizApplication.trial.amountLocked') }}</p>
                </template>
                <p v-else class="m-0 text-xs text-muted-color tabular-nums">{{ t(quote.unit === 'DAY' ? 'bizApplication.quoteDay' : 'bizApplication.quotePost', { price: won(quote.unitPrice), units: quote.units, supply: won(quote.supply) }) }}</p>
                <LabeledField :label="t('bizApplication.supply')" :error="supplyError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputNumber v-model="supply" :input-id="id" :min="0" :max="999_999_999" :suffix="t('bizApplication.wonSuffix')" :disabled="busy || trial" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
                <p v-if="detail.proposedSupply !== null && operator" class="m-0 text-xs text-muted-color tabular-nums">{{ t('bizApplication.proposedSupplyNote') }} ({{ won(detail.proposedSupply) }})</p>
                <i18n-t keypath="bizApplication.totalLine" tag="p" scope="global" class="m-0 text-[13px] tabular-nums">
                    <template #supply>{{ won(amount.supply) }}</template>
                    <template #vat>{{ won(amount.vat) }}</template>
                    <template #total><span class="text-[15px]" :class="EMPH.money">{{ won(amount.total) }}</span></template>
                </i18n-t>
                <LabeledField v-if="adjusted" :label="t('bizApplication.adjustReason')" :error="adjustError">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="adjustReason" rows="2" auto-resize maxlength="200" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
            </div>

            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">④ {{ method === 'ONLINE' ? t('bizApplication.payDeadline') : t('bizApplication.depositDeadline') }}</span>
                <p v-if="method === 'ONLINE' && trial" class="m-0 text-[13px]">{{ t('bizApplication.trial.deadlineOnline', { hours: UtilBizApplication.PAYMENT_DEADLINE_HOURS }) }}</p>
                <!-- 시작이 72시간 안이면 기한이 시작 시각으로 줄어든다 — 줄어든 기한은 주의 색 + 안내 -->
                <i18n-t
                    v-else-if="method === 'ONLINE'"
                    :keypath="deadlineClamped ? 'bizApplication.payDeadlineClampedLine' : 'bizApplication.payDeadlineLine'"
                    tag="p"
                    scope="global"
                    class="m-0 text-[13px] tabular-nums"
                >
                    <template #hours>{{ UtilBizApplication.PAYMENT_DEADLINE_HOURS }}</template>
                    <template #at><span :class="deadlineClamped ? EMPH.fee : EMPH.date">{{ time.full(onlineDeadline) }}</span></template>
                </i18n-t>
                <small v-if="method === 'ONLINE' && fieldErrors.deadlineAt" class="text-xs text-red-600 dark:text-red-400">{{ fieldErrors.deadlineAt }}</small>
                <LabeledField
                    v-else-if="method === 'MANUAL'"
                    :label="t('bizApplication.depositDeadlineDate')"
                    :help="trial ? t('bizApplication.trial.deadlineManualHelp', { days: UtilBizApplication.DEPOSIT_DEADLINE_DAYS }) : t('bizApplication.depositDeadlineHelp', { days: UtilBizApplication.DEPOSIT_DEADLINE_DAYS })"
                    :error="fieldErrors.deadlineAt ?? (submitted && !depositDate && !trial ? t('bizApplication.depositDeadlineRequired') : '')"
                >
                    <template #default="{ id, describedBy, invalid }">
                        <AppDatePicker v-model="depositDate" :input-id="id" :min="UtilBizApplication.kstToday()" :invalid="invalid" :described-by="describedBy" :disabled="busy" :show-button-bar="trial" />
                    </template>
                </LabeledField>
            </div>

            <div v-if="selfApproval" class="flex flex-col gap-1.5">
                <Message severity="warn" :closable="false"><span class="text-[13px]">{{ t('bizApplication.selfApprovalWarn') }}</span></Message>
                <LabeledField :label="t('bizApplication.selfApprovalMemo')" :hint="t('bizApplication.reason.hint', { min: 1, max: 200 })" :error="memoError">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="memo" rows="2" auto-resize maxlength="200" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
            </div>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-bell mt-0.5 text-xs" aria-hidden="true" />{{ method === 'ONLINE' ? t('bizApplication.notifyOnline') : t('bizApplication.notifyManual') }}</p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('bizApplication.actApprove')" icon="pi pi-check" :loading="busy" :disabled="blocked || checking || selfDenied" />
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
/** 강조 색(돈·날짜·자리·주의) — 심사 패널과 같은 표 */
const EMPH = UtilBizApplication.EMPH;

const method = ref<BizPaymentMethod>('ONLINE');
const period = ref<BizPeriod>({ startAt: '', endAt: '' });
const supply = ref<number | null>(0);
const adjustReason = ref('');
const depositDate = ref('');
const memo = ref('');
const submitted = ref(false);

/** 체험단 건(kind TRIAL) — 금액 잠금 · 기간·자리 검사 없음 · 기한 비워도 됨 */
const trial = computed(() => props.detail.kind === 'TRIAL');
/** 선결제 체험단 건(V244) — 모집 인원 기준 견적 · 차액 환불 없음 */
const prepaidTrial = computed(() => props.detail.content.type === 'TRIAL' && props.detail.content.paymentTiming === 'PREPAID');
/** 운영자 등록 건 — 수동 결제만(체험단 건은 운영자가 만든 건이어도 온라인 결제를 고를 수 있다 — 서버 `approveTrial`) */
const operator = computed(() => props.detail.source === 'OPERATOR' && !trial.value);
/** 자기 승인(내가 등록한 건) — 허용이면 메모 필수, 막는 설정이면 승인 불가 */
const selfApproval = computed(() => props.detail.registrant?.me === true && props.detail.selfApproval !== 'DENY');
const selfDenied = computed(() => props.detail.registrant?.me === true && props.detail.selfApproval === 'DENY');
/** 열 때의 시각 — 결제 기한 표시용 */
const openedAt = ref(Date.now());

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        method.value = operator.value ? 'MANUAL' : props.detail.preferredMethod;
        period.value = { ...props.detail.period };
        supply.value = trial.value ? props.detail.quote.supply : (props.detail.proposedSupply ?? props.detail.quote.supply);
        adjustReason.value = '';
        memo.value = '';
        // 체험단은 비워 두면 서버가 승인 + 7일로 정한다
        depositDate.value = trial.value ? '' : UtilBizApplication.addDays(UtilBizApplication.kstToday(), UtilBizApplication.DEPOSIT_DEADLINE_DAYS);
        submitted.value = false;
        openedAt.value = Date.now();
    },
    { immediate: true },
);

const methodOptions = computed(() => (['ONLINE', 'MANUAL'] as const).map((m) => ({ value: m, label: t(`bizApplication.method.${m}`), disabled: operator.value && m === 'ONLINE' })));
/** 요금표 계산 — 기간을 옮기면 서버가 다시 계산한 값(`availability`), 아니면 상세의 계산값 */
const quote = computed(() => props.availability?.quote ?? props.detail.quote);
const slotsLeft = computed(() => props.availability?.remainingSlots ?? props.detail.remainingSlots);
const started = computed(() => !trial.value && UtilBizApplication.started(period.value, openedAt.value));
/** 승인을 막는 기간·자리 문제(체험단은 둘 다 없다) */
const blocked = computed(() => !trial.value && (started.value || slotsLeft.value <= 0));
const moved = computed(() => period.value.startAt !== props.detail.period.startAt);
const amount = computed(() => UtilBizApplication.amountOf(supply.value ?? 0));
const adjusted = computed(() => !trial.value && (supply.value ?? 0) !== quote.value.supply);
/** 온라인 결제 기한 — 72시간 뒤, 단 시작 시각을 넘지 않는다(서버: 온라인 기한은 시작 이전이어야 함 — 400 `fields.deadlineAt`) */
function onlineDeadlineFrom(base: number): string {
    return new Date(Math.min(base + UtilBizApplication.PAYMENT_DEADLINE_HOURS * 3_600_000, Date.parse(period.value.startAt) || Infinity)).toISOString();
}
const onlineDeadline = computed(() => onlineDeadlineFrom(openedAt.value));
/** 시작 시각 때문에 72시간보다 짧아진 기한인가 */
const deadlineClamped = computed(() => Date.parse(onlineDeadline.value) < openedAt.value + UtilBizApplication.PAYMENT_DEADLINE_HOURS * 3_600_000);

const supplyError = computed(() => props.fieldErrors.supply ?? (submitted.value && !(supply.value && supply.value > 0) ? t('bizApplication.supplyRequired') : ''));
const memoError = computed(() => props.fieldErrors.memo ?? (submitted.value && selfApproval.value && !memo.value.trim() ? t('bizApplication.selfApprovalMemoRequired') : ''));
const adjustError = computed(() => props.fieldErrors.adjustReason ?? (submitted.value && adjusted.value && !adjustReason.value.trim() ? t('bizApplication.adjustReasonRequired') : ''));

function shift(): void {
    period.value = UtilBizApplication.shifted(period.value);
    emit('period', { ...period.value });
}

function submit(): void {
    submitted.value = true;
    if (props.busy || blocked.value || selfDenied.value) return;
    if (!(supply.value && supply.value > 0) || (adjusted.value && !adjustReason.value.trim())) return;
    if (selfApproval.value && !memo.value.trim()) return;
    if (method.value === 'MANUAL' && !depositDate.value && !trial.value) return;
    // 체험단 — 온라인은 서버 기본(승인 + 72시간), 수동은 고른 날이 있을 때만(비우면 승인 + 7일)
    const deadlineAt = trial.value
        ? method.value === 'MANUAL' && depositDate.value
            ? UtilBizApplication.kstMidnight(UtilBizApplication.addDays(depositDate.value, 1))
            : undefined
        : method.value === 'ONLINE'
          ? onlineDeadlineFrom(Date.now())
          : UtilBizApplication.kstMidnight(UtilBizApplication.addDays(depositDate.value, 1));
    emit('submit', {
        method: method.value,
        supply: supply.value,
        adjustReason: adjusted.value ? adjustReason.value.trim() : undefined,
        deadlineAt,
        period: { ...period.value },
        memo: selfApproval.value ? memo.value.trim() : undefined,
    });
}
</script>
