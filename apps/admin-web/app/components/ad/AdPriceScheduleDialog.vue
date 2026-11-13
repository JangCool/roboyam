<template>
    <!--
        상품 하나의 가격 변경 예약 — 상품 줄의 [가격 변경](새 예약) · 예약 목록의 [고치기](그 예약 값으로 시작)에서 연다(`pages/ads/placements.vue` 「상품 가격」 탭).
          적용 시각(그 상품 것 — 기본 내일 00:00 한국 시각, 지난 시각·그 상품의 같은 시각 예약은 칸 오류) → 칸마다 「지금」 옆에 「새 값」(바꾸지 않는 칸은 그 시각 바로 앞 값 그대로)
          → 사업자 예상 금액(지금 → 새 값, 7일/1건 + 부가세 10%) → 안내(신청 낸 시각 기준 가격 · 한도 줄이면 겹침 경고 · 0원 경고 · 뒤에 걸린 예약) → [예약하기].
        「바로 앞 값」 = 지금 값에 그 시각 전 예약들(고치는 예약은 빼고)을 차례로 적용한 값 — 새 값의 처음 값이고, 지금과 다르면 칸 아래에 적는다. 바로 앞 값과 같으면 [예약하기]가 잠긴다.
        서버는 부르지 않는다 — 값은 `submit` 으로 올린다. 서버 칸 오류(`fields.effectiveAt`·`unitPrice` …)는 그 칸 아래, 나머지는 아래 한 줄.
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :closable="!busy"
        :header="rule ? t(schedule ? 'ad.priceDialogEditTitle' : 'ad.priceDialogTitle', { product: t(UtilAdPrice.nameKey(rule.product)) }) : ''"
        :style="{ width: 'min(40rem, calc(100vw - 2rem))' }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <form v-if="rule" :id="formId" class="flex flex-col gap-4" novalidate data-testid="ad-price-schedule-dialog" @submit.prevent="submit">
            <div class="max-w-sm">
                <LabeledField :label="t('ad.priceEffectiveAt')" :help="t('ad.priceEffectiveAtHelp')" :error="effectiveAtError">
                    <template #default="{ id, describedBy, invalid }">
                        <AppDateTimePicker
                            v-model="form.effectiveAt"
                            :input-id="id"
                            :min="minAt"
                            :minute-step="UtilAdPrice.EFFECTIVE_MINUTE_STEP"
                            :readonly="busy"
                            :invalid="invalid"
                            :described-by="describedBy"
                            :show-button-bar="false"
                            data-testid="ad-price-schedule-effective-at"
                        />
                    </template>
                </LabeledField>
            </div>

            <!-- 칸마다 지금 · 새 값 -->
            <div class="flex flex-col gap-3">
                <div class="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-4 text-xs text-muted-color sm:grid-cols-[9rem_7rem_minmax(0,1fr)]" aria-hidden="true">
                    <span class="hidden sm:block">{{ t('ad.priceDialogField') }}</span>
                    <span>{{ t('ad.priceDialogNow') }}</span>
                    <span>{{ t('ad.priceDialogNew') }}</span>
                </div>
                <div v-for="field in fields" :key="field" class="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] items-start gap-x-4 gap-y-1 sm:grid-cols-[9rem_7rem_minmax(0,1fr)]">
                    <label :for="`${formId}-${field}`" class="col-span-2 flex flex-col gap-0.5 text-sm font-medium sm:col-span-1 sm:pt-2">
                        {{ labelOf(field) }}
                        <span v-if="field !== 'unitPrice'" class="text-xs font-normal text-muted-color">{{ t(`ad.priceFieldShortHelp.${field}`) }}</span>
                    </label>
                    <span class="pt-2 text-sm tabular-nums text-muted-color">{{ valueText(field, current[field]) }}</span>
                    <div class="flex min-w-0 flex-col gap-1">
                        <span v-if="field === 'capacity' && rule.unit === 'POST'" class="flex min-h-10 items-center text-sm text-muted-color">{{ t('ad.priceNoLimit') }}</span>
                        <InputGroup v-else>
                            <InputNumber
                                :model-value="form[field]"
                                :input-id="`${formId}-${field}`"
                                :min="UtilAdPrice.MIN[field]"
                                :max-fraction-digits="0"
                                :allow-empty="false"
                                :readonly="busy"
                                :invalid="!!fieldErrors[field]"
                                :aria-describedby="`${formId}-${field}-msg`"
                                fluid
                                input-class="min-w-0 text-right tabular-nums"
                                :data-testid="`ad-price-schedule-${field}`"
                                @update:model-value="(v: number | null) => onNumber(field, v)"
                            />
                            <InputGroupAddon>{{ suffixOf(field) }}</InputGroupAddon>
                        </InputGroup>
                        <small :id="`${formId}-${field}-msg`" class="text-xs" :class="fieldErrors[field] ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                            {{ fieldErrors[field] || (prior[field] !== current[field] ? t('ad.priceDialogPrior', { value: valueText(field, prior[field]) }) : '') }}
                        </small>
                    </div>
                </div>
            </div>

            <!-- 사업자 예상 금액 — 지금 → 새 값 -->
            <section class="flex flex-col gap-2 rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-950" :aria-labelledby="`${formId}-example`">
                <h3 :id="`${formId}-example`" class="m-0 text-xs font-medium">{{ t('ad.priceDialogExample') }}</h3>
                <table class="w-full border-collapse text-[13px] tabular-nums">
                    <thead>
                        <tr class="text-[0.6875rem] text-muted-color">
                            <th scope="col" class="p-0 pb-1 text-left font-normal">{{ t('ad.previewPriceItem') }}</th>
                            <th scope="col" class="p-0 pb-1 pl-3 text-right font-normal">{{ t('ad.priceDialogNow') }}</th>
                            <th scope="col" class="p-0 pb-1 pl-3 text-right font-normal">{{ t('ad.priceDialogNew') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in exampleRows" :key="row.key" :class="row.key === 'total' ? 'border-t border-(--line)' : ''">
                            <th scope="row" class="p-0 py-0.5 text-left font-normal" :class="row.key === 'total' ? 'pt-1.5 font-medium' : 'text-muted-color'">{{ row.label }}</th>
                            <td class="p-0 py-0.5 pl-3 text-right text-muted-color" :class="{ 'pt-1.5': row.key === 'total' }">{{ row.now }}</td>
                            <td class="p-0 py-0.5 pl-3 text-right" :class="row.key === 'total' ? 'pt-1.5 font-semibold text-primary' : row.now !== row.next ? 'font-medium' : ''">{{ row.next }}</td>
                        </tr>
                    </tbody>
                </table>
                <p class="m-0 text-[0.6875rem] text-muted-color">{{ t('ad.previewPriceVat') }}</p>
            </section>

            <!-- 안내 -->
            <div class="flex flex-col gap-1.5" aria-live="polite">
                <Message severity="secondary" variant="simple" size="small" :closable="false">{{ t('ad.priceDialogWhich') }}</Message>
                <Message v-for="notice in notices" :key="notice.key" :severity="notice.severity" variant="simple" size="small" :closable="false">{{ notice.text }}</Message>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <span v-if="noChange" class="mr-auto self-center text-xs text-muted-color">{{ t('ad.priceDialogNoChange') }}</span>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="schedule ? t('ad.priceDialogSaveEdit') : t('ad.priceDialogSave')" :loading="busy" :disabled="noChange" data-testid="ad-price-schedule-submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdPriceField, AdPriceRule, AdPriceSchedule, AdPriceScheduleRequest, AdPriceValues } from '~/composables/ad/api/useAdApi.types';
import UtilAdMessages from '~/utils/util-ad-messages';
import UtilAdPrice, { type AdPriceScheduledField } from '~/utils/util-ad-price';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    visible: boolean;
    /** 예약을 걸 상품(닫혀 있으면 null 일 수 있다) */
    rule?: AdPriceRule | null;
    /** 고칠 예약 — null 이면 새 예약 */
    schedule?: AdPriceSchedule | null;
    busy?: boolean;
    /** 칸에 안 붙는 서버 오류 */
    error?: string;
    /** 서버 칸 오류 — `effectiveAt`·`unitPrice`·`capacity`·`maxDays`·`maxLeadDays` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { rule: null, schedule: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: AdPriceScheduleRequest]; 'clear-error': [key: string] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const formId = useId();
const fields = UtilAdPrice.SCHEDULED_FIELDS;

const form = reactive<AdPriceScheduleRequest>({ effectiveAt: '', unitPrice: 0, capacity: null, maxDays: 1, maxLeadDays: 1 });
const submitted = ref(false);
/** 고르개의 가장 이른 순간 — 연 때 */
const minAt = ref(new Date().toISOString());

// 열 때마다 처음 값 — 고치기는 그 예약 값, 새 예약은 기본 적용 시각의 바로 앞 값
watch(
    () => props.visible,
    (open) => {
        const rule = props.rule;
        if (!open || !rule) return;
        const s = props.schedule;
        const effectiveAt = s?.effectiveAt ?? UtilAdPrice.defaultEffectiveAt();
        Object.assign(form, { effectiveAt, ...UtilAdPrice.valuesOf(rule.unit, s ?? UtilAdPrice.valuesBefore(rule, effectiveAt)) });
        submitted.value = false;
        minAt.value = new Date().toISOString();
    },
);

// 서버 칸 오류는 그 칸을 바꾸면 지운다
watch(
    () => form.effectiveAt,
    () => emit('clear-error', 'effectiveAt'),
);

const current = computed<AdPriceValues>(() => (props.rule ? UtilAdPrice.valuesOf(props.rule.unit, props.rule) : { unitPrice: 0, capacity: null, maxDays: 1, maxLeadDays: 1 }));
/** 그 시각 바로 앞 값(고치는 예약은 빼고) */
const prior = computed<AdPriceValues>(() => (props.rule ? UtilAdPrice.valuesBefore(props.rule, form.effectiveAt, props.schedule?.id ?? null) : current.value));
const draft = computed<AdPriceValues>(() => (props.rule ? UtilAdPrice.valuesOf(props.rule.unit, form) : current.value));
/** 바로 앞 값과 같으면 예약할 것이 없다(고치기는 시각만 바꿔도 된다) */
const noChange = computed(() => !!props.rule && UtilAdPrice.diff(prior.value, draft.value).length === 0 && (!props.schedule || props.schedule.effectiveAt === form.effectiveAt));

const effectiveAtError = computed(() => {
    if (props.fieldErrors.effectiveAt) return props.fieldErrors.effectiveAt;
    if (!props.rule) return '';
    const problem = UtilAdPrice.effectiveAtProblem(form.effectiveAt);
    if (problem === 'past') return t('ad.priceEffectiveAtPast');
    if (problem === 'required') return submitted.value ? t('ad.priceEffectiveAtRequired') : '';
    return UtilAdPrice.hasScheduleAt(props.rule, form.effectiveAt, props.schedule?.id ?? null) ? t('ad.priceEffectiveAtDuplicate') : '';
});

const valueText = (field: AdPriceField, value: number | null) => UtilAdMessages.priceRuleValue(t, field, value === null ? null : String(value), locale.value) ?? '';
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

function labelOf(field: AdPriceScheduledField): string {
    return field === 'unitPrice' && props.rule ? t(`ad.priceUnitPriceLabel.${props.rule.unit}`) : t(`ad.priceField.${field}`);
}

function suffixOf(field: AdPriceScheduledField): string {
    if (field === 'unitPrice') return t('ad.priceWonSuffix');
    if (field === 'capacity') return t('ad.priceCapacitySuffix');
    return t('ad.priceDaysSuffix');
}

/** `InputNumber` 는 지우는 중에 null 을 줄 수 있다 — 칸 값은 늘 정수로 둔다 */
function onNumber(field: AdPriceScheduledField, value: number | null): void {
    if (value === null) return;
    form[field] = Math.round(value);
    emit('clear-error', field);
}

/** 사업자 예상 금액 — 지금 값 · 새 값 */
const exampleRows = computed(() => {
    const unit = props.rule?.unit ?? 'DAY';
    const a = UtilAdPrice.example(unit, current.value);
    const b = UtilAdPrice.example(unit, draft.value);
    const units = (n: number) => (unit === 'POST' ? t('ad.previewPriceOnePost') : t('ad.priceDaysValue', { n }));
    return [
        { key: 'price', label: t(`ad.priceUnitPriceLabel.${unit}`), now: won(current.value.unitPrice), next: won(draft.value.unitPrice) },
        { key: 'units', label: t('ad.previewPriceUnits'), now: units(a.units), next: units(b.units) },
        { key: 'supply', label: t('ad.previewPriceSupply'), now: won(a.amount.supply), next: won(b.amount.supply) },
        { key: 'vat', label: t('ad.previewPriceVatRow'), now: won(a.amount.vat), next: won(b.amount.vat) },
        { key: 'total', label: t('ad.previewPriceTotal'), now: won(a.amount.total), next: won(b.amount.total) },
    ];
});

type Notice = { key: string; severity: 'warn' | 'info'; text: string };

const notices = computed<Notice[]>(() => {
    const rule = props.rule;
    if (!rule) return [];
    const out: Notice[] = [];
    if (draft.value.unitPrice === 0) out.push({ key: 'free', severity: 'warn', text: t('ad.priceFreeWarn') });
    const capacity = draft.value.capacity;
    if (capacity !== null && prior.value.capacity !== null && capacity < prior.value.capacity) {
        const n = UtilAdPrice.overbookedDays(rule.occupancy, capacity);
        out.push(n > 0 ? { key: 'capacity', severity: 'warn', text: t('ad.priceCapacityOverWarn', { n }) } : { key: 'capacity', severity: 'info', text: t('ad.priceCapacityDownOk') });
    }
    const at = Date.parse(form.effectiveAt);
    const later = rule.schedules.filter((s) => s.id !== props.schedule?.id && Date.parse(s.effectiveAt) > at);
    if (later.length) out.push({ key: 'later', severity: 'info', text: t('ad.priceDialogLater', { n: later.length, at: time.value.full(UtilAdPrice.sorted(later)[0]!.effectiveAt) }) });
    return out;
});

function submit(): void {
    submitted.value = true;
    if (!props.rule || props.busy || noChange.value || effectiveAtError.value) return;
    emit('submit', { effectiveAt: form.effectiveAt, ...UtilAdPrice.valuesOf(props.rule.unit, form) });
}
</script>
