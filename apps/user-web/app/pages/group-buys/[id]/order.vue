<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-4" data-testid="group-buy-order-page">
        <NuxtLink :to="UtilGroupBuy.detailPath(id)" class="inline-flex w-fit items-center gap-1 text-sm text-muted-color hover:underline">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" /> {{ gb?.title ?? term('GROUP_BUY') }}
        </NuxtLink>
        <h1 class="m-0 text-lg font-bold">{{ t('groupBuy.orderTitle') }}</h1>

        <div v-if="status === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-32 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('groupBuy.notFound')" :description="loadError">
            <Button as="router-link" :to="UtilGroupBuy.LIST_PATH" :label="t('groupBuy.backToList')" severity="secondary" variant="outlined" />
        </ErrorState>
        <ErrorState v-else-if="status === 'error' || !gb" :title="t('groupBuy.detailFailed')" :description="loadError" retry @retry="load()" />

        <!-- 주문할 수 없는 공구(서버 판정) — 결제 중인 주문이 이 화면에 있으면 그것을 먼저 보인다 -->
        <EmptyState v-else-if="!gb.actions.order && !started" :title="t('groupBuy.orderBlocked')" :description="gb.purchase.reason ? t(`groupBuy.reason.${gb.purchase.reason}`) : ''" icon="pi pi-ban" data-testid="group-buy-order-blocked">
            <Button v-if="gb.purchase.reason === 'ALREADY_JOINED' && gb.myOrderNo" as="router-link" :to="UtilGroupBuy.myOrderPath(gb.myOrderNo)" :label="t('groupBuy.seeMyOrder')" />
            <Button as="router-link" :to="UtilGroupBuy.detailPath(gb.id)" :label="t('groupBuy.backToDetail')" severity="secondary" variant="outlined" />
        </EmptyState>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <!-- 처음부터 보이는 판매조건·배송비·반품비·청약철회 제한(결정 57·59) -->
            <div class="flex flex-col gap-1">
                <GroupBuySalesTerms :gb="gb" />
                <!-- 상품정보제공고시(결정 87) — 주문서를 떠나지 않게 시트로 -->
                <Button
                    v-if="gb.notices.length > 0"
                    type="button"
                    variant="link"
                    size="small"
                    icon="pi pi-list"
                    class="w-fit px-0!"
                    :label="t('groupBuy.productInfoOpen')"
                    aria-haspopup="dialog"
                    data-testid="group-buy-order-product-info"
                    @click="noticesOpen = true"
                />
            </div>
            <GroupBuyShippingNotice :shipping="gb.shipping" :withdrawal-restriction="gb.withdrawalRestriction" />

            <!-- 1. 옵션 × 수량 — 기본 선택 없음 -->
            <section :aria-labelledby="`${uid}-items`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-buy-order-lines">
                <h2 :id="`${uid}-items`" class="m-0 mb-1 text-base font-semibold">{{ gb.options.length > 0 ? t('groupBuy.linesTitle') : t('groupBuy.qtyTitle') }}</h2>
                <p class="m-0 mb-3 text-xs text-muted-color">
                    {{ gb.options.length > 0 ? t('groupBuy.linesHelp') : t('groupBuy.qtyHelp') }}
                    <template v-if="gb.maxQtyPerUser !== null"> · {{ t('groupBuy.termsLimitValue', { count: gb.maxQtyPerUser }) }}</template>
                </p>
                <ul class="m-0 flex list-none flex-col gap-3 p-0">
                    <li v-for="(line, index) in lines" :key="line.key" class="flex flex-wrap items-start gap-2 rounded-lg border border-surface p-3">
                        <LabeledField v-if="gb.options.length > 0" class="min-w-0 flex-1 basis-56" :label="t('groupBuy.lineOption', { n: index + 1 })" :error="fieldError(`items[${index}].optionId`)">
                            <template #default="{ id: fieldId, describedBy, invalid }">
                                <Select
                                    v-model="line.optionId"
                                    :label-id="fieldId"
                                    :options="optionChoices(index)"
                                    option-label="label"
                                    option-value="value"
                                    option-disabled="disabled"
                                    :placeholder="t('groupBuy.optionPlaceholder')"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    :disabled="locked"
                                    fluid
                                    :data-testid="`group-buy-line-option-${index}`"
                                />
                            </template>
                        </LabeledField>
                        <LabeledField class="w-36" :label="t('groupBuy.lineQty')" :hint="remainingHint(line.optionId)" :error="fieldError(`items[${index}].qty`)">
                            <template #default="{ id: fieldId, describedBy, invalid }">
                                <InputNumber
                                    v-model="line.qty"
                                    :input-id="fieldId"
                                    :min="1"
                                    :max="999"
                                    show-buttons
                                    button-layout="horizontal"
                                    :placeholder="t('groupBuy.qtyPlaceholder')"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    :disabled="locked"
                                    fluid
                                    :data-testid="`group-buy-line-qty-${index}`"
                                />
                            </template>
                        </LabeledField>
                        <Button
                            v-if="lines.length > 1"
                            type="button"
                            icon="pi pi-times"
                            severity="secondary"
                            variant="text"
                            class="mt-6"
                            :aria-label="t('groupBuy.lineRemove', { n: index + 1 })"
                            :disabled="locked"
                            @click="removeLine(index)"
                        />
                    </li>
                </ul>
                <Button v-if="canAddLine" type="button" class="mt-3" :label="t('groupBuy.lineAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="locked" data-testid="group-buy-line-add" @click="addLine" />
                <p v-if="fieldError('items')" class="m-0 mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ fieldError('items') }}</p>
            </section>

            <!-- 2. 금액(서버 견적) -->
            <section :aria-labelledby="`${uid}-amount`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" aria-live="polite" data-testid="group-buy-quote">
                <h2 :id="`${uid}-amount`" class="m-0 mb-2 text-base font-semibold">{{ t('groupBuy.amountTitle') }}</h2>
                <p v-if="!linesComplete" class="m-0 text-sm text-muted-color">{{ t('groupBuy.quotePending') }}</p>
                <p v-else-if="quoteBusy && !quote" class="m-0 text-sm text-muted-color" role="status">{{ t('groupBuy.quoting') }}</p>
                <Message v-else-if="quoteError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ quoteError }}</Message>
                <dl v-else-if="quote" class="m-0 grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1 text-sm" :class="quoteBusy ? 'opacity-60' : ''">
                    <dt class="text-muted-color">{{ t('groupBuy.itemsAmount') }}</dt>
                    <dd class="m-0 text-right tabular-nums">{{ UtilGroupBuyMessages.won(t, quote.itemsAmount) }}</dd>
                    <dt class="text-muted-color">{{ t('groupBuy.shippingFee') }}</dt>
                    <dd class="m-0 text-right tabular-nums">{{ quote.shippingFee === 0 ? t('groupBuy.freeShipping') : UtilGroupBuyMessages.won(t, quote.shippingFee) }}</dd>
                    <dt class="border-t border-surface pt-1 font-semibold">{{ t('groupBuy.totalAmount') }}</dt>
                    <dd class="m-0 border-t border-surface pt-1 text-right text-lg font-bold tabular-nums text-primary" data-testid="group-buy-total">{{ UtilGroupBuyMessages.won(t, quote.totalAmount) }}</dd>
                </dl>
                <p v-if="quote && quote.freeShippingRemaining !== null && !quoteError" class="m-0 mt-2 text-xs text-muted-color">{{ t('groupBuy.freeShippingRemaining', { amount: UtilGroupBuyMessages.won(t, quote.freeShippingRemaining) }) }}</p>
            </section>

            <!-- 3. 배송지 -->
            <section :aria-labelledby="`${uid}-address`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                <h2 :id="`${uid}-address`" class="m-0 mb-1 text-base font-semibold">{{ t('groupBuy.addressTitle') }}</h2>
                <p class="m-0 mb-3 text-xs text-muted-color">{{ t('groupBuy.addressHelp') }}</p>
                <GroupBuyAddressFields v-model="address" :errors="addressErrors" :disabled="locked" />
            </section>

            <!-- 4. 결제 안내 · 동의 3종(기본 체크 없음) -->
            <section :aria-labelledby="`${uid}-agree`" class="flex flex-col gap-3 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                <h2 :id="`${uid}-agree`" class="m-0 text-base font-semibold">{{ t('groupBuy.payTitle') }}</h2>
                <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-sm leading-relaxed">
                    <li>{{ t('groupBuy.payCardOnly') }}</li>
                    <li>{{ t('groupBuy.payEscrow') }}</li>
                    <li>{{ t('groupBuy.payPrepaidCard') }}</li>
                    <li>{{ t('groupBuy.payHold', { minutes: 5 }) }}</li>
                    <li>{{ t('groupBuy.payDispute') }}</li>
                </ul>
                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" :disabled="locked">
                    <legend class="mb-1 text-sm font-medium">{{ t('groupBuy.agreeLegend') }}</legend>
                    <div v-for="item in agreementItems" :key="item.key" class="flex flex-col gap-0.5">
                        <label class="flex cursor-pointer items-start gap-2 text-sm">
                            <Checkbox v-model="agreements[item.key]" :input-id="`${uid}-agree-${item.key}`" binary class="mt-0.5" :invalid="!!fieldError(`agreements.${item.key}`)" :data-testid="`group-buy-agree-${item.key}`" />
                            <span class="min-w-0">
                                <span class="font-medium">{{ item.label }}</span>
                                <span class="block text-xs leading-relaxed text-muted-color">{{ item.help }}</span>
                            </span>
                        </label>
                        <p v-if="fieldError(`agreements.${item.key}`)" class="m-0 pl-7 text-xs text-red-600 dark:text-red-400" role="alert">{{ fieldError(`agreements.${item.key}`) }}</p>
                    </div>
                </fieldset>
            </section>

            <!-- 결제 중(선점) — 창을 닫았거나 실패했으면 다시 열기 -->
            <section v-if="started" class="flex flex-col gap-2 rounded-xl border border-primary bg-primary-50 p-4 dark:bg-primary-950/30" aria-live="polite" data-testid="group-buy-order-started">
                <p class="m-0 text-sm font-semibold">
                    {{ t('groupBuy.startedTitle', { orderNo: started.orderNo }) }}
                </p>
                <p v-if="!holdExpired" class="m-0 text-sm tabular-nums" data-testid="group-buy-hold-left">
                    {{ t('groupBuy.holdLeft', { time: UtilTime.mmss(hold.remaining.value) }) }}
                </p>
                <p v-if="pay.note.value" class="m-0 text-sm text-muted-color">{{ t(`groupBuy.payNote.${pay.note.value}`) }}</p>
                <Message v-if="holdExpired" severity="warn" size="small" :closable="false">{{ t('groupBuy.holdExpired') }}</Message>
                <Message v-if="resultText" severity="warn" size="small" :closable="false" role="alert">{{ resultText }}</Message>
                <Message v-if="pay.completeError.value || reopenError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ pay.completeError.value || reopenError }}</Message>
                <div class="flex flex-wrap gap-2">
                    <Button
                        v-if="!holdExpired && !pay.result.value"
                        type="button"
                        :label="t('groupBuy.payReopen')"
                        icon="pi pi-credit-card"
                        :loading="reopenBusy || pay.busy.value"
                        :disabled="pay.fakeOpen.value"
                        data-testid="group-buy-pay-reopen"
                        @click="reopen"
                    />
                    <Button v-if="holdExpired" type="button" :label="t('groupBuy.orderAgain')" icon="pi pi-refresh" @click="restart" />
                    <Button as="router-link" :to="UtilGroupBuy.myOrderPath(started.orderNo)" :label="t('groupBuy.seeOrder')" severity="secondary" variant="outlined" />
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('groupBuy.startedLocked') }}</p>
            </section>

            <!-- 제출 -->
            <div v-if="!started" class="flex flex-col gap-2">
                <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false" role="alert" data-testid="group-buy-order-error">{{ formError }}</Message>
                <Button
                    type="submit"
                    size="large"
                    :label="quote && linesComplete ? t('groupBuy.payButton', { amount: UtilGroupBuyMessages.won(t, quote.totalAmount) }) : t('groupBuy.payButtonPlain')"
                    icon="pi pi-lock"
                    :loading="submitBusy"
                    :disabled="quoteBusy"
                    data-testid="group-buy-pay"
                />
            </div>
        </form>

        <AppSheet v-if="gb && gb.notices.length > 0" v-model:visible="noticesOpen" :title="t('groupBuy.productInfoTitle')" :description="pickedOptionIds.length > 0 ? t('groupBuy.productInfoSheetHelp') : ''" wide>
            <div class="px-5 pt-3">
                <GroupBuyProductNotices :notices="gb.notices" :options="gb.options" :priority-option-ids="pickedOptionIds" heading-tag="h4" />
            </div>
        </AppSheet>

        <PaymentFakeCheckoutDialog
            :visible="pay.fakeOpen.value"
            :checkout="pay.target.value?.checkout ?? null"
            :expires-at="pay.target.value?.expiresAt ?? ''"
            :busy="pay.fakeBusy.value"
            :error="pay.fakeError.value"
            @submit="pay.submitFake"
            @close="pay.closeFake"
        />
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyAddressInput, GroupBuyDetail, GroupBuyOrderCreated, GroupBuyOrderLine, GroupBuyQuote } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';
import UtilIdempotency from '~/utils/util-idempotency';
import UtilTime from '#layers/display-time/utils/util-time';

/**
 * 공동구매 주문서 `/group-buys/{id}/order` — 로그인 필요. 계약 commerce-groupbuy 화면 목록 · 결정 23·41·57·59·63·72.
 * - **옵션 여러 줄 × 수량**(같은 옵션 두 줄 금지 — 고른 옵션은 다른 줄 목록에서 빠진다, 옵션 없는 공구는 수량만). 옵션·수량·동의 모두 기본 선택 없음(다크패턴 금지).
 * - 판매조건 옆 「상품정보제공고시 보기」 → 시트(결정 87 — 고른 옵션 줄이 있으면 그 옵션들의 양식을 먼저 펼친다).
 * - 판매조건·배송비·반품비·발송 예정·청약철회 제한·정상가 기준을 처음부터 보인다. 금액은 서버 견적(`quote`)만 — 화면은 더하지 않는다.
 * - 「결제하기」 → 화면 검사(미리 알림) → 본인인증 게이트(`GROUP_BUY`) → 주문 API(`expectedAmount` + 멱등 키) → 결제창(`useGroupBuyCheckout`).
 *   `409 GROUP_BUY_AMOUNT_CHANGED` 면 결제창 대신 「금액이 바뀌었어요」 확인 → 새 견적으로 다시 보낸다.
 * - 선점 5분(`reservedUntil`) 남은 시간을 보이고, 창을 닫았거나 실패했으면 「결제 다시 열기」(남은 선점 1분 미만이면 서버가 `GROUP_BUY_HOLD_EXPIRED`).
 * - 결제를 받아들이면 주문 상세로 간다. 모바일 리디렉션은 주문 상세로 돌아오고 거기서 이어 간다.
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
const { term } = useSiteTerms();
const route = useRoute();
const router = useRouter();
const api = useGroupBuyApi();
const orderApi = useGroupBuyOrderApi();
const gate = useIdentityGate();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const uid = useId();

const id = computed(() => Number(route.params.id));
const gb = ref<GroupBuyDetail | null>(null);
const status = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const loadError = ref('');
useSeoMeta({ title: () => t('groupBuy.orderTitle'), robots: 'noindex' });

/** @param keepLines 다시 주문할 때 — 고른 줄을 둔다 */
async function load(keepLines = false): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        gb.value = await api.get(id.value);
        if (!keepLines || lines.value.length === 0) resetLines();
        status.value = 'ready';
    } catch (e) {
        loadError.value = UtilGroupBuyMessages.error(t, e);
        status.value = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error';
    }
}

/* ── 옵션 × 수량 ── */

interface Line {
    key: number;
    optionId: number | null;
    qty: number | null;
}
let lineKey = 0;
const lines = ref<Line[]>([]);

function resetLines(): void {
    lines.value = [{ key: ++lineKey, optionId: null, qty: null }];
}

const canAddLine = computed(() => !!gb.value && gb.value.options.length > lines.value.length);

function addLine(): void {
    if (canAddLine.value) lines.value.push({ key: ++lineKey, optionId: null, qty: null });
}

function removeLine(index: number): void {
    lines.value.splice(index, 1);
}

/** 그 줄에서 고를 수 있는 옵션 — 다른 줄이 고른 옵션은 뺀다(같은 옵션 두 줄 금지), 품절은 이유와 함께 꺼 둔다 */
function optionChoices(index: number) {
    const g = gb.value;
    if (!g) return [];
    const taken = new Set(lines.value.filter((_, i) => i !== index).map((l) => l.optionId));
    return g.options
        .filter((o) => !taken.has(o.id))
        .map((o) => ({
            value: o.id,
            label: o.stockState === 'AVAILABLE' ? `${o.name} · ${UtilGroupBuyMessages.won(t, o.unitPrice)}` : `${o.name} · ${t(`groupBuy.stock.${o.stockState}`)}`,
            disabled: o.stockState !== 'AVAILABLE',
        }));
}

function remainingHint(optionId: number | null): string {
    const g = gb.value;
    if (!g) return '';
    if (g.options.length === 0) return t('groupBuy.optionRemaining', { count: g.remainingQty });
    const option = g.options.find((o) => o.id === optionId);
    return option ? t('groupBuy.optionRemaining', { count: option.remainingQty }) : '';
}

const linesComplete = computed(() => {
    const g = gb.value;
    if (!g || lines.value.length === 0) return false;
    return lines.value.every((l) => (g.options.length === 0 || l.optionId !== null) && l.qty !== null && l.qty >= 1);
});

/* ── 상품정보제공고시 시트(결정 87) — 고른 옵션의 양식을 먼저 ── */

const noticesOpen = ref(false);
const pickedOptionIds = computed(() => lines.value.map((l) => l.optionId).filter((optionId): optionId is number => optionId !== null));

const orderLines = computed<GroupBuyOrderLine[]>(() => lines.value.map((l) => ({ optionId: gb.value?.options.length ? l.optionId : null, qty: l.qty ?? 0 })));

/* ── 견적(서버) ── */

const quote = ref<GroupBuyQuote | null>(null);
const quoteBusy = ref(false);
const quoteError = ref('');
let quoteSeq = 0;
let quoteTimer: ReturnType<typeof setTimeout> | null = null;

async function requote(): Promise<GroupBuyQuote | null> {
    const g = gb.value;
    if (!g || !linesComplete.value) {
        quote.value = null;
        return null;
    }
    const mine = ++quoteSeq;
    quoteBusy.value = true;
    quoteError.value = '';
    try {
        const res = await api.quote(g.id, { items: orderLines.value });
        if (mine !== quoteSeq) return null;
        quote.value = res;
        clearItemErrors();
        return res;
    } catch (e) {
        if (mine !== quoteSeq) return null;
        quote.value = null;
        if (e instanceof ApiError && e.status === 400 && e.fields) fieldErrors.value = { ...fieldErrors.value, ...e.fields };
        quoteError.value = UtilGroupBuyMessages.error(t, e);
        return null;
    } finally {
        if (mine === quoteSeq) quoteBusy.value = false;
    }
}

watch(
    () => JSON.stringify(orderLines.value),
    () => {
        if (quoteTimer) clearTimeout(quoteTimer);
        if (!linesComplete.value) {
            quote.value = null;
            quoteError.value = '';
            return;
        }
        quoteTimer = setTimeout(() => void requote(), 300);
    },
);
onBeforeUnmount(() => {
    if (quoteTimer) clearTimeout(quoteTimer);
    quoteSeq++;
});

/* ── 배송지 · 동의 ── */

const address = ref<GroupBuyAddressInput>({ recipient: '', phone: '', postcode: '', address1: '', address2: '', memo: '' });
type AgreementKey = 'purchaseTerms' | 'refundPolicy' | 'privacy';
const agreements = reactive<Record<AgreementKey, boolean>>({ purchaseTerms: false, refundPolicy: false, privacy: false });
const agreementItems = computed(() =>
    (['purchaseTerms', 'refundPolicy', 'privacy'] as const).map((key) => ({ key, label: t(`groupBuy.agree.${key}`), help: t(`groupBuy.agreeHelp.${key}`) })),
);

/* ── 칸 오류 — 서버 `fields`(점·대괄호 경로) + 화면 미리 검사 ── */

const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');

function fieldError(key: string): string {
    return fieldErrors.value[key] ?? '';
}

function clearItemErrors(): void {
    fieldErrors.value = Object.fromEntries(Object.entries(fieldErrors.value).filter(([k]) => !k.startsWith('items')));
}

const addressErrors = computed(() => {
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(fieldErrors.value)) if (k.startsWith('address.')) out[k.slice('address.'.length)] = v;
    return out;
});

/** 화면 미리 검사(편의 — 판정은 서버) */
function precheck(): Record<string, string> {
    const g = gb.value!;
    const out: Record<string, string> = {};
    lines.value.forEach((l, index) => {
        if (g.options.length > 0 && l.optionId === null) out[`items[${index}].optionId`] = t('groupBuy.errOption');
        if (l.qty === null || l.qty < 1) out[`items[${index}].qty`] = t('groupBuy.errQty');
    });
    const a = address.value;
    if (!a.recipient.trim()) out['address.recipient'] = t('groupBuy.errRecipient');
    if (!a.phone.trim()) out['address.phone'] = t('groupBuy.errPhone');
    if (!a.postcode.trim()) out['address.postcode'] = t('groupBuy.errPostcode');
    if (!a.address1.trim()) out['address.address1'] = t('groupBuy.errAddress1');
    for (const key of ['purchaseTerms', 'refundPolicy', 'privacy'] as const) if (!agreements[key]) out[`agreements.${key}`] = t('groupBuy.errAgree');
    return out;
}

/* ── 주문 → 결제창 ── */

const submitBusy = ref(false);
let idempotencyKey = UtilIdempotency.newKey();
const started = ref<GroupBuyOrderCreated | null>(null);
const locked = computed(() => submitBusy.value || started.value !== null);

const holdEndsAt = ref(0);
const hold = useCountdown(holdEndsAt);
const holdExpired = computed(() => started.value !== null && !hold.active.value);

const resultText = ref('');
const pay = useGroupBuyCheckout(onPaid);

function onPaid(result: PaymentResult): void {
    const current = started.value;
    if (!current) return;
    if (result.accepted) {
        toast.success(t('groupBuy.paidToast'));
        void router.push(UtilGroupBuy.myOrderPath(current.orderNo));
        return;
    }
    resultText.value = result.anomaly ? t(`groupBuy.payAnomaly.${result.anomaly}`) : t('groupBuy.payNotAccepted');
}

async function submit(): Promise<void> {
    const g = gb.value;
    if (!g || submitBusy.value || started.value) return;
    formError.value = '';
    const local = precheck();
    fieldErrors.value = local;
    if (Object.keys(local).length > 0) {
        formError.value = t('groupBuy.errCheck');
        return;
    }
    submitBusy.value = true;
    try {
        const current = quote.value ?? (await requote());
        if (!current) return;
        if (!(await gate.ensure({ purpose: 'GROUP_BUY' }))) return;
        await place(g, current.totalAmount);
    } finally {
        submitBusy.value = false;
    }
}

async function place(g: GroupBuyDetail, expectedAmount: number): Promise<void> {
    try {
        const created = await api.order(g.id, { items: orderLines.value, address: { ...address.value }, agreements: { ...agreements }, expectedAmount }, idempotencyKey);
        idempotencyKey = UtilIdempotency.newKey();
        started.value = created;
        holdEndsAt.value = Date.parse(created.reservedUntil);
        resultText.value = '';
        pay.open(created.payment);
    } catch (e) {
        // 서버가 판정해 거절(4xx)했으면 새 키 — 같은 키로 다시 보내면 같은 거절이 돌아온다
        if (e instanceof ApiError && e.status >= 400 && e.status < 500) idempotencyKey = UtilIdempotency.newKey();
        if (e instanceof ApiError && e.code === 'GROUP_BUY_AMOUNT_CHANGED') return amountChanged(g, e);
        if (e instanceof ApiError && e.status === 400 && e.fields && Object.keys(e.fields).length) {
            fieldErrors.value = e.fields;
            formError.value = t('groupBuy.errCheck');
            return;
        }
        formError.value = UtilGroupBuyMessages.error(t, e);
    }
}

/** 견적과 합계가 달라졌다(결정 72) — 결제창 대신 확인, 새 견적으로 다시 */
async function amountChanged(g: GroupBuyDetail, e: ApiError): Promise<void> {
    const before = Number(e.extraText('expectedAmount') ?? quote.value?.totalAmount ?? 0);
    const fresh = await requote();
    if (!fresh) {
        formError.value = e.message;
        return;
    }
    const ok = await confirm({
        title: t('groupBuy.amountChangedTitle'),
        message: t('groupBuy.amountChangedText', { before: UtilGroupBuyMessages.won(t, before), after: UtilGroupBuyMessages.won(t, fresh.totalAmount) }),
        confirmLabel: t('groupBuy.amountChangedConfirm', { amount: UtilGroupBuyMessages.won(t, fresh.totalAmount) }),
    });
    if (!ok) {
        formError.value = t('groupBuy.amountChangedStopped');
        return;
    }
    if (!(await gate.ensure({ purpose: 'GROUP_BUY' }))) return;
    await place(g, fresh.totalAmount);
}

/* ── 결제 다시 열기(결정 63) ── */

const reopenBusy = ref(false);
const reopenError = ref('');

async function reopen(): Promise<void> {
    const current = started.value;
    if (!current || reopenBusy.value || pay.busy.value) return;
    reopenBusy.value = true;
    reopenError.value = '';
    try {
        const target = await orderApi.reopenPayment(current.orderNo);
        holdEndsAt.value = Date.parse(target.expiresAt);
        pay.open(target);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'GROUP_BUY_HOLD_EXPIRED') hold.stop();
        reopenError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        reopenBusy.value = false;
    }
}

/** 선점이 끝났다 — 처음부터 다시(같은 칸 값은 둔다, 동의는 다시 받는다) */
function restart(): void {
    started.value = null;
    pay.reset(null);
    reopenError.value = '';
    resultText.value = '';
    agreements.purchaseTerms = false;
    agreements.refundPolicy = false;
    agreements.privacy = false;
    void load(true).then(() => void requote());
}

void load();
</script>
