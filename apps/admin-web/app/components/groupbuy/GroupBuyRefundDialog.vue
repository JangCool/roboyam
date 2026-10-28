<template>
    <!--
        운영 환불(요청 없이 — 계약 결정 32·53·70·71). 발송 전 주문은 줄·수량을 고르고(발송 전 취소와 같다), 배송완료 주문은 금액만 넣는다(회수 없음 — 보상·가격 조정). SHIPPED 는 부르는 쪽이 열지 않는다.
        「사업자 귀책」을 켜면 서버가 배송비 재부과·반품비를 적용하지 않는다. 금액은 서버 미리 보기(`preview`)를 보여 주기만 한다 — 칸을 고치면 「미리 보기」를 다시 눌러야 환불할 수 있다.
        운영자가 금액을 정한 환불의 누계가 고액 문턱(설정값, 0 = 끔) 이상이면 `commerce.refund.large` 가 필요하고, 없으면 승인 대기로 올라간다(서버가 정한다 — 화면은 안내만).
        서버는 부르지 않는다 — `preview`·`submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('groupBuy.refundTitle')" :style="{ width: 'min(40rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="order" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ order.actions.refundMode === 'LINES' ? t('groupBuy.refundLinesBody') : t('groupBuy.refundAmountBody') }}</p>

            <div v-if="order.actions.refundMode === 'LINES'" class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">{{ t('groupBuy.refundLines') }}</span>
                <ul class="m-0 flex list-none flex-col rounded-lg border border-(--line) p-0">
                    <li v-for="item in order.items" :key="item.id" class="flex items-center gap-3 border-b border-(--line) px-3 py-2 text-[13px] last:border-b-0">
                        <span class="min-w-0 flex-1">{{ item.optionName ?? t('groupBuy.noOption') }} <span class="text-xs text-muted-color">{{ t('groupBuy.refundLeft', { n: left(item.id) }) }}</span></span>
                        <InputText :model-value="qty[item.id] ?? ''" inputmode="numeric" :aria-label="t('groupBuy.refundQtyFor', { name: item.optionName ?? t('groupBuy.noOption') })" :disabled="busy || left(item.id) === 0" class="w-20 text-right tabular-nums" @update:model-value="(v: string | undefined) => setQty(item.id, v ?? '')" />
                    </li>
                </ul>
                <p v-if="fieldErrors.lines || linesProblem" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.lines || linesProblem }}</p>
            </div>
            <LabeledField v-else :label="t('groupBuy.refundAmount')" :help="t('groupBuy.refundRemaining', { amount: won(order.amounts.remaining) })" :error="fieldErrors.amount">
                <template #default="{ id, describedBy, invalid }">
                    <InputGroup class="w-56">
                        <InputText :id="id" :model-value="amount" inputmode="numeric" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" class="text-right tabular-nums" @update:model-value="(v: string | undefined) => setAmount(v ?? '')" />
                        <InputGroupAddon>{{ t('groupBuy.wonUnit') }}</InputGroupAddon>
                    </InputGroup>
                </template>
            </LabeledField>

            <div class="flex items-start gap-2">
                <Checkbox :model-value="businessFault" binary :input-id="faultId" :disabled="busy" class="mt-0.5" @update:model-value="(v: boolean) => setFault(v)" />
                <label :for="faultId" class="text-[13px]"><span class="font-medium">{{ t('groupBuy.businessFault') }}</span><span class="block text-xs text-muted-color">{{ t('groupBuy.businessFaultHelp') }}</span></label>
            </div>

            <div class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2">
                <div class="flex items-center gap-2">
                    <span class="text-sm font-medium">{{ t('groupBuy.refundPreview') }}</span>
                    <Button type="button" :label="t('groupBuy.refundPreviewRun')" icon="pi pi-calculator" size="small" severity="secondary" variant="outlined" class="ml-auto" :loading="previewing" :disabled="busy || !canPreview" @click="runPreview" />
                </div>
                <template v-if="preview && !stale">
                    <p class="m-0 text-[13px]">{{ t('groupBuy.refundPreviewLine', { item: won(preview.itemRefund), ship: won(preview.shippingRefund), reship: won(preview.reshippingFee) }) }}</p>
                    <p class="m-0 text-base font-semibold tabular-nums">{{ t('groupBuy.refundPreviewTotal', { amount: won(preview.total), left: won(preview.remainingAfter) }) }}</p>
                    <Message v-if="preview.needsLarge" :severity="canLarge ? 'warn' : 'info'" :closable="false" size="small">
                        {{ canLarge ? t('groupBuy.refundLargeOk', { total: won(preview.adminRefundTotalAfter), threshold: won(preview.largeRefundThreshold) }) : t('groupBuy.refundLargeWait', { total: won(preview.adminRefundTotalAfter), threshold: won(preview.largeRefundThreshold) }) }}
                    </Message>
                </template>
                <p v-else class="m-0 text-xs text-muted-color">{{ stale && preview ? t('groupBuy.refundPreviewStale') : t('groupBuy.refundPreviewHint') }}</p>
                <p v-if="previewError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ previewError }}</p>
            </div>

            <LabeledField :label="t('payment.reason.label')" :hint="t('payment.reason.hint', { min: UtilGroupBuy.REASON.min, max: UtilGroupBuy.REASON.max })" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" rows="2" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :placeholder="t('groupBuy.refundReasonPlaceholder')" fluid />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('groupBuy.refundSubmit')" severity="danger" :loading="busy" :disabled="!preview || stale" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyAdminRefundRequest, GroupBuyOrderDetail, GroupBuyRefundPreview } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    visible: boolean;
    order: GroupBuyOrderDetail | null;
    preview?: GroupBuyRefundPreview | null;
    previewing?: boolean;
    previewError?: string;
    busy?: boolean;
    error?: string;
    /** 서버 칸 오류(`lines`·`amount`·`reason`) */
    fieldErrors?: Record<string, string>;
    /** `commerce.refund.large` */
    canLarge?: boolean;
}

const props = withDefaults(defineProps<Props>(), { preview: null, previewing: false, previewError: '', busy: false, error: '', fieldErrors: () => ({}), canLarge: false });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; preview: [body: GroupBuyAdminRefundRequest]; submit: [body: GroupBuyAdminRefundRequest] }>();

const { t, locale } = useI18n();
const formId = useId();
const faultId = useId();

const qty = ref<Record<string, string>>({});
const amount = ref('');
const businessFault = ref(false);
const reason = ref('');
const submitted = ref(false);
/** 미리 본 뒤 칸을 고쳤다 — 다시 미리 봐야 환불할 수 있다 */
const stale = ref(true);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        qty.value = Object.fromEntries((props.order?.items ?? []).map((i) => [i.id, '']));
        amount.value = '';
        businessFault.value = false;
        reason.value = '';
        submitted.value = false;
        stale.value = true;
    },
    { immediate: true },
);
watch(
    () => props.preview,
    (p) => {
        if (p) stale.value = false;
    },
);

function setQty(id: string, value: string): void {
    qty.value = { ...qty.value, [id]: value };
    stale.value = true;
}
function setAmount(value: string): void {
    amount.value = value;
    stale.value = true;
}
function setFault(value: boolean): void {
    businessFault.value = value;
    stale.value = true;
}

function won(n: number): string {
    return UtilGroupBuy.won(n, locale.value);
}

function left(itemId: string): number {
    const item = props.order?.items.find((i) => i.id === itemId);
    return item ? item.qty - item.canceledQty - item.returnedQty : 0;
}

const linesProblem = computed(() => {
    for (const [id, text] of Object.entries(qty.value)) {
        if (!text.trim()) continue;
        const n = UtilGroupBuy.int(text);
        if (n === null) return t('groupBuy.refundQtyInvalid');
        if (n > left(id)) return t('groupBuy.refundQtyTooMany');
    }
    return '';
});

function body(): GroupBuyAdminRefundRequest {
    if (props.order?.actions.refundMode === 'LINES') {
        const lines = Object.entries(qty.value)
            .map(([itemId, text]) => ({ itemId, qty: UtilGroupBuy.int(text) ?? 0 }))
            .filter((l) => l.qty > 0);
        return { lines, amount: null, businessFault: businessFault.value, reason: UtilGroupBuy.reasonText(reason.value) };
    }
    return { lines: null, amount: UtilGroupBuy.int(amount.value), businessFault: businessFault.value, reason: UtilGroupBuy.reasonText(reason.value) };
}

const canPreview = computed(() => {
    if (props.order?.actions.refundMode === 'LINES') return !linesProblem.value && (body().lines?.length ?? 0) > 0;
    return (UtilGroupBuy.int(amount.value) ?? 0) > 0;
});

function runPreview(): void {
    if (canPreview.value) emit('preview', body());
}

const reasonError = computed(() => {
    if (props.fieldErrors.reason) return props.fieldErrors.reason;
    if (!submitted.value) return '';
    return UtilGroupBuy.reasonOk(reason.value) ? '' : t('payment.reason.tooShort', { min: UtilGroupBuy.REASON.min });
});

function submit(): void {
    submitted.value = true;
    if (props.busy || stale.value || !props.preview || !UtilGroupBuy.reasonOk(reason.value)) return;
    emit('submit', body());
}
</script>
