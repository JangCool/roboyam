<template>
    <div class="mx-auto flex max-w-3xl flex-col gap-4">
        <NuxtLink :to="UtilEmoticon.STUDIO_PATH" class="inline-flex w-fit items-center gap-1 rounded text-sm text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="emoticon-edit-back">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ t('emoticon.studioTitle') }}
        </NuxtLink>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('emoticon.loading')">
            <div class="h-48 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-72 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('emoticon.notFoundTitle')" :description="loadError">
            <Button as="router-link" :to="UtilEmoticon.STUDIO_PATH" :label="t('emoticon.studioTitle')" />
        </ErrorState>
        <ErrorState v-else-if="status === 'error' || !rules" :title="t('emoticon.loadFailed')" :description="loadError" retry @retry="load" />

        <form v-else :key="formKey" class="flex flex-col gap-4" novalidate data-testid="emoticon-edit-form" @submit.prevent="submit">
            <div class="flex flex-wrap items-center gap-2">
                <h1 class="m-0 text-lg font-bold">{{ isNew ? t('emoticon.newPack') : t('emoticon.editPack') }}</h1>
                <EmoticonStatusTag v-if="pack" :status="pack.status" />
                <PointShopTag v-if="pack?.revision?.status === 'PENDING'" tone="warn">{{ t('emoticon.revisionPending') }}</PointShopTag>
            </div>

            <!-- 상태 안내 -->
            <div v-if="notice" role="status" class="rounded-lg px-3 py-2.5 text-sm leading-relaxed" :class="notice.tone === 'danger' ? 'bg-red-50 text-red-800 dark:bg-red-950/40 dark:text-red-200' : 'bg-amber-50 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200'" data-testid="emoticon-edit-notice">
                <p class="m-0 font-semibold">{{ notice.title }}</p>
                <p v-if="notice.text" class="m-0 mt-0.5">{{ notice.text }}</p>
            </div>

            <!-- 팩 정보 -->
            <section class="flex flex-col gap-4 panel-card p-4" :aria-labelledby="`${uid}-info`">
                <h2 :id="`${uid}-info`" class="m-0 text-sm font-semibold">{{ t('emoticon.packInfo') }}</h2>
                <LabeledField :label="t('emoticon.nameLabel')" :hint="t('emoticon.nameHint', { min: rules.nameMin, max: rules.nameMax })" :error="nameError">
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <InputText :id="fieldId" v-model="form.name" :maxlength="rules.nameMax" :invalid="invalid" :aria-describedby="describedBy" :readonly="locked" fluid data-testid="emoticon-edit-name" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('emoticon.descriptionLabel')" :hint="`${form.description.length} / ${rules.descriptionMax}`" :error="fieldErrors.description ?? ''">
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <Textarea :id="fieldId" v-model="form.description" :maxlength="rules.descriptionMax" rows="3" auto-resize :invalid="invalid" :aria-describedby="describedBy" :readonly="locked" fluid />
                    </template>
                </LabeledField>
                <LabeledField :label="t('emoticon.tagsInputLabel')" :hint="t('emoticon.tagsHint', { max: rules.maxTags })" :help="tagsPreview" :error="tagsError">
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <InputText :id="fieldId" v-model="form.tagsText" :placeholder="t('emoticon.tagsPlaceholder')" :invalid="invalid" :aria-describedby="describedBy" :readonly="locked" fluid />
                    </template>
                </LabeledField>

                <!-- 대표 이미지 -->
                <div class="flex flex-col gap-1.5">
                    <span :id="`${uid}-cover`" class="text-sm font-medium">{{ t('emoticon.coverLabel') }}
                        <small class="ml-1 text-xs font-normal text-muted-color">{{ t('emoticon.coverHint', { size: rules.imageSize, max: UtilEmoticonSpec.bytes(rules.coverMaxBytes) }) }}</small>
                    </span>
                    <div class="flex flex-wrap items-center gap-3">
                        <EmoticonPackCover :src="cover.previewUrl" size="lg" :alt="t('emoticon.coverLabel')" />
                        <div class="flex flex-col gap-1.5">
                            <Button type="button" :label="cover.previewUrl ? t('emoticon.coverChange') : t('emoticon.coverPick')" icon="pi pi-upload" size="small" severity="secondary" variant="outlined" :loading="cover.state === 'uploading'" :disabled="locked" :aria-describedby="`${uid}-cover`" @click="coverInput?.click()" />
                            <input ref="coverInput" type="file" class="sr-only" :accept="UtilEmoticonSpec.COVER_ACCEPT" tabindex="-1" aria-hidden="true" @change="onCoverPicked" />
                        </div>
                    </div>
                    <p v-for="(line, i) in cover.messages" :key="i" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ line }}</p>
                    <p v-if="fieldErrors.coverImageId" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.coverImageId }}</p>
                </div>

                <!--
                    판매 — 카카오 방식: 작가는 「무료 / 유료 판매 희망」만 고르고 가격은 운영자가 심사 때 가격 단계에서 정한다.
                    유료 희망이면 정산 정보 + 상품화 계약 동의가 있어야 심사 요청이 된다. 승인(판매 중·판매 중단)된 팩은 정해진 가격을 읽기만 하고 「가격 변경 요청 메모」만 남긴다
                -->
                <div v-if="priceFixed" class="flex flex-col gap-1.5" data-testid="emoticon-edit-price-fixed">
                    <label :for="`${uid}-price`" class="text-sm font-medium">{{ t('emoticon.priceLabel') }}</label>
                    <div class="flex flex-wrap items-center gap-2">
                        <InputText :id="`${uid}-price`" :model-value="UtilEmoticonMessages.price(t, pack?.price ?? 0)" readonly class="w-32" />
                        <span class="text-xs text-muted-color">{{ (pack?.price ?? 0) > 0 ? t('emoticon.priceSetByOperator') : t('emoticon.priceFreeFixed') }}</span>
                    </div>
                    <LabeledField v-if="(pack?.price ?? 0) > 0" :label="t('emoticon.priceRequestMemo')" :hint="t('emoticon.optional')" :help="t('emoticon.priceRequestMemoHelp')" :error="fieldErrors.priceRequestMemo ?? ''">
                        <template #default="{ id: fieldId, describedBy, invalid }">
                            <Textarea :id="fieldId" v-model="form.priceRequestMemo" rows="2" auto-resize maxlength="200" :invalid="invalid" :aria-describedby="describedBy" :readonly="locked" fluid />
                        </template>
                    </LabeledField>
                    <p v-if="paidRevision" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.paidRevisionNote') }}</p>
                </div>
                <fieldset v-else class="m-0 flex flex-col gap-1.5 border-0 p-0" :aria-describedby="`${uid}-price-help`">
                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('emoticon.saleWishLabel') }}</legend>
                    <div class="flex flex-wrap items-center gap-4">
                        <div class="flex items-center gap-2">
                            <RadioButton v-model="form.saleWish" :input-id="`${uid}-free`" value="FREE" :disabled="locked" />
                            <label :for="`${uid}-free`" class="text-sm">{{ t('emoticon.free') }}</label>
                        </div>
                        <div class="flex items-center gap-2">
                            <!-- 유료 판매가 꺼져 있으면 「유료」를 새로 고를 수 없다(이미 고른 임시 저장은 무료로만 돌릴 수 있다 — 서버 `400 fields.saleWish`) -->
                            <RadioButton v-model="form.saleWish" :input-id="`${uid}-paid`" value="PAID" :disabled="locked || !rules.paidSalesEnabled || underage" />
                            <label :for="`${uid}-paid`" class="text-sm" :class="{ 'text-muted-color': !rules.paidSalesEnabled || underage }">{{ t('emoticon.saleWishPaid') }}</label>
                        </div>
                    </div>
                    <p v-if="!rules.paidSalesEnabled" class="m-0 inline-flex items-start gap-1.5 text-xs leading-relaxed text-amber-700 dark:text-amber-400" data-testid="emoticon-edit-paid-off">
                        <i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('emoticon.paidSalesOff') }}
                    </p>
                    <!-- 만 19세 미만 작가(후속 결정 3) — 유료 판매 희망을 고를 수 없다(무료 팩은 된다) -->
                    <p v-else-if="underage" class="m-0 inline-flex items-start gap-1.5 text-xs leading-relaxed text-amber-700 dark:text-amber-400" data-testid="emoticon-edit-underage">
                        <i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" /><span>{{ underageText }} {{ UtilEmoticonMessages.underageRelease(t, underageFrom) }}</span>
                    </p>
                    <p :id="`${uid}-price-help`" class="m-0 text-xs leading-relaxed text-muted-color">
                        {{ form.saleWish === 'PAID' ? t('emoticon.priceHelpPaid', { tiers: UtilEmoticonMessages.tiers(t, rules.priceTiers), rate: UtilEmoticonMessages.percent(rules.feeRate) }) : t('emoticon.priceHelpFree') }}
                    </p>
                    <p v-if="saleWishError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ saleWishError }}</p>
                </fieldset>

                <!--
                    유료 조건 — 새 유료 희망(유료 판매가 켜져 있을 때) · 이미 유료로 나온 팩의 새 판(서버 「S2 구현 결과」 결정 1). 정산 정보 + 지금 판 상품화 계약 동의가 있어야 심사 요청이 된다(임시 저장은 된다)
                -->
                <div v-if="paidCondition" class="flex flex-col gap-1.5" data-testid="emoticon-edit-paid-condition">
                    <!-- 만 19세 미만 작가 — 유료 신청(새 유료 희망·유료 팩의 새 판)을 막는다. 정산 정보도 등록할 수 없어 등록 단추를 보이지 않는다 -->
                    <div v-if="underage" role="alert" class="flex flex-col gap-0.5 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="emoticon-edit-underage-block">
                        <span>{{ underageText }}</span>
                        <span class="text-xs">{{ UtilEmoticonMessages.underageRelease(t, underageFrom) }}</span>
                    </div>
                    <!-- 정산 정보 — 없으면 심사 요청을 막는다 -->
                    <div v-else-if="needsPayout" role="alert" class="flex flex-wrap items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="emoticon-edit-payout-needed">
                        <span class="min-w-0 flex-1">{{ payoutNeededText }}</span>
                        <Button as="router-link" :to="UtilEmoticon.PAYOUT_PATH" :label="t('emoticon.payoutRegister')" size="small" severity="warn" variant="outlined" />
                    </div>
                    <p v-else class="m-0 inline-flex items-center gap-1.5 text-xs text-green-700 dark:text-green-400"><i class="pi pi-check-circle text-[0.6875rem]" aria-hidden="true" />{{ t('emoticon.payoutReady') }}</p>

                    <!-- 로보티콘 상품화 계약 — 요약 + 전문(공개 약관 화면 — 새 창) + 판 + 동의. 게시된 계약이 없으면 유료로 낼 수 없다 -->
                    <div v-if="contract" class="mt-1 flex flex-col gap-2 rounded-lg border border-surface p-3" data-testid="emoticon-edit-contract">
                        <div class="flex flex-wrap items-center justify-between gap-2">
                            <p class="m-0 text-sm font-semibold">{{ contract.title }}</p>
                            <span class="text-xs text-muted-color">{{ t('emoticon.contractVersion', { version: UtilEmoticon.versionLabel(contract.version), date: contract.effectiveDate }) }}</span>
                        </div>
                        <ul v-if="contract.summary.length" class="m-0 flex flex-col gap-1 pl-5 text-xs leading-relaxed">
                            <li v-for="(line, i) in contract.summary" :key="i">{{ line }}</li>
                        </ul>
                        <NuxtLink
                            :to="UtilTerms.versionTo('ROBOTIKON_CONTRACT', contract.version)"
                            target="_blank"
                            rel="noopener"
                            class="inline-flex w-fit items-center gap-1.5 rounded text-sm text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                            data-testid="emoticon-edit-contract-full"
                        >
                            <i class="pi pi-file text-xs" aria-hidden="true" />{{ t('emoticon.contractFull') }}<i class="pi pi-external-link text-[0.625rem]" aria-hidden="true" /><span class="sr-only">{{ t('emoticon.newTab') }}</span>
                        </NuxtLink>
                        <p v-if="contractCurrent" class="m-0 inline-flex items-center gap-1.5 text-xs text-green-700 dark:text-green-400">
                            <i class="pi pi-check-circle text-[0.6875rem]" aria-hidden="true" />{{ t('emoticon.contractAgreedAt', { version: UtilEmoticon.versionLabel(contract.version), date: time.full(latestAgreement?.agreedAt) }) }}
                        </p>
                        <div v-else class="flex flex-col gap-0.5">
                            <div class="flex items-start gap-2">
                                <Checkbox v-model="contractAgree" :input-id="`${uid}-contract`" binary :invalid="Boolean(contractError)" :disabled="locked" class="mt-0.5" />
                                <label :for="`${uid}-contract`" class="text-sm leading-relaxed">{{ t('emoticon.contractAgree', { title: contract.title, version: UtilEmoticon.versionLabel(contract.version) }) }}</label>
                            </div>
                            <p v-if="latestAgreement" class="m-0 pl-7 text-xs text-amber-700 dark:text-amber-400">{{ t('emoticon.contractChanged', { old: UtilEmoticon.versionLabel(latestAgreement.version) }) }}</p>
                            <p v-if="contractError" role="alert" class="m-0 pl-7 text-xs text-red-600 dark:text-red-400">{{ contractError }}</p>
                        </div>
                    </div>
                    <p v-else role="alert" class="m-0 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="emoticon-edit-contract-missing">{{ t('emoticon.contractMissing') }}</p>
                </div>
            </section>

            <!-- 이모티콘 올리기 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-items`" data-testid="emoticon-edit-items">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h2 :id="`${uid}-items`" class="m-0 text-sm font-semibold">
                        {{ t('emoticon.itemsTitle') }}
                        <span class="ml-1 font-normal tabular-nums" :class="countError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ rows.length }} / {{ rules.maxCount }}</span>
                    </h2>
                    <Button type="button" :label="t('emoticon.addFiles')" icon="pi pi-images" size="small" :disabled="locked" data-testid="emoticon-edit-add" @click="fileInput?.click()" />
                    <input ref="fileInput" type="file" class="sr-only" multiple :accept="UtilEmoticonSpec.ACCEPT" tabindex="-1" aria-hidden="true" @change="onFilesPicked" />
                </div>
                <p class="m-0 mt-1 text-xs leading-relaxed text-muted-color">
                    {{ t('emoticon.itemsHint', { size: rules.imageSize, frames: rules.maxFrames, max: UtilEmoticonSpec.bytes(rules.maxBytes), min: rules.minCount, maxCount: rules.maxCount }) }}
                    <NuxtLink :to="UtilEmoticon.GUIDE_PATH" class="text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('emoticon.guideTitle') }}</NuxtLink>
                </p>
                <p v-if="countError" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ countError }}</p>

                <p v-if="rows.length === 0" class="m-0 mt-3 rounded-lg border border-dashed border-surface px-3 py-6 text-center text-sm text-muted-color">{{ t('emoticon.itemsEmpty') }}</p>
                <ol v-else class="m-0 mt-3 flex list-none flex-col gap-2 p-0">
                    <li
                        v-for="(row, i) in rows"
                        :key="row.key"
                        class="flex flex-wrap items-start gap-3 rounded-lg border p-2.5"
                        :class="rowBlocking(row) ? 'border-red-400 dark:border-red-500' : 'border-surface'"
                        data-testid="emoticon-edit-row"
                    >
                        <span class="w-5 pt-1 text-right text-xs tabular-nums text-muted-color">{{ i + 1 }}</span>
                        <span class="grid size-16 shrink-0 place-items-center overflow-hidden rounded-lg bg-surface-100 dark:bg-surface-800">
                            <img v-if="row.previewUrl" :src="row.previewUrl" :alt="row.label" class="size-16 object-contain" />
                        </span>
                        <div class="min-w-0 flex-1">
                            <InputText
                                v-model="row.label"
                                :aria-label="t('emoticon.labelAria', { n: i + 1 })"
                                :placeholder="t('emoticon.labelPlaceholder')"
                                :maxlength="rules.labelMax"
                                :invalid="Boolean(labelError(row, i))"
                                :readonly="locked"
                                size="small"
                                class="w-full max-w-48"
                            />
                            <p class="m-0 mt-1 text-[0.6875rem] text-muted-color">{{ rowMeta(row) }}</p>
                            <p v-if="labelError(row, i)" role="alert" class="m-0 mt-0.5 text-xs text-red-600 dark:text-red-400">{{ labelError(row, i) }}</p>
                            <p v-for="(line, j) in row.messages" :key="j" class="m-0 mt-0.5 text-xs" :class="line.severity === 'error' ? 'text-red-600 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'">{{ line.text }}</p>
                        </div>
                        <div class="flex shrink-0 gap-0.5">
                            <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('emoticon.moveUp')" :disabled="locked || i === 0" @click="move(i, -1)" />
                            <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('emoticon.moveDown')" :disabled="locked || i === rows.length - 1" @click="move(i, 1)" />
                            <Button type="button" icon="pi pi-trash" size="small" severity="secondary" variant="text" rounded :aria-label="t('emoticon.removeItem', { n: i + 1 })" :disabled="locked" @click="removeRow(i)" />
                        </div>
                    </li>
                </ol>
            </section>

            <!-- 동의 셋 + 저장 -->
            <section v-if="!locked" class="flex flex-col gap-3 panel-card p-4" :aria-labelledby="`${uid}-agree`">
                <h2 :id="`${uid}-agree`" class="m-0 text-sm font-semibold">{{ t('emoticon.agreeTitle') }}</h2>
                <div v-for="a in agreements" :key="a.key" class="flex flex-col gap-0.5">
                    <div class="flex items-start gap-2">
                        <Checkbox v-model="agree[a.key]" :input-id="`${uid}-${a.key}`" binary :invalid="Boolean(agreeError(a.key))" class="mt-0.5" />
                        <label :for="`${uid}-${a.key}`" class="text-sm leading-relaxed">{{ a.label }}</label>
                    </div>
                    <p v-if="agreeError(a.key)" role="alert" class="m-0 pl-7 text-xs text-red-600 dark:text-red-400">{{ agreeError(a.key) }}</p>
                </div>

                <Message v-if="formError" severity="error" variant="simple" size="small" role="alert">{{ formError }}</Message>

                <div class="flex flex-wrap items-center justify-end gap-2 border-t border-surface pt-3">
                    <span class="mr-auto text-xs text-muted-color">{{ dirty ? t('emoticon.unsaved') : t('emoticon.noChanges') }}</span>
                    <Button v-if="canDraft" type="button" :label="t('emoticon.saveDraft')" severity="secondary" variant="outlined" :loading="busy === 'draft'" :disabled="busy !== null" data-testid="emoticon-edit-draft" @click="saveDraft" />
                    <Button type="submit" :label="priceFixed ? t('emoticon.submitRevision') : t('emoticon.submitReview')" :loading="busy === 'submit'" :disabled="busy !== null" data-testid="emoticon-edit-submit" />
                </div>
            </section>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonRules } from '~/composables/emoticon/api/useEmoticonApi.types';
import type {
    EmoticonCreatorContract,
    EmoticonPayoutAccount,
    EmoticonSaleWish,
    EmoticonStudioImage,
    EmoticonStudioPack,
    EmoticonStudioSaveRequest,
} from '~/composables/emoticon/api/useEmoticonStudioApi.types';
import type { EmoticonImageInfo, EmoticonSpecProblem } from '~/utils/util-emoticon-spec';
import { ApiError } from '~/composables/common/useApiClient';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';
import UtilEmoticonSpec from '~/utils/util-emoticon-spec';
import UtilTerms from '~/utils/util-terms';

/**
 * 로보티콘 팩 신청·고치기 `/robotikon/studio/new` · `/robotikon/studio/{id}` — 한 화면이 둘 다 맡는다(`id === 'new'` 면 새 팩). 로그인 필요.
 * 이름·설명·태그·대표 이미지·이모티콘 올리기(여러 파일, 각자 이름표, 순서 바꾸기, 지우기) + **화면에서 규격 검사**(형식 GIF/PNG · 200×200 · 용량 · GIF 프레임·무한 반복 —
 * `UtilEmoticonSpec`, 팩 개수 8~40). 문제는 칸·이모티콘마다 보인다. 동의 셋 · 임시 저장 · 심사 요청. 판정은 서버다(같은 규칙을 다시 본다).
 * 판매 중·판매 중단 팩을 고치면 새 판 심사(「심사 동안 지금 판이 계속 보여요」 — 판매 중단 팩은 승인되면 다시 판매, 반려면 판매 중단 그대로), 심사 중(새 팩·새 판)이면 읽기만.
 * 판매(카카오 방식 — 계약 「가격은 운영자가 정한다」): 「무료 / 유료 판매 희망」만 고른다(금액 입력 없음 — 운영자가 심사 때 가격 단계 `rules.priceTiers` 에서 정한다).
 * 유료 판매가 꺼져 있으면(`rules.paidSalesEnabled=false`, 기본) 「유료」를 고를 수 없고 안내를 띄운다(서버 `400 fields.saleWish`).
 * 유료 조건(S2 실제 API): 새 유료 희망 · **이미 유료로 나온 팩의 새 판** — 정산 정보(`GET …/payout-account`) + 지금 판 로보티콘 상품화 계약 동의(`GET …/contract` — 요약·판,
 * 전문은 공개 약관 화면 `/robotikon/contract` 새 창, 지금 판에 이미 동의했으면 다시 묻지 않는다, 게시된 계약이 없으면 null 이라 유료로 낼 수 없다)가 있어야 심사 요청이 된다(임시 저장은 된다).
 * 서버 차례: 칸 400(`fields.saleWish`) → 만 19세 미만 `403 IDENTITY_UNDERAGE`(+ `adultFrom`, 후속 결정 3) → `409 EMOTICON_PAYOUT_REQUIRED` → `409 EMOTICON_CONTRACT_REQUIRED`(계약을 다시 받아 동의 칸을 다시 보인다) → `409 EMOTICON_PENDING_LIMIT`.
 * 만 19세 미만 작가(본인인증 상태 `adult: false`)는 「유료 판매 희망」을 고를 수 없고 안내(+ 19세 생일)를 보인다. 무료 팩은 그대로 낸다.
 * 승인된 팩은 정해진 가격을 읽기만 하고 「가격 변경 요청 메모」(선택)만 남긴다 — 바꾸는 것은 운영자다.
 * 그림은 고르는 즉시 검사하고, 맞으면 바로 올린다(`POST …/images` — 서버가 저장한 그림 주소). 맞지 않는 그림은 목록에 남아 이유를 보이고 저장·신청을 막는다(지우면 풀린다).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();

const uid = useId();
const route = useRoute();
const router = useRouter();
const api = useEmoticonStudioApi();
const publicApi = useEmoticonApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const identity = useIdentity();

const id = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => id.value === 'new');
useSeoMeta({ title: () => (isNew.value ? t('emoticon.newPack') : t('emoticon.editPack')), robots: 'noindex' });

/* ── 한 줄(이모티콘 하나) ── */
type RowState = 'checking' | 'uploading' | 'ready' | 'invalid' | 'failed';
interface Row {
    key: number;
    label: string;
    state: RowState;
    image: EmoticonStudioImage | null;
    previewUrl: string;
    info: EmoticonImageInfo | null;
    size: number;
    /** 규격 문제·올리기 실패 한 줄들 */
    messages: { text: string; severity: 'error' | 'warn' }[];
}

let rowSeq = 0;
/** 이 화면이 만든 blob 주소 — 떠날 때 풀어 준다 */
const blobUrls: string[] = [];

const pack = ref<EmoticonStudioPack | null>(null);
const rules = ref<EmoticonRules | null>(null);
const status = ref<'loading' | 'ready' | 'error' | 'notfound'>('loading');
const loadError = ref('');
const formKey = ref(0);

const form = reactive<{ name: string; description: string; tagsText: string; saleWish: EmoticonSaleWish; priceRequestMemo: string }>({
    name: '',
    description: '',
    tagsText: '',
    saleWish: 'FREE',
    priceRequestMemo: '',
});
/** 로보티콘 상품화 계약(유료 조건 — 게시본이 없으면 null) · 이번 신청의 동의 */
const contract = ref<EmoticonCreatorContract | null>(null);
const contractAgree = ref(false);
/** 정산 정보(유료 팩 심사 요청 조건) */
const payout = ref<EmoticonPayoutAccount | null>(null);
const cover = reactive<{ state: 'idle' | 'uploading'; image: EmoticonStudioImage | null; previewUrl: string | null; messages: string[] }>({ state: 'idle', image: null, previewUrl: null, messages: [] });
const rows = ref<Row[]>([]);
const agree = reactive<Record<'agreeOwnership' | 'agreeLicense' | 'agreePolicy', boolean>>({ agreeOwnership: false, agreeLicense: false, agreePolicy: false });
const savedSnapshot = ref('');

/** 서버 칸 오류(`400 VALIDATION fields`) · 폼 위 한 줄 */
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const tried = ref(false);
const busy = ref<'draft' | 'submit' | null>(null);

/** 심사 중(새 팩·새 판) — 읽기만 */
const locked = computed(() => Boolean(pack.value && !UtilEmoticon.editable(pack.value)));
const canDraft = computed(() => UtilEmoticon.draftable(pack.value?.status ?? null));

const notice = computed<{ title: string; text: string; tone: 'warn' | 'danger' } | null>(() => {
    const p = pack.value;
    if (!p) return null;
    if (p.status === 'PENDING') return { title: t('emoticon.noticePendingTitle'), text: t('emoticon.noticePendingText'), tone: 'warn' };
    if (p.revision?.status === 'PENDING') return { title: t('emoticon.revisionPending'), text: t('emoticon.revisionPendingText'), tone: 'warn' };
    if (p.status === 'APPROVED') return { title: t('emoticon.noticeApprovedTitle'), text: t('emoticon.noticeApprovedText'), tone: 'warn' };
    if (p.status === 'REJECTED') return { title: t('emoticon.noticeRejectedTitle'), text: p.rejectReason ?? '', tone: 'danger' };
    if (p.status === 'SUSPENDED') return { title: t('emoticon.noticeSuspendedTitle'), text: p.suspendReason ?? '', tone: 'danger' };
    return null;
});

function fillFrom(p: EmoticonStudioPack | null): void {
    form.name = p?.name ?? '';
    form.description = p?.description ?? '';
    form.tagsText = (p?.tags ?? []).join(', ');
    form.saleWish = p?.saleWish ?? 'FREE';
    form.priceRequestMemo = p?.priceRequestMemo ?? '';
    contractAgree.value = false;
    cover.state = 'idle';
    cover.image = p?.cover ?? null;
    cover.previewUrl = p?.cover?.url ?? null;
    cover.messages = [];
    rows.value = (p?.emoticons ?? []).map((e) => ({
        key: ++rowSeq,
        label: e.label,
        state: 'ready',
        image: e,
        previewUrl: e.url,
        info: { format: e.format, width: e.width, height: e.height, frames: e.frames, loopForever: true },
        size: e.bytes,
        messages: [],
    }));
    agree.agreeOwnership = false;
    agree.agreeLicense = false;
    agree.agreePolicy = false;
    fieldErrors.value = {};
    formError.value = '';
    tried.value = false;
    savedSnapshot.value = snapshot.value;
    formKey.value += 1;
}

/** 방금 새 팩을 저장하고 주소를 바꿨다 — 다시 받지 않는다(올린 그림·맞지 않는 줄을 그대로 둔다) */
let skipNextLoad = false;

async function load(): Promise<void> {
    if (skipNextLoad) {
        skipNextLoad = false;
        return;
    }
    status.value = 'loading';
    loadError.value = '';
    try {
        // 본인인증 상태는 만 19세 미만 작가의 유료 희망을 미리 막는 데만 쓴다(받지 못해도 화면은 연다 — 판정은 서버)
        const [r, p, account, terms] = await Promise.all([
            publicApi.rules(),
            isNew.value ? Promise.resolve(null) : api.pack(id.value),
            api.payoutAccount(),
            api.contract(),
            identity.ensureLoaded(),
        ]);
        rules.value = r;
        pack.value = p;
        payout.value = account;
        contract.value = terms;
        fillFrom(p);
        status.value = 'ready';
    } catch (e) {
        loadError.value = UtilEmoticonMessages.error(t, e);
        status.value = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error';
    }
}

/* ── 고친 것 ── */
const tags = computed(() => UtilEmoticonSpec.parseTags(form.tagsText));
/* ── 판매 희망·가격(카카오 방식 — 가격은 운영자가 정한다) ── */
/** 승인된(판매 중·판매 중단) 팩 — 판매 희망을 바꿀 수 없고 정해진 가격을 읽기만 한다 */
const priceFixed = computed(() => pack.value?.status === 'APPROVED' || pack.value?.status === 'SUSPENDED');
/** 이번 신청이 새로 유료 판매를 희망한다 */
const paidWish = computed(() => !priceFixed.value && form.saleWish === 'PAID');
/** 유료 판매가 꺼져 있는데 유료를 골라 두었다(임시 저장에서 불러온 값) — 심사 요청을 막는다(서버 `400 fields.saleWish`) */
const paidOff = computed(() => paidWish.value && rules.value?.paidSalesEnabled === false);
/** 이미 유료로 나온 팩(판매 중·판매 중단)의 새 판 — 서버가 새 유료 희망과 같은 조건을 본다(「S2 구현 결과」 결정 1) */
const paidRevision = computed(() => priceFixed.value && (pack.value?.price ?? 0) > 0);
/** 유료 조건(정산 정보 + 지금 판 상품화 계약 동의)을 보는 신청 */
const paidCondition = computed(() => (paidWish.value && !paidOff.value) || paidRevision.value);
/** 서버가 심사 요청을 `403 IDENTITY_UNDERAGE` 로 막았다 — 그 문구와 최상위 `adultFrom`(화면이 아는 인증 상태가 낡았어도 서버를 따른다) */
const underageError = ref<{ message: string; adultFrom: string | null } | null>(null);
/**
 * 만 19세 미만 작가(후속 결정 3) — 본인인증 상태의 `adult: false` 또는 서버 거절. 유료 판매 희망·유료 팩의 새 판을 낼 수 없다(무료 팩은 된다).
 * 본인인증 전이면 여기서 막지 않는다(서버도 지금처럼 `409 EMOTICON_PAYOUT_REQUIRED` — 정산 정보 등록 화면이 본인인증으로 이끈다)
 */
const underage = computed(() => underageError.value !== null || (identity.status.value?.verified === true && identity.status.value.adult === false));
/** 안내 첫 줄 — 서버 문구가 있으면 그것, 아니면 화면 문구 */
const underageText = computed(() => underageError.value?.message || t('emoticon.underageCreator'));
const underageFrom = computed(() => underageError.value?.adultFrom ?? identity.status.value?.adultFrom ?? null);
/** 유료 조건인데 만 19세 미만이다 — 심사 요청을 막는다 */
const underageBlocked = computed(() => paidCondition.value && !locked.value && underage.value);
/** 서버가 심사 요청을 `409 EMOTICON_PAYOUT_REQUIRED` 로 막았다 — 화면이 받은 정산 정보와 달라도 서버를 따른다 */
const payoutRequired = ref(false);
/** 유료 조건인데 정산 정보가 없다(만 19세 미만이면 그 안내가 먼저다) */
const needsPayout = computed(() => paidCondition.value && !locked.value && !underage.value && ((payout.value !== null && !payout.value.registered) || payoutRequired.value));
const payoutNeededText = computed(() => (paidRevision.value ? t('emoticon.payoutNeededRevision') : t('emoticon.payoutNeeded')));
/** 내 최근 동의(동의 기록은 추가만 — 가장 늦은 것) */
const latestAgreement = computed(() => (contract.value ? UtilEmoticon.latestAgreement(contract.value.agreements) : null));
/** 지금 판의 상품화 계약에 이미 동의했다 */
const contractCurrent = computed(() => Boolean(contract.value && latestAgreement.value?.version === contract.value.version));
/** 유료 조건인데 지금 판 계약 동의가 없다(게시된 계약이 없으면 늘 — 동의할 판이 없다) */
const needsContract = computed(() => paidCondition.value && !contractCurrent.value && !(contract.value && contractAgree.value));
const contractRequiredText = computed(() => (paidRevision.value ? t('emoticon.contractRequiredRevision') : t('emoticon.contractRequired')));
const contractError = computed(() => fieldErrors.value.agreeContractVersion || (tried.value && needsContract.value ? contractRequiredText.value : ''));
/** 판매 희망 칸 오류 — 서버(`fields.saleWish`) 또는 유료 판매가 꺼져 있는데 유료를 고름 */
const saleWishError = computed(() => fieldErrors.value.saleWish || (tried.value && paidOff.value ? t('emoticon.paidSalesOff') : ''));

const snapshot = computed(() =>
    JSON.stringify({ n: form.name, d: form.description, w: form.saleWish, m: form.priceRequestMemo, t: tags.value, c: cover.image?.imageId ?? null, r: rows.value.map((r) => [r.image?.imageId ?? r.key, r.label, r.state]) }),
);
const dirty = computed(() => snapshot.value !== savedSnapshot.value);

onBeforeRouteLeave(async () => {
    if (!dirty.value || busy.value || locked.value) return true;
    return confirm({ title: t('emoticon.leaveTitle'), message: t('emoticon.leaveText'), confirmLabel: t('emoticon.leaveConfirm'), tone: 'danger' });
});
onBeforeUnmount(() => {
    for (const url of blobUrls) URL.revokeObjectURL(url);
});

/* ── 화면 검사 ── */
const nameError = computed(() => {
    if (fieldErrors.value.name) return fieldErrors.value.name;
    if (!rules.value || !tried.value) return '';
    return UtilEmoticonMessages.name(t, UtilEmoticonSpec.nameProblem(form.name, rules.value), rules.value);
});
const tagsError = computed(() => fieldErrors.value.tags || (rules.value ? UtilEmoticonMessages.tags(t, UtilEmoticonSpec.tagsProblem(tags.value, rules.value), rules.value) : ''));
const tagsPreview = computed(() => (tags.value.length ? tags.value.map((x) => `#${x}`).join(' ') : ''));
const countError = computed(() => {
    if (fieldErrors.value.emoticons) return fieldErrors.value.emoticons;
    if (!rules.value) return '';
    const problem = UtilEmoticonSpec.countProblem(rows.value.length, rules.value);
    // 모자람은 신청할 때만, 넘침은 늘
    if (problem === 'TOO_FEW' && !tried.value) return '';
    return UtilEmoticonMessages.count(t, problem, rules.value, rows.value.length);
});

function labelError(row: Row, index: number): string {
    const server = fieldErrors.value[`emoticons[${index}].label`];
    if (server) return server;
    if (!rules.value || !tried.value) return '';
    return UtilEmoticonMessages.label(t, UtilEmoticonSpec.labelProblem(row.label, rules.value), rules.value);
}

function rowBlocking(row: Row): boolean {
    return row.state === 'invalid' || row.state === 'failed';
}

function rowMeta(row: Row): string {
    if (row.state === 'checking') return t('emoticon.rowChecking');
    if (row.state === 'uploading') return t('emoticon.rowUploading');
    if (!row.info) return UtilEmoticonSpec.bytes(row.size);
    const frames = row.info.format === 'GIF' ? t('emoticon.rowFrames', { count: row.info.frames }) : '';
    return [row.info.format, `${row.info.width}×${row.info.height}`, frames, UtilEmoticonSpec.bytes(row.size)].filter(Boolean).join(' · ');
}

const agreements = computed(() => [
    { key: 'agreeOwnership' as const, label: t('emoticon.agreeOwnership') },
    { key: 'agreeLicense' as const, label: t('emoticon.agreeLicense') },
    { key: 'agreePolicy' as const, label: t('emoticon.agreePolicy') },
]);

function agreeError(key: 'agreeOwnership' | 'agreeLicense' | 'agreePolicy'): string {
    return fieldErrors.value[key] || (tried.value && !agree[key] ? t('emoticon.agreeRequired') : '');
}

/* ── 그림 고르기·검사·올리기 ── */
const fileInput = ref<HTMLInputElement | null>(null);
const coverInput = ref<HTMLInputElement | null>(null);

function blobOf(file: File): string {
    const url = URL.createObjectURL(file);
    blobUrls.push(url);
    return url;
}

/** 파일 이름에서 이름표 기본값 — 확장자를 떼고 이름표 길이로 자른다 */
function defaultLabel(file: File): string {
    const base = file.name.replace(/\.[^.]+$/, '').trim();
    return base.slice(0, rules.value?.labelMax ?? 10);
}

function specLines(problems: EmoticonSpecProblem[], info: EmoticonImageInfo | null, size: number, isCover = false) {
    const r = rules.value as EmoticonRules;
    return problems.map((p) => ({ text: UtilEmoticonMessages.spec(t, p, info, size, r, isCover), severity: p.severity }));
}

async function onFilesPicked(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = ''; // 같은 파일을 다시 골라도 change 가 나오게
    for (const file of files) void addFile(file);
}

async function addFile(file: File): Promise<void> {
    const r = rules.value;
    if (!r) return;
    const row = reactive<Row>({ key: ++rowSeq, label: defaultLabel(file), state: 'checking', image: null, previewUrl: blobOf(file), info: null, size: file.size, messages: [] });
    rows.value.push(row);
    const info = UtilEmoticonSpec.inspect(new Uint8Array(await file.arrayBuffer()));
    const problems = UtilEmoticonSpec.imageProblems(info, file.size, r);
    row.info = info;
    row.messages = specLines(problems, info, file.size);
    if (UtilEmoticonSpec.blocking(problems)) {
        row.state = 'invalid';
        return;
    }
    row.state = 'uploading';
    try {
        row.image = await api.upload(file, 'EMOTICON');
        row.state = 'ready';
    } catch (e) {
        row.state = 'failed';
        row.messages = [...row.messages, { text: UtilEmoticonMessages.error(t, e), severity: 'error' }];
    }
}

async function onCoverPicked(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const r = rules.value;
    if (!file || !r) return;
    const info = UtilEmoticonSpec.inspect(new Uint8Array(await file.arrayBuffer()));
    const problems = UtilEmoticonSpec.coverProblems(info, file.size, r);
    cover.messages = specLines(problems, info, file.size, true).map((l) => l.text);
    if (UtilEmoticonSpec.blocking(problems)) return;
    cover.state = 'uploading';
    try {
        cover.image = await api.upload(file, 'COVER');
        cover.previewUrl = blobOf(file);
    } catch (e) {
        cover.messages = [UtilEmoticonMessages.error(t, e)];
    } finally {
        cover.state = 'idle';
    }
}

function move(index: number, delta: number): void {
    const list = rows.value.slice();
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    [list[index], list[target]] = [list[target] as Row, list[index] as Row];
    rows.value = list;
}

function removeRow(index: number): void {
    rows.value = rows.value.filter((_, i) => i !== index);
}

/* ── 저장 ── */
function body(): EmoticonStudioSaveRequest {
    return {
        name: form.name.trim(),
        description: form.description.trim(),
        tags: tags.value.slice(),
        coverImageId: cover.image?.imageId ?? null,
        emoticons: rows.value.filter((r) => r.state === 'ready' && r.image).map((r) => ({ imageId: (r.image as EmoticonStudioImage).imageId, label: r.label.trim() })),
        // 승인된 팩은 판매 희망을 바꾸지 않는다(서버도 무시한다)
        saleWish: priceFixed.value ? (pack.value?.saleWish ?? 'FREE') : form.saleWish,
        priceRequestMemo: priceFixed.value && form.priceRequestMemo.trim() ? form.priceRequestMemo.trim() : null,
    };
}

/** 올리는 중·맞지 않는 그림이 남았다 */
function filesProblem(): string {
    if (rows.value.some((r) => r.state === 'checking' || r.state === 'uploading') || cover.state === 'uploading') return t('emoticon.waitUploads');
    if (rows.value.some(rowBlocking)) return t('emoticon.fixInvalid');
    return '';
}

function applyError(e: unknown): void {
    if (e instanceof ApiError && e.code === 'IDENTITY_UNDERAGE') {
        // 만 19세 미만 작가의 유료 신청(후속 결정 3) — 서버 문구로 안내하고 인증 상태를 새로 받는다
        underageError.value = { message: e.message, adultFrom: e.extraText('adultFrom') ?? null };
        formError.value = e.message;
        void identity.load();
        return;
    }
    if (e instanceof ApiError && e.code === 'EMOTICON_PAYOUT_REQUIRED') {
        payoutRequired.value = true;
        formError.value = payoutNeededText.value;
        return;
    }
    if (e instanceof ApiError && e.code === 'EMOTICON_CONTRACT_REQUIRED') {
        // 그새 판이 바뀌었거나 게시본이 내려갔을 수 있다 — 계약을 다시 받아 동의 칸을 다시 보인다
        contractAgree.value = false;
        fieldErrors.value = { agreeContractVersion: e.message };
        formError.value = e.message;
        void api
            .contract()
            .then((c) => {
                contract.value = c;
            })
            .catch(() => undefined);
        return;
    }
    if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields) {
        fieldErrors.value = e.fields;
        formError.value = e.message;
        return;
    }
    formError.value = UtilEmoticonMessages.error(t, e);
}

async function afterSave(saved: EmoticonStudioPack, message: string): Promise<void> {
    pack.value = saved;
    savedSnapshot.value = snapshot.value;
    toast.success(message);
    if (isNew.value) {
        skipNextLoad = true;
        await router.replace(UtilEmoticon.studioPackPath(saved.id));
    }
}

async function saveDraft(): Promise<void> {
    if (busy.value) return;
    fieldErrors.value = {};
    formError.value = '';
    // 올리는 중이면 기다린다. 맞지 않는 그림은 빼고 저장한다(목록에는 남아 이유를 보인다)
    if (rows.value.some((r) => r.state === 'checking' || r.state === 'uploading') || cover.state === 'uploading') {
        formError.value = t('emoticon.waitUploads');
        return;
    }
    busy.value = 'draft';
    try {
        const saved = await api.save(isNew.value ? null : id.value, body());
        await afterSave(saved, t('emoticon.draftSaved'));
    } catch (e) {
        applyError(e);
    } finally {
        busy.value = null;
    }
}

async function submit(): Promise<void> {
    const r = rules.value;
    if (busy.value || !r || locked.value) return;
    tried.value = true;
    fieldErrors.value = {};
    formError.value = '';
    const files = filesProblem();
    const problems =
        files ||
        UtilEmoticonSpec.nameProblem(form.name, r) ||
        UtilEmoticonSpec.tagsProblem(tags.value, r) ||
        UtilEmoticonSpec.countProblem(rows.value.length, r) ||
        paidOff.value ||
        underageBlocked.value ||
        needsPayout.value ||
        needsContract.value ||
        rows.value.some((row) => UtilEmoticonSpec.labelProblem(row.label, r)) ||
        !cover.image ||
        !agree.agreeOwnership ||
        !agree.agreeLicense ||
        !agree.agreePolicy;
    if (problems) {
        formError.value =
            files ||
            (!cover.image
                ? t('emoticon.coverRequired')
                : paidOff.value
                  ? t('emoticon.paidSalesOff')
                  : underageBlocked.value
                    ? underageText.value
                    : needsPayout.value
                      ? payoutNeededText.value
                      : needsContract.value
                        ? contract.value
                            ? contractRequiredText.value
                            : t('emoticon.contractMissing')
                        : t('emoticon.fixFields'));
        return;
    }
    busy.value = 'submit';
    const paid = paidCondition.value;
    try {
        // 유료 조건이면 지금 판을 보낸다(이미 지금 판에 동의했으면 서버가 다시 남기지 않는다)
        const saved = await api.submit(isNew.value ? null : id.value, { ...body(), ...agree, agreeContractVersion: paid ? (contract.value?.version ?? null) : null });
        // 동의를 남겼으니 계약 상태를 다시 받는다(다음 신청에서 다시 묻지 않게)
        if (paid) contract.value = await api.contract().catch(() => contract.value);
        // 판매 중·판매 중단 팩의 심사 요청은 새 판 심사(UPDATE)다
        const revision = priceFixed.value;
        await afterSave(saved, revision ? t('emoticon.revisionSubmitted') : t('emoticon.submitted'));
        fillFrom(saved);
    } catch (e) {
        applyError(e);
    } finally {
        busy.value = null;
    }
}

// 맨 끝에 둔다 — `load` 가 위의 `snapshot` 을 읽는다
watch(id, () => void load(), { immediate: true });
</script>
