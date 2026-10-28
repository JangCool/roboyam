<template>
    <!--
        취소·반품 요청 상세 서랍 — 요청 대기열에서 줄을 누르면 연다(`?request=ID`).
        구획: ① 기한(경과 영업일 — 2영업일 경고·3영업일 심각) · 다시 계산 값이 요청 때와 다르면 두 값 + 사유(결정 72) ② 요청 줄(줄마다 사유·수량·줄 환불액) · 사진 · 회수 방식
        ③ 환불 계산(서버 값 — 상품·배송비·반품비·재부과·재부과 환급·보상) · 반품비 부족분 추가 결제 상태(결정 68) ④ 회수·교환·거절 기록 ⑤ 이력 ⑥ 운영 동작.
        서버는 부르지 않는다 — 동작은 emit 으로 올린다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '46rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ t(`groupBuy.requestType.${head.type}`) }}<template v-if="head.resolution === 'EXCHANGE'"> · {{ t('groupBuy.resolutionExchange') }}</template></h2>
                    <Tag :value="t(`groupBuy.requestStatus.${head.status}`)" :severity="UtilGroupBuy.requestSeverity(head.status)" />
                    <Tag v-if="isOpenRequest && head.deadlineLevel !== 'OK'" :value="t(`groupBuy.deadline.${head.deadlineLevel}`, { n: head.elapsedBusinessDays })" :severity="UtilGroupBuy.deadlineSeverity(head.deadlineLevel)" icon="pi pi-clock" />
                </div>
                <span class="truncate text-[12px] text-muted-color"><code>{{ head.order.orderNo }}</code> · {{ head.groupBuy.title }} · {{ head.member.nickname }}</span>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('groupBuy.requestLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : error.status === 403 ? '403' : '!'" :retry="error.status !== 404 && error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '65%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <!-- ① 기한 · 금액 바뀜 -->
            <p class="m-0 text-xs text-muted-color">{{ deadlineText }}</p>
            <Message v-if="detail.recalculated && detail.recalculated.breakdown.total !== detail.previewAmount" severity="warn" :closable="false">
                <div class="flex flex-col gap-1 text-[13px]">
                    <span class="font-medium">{{ t('groupBuy.recalcTitle', { before: won(detail.previewAmount), after: won(detail.recalculated.breakdown.total) }) }}</span>
                    <span v-for="(r, i) in detail.recalculated.reasons" :key="i">{{ r }}</span>
                    <span class="text-xs">{{ t('groupBuy.recalcHelp') }}</span>
                </div>
            </Message>

            <!-- ② 요청 줄 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-items`">
                <h3 :id="`${titleId}-items`" class="text-sm font-semibold">{{ t('groupBuy.secRequestItems') }}</h3>
                <ul class="m-0 flex list-none flex-col gap-1 p-0">
                    <li v-for="item in detail.items" :key="item.id" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                        <div class="flex flex-wrap items-center gap-2">
                            <span class="font-medium">{{ item.optionName ?? t('groupBuy.noOption') }}</span>
                            <span class="tabular-nums">{{ t('groupBuy.qtyN', { n: item.qty }) }}</span>
                            <Tag :value="t(`groupBuy.reasonCode.${item.reasonCode}`)" :severity="item.reasonCode === 'CHANGE_OF_MIND' || item.reasonCode === 'OTHER' ? 'secondary' : 'warn'" />
                            <span class="ml-auto tabular-nums">{{ t('groupBuy.won', { amount: won(item.refundAmount) }) }}</span>
                        </div>
                        <span v-if="item.reasonText" class="text-xs text-muted-color [overflow-wrap:anywhere]">“{{ item.reasonText }}”</span>
                        <span v-if="item.dispositions.length" class="text-xs text-muted-color">{{ item.dispositions.map((d) => `${t(`groupBuy.disposition.${d.disposition}`)} ${d.qty}`).join(' · ') }}</span>
                    </li>
                </ul>
                <div v-if="detail.photos.length" class="flex flex-wrap gap-2">
                    <a v-for="(p, i) in detail.photos" :key="p.id" :href="p.url" target="_blank" rel="noopener noreferrer"><img :src="p.url" :alt="t('groupBuy.memberPhotoAlt', { n: i + 1 })" class="size-20 rounded-md border border-(--line) object-cover" /></a>
                </div>
                <p v-if="detail.collectionMethod" class="m-0 text-xs text-muted-color">{{ t('groupBuy.collectionLine', { method: t(`groupBuy.collection.${detail.collectionMethod}`) }) }}</p>
            </section>

            <!-- ③ 환불 계산 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-calc`">
                <h3 :id="`${titleId}-calc`" class="text-sm font-semibold">{{ t('groupBuy.secRefundCalc') }}</h3>
                <dl class="m-0 grid grid-cols-2 gap-x-4 gap-y-1 text-[13px] sm:grid-cols-3">
                    <template v-for="row in calcRows" :key="row.key">
                        <div class="flex justify-between gap-2"><dt class="text-muted-color">{{ row.label }}</dt><dd class="m-0 tabular-nums">{{ row.value }}</dd></div>
                    </template>
                </dl>
                <p class="m-0 text-base font-semibold tabular-nums">{{ t('groupBuy.calcTotal', { amount: won(shownBreakdown.total) }) }}</p>
                <p v-if="shownBreakdown.operatorPaysReturnFee" class="m-0 text-xs text-muted-color">{{ t('groupBuy.operatorPaysFee') }}</p>
                <div v-if="detail.extraPayment" class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                    <span>{{ t('groupBuy.extraPayment', { amount: won(detail.extraPayment.amount) }) }}</span>
                    <Tag :value="t(`groupBuy.extraPaymentStatus.${detail.extraPayment.status}`)" :severity="detail.extraPayment.status === 'PAID' ? 'success' : detail.extraPayment.status === 'READY' ? 'info' : 'secondary'" />
                    <code class="text-[11px] text-muted-color">{{ detail.extraPayment.paymentId }}</code>
                    <span v-if="detail.extraPayment.refundedAmount" class="text-xs text-muted-color">{{ t('groupBuy.extraRefunded', { amount: won(detail.extraPayment.refundedAmount) }) }}</span>
                </div>
            </section>

            <!-- ④ 회수·교환·거절 -->
            <section v-if="detail.pickup || detail.receivedAt || detail.exchangeShipment || detail.rejectReasonCode" class="flex flex-col gap-1 text-[13px]" :aria-labelledby="`${titleId}-logistics`">
                <h3 :id="`${titleId}-logistics`" class="text-sm font-semibold">{{ t('groupBuy.secLogistics') }}</h3>
                <p v-if="detail.pickup" class="m-0">{{ t('groupBuy.pickupLine', { carrier: detail.pickup.carrierCode, no: detail.pickup.trackingNo }) }}</p>
                <p v-if="detail.receivedAt" class="m-0">{{ t('groupBuy.receivedLine', { at: time.date(detail.receivedAt) }) }}</p>
                <p v-if="detail.exchangeShipment" class="m-0">{{ t('groupBuy.exchangeLine', { carrier: detail.exchangeShipment.carrierCode, no: detail.exchangeShipment.trackingNo, at: time.shortDateTime(detail.exchangeShipment.shippedAt) }) }}</p>
                <template v-if="detail.rejectReasonCode">
                    <p class="m-0">{{ t('groupBuy.rejectedLine', { code: t(`groupBuy.rejectReason.${detail.rejectReasonCode}`), reason: detail.rejectReason ?? '' }) }}</p>
                    <div v-if="detail.rejectPhotos.length" class="flex flex-wrap gap-2">
                        <img v-for="(p, i) in detail.rejectPhotos" :key="p.id" :src="p.url" :alt="t('groupBuy.evidenceAlt', { n: i + 1 })" class="size-16 rounded-md border border-(--line) object-cover" />
                    </div>
                </template>
            </section>

            <!-- ⑤ 이력 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-history`">
                <h3 :id="`${titleId}-history`" class="text-sm font-semibold">{{ t('groupBuy.secHistory') }}</h3>
                <ol class="m-0 flex list-none flex-col p-0">
                    <li v-for="(h, i) in detail.history" :key="i" class="flex flex-wrap items-baseline gap-x-2 border-b border-(--line) py-1.5 text-[13px] last:border-b-0">
                        <span class="text-[11px] tabular-nums text-muted-color">{{ time.full(h.at) }}</span>
                        <span>{{ t(`groupBuy.requestStatus.${h.status}`) }}</span>
                        <span class="text-xs text-muted-color">{{ h.actor }}</span>
                        <span v-if="h.note" class="text-xs text-muted-color">· {{ h.note }}</span>
                    </li>
                </ol>
                <NuxtLink :to="`/group-buy-orders?order=${encodeURIComponent(detail.order.id)}`" class="text-xs text-primary hover:underline">{{ t('groupBuy.openOrder') }} ›</NuxtLink>
            </section>

            <!-- ⑥ 운영 동작 -->
            <section v-if="canManage && hasAction" class="flex flex-col gap-2 rounded-xl border border-(--line) p-3" :aria-labelledby="`${titleId}-actions`">
                <h3 :id="`${titleId}-actions`" class="text-sm font-semibold">{{ t('groupBuy.secOrderActions') }}</h3>
                <div class="flex flex-wrap items-center gap-2">
                    <Button v-if="detail.actions.canApprove" type="button" :label="detail.type === 'CANCEL' ? t('groupBuy.actApproveCancel') : t('groupBuy.actApprove')" icon="pi pi-check" size="small" :disabled="busy || (needsRefundPerm && !canRefund)" @click="emit('approve')" />
                    <Button v-if="detail.actions.canReject" type="button" :label="t('groupBuy.actReject')" size="small" severity="danger" variant="outlined" :disabled="busy" @click="emit('reject')" />
                    <Button v-if="detail.actions.canSetPickup" type="button" :label="t('groupBuy.actPickup')" icon="pi pi-truck" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('pickup')" />
                    <Button v-if="detail.actions.canReceive" type="button" :label="t('groupBuy.actReceive')" icon="pi pi-inbox" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('receive')" />
                    <Button v-if="detail.actions.canComplete" type="button" :label="t('groupBuy.actComplete')" icon="pi pi-check-circle" size="small" :disabled="busy || !canRefund" @click="emit('complete')" />
                    <Button v-if="detail.actions.canExchangeShip" type="button" :label="t('groupBuy.actExchangeShip')" icon="pi pi-send" size="small" :disabled="busy" @click="emit('exchangeShip')" />
                    <Button v-if="detail.actions.canConvertToRefund" type="button" :label="t('groupBuy.actConvert')" size="small" severity="secondary" variant="text" :disabled="busy" @click="emit('convert')" />
                </div>
                <p v-if="!canRefund && (detail.actions.canComplete || (detail.actions.canApprove && needsRefundPerm))" class="m-0 text-xs text-muted-color">{{ t('groupBuy.needRefundPermission') }}</p>
            </section>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyRequestDetail, GroupBuyRequestRow } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    summary: GroupBuyRequestRow | null;
    detail: GroupBuyRequestDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    /** `commerce.order.manage` */
    canManage?: boolean;
    /** `commerce.refund.approve` — 환불이 나가는 승인(발송 전 취소)·반품 완료 */
    canRefund?: boolean;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, busy: false, canManage: false, canRefund: false });
const emit = defineEmits<{ retry: []; approve: []; reject: []; pickup: []; receive: []; complete: []; exchangeShip: []; convert: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const head = computed(() => props.detail ?? props.summary);
/** 열린 요청(기한 표시를 보이는 것) */
const isOpenRequest = computed(() => head.value?.status === 'REQUESTED' || head.value?.status === 'APPROVED');
/** 발송 전 취소 승인은 그 자리에서 환불이 나간다 */
const needsRefundPerm = computed(() => props.detail?.type === 'CANCEL');
const hasAction = computed(() => {
    const a = props.detail?.actions;
    return !!a && (a.canApprove || a.canReject || a.canSetPickup || a.canReceive || a.canComplete || a.canExchangeShip || a.canConvertToRefund);
});

function won(n: number): string {
    return UtilGroupBuy.won(n, locale.value);
}

const shownBreakdown = computed(() => props.detail!.recalculated?.breakdown ?? props.detail!.breakdown);
const calcRows = computed(() => {
    const b = shownBreakdown.value;
    const rows = [
        { key: 'item', label: t('groupBuy.calcItem'), value: won(b.itemRefund) },
        { key: 'ship', label: t('groupBuy.calcShipping'), value: won(b.shippingRefund) },
        { key: 'return', label: t('groupBuy.calcReturnFee'), value: b.returnFee ? `−${won(b.returnFee)}` : '0' },
        { key: 'reship', label: t('groupBuy.calcReshipping'), value: b.reshippingFee ? `−${won(b.reshippingFee)}` : '0' },
        { key: 'reshipRefund', label: t('groupBuy.calcReshippingRefund'), value: won(b.reshippingRefund) },
        { key: 'comp', label: t('groupBuy.calcCompensation'), value: won(props.detail?.compensationAmount ?? b.compensation) },
    ];
    return rows;
});

const deadlineText = computed(() => {
    const d = props.detail;
    if (!d) return '';
    if (d.status !== 'REQUESTED' && d.status !== 'APPROVED') return t('groupBuy.deadlineClosed', { at: d.handledAt ? time.value.full(d.handledAt) : '—', by: d.handledBy ?? '—' });
    if (d.type === 'RETURN' && !d.receivedAt) return t('groupBuy.deadlineWaitingReturn', { n: d.elapsedBusinessDays });
    return t('groupBuy.deadlineRunning', { n: d.elapsedBusinessDays });
});
</script>
