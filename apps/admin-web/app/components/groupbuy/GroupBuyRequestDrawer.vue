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
            <p class="m-0 flex items-center gap-1.5 text-xs" :class="isOpenRequest ? 'text-muted-color' : 'text-green-700 dark:text-green-400'">
                <i :class="isOpenRequest ? 'pi pi-clock' : 'pi pi-check-circle'" aria-hidden="true" />{{ deadlineText }}
            </p>
            <Message v-if="detail.recalculated && detail.recalculated.breakdown.total !== detail.previewAmount" severity="warn" :closable="false">
                <div class="flex flex-col gap-1 text-[13px]">
                    <span class="font-medium">{{ t('groupBuy.recalcTitle', { before: won(detail.previewAmount), after: won(detail.recalculated.breakdown.total) }) }}</span>
                    <span v-for="(r, i) in detail.recalculated.reasons" :key="i">{{ r }}</span>
                    <span class="text-xs">{{ t('groupBuy.recalcHelp') }}</span>
                </div>
            </Message>

            <!-- ② 요청 줄 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-items`">
                <h3 :id="`${titleId}-items`" class="text-sm font-semibold">{{ t(`groupBuy.secItems.${detail.resolution === 'EXCHANGE' ? 'EXCHANGE' : detail.type}`) }}</h3>
                <!-- 상품 한 칸: 아이콘 · 이름과 줄 환불액 · 아래 한 줄에 수량 · 사유(하자·오배송은 주황) · 처리 방법(색 점) -->
                <ul class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="item in detail.items" :key="item.id" class="flex items-start gap-3 px-3.5 py-3 text-[13px]">
                        <span class="flex size-9 shrink-0 items-center justify-center rounded-md bg-(--p-content-hover-background) text-muted-color" aria-hidden="true"><i class="pi pi-box" /></span>
                        <div class="flex min-w-0 flex-1 flex-col gap-1">
                            <div class="flex items-baseline justify-between gap-3">
                                <GroupBuySkuLabel :name="item.skuName" :kind="item.kind" class="min-w-0 font-medium" />
                                <span class="shrink-0 font-semibold tabular-nums">{{ t('groupBuy.won', { amount: won(item.refundAmount) }) }}</span>
                            </div>
                            <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-color">
                                <span class="tabular-nums">{{ t('groupBuy.qtyN', { n: item.qty }) }}</span>
                                <span aria-hidden="true">·</span>
                                <span class="inline-flex items-center gap-1" :class="isFault(item.reasonCode) ? 'font-medium text-amber-700 dark:text-amber-400' : ''">
                                    <i v-if="isFault(item.reasonCode)" class="pi pi-exclamation-triangle text-[11px]" aria-hidden="true" />{{ t(`groupBuy.reasonCode.${item.reasonCode}`) }}
                                </span>
                                <template v-for="d in item.dispositions" :key="d.disposition">
                                    <span aria-hidden="true">·</span>
                                    <span class="inline-flex items-center gap-1.5 text-(--p-text-color)">
                                        <span class="size-2 rounded-full" :class="dispositionDot(d.disposition)" aria-hidden="true" />{{ t(`groupBuy.disposition.${d.disposition}`) }} {{ t('groupBuy.qtyN', { n: d.qty }) }}
                                    </span>
                                </template>
                            </div>
                            <p v-if="item.reasonText" class="m-0 mt-0.5 rounded-md bg-(--p-content-hover-background) px-2.5 py-1.5 text-xs [overflow-wrap:anywhere]">{{ item.reasonText }}</p>
                        </div>
                    </li>
                </ul>
                <div v-if="detail.photos.length" class="flex flex-wrap gap-2">
                    <a v-for="(p, i) in detail.photos" :key="p.id" :href="p.url" target="_blank" rel="noopener noreferrer"><img :src="p.url" :alt="t('groupBuy.memberPhotoAlt', { n: i + 1 })" class="size-20 rounded-md border border-(--line) object-cover" /></a>
                </div>
                <p v-if="detail.collectionMethod" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                    <i class="pi pi-truck" aria-hidden="true" />{{ t('groupBuy.collectionLine', { method: t(`groupBuy.collection.${detail.collectionMethod}`) }) }}
                </p>
            </section>

            <!-- ③ 환불 계산 — 더하는 값은 초록, 빼는 값은 빨강, 0 은 흐리게. 합계는 띠로 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-calc`">
                <h3 :id="`${titleId}-calc`" class="text-sm font-semibold">{{ t('groupBuy.secRefundCalc') }}</h3>
                <div class="overflow-hidden rounded-lg border border-(--line)">
                    <dl class="m-0 grid grid-cols-1 gap-x-6 gap-y-1.5 px-3 py-2.5 text-[13px] sm:grid-cols-2">
                        <div v-for="row in calcRows" :key="row.key" class="flex justify-between gap-2">
                            <dt :class="row.amount === 0 ? 'text-muted-color' : ''">{{ row.label }}</dt>
                            <dd class="m-0 tabular-nums" :class="amountClass(row.amount)">{{ signed(row.amount) }}</dd>
                        </div>
                    </dl>
                    <div class="flex items-baseline justify-between gap-2 border-t border-(--line) bg-(--p-content-hover-background) px-3 py-2.5">
                        <span class="text-sm font-semibold">{{ t('groupBuy.calcTotalLabel') }}</span>
                        <span class="text-lg font-bold tabular-nums" :class="shownBreakdown.total < 0 ? 'text-red-600 dark:text-red-400' : 'text-primary'">{{ t('groupBuy.won', { amount: won(shownBreakdown.total) }) }}</span>
                    </div>
                </div>
                <p v-if="shownBreakdown.operatorPaysReturnFee" class="m-0 text-xs text-muted-color">{{ t('groupBuy.operatorPaysFee') }}</p>
                <div v-if="detail.extraPayment" class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                    <span>{{ t('groupBuy.extraPayment', { amount: won(detail.extraPayment.amount) }) }}</span>
                    <Tag :value="t(`groupBuy.extraPaymentStatus.${detail.extraPayment.status}`)" :severity="detail.extraPayment.status === 'PAID' ? 'success' : detail.extraPayment.status === 'READY' ? 'info' : 'secondary'" />
                    <code class="text-[11px] text-muted-color">{{ detail.extraPayment.paymentId }}</code>
                    <span v-if="detail.extraPayment.refundedAmount" class="text-xs text-muted-color">{{ t('groupBuy.extraRefunded', { amount: won(detail.extraPayment.refundedAmount) }) }}</span>
                </div>
            </section>

            <!-- ④ 회수·교환·거절 -->
            <section v-if="detail.pickup || detail.receivedAt || detail.exchangeShipment || detail.rejectReasonCode" class="flex flex-col gap-1.5 text-[13px]" :aria-labelledby="`${titleId}-logistics`">
                <h3 :id="`${titleId}-logistics`" class="text-sm font-semibold">{{ t('groupBuy.secLogistics') }}</h3>
                <p v-if="detail.pickup" class="m-0 flex items-center gap-2"><i class="pi pi-truck text-muted-color" aria-hidden="true" />{{ t('groupBuy.pickupLine', { carrier: detail.pickup.carrierCode, no: detail.pickup.trackingNo }) }}</p>
                <p v-if="detail.receivedAt" class="m-0 flex items-center gap-2"><i class="pi pi-inbox text-green-700 dark:text-green-400" aria-hidden="true" />{{ t('groupBuy.receivedLine', { at: time.date(detail.receivedAt) }) }}</p>
                <p v-if="detail.exchangeShipment" class="m-0 flex items-center gap-2">
                    <i class="pi pi-send text-sky-700 dark:text-sky-400" aria-hidden="true" />{{ t('groupBuy.exchangeLine', { carrier: detail.exchangeShipment.carrierCode, no: detail.exchangeShipment.trackingNo, at: time.shortDateTime(detail.exchangeShipment.shippedAt) }) }}
                </p>
                <template v-if="detail.rejectReasonCode">
                    <p class="m-0 flex items-center gap-2 text-red-600 dark:text-red-400">
                        <i class="pi pi-ban" aria-hidden="true" />{{ t('groupBuy.rejectedLine', { code: t(`groupBuy.rejectReason.${detail.rejectReasonCode}`), reason: detail.rejectReason ?? '' }) }}
                    </p>
                    <div v-if="detail.rejectPhotos.length" class="flex flex-wrap gap-2">
                        <img v-for="(p, i) in detail.rejectPhotos" :key="p.id" :src="p.url" :alt="t('groupBuy.evidenceAlt', { n: i + 1 })" class="size-16 rounded-md border border-(--line) object-cover" />
                    </div>
                </template>
            </section>

            <!-- ⑤ 이력 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-history`">
                <h3 :id="`${titleId}-history`" class="text-sm font-semibold">{{ t('groupBuy.secHistory') }}</h3>
                <!-- 시간 순 타임라인 — 점 색은 상태(접수 주황 · 승인 파랑 · 완료 초록 · 거절 빨강) -->
                <ol class="m-0 flex list-none flex-col p-0">
                    <li v-for="(h, i) in history" :key="i" class="relative flex gap-3 pb-3 text-[13px] last:pb-0">
                        <span v-if="i < history.length - 1" class="absolute left-[5px] top-4 bottom-0 w-px bg-(--line)" aria-hidden="true" />
                        <span class="mt-1 size-[11px] shrink-0 rounded-full ring-2 ring-(--p-content-background)" :class="dotClass(h)" aria-hidden="true" />
                        <div class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-2">
                            <span class="font-medium">{{ historyLabel(h) }}</span>
                            <span class="text-xs text-muted-color">{{ h.actor }}</span>
                            <span v-if="h.note && h.note !== 'RECEIVED'" class="text-xs text-muted-color [overflow-wrap:anywhere]">· {{ h.note }}</span>
                            <span class="ml-auto text-[11px] tabular-nums text-muted-color">{{ time.full(h.at) }}</span>
                        </div>
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
import type { GroupBuyDisposition, GroupBuyReasonCode, GroupBuyRequestDetail, GroupBuyRequestRow } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
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

/** 업체 귀책 사유(하자·오배송) — 줄 왼쪽 띠·사유 태그를 주황으로 */
function isFault(code: GroupBuyReasonCode): boolean {
    return code === 'DEFECT' || code === 'WRONG_ITEM';
}

/** 처리 방법 점 색 — 다시 판매 초록 · 보관 파랑 · 공급처 반송 주황 · 폐기 빨강 */
function dispositionDot(d: GroupBuyDisposition): string {
    if (d === 'RESELL') return 'bg-green-500';
    if (d === 'KEEP') return 'bg-sky-500';
    if (d === 'RETURN_TO_SUPPLIER') return 'bg-amber-500';
    return 'bg-red-500';
}

const shownBreakdown = computed(() => props.detail!.recalculated?.breakdown ?? props.detail!.breakdown);
/** 환불 계산 줄 — `amount` 는 환불에 더하는 쪽이 +, 빼는 쪽(반품비·다시 받는 배송비)이 − */
const calcRows = computed(() => {
    const b = shownBreakdown.value;
    return [
        { key: 'item', label: t('groupBuy.calcItem'), amount: b.itemRefund },
        { key: 'ship', label: t('groupBuy.calcShipping'), amount: b.shippingRefund },
        { key: 'return', label: t('groupBuy.calcReturnFee'), amount: -b.returnFee },
        { key: 'reship', label: t('groupBuy.calcReshipping'), amount: -b.reshippingFee },
        { key: 'reshipRefund', label: t('groupBuy.calcReshippingRefund'), amount: b.reshippingRefund },
        { key: 'comp', label: t('groupBuy.calcCompensation'), amount: props.detail?.compensationAmount ?? b.compensation },
    ];
});

function signed(amount: number): string {
    if (amount === 0) return '0';
    return amount > 0 ? `+${won(amount)}` : `−${won(-amount)}`;
}

function amountClass(amount: number): string {
    if (amount > 0) return 'text-green-700 dark:text-green-400';
    if (amount < 0) return 'text-red-600 dark:text-red-400';
    return 'text-muted-color';
}

type HistoryLine = GroupBuyRequestDetail['history'][number];
/** 이력은 시간 순(서버가 단계 순으로 모아 준다) */
const history = computed(() => [...(props.detail?.history ?? [])].sort((a, b) => a.at.localeCompare(b.at)));

/** 회수 확인 줄은 서버가 승인 상태 + `RECEIVED` 로 준다 — 「회수 확인」으로 보인다 */
function historyLabel(h: HistoryLine): string {
    return h.note === 'RECEIVED' ? t('groupBuy.historyReceived') : t(`groupBuy.requestStatus.${h.status}`);
}

function dotClass(h: HistoryLine): string {
    if (h.note === 'RECEIVED') return 'bg-green-500';
    const severity = UtilGroupBuy.requestSeverity(h.status);
    if (severity === 'success') return 'bg-green-600';
    if (severity === 'danger') return 'bg-red-500';
    if (severity === 'warn') return 'bg-amber-500';
    if (severity === 'info') return 'bg-sky-500';
    return 'bg-surface-400';
}

const deadlineText = computed(() => {
    const d = props.detail;
    if (!d) return '';
    if (d.status !== 'REQUESTED' && d.status !== 'APPROVED') return t('groupBuy.deadlineClosed', { at: d.handledAt ? time.value.full(d.handledAt) : '—', by: d.handledBy ?? '—' });
    if (d.type === 'RETURN' && !d.receivedAt) return t('groupBuy.deadlineWaitingReturn', { n: d.elapsedBusinessDays });
    return t('groupBuy.deadlineRunning', { n: d.elapsedBusinessDays });
});
</script>
