<template>
    <!--
        공구 › 공구 등록·수정(`/group-buys/new` · `/group-buys/{id}`) — 계약 commerce-groupbuy 「화면 목록 (2b-1)」·결정 2·17·29·31·51~53·55·58.
        · DRAFT(새 공구 포함)는 모든 칸, 모집 시작 뒤에는 **설명·이미지(요약·사양·설명·대표 이미지)만** 고친다 — 나머지 칸은 읽기 전용 + 「잠김」 표시(서버 `409 GROUP_BUY_LOCKED_FIELD`).
          가격·배송비 칸은 `commerce.product.price` 가 없으면 읽기 전용이다. 저장은 화면 하나로(DRAFT `PUT` 한 번 · 모집 뒤 `PATCH …/content` 한 번) — 떠 있는 저장 줄.
        · 칸: 제목·상품명·요약·사양·설명(`@roboyam/rich-editor` 고급 수준 + 본문 이미지)·대표 이미지(8장·순서)·정상가·공구가·정상가 기준(+ 출처 메모, 「최저가」 금지 안내)·성사 수량(0 = 선착순)·발송 시점
          (즉시 발송은 선착순만)·최대 수량·1인 한도·차수 사이 한도 합산·기간·연장 방식·배송비 정책·발송 예정일(마감 후 발주) 또는 결제 후 N영업일(즉시 발송)·청약철회 제한 안내.
        · 옵션(결정 88, `GroupBuyAxesEditor`): 축 0~3개 → 조합마다 MAIN 단품(추가금·한도·판매 여부). 추가 구성상품(`GroupBuyAddonsEditor`): ADDON 단품(이름·가격·한도·이미지 한 장·판매 여부·차례).
          저장 때 `axes` + `skus`(MAIN 조합 차례 다음 ADDON)로 보낸다. 모집 뒤에는 둘 다 잠긴다(한도는 수량 화면).
        · 고시 양식(결정 87·88, `GroupBuyNoticeEditor`): 기본 양식(적용 단품 빔 = MAIN 단품들) + 추가 구성상품마다 양식(필수), 품목별 항목·「제공 불가」 + 사유·판매자 추가 항목. **모집 뒤에도 고친다**(`PATCH …/content` 에 같이 — 이력 `NOTICE`).
          적용 단품은 화면에서 단품 줄 키로 들고, 저장 때 단품 이름(`skuNames`)으로 바꾼다. 양식 없음·빈 항목·어느 양식에도 안 든 단품이 있으면 모집 시작이 `409 GROUP_BUY_NOTICE_INCOMPLETE` — 화면이 그 항목으로 옮겨 표시한다. 카탈로그는 `GET …/notice-catalog`(서버 정본).
        · 문구 경고(결정 55·58): 「개봉하면 반품 불가」·「재판매 금지·리셀」·「최저가」·「한정」이 보이면 위에 경고(막지는 않는다).
        · 운영 동작(서버가 정한 `actions`): 모집 시작 · 조기 마감(사유) · 중단(확인 창 「환불 N명 · 총 N원 / 이미 발송 M명은 그대로」 + 사유) · 발주 처리 · 추가 차수로 열기 ·
          발송 지연 공지(새 예정일 + 사유) · 단품 공급 불가 처리(단품 + 사유, 결과 미리 보기) · 수정 이력(오른쪽 서랍) · 수량 화면·주문 링크.
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
                    <p v-else-if="detail.actions.canStart && canManage && noticeSummary && !noticeSummary.complete" class="m-0 text-xs text-amber-700 dark:text-amber-400">
                        {{ noticeSummary.noNotice ? t('groupBuy.noticeStartBlockedNone') : t('groupBuy.noticeStartBlocked', { blank: noticeSummary.blank, unassigned: noticeSummary.unassigned + noticeSummary.addonMissing }) }}
                        <a href="#gb-notice" class="ml-1 text-primary hover:underline" @click.prevent="goNotice">{{ t('groupBuy.noticeGo') }}</a>
                    </p>
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
                                {{ t('groupBuy.supplyLine', { sku: skuText(f.skuName, f.skuKind), lines: f.refundedLines, amount: UtilGroupBuy.won(f.refundAmount, locale), choice: f.choiceOrders, pending: f.choicePending, deadline: time.full(f.choiceDeadline) }) }}
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
                <!-- ⓪ 상품(결정 89) — 설명·이미지·고시·옵션 축은 상품에서 온다. 상품은 DRAFT 에서만 고른다 -->
                <AppGadget :title="t('groupBuy.secProduct')" anchor="gb-product" :invalid="fe('productId') ? 1 : 0">
                    <div class="flex flex-col gap-3">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.productPickHelp') }}</p>
                        <LabeledField :label="t('groupBuy.fieldProduct')" :hint="draft ? '' : t('groupBuy.locked')" :error="fe('productId') || productError">
                            <template #default="{ id, describedBy, invalid }">
                                <Select
                                    :input-id="id"
                                    :model-value="form.productId"
                                    :options="productChoices"
                                    option-label="label"
                                    option-value="value"
                                    filter
                                    :loading="productLoading"
                                    :placeholder="t('groupBuy.productPickPlaceholder')"
                                    :disabled="!draft || !canManage || saving"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    fluid
                                    @update:model-value="(v: string) => void pickProduct(v)"
                                />
                            </template>
                        </LabeledField>
                        <div v-if="form.productId" class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                            <span class="text-muted-color">{{ t('groupBuy.productCategory') }} <b class="font-medium text-color">{{ productPath || '—' }}</b></span>
                            <NuxtLink :to="`/group-buy-products/${encodeURIComponent(form.productId)}`" class="text-primary hover:underline">{{ t('groupBuy.productEdit') }}</NuxtLink>
                        </div>
                        <Message v-if="!form.productId" severity="warn" :closable="false" size="small">{{ t('groupBuy.productNone') }}</Message>
                    </div>
                </AppGadget>

                <!-- ① 기본 정보 -->
                <AppGadget :title="t('groupBuy.secBasic')" anchor="gb-basic">
                    <div class="flex flex-col gap-4">
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
                        <LabeledField :label="t('groupBuy.fieldSummary')" :hint="t('groupBuy.plainHint', { max: UtilGroupBuy.TEXT_MAX })" :help="t('groupBuy.summaryHelp')" :error="fe('summary')">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" v-model="form.summary" rows="2" auto-resize :maxlength="UtilGroupBuy.TEXT_MAX" :readonly="ro('summary')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('groupBuy.fieldSpec')" :hint="t('groupBuy.plainHint', { max: UtilGroupBuy.TEXT_MAX })" :help="t('groupBuy.specHelp')" :error="fe('spec')">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea :id="id" v-model="form.spec" rows="4" auto-resize :maxlength="UtilGroupBuy.TEXT_MAX" :readonly="ro('spec')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                </AppGadget>

                <!-- ② 설명·이미지 — 상품에서 온 사본(여기서 고치지 않는다) -->
                <AppGadget :title="t('groupBuy.secContent')" anchor="gb-content">
                    <div class="flex flex-col gap-4">
                        <Message severity="secondary" :closable="false" size="small">{{ t('groupBuy.fromProductHelp') }}</Message>
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
                    <div class="flex flex-col gap-4">
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
                        <Message severity="secondary" :closable="false" size="small">{{ t('groupBuy.basisNotice') }}</Message>
                    </div>
                </AppGadget>

                <!-- ④ 옵션(축·조합 = MAIN 단품) — 결정 88 -->
                <AppGadget :title="t('groupBuy.secOptions')" anchor="gb-options" :invalid="fe('axes') || fe('skus') || fe('options') ? 1 : 0">
                    <template v-if="!draft" #title-extra><Tag :value="t('groupBuy.locked')" severity="secondary" /></template>
                    <GroupBuyAxesEditor v-model:axes="form.axes" v-model:mains="form.mains" :group-price="form.groupPrice" :locked="!draft" :disabled="!canManage || saving" :errors="serverErrors" axes-locked :show-supply="canSeeSupply" :supply-editable="canPrice" />
                </AppGadget>

                <!-- ④-2 추가 구성상품(ADDON 단품) — 결정 88 -->
                <AppGadget :title="t('groupBuy.secAddons')" anchor="gb-addons" :invalid="fe('addons') ? 1 : 0">
                    <template v-if="!draft" #title-extra><Tag :value="t('groupBuy.locked')" severity="secondary" /></template>
                    <GroupBuyAddonsEditor v-model="form.addons" :sources="addonSources" :locked="!draft" :disabled="!canManage || saving" :errors="serverErrors" :show-supply="canSeeSupply" :supply-editable="canPrice" />
                </AppGadget>

                <!-- ⑤ 수량·방식 -->
                <AppGadget :title="t('groupBuy.secQty')" anchor="gb-qty">
                    <div class="flex flex-col gap-4">
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
                        <div class="flex items-start gap-2">
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
                    <div class="flex flex-col gap-4">
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

                <!-- ⑧ 상품정보제공고시 — 모집 뒤에도 고친다(결정 87) -->
                <AppGadget :title="t('groupBuy.secNotice')" anchor="gb-notice" :invalid="noticeErrorCount">
                    <template #title-extra>
                        <template v-if="noticeSummary">
                            <Tag v-if="noticeSummary.noNotice" :value="t('groupBuy.noticeHeaderNone')" severity="danger" />
                            <Tag v-if="noticeSummary.blank" :value="t('groupBuy.noticeHeaderBlank', { n: noticeSummary.blank })" severity="warn" />
                            <Tag v-if="noticeSummary.addonMissing" :value="t('groupBuy.noticeHeaderAddon', { n: noticeSummary.addonMissing })" severity="danger" />
                            <Tag v-if="noticeSummary.unassigned" :value="t('groupBuy.noticeHeaderUnassigned', { n: noticeSummary.unassigned })" severity="danger" />
                            <Tag v-if="noticeSummary.complete" :value="t('groupBuy.noticeHeaderOk', { n: form.notices.length })" severity="success" />
                        </template>
                    </template>
                    <Message severity="secondary" :closable="false" size="small" class="mb-3">{{ t('groupBuy.noticeFromProduct') }}</Message>
                    <GroupBuyNoticeEditor v-if="catalog" ref="noticeEditor" v-model="form.notices" :catalog="catalog" :skus="skuRefs" disabled :errors="serverErrors" :incomplete="noticeIncomplete" />
                    <ErrorState v-else-if="catalogError" :title="t('groupBuy.noticeCatalogFailed')" :description="catalogError.message" retry :retrying="catalogLoading" @retry="loadCatalog" />
                    <Skeleton v-else height="8rem" />
                </AppGadget>

                <!-- ⑨ 청약철회 제한 안내 -->
                <AppGadget :title="t('groupBuy.secWithdrawal')" anchor="gb-withdrawal">
                    <LabeledField :label="t('groupBuy.fieldWithdrawal')" :hint="lockHint('withdrawalRestriction')" :help="t('groupBuy.withdrawalHelp')" :error="fe('withdrawalRestriction')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.withdrawalRestriction" rows="3" auto-resize maxlength="1000" :readonly="ro('withdrawalRestriction')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                </AppGadget>

                <StickySaveBar v-if="canManage && (draft || canPrice)" submit revertable :dirty="dirty" :saving="saving" :disabled="!!fulfillProblem" :error-count="Object.keys(serverErrors).length" :error="formError" @revert="revert" />
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

        <!-- 단품 공급 불가(결정 53 · 88) -->
        <PaymentReasonDialog v-model:visible="supplyOpen" :title="t('groupBuy.supplyTitle')" :description="t('groupBuy.supplyBody')" :confirm-label="t('groupBuy.supplySubmit')" tone="danger" :busy="acting === 'supply'" :error="dialogError" :field-error="dialogFieldError" @submit="doSupply">
            <LabeledField :label="t('groupBuy.supplySku')" :error="supplySkuError">
                <template #default="{ id, describedBy, invalid }">
                    <Select :input-id="id" v-model="supplySkuId" :options="supplyChoices" option-label="label" option-value="value" :placeholder="t('groupBuy.supplySkuPlaceholder')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>
            <p class="m-0 text-xs text-muted-color">{{ t('groupBuy.supplyAddonNote') }}</p>
            <div v-if="supplyPreview" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                <span>{{ t('groupBuy.supplyPreviewRefund', { lines: supplyPreview.cancelLines, amount: UtilGroupBuy.won(supplyPreview.refundAmount, locale) }) }}</span>
                <span>{{ t('groupBuy.supplyPreviewFull', { n: supplyPreview.fullRefundOrders }) }}</span>
                <span>{{ t('groupBuy.supplyPreviewChoice', { n: supplyPreview.choiceOrders, deadline: time.full(supplyPreview.choiceDeadline) }) }}</span>
            </div>
            <Skeleton v-else-if="supplySkuId && supplyLoading" height="3.5rem" />
        </PaymentReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { GroupBuyNoticeRow } from '~/components/groupbuy/GroupBuyNoticeEditor.types';
import type { GroupBuyAddonRow, GroupBuyAxisRow, GroupBuyMainSkuRow, GroupBuySkuRef } from '~/components/groupbuy/GroupBuySkuEditor.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    GroupBuyCancelPreview,
    GroupBuyDetail,
    GroupBuyExtensionMode,
    GroupBuyFormOptions,
    GroupBuyFulfillment,
    GroupBuyImage,
    GroupBuyNotice,
    GroupBuyNoticeIncomplete,
    GroupBuyNoticeInput,
    GroupBuyRevision,
    GroupBuySaveRequest,
    GroupBuySku,
    GroupBuySkuInput,
    GroupBuySkuKind,
    GroupBuySupplyFailurePreview,
    NormalPriceBasis,
    NoticeCatalog,
    NoticeCustomInput,
    ReturnFeeMode,
} from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import { NORMAL_PRICE_BASES } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type { GroupBuyAddonSource, GroupBuyProductDetail, GroupBuyProductRow } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface EditForm {
    title: string;
    /** 상품(결정 89) */
    productId: string | null;
    productName: string;
    summary: string;
    spec: string;
    description: string;
    images: GroupBuyImage[];
    normalPrice: string;
    groupPrice: string;
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    /** 옵션 축 · 조합(MAIN 단품, 축이 없으면 한 줄) · 추가 구성상품(결정 88) */
    axes: GroupBuyAxisRow[];
    mains: GroupBuyMainSkuRow[];
    addons: GroupBuyAddonRow[];
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
    notices: GroupBuyNoticeRow[];
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
/** 공급가를 보는가(결정 89 — 가격 권한 또는 정산 조회). 고치는 것은 `canPrice` */
const canSeeSupply = computed(() => canPrice.value || can('commerce.settlement.read'));
const productApi = useGroupBuyProductApi();
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

// 상품정보제공고시 카탈로그·409 표시(아래 「상품정보제공고시」 절에서 쓴다)
const catalog = ref<NoticeCatalog | null>(null);
const catalogError = ref<ApiError | null>(null);
const catalogLoading = ref(false);
/** 서버 409 `GROUP_BUY_NOTICE_INCOMPLETE` 가 돌려준 빈 양식·항목 */
const noticeIncomplete = ref<GroupBuyNoticeIncomplete | null>(null);
const noticeEditor = ref<{ focusProblem: () => Promise<void> } | null>(null);

const pageTitle = computed(() => (isNew.value ? t('groupBuy.newTitle') : (detail.value?.title ?? t('groupBuy.editTitle'))));
useSeoMeta({ title: () => pageTitle.value });

/** DRAFT(새 공구 포함)면 모든 칸을 고친다 */
const draft = computed(() => isNew.value || detail.value?.status === 'DRAFT');

function emptyForm(): EditForm {
    return {
        title: '',
        productId: typeof route.query.productId === 'string' ? route.query.productId : null,
        productName: '',
        summary: '',
        spec: '',
        description: '',
        images: [],
        normalPrice: '',
        groupPrice: '',
        normalPriceBasis: null,
        normalPriceSource: '',
        axes: [],
        mains: [singleMain()],
        addons: [],
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
        notices: [],
    };
}

/** 축 없는 단품 하나(공구 전체) */
function singleMain(): GroupBuyMainSkuRow {
    return { key: 'main:', id: null, productSkuId: null, valueKeys: [], extraPrice: '0', maxQty: '', onSale: true, supplyPrice: '', currentQty: 0, reservedQty: 0 };
}

/** 응답 단품 → 편집 줄. 불러온 축·값·단품의 줄 키는 서버 ID(조합 줄 키 = `main:` + 값 ID 들 — 편집기와 같은 규칙) */
function skuRowsOf(d: GroupBuyDetail): Pick<EditForm, 'axes' | 'mains' | 'addons'> {
    const axes = d.axes.map((a) => ({ key: a.id, id: a.id, name: a.name, values: a.values.map((v) => ({ key: v.id, id: v.id, name: v.name })) }));
    const mains = d.skus
        .filter((s) => s.kind === 'MAIN')
        .map((s) => ({ key: mainKeyOf(s), id: s.skuId, productSkuId: s.productSkuId, valueKeys: [...s.valueIds], extraPrice: String(s.extraPrice), maxQty: s.maxQty === null ? '' : String(s.maxQty), onSale: s.onSale, supplyPrice: s.supplyPrice === null ? '' : String(s.supplyPrice), currentQty: s.currentQty, reservedQty: s.reservedQty }));
    const addons = d.skus
        .filter((s) => s.kind === 'ADDON')
        .map((s) => ({
            key: s.skuId,
            id: s.skuId,
            productSkuId: s.productSkuId,
            name: s.name,
            price: String(s.unitPrice),
            supplyPrice: s.supplyPrice === null ? '' : String(s.supplyPrice),
            maxQty: s.maxQty === null ? '' : String(s.maxQty),
            image: s.imageKey && s.imageUrl ? { mediaId: s.imageKey, url: s.imageUrl, thumbUrl: s.imageUrl } : null,
            currentQty: s.currentQty,
            reservedQty: s.reservedQty,
        }));
    return { axes, mains: mains.length ? mains : [singleMain()], addons };
}

/** 응답 단품의 편집 줄 키 — MAIN 은 `main:` + 값 ID 들(편집기의 조합 키와 같은 규칙), ADDON 은 단품 ID */
function mainKeyOf(s: GroupBuySku): string {
    return s.kind === 'MAIN' ? `main:${s.valueIds.join('|')}` : s.skuId;
}

/** 편집 중 단품 목록(고시 적용 대상 · 저장 이름) — MAIN 이름은 지금 축 값으로 만든다. 추가 구성상품은 늘 판매(저장 칸 없음) */
function skuRefsOf(f: Pick<EditForm, 'axes' | 'mains' | 'addons'>): GroupBuySkuRef[] {
    const valueName = new Map(f.axes.flatMap((a) => a.values.map((v) => [v.key, v.name] as const)));
    return [
        ...f.mains.map((m): GroupBuySkuRef => ({ key: m.key, kind: 'MAIN', name: UtilGroupBuy.skuMainName(m.valueKeys.map((k) => valueName.get(k) ?? '')), onSale: m.onSale })),
        ...f.addons.map((a): GroupBuySkuRef => ({ key: a.key, kind: 'ADDON', name: a.name.trim(), onSale: true })),
    ];
}

function formOf(d: GroupBuyDetail): EditForm {
    const skuRows = skuRowsOf(d);
    const keyOfSku = new Map(d.skus.map((s) => [s.skuId, mainKeyOf(s)]));
    return {
        title: d.title,
        productId: d.productId,
        productName: d.productName,
        summary: d.summary,
        spec: d.spec,
        description: d.description,
        images: d.images.map((i) => ({ ...i })),
        normalPrice: String(d.normalPrice),
        groupPrice: String(d.groupPrice),
        normalPriceBasis: d.normalPriceBasis,
        normalPriceSource: d.normalPriceSource,
        ...skuRows,
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
        notices: d.notices.map((n, i) => noticeRowOf(n, i, keyOfSku)),
    };
}

const form = ref<EditForm>(emptyForm());
const initial = ref('');
/** 비교용 — 줄 키·표시용 수량은 뺀다(조합 줄 키는 값 키라서 조합 비교에 쓴다) */
function snapshot(f: EditForm): string {
    return JSON.stringify({
        ...f,
        images: f.images.map((i) => i.mediaId),
        axes: f.axes.map((a) => [a.id, a.name, a.values.map((v) => [v.id, v.name])]),
        mains: f.mains.map((m) => [m.key, m.extraPrice, m.maxQty, m.onSale, m.supplyPrice]),
        addons: f.addons.map((a) => [a.id, a.productSkuId, a.name, a.price, a.maxQty, a.supplyPrice]),
        notices: noticesView(f),
    });
}
const dirty = computed(() => initial.value !== '' && snapshot(form.value) !== initial.value);

function applyDetail(d: GroupBuyDetail): void {
    detail.value = d;
    form.value = formOf(d);
    initial.value = snapshot(form.value);
    serverErrors.value = {};
    formError.value = '';
    noticeIncomplete.value = null;
    formKey.value += 1;
}

/** 칸이 읽기 전용인가 — 권한 없음 · 저장 중 · 모집 뒤 잠긴 칸 · 가격 칸인데 가격 권한 없음 */
function ro(field: string, price = false): boolean {
    if (!canManage.value || saving.value) return true;
    if (UtilGroupBuy.PRODUCT_FIELDS.includes(field)) return true;
    if (!draft.value && !UtilGroupBuy.CONTENT_FIELDS.includes(field)) return true;
    return price && !canPrice.value;
}

function lockHint(field: string, price = false): string {
    if (UtilGroupBuy.PRODUCT_FIELDS.includes(field)) return t('groupBuy.fromProduct');
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

// ── 상품정보제공고시(결정 87·88) ──
/** 응답 양식 → 편집 줄. 적용 단품 ID(`skuIds`)를 단품 줄 키로 바꾼다 — 지금 단품에 없는 ID 는 버린다 */
function noticeRowOf(n: GroupBuyNotice, index: number, keyOfSku: ReadonlyMap<string, string>): GroupBuyNoticeRow {
    const skuKeys = n.skuIds.flatMap((id) => (keyOfSku.has(id) ? [keyOfSku.get(id)!] : []));
    return { key: `notice-${index}`, categoryCode: n.categoryCode, title: n.title, skuKeys, values: n.values.map((v) => ({ ...v })), customItems: n.customItems.map((c) => ({ ...c })) };
}

/** 응답 모양 양식(ID 없이) — 화면 키를 떼고 적용 단품을 지금 단품 이름으로(이름 없는·지운 단품은 뺀다). 미리 세기·비교·저장용 */
function noticesView(f: EditForm): Omit<GroupBuyNotice, 'skuIds'>[] {
    const nameOf = new Map(skuRefsOf(f).map((s) => [s.key, s.name]));
    return f.notices.map((n) => ({
        categoryCode: n.categoryCode,
        title: n.title,
        skuNames: n.skuKeys.flatMap((key) => (nameOf.get(key) ? [nameOf.get(key)!] : [])),
        values: n.values.map((v) => ({ ...v })),
        customItems: n.customItems.map((c, i) => ({ ...c, sortOrder: i })),
    }));
}

/** 저장 요청 양식 — 고시 항목 뒤에 판매자 추가 항목을 `values[]` 의 `custom` 줄로(빈 줄도 보낸다 — 서버가 버린다) */
function noticesOut(f: EditForm): GroupBuyNoticeInput[] {
    return noticesView(f).map(({ customItems, ...n }) => ({
        ...n,
        values: [...n.values, ...customItems.map((c): NoticeCustomInput => ({ custom: true, itemKey: null, name: c.name, value: c.value, sortOrder: c.sortOrder }))],
    }));
}

const skuRefs = computed<GroupBuySkuRef[]>(() => skuRefsOf(form.value));
/** 조합 이름(추가 구성상품 이름 겹침 검사용) */
const mainNames = computed(() => skuRefs.value.filter((s) => s.kind === 'MAIN' && s.name).map((s) => s.name));

/** 구획 머리 배지·모집 시작 안내 — 서버와 같은 규칙으로 센다(판정은 서버). 고시 대상 = 판매하는 이름 있는 단품 */
const noticeSummary = computed(() => {
    if (!catalog.value) return null;
    const targets = skuRefs.value.filter((s) => s.onSale && s.name);
    const problems = UtilGroupBuy.noticeProblems(noticesView(form.value), catalog.value, targets);
    const addonNames = new Set(targets.filter((s) => s.kind === 'ADDON').map((s) => s.name));
    const blank = problems.notices.reduce((sum, p) => sum + p.missingKeys.length, 0);
    const addonMissing = problems.unassignedSkus.filter((name) => addonNames.has(name)).length;
    return { noNotice: problems.noNotice, blank, addonMissing, unassigned: problems.unassignedSkus.length - addonMissing, complete: UtilGroupBuy.noticeComplete(problems) };
});
const noticeErrorCount = computed(() => Object.keys(serverErrors.value).filter((k) => k.startsWith('notices[')).length);

async function loadCatalog(): Promise<void> {
    catalogLoading.value = true;
    catalogError.value = null;
    try {
        catalog.value = await api.noticeCatalog();
    } catch (e) {
        catalogError.value = toApiError(e);
    } finally {
        catalogLoading.value = false;
    }
}

function goNotice(): void {
    void noticeEditor.value?.focusProblem();
}

/** 409 `GROUP_BUY_NOTICE_INCOMPLETE` 면 그 목록을 표시하고 첫 항목으로 옮긴다 */
function showNoticeIncomplete(failure: ApiError): void {
    if (failure.code !== 'GROUP_BUY_NOTICE_INCOMPLETE' || !failure.extras) return;
    const extras = failure.extras as Partial<GroupBuyNoticeIncomplete>;
    noticeIncomplete.value = { noNotice: !!extras.noNotice, notices: Array.isArray(extras.notices) ? extras.notices : [], unassignedSkus: Array.isArray(extras.unassignedSkus) ? extras.unassignedSkus : [] };
    void noticeEditor.value?.focusProblem();
}

const warnings = computed(() =>
    UtilGroupBuy.phraseWarnings(form.value.title, form.value.summary, form.value.spec, form.value.description, form.value.withdrawalRestriction, ...form.value.axes.flatMap((a) => [a.name, ...a.values.map((v) => v.name)]), ...form.value.addons.map((a) => a.name)),
);

// ── 상품(결정 89) ──
const products = ref<GroupBuyProductRow[]>([]);
const productLoading = ref(false);
const productError = ref('');
const addonSources = ref<GroupBuyAddonSource[]>([]);
/** 추가 구성상품 상품의 고시 양식(상품 ID → 상세) — 고른 단품마다 그 상품의 양식을 붙인다 */
const addonProducts = new Map<string, GroupBuyProductDetail>();

const productChoices = computed(() => products.value.map((p) => ({ value: p.id, label: `${p.name} · ${p.categoryPath}` })));
const productPath = computed(() => products.value.find((p) => p.id === form.value.productId)?.categoryPath ?? detail.value?.categoryPath ?? '');

async function loadProducts(): Promise<void> {
    productLoading.value = true;
    try {
        products.value = (await productApi.list({ page: 0, size: 200 })).items;
        addonSources.value = await productApi.addonSources(form.value.productId);
    } catch (e) {
        productError.value = toApiError(e).message;
    } finally {
        productLoading.value = false;
    }
}

/** 상품의 설명·이미지·고시·옵션 축·단품을 공구 칸에 채운다(단품별 공구 설정은 새로 — 공급가는 기본 공급가 복사) */
function fillFromProduct(p: GroupBuyProductDetail): void {
    const axes = p.axes.map((a) => ({ key: a.id, id: null, name: a.name, values: a.values.map((v) => ({ key: v.id, id: null, name: v.name })) }));
    const mains: GroupBuyMainSkuRow[] = p.skus.map((s) => ({ key: `main:${s.valueIds.join('|')}`, id: null, productSkuId: s.skuId, valueKeys: [...s.valueIds], extraPrice: '0', maxQty: '', onSale: s.onSale, supplyPrice: s.defaultSupplyPrice === null ? '' : String(s.defaultSupplyPrice), currentQty: 0, reservedQty: 0 }));
    const keyOfName = new Map(p.skus.map((s, i) => [s.name, mains[i]!.key]));
    form.value = {
        ...form.value,
        productId: p.id,
        productName: p.name,
        title: form.value.title.trim() ? form.value.title : `${p.name} 공동구매`,
        summary: p.summary,
        spec: p.spec,
        description: p.description,
        images: p.images.map((i) => ({ ...i })),
        normalPrice: String(p.normalPrice),
        normalPriceBasis: p.normalPriceBasis,
        normalPriceSource: p.normalPriceSource,
        withdrawalRestriction: p.withdrawalRestriction,
        axes,
        mains: mains.length ? mains : [singleMain()],
        addons: [],
        notices: p.notices.map((n, i) => ({ key: `notice-p-${i}`, categoryCode: n.categoryCode, title: n.title, skuKeys: n.skuNames.flatMap((name) => (keyOfName.has(name) ? [keyOfName.get(name)!] : [])), values: n.values.map((v) => ({ ...v })), customItems: n.customItems.map((c) => ({ ...c })) })),
    };
    formKey.value += 1;
}

async function pickProduct(id: string): Promise<void> {
    if (!id || id === form.value.productId) return;
    if (form.value.productId && !(await confirm({ title: t('groupBuy.productChangeTitle'), message: t('groupBuy.productChangeBody'), confirmLabel: t('groupBuy.productChange') }))) return;
    productError.value = '';
    try {
        fillFromProduct(await productApi.detail(id));
        addonSources.value = await productApi.addonSources(id);
    } catch (e) {
        productError.value = toApiError(e).message;
    }
}

/** 추가 구성상품을 고르거나 빼면 그 상품의 고시 양식을 붙이거나 뗀다(그 상품의 첫 양식 = 그 단품 하나에 적용) */
async function syncAddonNotices(): Promise<void> {
    if (!draft.value) return;
    const addonKeys = new Set(form.value.addons.map((a) => a.key));
    const mainNotices = form.value.notices.filter((n) => !n.key.startsWith('notice-a-'));
    const addonNotices: GroupBuyNoticeRow[] = [];
    for (const a of form.value.addons) {
        const src = addonSources.value.find((s) => s.productSkuId === a.productSkuId);
        if (!src) continue;
        let p = addonProducts.get(src.productId);
        if (!p) {
            try {
                p = await productApi.detail(src.productId);
                addonProducts.set(src.productId, p);
            } catch {
                continue;
            }
        }
        const n = p.notices[0];
        if (n) addonNotices.push({ key: `notice-a-${a.key}`, categoryCode: n.categoryCode, title: n.title || a.name, skuKeys: [a.key], values: n.values.map((v) => ({ ...v })), customItems: n.customItems.map((c) => ({ ...c })) });
    }
    void addonKeys;
    form.value = { ...form.value, notices: [...mainNotices, ...addonNotices] };
}
watch(
    () => form.value.addons.map((a) => `${a.key}:${a.productSkuId ?? ''}`).join(','),
    (now, before) => {
        if (before !== undefined && now !== before) void syncAddonNotices();
    },
);

// ── 공급가(결정 89) ──
/** 서버가 준 단품 ID(공구 단품 또는 상품 단품) → 이름 */
function skuNamesOf(ids: readonly string[]): string[] {
    const d = detail.value;
    const nameOf = new Map(skuRefs.value.map((s) => [s.key, s.name]));
    return ids.map(
        (id) =>
            d?.skus.find((s) => s.skuId === id || s.productSkuId === id)?.name ||
            nameOf.get(form.value.mains.find((m) => m.productSkuId === id)?.key ?? '') ||
            form.value.addons.find((a) => a.productSkuId === id)?.name ||
            form.value.productName,
    );
}

/** 409 `GROUP_BUY_NEGATIVE_MARGIN {skuIds}`(판매가 < 공급가 — 결정 89)이면 확인을 받는다. true = 확인했으니 `marginConfirmed` 로 다시 보낸다 */
async function confirmNegativeMargin(failure: ApiError): Promise<boolean> {
    if (failure.code !== 'GROUP_BUY_NEGATIVE_MARGIN') return false;
    const ids = Array.isArray(failure.extras?.skuIds) ? (failure.extras.skuIds as string[]) : [];
    return confirm({ title: t('groupBuy.marginTitle'), message: t('groupBuy.marginBody', { names: skuNamesOf(ids).join(', ') }), confirmLabel: t('groupBuy.marginConfirm'), tone: 'danger' });
}

/** 모집 뒤 공급가 정정 — 단품 ID 와 지금 칸 값 */
function supplyPricesOut(f: EditForm, d: GroupBuyDetail): { skuId: string; supplyPrice: number | null }[] {
    const opt = (s: string) => (s.trim() === '' ? null : UtilGroupBuy.int(s));
    const mainId = new Map(d.skus.filter((s) => s.kind === 'MAIN').map((s) => [mainKeyOf(s), s.skuId]));
    return [...f.mains.flatMap((m) => (mainId.get(m.key) ? [{ skuId: mainId.get(m.key)!, supplyPrice: opt(m.supplyPrice) }] : [])), ...f.addons.flatMap((a) => (a.id ? [{ skuId: a.id, supplyPrice: opt(a.supplyPrice) }] : []))];
}

/** 모집 시작 실패 — 고시 409 는 목록 표시, 공급가 409 는 빠진 단품 이름을 붙인다 */
function onStartFailure(failure: ApiError): void {
    showNoticeIncomplete(failure);
    if (failure.code === 'GROUP_BUY_SUPPLY_PRICE_MISSING') {
        const names = skuNamesOf(Array.isArray(failure.extras?.skuIds) ? (failure.extras.skuIds as string[]) : []);
        if (names.length) queueMicrotask(() => (actionError.value = `${failure.message} (${names.join(', ')})`));
    }
}

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
    void loadCatalog();
    await loadOptions();
    await load();
    await loadProducts();
    // 「이 상품으로 공구 만들기」(`?productId=`) — 새 공구에 상품을 바로 채운다
    if (isNew.value && form.value.productId) {
        const id = form.value.productId;
        form.value = { ...form.value, productId: null };
        await pickProduct(id);
        initial.value = snapshot(emptyForm());
    }
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
/** 서버 `GroupBuyDraft`(결정 89 · 2b-2) — 상품 + 판매 조건 + 상품 단품마다 이 공구 설정 + 다른 상품 단품의 추가 구성상품 */
function toRequest(f: EditForm, marginConfirmed: boolean): GroupBuySaveRequest {
    const num = (s: string) => UtilGroupBuy.int(s) ?? 0;
    const opt = (s: string) => (s.trim() === '' ? null : UtilGroupBuy.int(s));
    return {
        productId: f.productId,
        title: f.title.trim(),
        groupPrice: num(f.groupPrice),
        shippingPolicyCode: f.shippingPolicyCode,
        minQty: num(f.minQty),
        maxQty: num(f.maxQty),
        maxQtyPerUser: opt(f.maxQtyPerUser),
        startAt: f.startAt || null,
        endAt: f.endAt || null,
        extensionMode: f.extensionMode,
        fulfillment: f.fulfillment,
        shipPlannedAt: f.fulfillment === 'AFTER_CLOSE' ? f.shipPlannedAt || null : null,
        shipWithinBusinessDays: f.fulfillment === 'IMMEDIATE' ? opt(f.shipWithinBusinessDays) : null,
        returnPolicy: '',
        roundLimitShared: f.roundLimitShared,
        skus: f.mains.flatMap((m): GroupBuySkuInput[] => (m.productSkuId ? [{ productSkuId: m.productSkuId, extraPrice: num(m.extraPrice), maxQty: opt(m.maxQty), onSale: m.onSale, supplyPrice: opt(m.supplyPrice) }] : [])),
        addons: f.addons.flatMap((a) => (a.productSkuId ? [{ productSkuId: a.productSkuId, price: num(a.price), maxQty: opt(a.maxQty), supplyPrice: opt(a.supplyPrice) }] : [])),
        marginConfirmed,
        version: detail.value?.version ?? null,
    };
}

async function saveOnce(marginConfirmed: boolean): Promise<void> {
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    noticeIncomplete.value = null;
    try {
        if (isNew.value) {
            const created = await api.create(toRequest(form.value, marginConfirmed));
            initial.value = snapshot(form.value); // 떠나기 확인을 건너뛴다
            toast.success(t('groupBuy.created'));
            await router.replace(`/group-buys/${encodeURIComponent(created.id)}`);
            return;
        }
        const d = detail.value!;
        // 모집 뒤는 공급가만(결정 89 · 2b-2)
        const saved = draft.value ? await api.update(d.id, toRequest(form.value, marginConfirmed)) : await api.updateContent(d.id, { supplyPrices: supplyPricesOut(form.value, d), marginConfirmed, version: d.version });
        applyDetail(saved);
        toast.success(t('groupBuy.saved'));
    } catch (e) {
        const failure = toApiError(e);
        if (!marginConfirmed && failure.code === 'GROUP_BUY_NEGATIVE_MARGIN') {
            saving.value = false;
            if (await confirmNegativeMargin(failure)) await saveOnce(true);
            return;
        }
        if (failure.code === 'VALIDATION' && failure.fields && Object.keys(failure.fields).length) {
            serverErrors.value = failure.fields;
            formError.value = t('groupBuy.saveFieldsRejected');
        } else {
            formError.value = failure.message;
            showNoticeIncomplete(failure);
        }
    } finally {
        saving.value = false;
    }
}

async function save(): Promise<void> {
    if (saving.value || !dirty.value || fulfillProblem.value) return;
    await saveOnce(false);
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
async function run(act: Act, work: () => Promise<GroupBuyDetail | null>, done: string, inDialog: boolean, onFailure?: (failure: ApiError) => void): Promise<boolean> {
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
        onFailure?.(failure);
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
    noticeIncomplete.value = null;
    if (!ok) return;
    let margin: ApiError | null = null;
    const done = await run('start', () => api.start(d.id), t('groupBuy.started'), false, (failure) => {
        if (failure.code === 'GROUP_BUY_NEGATIVE_MARGIN') margin = failure;
        onStartFailure(failure);
    });
    // 역마진(409) — 확인하면 `marginConfirmed` 로 다시
    if (!done && margin && (await confirmNegativeMargin(margin))) await run('start', () => api.start(d.id, true), t('groupBuy.started'), false, onStartFailure);
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

/** 단품 이름 글자(축 없는 단품 하나는 「단품 하나」, 추가 구성상품은 딱지 글자를 붙인다) */
function skuText(name: string, kind: GroupBuySkuKind): string {
    const base = name || t('groupBuy.skuSingle');
    return kind === 'ADDON' ? `${base} (${t('groupBuy.skuAddonTag')})` : base;
}

// 단품 공급 불가(결정 53 · 88) — 판매하는 단품에서 고른다
const supplyOpen = ref(false);
const supplySkuId = ref<string | null>(null);
const supplySkuError = ref('');
const supplyPreview = ref<GroupBuySupplyFailurePreview | null>(null);
const supplyLoading = ref(false);
const supplyChoices = computed(() => (detail.value?.skus ?? []).filter((s) => s.onSale).map((s) => ({ value: s.skuId, label: skuText(s.name, s.kind) })));
function openSupply(): void {
    supplySkuId.value = null;
    supplyPreview.value = null;
    supplySkuError.value = '';
    dialogError.value = '';
    dialogFieldError.value = '';
    supplyOpen.value = true;
}
watch(supplySkuId, async (skuId) => {
    supplyPreview.value = null;
    supplySkuError.value = '';
    const d = detail.value;
    if (!skuId || !d) return;
    supplyLoading.value = true;
    try {
        const preview = await api.supplyFailurePreview(d.id, skuId);
        if (supplySkuId.value === skuId) supplyPreview.value = preview;
    } catch (e) {
        supplySkuError.value = toApiError(e).message;
    } finally {
        supplyLoading.value = false;
    }
});
async function doSupply(reason: string): Promise<void> {
    const d = detail.value;
    if (!d) return;
    if (!supplySkuId.value) {
        supplySkuError.value = t('groupBuy.supplySkuRequired');
        return;
    }
    const skuId = supplySkuId.value;
    if (await run('supply', () => api.supplyFailure(d.id, { skuId, reason }), t('groupBuy.supplyDone'), true)) supplyOpen.value = false;
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
