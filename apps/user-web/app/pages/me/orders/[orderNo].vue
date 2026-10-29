<template>
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="my-order">
        <NuxtLink :to="UtilCommerce.ORDERS_PATH" class="inline-flex w-fit items-center gap-1 text-sm text-muted-color hover:underline">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" /> {{ t('commerce.ordersTitle') }}
        </NuxtLink>

        <div v-if="status === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')" data-testid="my-order-loading">
            <div v-for="n in 3" :key="n" class="h-32 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('groupBuy.orderNotFound')" :description="loadError" data-testid="my-order-notfound">
            <Button as="router-link" :to="UtilCommerce.ORDERS_PATH" :label="t('commerce.ordersTitle')" severity="secondary" variant="outlined" />
        </ErrorState>
        <ErrorState v-else-if="status === 'error' || !order" :title="t('groupBuy.orderFailed')" :description="loadError" retry data-testid="my-order-error" @retry="load(false)" />

        <template v-else>
            <!-- 머리 -->
            <header class="flex gap-3">
                <GroupBuyThumb :src="order.groupBuy.thumbnailUrl" :alt="order.groupBuy.title" size="sm" />
                <div class="flex min-w-0 flex-1 flex-col gap-1">
                    <div class="flex flex-wrap items-center gap-1.5">
                        <PointShopTag :tone="order.status === 'PENDING_PAYMENT' ? 'warn' : order.status === 'DELIVERED' ? 'success' : 'primary'">{{ t(`groupBuy.orderStatus.${order.status}`) }}</PointShopTag>
                        <PointShopTag v-if="order.groupBuy.roundNo > 1">{{ t('groupBuy.roundNo', { n: order.groupBuy.roundNo }) }}</PointShopTag>
                    </div>
                    <h1 class="m-0 text-lg font-bold leading-snug">
                        <NuxtLink :to="UtilGroupBuy.detailPath(order.groupBuy.id)" class="hover:underline">{{ order.groupBuy.title }}</NuxtLink>
                    </h1>
                    <p class="m-0 text-xs tabular-nums text-muted-color">{{ t('groupBuy.orderNoLine', { orderNo: order.orderNo, at: time.full(order.createdAt) }) }}</p>
                </div>
            </header>

            <!-- 결제 대기 — 선점 남은 시간 · 결제 다시 열기(결정 41·63) -->
            <section v-if="order.status === 'PENDING_PAYMENT'" class="flex flex-col gap-2 rounded-xl border border-primary bg-primary-50 p-4 dark:bg-primary-950/30" aria-live="polite" data-testid="my-order-pending">
                <p class="m-0 text-sm font-semibold">{{ t('groupBuy.pendingTitle') }}</p>
                <p v-if="hold.active.value" class="m-0 text-sm tabular-nums">{{ t('groupBuy.holdLeft', { time: UtilTime.mmss(hold.remaining.value) }) }}</p>
                <Message v-else severity="warn" size="small" :closable="false">{{ t('groupBuy.holdExpired') }}</Message>
                <p v-if="pay.note.value" class="m-0 text-sm text-muted-color">{{ t(`groupBuy.payNote.${pay.note.value}`) }}</p>
                <div class="flex flex-wrap gap-2">
                    <Button v-if="order.actions.payAgain && hold.active.value" type="button" :label="t('groupBuy.payReopen')" icon="pi pi-credit-card" :loading="payBusy || pay.busy.value" data-testid="my-order-pay" @click="payAgain" />
                    <Button v-else as="router-link" :to="UtilGroupBuy.detailPath(order.groupBuy.id)" :label="t('groupBuy.orderAgain')" severity="secondary" variant="outlined" />
                </div>
            </section>
            <Message v-if="payMessage" :severity="payMessage.severity" size="small" :closable="false" role="alert" data-testid="my-order-pay-message">{{ payMessage.text }}</Message>
            <Message v-if="pay.completeError.value" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ pay.completeError.value }}</Message>

            <!-- 발송 지연 공지 — 바로 전액 취소(결정 52) -->
            <section v-if="order.delayNotice" class="flex flex-col gap-2 rounded-xl border border-amber-300 bg-amber-50 p-4 text-amber-950 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100" data-testid="my-order-delay">
                <p class="m-0 text-sm font-semibold">{{ t('groupBuy.delayTitle', { date: time.serviceDate(order.delayNotice.newPlannedAt) }) }}</p>
                <p class="m-0 text-sm">{{ order.delayNotice.reason }}</p>
                <p v-if="order.delayNotice.previousPlannedAt" class="m-0 text-xs">{{ t('groupBuy.delayPrevious', { date: time.serviceDate(order.delayNotice.previousPlannedAt) }) }}</p>
                <template v-if="order.actions.delayCancel">
                    <p class="m-0 text-sm">{{ t('groupBuy.delayCancelHelp', { amount: UtilGroupBuyMessages.won(t, order.delayNotice.refundAmount) }) }}</p>
                    <div>
                        <Button type="button" :label="t('groupBuy.delayCancel')" icon="pi pi-times-circle" severity="danger" variant="outlined" size="small" :loading="actionBusy === 'delay'" data-testid="my-order-delay-cancel" @click="delayCancel" />
                    </div>
                </template>
            </section>

            <!-- 옵션 공급 불가 — 나머지만 받기 / 전체 취소(결정 53) -->
            <section v-if="order.supplyChoice" class="flex flex-col gap-2 rounded-xl border border-amber-300 bg-amber-50 p-4 text-amber-950 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100" data-testid="my-order-supply">
                <p class="m-0 text-sm font-semibold">{{ t('groupBuy.supplyTitle', { name: order.supplyChoice.skuName }) }}</p>
                <p class="m-0 text-sm">{{ order.supplyChoice.reason }}</p>
                <template v-if="order.actions.supplyChoice">
                    <p class="m-0 text-sm">{{ t('groupBuy.supplyAsk') }}</p>
                    <p class="m-0 text-xs">{{ t('groupBuy.supplyDeadline', { at: time.full(order.supplyChoice.deadline) }) }}</p>
                    <div class="flex flex-wrap gap-2">
                        <Button type="button" :label="t('groupBuy.supplyKeep')" icon="pi pi-check" size="small" :loading="actionBusy === 'KEEP_REST'" data-testid="my-order-supply-keep" @click="supplyChoice('KEEP_REST')" />
                        <Button
                            type="button"
                            :label="t('groupBuy.supplyCancelAll', { amount: UtilGroupBuyMessages.won(t, order.supplyChoice.cancelAllRefund) })"
                            icon="pi pi-times"
                            size="small"
                            severity="danger"
                            variant="outlined"
                            :loading="actionBusy === 'CANCEL_ALL'"
                            data-testid="my-order-supply-cancel"
                            @click="supplyChoice('CANCEL_ALL')"
                        />
                    </div>
                </template>
                <p v-else-if="order.supplyChoice.choice" class="m-0 text-sm font-medium">
                    {{ t(`groupBuy.supplyChosen.${order.supplyChoice.choice}`) }}<template v-if="order.supplyChoice.defaulted"> · {{ t('groupBuy.supplyDefaulted') }}</template>
                </p>
            </section>
            <Message v-if="actionError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ actionError }}</Message>

            <!-- 주문 상품 -->
            <section :aria-labelledby="`${uid}-items`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                <h2 :id="`${uid}-items`" class="m-0 mb-2 text-base font-semibold">{{ t('groupBuy.itemsTitle') }}</h2>
                <ul class="m-0 flex list-none flex-col divide-y divide-surface p-0" data-testid="my-order-items">
                    <li v-for="item in order.items" :key="item.id" class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 py-2 text-sm">
                        <span class="min-w-0">
                            <PointShopTag v-if="item.kind === 'ADDON'" tone="neutral" class="mr-1.5 align-middle">{{ t('groupBuy.addonTag') }}</PointShopTag>
                            <span class="font-medium">{{ item.skuName }}</span>
                            <span class="ml-1.5 tabular-nums text-muted-color">{{ t('groupBuy.unitTimesQty', { price: UtilGroupBuyMessages.won(t, item.unitPrice), qty: item.qty }) }}</span>
                            <span v-if="item.canceledQty > 0" class="ml-1.5 text-xs text-muted-color">{{ t('groupBuy.canceledQty', { count: item.canceledQty }) }}</span>
                            <span v-if="item.returnedQty > 0" class="ml-1.5 text-xs text-muted-color">{{ t('groupBuy.returnedQty', { count: item.returnedQty }) }}</span>
                        </span>
                        <span class="tabular-nums">{{ UtilGroupBuyMessages.won(t, item.itemAmount) }}</span>
                    </li>
                </ul>
                <dl class="m-0 mt-2 grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1 border-t border-surface pt-2 text-sm">
                    <dt class="text-muted-color">{{ t('groupBuy.itemsAmount') }}</dt>
                    <dd class="m-0 text-right tabular-nums">{{ UtilGroupBuyMessages.won(t, order.itemsAmount) }}</dd>
                    <dt class="text-muted-color">{{ t('groupBuy.shippingFee') }}</dt>
                    <dd class="m-0 text-right tabular-nums">{{ order.shippingFee === 0 ? t('groupBuy.freeShipping') : UtilGroupBuyMessages.won(t, order.shippingFee) }}</dd>
                    <dt class="font-semibold">{{ t('groupBuy.paidTotal') }}</dt>
                    <dd class="m-0 text-right font-bold tabular-nums">{{ UtilGroupBuyMessages.won(t, order.amount) }}</dd>
                    <template v-if="order.refundedAmount > 0">
                        <dt class="text-muted-color">{{ t('groupBuy.refundedTotal') }}</dt>
                        <dd class="m-0 text-right tabular-nums">−{{ UtilGroupBuyMessages.won(t, order.refundedAmount) }}</dd>
                    </template>
                    <template v-if="order.compensationAmount > 0">
                        <dt class="text-muted-color">{{ t('groupBuy.compensationTotal') }}</dt>
                        <dd class="m-0 text-right tabular-nums">{{ UtilGroupBuyMessages.won(t, order.compensationAmount) }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('groupBuy.paidAt') }}</dt>
                    <dd class="m-0 text-right tabular-nums">{{ order.paidAt ? time.full(order.paidAt) : '—' }}</dd>
                </dl>
            </section>

            <!-- 취소·반품 단추(서버 `actions`) -->
            <div v-if="order.actions.cancelNow || order.actions.requestCancel || order.actions.requestReturn" class="flex flex-wrap gap-2" data-testid="my-order-actions">
                <Button v-if="order.actions.cancelNow" type="button" :label="t('groupBuy.cancelNow')" icon="pi pi-times" severity="secondary" variant="outlined" data-testid="my-order-cancel-now" @click="openDialog('CANCEL_NOW')" />
                <Button v-if="order.actions.requestCancel" type="button" :label="t('groupBuy.requestCancel')" icon="pi pi-times" severity="secondary" variant="outlined" data-testid="my-order-request-cancel" @click="openDialog('CANCEL_REQUEST')" />
                <Button v-if="order.actions.requestReturn" type="button" :label="t('groupBuy.requestReturn')" icon="pi pi-replay" severity="secondary" variant="outlined" data-testid="my-order-request-return" @click="openDialog('RETURN')" />
            </div>
            <p v-if="order.returnUntil || order.defectReturnUntil" class="m-0 -mt-2 text-xs text-muted-color">
                <template v-if="order.returnUntil">{{ t('groupBuy.returnUntil', { at: time.date(order.returnUntil) }) }}</template>
                <template v-if="order.defectReturnUntil"> {{ t('groupBuy.defectReturnUntil', { at: time.date(order.defectReturnUntil) }) }}</template>
            </p>

            <!-- 환불 진행(결정 16) -->
            <section v-if="order.refunds.length > 0" :aria-labelledby="`${uid}-refunds`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="my-order-refunds">
                <h2 :id="`${uid}-refunds`" class="m-0 mb-2 text-base font-semibold">{{ t('groupBuy.refundsTitle') }}</h2>
                <ul class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="refund in order.refunds" :key="refund.id" class="flex flex-wrap items-baseline justify-between gap-x-3 text-sm">
                        <span class="min-w-0">
                            <span class="font-medium">{{ t(`groupBuy.refundCause.${refund.cause}`) }}</span>
                            <span class="ml-1.5" :class="refund.progress === 'DELAYED' ? 'text-orange-700 dark:text-orange-400' : 'text-muted-color'">{{ UtilGroupBuyMessages.refundProgress(t, time, refund) }}</span>
                        </span>
                        <span class="tabular-nums">{{ UtilGroupBuyMessages.won(t, refund.amount) }}</span>
                    </li>
                </ul>
            </section>

            <!-- 요청(취소·반품) — 상태 · 철회 · 반품비 부족분 결제 -->
            <section v-if="order.requests.length > 0" :aria-labelledby="`${uid}-requests`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="my-order-requests">
                <h2 :id="`${uid}-requests`" class="m-0 mb-2 text-base font-semibold">{{ t('groupBuy.requestsTitle') }}</h2>
                <ul class="m-0 flex list-none flex-col divide-y divide-surface p-0">
                    <li v-for="request in order.requests" :key="request.id" class="flex flex-col gap-1 py-2 text-sm">
                        <div class="flex flex-wrap items-center gap-1.5">
                            <span class="font-medium">{{ t(`groupBuy.requestType.${request.type}`) }}</span>
                            <PointShopTag :tone="requestTone(request.status)">{{ t(`groupBuy.requestStatus.${request.status}`) }}</PointShopTag>
                            <PointShopTag v-if="request.resolution === 'EXCHANGE'" tone="primary">{{ t('groupBuy.resolution.EXCHANGE') }}</PointShopTag>
                            <span class="text-xs tabular-nums text-muted-color">{{ time.listLabel(request.createdAt) }}</span>
                        </div>
                        <p class="m-0 text-xs text-muted-color">
                            <span v-for="(item, index) in request.items" :key="item.skuId">
                                <span v-if="index > 0"> · </span><template v-if="item.kind === 'ADDON'">[{{ t('groupBuy.addonTag') }}] </template>{{ item.skuName }} {{ t('groupBuy.qtyCount', { count: item.qty }) }}<template v-if="item.reason"> ({{ t(`groupBuy.requestReason.${item.reason}`) }})</template>
                            </span>
                            <template v-if="request.collectionMethod"> · {{ t(`groupBuy.collection.${request.collectionMethod}`) }}</template>
                        </p>
                        <p v-if="request.refundAmount !== null || request.previewAmount !== null" class="m-0 text-xs tabular-nums">
                            {{ request.refundAmount !== null ? t('groupBuy.requestRefunded', { amount: UtilGroupBuyMessages.won(t, request.refundAmount) }) : t('groupBuy.requestPreview', { amount: UtilGroupBuyMessages.signedWon(t, request.previewAmount ?? 0) }) }}
                        </p>
                        <p v-if="request.pickupTrackingNo" class="m-0 text-xs">{{ t('groupBuy.pickupTracking', { no: request.pickupTrackingNo }) }}</p>
                        <p v-if="request.status === 'REJECTED' && request.rejectReasonCode" class="m-0 text-xs text-orange-700 dark:text-orange-400">{{ t(`groupBuy.rejectReason.${request.rejectReasonCode}`) }}</p>
                        <p v-if="request.status === 'AWAITING_PAYMENT' && request.returnFeeDue" class="m-0 text-xs font-medium text-orange-700 dark:text-orange-400">{{ t('groupBuy.awaitingFee', { amount: UtilGroupBuyMessages.won(t, request.returnFeeDue) }) }}</p>
                        <div v-if="request.actions.withdraw || request.actions.payReturnFee" class="flex flex-wrap gap-2 pt-1">
                            <Button v-if="request.actions.payReturnFee" type="button" :label="t('groupBuy.payReturnFee', { amount: UtilGroupBuyMessages.won(t, request.returnFeeDue ?? 0) })" icon="pi pi-credit-card" size="small" :loading="actionBusy === `fee-${request.id}` || pay.busy.value" @click="payReturnFee(request.id)" />
                            <Button v-if="request.actions.withdraw" type="button" :label="t('groupBuy.withdraw')" size="small" severity="secondary" variant="outlined" :loading="actionBusy === `withdraw-${request.id}`" @click="withdraw(request.id)" />
                        </div>
                    </li>
                </ul>
            </section>

            <!-- 배송 -->
            <section :aria-labelledby="`${uid}-ship`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="my-order-shipping">
                <h2 :id="`${uid}-ship`" class="m-0 mb-2 text-base font-semibold">{{ t('groupBuy.shippingTitle') }}</h2>
                <p v-if="!order.shipment" class="m-0 text-sm text-muted-color">{{ UtilGroupBuyMessages.shipPlan(t, time, order) || t('groupBuy.notShippedYet') }}</p>
                <dl v-else class="m-0 grid grid-cols-[minmax(0,6rem)_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm">
                    <dt class="text-muted-color">{{ t('groupBuy.carrier') }}</dt>
                    <dd class="m-0">{{ order.shipment.carrierName }}<template v-if="order.shipment.kind === 'EXCHANGE'"> · {{ t('groupBuy.exchangeShipment') }}</template></dd>
                    <dt class="text-muted-color">{{ t('groupBuy.trackingNo') }}</dt>
                    <dd class="m-0 flex flex-wrap items-center gap-2">
                        <span class="tabular-nums">{{ order.shipment.trackingNo }}</span>
                        <a v-if="order.shipment.trackingUrl" :href="order.shipment.trackingUrl" target="_blank" rel="noopener noreferrer" class="text-primary hover:underline">{{ t('groupBuy.trackOpen') }}<span class="sr-only"> {{ t('groupBuy.newWindow') }}</span></a>
                    </dd>
                    <dt class="text-muted-color">{{ t('groupBuy.shippedAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(order.shipment.shippedAt) }}</dd>
                    <dt class="text-muted-color">{{ t('groupBuy.deliveredAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ order.shipment.deliveredAt ? time.full(order.shipment.deliveredAt) : '—' }}</dd>
                </dl>
            </section>

            <!-- 배송지 — 가린 값 · 전체 보기 · 수정(기한 안 — 서버 actions) -->
            <section :aria-labelledby="`${uid}-addr`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="my-order-address">
                <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <h2 :id="`${uid}-addr`" class="m-0 text-base font-semibold">{{ t('groupBuy.addressTitle') }}</h2>
                    <div v-if="!editing && !order.address.purged" class="flex gap-1">
                        <Button
                            v-if="order.actions.revealAddress"
                            type="button"
                            size="small"
                            variant="text"
                            :icon="revealed ? 'pi pi-eye-slash' : 'pi pi-eye'"
                            :label="revealed ? t('groupBuy.addrHide') : t('groupBuy.addrReveal')"
                            :aria-pressed="!!revealed"
                            :loading="actionBusy === 'reveal'"
                            data-testid="my-order-address-reveal"
                            @click="toggleReveal"
                        />
                        <Button v-if="order.actions.editAddress" type="button" size="small" variant="text" icon="pi pi-pencil" :label="t('common.edit')" :loading="actionBusy === 'editOpen'" data-testid="my-order-address-edit" @click="startEdit" />
                    </div>
                </div>
                <p v-if="order.address.purged" class="m-0 text-sm text-muted-color">{{ t('groupBuy.addrPurged') }}</p>
                <form v-else-if="editing" class="flex flex-col gap-3" novalidate @submit.prevent="saveAddress">
                    <GroupBuyAddressFields v-model="addrForm" :errors="addrErrors" :disabled="actionBusy === 'address'" manual-first />
                    <Message v-if="addrFormError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ addrFormError }}</Message>
                    <div class="flex gap-2">
                        <Button type="submit" :label="t('common.save')" :loading="actionBusy === 'address'" />
                        <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="cancelEdit" />
                    </div>
                </form>
                <template v-else>
                    <dl class="m-0 grid grid-cols-[minmax(0,6rem)_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm">
                        <dt class="text-muted-color">{{ t('groupBuy.addrRecipient') }}</dt>
                        <dd class="m-0">{{ shownAddress.recipient }}</dd>
                        <dt class="text-muted-color">{{ t('groupBuy.addrPhone') }}</dt>
                        <dd class="m-0 tabular-nums">{{ shownAddress.phone }}</dd>
                        <dt class="text-muted-color">{{ t('groupBuy.addrLine1') }}</dt>
                        <dd class="m-0">({{ shownAddress.postcode }}) {{ shownAddress.address1 }} {{ shownAddress.address2 }}</dd>
                        <template v-if="shownAddress.memo">
                            <dt class="text-muted-color">{{ t('groupBuy.addrMemo') }}</dt>
                            <dd class="m-0">{{ shownAddress.memo }}</dd>
                        </template>
                    </dl>
                    <p v-if="order.actions.editAddress && order.addressEditableUntilText" class="m-0 mt-2 text-xs text-muted-color">{{ t('groupBuy.addrEditUntil', { until: order.addressEditableUntilText }) }}</p>
                </template>
            </section>
        </template>

        <!-- 취소·반품 대화상자(이 화면 전용) -->
        <Dialog
            v-model:visible="dialogOpen"
            modal
            :draggable="false"
            :closable="!dialogBusy"
            :close-on-escape="!dialogBusy"
            :header="dialogMode ? t(`groupBuy.dialogTitle.${dialogMode}`) : ''"
            :style="{ width: 'min(36rem, calc(100vw - 2rem))' }"
            data-testid="my-order-dialog"
        >
            <form v-if="order && dialogMode" :id="dialogFormId" class="flex flex-col gap-4" novalidate @submit.prevent="submitDialog">
                <p class="m-0 text-sm text-muted-color">{{ t(`groupBuy.dialogHelp.${dialogMode}`) }}</p>

                <!-- 줄 · 수량 · (반품) 줄마다 사유 -->
                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                    <legend class="mb-1 text-sm font-medium">{{ t('groupBuy.pickLines') }}</legend>
                    <div v-for="pick in picks" :key="pick.item.id" class="flex flex-col gap-2 rounded-lg border border-surface p-3">
                        <label class="flex cursor-pointer items-center gap-2 text-sm">
                            <Checkbox
                                :model-value="pick.on"
                                binary
                                :input-id="`${dialogFormId}-pick-${pick.item.id}`"
                                :disabled="dialogBusy"
                                @update:model-value="(on: boolean) => setPicked(pick, on)"
                            />
                            <span class="inline-flex min-w-0 flex-1 flex-wrap items-center gap-1.5">
                                <PointShopTag v-if="pick.item.kind === 'ADDON'" tone="neutral">{{ t('groupBuy.addonTag') }}</PointShopTag>
                                <span class="font-medium">{{ pick.item.skuName }}</span>
                            </span>
                            <span class="text-xs tabular-nums text-muted-color">{{ t('groupBuy.selectable', { count: pick.item.selectableQty }) }}</span>
                        </label>
                        <div v-if="pick.on" class="flex flex-wrap items-start gap-2 pl-7">
                            <LabeledField class="w-32" :label="t('groupBuy.lineQty')">
                                <template #default="{ id: fieldId }">
                                    <InputNumber v-model="pick.qty" :input-id="fieldId" :min="1" :max="pick.item.selectableQty" show-buttons button-layout="horizontal" :disabled="dialogBusy" fluid />
                                </template>
                            </LabeledField>
                            <LabeledField v-if="dialogMode === 'RETURN'" class="min-w-0 flex-1 basis-44" :label="t('groupBuy.lineReason')">
                                <template #default="{ id: fieldId }">
                                    <Select v-model="pick.reason" :label-id="fieldId" :options="reasonOptions" option-label="label" option-value="value" :placeholder="t('groupBuy.reasonPlaceholder')" :disabled="dialogBusy" fluid />
                                </template>
                            </LabeledField>
                        </div>
                    </div>
                </fieldset>

                <!-- 본 상품 줄을 모두 골랐는데 추가 구성상품이 남는다 — 함께 고를지 묻는다(결정 88) -->
                <div v-if="addonsLeft" class="flex flex-col gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-950 dark:bg-amber-950/40 dark:text-amber-100" data-testid="my-order-addons-ask">
                    <p class="m-0">{{ t(`groupBuy.pickAddonsAsk.${dialogMode}`) }}</p>
                    <Button type="button" size="small" severity="secondary" variant="outlined" class="w-fit" :label="t('groupBuy.pickAddonsButton')" :disabled="dialogBusy" @click="pickAddons" />
                </div>

                <!-- 하자·오배송 — 사진(최대 3) · 환불/교환(결정 54) -->
                <template v-if="dialogMode === 'RETURN' && hasCompanyFault">
                    <div class="flex flex-col gap-2">
                        <span class="text-sm font-medium">{{ t('groupBuy.photosTitle', { max: UtilGroupBuy.PHOTO_MAX }) }}</span>
                        <div class="flex flex-wrap gap-2">
                            <div v-for="photo in photos" :key="photo.id" class="relative size-20 overflow-hidden rounded-lg border border-surface">
                                <img :src="photo.url" :alt="t('groupBuy.photoAlt')" class="size-full object-cover" />
                                <Button type="button" icon="pi pi-times" size="small" rounded severity="secondary" class="absolute! right-0.5 top-0.5 size-6!" :aria-label="t('groupBuy.photoRemove')" @click="removePhoto(photo.id)" />
                            </div>
                            <label v-if="photos.length < UtilGroupBuy.PHOTO_MAX" class="grid size-20 cursor-pointer place-items-center rounded-lg border border-dashed border-surface text-muted-color hover:border-primary focus-within:outline-2 focus-within:outline-primary">
                                <i v-if="!photoBusy" class="pi pi-camera" aria-hidden="true" />
                                <i v-else class="pi pi-spin pi-spinner" aria-hidden="true" />
                                <span class="sr-only">{{ t('groupBuy.photoAdd') }}</span>
                                <input type="file" accept="image/jpeg,image/png,image/webp" class="sr-only" :disabled="photoBusy || dialogBusy" @change="addPhoto" />
                            </label>
                        </div>
                        <p v-if="photoError" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ photoError }}</p>
                    </div>
                    <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                        <legend class="mb-1 text-sm font-medium">{{ t('groupBuy.resolutionTitle') }}</legend>
                        <label v-for="value in (['REFUND', 'EXCHANGE'] as const)" :key="value" class="flex cursor-pointer items-start gap-2 text-sm">
                            <RadioButton v-model="resolution" :value="value" :input-id="`${dialogFormId}-res-${value}`" name="resolution" class="mt-0.5" :disabled="dialogBusy || (value === 'EXCHANGE' && !exchangeAllowed)" />
                            <span>
                                <span class="block">{{ t(`groupBuy.resolution.${value}`) }}</span>
                                <span class="block text-xs text-muted-color">{{ t(`groupBuy.resolutionHelp.${value}`) }}</span>
                            </span>
                        </label>
                        <p v-if="!exchangeAllowed" class="m-0 text-xs text-muted-color">{{ t('groupBuy.exchangeOnlyDefect') }}</p>
                    </fieldset>
                </template>
                <p v-if="dialogMode === 'RETURN' && !hasCompanyFault && picked.length > 0" class="m-0 text-xs text-muted-color">{{ t('groupBuy.noExchangeForMind') }}</p>

                <LabeledField v-if="dialogMode !== 'CANCEL_NOW'" :label="t('groupBuy.reasonText')" :hint="t('groupBuy.reasonTextHint')">
                    <template #default="{ id: fieldId, describedBy }">
                        <Textarea :id="fieldId" v-model="reasonText" rows="2" maxlength="500" auto-resize :aria-describedby="describedBy" :disabled="dialogBusy" fluid />
                    </template>
                </LabeledField>

                <!-- 미리 보기(서버 계산) -->
                <section class="flex flex-col gap-2" aria-live="polite" data-testid="my-order-preview">
                    <h3 class="m-0 text-sm font-semibold">{{ t('groupBuy.previewTitle') }}</h3>
                    <p v-if="!selectionReady" class="m-0 text-sm text-muted-color">{{ t('groupBuy.previewPending') }}</p>
                    <p v-else-if="previewBusy && !cancelPreview && !returnPreview" class="m-0 text-sm text-muted-color" role="status">{{ t('groupBuy.previewing') }}</p>
                    <Message v-else-if="previewError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ previewError }}</Message>
                    <template v-else-if="dialogMode !== 'RETURN' && cancelPreview">
                        <dl class="m-0 grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1 text-sm" :class="previewBusy ? 'opacity-60' : ''">
                            <dt class="text-muted-color">{{ t('groupBuy.itemRefund') }}</dt>
                            <dd class="m-0 text-right tabular-nums">{{ UtilGroupBuyMessages.won(t, cancelPreview.itemRefund) }}</dd>
                            <template v-if="cancelPreview.shippingRefund > 0">
                                <dt class="text-muted-color">{{ t('groupBuy.shippingRefund') }}</dt>
                                <dd class="m-0 text-right tabular-nums">{{ UtilGroupBuyMessages.won(t, cancelPreview.shippingRefund) }}</dd>
                            </template>
                            <template v-if="cancelPreview.reshippingFee > 0">
                                <dt class="text-muted-color">{{ t('groupBuy.reshippingFee') }}</dt>
                                <dd class="m-0 text-right tabular-nums">−{{ UtilGroupBuyMessages.won(t, cancelPreview.reshippingFee) }}</dd>
                            </template>
                            <dt class="font-semibold">{{ t('groupBuy.refundAmount') }}</dt>
                            <dd class="m-0 text-right font-bold tabular-nums" data-testid="my-order-preview-amount">{{ UtilGroupBuyMessages.won(t, cancelPreview.refundAmount) }}</dd>
                        </dl>
                        <p v-if="cancelPreview.reshippingFee > 0" class="m-0 text-xs text-muted-color">{{ t('groupBuy.reshippingHelp') }}</p>
                    </template>
                    <template v-else-if="dialogMode === 'RETURN' && returnPreview">
                        <p v-if="resolution === 'EXCHANGE'" class="m-0 text-sm">{{ t('groupBuy.exchangePreview') }}</p>
                        <template v-else>
                            <p class="m-0 text-xs text-muted-color">{{ t(`groupBuy.feeBearer.${returnPreview.feeBearer}`) }}</p>
                            <!-- 회수 방식별 결과를 나란히(결정 68) -->
                            <div class="grid gap-2 sm:grid-cols-2" role="radiogroup" :aria-label="t('groupBuy.collectionTitle')" :class="previewBusy ? 'opacity-60' : ''">
                                <label
                                    v-for="method in (['COMPANY_PICKUP', 'CUSTOMER_SEND'] as const)"
                                    :key="method"
                                    class="flex cursor-pointer flex-col gap-1 rounded-lg border-2 p-3 text-sm"
                                    :class="collection === method ? 'border-primary' : 'border-surface'"
                                    :data-testid="`my-order-method-${method}`"
                                >
                                    <span class="flex items-center gap-2">
                                        <RadioButton v-model="collection" :value="method" :input-id="`${dialogFormId}-m-${method}`" name="collection" :disabled="dialogBusy" />
                                        <span class="font-medium">{{ t(`groupBuy.collection.${method}`) }}</span>
                                    </span>
                                    <span class="text-lg font-bold tabular-nums" :class="returnPreview.byMethod[method].refundAmount < 0 ? 'text-red-600 dark:text-red-400' : 'text-primary'">
                                        {{ UtilGroupBuyMessages.signedWon(t, returnPreview.byMethod[method].refundAmount) }}
                                    </span>
                                    <span class="text-xs text-muted-color">{{ outcomeLine(method) }}</span>
                                </label>
                            </div>
                            <p v-if="collection === 'CUSTOMER_SEND'" class="m-0 text-xs text-muted-color">{{ t('groupBuy.customerSendHelp', { address: returnPreview.returnAddress }) }}</p>
                            <!-- 마이너스 — 직접 발송으로 바꾸기 / 부족분 추가 결제하고 접수 -->
                            <div v-if="shortfall > 0" class="flex flex-col gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-950 dark:bg-amber-950/40 dark:text-amber-100" data-testid="my-order-shortfall">
                                <p class="m-0">{{ t('groupBuy.shortfallText', { amount: UtilGroupBuyMessages.won(t, shortfall) }) }}</p>
                                <div class="flex flex-wrap gap-2">
                                    <Button
                                        v-if="collection === 'COMPANY_PICKUP'"
                                        type="button"
                                        size="small"
                                        severity="secondary"
                                        variant="outlined"
                                        :label="t('groupBuy.switchToSend', { amount: UtilGroupBuyMessages.signedWon(t, returnPreview.byMethod.CUSTOMER_SEND.refundAmount) })"
                                        @click="collection = 'CUSTOMER_SEND'"
                                    />
                                </div>
                                <p class="m-0 text-xs">{{ t('groupBuy.shortfallHelp') }}</p>
                            </div>
                        </template>
                    </template>
                </section>

                <Message v-if="dialogError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ dialogError }}</Message>
            </form>
            <template #footer>
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="dialogBusy" @click="dialogOpen = false" />
                <Button type="submit" :form="dialogFormId" :label="submitLabel" :loading="dialogBusy" :disabled="!canSubmitDialog" :severity="dialogMode === 'RETURN' ? undefined : 'danger'" data-testid="my-order-dialog-submit" />
            </template>
        </Dialog>

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
import type { GroupBuyAddressInput, GroupBuyPaymentStart } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type {
    GroupBuyCancelPreview,
    GroupBuyCollectionMethod,
    GroupBuyRequestReason,
    GroupBuyRequestStatus,
    GroupBuyResolution,
    GroupBuyReturnPreview,
    GroupBuySupplyChoiceValue,
    MyGroupBuyAddress,
    MyGroupBuyOrderDetail,
    MyGroupBuyOrderItem,
    GroupBuyRequestPhoto,
} from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type { PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilCommerce from '~/utils/util-commerce';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';
import UtilIdempotency from '~/utils/util-idempotency';
import UtilTime from '#layers/display-time/utils/util-time';

/**
 * 내 주문 상세 `/me/orders/{orderNo}` — 로그인 필요(본인 주문만). 계약 commerce-groupbuy 화면 목록 · 결정 16·30·42·44·52·53·54·63·68.
 * - 주문 줄(단품 이름 · 추가 구성상품은 「추가 구성」 딱지 · 수량·단가·취소·반품 수량 — 결정 88)·결제·배송(택배사·송장·조회 링크)·환불 진행(진행 중 · 카드사 반영 3~7영업일 / 완료 / 지연 / 운영자 처리).
 * - 결제 대기면 선점 남은 시간 + 「결제 다시 열기」. 포트원 모바일 리디렉션은 쿼리 없이 이 화면으로 돌아오고 `resumeRedirect` 가 이어 간다(결정 13).
 * - 배송지: 가린 값 + 「전체 보기」(화면 메모리에만) + 수정(기한 안 — 서버 `actions.editAddress`).
 * - 본인 즉시 취소·취소 요청·반품 요청: 줄·수량을 고르고(반품은 줄마다 사유), 환불 예정 금액은 서버 미리 보기만. 하자·오배송은 사진 3장 + 환불/교환.
 *   반품은 회수 방식별 결과를 나란히 보이고, 마이너스이면 「직접 발송으로 바꾸기」 또는 「부족분 결제하고 접수」(같은 결제창 흐름 — 결제해야 접수).
 *   본 상품 줄을 모두 고르고 추가 구성상품이 남으면 「함께 고를까요?」를 대화상자 안에서 묻는다. 본인 즉시 취소가 409 `GROUP_BUY_ADDON_ONLY` 면 확인 뒤 함께 다시 보낸다.
 * - 요청 철회 · 요청 상태(거절 사유 코드 문구 포함) · 발송 지연 공지의 「바로 전액 취소」 · 단품 공급 불가 「나머지만 받기 / 전체 취소」.
 * 단추는 모두 서버 `actions` 로만 보인다(감추는 것은 편의, 막는 것은 서버).
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
const route = useRoute();
const time = useDisplayTime();
const api = useGroupBuyOrderApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const uid = useId();
const dialogFormId = useId();

const orderNo = computed(() => String(route.params.orderNo ?? ''));
const order = ref<MyGroupBuyOrderDetail | null>(null);
const status = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const loadError = ref('');
let seq = 0;
useSeoMeta({ title: () => t('groupBuy.orderDetailTitle'), robots: 'noindex' });

const holdEndsAt = ref(0);
const hold = useCountdown(holdEndsAt);

async function load(quiet: boolean): Promise<void> {
    const mine = ++seq;
    if (!quiet) status.value = 'loading';
    loadError.value = '';
    try {
        const detail = await api.get(orderNo.value);
        if (mine !== seq) return;
        apply(detail);
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (quiet && order.value) {
            actionError.value = UtilGroupBuyMessages.error(t, e);
            return;
        }
        loadError.value = UtilGroupBuyMessages.error(t, e);
        status.value = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error';
    }
}

function apply(detail: MyGroupBuyOrderDetail): void {
    order.value = detail;
    holdEndsAt.value = detail.reservedUntil ? Date.parse(detail.reservedUntil) : 0;
}

watch(orderNo, () => {
    revealed.value = null;
    editing.value = false;
    void load(false);
});
onBeforeUnmount(() => seq++);

/* ── 결제(다시 열기 · 반품비 부족분) ── */

const payMessage = ref<{ text: string; severity: 'success' | 'warn' } | null>(null);
const payBusy = ref(false);
const pay = useGroupBuyCheckout(onPaid);

function onPaid(result: PaymentResult, target: GroupBuyPaymentStart): void {
    if (result.accepted) {
        const text = target.orderType === 'GROUP_BUY_RETURN_FEE' ? t('groupBuy.returnFeePaid') : t('groupBuy.paidToast');
        toast.success(text);
        payMessage.value = { text, severity: 'success' };
    } else {
        payMessage.value = { text: result.anomaly ? t(`groupBuy.payAnomaly.${result.anomaly}`) : t('groupBuy.payNotAccepted'), severity: 'warn' };
    }
    void load(true);
}

onMounted(async () => {
    // 모바일 리디렉션에서 돌아왔으면 이어 간다(같은 결제 번호일 때만) — 그 뒤 주문을 읽는다
    // (이어 간 결제의 결과는 `onPaid` 가 조용히 다시 읽는다 — 그래도 아직 주문이 없으면 여기서 읽는다)
    await pay.resumeRedirect();
    if (!order.value) void load(false);
});

async function payAgain(): Promise<void> {
    const current = order.value;
    if (!current || payBusy.value || pay.busy.value) return;
    payBusy.value = true;
    payMessage.value = null;
    try {
        const target = await api.reopenPayment(current.orderNo);
        holdEndsAt.value = Date.parse(target.expiresAt);
        pay.open(target);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'GROUP_BUY_HOLD_EXPIRED') hold.stop();
        payMessage.value = { text: UtilGroupBuyMessages.error(t, e), severity: 'warn' };
    } finally {
        payBusy.value = false;
    }
}

async function payReturnFee(requestId: string): Promise<void> {
    const current = order.value;
    if (!current || actionBusy.value || pay.busy.value) return;
    actionBusy.value = `fee-${requestId}`;
    payMessage.value = null;
    try {
        pay.open(await api.requestFeePayment(current.orderNo, requestId));
    } catch (e) {
        actionError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        actionBusy.value = null;
    }
}

/* ── 주문 단위 동작(지연 취소 · 공급 불가 · 철회) ── */

const actionBusy = ref<string | null>(null);
const actionError = ref('');

async function run(key: string, task: () => Promise<MyGroupBuyOrderDetail>, success: string): Promise<void> {
    if (actionBusy.value) return;
    actionBusy.value = key;
    actionError.value = '';
    try {
        apply(await task());
        toast.success(success);
    } catch (e) {
        actionError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        actionBusy.value = null;
    }
}

async function delayCancel(): Promise<void> {
    const current = order.value;
    if (!current?.delayNotice) return;
    const ok = await confirm({
        title: t('groupBuy.delayCancelConfirm'),
        message: t('groupBuy.delayCancelConfirmText', { amount: UtilGroupBuyMessages.won(t, current.delayNotice.refundAmount) }),
        confirmLabel: t('groupBuy.delayCancel'),
        tone: 'danger',
    });
    if (ok) await run('delay', () => api.delayCancel(current.orderNo), t('groupBuy.canceledToast'));
}

async function supplyChoice(choice: GroupBuySupplyChoiceValue): Promise<void> {
    const current = order.value;
    if (!current?.supplyChoice) return;
    if (choice === 'CANCEL_ALL') {
        const ok = await confirm({
            title: t('groupBuy.supplyCancelConfirm'),
            message: t('groupBuy.supplyCancelConfirmText', { amount: UtilGroupBuyMessages.won(t, current.supplyChoice.cancelAllRefund) }),
            confirmLabel: t('groupBuy.supplyCancelAllShort'),
            tone: 'danger',
        });
        if (!ok) return;
    }
    await run(choice, () => api.supplyChoice(current.orderNo, { choice }), t(`groupBuy.supplyChosen.${choice}`));
}

async function withdraw(requestId: string): Promise<void> {
    const current = order.value;
    if (!current) return;
    const ok = await confirm({ title: t('groupBuy.withdrawConfirm'), message: t('groupBuy.withdrawConfirmText'), confirmLabel: t('groupBuy.withdraw') });
    if (ok) await run(`withdraw-${requestId}`, () => api.withdrawRequest(current.orderNo, requestId), t('groupBuy.withdrawnToast'));
}

function requestTone(s: GroupBuyRequestStatus): 'neutral' | 'primary' | 'success' | 'warn' | 'danger' {
    if (s === 'COMPLETED') return 'success';
    if (s === 'REJECTED') return 'danger';
    if (s === 'AWAITING_PAYMENT') return 'warn';
    if (s === 'WITHDRAWN' || s === 'SUPERSEDED') return 'neutral';
    return 'primary';
}

/* ── 배송지 ── */

/** 「전체 보기」로 받은 값 — 화면 메모리에만(저장소·주소줄에 남기지 않는다) */
const revealed = ref<MyGroupBuyAddress | null>(null);
const shownAddress = computed(() => revealed.value ?? order.value!.address);

async function fetchFull(): Promise<MyGroupBuyAddress | null> {
    const current = order.value;
    if (!current) return null;
    try {
        return await api.revealAddress(current.orderNo);
    } catch (e) {
        actionError.value = UtilGroupBuyMessages.error(t, e);
        return null;
    }
}

async function toggleReveal(): Promise<void> {
    if (revealed.value) {
        revealed.value = null;
        return;
    }
    if (actionBusy.value) return;
    actionBusy.value = 'reveal';
    actionError.value = '';
    revealed.value = await fetchFull();
    actionBusy.value = null;
}

const editing = ref(false);
const addrForm = ref<GroupBuyAddressInput>({ recipient: '', phone: '', postcode: '', address1: '', address2: '', memo: '' });
const addrErrors = ref<Record<string, string>>({});
const addrFormError = ref('');

/** 수정은 전체 값에서 시작한다(가린 값을 고치게 하지 않는다) */
async function startEdit(): Promise<void> {
    if (actionBusy.value) return;
    actionBusy.value = 'editOpen';
    actionError.value = '';
    const full = revealed.value ?? (await fetchFull());
    actionBusy.value = null;
    if (!full) return;
    addrForm.value = { recipient: full.recipient, phone: full.phone, postcode: full.postcode, address1: full.address1, address2: full.address2, memo: full.memo };
    addrErrors.value = {};
    addrFormError.value = '';
    editing.value = true;
}

function cancelEdit(): void {
    editing.value = false;
    addrForm.value = { recipient: '', phone: '', postcode: '', address1: '', address2: '', memo: '' };
}

async function saveAddress(): Promise<void> {
    const current = order.value;
    if (!current || actionBusy.value) return;
    actionBusy.value = 'address';
    addrErrors.value = {};
    addrFormError.value = '';
    try {
        apply(await api.updateAddress(current.orderNo, { ...addrForm.value }));
        revealed.value = null;
        cancelEdit();
        toast.success(t('groupBuy.addrSaved'));
    } catch (e) {
        if (e instanceof ApiError && e.status === 400 && e.fields && Object.keys(e.fields).length) addrErrors.value = e.fields;
        else addrFormError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        actionBusy.value = null;
    }
}

/* ── 취소·반품 대화상자 ── */

type DialogMode = 'CANCEL_NOW' | 'CANCEL_REQUEST' | 'RETURN';
interface Pick {
    item: MyGroupBuyOrderItem;
    on: boolean;
    qty: number | null;
    reason: GroupBuyRequestReason | null;
}

const dialogOpen = ref(false);
const dialogMode = ref<DialogMode | null>(null);
const dialogBusy = ref(false);
const dialogError = ref('');
const picks = ref<Pick[]>([]);
const reasonText = ref('');
const photos = ref<GroupBuyRequestPhoto[]>([]);
const photoBusy = ref(false);
const photoError = ref('');
const resolution = ref<GroupBuyResolution>('REFUND');
const collection = ref<GroupBuyCollectionMethod>('COMPANY_PICKUP');
const cancelPreview = ref<GroupBuyCancelPreview | null>(null);
const returnPreview = ref<GroupBuyReturnPreview | null>(null);
const previewBusy = ref(false);
const previewError = ref('');
let previewSeq = 0;
let previewTimer: ReturnType<typeof setTimeout> | null = null;
let dialogKey = UtilIdempotency.newKey();

const reasonOptions = computed(() => (['CHANGE_OF_MIND', 'DEFECT', 'WRONG_ITEM', 'OTHER'] as const).map((value) => ({ value, label: t(`groupBuy.requestReason.${value}`) })));

function openDialog(mode: DialogMode): void {
    const current = order.value;
    if (!current) return;
    dialogMode.value = mode;
    picks.value = current.items.filter((i) => i.selectableQty > 0).map((item) => ({ item, on: false, qty: null, reason: null }));
    reasonText.value = '';
    photos.value = [];
    photoError.value = '';
    resolution.value = 'REFUND';
    collection.value = 'COMPANY_PICKUP';
    cancelPreview.value = null;
    returnPreview.value = null;
    previewError.value = '';
    dialogError.value = '';
    dialogKey = UtilIdempotency.newKey();
    dialogOpen.value = true;
}

/** 줄 고르기 — 처음 고르면 수량을 1로 채운다(빈 칸이면 「+」를 한 번 더 눌러야 했다) */
function setPicked(pick: Pick, on: boolean): void {
    pick.on = on;
    if (on && pick.qty === null) pick.qty = 1;
}

const picked = computed(() => picks.value.filter((p) => p.on && p.qty !== null && p.qty >= 1));
const hasCompanyFault = computed(() => picked.value.some((p) => UtilGroupBuy.isCompanyFault(p.reason)));
/** 교환은 고른 줄이 모두 하자·오배송일 때만(결정 54) */
const exchangeAllowed = computed(() => picked.value.length > 0 && picked.value.every((p) => UtilGroupBuy.isCompanyFault(p.reason)));
const selectionReady = computed(() => {
    if (picked.value.length === 0 || picks.value.some((p) => p.on && (p.qty === null || p.qty < 1))) return false;
    return dialogMode.value !== 'RETURN' || picked.value.every((p) => p.reason !== null);
});

watch(exchangeAllowed, (allowed) => {
    if (!allowed) resolution.value = 'REFUND';
});

/** 고른 회수 방식의 부족분(결정 68) — 0 이면 없음 */
const shortfall = computed(() => {
    if (dialogMode.value !== 'RETURN' || resolution.value === 'EXCHANGE' || !returnPreview.value) return 0;
    const amount = returnPreview.value.byMethod[collection.value].refundAmount;
    return amount < 0 ? -amount : 0;
});

/** 지금 보낼 금액(비교용 `expectedAmount`) */
const expectedAmount = computed<number | null>(() => {
    if (dialogMode.value === 'RETURN') {
        if (resolution.value === 'EXCHANGE') return 0;
        return returnPreview.value ? returnPreview.value.byMethod[collection.value].refundAmount : null;
    }
    return cancelPreview.value?.refundAmount ?? null;
});

const canSubmitDialog = computed(() => selectionReady.value && !previewBusy.value && !photoBusy.value && expectedAmount.value !== null && !previewError.value);

const submitLabel = computed(() => {
    if (dialogMode.value === 'RETURN' && shortfall.value > 0) return t('groupBuy.payShortfallAndSubmit', { amount: UtilGroupBuyMessages.won(t, shortfall.value) });
    return dialogMode.value ? t(`groupBuy.dialogSubmit.${dialogMode.value}`) : '';
});

function outcomeLine(method: GroupBuyCollectionMethod): string {
    const o = returnPreview.value?.byMethod[method];
    if (!o) return '';
    const parts = [t('groupBuy.outcomeItem', { amount: UtilGroupBuyMessages.won(t, o.itemRefund) })];
    if (o.shippingRefund > 0) parts.push(t('groupBuy.outcomeShipping', { amount: UtilGroupBuyMessages.won(t, o.shippingRefund) }));
    if (o.reshippingFee > 0) parts.push(t('groupBuy.outcomeReshipping', { amount: UtilGroupBuyMessages.won(t, o.reshippingFee) }));
    if (o.returnFee > 0) parts.push(t('groupBuy.outcomeReturnFee', { amount: UtilGroupBuyMessages.won(t, o.returnFee) }));
    if (o.compensationEligible) parts.push(t('groupBuy.outcomeCompensation'));
    return parts.join(' · ');
}

async function preview(): Promise<void> {
    const current = order.value;
    if (!current || !selectionReady.value) return;
    const mine = ++previewSeq;
    previewBusy.value = true;
    previewError.value = '';
    try {
        if (dialogMode.value === 'RETURN') {
            const res = await api.returnPreview(current.orderNo, { items: picked.value.map((p) => ({ skuId: p.item.skuId, qty: p.qty!, reason: p.reason! })) });
            if (mine === previewSeq) returnPreview.value = res;
        } else {
            const res = await api.cancelPreview(current.orderNo, { lines: cancelLines() });
            if (mine === previewSeq) cancelPreview.value = res;
        }
    } catch (e) {
        if (mine === previewSeq) previewError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        if (mine === previewSeq) previewBusy.value = false;
    }
}

watch(
    () => JSON.stringify(picks.value.map((p) => [p.item.id, p.on, p.qty, p.reason])),
    () => {
        if (!dialogOpen.value) return;
        if (previewTimer) clearTimeout(previewTimer);
        cancelPreview.value = null;
        returnPreview.value = null;
        previewError.value = '';
        if (selectionReady.value) previewTimer = setTimeout(() => void preview(), 300);
    },
);
onBeforeUnmount(() => {
    if (previewTimer) clearTimeout(previewTimer);
    previewSeq++;
});

async function addPhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const current = order.value;
    if (!file || !current || photos.value.length >= UtilGroupBuy.PHOTO_MAX) return;
    photoBusy.value = true;
    photoError.value = '';
    try {
        photos.value = [...photos.value, await api.uploadPhoto(current.orderNo, file)];
    } catch (e) {
        photoError.value = e instanceof ApiError && e.fields?.file ? e.fields.file : UtilGroupBuyMessages.error(t, e);
    } finally {
        photoBusy.value = false;
    }
}

function removePhoto(id: string): void {
    photos.value = photos.value.filter((p) => p.id !== id);
}

/** 본인 즉시 취소 줄(서버 2b-2 — 단품 단위 `lines[{ skuId, qty }]`) */
function cancelLines(): { skuId: string; qty: number }[] {
    return picked.value.map((p) => ({ skuId: p.item.skuId, qty: p.qty! }));
}

/** 본 상품(MAIN) 줄을 남김없이 골랐는데, 고르지 않은 추가 구성상품이 남는가 — 함께 고를지 묻는다(결정 88) */
const addonsLeft = computed(() => {
    const current = order.value;
    if (!current) return false;
    const live = (i: MyGroupBuyOrderItem) => i.qty - i.canceledQty - i.returnedQty;
    const mainLive = current.items.filter((i) => i.kind === 'MAIN').reduce((n, i) => n + live(i), 0);
    const mainPicked = picked.value.filter((p) => p.item.kind === 'MAIN').reduce((n, p) => n + (p.qty ?? 0), 0);
    if (mainLive === 0 || mainPicked < mainLive) return false;
    return picks.value.some((p) => p.item.kind === 'ADDON' && (!p.on || (p.qty ?? 0) < p.item.selectableQty));
});

/** 남은 추가 구성상품 줄도 고른다(고를 수 있는 수량 전부 — 반품 사유는 본 상품 줄과 같으면 그대로 따른다) */
function pickAddons(): void {
    const reasons = [...new Set(picked.value.filter((p) => p.item.kind === 'MAIN').map((p) => p.reason))];
    const reason = reasons.length === 1 ? reasons[0]! : null;
    picks.value = picks.value.map((p) => (p.item.kind === 'ADDON' ? { ...p, on: true, qty: p.item.selectableQty, reason: p.reason ?? reason } : p));
}

/** 본인 즉시 취소 — 409 `GROUP_BUY_ADDON_ONLY {skuIds}`(본 상품을 모두 취소하며 추가 구성상품을 남김)이면 함께 취소할지 묻고 함께 다시 보낸다 */
async function cancelNow(current: MyGroupBuyOrderDetail): Promise<void> {
    try {
        apply(await api.cancelNow(current.orderNo, { lines: cancelLines() }, dialogKey));
    } catch (e) {
        if (!(e instanceof ApiError && e.code === 'GROUP_BUY_ADDON_ONLY')) throw e;
        dialogKey = UtilIdempotency.newKey();
        const raw = e.extras?.skuIds;
        const skuIds = Array.isArray(raw) ? raw.map(String) : current.items.filter((i) => i.kind === 'ADDON').map((i) => i.skuId);
        const names = current.items.filter((i) => skuIds.includes(i.skuId)).map((i) => i.skuName).join(', ');
        const ok = await confirm({ title: t('groupBuy.addonOnlyTitle'), message: t('groupBuy.addonOnlyText', { names }), confirmLabel: t('groupBuy.addonOnlyConfirm'), tone: 'danger' });
        if (!ok) return;
        const lines = cancelLines();
        for (const item of current.items) if (skuIds.includes(item.skuId) && item.selectableQty > 0 && !lines.some((l) => l.skuId === item.skuId)) lines.push({ skuId: item.skuId, qty: item.selectableQty });
        apply(await api.cancelNow(current.orderNo, { lines }, dialogKey));
    }
    dialogOpen.value = false;
    toast.success(t('groupBuy.canceledToast'));
}

async function submitDialog(): Promise<void> {
    const current = order.value;
    const mode = dialogMode.value;
    if (!current || !mode || !canSubmitDialog.value || dialogBusy.value) return;
    dialogBusy.value = true;
    dialogError.value = '';
    try {
        if (mode === 'CANCEL_NOW') {
            await cancelNow(current);
            return;
        }
        const created = await api.createRequest(
            current.orderNo,
            {
                type: mode === 'RETURN' ? 'RETURN' : 'CANCEL',
                items: picked.value.map((p) => ({ skuId: p.item.skuId, qty: p.qty!, reason: mode === 'RETURN' ? p.reason : null })),
                collectionMethod: mode === 'RETURN' ? collection.value : null,
                resolution: mode === 'RETURN' ? resolution.value : null,
                reasonText: reasonText.value.trim(),
                photoIds: hasCompanyFault.value ? photos.value.map((p) => p.id) : [],
                expectedAmount: expectedAmount.value,
            },
            dialogKey,
        );
        dialogOpen.value = false;
        await load(true);
        if (created.returnFeePayment) {
            // 부족분을 결제해야 접수된다(결정 68) — 같은 결제창 흐름
            payMessage.value = { text: t('groupBuy.awaitingFee', { amount: UtilGroupBuyMessages.won(t, created.returnFeePayment.checkout.amount) }), severity: 'warn' };
            pay.open(created.returnFeePayment);
        } else toast.success(t('groupBuy.requestedToast'));
    } catch (e) {
        if (e instanceof ApiError && e.status >= 400 && e.status < 500) dialogKey = UtilIdempotency.newKey();
        if (e instanceof ApiError && e.code === 'GROUP_BUY_AMOUNT_CHANGED') {
            dialogError.value = t('groupBuy.previewChanged');
            void preview();
        } else dialogError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        dialogBusy.value = false;
    }
}
</script>
