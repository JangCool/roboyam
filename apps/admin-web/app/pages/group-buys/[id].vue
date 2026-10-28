<template>
    <!--
        공구 › 공구 등록·수정(`/group-buys/new` · `/group-buys/{id}`) — 계약 commerce-groupbuy 「화면 목록 (2b-1)」·결정 2·17·29·31·51~53·55·58.
        · DRAFT(새 공구 포함)는 모든 칸, 모집 시작 뒤에는 **설명·이미지(요약·사양·설명·대표 이미지)만** 고친다 — 나머지 칸은 읽기 전용 + 「잠김」 표시(서버 `409 GROUP_BUY_LOCKED_FIELD`).
          가격·배송비 칸은 `commerce.product.price` 가 없으면 읽기 전용이다. 저장은 화면 하나로(DRAFT `PUT` 한 번 · 모집 뒤 `PATCH …/content` 한 번) — 떠 있는 저장 줄.
        · 칸: 제목·상품명·요약·사양·설명(`@roboyam/rich-editor` 고급 수준 + 본문 이미지)·대표 이미지(8장·순서)·정상가·공구가·정상가 기준(+ 출처 메모, 「최저가」 금지 안내)·옵션·성사 수량(0 = 선착순)·발송 시점
          (즉시 발송은 선착순만)·최대 수량·1인 한도·차수 사이 한도 합산·기간·연장 방식·배송비 정책·발송 예정일(마감 후 발주) 또는 결제 후 N영업일(즉시 발송)·청약철회 제한 안내.
        · 문구 경고(결정 55·58): 「개봉하면 반품 불가」·「재판매 금지·리셀」·「최저가」·「한정」이 보이면 위에 경고(막지는 않는다).
        · 운영 동작(서버가 정한 `actions`): 모집 시작 · 조기 마감(사유) · 중단(확인 창 「환불 N명 · 총 N원 / 이미 발송 M명은 그대로」 + 사유) · 발주 처리 · 추가 차수로 열기 ·
          발송 지연 공지(새 예정일 + 사유) · 옵션 공급 불가 처리(옵션 + 사유, 결과 미리 보기) · 수정 이력(오른쪽 서랍) · 수량 화면·주문 링크.
        권한: 화면 `commerce.product.read` · 저장·모집 시작·조기 마감·추가 차수·지연 공지 `commerce.product.manage` · 중단 `commerce.groupbuy.cancel` · 발주 `commerce.order.manage`
        · 공급 불가 `commerce.order.manage` + `commerce.refund.approve`. 권한이 없는 단추는 숨긴다(막는 것은 서버).
        서버 호출은 `useGroupBuyAdminApi`(지금 목업 — 스위치는 `groupbuy-mock.ts` 머리).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="pageTitle" :subtitle="isNew ? t('groupBuy.newSubtitle') : t('groupBuy.editSubtitle')">
            <template #actions>
                <Button as="router-link" to="/group-buys" :label="t('groupBuy.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="text" />
                <Button v-if="detail" type="button" :label="t('groupBuy.revisions')" icon="pi pi-history" size="small" severity="secondary" variant="outlined" @click="openRevisions" />
            </template>
        </PageTitle>

        <!-- 읽는 중 · 실패 -->
        <div v-if="!isNew && loading && !detail" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="6rem" />
            <Skeleton v-for="n in 3" :key="n" height="10rem" />
        </div>
        <AppGadget v-else-if="!isNew && !detail" :title="t('groupBuy.detailLoadFailed')">
            <ErrorState :code="loadError?.status === 404 ? '404' : loadError?.status === 403 ? '403' : '!'" :title="loadError?.status === 403 ? t('groupBuy.forbidden') : t('groupBuy.detailLoadFailed')" :description="loadError?.message" :retry="loadError?.status !== 404 && loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>
        <div v-else-if="!options" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <ErrorState v-if="optionsError" :title="t('groupBuy.formOptionsFailed')" :description="optionsError.message" retry :retrying="loading" @retry="loadOptions" />
            <Skeleton v-else height="10rem" />
        </div>

        <template v-else>
            <!-- 상태·운영 동작 -->
            <AppGadget v-if="detail" :title="t('groupBuy.statusPanel')">
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-center gap-2">
                        <Tag :value="t(`groupBuy.status.${detail.status}`)" :severity="UtilGroupBuy.statusSeverity(detail.status)" />
                        <Tag v-if="detail.closeReason" :value="t(`groupBuy.closeReason.${detail.closeReason}`)" :severity="UtilGroupBuy.closeReasonSeverity(detail.closeReason)" />
                        <Tag v-if="detail.roundNo > 1 || detail.rounds.length > 1" :value="t('groupBuy.roundTag', { n: detail.roundNo })" severity="secondary" />
                        <span class="text-xs text-muted-color">{{ t('groupBuy.statusMeta', { qty: detail.currentQty, max: detail.maxQty, reserved: detail.reservedQty, updated: time.full(detail.updatedAt) }) }}</span>
                    </div>
                    <p v-if="detail.status === 'DRAFT'" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.draftHelp') }}</p>
                    <p v-else-if="detail.status === 'RECRUITING' && detail.startAt && detail.startAt > nowIso" class="m-0 text-xs text-muted-color">{{ t('groupBuy.scheduledHelp', { at: time.full(detail.startAt) }) }}</p>

                    <div class="flex flex-wrap items-center gap-2">
                        <Button v-if="detail.actions.canStart && canManage" type="button" :label="t('groupBuy.actStart')" icon="pi pi-play" size="small" :disabled="dirty || acting !== null" :loading="acting === 'start'" @click="doStart" />
                        <Button v-if="detail.actions.canCloseEarly && canManage" type="button" :label="t('groupBuy.actCloseEarly')" icon="pi pi-stop-circle" size="small" severity="secondary" variant="outlined" :disabled="acting !== null" @click="openCloseEarly" />
                        <Button v-if="detail.actions.canMarkOrdered && can('commerce.order.manage')" type="button" :label="t('groupBuy.actMarkOrdered')" icon="pi pi-truck" size="small" severity="secondary" variant="outlined" :disabled="acting !== null" :loading="acting === 'ordered'" @click="doMarkOrdered" />
                        <Button v-if="canManage && detail.status !== 'DRAFT'" type="button" :label="t('groupBuy.actRound')" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" :disabled="!detail.actions.canCreateRound || acting !== null" :loading="acting === 'round'" @click="doRound" />
                        <Button v-if="detail.actions.canDelayNotice && canManage" type="button" :label="t('groupBuy.actDelay')" icon="pi pi-clock" size="small" severity="secondary" variant="outlined" :disabled="acting !== null" @click="openDelay" />
                        <Button v-if="detail.actions.canSupplyFailure && canSupply" type="button" :label="t('groupBuy.actSupply')" icon="pi pi-ban" size="small" severity="secondary" variant="outlined" :disabled="acting !== null" @click="openSupply" />
                        <Button v-if="detail.actions.canCancel && can('commerce.groupbuy.cancel')" type="button" :label="t('groupBuy.actCancel')" icon="pi pi-times-circle" size="small" severity="danger" variant="outlined" :disabled="acting !== null" @click="openCancel" />
                        <span class="mx-1 hidden h-5 w-px bg-(--line) sm:inline-block" aria-hidden="true" />
                        <Button v-if="detail.status !== 'DRAFT'" as="router-link" :to="`/group-buy-stock?id=${encodeURIComponent(detail.id)}`" :label="t('groupBuy.linkStock')" icon="pi pi-box" size="small" severity="secondary" variant="text" />
                        <Button v-if="detail.status !== 'DRAFT' && can('commerce.order.read')" as="router-link" :to="`/group-buy-orders?groupBuyId=${encodeURIComponent(detail.id)}`" :label="t('groupBuy.linkOrders')" icon="pi pi-list" size="small" severity="secondary" variant="text" />
                    </div>
                    <p v-if="dirty && detail.actions.canStart" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('groupBuy.saveBeforeStart') }}</p>
                    <p v-if="canManage && detail.status !== 'DRAFT' && !detail.actions.canCreateRound && detail.actions.roundBlockedReason" class="m-0 text-xs text-muted-color">{{ t(`groupBuy.roundBlocked.${detail.actions.roundBlockedReason}`) }}</p>
                    <Message v-if="actionError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ actionError }}</Message>

                    <!-- 차수 · 지연 공지 · 공급 불가 이력 -->
                    <div v-if="detail.rounds.length > 1" class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                        <span class="text-muted-color">{{ t('groupBuy.roundsLabel') }}</span>
                        <NuxtLink v-for="r in detail.rounds" :key="r.id" :to="`/group-buys/${encodeURIComponent(r.id)}`" class="hover:underline" :class="r.id === detail.id ? 'font-semibold' : 'text-primary'">
                            {{ t('groupBuy.roundTag', { n: r.roundNo }) }} · {{ t(`groupBuy.status.${r.status}`) }}
                        </NuxtLink>
                    </div>
                    <div v-if="detail.delayNotices.length" class="flex flex-col gap-1">
                        <span class="text-xs font-medium">{{ t('groupBuy.delayHistory') }}</span>
                        <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs">
                            <li v-for="n in detail.delayNotices" :key="n.id" class="rounded border border-(--line) px-2 py-1">
                                {{ t('groupBuy.delayLine', { from: n.previousPlannedAt ?? '—', to: n.newPlannedAt, count: n.notifiedOrders }) }}
                                <span class="text-muted-color"> · {{ n.reason }} · {{ n.operator }} · {{ time.full(n.createdAt) }}</span>
                            </li>
                        </ul>
                    </div>
                    <div v-if="detail.supplyFailures.length" class="flex flex-col gap-1">
                        <span class="text-xs font-medium">{{ t('groupBuy.supplyHistory') }}</span>
                        <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs">
                            <li v-for="f in detail.supplyFailures" :key="f.id" class="rounded border border-(--line) px-2 py-1">
                                {{ t('groupBuy.supplyLine', { option: f.optionName, lines: f.refundedLines, amount: UtilGroupBuy.won(f.refundAmount, locale), choice: f.choiceOrders, pending: f.choicePending, deadline: time.full(f.choiceDeadline) }) }}
                                <span class="text-muted-color"> · {{ f.reason }} · {{ f.operator }}</span>
                            </li>
                        </ul>
                    </div>
                </div>
            </AppGadget>

            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('groupBuy.readOnly') }}</Message>
            <Message v-else-if="!draft" severity="info" :closable="false" size="small">{{ t('groupBuy.lockedHelp') }}</Message>
            <Message v-if="draft && canManage && !canPrice" severity="info" :closable="false" size="small">{{ t('groupBuy.priceReadOnly') }}</Message>
            <Message v-for="w in warnings" :key="w" severity="warn" :closable="false" size="small">{{ t(`groupBuy.phraseWarning.${w}`) }}</Message>

            <form :key="formKey" class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <!-- ① 기본 정보 -->
                <AppGadget :title="t('groupBuy.secBasic')" anchor="gb-basic">
                    <div class="grid gap-4 md:grid-cols-2">
                        <LabeledField :label="t('groupBuy.fieldTitle')" :hint="lockHint('title')" :error="fe('title')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.title" maxlength="100" :readonly="ro('title')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldProductName')" :hint="lockHint('productName')" :error="fe('productName')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.productName" maxlength="100" :readonly="ro('productName')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldSummary')" :hint="t('groupBuy.plainHint', { max: UtilGroupBuy.TEXT_MAX })" :help="t('groupBuy.summaryHelp')" :error="fe('summary')" class="md:col-span-2">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" v-model="form.summary" rows="2" auto-resize :maxlength="UtilGroupBuy.TEXT_MAX" :readonly="ro('summary')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldSpec')" :hint="t('groupBuy.plainHint', { max: UtilGroupBuy.TEXT_MAX })" :help="t('groupBuy.specHelp')" :error="fe('spec')" class="md:col-span-2">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" v-model="form.spec" rows="4" auto-resize :maxlength="UtilGroupBuy.TEXT_MAX" :readonly="ro('spec')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                </AppGadget>

                <!-- ② 설명·이미지 -->
                <AppGadget :title="t('groupBuy.secContent')" anchor="gb-content">
                    <div class="flex flex-col gap-4">
                        <div class="flex flex-col gap-1.5">
                            <span :id="descLabelId" class="text-sm font-medium">{{ t('groupBuy.fieldDescription') }}</span>
                            <RichEditor :key="`gb-desc-${formKey}`" v-model="form.description" level="ADVANCED" image-upload :upload="richUpload" :disabled="ro('description')" :labelledby="descLabelId" :invalid="!!fe('description')" :placeholder="t('groupBuy.descriptionPlaceholder')" />
                            <p v-if="fe('description')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fe('description') }}</p>
                        </div>
                        <div class="flex flex-col gap-1.5">
                            <span class="text-sm font-medium">{{ t('groupBuy.fieldImages') }}</span>
                            <GroupBuyImagesEditor v-model="form.images" :label="t('groupBuy.fieldImages')" :upload="uploadImage" :disabled="ro('images')" />
                            <p v-if="fe('imageIds')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fe('imageIds') }}</p>
                        </div>
                    </div>
                </AppGadget>

                <!-- ③ 가격 -->
                <AppGadget :title="t('groupBuy.secPrice')" anchor="gb-price">
                    <div class="grid gap-4 md:grid-cols-2">
                        <LabeledField :label="t('groupBuy.fieldNormalPrice')" :hint="lockHint('normalPrice', true)" :error="fe('normalPrice')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputGroup>
                                    <InputText :id="id" v-model="form.normalPrice" inputmode="numeric" :readonly="ro('normalPrice', true)" :invalid="invalid" :aria-describedby="describedBy" class="text-right tabular-nums" />
                                    <InputGroupAddon>{{ t('groupBuy.wonUnit') }}</InputGroupAddon>
                                </InputGroup>
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldGroupPrice')" :hint="lockHint('groupPrice', true)" :error="fe('groupPrice')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputGroup>
                                    <InputText :id="id" v-model="form.groupPrice" inputmode="numeric" :readonly="ro('groupPrice', true)" :invalid="invalid" :aria-describedby="describedBy" class="text-right tabular-nums" />
                                    <InputGroupAddon>{{ t('groupBuy.wonUnit') }}</InputGroupAddon>
                                </InputGroup>
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldBasis')" :hint="lockHint('normalPriceBasis', true)" :error="fe('normalPriceBasis')">
                            <template #default="{ id, describedBy, invalid }">
                                <Select :input-id="id" v-model="form.normalPriceBasis" :options="basisOptions" option-label="label" option-value="value" :placeholder="t('groupBuy.basisPlaceholder')" :disabled="ro('normalPriceBasis', true)" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldBasisSource')" :hint="lockHint('normalPriceSource', true)" :help="t('groupBuy.basisSourceHelp')" :error="fe('normalPriceSource')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.normalPriceSource" maxlength="300" :readonly="ro('normalPriceSource', true)" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <Message severity="secondary" :closable="false" size="small" class="md:col-span-2">{{ t('groupBuy.basisNotice') }}</Message>
                    </div>
                </AppGadget>

                <!-- ④ 옵션 -->
                <AppGadget :title="t('groupBuy.secOptions')" anchor="gb-options">
                    <GroupBuyOptionsEditor v-model="form.options" :locked="!draft" :disabled="!canManage || saving" :errors="serverErrors" />
                </AppGadget>

                <!-- ⑤ 수량·방식 -->
                <AppGadget :title="t('groupBuy.secQty')" anchor="gb-qty">
                    <div class="grid gap-4 md:grid-cols-2">
                        <LabeledField :label="t('groupBuy.fieldMinQty')" :hint="lockHint('minQty')" :help="t('groupBuy.minQtyHelp')" :error="fe('minQty')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.minQty" inputmode="numeric" :readonly="ro('minQty')" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                            </template>
                        </LabeledField>
                        <div class="flex flex-col gap-1.5">
                            <span :id="fulfillId" class="text-sm font-medium">{{ t('groupBuy.fieldFulfillment') }} <small v-if="lockHint('fulfillment')" class="ml-1 text-xs font-normal text-muted-color">{{ lockHint('fulfillment') }}</small></span>
                            <div role="radiogroup" :aria-labelledby="fulfillId" class="flex flex-col gap-1.5">
                                <label v-for="f in fulfillments" :key="f" class="flex items-start gap-2 text-[13px]" :class="fulfillDisabled(f) ? 'opacity-60' : 'cursor-pointer'">
                                    <RadioButton v-model="form.fulfillment" :value="f" :name="fulfillId" :disabled="fulfillDisabled(f)" class="mt-0.5" />
                                    <span><span class="font-medium">{{ t(`groupBuy.fulfillment.${f}`) }}</span><span class="block text-xs text-muted-color">{{ t(`groupBuy.fulfillmentHelp.${f}`) }}</span></span>
                                </label>
                            </div>
                            <p v-if="fe('fulfillment') || fulfillProblem" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fe('fulfillment') || fulfillProblem }}</p>
                        </div>
                        <LabeledField :label="t('groupBuy.fieldMaxQty')" :hint="lockHint('maxQty')" :help="draft ? t('groupBuy.maxQtyHelp') : t('groupBuy.maxQtyLockedHelp')" :error="fe('maxQty')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.maxQty" inputmode="numeric" :readonly="ro('maxQty')" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldPerUser')" :hint="lockHint('maxQtyPerUser')" :help="t('groupBuy.perUserHelp')" :error="fe('maxQtyPerUser')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.maxQtyPerUser" inputmode="numeric" :placeholder="t('groupBuy.unlimited')" :readonly="ro('maxQtyPerUser')" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                            </template>
                        </LabeledField>
                        <div class="flex items-start gap-2 md:col-span-2">
                            <Checkbox v-model="form.roundLimitShared" binary :input-id="sharedId" :disabled="ro('roundLimitShared')" class="mt-0.5" />
                            <label :for="sharedId" class="text-[13px]">
                                <span class="font-medium">{{ t('groupBuy.fieldRoundShared') }}</span>
                                <small v-if="lockHint('roundLimitShared')" class="ml-1 text-xs text-muted-color">{{ lockHint('roundLimitShared') }}</small>
                                <span class="block text-xs text-muted-color">{{ t('groupBuy.roundSharedHelp') }}</span>
                            </label>
                        </div>
                    </div>
                </AppGadget>

                <!-- ⑥ 기간 -->
                <AppGadget :title="t('groupBuy.secPeriod')" anchor="gb-period">
                    <div class="grid gap-4 md:grid-cols-2">
                        <LabeledField :label="t('groupBuy.fieldStartAt')" :hint="lockHint('startAt')" :error="fe('startAt')">
                            <template #default="{ id, describedBy, invalid }">
                                <AppDateTimePicker v-model="form.startAt" :input-id="id" :readonly="ro('startAt')" :invalid="invalid" :described-by="describedBy" show-zone />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldEndAt')" :hint="lockHint('endAt')" :help="t('groupBuy.periodCapHelp', { days: periodCap })" :error="fe('endAt')">
                            <template #default="{ id, describedBy, invalid }">
                                <AppDateTimePicker v-model="form.endAt" :input-id="id" :min="form.startAt || undefined" :readonly="ro('endAt')" :invalid="invalid" :described-by="describedBy" show-zone />
                            </template>
                        </LabeledField>
                        <div class="flex flex-col gap-1.5 md:col-span-2">
                            <span :id="extId" class="text-sm font-medium">{{ t('groupBuy.fieldExtension') }} <small v-if="lockHint('extensionMode')" class="ml-1 text-xs font-normal text-muted-color">{{ lockHint('extensionMode') }}</small></span>
                            <div role="radiogroup" :aria-labelledby="extId" class="flex flex-col gap-1.5">
                                <label v-for="m in extensionModes" :key="m" class="flex items-start gap-2 text-[13px]" :class="ro('extensionMode') ? 'opacity-60' : 'cursor-pointer'">
                                    <RadioButton v-model="form.extensionMode" :value="m" :name="extId" :disabled="ro('extensionMode')" class="mt-0.5" />
                                    <span><span class="font-medium">{{ t(`groupBuy.extension.${m}`) }}</span><span class="block text-xs text-muted-color">{{ t(`groupBuy.extensionHelp.${m}`) }}</span></span>
                                </label>
                            </div>
                        </div>
                    </div>
                </AppGadget>

                <!-- ⑦ 배송 -->
                <AppGadget :title="t('groupBuy.secShipping')" anchor="gb-shipping">
                    <div class="grid gap-4 md:grid-cols-2">
                        <LabeledField :label="t('groupBuy.fieldPolicy')" :hint="lockHint('shippingPolicyCode', true)" :error="fe('shippingPolicyCode')">
                            <template #default="{ id, describedBy, invalid }">
                                <Select :input-id="id" v-model="form.shippingPolicyCode" :options="policyOptions" option-label="label" option-value="value" :disabled="ro('shippingPolicyCode', true)" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <div class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2 text-xs">
                            <template v-if="detail?.shippingSnapshot">
                                <span class="font-medium">{{ t('groupBuy.snapshotTitle') }}</span>
                                <span>{{ policyText(detail.shippingSnapshot.shippingFee, detail.shippingSnapshot.freeShippingOver, detail.shippingSnapshot.returnShippingFee, detail.shippingSnapshot.returnFeeMode) }}</span>
                            </template>
                            <template v-else-if="selectedPolicy">
                                <span v-if="selectedPolicy.current">{{ t('groupBuy.policyNow') }} {{ policyText(selectedPolicy.current.baseFee, selectedPolicy.current.freeShippingOver, selectedPolicy.current.returnFee, selectedPolicy.current.returnFeeMode) }}</span>
                                <span v-else class="text-red-600 dark:text-red-400">{{ t('groupBuy.policyNoCurrent') }}</span>
                                <span v-if="selectedPolicy.upcoming" class="text-amber-700 dark:text-amber-400">{{ t('groupBuy.policyUpcoming', { at: time.date(selectedPolicy.upcoming.effectiveFrom) }) }} {{ policyText(selectedPolicy.upcoming.baseFee, selectedPolicy.upcoming.freeShippingOver, selectedPolicy.upcoming.returnFee, selectedPolicy.upcoming.returnFeeMode) }}</span>
                                <span class="text-muted-color">{{ t('groupBuy.policySnapshotHelp') }}</span>
                            </template>
                        </div>
                        <LabeledField v-if="form.fulfillment === 'AFTER_CLOSE'" :label="t('groupBuy.fieldShipPlanned')" :hint="lockHint('shipPlannedAt')" :help="t('groupBuy.shipPlannedHelp')" :error="fe('shipPlannedAt')">
                            <template #default="{ id, describedBy, invalid }">
                                <AppDatePicker v-model="form.shipPlannedAt" :input-id="id" :readonly="ro('shipPlannedAt')" :invalid="invalid" :described-by="describedBy" />
                            </template>
                        </LabeledField>
                        <LabeledField v-else :label="t('groupBuy.fieldShipWithin')" :hint="lockHint('shipWithinBusinessDays')" :help="t('groupBuy.shipWithinHelp')" :error="fe('shipWithinBusinessDays')">
                            <template #default="{ id, describedBy, invalid }">
                                <InputGroup class="w-48">
                                    <InputGroupAddon>{{ t('groupBuy.afterPayment') }}</InputGroupAddon>
                                    <InputText :id="id" v-model="form.shipWithinBusinessDays" inputmode="numeric" :readonly="ro('shipWithinBusinessDays')" :invalid="invalid" :aria-describedby="describedBy" class="text-right tabular-nums" />
                                    <InputGroupAddon>{{ t('groupBuy.businessDays') }}</InputGroupAddon>
                                </InputGroup>
                            </template>
                        </LabeledField>
                    </div>
                </AppGadget>

                <!-- ⑧ 청약철회 제한 안내 -->
                <AppGadget :title="t('groupBuy.secWithdrawal')" anchor="gb-withdrawal">
                    <LabeledField :label="t('groupBuy.fieldWithdrawal')" :hint="lockHint('withdrawalRestriction')" :help="t('groupBuy.withdrawalHelp')" :error="fe('withdrawalRestriction')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.withdrawalRestriction" rows="3" auto-resize maxlength="1000" :readonly="ro('withdrawalRestriction')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                </AppGadget>

                <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :disabled="!!fulfillProblem" :error-count="Object.keys(serverErrors).length" :error="formError" @revert="revert" />
            </form>
        </template>

        <GroupBuyRevisionDrawer v-model:visible="revOpen" :items="revItems" :total="revTotal" :loading="revLoading" :error="revError" @more="loadRevisions(false)" @retry="loadRevisions(true)" />

        <!-- 조기 마감 -->
        <PaymentReasonDialog v-model:visible="closeEarlyOpen" :title="t('groupBuy.closeEarlyTitle')" :description="t('groupBuy.closeEarlyBody')" :confirm-label="t('groupBuy.actCloseEarly')" :busy="acting === 'closeEarly'" :error="dialogError" :field-error="dialogFieldError" @submit="doCloseEarly" />

        <!-- 중단 — 서버 미리 보기 숫자(결정 29) -->
        <PaymentReasonDialog v-model:visible="cancelOpen" :title="t('groupBuy.cancelTitle')" :description="t('groupBuy.cancelBody')" :confirm-label="t('groupBuy.actCancel')" tone="danger" :busy="acting === 'cancel'" :error="dialogError" :field-error="dialogFieldError" @submit="doCancel">
            <div v-if="cancelPreview" class="grid grid-cols-1 gap-2 sm:grid-cols-3">
                <div class="rounded-lg border border-red-300 px-3 py-2 dark:border-red-800">
                    <div class="text-[11px] text-muted-color">{{ t('groupBuy.cancelRefund') }}</div>
                    <div class="text-[15px] font-semibold tabular-nums">{{ t('groupBuy.cancelRefundLine', { n: cancelPreview.refundOrders, amount: UtilGroupBuy.won(cancelPreview.refundAmount, locale) }) }}</div>
                </div>
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <div class="text-[11px] text-muted-color">{{ t('groupBuy.cancelExpire') }}</div>
                    <div class="text-[15px] font-semibold tabular-nums">{{ t('groupBuy.peopleCount', { n: cancelPreview.expireOrders }) }}</div>
                </div>
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <div class="text-[11px] text-muted-color">{{ t('groupBuy.cancelShipped') }}</div>
                    <div class="text-[15px] font-semibold tabular-nums">{{ t('groupBuy.peopleCount', { n: cancelPreview.shippedOrders }) }}</div>
                </div>
            </div>
            <Skeleton v-else-if="!previewError" height="4rem" />
            <Message v-else severity="error" variant="simple" size="small" :closable="false">{{ previewError }}</Message>
        </PaymentReasonDialog>

        <!-- 발송 지연 공지(결정 52) -->
        <PaymentReasonDialog v-model:visible="delayOpen" :title="t('groupBuy.delayTitle')" :description="t('groupBuy.delayBody', { n: detail?.preShipOrderCount ?? 0 })" :confirm-label="t('groupBuy.delaySubmit')" :busy="acting === 'delay'" :error="dialogError" :field-error="dialogFieldError" @submit="doDelay">
            <LabeledField :label="t('groupBuy.delayNewDate')" :help="detail?.shipPlannedAt ? t('groupBuy.delayCurrent', { date: detail.shipPlannedAt }) : ''" :error="delayDateError">
                <template #default="{ id, describedBy, invalid }">
                    <AppDatePicker v-model="delayDate" :input-id="id" :min="detail?.shipPlannedAt ?? undefined" :invalid="invalid" :described-by="describedBy" />
                </template>
            </LabeledField>
        </PaymentReasonDialog>

        <!-- 옵션 공급 불가(결정 53) -->
        <PaymentReasonDialog v-model:visible="supplyOpen" :title="t('groupBuy.supplyTitle')" :description="t('groupBuy.supplyBody')" :confirm-label="t('groupBuy.supplySubmit')" tone="danger" :busy="acting === 'supply'" :error="dialogError" :field-error="dialogFieldError" @submit="doSupply">
            <LabeledField :label="t('groupBuy.supplyOption')" :error="supplyOptionError">
                <template #default="{ id, describedBy, invalid }">
                    <Select :input-id="id" v-model="supplyOptionId" :options="detail?.options ?? []" option-label="name" option-value="id" :placeholder="t('groupBuy.supplyOptionPlaceholder')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>
            <div v-if="supplyPreview" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                <span>{{ t('groupBuy.supplyPreviewRefund', { lines: supplyPreview.cancelLines, amount: UtilGroupBuy.won(supplyPreview.refundAmount, locale) }) }}</span>
                <span>{{ t('groupBuy.supplyPreviewFull', { n: supplyPreview.fullRefundOrders }) }}</span>
                <span>{{ t('groupBuy.supplyPreviewChoice', { n: supplyPreview.choiceOrders, deadline: time.full(supplyPreview.choiceDeadline) }) }}</span>
            </div>
            <Skeleton v-else-if="supplyOptionId && supplyLoading" height="3.5rem" />
        </PaymentReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { GroupBuyOptionRow } from '~/components/groupbuy/GroupBuyOptionsEditor.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    GroupBuyCancelPreview,
    GroupBuyDetail,
    GroupBuyExtensionMode,
    GroupBuyFormOptions,
    GroupBuyFulfillment,
    GroupBuyImage,
    GroupBuyRevision,
    GroupBuySaveRequest,
    GroupBuySupplyFailurePreview,
    NormalPriceBasis,
    ReturnFeeMode,
} from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import { NORMAL_PRICE_BASES } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface EditForm {
    title: string;
    productName: string;
    summary: string;
    spec: string;
    description: string;
    images: GroupBuyImage[];
    normalPrice: string;
    groupPrice: string;
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    options: GroupBuyOptionRow[];
    minQty: string;
    fulfillment: GroupBuyFulfillment;
    maxQty: string;
    maxQtyPerUser: string;
    roundLimitShared: boolean;
    startAt: string;
    endAt: string;
    extensionMode: GroupBuyExtensionMode;
    shippingPolicyCode: string;
    shipPlannedAt: string;
    shipWithinBusinessDays: string;
    withdrawalRestriction: string;
}

const { t, locale } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useGroupBuyAdminApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

const descLabelId = useId();
const fulfillId = useId();
const sharedId = useId();
const extId = useId();

const idParam = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => idParam.value === 'new');
const canManage = computed(() => can('commerce.product.manage'));
const canPrice = computed(() => can('commerce.product.price'));
const canSupply = computed(() => can('commerce.order.manage') && can('commerce.refund.approve'));
const nowIso = new Date().toISOString();

const detail = ref<GroupBuyDetail | null>(null);
const options = ref<GroupBuyFormOptions | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const optionsError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
const formKey = ref(0);

const pageTitle = computed(() => (isNew.value ? t('groupBuy.newTitle') : (detail.value?.title ?? t('groupBuy.editTitle'))));
useSeoMeta({ title: () => pageTitle.value });

/** DRAFT(새 공구 포함)면 모든 칸을 고친다 */
const draft = computed(() => isNew.value || detail.value?.status === 'DRAFT');

function emptyForm(): EditForm {
    return {
        title: '',
        productName: '',
        summary: '',
        spec: '',
        description: '',
        images: [],
        normalPrice: '',
        groupPrice: '',
        normalPriceBasis: null,
        normalPriceSource: '',
        options: [],
        minQty: '0',
        fulfillment: 'IMMEDIATE',
        maxQty: '',
        maxQtyPerUser: '',
        roundLimitShared: false,
        startAt: '',
        endAt: '',
        extensionMode: 'DEMAND',
        shippingPolicyCode: options.value?.shippingPolicies[0]?.code ?? '',
        shipPlannedAt: '',
        shipWithinBusinessDays: String(options.value?.defaultShipWithinBusinessDays ?? 3),
        withdrawalRestriction: '',
    };
}

function formOf(d: GroupBuyDetail): EditForm {
    return {
        title: d.title,
        productName: d.productName,
        summary: d.summary,
        spec: d.spec,
        description: d.description,
        images: d.images.map((i) => ({ ...i })),
        normalPrice: String(d.normalPrice),
        groupPrice: String(d.groupPrice),
        normalPriceBasis: d.normalPriceBasis,
        normalPriceSource: d.normalPriceSource,
        options: d.options.map((o) => ({ key: o.id, id: o.id, name: o.name, extraPrice: String(o.extraPrice), maxQty: o.maxQty === null ? '' : String(o.maxQty), currentQty: o.currentQty, reservedQty: o.reservedQty })),
        minQty: String(d.minQty),
        fulfillment: d.fulfillment,
        maxQty: String(d.maxQty),
        maxQtyPerUser: d.maxQtyPerUser === null ? '' : String(d.maxQtyPerUser),
        roundLimitShared: d.roundLimitShared,
        startAt: d.startAt ?? '',
        endAt: d.endAt ?? '',
        extensionMode: d.extensionMode,
        shippingPolicyCode: d.shippingPolicyCode,
        shipPlannedAt: d.shipPlannedAt ?? '',
        shipWithinBusinessDays: d.shipWithinBusinessDays === null ? '' : String(d.shipWithinBusinessDays),
        withdrawalRestriction: d.withdrawalRestriction,
    };
}

const form = ref<EditForm>(emptyForm());
const initial = ref('');
/** 비교용 — 줄 키·표시용 수량은 뺀다 */
function snapshot(f: EditForm): string {
    return JSON.stringify({ ...f, images: f.images.map((i) => i.mediaId), options: f.options.map((o) => [o.id, o.name, o.extraPrice, o.maxQty]) });
}
const dirty = computed(() => initial.value !== '' && snapshot(form.value) !== initial.value);

function applyDetail(d: GroupBuyDetail): void {
    detail.value = d;
    form.value = formOf(d);
    initial.value = snapshot(form.value);
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

/** 칸이 읽기 전용인가 — 권한 없음 · 저장 중 · 모집 뒤 잠긴 칸 · 가격 칸인데 가격 권한 없음 */
function ro(field: string, price = false): boolean {
    if (!canManage.value || saving.value) return true;
    if (!draft.value && !UtilGroupBuy.CONTENT_FIELDS.includes(field)) return true;
    return price && !canPrice.value;
}

function lockHint(field: string, price = false): string {
    if (!draft.value && !UtilGroupBuy.CONTENT_FIELDS.includes(field)) return t('groupBuy.locked');
    if (draft.value && price && canManage.value && !canPrice.value) return t('groupBuy.locked');
    return '';
}

function fe(field: string): string {
    return serverErrors.value[field] ?? '';
}

const fulfillments: GroupBuyFulfillment[] = ['IMMEDIATE', 'AFTER_CLOSE'];
const extensionModes: GroupBuyExtensionMode[] = ['DEMAND', 'ALWAYS'];

/** 즉시 발송은 선착순(min 0)만 — CHECK `fulfillment = 'AFTER_CLOSE' OR min_qty = 0` */
function fulfillDisabled(f: GroupBuyFulfillment): boolean {
    return ro('fulfillment') || (f === 'IMMEDIATE' && (UtilGroupBuy.int(form.value.minQty) ?? 0) > 0);
}
const fulfillProblem = computed(() => (draft.value && form.value.fulfillment === 'IMMEDIATE' && (UtilGroupBuy.int(form.value.minQty) ?? 0) > 0 ? t('groupBuy.fulfillmentProblem') : ''));

const periodCap = computed(() => ((UtilGroupBuy.int(form.value.minQty) ?? 0) > 0 ? (options.value?.periodMaxDays.threshold ?? 14) : (options.value?.periodMaxDays.firstCome ?? 30)));

const basisOptions = computed(() => NORMAL_PRICE_BASES.map((b) => ({ value: b, label: t(`groupBuy.basis.${b}`) })));
const policyOptions = computed(() => (options.value?.shippingPolicies ?? []).map((p) => ({ value: p.code, label: `${p.name} (${p.code})` })));
const selectedPolicy = computed(() => options.value?.shippingPolicies.find((p) => p.code === form.value.shippingPolicyCode) ?? null);

function policyText(fee: number, free: number | null, returnFee: number, mode: ReturnFeeMode): string {
    const won = (n: number) => UtilGroupBuy.won(n, locale.value);
    return t('groupBuy.policyText', { fee: won(fee), free: free === null ? t('groupBuy.policyNoFree') : t('groupBuy.policyFree', { amount: won(free) }), ret: won(returnFee), mode: t(`groupBuy.returnMode.${mode}`) });
}

const warnings = computed(() => UtilGroupBuy.phraseWarnings(form.value.title, form.value.summary, form.value.spec, form.value.description, form.value.withdrawalRestriction, ...form.value.options.map((o) => o.name)));

// ── 읽기 ──
async function loadOptions(): Promise<void> {
    optionsError.value = null;
    try {
        options.value = await api.formOptions();
        if (isNew.value && !form.value.shippingPolicyCode) {
            form.value = emptyForm();
            initial.value = snapshot(form.value);
        }
    } catch (e) {
        optionsError.value = toApiError(e);
    }
}

async function load(): Promise<void> {
    if (isNew.value) {
        detail.value = null;
        form.value = emptyForm();
        initial.value = snapshot(form.value);
        formKey.value += 1;
        return;
    }
    loading.value = true;
    loadError.value = null;
    try {
        applyDetail(await api.detail(idParam.value));
    } catch (e) {
        detail.value = null;
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

onMounted(async () => {
    await loadOptions();
    await load();
});
watch(idParam, () => void load());

// ── 이미지 올리기 ──
async function uploadImage(file: File): Promise<GroupBuyImage> {
    const r = await api.uploadImage(file);
    return { mediaId: r.mediaId, url: r.url, thumbUrl: r.thumbUrl };
}
const richUpload: RichImageUploader = async (file, opts) => {
    const r = await api.uploadImage(file);
    opts.onProgress(1);
    return { id: r.mediaId, url: r.url, width: r.width, height: r.height };
};

// ── 저장 ──
function toRequest(f: EditForm): GroupBuySaveRequest {
    const num = (s: string) => UtilGroupBuy.int(s) ?? 0;
    const opt = (s: string) => (s.trim() === '' ? null : UtilGroupBuy.int(s));
    return {
        title: f.title.trim(),
        productName: f.productName.trim(),
        summary: f.summary,
        spec: f.spec,
        description: f.description,
        imageIds: f.images.map((i) => i.mediaId),
        normalPrice: num(f.normalPrice),
        groupPrice: num(f.groupPrice),
        normalPriceBasis: f.normalPriceBasis,
        normalPriceSource: f.normalPriceSource.trim(),
        options: f.options.map((o) => ({ id: o.id, name: o.name.trim(), extraPrice: num(o.extraPrice), maxQty: opt(o.maxQty) })),
        minQty: num(f.minQty),
        fulfillment: f.fulfillment,
        maxQty: num(f.maxQty),
        maxQtyPerUser: opt(f.maxQtyPerUser),
        roundLimitShared: f.roundLimitShared,
        startAt: f.startAt || null,
        endAt: f.endAt || null,
        extensionMode: f.extensionMode,
        shippingPolicyCode: f.shippingPolicyCode,
        shipPlannedAt: f.fulfillment === 'AFTER_CLOSE' ? f.shipPlannedAt || null : null,
        shipWithinBusinessDays: f.fulfillment === 'IMMEDIATE' ? opt(f.shipWithinBusinessDays) : null,
        withdrawalRestriction: f.withdrawalRestriction.trim(),
        version: detail.value?.version ?? null,
    };
}

async function save(): Promise<void> {
    if (saving.value || !dirty.value || fulfillProblem.value) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        if (isNew.value) {
            const created = await api.create(toRequest(form.value));
            initial.value = snapshot(form.value); // 떠나기 확인을 건너뛴다
            toast.success(t('groupBuy.created'));
            await router.replace(`/group-buys/${encodeURIComponent(created.id)}`);
            return;
        }
        const d = detail.value!;
        const saved = draft.value ? await api.update(d.id, toRequest(form.value)) : await api.updateContent(d.id, { summary: form.value.summary, spec: form.value.spec, description: form.value.description, imageIds: form.value.images.map((i) => i.mediaId), version: d.version });
        applyDetail(saved);
        toast.success(t('groupBuy.saved'));
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields && Object.keys(failure.fields).length) {
            serverErrors.value = failure.fields;
            formError.value = t('groupBuy.saveFieldsRejected');
        } else {
            formError.value = failure.message;
        }
    } finally {
        saving.value = false;
    }
}

function revert(): void {
    if (detail.value) applyDetail(detail.value);
    else {
        form.value = emptyForm();
        initial.value = snapshot(form.value);
        formKey.value += 1;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('groupBuy.leaveTitle'), message: t('groupBuy.leaveMessage'), confirmLabel: t('groupBuy.leaveConfirm'), cancelLabel: t('groupBuy.leaveCancel'), tone: 'danger' });
});

// ── 운영 동작 ──
type Act = 'start' | 'closeEarly' | 'cancel' | 'ordered' | 'round' | 'delay' | 'supply';
const acting = ref<Act | null>(null);
const actionError = ref('');
const dialogError = ref('');
const dialogFieldError = ref('');
const reasonOpen = ref<'closeEarly' | null>(null);
const closeEarlyOpen = computed({ get: () => reasonOpen.value === 'closeEarly', set: (v: boolean) => (reasonOpen.value = v ? 'closeEarly' : null) });
function openCloseEarly(): void {
    dialogError.value = '';
    dialogFieldError.value = '';
    reasonOpen.value = 'closeEarly';
}

/** 동작 하나를 돌린다 — 성공하면 상세를 바꾸고 토스트, 실패는 대화상자 안(있으면) 또는 상태 칸 아래 */
async function run(act: Act, work: () => Promise<GroupBuyDetail | null>, done: string, inDialog: boolean): Promise<boolean> {
    acting.value = act;
    actionError.value = '';
    dialogError.value = '';
    dialogFieldError.value = '';
    try {
        const d = await work();
        if (d) applyDetail(d);
        toast.success(done);
        return true;
    } catch (e) {
        const failure = toApiError(e);
        const reasonField = failure.field('reason');
        if (inDialog) {
            dialogFieldError.value = reasonField ?? '';
            dialogError.value = reasonField ? '' : failure.message;
        } else {
            actionError.value = failure.message;
        }
        return false;
    } finally {
        acting.value = null;
    }
}

async function doStart(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    const ok = await confirm({ title: t('groupBuy.startTitle'), message: t('groupBuy.startBody'), confirmLabel: t('groupBuy.actStart') });
    if (ok) await run('start', () => api.start(d.id), t('groupBuy.started'), false);
}

async function doCloseEarly(reason: string): Promise<void> {
    const d = detail.value;
    if (d && (await run('closeEarly', () => api.closeEarly(d.id, reason), t('groupBuy.closedEarly'), true))) reasonOpen.value = null;
}

async function doMarkOrdered(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    const ok = await confirm({ title: t('groupBuy.orderedTitle'), message: t('groupBuy.orderedBody'), confirmLabel: t('groupBuy.actMarkOrdered') });
    if (ok) await run('ordered', () => api.markOrdered(d.id), t('groupBuy.markedOrdered'), false);
}

async function doRound(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    const ok = await confirm({ title: t('groupBuy.roundTitle'), message: t('groupBuy.roundBody'), confirmLabel: t('groupBuy.actRound') });
    if (!ok) return;
    let createdId = '';
    const done = await run(
        'round',
        async () => {
            const created = await api.createRound(d.id);
            createdId = created.id;
            return null;
        },
        t('groupBuy.roundCreated'),
        false,
    );
    if (done && createdId) await router.push(`/group-buys/${encodeURIComponent(createdId)}`);
}

// 중단
const cancelOpen = ref(false);
const cancelPreview = ref<GroupBuyCancelPreview | null>(null);
const previewError = ref('');
async function openCancel(): Promise<void> {
    const d = detail.value;
    if (!d) return;
    cancelPreview.value = null;
    previewError.value = '';
    dialogError.value = '';
    dialogFieldError.value = '';
    cancelOpen.value = true;
    try {
        cancelPreview.value = await api.cancelPreview(d.id);
    } catch (e) {
        previewError.value = toApiError(e).message;
    }
}
async function doCancel(reason: string): Promise<void> {
    const d = detail.value;
    if (d && (await run('cancel', () => api.cancel(d.id, reason), t('groupBuy.canceled'), true))) cancelOpen.value = false;
}

// 발송 지연 공지
const delayOpen = ref(false);
const delayDate = ref('');
const delayDateError = ref('');
function openDelay(): void {
    delayDate.value = '';
    delayDateError.value = '';
    dialogError.value = '';
    dialogFieldError.value = '';
    delayOpen.value = true;
}
async function doDelay(reason: string): Promise<void> {
    const d = detail.value;
    if (!d) return;
    delayDateError.value = delayDate.value ? '' : t('groupBuy.delayDateRequired');
    if (delayDateError.value) return;
    acting.value = 'delay';
    dialogError.value = '';
    dialogFieldError.value = '';
    try {
        applyDetail(await api.delayNotice(d.id, { newPlannedAt: delayDate.value, reason }));
        toast.success(t('groupBuy.delayDone'));
        delayOpen.value = false;
    } catch (e) {
        const failure = toApiError(e);
        delayDateError.value = failure.field('newPlannedAt') ?? '';
        dialogFieldError.value = failure.field('reason') ?? '';
        dialogError.value = delayDateError.value || dialogFieldError.value ? '' : failure.message;
    } finally {
        acting.value = null;
    }
}

// 옵션 공급 불가
const supplyOpen = ref(false);
const supplyOptionId = ref<string | null>(null);
const supplyOptionError = ref('');
const supplyPreview = ref<GroupBuySupplyFailurePreview | null>(null);
const supplyLoading = ref(false);
function openSupply(): void {
    supplyOptionId.value = null;
    supplyPreview.value = null;
    supplyOptionError.value = '';
    dialogError.value = '';
    dialogFieldError.value = '';
    supplyOpen.value = true;
}
watch(supplyOptionId, async (optionId) => {
    supplyPreview.value = null;
    supplyOptionError.value = '';
    const d = detail.value;
    if (!optionId || !d) return;
    supplyLoading.value = true;
    try {
        const preview = await api.supplyFailurePreview(d.id, optionId);
        if (supplyOptionId.value === optionId) supplyPreview.value = preview;
    } catch (e) {
        supplyOptionError.value = toApiError(e).message;
    } finally {
        supplyLoading.value = false;
    }
});
async function doSupply(reason: string): Promise<void> {
    const d = detail.value;
    if (!d) return;
    if (!supplyOptionId.value) {
        supplyOptionError.value = t('groupBuy.supplyOptionRequired');
        return;
    }
    const optionId = supplyOptionId.value;
    if (await run('supply', () => api.supplyFailure(d.id, { optionId, reason }), t('groupBuy.supplyDone'), true)) supplyOpen.value = false;
}

// ── 수정 이력(서랍을 처음 열 때 받는다) ──
const revOpen = ref(false);
const revItems = ref<GroupBuyRevision[]>([]);
const revTotal = ref(0);
const revLoading = ref(false);
const revError = ref<ApiError | null>(null);
const revLoaded = ref(false);
function openRevisions(): void {
    revOpen.value = true;
    if (!revLoaded.value) void loadRevisions(true);
}
async function loadRevisions(reset: boolean): Promise<void> {
    const d = detail.value;
    if (!d) return;
    revLoading.value = true;
    revError.value = null;
    try {
        const page = reset ? 0 : Math.ceil(revItems.value.length / 20);
        const found = await api.revisions(d.id, page, 20);
        revItems.value = reset ? found.items : [...revItems.value, ...found.items];
        revTotal.value = found.total;
        revLoaded.value = true;
    } catch (e) {
        revError.value = toApiError(e);
    } finally {
        revLoading.value = false;
    }
}
// 저장·동작으로 이력이 늘었으면 이미 받은 적이 있을 때만 다시 받는다
watch(
    () => detail.value?.revisionNo,
    (now, before) => {
        if (revLoaded.value && before !== undefined && now !== before) void loadRevisions(true);
    },
);
</script>
