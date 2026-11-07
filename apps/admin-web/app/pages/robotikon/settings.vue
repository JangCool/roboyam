<template>
    <!--
        로보티콘 › 로보티콘 설정(`/robotikon/settings`) — 작가 보상(무료 팩) · 팩 규격 · 심사 · 유료 판매·수수료·정산 규칙. 계약 `emoticon-contract.md` 「가격과 결제」·가정 1·3·4·6.
        실제 API(`GET|PUT /api/admin/emoticons/settings`). S2 부터 서버가 모든 칸을 저장한다(빠진 칸은 `400 fields.<칸>`) — 유료 판매 켜기(기본 꺼짐)·가격 단계·수수료·정산·환불 칸도 고칠 수 있다(`PAID_LOCKED` false).
        쓰는 가격 단계(`priceTiersInUse` — 판매 중 + 판매 중단 팩)는 뺄 수 없다(서버 `400 fields.priceTiers`). 상품화 계약 요약(`contract` — 게시본 없으면 null).
        저장은 화면 하나로 한 번(PUT) — 떠 있는 저장 줄(`StickySaveBar`, `forms.md` 2-4). 칸은 `InputText` + `inputmode=numeric`(`InputNumber` 는 범위 밖 값을 말없이 고친다).
        바꿀 수 있는 칸(결정 — 보고함): 받을 때마다 적립 포인트(0~1,000, 기본 10 — 0 이면 보상 없음) · 작가 하루 상한(1~100,000, 기본 500, 한 번 보상 이상) · 팩당 개수 최소(1~40, 기본 8)·최대(8~60, 기본 40)
          · 한 사람 심사 대기 팩(1~10, 기본 3). 범위는 `UtilEmoticon.SETTING_RANGES`(서버 CHECK 후보).
        고정 안내(바꾸지 않는다): 200×200(그림 190 + 둘레 5) · GIF/PNG · 배경 투명 · GIF 24프레임·무한 반복 · 한 개 2MB(권장 300KB) · 대표 200×200 PNG 1MB · 이름 2~20자 · 이름표 1~10자 · 태그 5개
          — 표시 크기·user-web 제작 가이드·업로드 검사와 묶여 있고, 바꾸면 이미 승인된 팩이 규격을 어기게 된다.
        유료 판매(실결제 — 카카오 방식, 사이트가 판매자): 켜기 · 가격 단계 목록(기본 1,500·2,500·3,500원, 100원 단위, 판매 중 팩이 쓰는 단계는 빼기 막음) · 수수료율(기본 30%, 결제 대행 수수료 포함)
          · 정산 — 매월 정산일(기본 10일) · 최소 지급(기본 10,000원, 미만은 다음 달로) · 셀프 환불 기간(기본 7일 — 정산은 이 기간이 지난 판매만 모은다). 원천징수 3.3%(개인 작가)는 법이 정한 값이라 안내만.
          정산 기준 = (판매 − 환불) ÷ 1.1(부가세 제외), 작가 몫 = 정산 기준 × (1 − 수수료율) → 별도 정산(현금). 계산은 `UtilEmoticon.creatorShare` 한 곳. 상품화 계약 구획 = 지금 판·시행일·동의 작가 수 + 약관 관리(`/settings/terms?kind=robotikon_contract`) 링크 — 문서는 약관 종류 `ROBOTIKON_CONTRACT`(2026-11-05 확정). 법무·세무 확인 필요 한 줄은 남긴다.
        권한(후보): 조회 `emoticon.read`(메뉴) · 저장 `emoticon.settings`(없으면 저장 줄 대신 읽기 전용 안내, 칸은 `readonly`).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('emoticon.settingsTitle')" :subtitle="t('emoticon.settingsSubtitle')" />

        <AppGadget v-if="loading && !saved" :title="t('emoticon.settings.rewardSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="3.5rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!saved" :title="t('emoticon.settings.rewardSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="loadError?.status === 403 ? t('emoticon.forbidden') : t('emoticon.settings.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <!-- 작가 보상 -->
            <AppGadget :title="t('emoticon.settings.rewardSection')" anchor="emoticon-reward" :changes="changedIn(['rewardPerAcquire', 'rewardDailyCap'])" :invalid="invalidIn(['rewardPerAcquire', 'rewardDailyCap'])">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.settings.rewardLead') }}</p>
                    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        <LabeledField v-for="field in REWARD_FIELDS" :key="field" :label="t(`emoticon.settings.field.${field}`)" :hint="rangeHint(field)" :help="t(`emoticon.settings.help.${field}`)" :error="errorOf(field)">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex items-center gap-2">
                                    <InputText :id="id" v-model="form[field]" inputmode="numeric" autocomplete="off" :readonly="!canSave || saving" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-28 text-right tabular-nums" @update:model-value="serverErrors[field] = ''" />
                                    <span class="text-sm text-muted-color">{{ t('emoticon.settings.unitPoint') }}</span>
                                    <span v-if="UtilEmoticon.parseInt(form[field]) !== saved.defaults[field]" class="text-xs text-muted-color">{{ t('emoticon.settings.defaultValue', { value: saved.defaults[field].toLocaleString(locale) }) }}</span>
                                </div>
                            </template>
                        </LabeledField>
                    </div>
                    <div class="rounded-lg border border-(--line) px-3 py-2.5">
                        <h3 class="m-0 mb-1.5 text-[13px] font-semibold">{{ t('emoticon.settings.countedTitle') }}</h3>
                        <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed">
                            <li v-for="key in COUNTED_RULES" :key="key" class="flex items-start gap-2"><i class="pi pi-check mt-0.5 text-xs text-green-600 dark:text-green-400" aria-hidden="true" /><span>{{ t(`emoticon.settings.counted.${key}`) }}</span></li>
                        </ul>
                    </div>
                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li v-for="key in REWARD_NOTES" :key="key" class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t(`emoticon.settings.rewardNote.${key}`) }}</span></li>
                    </ul>
                </div>
            </AppGadget>

            <!-- 팩 규격 -->
            <AppGadget :title="t('emoticon.settings.specSection')" anchor="emoticon-spec" :changes="changedIn(['itemMin', 'itemMax'])" :invalid="invalidIn(['itemMin', 'itemMax'])">
                <div class="flex flex-col gap-4">
                    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        <LabeledField v-for="field in COUNT_FIELDS" :key="field" :label="t(`emoticon.settings.field.${field}`)" :hint="rangeHint(field)" :help="t(`emoticon.settings.help.${field}`)" :error="errorOf(field)">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex items-center gap-2">
                                    <InputText :id="id" v-model="form[field]" inputmode="numeric" autocomplete="off" :readonly="!canSave || saving" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-20 text-right tabular-nums" @update:model-value="serverErrors[field] = ''" />
                                    <span class="text-sm text-muted-color">{{ t('emoticon.settings.unitCount') }}</span>
                                    <span v-if="UtilEmoticon.parseInt(form[field]) !== saved.defaults[field]" class="text-xs text-muted-color">{{ t('emoticon.settings.defaultValue', { value: saved.defaults[field] }) }}</span>
                                </div>
                            </template>
                        </LabeledField>
                    </div>
                    <div class="rounded-lg border border-dashed border-(--line) px-3 py-2.5">
                        <h3 class="m-0 mb-1.5 flex items-center gap-1.5 text-[13px] font-semibold"><i class="pi pi-lock text-xs text-muted-color" aria-hidden="true" />{{ t('emoticon.settings.fixedTitle') }}</h3>
                        <dl class="m-0 grid grid-cols-1 gap-x-4 gap-y-1.5 text-xs sm:grid-cols-2">
                            <div v-for="row in fixedRows" :key="row.key" class="flex min-w-0 items-baseline gap-2">
                                <dt class="shrink-0 text-muted-color">{{ row.label }}</dt>
                                <dd class="m-0 min-w-0 tabular-nums">{{ row.value }}</dd>
                            </div>
                        </dl>
                        <p class="m-0 mt-2 text-xs text-muted-color">{{ t('emoticon.settings.fixedNote') }}</p>
                    </div>
                </div>
            </AppGadget>

            <!-- 심사 -->
            <AppGadget :title="t('emoticon.settings.reviewSection')" anchor="emoticon-review" :changes="changedIn(['pendingLimit'])" :invalid="invalidIn(['pendingLimit'])">
                <div class="flex flex-col gap-4">
                    <LabeledField :label="t('emoticon.settings.field.pendingLimit')" :hint="rangeHint('pendingLimit')" :help="t('emoticon.settings.help.pendingLimit')" :error="errorOf('pendingLimit')">
                        <template #default="{ id, describedBy, invalid }">
                            <div class="flex items-center gap-2">
                                <InputText :id="id" v-model="form.pendingLimit" inputmode="numeric" autocomplete="off" :readonly="!canSave || saving" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-20 text-right tabular-nums" @update:model-value="serverErrors.pendingLimit = ''" />
                                <span class="text-sm text-muted-color">{{ t('emoticon.settings.unitPack') }}</span>
                                <span v-if="UtilEmoticon.parseInt(form.pendingLimit) !== saved.defaults.pendingLimit" class="text-xs text-muted-color">{{ t('emoticon.settings.defaultValue', { value: saved.defaults.pendingLimit }) }}</span>
                            </div>
                        </template>
                    </LabeledField>
                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settings.creatorRule') }}</span></li>
                    </ul>
                </div>
            </AppGadget>

            <!-- 유료 판매·수수료 — 실결제(원). 포인트로는 사지 않는다 -->
            <AppGadget :title="t('emoticon.settings.paidSection')" anchor="emoticon-paid" :changes="changedIn(PAID_FIELDS) + (paidDirty ? 1 : 0) + (tiersDirty ? 1 : 0)" :invalid="invalidIn(PAID_FIELDS) + (tiersError ? 1 : 0)">
                <div class="flex flex-col gap-4">
                    <Message v-if="PAID_LOCKED" severity="info" :closable="false" data-testid="emoticon-settings-paid-locked"><span class="text-[13px]">{{ t('emoticon.settings.paidLocked') }}</span></Message>
                    <Message v-if="paidStageLocked" severity="warn" :closable="false" data-testid="emoticon-settings-paid-stage">
                        <span class="text-[13px]">{{ t('emoticon.settings.paidStageLocked') }}</span>
                        <NuxtLink v-if="can('site.read')" to="/settings/operator" class="ml-1 whitespace-nowrap text-[13px] font-medium text-primary hover:underline">{{ t('emoticon.settings.paidStageLink') }} ›</NuxtLink>
                    </Message>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.settings.paidLead') }}</p>
                    <p class="m-0 flex items-start gap-2 text-xs leading-relaxed"><i class="pi pi-building mt-0.5 text-xs text-muted-color" aria-hidden="true" /><span>{{ t('emoticon.settings.sellerNote') }}</span></p>
                    <div class="flex flex-col gap-1">
                        <div class="flex items-center gap-3">
                            <ToggleSwitch v-model="paidSalesEnabled" :input-id="ids.paid" :disabled="!canSave || saving || PAID_LOCKED || (paidStageLocked && !paidSalesEnabled)" />
                            <label :for="ids.paid" class="text-sm font-medium">{{ t('emoticon.settings.field.paidSales') }}</label>
                            <span v-if="paidSalesEnabled !== saved.defaults.paidSalesEnabled" class="text-xs text-muted-color">{{ t('emoticon.settings.defaultValue', { value: saved.defaults.paidSalesEnabled ? t('common.on') : t('common.off') }) }}</span>
                        </div>
                        <p class="m-0 text-xs text-muted-color">{{ t('emoticon.settings.help.paidSales') }}</p>
                    </div>

                    <!-- 가격 단계(카카오 방식) — 운영자가 승인·가격 바꾸기 때 여기서 고른다. 판매 중 팩이 쓰는 단계는 뺄 수 없다 -->
                    <div class="flex flex-col gap-2">
                        <div class="flex flex-wrap items-baseline justify-between gap-2">
                            <span :id="ids.tiers" class="text-sm font-medium">{{ t('emoticon.settings.tiersTitle') }}</span>
                            <span v-if="!sameTiers(tiers, saved.defaults.priceTiers)" class="text-xs text-muted-color">{{ t('emoticon.settings.defaultValue', { value: saved.defaults.priceTiers.map((v) => v.toLocaleString(locale)).join(' · ') }) }}</span>
                        </div>
                        <ul class="m-0 flex list-none flex-wrap gap-2 p-0" :aria-labelledby="ids.tiers">
                            <li v-for="tier in tiers" :key="tier" class="inline-flex items-center gap-1 rounded-lg border border-(--line) py-1 pr-1 pl-3 text-[13px]">
                                <span class="tabular-nums">{{ t('emoticon.wonValue', { amount: tier.toLocaleString(locale) }) }}</span>
                                <span v-if="saved.priceTiersInUse.includes(tier)" v-tooltip.top="t('emoticon.settings.tierInUse')" class="inline-flex text-muted-color"><i class="pi pi-lock text-xs" :aria-label="t('emoticon.settings.tierInUse')" /></span>
                                <Button
                                    v-else-if="canSave && !PAID_LOCKED"
                                    type="button"
                                    icon="pi pi-times"
                                    size="small"
                                    severity="secondary"
                                    variant="text"
                                    :disabled="saving || tiers.length <= 1"
                                    :aria-label="t('emoticon.settings.tierRemove', { price: tier.toLocaleString(locale) })"
                                    @click="removeTier(tier)"
                                />
                            </li>
                        </ul>
                        <div v-if="canSave && !PAID_LOCKED" class="flex flex-wrap items-center gap-2">
                            <label :for="ids.tierInput" class="sr-only">{{ t('emoticon.settings.tierInput') }}</label>
                            <InputText :id="ids.tierInput" v-model="tierDraft" inputmode="numeric" autocomplete="off" :placeholder="t('emoticon.settings.tierPlaceholder')" :invalid="Boolean(tierDraftError)" :disabled="saving" class="w-32 text-right tabular-nums" @keydown.enter.prevent="addTier" />
                            <span class="text-sm text-muted-color">{{ t('emoticon.settings.unitWon') }}</span>
                            <Button type="button" :label="t('emoticon.settings.tierAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="saving || !tierDraft.trim()" @click="addTier" />
                        </div>
                        <p v-if="tierDraftError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ tierDraftError }}</p>
                        <p v-if="tiersError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ tiersError }}</p>
                        <p class="m-0 text-xs text-muted-color">{{ t('emoticon.settings.tiersHelp', { min: UtilEmoticon.PRICE_TIER.min.toLocaleString(locale), max: UtilEmoticon.PRICE_TIER.max.toLocaleString(locale), unit: UtilEmoticon.PRICE_TIER.unit, count: UtilEmoticon.PRICE_TIER.maxCount }) }}</p>
                        <p v-if="saved.priceTiersInUse.length" class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-lock mt-0.5 text-xs" aria-hidden="true" />{{ t('emoticon.settings.tiersInUseNote', { tiers: saved.priceTiersInUse.map((v) => v.toLocaleString(locale)).join(' · ') }) }}</p>
                    </div>

                    <LabeledField :label="t('emoticon.settings.field.feeRatePercent')" :hint="rangeHint('feeRatePercent')" :help="t('emoticon.settings.help.feeRatePercent')" :error="errorOf('feeRatePercent')">
                        <template #default="{ id, describedBy, invalid }">
                            <div class="flex flex-wrap items-center gap-2">
                                <InputText :id="id" v-model="form.feeRatePercent" inputmode="numeric" autocomplete="off" :readonly="!canSave || saving || PAID_LOCKED" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-20 text-right tabular-nums" @update:model-value="serverErrors.feeRatePercent = ''" />
                                <span class="text-sm text-muted-color">%</span>
                                <span v-if="UtilEmoticon.parseInt(form.feeRatePercent) !== saved.defaults.feeRatePercent" class="text-xs text-muted-color">{{ t('emoticon.settings.defaultValue', { value: saved.defaults.feeRatePercent }) }}</span>
                            </div>
                        </template>
                    </LabeledField>
                    <ul v-if="feeExamples.length" class="m-0 -mt-2 flex list-none flex-col gap-1 rounded-lg bg-(--p-content-hover-background) p-0 px-3 py-2 text-xs leading-relaxed tabular-nums">
                        <li v-for="line in feeExamples" :key="line">{{ line }}</li>
                    </ul>

                    <div class="rounded-lg border border-(--line) px-3 py-3">
                        <h3 class="m-0 mb-3 text-[13px] font-semibold">{{ t('emoticon.settings.settlementTitle') }}</h3>
                        <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
                            <LabeledField v-for="field in SETTLEMENT_FIELDS" :key="field" :label="t(`emoticon.settings.field.${field}`)" :hint="rangeHint(field)" :help="t(`emoticon.settings.help.${field}`)" :error="errorOf(field)">
                                <template #default="{ id, describedBy, invalid }">
                                    <div class="flex items-center gap-2">
                                        <span v-if="field === 'settlementDay'" class="text-sm text-muted-color">{{ t('emoticon.settings.unitMonthly') }}</span>
                                        <InputText :id="id" v-model="form[field]" inputmode="numeric" autocomplete="off" :readonly="!canSave || saving || PAID_LOCKED" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" class="w-24 text-right tabular-nums" @update:model-value="serverErrors[field] = ''" />
                                        <span class="text-sm text-muted-color">{{ t(`emoticon.settings.unit.${field}`) }}</span>
                                    </div>
                                    <span v-if="UtilEmoticon.parseInt(form[field]) !== saved.defaults[field]" class="text-xs text-muted-color">{{ t('emoticon.settings.defaultValue', { value: saved.defaults[field].toLocaleString(locale) }) }}</span>
                                </template>
                            </LabeledField>
                        </div>
                        <ul class="m-0 mt-3 flex list-none flex-col gap-1.5 border-t border-(--line) p-0 pt-3 text-xs leading-relaxed text-muted-color">
                            <li class="flex items-start gap-2"><i class="pi pi-calendar mt-0.5 text-xs" aria-hidden="true" /><span>{{ settlementSummary }}</span></li>
                            <li class="flex items-start gap-2"><i class="pi pi-wallet mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settings.settlementNote') }}</span></li>
                            <li class="flex items-start gap-2"><i class="pi pi-percentage mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settings.withholdingNote', { rate: UtilEmoticon.WITHHOLDING_PERCENT }) }}</span></li>
                            <li class="flex items-start gap-2"><i class="pi pi-replay mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settings.refundNote') }}</span></li>
                        </ul>
                    </div>

                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed">
                        <li class="flex items-start gap-2 text-muted-color"><i class="pi pi-gift mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settings.freeRewardSeparate') }}</span></li>
                        <li class="flex items-start gap-2 text-amber-700 dark:text-amber-400"><i class="pi pi-exclamation-triangle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settings.legalNote') }}</span></li>
                    </ul>
                </div>
            </AppGadget>

            <!-- 로보티콘 상품화 계약 — 문서(판·시행일·본문·게시)는 약관 관리의 종류 `ROBOTIKON_CONTRACT` 가 맡는다(2026-11-05 확정). 여기는 요약 + 링크 -->
            <AppGadget :title="t('emoticon.settings.contractSection')" anchor="emoticon-contract" :nav-label="t('emoticon.settings.contractNav')">
                <div class="flex flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.settings.contractLead') }}</p>
                    <dl v-if="saved.contract" class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-3">
                        <div class="rounded-lg border border-(--line) px-3 py-2">
                            <dt class="text-xs text-muted-color">{{ t('emoticon.settings.contractVersion') }}</dt>
                            <dd class="m-0 mt-0.5 text-[13px] font-semibold">{{ t('emoticon.versionNo', { version: saved.contract.version }) }}</dd>
                        </div>
                        <div class="rounded-lg border border-(--line) px-3 py-2">
                            <dt class="text-xs text-muted-color">{{ t('emoticon.settings.contractEffective') }}</dt>
                            <dd class="m-0 mt-0.5 text-[13px] tabular-nums">{{ time.serviceDate(saved.contract.effectiveDate) }}</dd>
                        </div>
                        <div class="rounded-lg border border-(--line) px-3 py-2">
                            <dt class="text-xs text-muted-color">{{ t('emoticon.settings.contractAgreed') }}</dt>
                            <dd class="m-0 mt-0.5 text-[13px] tabular-nums">{{ t('emoticon.countPeople', { count: saved.contract.agreedCount.toLocaleString(locale) }) }}</dd>
                        </div>
                    </dl>
                    <Message v-else severity="warn" :closable="false"><span class="text-[13px]">{{ t('emoticon.settings.contractNone') }}</span></Message>
                    <div>
                        <Button as="router-link" :to="{ path: '/settings/terms', query: { kind: 'robotikon_contract' } }" :label="t('emoticon.settings.contractManage')" icon="pi pi-external-link" size="small" severity="secondary" variant="outlined" />
                    </div>
                    <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ t('emoticon.settings.contractReconsent') }}</p>
                </div>
            </AppGadget>

            <p class="m-0 text-xs text-muted-color">
                {{ saved.updatedAt ? t('emoticon.settings.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('emoticon.settings.neverSaved') }}
            </p>

            <StickySaveBar v-if="canSave" submit revertable :dirty="dirtyCount > 0" :saving="saving" :disabled="invalidCount > 0" :count="dirtyCount" :error-count="invalidCount" :error="formError" @revert="revert" />
            <p v-else class="text-xs text-muted-color">{{ t('emoticon.settings.readOnly') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonSettingsSaveRequest, EmoticonSettingsView } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages, { type EmoticonSettingField } from '~/utils/util-emoticon-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('emoticon.settingsTitle') });

const api = useEmoticonAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const ids = { paid: useId(), tiers: useId(), tierInput: useId() };

/** 저장 권한(후보 — `util-permission-catalog.ts`) */
const canSave = computed(() => can('emoticon.settings'));

const FIELDS: readonly EmoticonSettingField[] = ['rewardPerAcquire', 'rewardDailyCap', 'itemMin', 'itemMax', 'pendingLimit', 'feeRatePercent', 'settlementDay', 'payoutMinimum', 'refundDays'];
const REWARD_FIELDS = ['rewardPerAcquire', 'rewardDailyCap'] as const;
const COUNT_FIELDS = ['itemMin', 'itemMax'] as const;
const SETTLEMENT_FIELDS = ['settlementDay', 'payoutMinimum', 'refundDays'] as const;
const PAID_FIELDS: readonly EmoticonSettingField[] = ['feeRatePercent', ...SETTLEMENT_FIELDS];
/**
 * 유료 판매·가격 단계·수수료·정산·환불 칸 잠금 — S1 서버는 이 칸들을 저장하지 않아 읽기 전용으로 두었다. S2 서버가 모든 칸을 저장해 false(다음에 손댈 때 이 상수와 쓰는 곳의 조건·`settings.paidLocked` 문구를 지워도 된다)
 */
const PAID_LOCKED = false;


// ── 가격 단계 — 화면 목록(오름차순), 저장 때 한 번에 보낸다 ─
const tiers = ref<number[]>([]);
const tierDraft = ref('');
const tierDraftError = ref('');
/** 서버가 거절한 가격 단계(`fields.priceTiers`) */
const tiersServerError = ref('');

function sameTiers(a: readonly number[], b: readonly number[]): boolean {
    return a.length === b.length && a.every((v, i) => v === b[i]);
}
const tiersDirty = computed(() => saved.value !== null && !sameTiers(tiers.value, saved.value.priceTiers));
const tiersError = computed(() => (tiers.value.length === 0 ? t('emoticon.settings.tiersEmpty') : tiersServerError.value));

function addTier(): void {
    const value = UtilEmoticon.parseInt(tierDraft.value);
    const problem = UtilEmoticon.tierProblem(value, tiers.value);
    if (problem) {
        const T = UtilEmoticon.PRICE_TIER;
        tierDraftError.value = t(`emoticon.settings.tierProblem.${problem}`, { min: T.min.toLocaleString(locale.value), max: T.max.toLocaleString(locale.value), unit: T.unit, count: T.maxCount });
        return;
    }
    tiers.value = [...tiers.value, value!].sort((a, b) => a - b);
    tierDraft.value = '';
    tierDraftError.value = '';
    tiersServerError.value = '';
}

function removeTier(value: number): void {
    // 판매 중 팩이 쓰는 단계는 단추가 없다(자물쇠) — 서버도 400 으로 막는다
    if (saved.value?.priceTiersInUse.includes(value)) return;
    tiers.value = tiers.value.filter((v) => v !== value);
    tiersServerError.value = '';
}
/** 「인정된 받기」 기준(추천 보너스의 「인정된 추천」과 같다 — 다계정 받기 막기) */
const COUNTED_RULES = ['notSelf', 'verified', 'grade', 'once'] as const;
const REWARD_NOTES = ['official', 'kept', 'ledger'] as const;

function blankRecord(): Record<EmoticonSettingField, string> {
    return Object.fromEntries(Object.keys(UtilEmoticon.SETTING_RANGES).map((k) => [k, ''])) as Record<EmoticonSettingField, string>;
}

const saved = ref<EmoticonSettingsView | null>(null);
const form = reactive<Record<EmoticonSettingField, string>>(blankRecord());
const serverErrors = reactive<Record<EmoticonSettingField, string>>(blankRecord());
const paidSalesEnabled = ref(false);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');

function rangeHint(field: EmoticonSettingField): string {
    const range = UtilEmoticon.SETTING_RANGES[field];
    return t('emoticon.settings.range', { min: range.min.toLocaleString(locale.value), max: range.max.toLocaleString(locale.value) });
}

function valueOf(name: EmoticonSettingField): number | null {
    return UtilEmoticon.parseInt(form[name]);
}

/** 칸 하나의 문제 — 화면 검사(범위·정수) → 칸끼리 규칙(최소 ≤ 최대 · 한 번 보상 ≤ 하루 상한 · 가격은 단위의 배수) → 서버 문구 */
function errorOf(field: EmoticonSettingField): string {
    const own = UtilEmoticonMessages.settingProblem(t, field, form[field], locale.value);
    if (own) return own;
    const v = valueOf(field)!;
    if (field === 'itemMax' && valueOf('itemMin') !== null && v < valueOf('itemMin')!) return t('emoticon.settings.maxBelowMin');
    if (field === 'rewardDailyCap' && valueOf('rewardPerAcquire') !== null && v < valueOf('rewardPerAcquire')!) return t('emoticon.settings.capBelowReward');
    return serverErrors[field];
}

function isDirty(field: EmoticonSettingField): boolean {
    return saved.value !== null && valueOf(field) !== saved.value[field];
}

function changedIn(fields: readonly EmoticonSettingField[]): number {
    return fields.filter(isDirty).length;
}
function invalidIn(fields: readonly EmoticonSettingField[]): number {
    return fields.filter((f) => errorOf(f)).length;
}

/** 운영 주체가 유료 판매를 받을 수 없는 단계(서버 `paidSalesAllowed` — 개인사업자 이상 + 통신판매업 신고번호, terms-operator-contract) — 꺼진 스위치는 켤 수 없다(켜진 것은 끌 수 있다). 막는 것은 서버(400 fields.paidSalesEnabled) */
const paidStageLocked = computed(() => saved.value !== null && !saved.value.paidSalesAllowed);
const paidDirty = computed(() => saved.value !== null && paidSalesEnabled.value !== saved.value.paidSalesEnabled);
const dirtyCount = computed(() => changedIn(FIELDS) + (paidDirty.value ? 1 : 0) + (tiersDirty.value ? 1 : 0));
const invalidCount = computed(() => invalidIn(FIELDS) + (tiersError.value ? 1 : 0));

/** 수수료 예 — 가격 단계마다 한 건: 부가세 제외 → 작가 몫(개인은 원천징수 뒤). 계산은 `UtilEmoticon.creatorShare` 한 곳 */
const feeExamples = computed(() => {
    const rate = valueOf('feeRatePercent');
    if (rate === null || errorOf('feeRatePercent')) return [];
    return tiers.value.map((price) => {
        const { baseAmount, shareAmount } = UtilEmoticon.creatorShare(price, rate);
        const afterTax = shareAmount - UtilEmoticon.withholding(shareAmount);
        return t('emoticon.settings.feeExample', { price: price.toLocaleString(locale.value), base: baseAmount.toLocaleString(locale.value), share: shareAmount.toLocaleString(locale.value), afterTax: afterTax.toLocaleString(locale.value) });
    });
});

/** 정산 규칙 한 줄 — 지금 칸 값으로 */
const settlementSummary = computed(() =>
    t('emoticon.settings.settlementSummary', {
        day: form.settlementDay || '—',
        refundDays: form.refundDays || '—',
        minimum: valueOf('payoutMinimum')?.toLocaleString(locale.value) ?? '—',
    }),
);

/** 고정 규격 표 */
const fixedRows = computed(() => {
    const S = UtilEmoticon.SPEC;
    return [
        { key: 'size', label: t('emoticon.settings.fixed.size'), value: t('emoticon.settings.fixedValue.size', { size: S.size, art: S.artSize, pad: S.padding }) },
        { key: 'format', label: t('emoticon.settings.fixed.format'), value: t('emoticon.settings.fixedValue.format') },
        { key: 'frames', label: t('emoticon.settings.fixed.frames'), value: t('emoticon.settings.fixedValue.frames', { frames: S.maxFrames }) },
        { key: 'bytes', label: t('emoticon.settings.fixed.bytes'), value: t('emoticon.settings.fixedValue.bytes', { max: UtilEmoticon.fileSize(S.maxBytes), recommended: UtilEmoticon.fileSize(S.recommendedBytes) }) },
        { key: 'cover', label: t('emoticon.settings.fixed.cover'), value: t('emoticon.settings.fixedValue.cover', { size: S.size, max: UtilEmoticon.fileSize(S.coverMaxBytes) }) },
        { key: 'name', label: t('emoticon.settings.fixed.name'), value: t('emoticon.settings.fixedValue.name', { min: S.nameMin, max: S.nameMax }) },
        { key: 'alt', label: t('emoticon.settings.fixed.alt'), value: t('emoticon.settings.fixedValue.alt', { min: S.altMin, max: S.altMax }) },
        { key: 'tags', label: t('emoticon.settings.fixed.tags'), value: t('emoticon.settings.fixedValue.tags', { max: S.tagsMax }) },
        { key: 'display', label: t('emoticon.settings.fixed.display'), value: t('emoticon.settings.fixedValue.display', { display: S.displaySize, picker: S.pickerSize }) },
    ];
});

onMounted(() => void load());

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.settings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: EmoticonSettingsView): void {
    saved.value = view;
    for (const field of FIELDS) {
        form[field] = String(view[field]);
        serverErrors[field] = '';
    }
    paidSalesEnabled.value = view.paidSalesEnabled;
    tiers.value = [...view.priceTiers].sort((a, b) => a - b);
    tierDraft.value = '';
    tierDraftError.value = '';
    tiersServerError.value = '';
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (saving.value || !canSave.value || dirtyCount.value === 0 || invalidCount.value > 0) return;
    const numbers = Object.fromEntries(FIELDS.map((f) => [f, valueOf(f)!])) as Record<EmoticonSettingField, number>;
    const body: EmoticonSettingsSaveRequest = { ...numbers, paidSalesEnabled: paidSalesEnabled.value, priceTiers: [...tiers.value] };
    saving.value = true;
    formError.value = '';
    try {
        applySaved(await api.saveSettings(body));
        toast.success(t('emoticon.settings.saved'));
    } catch (e) {
        const failure = toApiError(e);
        const fields = failure.code === 'VALIDATION' ? (failure.fields ?? {}) : {};
        let attached = false;
        for (const field of FIELDS) {
            if (fields[field]) {
                serverErrors[field] = fields[field];
                attached = true;
            }
        }
        if (fields.priceTiers) {
            tiersServerError.value = fields.priceTiers;
            attached = true;
        }
        // 유료 판매 켜기 칸은 입력칸 아래 자리가 없어 폼 위 한 줄로 모은다(forms.md — 칸에 붙지 않는 것)
        if (fields.paidSalesEnabled) {
            formError.value = fields.paidSalesEnabled;
            return;
        }
        formError.value = attached ? t('emoticon.saveFieldsRejected') : `${t('emoticon.saveFailed')} — ${failure.message}`;
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (dirtyCount.value === 0) return true;
    return confirm({ title: t('emoticon.leaveTitle'), message: t('emoticon.leaveMessage'), confirmLabel: t('emoticon.leaveConfirm'), cancelLabel: t('emoticon.leaveCancel'), tone: 'danger' });
});
</script>

