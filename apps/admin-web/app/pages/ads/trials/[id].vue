<template>
    <!--
        광고 › 체험단 › 모집 상세(`/ads/trials/{id}?tab=applicants|shipping|damage`, trial-group-contract 「모집 · 신청 · 뽑기」·「운영자가 대신 보낼 때」·「대여형 파손」·「구현 결정」 10 — 서버 `TrialDetail`).
        머리: 제목 · 광고주·상품·제품값 · [공개 화면] · [고치기](서랍 — 뽑기 전만) · 상태 단추(서버 `action` — 모집 열기 · 신청 마감 · 끝내기 · 취소, 확인 뒤).
        요약: 상태 · 형태·보내는 쪽 · 인원(리뷰어 몫/일반 몫) · 신청 기간 · 사용기 게시판 · 기한 · 요금 · 금액(뽑기 뒤 확정) · 단계별 수.
        탭 ① 신청자·뽑기 — 표(회원 · 리뷰어 단계 · 몫 · 사용기 수 · 평균 추천 · 약속 지킨 비율 · 추천 ☆ — 운영자도 켜고 끈다)에서 고르고 리뷰어 몫/일반 몫을 따로 세며(넘치면 경고) [뽑기 확정](확인).
        탭 ② 입고·발송 — 보내는 쪽 운영자: 입고(수량 · 상태 사진 필수 → [입고 완료] · 보관 기한) · 회원별 [송장] · 받음 · 사용기 · (대여형) [반납 받음](사진) · [파손 신고](사진 + 설명) ·
            광고주 반송 송장. 보내는 쪽 광고주: 같은 표를 읽기만(송장·발송 전 사진·반납은 광고주가 사업자 센터에서 넣는다).
        탭 ③ 파손 신고 — 이 모집의 신고·이의·판정. 판정은 확인할 일(`/ads/trials/review?type=DAMAGE_REPORT`)에서 한다(사진 비교와 판정표).
        권한: 화면·처리 `trial.manage` · 확인할 일 `trial.review`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <NuxtLink :to="UtilTrial.PATHS.list" class="self-start text-xs font-semibold text-(--p-primary-color) hover:underline">{{ t('trial.backToList') }}</NuxtLink>

        <div v-if="state === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div class="h-20 animate-pulse rounded-xl bg-surface-100 dark:bg-surface-800" />
            <div class="h-64 animate-pulse rounded-xl bg-surface-100 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="state === 'error' || !detail" :title="t('trial.loadFailed')" :description="loadError" retry @retry="load" />

        <template v-else>
            <PageTitle :title="detail.title" :subtitle="`${detail.advertiser.name} · ${detail.productName} · ${won(detail.productPrice)}`">
                <template #actions>
                    <a v-if="origin" :href="`${origin}/trials/${encodeURIComponent(detail.id)}`" target="_blank" rel="noopener" class="p-button p-button-sm p-button-secondary p-button-outlined no-underline">{{ t('trial.openPublic') }}</a>
                    <Button v-if="canManage && detail.selectedCount === null && detail.status !== 'CANCELLED'" type="button" :label="t('trial.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="outlined" @click="openEdit" />
                    <template v-if="canManage">
                        <Button
                            v-for="a in UtilTrial.actionsFor(detail.status, detail.selectedCount !== null)"
                            :key="a"
                            type="button"
                            :label="t(`trial.actionStatus.${a}`)"
                            size="small"
                            :severity="a === 'CANCEL' ? 'danger' : undefined"
                            :variant="a === 'CANCEL' ? 'outlined' : undefined"
                            :disabled="busy"
                            @click="runAction(a)"
                        />
                    </template>
                </template>
            </PageTitle>

            <dl class="m-0 grid grid-cols-2 gap-x-4 gap-y-2 rounded-xl border border-(--line) bg-(--p-content-background) p-3 text-[13px] sm:grid-cols-4">
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.col.status') }}</dt><dd class="m-0"><Tag :value="t(`trial.status.${detail.status}`)" /></dd></div>
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.col.form') }}</dt><dd class="m-0">{{ t(`trial.form.${detail.form}`) }} · {{ t(`trial.shipper.${detail.shipper}`) }}</dd></div>
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.col.people') }}</dt><dd class="m-0 tabular-nums">{{ t('trial.splitLine', { total: detail.recruitCount, reviewer: detail.reviewerSlots, general: detail.recruitCount - detail.reviewerSlots }) }}</dd></div>
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.col.period') }}</dt><dd class="m-0 tabular-nums">{{ time.date(detail.applyStartAt) }} ~ {{ time.date(detail.applyEndAt) }}</dd></div>
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.drawer.reviewBoard') }}</dt><dd class="m-0">{{ detail.reviewBoard.name }}</dd></div>
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.dueLabel') }}</dt><dd class="m-0">{{ detail.returnDays ? t('trial.dueBoth', { review: detail.reviewDays, ret: detail.returnDays }) : t('trial.dueReview', { review: detail.reviewDays }) }}</dd></div>
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.priceTerms') }}</dt><dd class="m-0 tabular-nums">{{ t(`trial.drawer.priceUnit.${detail.priceUnit}`) }} {{ won(detail.unitPrice) }}</dd></div>
                <div><dt class="text-[11px] text-muted-color">{{ t('trial.col.amount') }}</dt><dd class="m-0 tabular-nums">{{ detail.amount === null ? t('trial.amountPending') : detail.amount === 0 ? t('trial.free') : won(detail.amount) }}</dd></div>
                <div class="col-span-2 sm:col-span-4">
                    <dt class="text-[11px] text-muted-color">{{ t('trial.countsLabel') }}</dt>
                    <dd class="m-0 tabular-nums text-[12px]">{{ t('trial.countsLine', { ...detail.counts }) }}</dd>
                </div>
            </dl>

            <Tabs :value="tab" class="min-w-0" @update:value="(v: string | number) => setTab(String(v))">
                <TabList>
                    <Tab value="applicants">{{ t('trial.tabApplicants') }}</Tab>
                    <Tab value="shipping">{{ t('trial.tabShipping') }}</Tab>
                    <Tab value="damage">{{ t('trial.tabDamage', { n: detail.damageReports.length }) }}</Tab>
                </TabList>
            </Tabs>

            <!-- ① 신청자·뽑기 -->
            <section v-if="tab === 'applicants'" class="flex flex-col gap-3">
                <div v-if="selectable" class="flex flex-wrap items-center gap-3 rounded-lg bg-surface-100 px-3 py-2 text-[13px] dark:bg-surface-900" aria-live="polite">
                    <span :class="picked.reviewer > slots.reviewer ? 'text-red-600 dark:text-red-400' : ''">{{ t('trial.pickedReviewer', { n: picked.reviewer, max: slots.reviewer }) }}</span>
                    <span :class="picked.general > slots.general ? 'text-red-600 dark:text-red-400' : ''">{{ t('trial.pickedGeneral', { n: picked.general, max: slots.general }) }}</span>
                    <span class="text-muted-color">{{ t('trial.pickedTotal', { n: selection.length, max: detail.recruitCount }) }}</span>
                    <Button type="button" :label="t('trial.pickRecommended')" size="small" severity="secondary" variant="text" @click="pickRecommended" />
                    <Button type="button" :label="t('trial.selectConfirm')" icon="pi pi-check" size="small" class="ml-auto" :disabled="selection.length === 0 || selection.length > detail.recruitCount || busy" @click="confirmSelect" />
                </div>
                <Message v-else-if="detail.selectedCount !== null" severity="secondary" variant="simple" size="small">{{ t('trial.selectedDone', { n: detail.selectedCount }) }}</Message>
                <Message v-if="selection.length > detail.recruitCount" severity="error" variant="simple" size="small">{{ t('trial.pickOver', { max: detail.recruitCount }) }}</Message>
                <Message v-else-if="selectable && (picked.reviewer > slots.reviewer || picked.general > slots.general)" severity="warn" variant="simple" size="small">{{ t('trial.pickQuotaWarn') }}</Message>

                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable v-model:selection="selection" :value="applications" data-key="id" :loading="appsLoading" scrollable>
                        <template #empty>
                            <ErrorState v-if="appsError" :title="t('trial.appsLoadFailed')" :description="appsError" retry @retry="loadApps" />
                            <div v-else-if="appsLoading" class="h-24" aria-hidden="true" />
                            <EmptyState v-else icon="pi pi-users" :title="t('trial.appsEmpty')" />
                        </template>
                        <Column v-if="selectable" selection-mode="multiple" style="width: 3rem" />
                        <Column :header="t('trial.col.member')" style="min-width: 9rem">
                            <template #body="{ data }"><TrialMemberName :member="data.member" :linkable="canReadMember" class="text-[13px] font-medium" /></template>
                        </Column>
                        <Column :header="t('trial.col.quota')" style="min-width: 7rem">
                            <template #body="{ data }">
                                <span class="text-[12px]">{{ t(`trial.quota.${data.quota}`) }}</span>
                                <span v-if="data.reviewerTier" class="block text-[11px] text-muted-color">{{ t('trial.reviewerTier', { tier: t(`trial.tier.${data.reviewerTier}`) }) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('trial.col.reviews')" style="min-width: 5rem"><template #body="{ data }"><span class="tabular-nums">{{ data.stats.reviewPosts }}</span></template></Column>
                        <Column :header="t('trial.col.avgLikes')" style="min-width: 5rem"><template #body="{ data }"><span class="tabular-nums">{{ data.stats.averageLikes.toFixed(1) }}</span></template></Column>
                        <Column :header="t('trial.col.kept')" style="min-width: 6rem">
                            <template #body="{ data }"><span class="tabular-nums">{{ data.stats.keptRate === null ? '—' : t('trial.keptLine', { rate: data.stats.keptRate, n: data.stats.judged }) }}</span></template>
                        </Column>
                        <Column :header="t('trial.col.recommended')" style="min-width: 6rem">
                            <template #body="{ data }">
                                <Button
                                    type="button"
                                    :icon="data.recommended ? 'pi pi-star-fill' : 'pi pi-star'"
                                    size="small"
                                    variant="text"
                                    :severity="data.recommended ? 'warn' : 'secondary'"
                                    :aria-label="data.recommended ? t('trial.recommendOff') : t('trial.recommendOn')"
                                    :aria-pressed="data.recommended"
                                    :disabled="!canManage || busy || data.status !== 'APPLIED'"
                                    @click="toggleRecommend(data.id, !data.recommended)"
                                />
                            </template>
                        </Column>
                        <Column :header="t('trial.col.result')" style="min-width: 6rem">
                            <template #body="{ data }"><Tag :value="t(`trial.appStatus.${data.status}`)" :severity="data.status === 'SELECTED' ? 'success' : data.status === 'APPLIED' ? 'secondary' : 'contrast'" /></template>
                        </Column>
                    </DataTable>
                </div>
            </section>

            <!-- ② 입고·발송 -->
            <section v-else-if="tab === 'shipping'" class="flex flex-col gap-3">
                <Message v-if="detail.shipper === 'ADVERTISER'" severity="secondary" variant="simple" size="small">{{ t('trial.advertiserShips') }}</Message>

                <!-- 입고(운영자가 대신) -->
                <div v-if="detail.shipper === 'OPERATOR'" class="rounded-xl border border-(--line) bg-(--p-content-background) p-3">
                    <h3 class="m-0 text-sm font-semibold">{{ t('trial.stockTitle') }}</h3>
                    <template v-if="detail.stock">
                        <p class="m-0 mt-1 text-[13px]">{{ t('trial.stockLine', { n: detail.stock.quantity, at: time.full(detail.stock.receivedAt), until: time.date(detail.stock.keepUntil) }) }}</p>
                        <p v-if="detail.stock.note" class="m-0 text-[12px] text-muted-color">{{ detail.stock.note }}</p>
                        <div class="mt-2 flex flex-wrap gap-1.5">
                            <a v-for="p in detail.stock.photos" :key="p.id" :href="p.url" target="_blank" rel="noopener"><img :src="p.url" alt="" class="size-14 rounded object-cover" /></a>
                        </div>
                        <p v-if="detail.stock.quantity < (detail.selectedCount ?? detail.recruitCount)" class="m-0 mt-2 text-[12px] text-red-600 dark:text-red-400">{{ t('trial.stockShort') }}</p>
                        <div class="mt-2 border-t border-(--line) pt-2 text-[13px]">
                            <span v-if="detail.stock.returnTrackingNo">{{ t('trial.returnShipLine', { carrier: detail.stock.returnCarrier ?? '', no: detail.stock.returnTrackingNo, at: detail.stock.returnShippedAt ? time.date(detail.stock.returnShippedAt) : '' }) }}</span>
                            <Button v-else-if="canManage" type="button" :label="t('trial.returnShip')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" @click="openAction('returnShip', null)" />
                        </div>
                    </template>
                    <template v-else>
                        <p class="m-0 mt-1 text-[12px] text-muted-color">{{ t('trial.stockHint') }}</p>
                        <Button v-if="canManage" type="button" :label="t('trial.stockReceive')" icon="pi pi-inbox" size="small" class="mt-2" @click="openAction('stock', null)" />
                    </template>
                </div>

                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable :value="detail.participants" data-key="id" scrollable>
                        <template #empty><EmptyState icon="pi pi-users" :title="t('trial.noParticipants')" /></template>
                        <Column :header="t('trial.col.member')" style="min-width: 8rem">
                            <template #body="{ data }">
                                <TrialMemberName :member="data.member" :linkable="canReadMember" class="text-[13px]" /><span class="block text-[11px] text-muted-color">{{ t(`trial.quota.${data.quota}`) }}</span>
                                <span v-if="data.recipient && !data.addressPurged" class="block text-[11px] text-muted-color">{{ data.recipient }} · {{ data.phone }}</span>
                                <span v-if="data.address1 && !data.addressPurged" class="block text-[11px] text-muted-color">({{ data.postcode }}) {{ data.address1 }} {{ data.address2 }}</span>
                                <span v-if="data.addressPurged" class="block text-[11px] text-muted-color">{{ t('trial.addressPurged') }}</span>
                            </template>
                        </Column>
                        <Column :header="t('trial.col.shipment')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <span v-if="data.trackingNo" class="text-[12px] tabular-nums">{{ data.carrier }} {{ data.trackingNo }}</span>
                                <Button v-else-if="canManage && detail.shipper === 'OPERATOR' && detail.stock" type="button" :label="t('trial.ship')" icon="pi pi-truck" size="small" @click="openAction('ship', data)" />
                                <span v-else class="text-[12px] text-muted-color">{{ detail.shipper === 'OPERATOR' && !detail.stock ? t('trial.needStock') : t('trial.notShipped') }}</span>
                                <span v-if="UtilTrial.photosOf(data, 'PRE_SHIP').length" class="block text-[11px] text-muted-color">{{ t('trial.preShipCount', { n: UtilTrial.photosOf(data, 'PRE_SHIP').length }) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('trial.col.received')" style="min-width: 7rem">
                            <template #body="{ data }"><span class="text-[12px]">{{ data.receivedAt ? `${time.date(data.receivedAt)}${data.receivedBy === 'AUTO' ? ` ${t('trial.autoReceived')}` : ''}` : '—' }}</span></template>
                        </Column>
                        <Column :header="t('trial.col.review')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <a v-if="data.reviewPost" :href="`${origin}/posts/${encodeURIComponent(data.reviewPost.id)}`" target="_blank" rel="noopener" class="line-clamp-1 text-[12px] text-(--p-primary-color) hover:underline">{{ data.reviewPost.name }}</a>
                                <span v-else-if="data.reviewDueAt" class="text-[12px]" :class="data.reviewLateAt ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('trial.reviewDueAt', { date: time.date(data.reviewDueAt) }) }}</span>
                                <span v-else class="text-[12px] text-muted-color">—</span>
                            </template>
                        </Column>
                        <Column v-if="detail.form === 'LEND'" :header="t('trial.col.return')" style="min-width: 11rem">
                            <template #body="{ data }">
                                <span v-if="data.unreturnedAt" class="text-[12px] text-red-600 dark:text-red-400">{{ t('trial.unreturned', { date: time.date(data.unreturnedAt) }) }}</span>
                                <span v-else-if="data.returnedAt" class="text-[12px]">{{ t('trial.returnedAt', { date: time.date(data.returnedAt) }) }}</span>
                                <template v-else>
                                    <span v-if="data.returnDueAt" class="block text-[12px] text-muted-color">{{ t('trial.returnDueAt', { date: time.date(data.returnDueAt) }) }}</span>
                                    <Button v-if="canMarkReturned && data.shippedAt" type="button" :label="t('trial.markReturned')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" class="mt-1" @click="openAction('returned', data)" />
                                </template>
                            </template>
                        </Column>
                        <Column v-if="detail.form === 'LEND' && detail.shipper === 'OPERATOR'" style="min-width: 7rem">
                            <template #body="{ data }">
                                <Button v-if="canManage && data.returnedAt" type="button" :label="t('trial.reportDamage')" icon="pi pi-exclamation-triangle" size="small" severity="warn" variant="text" @click="openAction('damage', data)" />
                            </template>
                        </Column>
                    </DataTable>
                </div>
            </section>

            <!-- ③ 파손 신고 -->
            <section v-else class="flex flex-col gap-2">
                <div class="flex items-center justify-between gap-2">
                    <p class="m-0 text-[12px] text-muted-color">{{ t('trial.damageHint') }}</p>
                    <Button v-if="can('trial.review')" as="router-link" :to="{ path: UtilTrial.PATHS.review, query: { type: 'DAMAGE_REPORT' } }" :label="t('trial.goDecide')" size="small" severity="secondary" variant="outlined" />
                </div>
                <EmptyState v-if="detail.damageReports.length === 0" icon="pi pi-shield" :title="t('trial.damageEmpty')" />
                <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="r in detail.damageReports" :key="r.id" class="rounded-xl border border-(--line) bg-(--p-content-background) p-3 text-[13px]">
                        <p class="m-0 flex flex-wrap items-center gap-2">
                            <Tag :value="r.verdict ? t(`trial.verdict.${r.verdict}`) : t(`trial.damageStatus.${r.status}`)" :severity="r.verdict ? 'secondary' : 'warn'" />
                            <TrialMemberName :member="r.member" :linkable="canReadMember" class="font-medium" />
                            <span class="text-[11px] text-muted-color">{{ t('trial.reportedBy', { kind: t(`trial.reporter.${r.reporterType}`), at: time.full(r.createdAt) }) }}</span>
                        </p>
                        <p class="m-0 mt-1 whitespace-pre-line">{{ r.description }}</p>
                        <div class="mt-2 flex flex-wrap gap-1.5">
                            <a v-for="p in r.photos" :key="p.id" :href="p.url" target="_blank" rel="noopener"><img :src="p.url" alt="" class="size-14 rounded object-cover" /></a>
                        </div>
                        <p v-if="r.objection" class="m-0 mt-2 rounded bg-surface-100 px-2 py-1 text-[12px] dark:bg-surface-800">{{ t('trial.objectionLine', { text: r.objection }) }}</p>
                        <p v-if="r.decisionNote" class="m-0 mt-1 text-[12px] text-muted-color">{{ t('trial.decisionLine', { note: r.decisionNote }) }}</p>
                    </li>
                </ul>
            </section>
        </template>

        <!-- 고치기 서랍 -->
        <TrialEditDrawer
            v-model:open="drawerOpen"
            :trial="detail"
            :options="formOptions.options.value"
            :can-manage="canManage"
            :saving="saving"
            :error="saveError"
            :field-errors="saveFieldErrors"
            :upload-cover="uploadCover"
            @save="save"
        />

        <!-- 입고 · 송장 · 반납 받음 · 파손 신고 · 광고주 반송 -->
        <Dialog v-model:visible="actionOpen" modal :header="t(`trial.action.${actionKind}`)" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!acting">
            <form class="flex flex-col gap-3" novalidate @submit.prevent="submitAction">
                <p v-if="actionTarget" class="m-0 text-sm">{{ t('trial.actionFor', { name: actionTarget.member.nickname ?? '' }) }}</p>
                <template v-if="actionKind === 'stock'">
                    <LabeledField :label="t('trial.stockQuantity')" :help="t('trial.stockQuantityHelp', { n: (detail?.selectedCount ?? detail?.recruitCount ?? 0) + 1 })" :error="fieldErrors.quantity ?? ''">
                        <template #default="{ id, describedBy, invalid }"><InputNumber v-model="quantity" :input-id="id" :min="1" :max="10000" :allow-empty="false" fluid :invalid="invalid" :aria-describedby="describedBy" /></template>
                    </LabeledField>
                    <LabeledField :label="t('trial.stockNote')" :error="fieldErrors.note ?? ''">
                        <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="note" maxlength="300" fluid :invalid="invalid" :aria-describedby="describedBy" /></template>
                    </LabeledField>
                </template>
                <template v-if="actionKind === 'ship' || actionKind === 'returnShip'">
                    <LabeledField :label="t('trial.carrier')" :error="fieldErrors.carrier ?? ''">
                        <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="carrier" maxlength="30" fluid :invalid="invalid" :aria-describedby="describedBy" /></template>
                    </LabeledField>
                    <LabeledField :label="t('trial.trackingNo')" :error="fieldErrors.trackingNo ?? (tried && !trackingNo.trim() ? t('trial.trackingRequired') : '')">
                        <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="trackingNo" maxlength="40" fluid :invalid="invalid" :aria-describedby="describedBy" /></template>
                    </LabeledField>
                    <p v-if="actionKind === 'returnShip'" class="m-0 text-xs text-muted-color">{{ t('trial.returnShipHelp') }}</p>
                </template>
                <LabeledField v-if="actionKind === 'damage'" :label="t('trial.damageDescription')" :error="fieldErrors.description ?? (tried && !description.trim() ? t('trial.damageDescriptionRequired') : '')">
                    <template #default="{ id, describedBy, invalid }"><Textarea :id="id" v-model="description" rows="3" auto-resize fluid :maxlength="UtilTrial.LIMITS.textMax" :invalid="invalid" :aria-describedby="describedBy" /></template>
                </LabeledField>
                <div v-if="photoKind" class="flex flex-col gap-1.5">
                    <span class="text-sm font-medium">{{ t(`trial.photoLabel.${photoKind}`) }}<span v-if="photoKind !== 'RETURN'" class="ml-1 text-xs text-red-600 dark:text-red-400">{{ t('trial.required') }}</span></span>
                    <div class="flex flex-wrap items-center gap-2">
                        <img v-for="p in photos" :key="p.id" :src="p.url" alt="" class="size-16 rounded object-cover" />
                        <input ref="fileInput" type="file" class="sr-only" accept="image/jpeg,image/png,image/webp" :aria-label="t('trial.photoAdd')" @change="onPhoto" />
                        <Button type="button" :label="t('trial.photoAdd')" icon="pi pi-camera" size="small" severity="secondary" variant="outlined" :loading="uploading" :disabled="acting" @click="fileInput?.click()" />
                    </div>
                    <small v-if="photoError" class="text-xs text-red-600 dark:text-red-400">{{ photoError }}</small>
                </div>
                <Message v-if="actionError" severity="error" variant="simple" size="small">{{ actionError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="acting" @click="actionOpen = false" />
                    <Button type="submit" :label="t('common.save')" :loading="acting" :disabled="uploading" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { AdminTrialApplication, AdminTrialDetail, AdminTrialParticipant, AdminTrialRequest, TrialAction, TrialPhotoKind, TrialUploaded } from '~/composables/trial/api/useTrialApi.types';
import UtilTrial from '~/utils/util-trial';
import UtilTrialMessages from '~/utils/util-trial-messages';

type Tab = 'applicants' | 'shipping' | 'damage';
type ActionKind = 'stock' | 'ship' | 'returned' | 'damage' | 'returnShip';

const { t, locale } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useTrialApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const { origin } = useUserWebOrigin();

const canManage = computed(() => can('trial.manage'));
const canReadMember = computed(() => can('member.read'));
/** 반납 받음 — 서버 `confirmReturn`: 운영자는 보내는 쪽과 상관없이, 발송한 줄이면(받음을 안 눌렀으면 서버가 받음으로 채운다). 반납 사진이 필수인데 사진 올리기가 trial.manage 라 그 권한만 */
const canMarkReturned = computed(() => canManage.value);
const id = computed(() => String(route.params.id ?? ''));
const tab = computed<Tab>(() => (route.query.tab === 'shipping' || route.query.tab === 'damage' ? route.query.tab : 'applicants'));
function setTab(v: string): void {
    void router.replace({ query: v === 'applicants' ? {} : { tab: v } });
}

const detail = ref<AdminTrialDetail | null>(null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const busy = ref(false);
const won = (n: number) => UtilTrialMessages.won(t, n, locale.value);

async function load(): Promise<void> {
    if (!detail.value) state.value = 'loading';
    try {
        detail.value = await api.detail(id.value);
        state.value = 'ready';
    } catch (e) {
        loadError.value = errorOf(e).message;
        state.value = 'error';
    }
}

/* ── 상태 바꾸기(모집 열기 · 신청 마감 · 끝내기 · 취소) ── */
async function runAction(action: TrialAction): Promise<void> {
    const d = detail.value;
    if (!d || busy.value) return;
    const ok = await confirm({ title: t(`trial.actionStatus.${action}`), message: t(`trial.actionConfirm.${action}`), confirmLabel: t(`trial.actionStatus.${action}`), ...(action === 'CANCEL' ? { tone: 'danger' as const } : {}) });
    if (!ok) return;
    busy.value = true;
    try {
        detail.value = await api.update(d.id, { ...UtilTrial.formOf(d), action });
        toast.success(t('trial.actionStatusDone', { action: t(`trial.actionStatus.${action}`) }));
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        busy.value = false;
    }
}

/* ── 신청자·뽑기 ── */
const applications = ref<AdminTrialApplication[]>([]);
const slots = ref({ reviewer: 0, general: 0 });
const appsLoading = ref(true);
const appsError = ref('');
const selection = ref<AdminTrialApplication[]>([]);
/** 뽑을 수 있나 — 모집 중·뽑는 중이고 아직 뽑지 않았다 */
const selectable = computed(() => canManage.value && !!detail.value && (detail.value.status === 'RECRUITING' || detail.value.status === 'SELECTING') && detail.value.selectedCount === null);
const picked = computed(() => UtilTrial.countPicked(applications.value, new Set(selection.value.map((a) => a.id))));

async function loadApps(): Promise<void> {
    appsLoading.value = true;
    appsError.value = '';
    try {
        const page = await api.applications(id.value);
        applications.value = page.items;
        slots.value = { reviewer: page.reviewerSlots, general: page.generalSlots };
        selection.value = page.items.filter((a) => a.status === 'SELECTED');
    } catch (e) {
        appsError.value = errorOf(e).message;
    } finally {
        appsLoading.value = false;
    }
}

/** 추천 후보를 몫마다 인원까지 고른다 */
function pickRecommended(): void {
    const rec = applications.value.filter((a) => a.recommended && a.status === 'APPLIED');
    selection.value = [...rec.filter((a) => a.quota === 'REVIEWER').slice(0, slots.value.reviewer), ...rec.filter((a) => a.quota === 'GENERAL').slice(0, slots.value.general)];
}

async function toggleRecommend(applicationId: string, on: boolean): Promise<void> {
    if (busy.value) return;
    busy.value = true;
    try {
        await api.recommend(applicationId, on);
        applications.value = applications.value.map((a) => (a.id === applicationId ? { ...a, recommended: on } : a));
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        busy.value = false;
    }
}

async function confirmSelect(): Promise<void> {
    const d = detail.value;
    if (!d || busy.value) return;
    const ok = await confirm({ title: t('trial.selectTitle'), message: t('trial.selectText', { n: selection.value.length, reviewer: picked.value.reviewer, general: picked.value.general }), confirmLabel: t('trial.selectConfirm') });
    if (!ok) return;
    busy.value = true;
    try {
        detail.value = await api.select(d.id, { applicationIds: selection.value.map((a) => a.id) });
        toast.success(t('trial.selected', { n: selection.value.length }));
        await loadApps();
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        busy.value = false;
    }
}

watch(
    id,
    () => {
        detail.value = null;
        void load();
        void loadApps();
    },
    { immediate: true },
);

/* ── 고치기 ── */
const drawerOpen = ref(false);
const formOptions = useTrialFormOptions();
const saving = ref(false);
const saveError = ref('');
const saveFieldErrors = ref<Record<string, string>>({});
async function openEdit(): Promise<void> {
    saveError.value = '';
    saveFieldErrors.value = {};
    drawerOpen.value = true;
    await formOptions.ensure();
}
async function uploadCover(file: File): Promise<{ key: string; url: string }> {
    const uploaded = await api.uploadCover(file);
    return { key: uploaded.key, url: uploaded.url };
}
async function save(body: AdminTrialRequest, trialId: string | null): Promise<void> {
    if (!trialId || saving.value) return;
    saving.value = true;
    saveError.value = '';
    saveFieldErrors.value = {};
    try {
        detail.value = await api.update(trialId, body);
        drawerOpen.value = false;
        toast.success(t('trial.saved'));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) saveFieldErrors.value = { ...error.fields };
        else saveError.value = error.message;
    } finally {
        saving.value = false;
    }
}

/* ── 입고 · 송장 · 반납 받음 · 파손 신고 · 광고주 반송 ── */
const actionOpen = ref(false);
const actionKind = ref<ActionKind>('stock');
const actionTarget = ref<AdminTrialParticipant | null>(null);
const quantity = ref(1);
const note = ref('');
const carrier = ref('');
const trackingNo = ref('');
const description = ref('');
const photos = ref<TrialUploaded[]>([]);
const tried = ref(false);
const acting = ref(false);
const uploading = ref(false);
const actionError = ref('');
const photoError = ref('');
const fieldErrors = ref<Record<string, string>>({});
const fileInput = ref<HTMLInputElement | null>(null);

const photoKind = computed<TrialPhotoKind | null>(() => (actionKind.value === 'stock' ? 'STOCK' : actionKind.value === 'returned' ? 'RETURN' : actionKind.value === 'damage' ? 'DAMAGE' : null));

function openAction(kind: ActionKind, target: AdminTrialParticipant | null): void {
    actionKind.value = kind;
    actionTarget.value = target;
    quantity.value = (detail.value?.selectedCount ?? detail.value?.recruitCount ?? 0) + 1;
    note.value = '';
    carrier.value = '';
    trackingNo.value = '';
    description.value = '';
    photos.value = [];
    tried.value = false;
    actionError.value = '';
    photoError.value = '';
    fieldErrors.value = {};
    actionOpen.value = true;
}

async function onPhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file || !detail.value || !photoKind.value) return;
    const problem = UtilTrial.photoProblem(file);
    if (problem) {
        photoError.value = problem === 'type' ? t('trial.photoTypeError') : t('trial.photoSizeError');
        return;
    }
    photoError.value = '';
    uploading.value = true;
    try {
        photos.value = [...photos.value, await api.uploadPhoto(detail.value.id, photoKind.value, file)];
    } catch (e) {
        photoError.value = errorOf(e).message;
    } finally {
        uploading.value = false;
    }
}

async function submitAction(): Promise<void> {
    tried.value = true;
    const d = detail.value;
    if (!d || acting.value) return;
    const kind = actionKind.value;
    const photoIds = photos.value.map((p) => p.id);
    if ((kind === 'stock' || kind === 'damage') && photoIds.length === 0) {
        photoError.value = kind === 'stock' ? t('trial.photoRequiredStock') : t('trial.photoRequiredDamage');
        return;
    }
    if ((kind === 'ship' || kind === 'returnShip') && !trackingNo.value.trim()) return;
    if (kind === 'damage' && !description.value.trim()) return;
    const target = actionTarget.value;
    acting.value = true;
    actionError.value = '';
    fieldErrors.value = {};
    try {
        if (kind === 'stock') detail.value = await api.stock(d.id, { quantity: quantity.value, note: note.value.trim() || null, photoIds });
        else if (kind === 'returnShip') detail.value = await api.returnShipment(d.id, { carrier: carrier.value.trim(), trackingNo: trackingNo.value.trim() });
        else if (target && kind === 'ship') await api.shipment(target.id, { carrier: carrier.value.trim(), trackingNo: trackingNo.value.trim() });
        else if (target && kind === 'returned') await api.returned(target.id, { photoIds });
        else if (target && kind === 'damage') await api.damage(target.id, { description: description.value.trim(), photoIds });
        actionOpen.value = false;
        toast.success(t(`trial.actionDone.${kind}`));
        if (kind !== 'stock' && kind !== 'returnShip') await load();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) {
            fieldErrors.value = { ...error.fields };
            if (error.fields.photoIds) photoError.value = error.fields.photoIds;
        } else actionError.value = error.message;
    } finally {
        acting.value = false;
    }
}

useSeoMeta({ title: () => (detail.value ? `${detail.value.title} · ${t('trial.title')}` : t('trial.title')) });
</script>
