<template>
    <!--
        공구 주문 상세 서랍 — 주문 목록에서 줄을 누르면 연다(`?order=ID`). 열리는 쪽은 설정의 「서랍 위치」. 서버가 열 때마다 열람 감사(`GROUPBUY_ORDER_VIEW`)를 남긴다는 한 줄을 머리에 둔다.
        구획: ① 금액(주문·환불 누계·보상·남은 결제액)과 주문 줄(취소·반품·다시 판매 수량) ② 결제 ③ 배송지(가린 값 — 「배송지 열람」은 사유 5~200자 → 남은 시간 뒤 다시 가림, 열람 기록)
        ④ 배송(송장·조회 링크·교환 재발송) ⑤ 환불 기록(진행 표시 — 결정 16) ⑥ 운영 메모 ⑦ 상태 이력 ⑧ 운영 동작(준비중·발송·송장 정정·배송완료·운영 환불).
        서버는 부르지 않는다(`components.md`) — 동작은 emit 으로 올리고, 열람한 배송지(`revealed`)는 부르는 쪽 메모리에만 두고 `revealExpiresAt` 이 지나면 지운다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '48rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold"><code>{{ head.orderNo }}</code></h2>
                    <Tag :value="t(`groupBuy.orderStatus.${head.status}`)" :severity="UtilGroupBuy.orderSeverity(head.status)" />
                    <Tag v-if="head.openRequest" :value="t(`groupBuy.openRequest.${head.openRequest.type}`)" severity="warn" icon="pi pi-replay" />
                </div>
                <span class="truncate text-[12px] text-muted-color">{{ head.groupBuy.title }}<template v-if="head.groupBuy.roundNo > 1"> · {{ t('groupBuy.roundTag', { n: head.groupBuy.roundNo }) }}</template> · {{ head.member.nickname }}<template v-if="head.member.id"> ({{ head.member.id }})</template></span>
                <p class="m-0 text-[11px] text-muted-color">{{ t('groupBuy.orderViewAudit') }}</p>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('groupBuy.orderLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : error.status === 403 ? '403' : '!'" :retry="error.status !== 404 && error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton width="40%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '65%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <Message v-if="detail.supplyChoice && detail.supplyChoicePending" severity="warn" :closable="false" size="small">{{ t('groupBuy.supplyChoicePending', { deadline: time.full(detail.supplyChoice.deadline) }) }}</Message>
            <Message v-if="detail.delayCancelable" severity="info" :closable="false" size="small">{{ t('groupBuy.delayCancelable') }}</Message>

            <!-- ① 금액 · 주문 줄 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-amount`">
                <h3 :id="`${titleId}-amount`" class="text-sm font-semibold">{{ t('groupBuy.secOrderAmount') }}</h3>
                <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
                    <div v-for="box in amountBoxes" :key="box.key" class="rounded-lg border border-(--line) px-3 py-2">
                        <div class="text-[11px] text-muted-color">{{ box.label }}</div>
                        <div class="text-[15px] font-semibold tabular-nums" :class="box.tone">{{ box.value }}</div>
                    </div>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('groupBuy.amountBreakdown', { items: won(detail.amounts.itemsAmount), ship: won(detail.amounts.shippingFee), reship: won(detail.amounts.reshippingFeeCharged) }) }}</p>
                <!-- 상품 한 칸: 아이콘 · 이름/금액 · 단가×수량 · 바뀐 수량(취소 회색 · 반품 주황 · 다시 판매 초록) -->
                <ul class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="item in detail.items" :key="item.id" class="flex items-start gap-3 px-3.5 py-3 text-[13px]">
                        <span class="flex size-9 shrink-0 items-center justify-center rounded-md bg-(--p-content-hover-background) text-muted-color" aria-hidden="true"><i class="pi pi-box" /></span>
                        <div class="flex min-w-0 flex-1 flex-col gap-1">
                            <div class="flex items-baseline justify-between gap-3">
                                <GroupBuySkuLabel :name="item.skuName" :kind="item.kind" class="min-w-0 font-medium" />
                                <span class="shrink-0 font-semibold tabular-nums">{{ t('groupBuy.won', { amount: won(item.itemAmount) }) }}</span>
                            </div>
                            <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-color">
                                <span class="tabular-nums">{{ t('groupBuy.unitTimesQty', { price: won(item.unitPrice), qty: item.qty }) }}</span>
                                <template v-for="c in itemChanges(item)" :key="c.key">
                                    <span aria-hidden="true">·</span>
                                    <span class="inline-flex items-center gap-1.5 text-(--p-text-color)"><span class="size-2 rounded-full" :class="c.dot" aria-hidden="true" />{{ c.label }}</span>
                                </template>
                            </div>
                        </div>
                    </li>
                </ul>
            </section>

            <!-- ② 결제 -->
            <section class="flex flex-col gap-1" :aria-labelledby="`${titleId}-pay`">
                <div class="flex items-center gap-2">
                    <h3 :id="`${titleId}-pay`" class="text-sm font-semibold">{{ t('groupBuy.secPayment') }}</h3>
                    <NuxtLink v-if="detail.payment && can('commerce.payment.read')" :to="`/payments/orders?payment=${encodeURIComponent(detail.payment.paymentOrderId)}`" class="ml-auto text-xs text-primary hover:underline">{{ t('groupBuy.openPayment') }} ›</NuxtLink>
                </div>
                <p v-if="!detail.payment" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.paymentNone') }}</p>
                <dl v-else class="m-0 flex flex-col">
                    <div :class="ROW">
                        <dt :class="DT">{{ t('groupBuy.payMethodLabel') }}</dt>
                        <dd :class="DD" class="flex flex-wrap items-center gap-2"><span class="font-medium">{{ detail.payment.payMethod ?? '—' }}</span><Tag v-if="detail.payment.escrow" :value="t('groupBuy.escrow')" severity="info" /></dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('groupBuy.payPaidAt') }}</dt>
                        <dd class="tabular-nums" :class="[DD, detail.payment.paidAt ? 'font-medium text-green-700 dark:text-green-400' : 'text-muted-color']">
                            <i v-if="detail.payment.paidAt" class="pi pi-check-circle mr-1 text-xs" aria-hidden="true" />{{ detail.payment.paidAt ? time.full(detail.payment.paidAt) : '—' }}
                        </dd>
                    </div>
                    <div :class="ROW"><dt :class="DT">{{ t('groupBuy.payIdLabel') }}</dt><dd :class="DD"><code class="text-xs text-muted-color">{{ detail.payment.paymentId }}</code></dd></div>
                </dl>
            </section>

            <!-- ③ 배송지 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-addr`">
                <div class="flex flex-wrap items-center gap-2">
                    <h3 :id="`${titleId}-addr`" class="text-sm font-semibold">{{ t('groupBuy.secAddress') }}</h3>
                    <Tag v-if="revealed" :value="t('groupBuy.revealLeft', { time: revealLeftText })" severity="warn" icon="pi pi-eye" />
                    <Button v-if="!revealed && detail.actions.canRevealAddress && canReveal" type="button" :label="t('groupBuy.revealOpen')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" class="ml-auto" :loading="revealBusy" @click="emit('reveal')" />
                </div>
                <p v-if="detail.address.erasedAt" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.addressErased', { at: time.full(detail.address.erasedAt) }) }}</p>
                <dl v-else class="m-0 flex flex-col">
                    <div :class="ROW"><dt :class="DT">{{ t('groupBuy.addrRecipient') }}</dt><dd :class="DD">{{ revealed?.recipient ?? detail.address.recipient }}</dd></div>
                    <div :class="ROW"><dt :class="DT">{{ t('groupBuy.addrPhone') }}</dt><dd :class="DD" class="tabular-nums">{{ revealed?.phone ?? detail.address.phone }}</dd></div>
                    <div :class="ROW"><dt :class="DT">{{ t('groupBuy.addrAddress') }}</dt><dd :class="DD">({{ revealed?.postcode ?? detail.address.postcode }}) {{ revealed ? `${revealed.address1} ${revealed.address2}` : detail.address.address }}</dd></div>
                    <div :class="ROW"><dt :class="DT">{{ t('groupBuy.addrMemo') }}</dt><dd :class="DD">{{ revealed?.memo ?? (detail.address.memo || '—') }}</dd></div>
                </dl>
                <p v-if="!canReveal && !detail.address.erasedAt" class="m-0 text-xs text-muted-color">{{ t('groupBuy.revealNeedPermission') }}</p>
                <div v-if="detail.addressViews.length" class="flex flex-col gap-0.5">
                    <span class="text-xs text-muted-color">{{ t('groupBuy.addressViews') }}</span>
                    <ul class="m-0 flex list-none flex-col p-0 text-xs">
                        <li v-for="(v, i) in detail.addressViews" :key="i">{{ v.operator }} · {{ time.full(v.viewedAt) }} · <span class="text-muted-color">{{ v.reason }}</span></li>
                    </ul>
                </div>
            </section>

            <!-- ④ 배송 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-ship`">
                <h3 :id="`${titleId}-ship`" class="text-sm font-semibold">{{ t('groupBuy.secShipment') }}</h3>
                <p class="m-0 text-xs text-muted-color">{{ detail.shipDueAt ? t('groupBuy.shipDue', { at: time.date(detail.shipDueAt) }) : t('groupBuy.shipDueNone') }}</p>
                <p v-if="!detail.shipments.length" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.shipmentsEmpty') }}</p>
                <ul v-else class="m-0 flex list-none flex-col gap-1 p-0">
                    <li v-for="(s, i) in detail.shipments" :key="i" class="flex flex-wrap items-center gap-x-2 gap-y-0.5 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                        <Tag :value="t(`groupBuy.shipmentKind.${s.kind}`)" :severity="s.kind === 'EXCHANGE' ? 'warn' : 'info'" icon="pi pi-truck" />
                        <span>{{ s.carrierName }}</span>
                        <code class="text-xs">{{ s.trackingNo }}</code>
                        <a v-if="s.trackingUrl" :href="s.trackingUrl" target="_blank" rel="noopener noreferrer" class="text-xs text-primary hover:underline">{{ t('groupBuy.trackLink') }} <i class="pi pi-external-link text-[10px]" aria-hidden="true" /></a>
                        <span class="ml-auto text-[11px] text-muted-color">{{ t('groupBuy.shippedAt', { at: time.shortDateTime(s.shippedAt) }) }}<template v-if="s.deliveredAt"> · {{ t('groupBuy.deliveredAt', { at: time.shortDateTime(s.deliveredAt) }) }}</template></span>
                    </li>
                </ul>
            </section>

            <!-- ⑤ 환불 기록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-refund`">
                <h3 :id="`${titleId}-refund`" class="text-sm font-semibold">{{ t('groupBuy.secRefunds') }}</h3>
                <p v-if="!detail.refunds.length" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.refundsEmpty') }}</p>
                <!-- 환불 한 칸: 금액·원인·상태 · 구성(더하는 값 초록 · 빼는 값 빨강) · 진행 안내 · 사유(요청에서 나온 환불은 그 요청으로) -->
                <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="r in detail.refunds" :key="r.id" class="flex flex-col gap-1.5 px-3.5 py-3 text-[13px]">
                        <div class="flex flex-wrap items-center gap-2">
                            <span class="text-[15px] font-semibold tabular-nums" :class="r.state === 'DONE' ? 'text-red-600 dark:text-red-400' : ''">−{{ t('groupBuy.won', { amount: won(r.total) }) }}</span>
                            <Tag :value="t(`groupBuy.refundCause.${r.cause}`)" :severity="UtilGroupBuy.refundCauseSeverity(r.cause)" />
                            <Tag :value="t(`groupBuy.refundState.${r.state}`)" :severity="UtilGroupBuy.refundSeverity(r.state)" />
                            <Tag v-if="r.businessFault" :value="t('groupBuy.businessFault')" severity="warn" />
                            <Tag v-if="r.method !== 'PG_CANCEL'" :value="t(`groupBuy.refundMethod.${r.method}`)" severity="secondary" />
                            <span class="ml-auto text-[11px] tabular-nums text-muted-color">{{ time.full(r.requestedAt) }}</span>
                        </div>
                        <div class="flex flex-wrap gap-x-3 gap-y-0.5 text-xs">
                            <span v-for="p in refundParts(r)" :key="p.key" class="tabular-nums"><span class="text-muted-color">{{ p.label }}</span> <span :class="p.amount < 0 ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400'">{{ signed(p.amount) }}</span></span>
                        </div>
                        <p class="m-0 text-xs text-muted-color">{{ t(`groupBuy.refundStateHelp.${r.state}`, { at: r.completedAt ? time.date(r.completedAt) : '' }) }}</p>
                        <p v-if="r.reason" class="m-0 text-xs text-muted-color">
                            <template v-if="requestIdOf(r.reason)">
                                <NuxtLink :to="`/group-buy-requests?request=${requestIdOf(r.reason)}`" class="text-primary hover:underline">{{ t('groupBuy.refundFromRequest') }} ›</NuxtLink> · {{ r.operator ?? '—' }}
                            </template>
                            <template v-else>{{ t('groupBuy.refundReasonLine', { reason: r.reason, operator: r.operator ?? '—' }) }}</template>
                        </p>
                    </li>
                </ul>
            </section>

            <!-- ⑥ 운영 메모 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-memo`">
                <h3 :id="`${titleId}-memo`" class="text-sm font-semibold">{{ t('groupBuy.secMemo') }}</h3>
                <Textarea v-model="memo" rows="3" auto-resize :maxlength="UtilGroupBuy.MEMO_MAX" :readonly="!canManage || memoBusy" :aria-labelledby="`${titleId}-memo`" :placeholder="t('groupBuy.memoPlaceholder')" fluid />
                <div v-if="canManage" class="flex items-center justify-end gap-2">
                    <span class="mr-auto text-xs text-muted-color">{{ t('groupBuy.memoHelp') }}</span>
                    <Button type="button" :label="t('common.save')" size="small" :disabled="memo === detail.adminMemo" :loading="memoBusy" @click="emit('saveMemo', memo)" />
                </div>
            </section>

            <!-- ⑦ 상태 이력 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-history`">
                <h3 :id="`${titleId}-history`" class="text-sm font-semibold">{{ t('groupBuy.secHistory') }}</h3>
                <!-- 시간 순 타임라인 — 점 색은 바뀐 뒤 상태, 원인 코드는 글자로 -->
                <ol class="m-0 flex list-none flex-col p-0">
                    <li v-for="(h, i) in detail.history" :key="i" class="relative flex gap-3 pb-3 text-[13px] last:pb-0">
                        <span v-if="i < detail.history.length - 1" class="absolute left-[5px] top-4 bottom-0 w-px bg-(--line)" aria-hidden="true" />
                        <span class="mt-1 size-[11px] shrink-0 rounded-full ring-2 ring-(--p-content-background)" :class="statusDot(h.to)" aria-hidden="true" />
                        <div class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-2">
                            <span class="font-medium">{{ t(`groupBuy.orderStatus.${h.to}`) }}</span>
                            <span v-if="h.from" class="text-xs text-muted-color">{{ t('groupBuy.historyFrom', { status: t(`groupBuy.orderStatus.${h.from}`) }) }}</span>
                            <span class="text-xs text-muted-color">{{ h.actor === 'SYSTEM' ? t('groupBuy.system') : h.actor }}</span>
                            <span v-if="h.note" class="text-xs text-muted-color">· {{ causeText(h.note) }}</span>
                            <span class="ml-auto text-[11px] tabular-nums text-muted-color">{{ time.full(h.at) }}</span>
                        </div>
                    </li>
                </ol>
            </section>

            <!-- ⑧ 운영 동작 -->
            <section v-if="canManage || canRefund" class="flex flex-col gap-3 rounded-xl border border-(--line) p-3" :aria-labelledby="`${titleId}-actions`">
                <h3 :id="`${titleId}-actions`" class="text-sm font-semibold">{{ t('groupBuy.secOrderActions') }}</h3>
                <div class="flex flex-wrap items-center gap-2">
                    <template v-if="canManage">
                        <Button v-if="detail.actions.canPrepare" type="button" :label="t('groupBuy.actPrepare')" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('prepare')" />
                        <Button v-if="detail.actions.canShip" type="button" :label="t('groupBuy.actShip')" icon="pi pi-send" size="small" :disabled="busy" @click="emit('ship')" />
                        <Button v-if="detail.actions.canFixTracking" type="button" :label="t('groupBuy.actFixTracking')" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('fixTracking')" />
                        <Button v-if="detail.actions.canDeliver" type="button" :label="t('groupBuy.actDeliver')" icon="pi pi-check" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('deliver')" />
                    </template>
                    <Button v-if="canRefund && detail.actions.refundMode" type="button" :label="t('groupBuy.actRefund')" icon="pi pi-undo" size="small" severity="danger" variant="outlined" :disabled="busy" @click="emit('refund')" />
                </div>
                <p v-if="canRefund && detail.actions.refundBlockedReason" class="m-0 text-xs text-muted-color">{{ t(`groupBuy.refundBlocked.${detail.actions.refundBlockedReason}`) }}</p>
                <p v-if="canManage && !detail.actions.canShip && (detail.status === 'CONFIRMED' || detail.status === 'PREPARING')" class="m-0 text-xs text-muted-color">{{ shipBlockedText }}</p>
            </section>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import UtilTime from '#layers/display-time/utils/util-time';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyOrderDetail, GroupBuyOrderRow, GroupBuyRefundRecord, GroupBuyRevealedAddress } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    summary: GroupBuyOrderRow | null;
    detail: GroupBuyOrderDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 열람한 배송지(부르는 쪽 메모리) */
    revealed?: GroupBuyRevealedAddress | null;
    revealBusy?: boolean;
    memoBusy?: boolean;
    /** 동작 하나가 도는 중 */
    busy?: boolean;
    /** `commerce.order.manage` */
    canManage?: boolean;
    /** `commerce.refund.approve` */
    canRefund?: boolean;
    /** `commerce.address.reveal` */
    canReveal?: boolean;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, revealed: null, revealBusy: false, memoBusy: false, busy: false, canManage: false, canRefund: false, canReveal: false });
const emit = defineEmits<{ retry: []; reveal: []; saveMemo: [memo: string]; prepare: []; ship: []; fixTracking: []; deliver: []; refund: [] }>();

const { t, te, locale } = useI18n();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const ROW = 'grid grid-cols-1 gap-x-3 gap-y-0.5 border-b border-(--line) py-2 last:border-b-0 sm:grid-cols-[8rem_minmax(0,1fr)]';
const DT = 'text-[13px] text-muted-color';
const DD = 'm-0 min-w-0 text-[13px] [overflow-wrap:anywhere]';

const head = computed(() => props.detail ?? props.summary);
const memo = ref('');
watch(
    () => props.detail?.adminMemo,
    (value) => (memo.value = value ?? ''),
    { immediate: true },
);

function won(n: number): string {
    return UtilGroupBuy.won(n, locale.value);
}

const amountBoxes = computed(() => {
    const a = props.detail?.amounts;
    if (!a) return [];
    const text = (n: number) => t('groupBuy.won', { amount: won(n) });
    return [
        { key: 'amount', label: t('groupBuy.amountTotal'), value: text(a.amount), tone: '' },
        { key: 'refunded', label: t('groupBuy.amountRefunded'), value: a.refundedAmount > 0 ? `−${text(a.refundedAmount)}` : text(0), tone: a.refundedAmount > 0 ? 'text-red-600 dark:text-red-400' : 'text-muted-color' },
        { key: 'comp', label: t('groupBuy.amountCompensation'), value: text(a.compensationAmount), tone: a.compensationAmount > 0 ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color' },
        { key: 'remaining', label: t('groupBuy.amountRemaining'), value: text(a.remaining), tone: 'text-primary' },
    ];
});

function signed(amount: number): string {
    return amount < 0 ? `−${won(-amount)}` : `+${won(amount)}`;
}

/** 환불 구성 — 상품은 늘, 나머지는 0 이 아닐 때만. 반품비·추가 배송비는 빼는 값(−) */
function refundParts(r: GroupBuyRefundRecord): { key: string; label: string; amount: number }[] {
    const parts = [{ key: 'item', label: t('groupBuy.calcItem'), amount: r.itemRefund }];
    if (r.shippingRefund) parts.push({ key: 'ship', label: t('groupBuy.calcShipping'), amount: r.shippingRefund });
    if (r.returnFee) parts.push({ key: 'return', label: t('groupBuy.calcReturnFee'), amount: -r.returnFee });
    if (r.reshippingFee) parts.push({ key: 'reship', label: t('groupBuy.calcReshipping'), amount: -r.reshippingFee });
    if (r.reshippingRefund) parts.push({ key: 'reshipRefund', label: t('groupBuy.calcReshippingRefund'), amount: r.reshippingRefund });
    if (r.compensation) parts.push({ key: 'comp', label: t('groupBuy.calcCompensation'), amount: r.compensation });
    return parts;
}

/** 요청에서 나온 환불의 사유는 서버가 `REQUEST <요청 번호>` 로 남긴다 — 그 요청으로 잇는다 */
function requestIdOf(reason: string): string | null {
    return /^REQUEST (\d+)$/.exec(reason)?.[1] ?? null;
}

/** 주문 줄의 바뀐 수량 — 있는 것만 */
function itemChanges(item: GroupBuyOrderDetail['items'][number]): { key: string; label: string; dot: string }[] {
    const out = [];
    if (item.canceledQty) out.push({ key: 'canceled', label: t('groupBuy.itemCanceledN', { n: item.canceledQty }), dot: 'bg-surface-400' });
    if (item.returnedQty) out.push({ key: 'returned', label: t('groupBuy.itemReturnedN', { n: item.returnedQty }), dot: 'bg-amber-500' });
    if (item.restockedQty) out.push({ key: 'restocked', label: t('groupBuy.itemRestockedN', { n: item.restockedQty }), dot: 'bg-green-500' });
    return out;
}

/** 이력 원인 코드 → 글자(모르는 코드는 그대로) */
function causeText(code: string): string {
    const key = `groupBuy.historyCause.${code}`;
    return te(key) ? t(key) : code;
}

function statusDot(status: GroupBuyOrderDetail['status']): string {
    const severity = UtilGroupBuy.orderSeverity(status);
    if (severity === 'success') return 'bg-green-600';
    if (severity === 'danger') return 'bg-red-500';
    if (severity === 'warn') return 'bg-amber-500';
    if (severity === 'info') return 'bg-sky-500';
    if (severity === 'contrast') return 'bg-surface-700 dark:bg-surface-200';
    return 'bg-surface-400';
}

const shipBlockedText = computed(() => {
    const d = props.detail;
    if (!d) return '';
    if (d.openRequest) return t('groupBuy.shipBlockedRequest');
    if (d.supplyChoicePending) return t('groupBuy.shipBlockedSupply');
    return t('groupBuy.shipBlockedOrdered');
});

// 배송지 열람 남은 시간
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;
watch(
    () => props.revealed,
    (value) => {
        if (timer) clearInterval(timer);
        timer = value ? setInterval(() => (now.value = Date.now()), 1000) : null;
        now.value = Date.now();
    },
    { immediate: true },
);
onBeforeUnmount(() => {
    if (timer) clearInterval(timer);
});
const revealLeftText = computed(() => (props.revealed ? UtilTime.mmss(Math.max(0, Math.ceil((Date.parse(props.revealed.expiresAt) - now.value) / 1000))) : ''));
</script>
