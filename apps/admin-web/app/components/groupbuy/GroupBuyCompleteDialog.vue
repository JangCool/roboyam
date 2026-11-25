<template>
    <!--
        반품 완료(회수 확인 뒤) — **요청 줄마다 처리 방법과 수량**을 고른다(필수, 합 = 그 줄 반품 수량 — 예 「2개 다시 판매 + 1개 폐기」, 계약 결정 15).
        보통은 선택 상자에서 방법 하나를 골라 줄 수량 전체에 적용하고, 2개 이상인 줄만 「나눠서 처리」로 방법마다 수량을 적는다(2026-11-25 사용자 「selectbox로 선택하게」).
        다시 판매(`RESELL`)는 공구가 모집 중이고 줄 사유가 단순 변심일 때만 보인다(하자·오배송은 서버가 `GROUP_BUY_NOT_RESELLABLE` 로 거절). 폐기·공급처 반송·보관은 수량을 바꾸지 않고 기록만.
        고객발송 + 하자·오배송이면 고객이 낸 택배비 보상액(상한은 설정값 — 기본 10,000원)을 넣는다 — 운영자가 정하는 금액이라 누계가 고액 문턱 이상이면 `commerce.refund.large` 가 필요하다(결정 70).
        환불액은 서버 계산 값(`breakdown.total`)을 보이기만 하고, 보상액은 따로 더해 보인다. 서버는 부르지 않는다 — `submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('groupBuy.completeTitle')" :style="{ width: 'min(44rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="request" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('groupBuy.completeBody') }}</p>

            <div v-for="item in request.items" :key="item.id" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2">
                <div class="flex flex-wrap items-center gap-2 text-[13px]">
                    <GroupBuySkuLabel :name="item.skuName" :kind="item.kind" class="font-medium" />
                    <span class="tabular-nums">{{ t('groupBuy.qtyN', { n: item.qty }) }}</span>
                    <Tag :value="t(`groupBuy.reasonCode.${item.reasonCode}`)" :severity="item.reasonCode === 'CHANGE_OF_MIND' || item.reasonCode === 'OTHER' ? 'secondary' : 'warn'" />
                    <span class="ml-auto text-xs tabular-nums" :class="sumOf(item.id) === item.qty ? 'text-green-700 dark:text-green-400' : 'text-amber-700 dark:text-amber-400'">{{ t('groupBuy.completeSum', { sum: sumOf(item.id), qty: item.qty }) }}</span>
                </div>
                <!-- 보통은 처리 방법 하나를 골라 그 줄 수량 전체에 적용 — 2개 이상이면 「나눠서 처리」로 방법마다 수량 칸 -->
                <Select
                    :model-value="modes[item.id] ?? null"
                    :options="modeOptions(item.reasonCode, item.qty)"
                    option-label="label"
                    option-value="value"
                    :placeholder="t('groupBuy.dispositionPick')"
                    :aria-label="t('groupBuy.dispositionPickFor', { name: item.skuName })"
                    :invalid="submitted && !modes[item.id]"
                    :disabled="busy"
                    class="w-full sm:w-64"
                    @update:model-value="(v: string | null) => setMode(item.id, v)"
                />
                <div v-if="modes[item.id] === SPLIT" class="flex flex-wrap gap-3">
                    <div v-for="d in UtilGroupBuy.dispositionsFor(item.reasonCode, request.actions.resellAllowed)" :key="d" class="flex items-center gap-1.5">
                        <label :for="`${formId}-${item.id}-${d}`" class="text-[13px]">{{ t(`groupBuy.disposition.${d}`) }}</label>
                        <InputText :id="`${formId}-${item.id}-${d}`" :model-value="cell(item.id, d)" inputmode="numeric" class="w-16 text-right tabular-nums" :disabled="busy" @update:model-value="(v: string | undefined) => setCell(item.id, d, v ?? '')" />
                    </div>
                </div>
                <p v-if="!request.actions.resellAllowed && item.reasonCode === 'CHANGE_OF_MIND'" class="m-0 text-[11px] text-muted-color">{{ t('groupBuy.resellHidden') }}</p>
                <p v-if="fieldErrors[`dispositions.${item.id}`] || (submitted && sumOf(item.id) !== item.qty)" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldErrors[`dispositions.${item.id}`] || (modes[item.id] ? t('groupBuy.completeSumProblem', { qty: item.qty }) : t('groupBuy.dispositionRequired')) }}</p>
            </div>

            <LabeledField v-if="needsCompensation" :label="t('groupBuy.compensation')" :help="t('groupBuy.compensationHelp', { cap: won(request.compensationCap) })" :error="fieldErrors.compensationAmount || compProblem">
                <template #default="{ id, describedBy, invalid }">
                    <InputGroup class="w-48">
                        <InputText :id="id" v-model="compensation" inputmode="numeric" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" class="text-right tabular-nums" />
                        <InputGroupAddon>{{ t('groupBuy.wonUnit') }}</InputGroupAddon>
                    </InputGroup>
                </template>
            </LabeledField>
            <Message v-if="needsLarge" :severity="canLarge ? 'warn' : 'info'" :closable="false" size="small">{{ canLarge ? t('groupBuy.compLargeOk', { threshold: won(request.largeRefundThreshold) }) : t('groupBuy.compLargeWait', { threshold: won(request.largeRefundThreshold) }) }}</Message>

            <div class="rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                <div>{{ t('groupBuy.completeRefund', { amount: won(request.breakdown.total) }) }}<template v-if="compValue > 0"> · {{ t('groupBuy.completeComp', { amount: won(compValue) }) }}</template></div>
                <div class="text-xs text-muted-color">{{ t('groupBuy.completeDeadline') }}</div>
            </div>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('groupBuy.actComplete')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyCompleteRequest, GroupBuyDisposition, GroupBuyReasonCode, GroupBuyRequestDetail } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    visible: boolean;
    request: GroupBuyRequestDetail | null;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
    /** `commerce.refund.large` */
    canLarge?: boolean;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}), canLarge: false });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: GroupBuyCompleteRequest] }>();

const { t, locale } = useI18n();
const formId = useId();

/** 선택 상자의 「나눠서 처리」 값 — 처리 방법 코드와 겹치지 않는다 */
const SPLIT = 'SPLIT';
/** 줄 ID → 고른 처리 방법(또는 나눠서 처리) */
const modes = ref<Record<string, GroupBuyDisposition | typeof SPLIT>>({});
/** 줄 ID → 처리 방법 → 수량 글자(나눠서 처리일 때만) */
const cells = ref<Record<string, Partial<Record<GroupBuyDisposition, string>>>>({});
const compensation = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        modes.value = {};
        cells.value = {};
        compensation.value = props.request?.compensationAmount ? String(props.request.compensationAmount) : '';
        submitted.value = false;
    },
    { immediate: true },
);

function won(n: number): string {
    return UtilGroupBuy.won(n, locale.value);
}

function cell(itemId: string, d: GroupBuyDisposition): string {
    return cells.value[itemId]?.[d] ?? '';
}

function setCell(itemId: string, d: GroupBuyDisposition, value: string): void {
    cells.value = { ...cells.value, [itemId]: { ...cells.value[itemId], [d]: value } };
}

/** 선택 상자 항목 — 이 줄에 고를 수 있는 처리 방법 + (2개 이상이면) 나눠서 처리 */
function modeOptions(reason: GroupBuyReasonCode, qty: number): { value: string; label: string }[] {
    const r = props.request;
    const list = UtilGroupBuy.dispositionsFor(reason, !!r?.actions.resellAllowed).map((d) => ({ value: d, label: t(`groupBuy.disposition.${d}`) }));
    return qty > 1 ? [...list, { value: SPLIT, label: t('groupBuy.dispositionSplit') }] : list;
}

function setMode(itemId: string, value: string | null): void {
    const next = { ...modes.value };
    if (value) next[itemId] = value as GroupBuyDisposition | typeof SPLIT;
    else delete next[itemId];
    modes.value = next;
}

/** 그 줄에 정한 수량 합 — 방법 하나면 줄 수량 전체, 나눠서면 칸의 합, 안 골랐으면 0 */
function sumOf(itemId: string): number {
    const mode = modes.value[itemId];
    if (!mode) return 0;
    if (mode !== SPLIT) return props.request?.items.find((i) => i.id === itemId)?.qty ?? 0;
    return Object.values(cells.value[itemId] ?? {}).reduce((s, v) => s + (UtilGroupBuy.int(v ?? '') ?? 0), 0);
}

/** 고객발송 + 하자·오배송(운영자 부담)일 때만 보상액 칸 */
const needsCompensation = computed(() => !!props.request && props.request.collectionMethod === 'CUSTOMER_SEND' && props.request.breakdown.operatorPaysReturnFee);
const compValue = computed(() => (needsCompensation.value ? (UtilGroupBuy.int(compensation.value) ?? 0) : 0));
const compProblem = computed(() => {
    if (!needsCompensation.value || !compensation.value.trim()) return '';
    const n = UtilGroupBuy.int(compensation.value);
    if (n === null) return t('groupBuy.compInvalid');
    return props.request && n > props.request.compensationCap ? t('groupBuy.compOverCap', { cap: won(props.request.compensationCap) }) : '';
});
const needsLarge = computed(() => {
    const r = props.request;
    return !!r && compValue.value > 0 && r.largeRefundThreshold > 0 && r.adminRefundTotal + compValue.value >= r.largeRefundThreshold;
});

function submit(): void {
    submitted.value = true;
    const r = props.request;
    if (!r || props.busy || compProblem.value) return;
    if (r.items.some((i) => sumOf(i.id) !== i.qty)) return;
    const dispositions: GroupBuyCompleteRequest['dispositions'] = [];
    for (const item of r.items) {
        const mode = modes.value[item.id];
        if (mode && mode !== SPLIT) {
            dispositions.push({ requestItemId: item.id, disposition: mode, qty: item.qty });
            continue;
        }
        for (const [d, text] of Object.entries(cells.value[item.id] ?? {})) {
            const qty = UtilGroupBuy.int(text ?? '') ?? 0;
            if (qty > 0) dispositions.push({ requestItemId: item.id, disposition: d as GroupBuyDisposition, qty });
        }
    }
    emit('submit', { dispositions, compensationAmount: compValue.value, expectedAmount: r.breakdown.total + compValue.value });
}
</script>
