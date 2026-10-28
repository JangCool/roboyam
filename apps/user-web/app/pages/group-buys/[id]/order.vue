<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-4" data-testid="group-buy-order-page">
        <NuxtLink :to="detailLink" class="inline-flex w-fit items-center gap-1 text-sm text-muted-color hover:underline">
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

        <!-- 고른 목록이 없거나 깨졌다(결정 88 — 고르는 곳은 상세) -->
        <EmptyState
            v-else-if="itemsState !== 'ok' && !started"
            :title="itemsState === 'broken' ? t('groupBuy.orderBrokenItems') : t('groupBuy.orderNoItems')"
            :description="itemsState === 'broken' ? t('groupBuy.orderBrokenItemsText') : t('groupBuy.orderNoItemsText')"
            icon="pi pi-shopping-cart"
            data-testid="group-buy-order-no-items"
        >
            <Button as="router-link" :to="UtilGroupBuy.detailPath(gb.id)" :label="t('groupBuy.repick')" />
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

            <!-- 1. 주문 상품 — 상세에서 고른 목록을 확인만(바꾸려면 상세로 — 결정 88) -->
            <section :aria-labelledby="`${uid}-items`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-buy-order-lines">
                <div class="mb-1 flex flex-wrap items-center justify-between gap-2">
                    <h2 :id="`${uid}-items`" class="m-0 text-base font-semibold">{{ t('groupBuy.itemsTitle') }}</h2>
                    <Button v-if="!started" as="router-link" :to="detailLink" :label="t('groupBuy.repick')" icon="pi pi-pencil" size="small" severity="secondary" variant="text" data-testid="group-buy-repick" />
                </div>
                <p class="m-0 text-xs text-muted-color">
                    {{ t('groupBuy.orderItemsHelp') }}
                    <template v-if="gb.maxQtyPerUser !== null"> · {{ t('groupBuy.termsLimitValue', { count: gb.maxQtyPerUser }) }}</template>
                </p>
                <GroupBuyPickedLines :lines="pickedLines" />
            </section>

            <!-- 2. 금액(서버 견적) -->
            <section :aria-labelledby="`${uid}-amount`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                <h2 :id="`${uid}-amount`" class="m-0 mb-2 text-base font-semibold">{{ t('groupBuy.amountTitle') }}</h2>
                <GroupBuyQuoteSummary :quote="quote" :ready="lines.length > 0" :busy="quoteBusy" :error="quoteError" />
                <Button v-if="quoteError && !started" as="router-link" :to="detailLink" :label="t('groupBuy.repick')" icon="pi pi-pencil" size="small" severity="secondary" variant="outlined" class="mt-2" />
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
                <Button v-if="repickHint" as="router-link" :to="detailLink" :label="t('groupBuy.repick')" icon="pi pi-pencil" size="small" severity="secondary" variant="outlined" class="w-fit" />
                <Button
                    type="submit"
                    size="large"
                    :label="quote ? t('groupBuy.payButton', { amount: UtilGroupBuyMessages.won(t, quote.totalAmount) }) : t('groupBuy.payButtonPlain')"
                    icon="pi pi-lock"
                    :loading="submitBusy"
                    :disabled="quoteBusy"
                    data-testid="group-buy-pay"
                />
            </div>
        </form>

        <AppSheet v-if="gb && gb.notices.length > 0" v-model:visible="noticesOpen" :title="t('groupBuy.productInfoTitle')" :description="lines.length > 0 ? t('groupBuy.productInfoSheetHelp') : ''" wide>
            <div class="px-5 pt-3">
                <GroupBuyProductNotices :notices="gb.notices" :skus="gb.availability.skus.filter((s) => s.onSale)" :priority-sku-ids="lines.map((l) => l.skuId)" heading-tag="h4" />
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
import type { GroupBuyPickedLine } from '~/components/groupbuy/GroupBuyPickedLines.types';
import type { PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';
import UtilIdempotency from '~/utils/util-idempotency';
import UtilTime from '#layers/display-time/utils/util-time';

/**
 * 공동구매 주문서 `/group-buys/{id}/order` — 로그인 필요. 계약 commerce-groupbuy 화면 목록 · 결정 23·41·57·59·63·72.
 * - **고른 상품은 확인만**(결정 88 — 고르는 곳은 상세): 상세가 주소 `?items=skuId:qty,…` 로 넘긴 목록(새로고침해도 남는다)의 줄 이름·수량·금액.
 *   바꾸려면 「상품 다시 고르기」 → 상세(고른 것을 들고 간다). 목록이 없거나 깨졌으면(모양 · 이 공구에 없는 단품) 상세로 돌려보내는 안내.
 *   판매 안 함 · 품절 · 추가 구성상품만 같은 규칙은 서버 견적·주문이 알려 주고(409 `GROUP_BUY_SKU_*` · 400 `fields.items`), 그 옆에 「상품 다시 고르기」. 동의는 기본 체크 없음(다크패턴 금지).
 * - 판매조건 옆 「상품정보제공고시 보기」 → 시트(결정 87·88 — 고른 단품의 양식을 먼저 펼친다).
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

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        gb.value = await api.get(id.value);
        readLines();
        status.value = 'ready';
        void requote();
    } catch (e) {
        loadError.value = UtilGroupBuyMessages.error(t, e);
        status.value = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error';
    }
}

/* ── 고른 상품 — 상세가 주소 `?items=skuId:qty,…` 로 넘긴 목록(결정 88, 확인만) ── */

const lines = ref<GroupBuyOrderLine[]>([]);
/** ok · 없음(상세에서 고르지 않고 들어옴) · 깨짐(모양이 틀림 · 이 공구에 없는 단품) */
const itemsState = ref<'ok' | 'empty' | 'broken'>('ok');

/** 주소의 목록을 읽는다 — 모양·단품 존재만 본다(판매 여부·품절·추가 구성상품만 같은 규칙은 서버 견적이 알려 준다) */
function readLines(): void {
    const g = gb.value;
    const parsed = UtilGroupBuy.parseItems(route.query[UtilGroupBuy.ITEMS_QUERY]);
    if (!g || parsed === null || parsed.some((l) => !g.availability.skus.some((s) => s.skuId === l.skuId))) {
        lines.value = [];
        itemsState.value = 'broken';
        return;
    }
    lines.value = parsed;
    itemsState.value = parsed.length > 0 ? 'ok' : 'empty';
}

/** 상세로 돌아가는 길 — 고른 목록을 그대로 들고 간다(개발용 `?mock=` 도) */
const detailLink = computed(() => {
    const query: Record<string, string> = {};
    const mock = route.query.mock;
    if (typeof mock === 'string' && mock) query.mock = mock;
    if (lines.value.length > 0) query[UtilGroupBuy.ITEMS_QUERY] = UtilGroupBuy.formatItems(lines.value);
    return { path: UtilGroupBuy.detailPath(id.value), query };
});

/** 줄 표시 — 이름·종류·단가는 공구 단품(서버), 줄 금액은 견적(서버) */
const pickedLines = computed<GroupBuyPickedLine[]>(() =>
    lines.value.flatMap((l) => {
        const sku = gb.value?.availability.skus.find((s) => s.skuId === l.skuId);
        if (!sku) return [];
        const amount = quote.value?.items.find((q) => q.skuId === l.skuId)?.itemAmount ?? null;
        return [{ skuId: sku.skuId, name: sku.name, kind: sku.kind, unitPrice: sku.unitPrice, qty: l.qty, max: l.qty, amount, removable: false }];
    }),
);

/* ── 상품정보제공고시 시트(결정 87·88) — 고른 단품의 양식을 먼저 ── */

const noticesOpen = ref(false);

/* ── 견적(서버) ── */

const quote = ref<GroupBuyQuote | null>(null);
const quoteBusy = ref(false);
const quoteError = ref('');
let quoteSeq = 0;

async function requote(): Promise<GroupBuyQuote | null> {
    const g = gb.value;
    if (!g || lines.value.length === 0) {
        quote.value = null;
        return null;
    }
    const mine = ++quoteSeq;
    quoteBusy.value = true;
    quoteError.value = '';
    try {
        const res = await api.quote(g.id, { items: lines.value.map((l) => ({ ...l })) });
        if (mine !== quoteSeq) return null;
        quote.value = res;
        return res;
    } catch (e) {
        if (mine !== quoteSeq) return null;
        quote.value = null;
        quoteError.value = e instanceof ApiError && e.status === 400 && e.fields ? (e.fields.items ?? Object.values(e.fields)[0] ?? e.message) : UtilGroupBuyMessages.error(t, e);
        return null;
    } finally {
        if (mine === quoteSeq) quoteBusy.value = false;
    }
}
onBeforeUnmount(() => quoteSeq++);

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
/** 줄 문제(단품 품절 · 판매 안 함 · 줄 오류)로 거절됐다 — 오류 옆에 「상품 다시 고르기」 */
const repickHint = ref(false);

function fieldError(key: string): string {
    return fieldErrors.value[key] ?? '';
}

const addressErrors = computed(() => {
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(fieldErrors.value)) if (k.startsWith('address.')) out[k.slice('address.'.length)] = v;
    return out;
});

/** 화면 미리 검사(편의 — 판정은 서버) */
function precheck(): Record<string, string> {
    const out: Record<string, string> = {};
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
    repickHint.value = false;
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
        const created = await api.order(g.id, { items: lines.value.map((l) => ({ ...l })), address: { ...address.value }, agreements: { ...agreements }, expectedAmount }, idempotencyKey);
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
            // 줄 오류(`items…`)는 이 화면에서 고칠 칸이 없다 — 문구를 그대로 보이고 「상품 다시 고르기」로
            const itemsError = Object.entries(e.fields).find(([k]) => k.startsWith('items'))?.[1];
            formError.value = itemsError ?? t('groupBuy.errCheck');
            repickHint.value = !!itemsError;
            return;
        }
        formError.value = UtilGroupBuyMessages.error(t, e);
        repickHint.value = e instanceof ApiError && (e.code === 'GROUP_BUY_SKU_SOLD_OUT' || e.code === 'GROUP_BUY_SKU_NOT_ON_SALE');
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
    void load();
}

void load();
</script>
