<template>
    <!--
        핫딜 쓰기·고치기의 딜 칸(사용자 설계 7-2 순서) — 상품 링크 → 링크 판정(호스트 `Tag` · 「→ 최종 호스트」 · 대형몰 · 제휴 자동/차단 · 단축 주소 칸 오류)
          → 이미지(우리 서버 사본 — 없음·실패·꺼짐 안내)·쇼핑몰 이름 자동 채움(고칠 수 있음) → 「진행 중인 같은 딜 N건」(펼침 — 줄마다 「이 글보다 N원 저렴/비쌈」, 그래도 등록 가능)
          → 가격(딜가 = 쿠폰 적용 뒤 실제 결제가·배송비 제외 · 정상가, 할인율 미리보기 · 90% 이상 경고) → 배송비 → 마감(비우면 30일 뒤 기간 종료) → 재고(품절 = 종료 안내) → 제휴(단정형)
          → 가격 기준(선택지 + 메모, 해외 직구면 「관부가세 포함」 — 0원 딜·할인율 경고면 필수) → 쿠폰.
        그리기만 하고 판정(`check` — `useDealLinkCheck`)·검사 문구(`errors`)·저장은 부르는 화면이 한다. 이미지 올리기·다시 시도는 `actions`.
        `readonly`(종료된 딜 고치기)면 모든 칸을 막고 안내만. `expired`(기간 지남)면 「마감을 고쳐 주세요」 안내.
        제휴 링크 방식(D5 — `affiliateMode` prop, 판정 결과의 값이 우선): `SITE` 면 「제휴 링크예요」 체크를 숨기고, 개인 제휴 링크(AFFILIATE — 최종 호스트 포함)는 칸 오류(등록 불가),
          사이트 제휴 몰이면 판정 줄에 단정형 「구매 링크는 아이로빗 제휴 링크로 바뀝니다」. `MEMBER` 면 그대로.
    -->
    <fieldset class="m-0 flex min-w-0 flex-col gap-4 rounded-lg border border-surface p-3.5" :aria-busy="check.status === 'checking'" data-testid="hotdeal-fields">
        <legend v-if="legend" class="px-1 text-sm font-semibold">{{ t('hotdeal.legend') }}</legend>

        <Message v-if="readonly" severity="secondary" :closable="false" size="small" data-testid="hotdeal-fields-readonly">{{ t('hotdeal.endedReadonly') }}</Message>
        <Message v-else-if="expired" severity="warn" :closable="false" size="small" data-testid="hotdeal-fields-expired">{{ t('hotdeal.expiredNotice') }}</Message>

        <!-- 1 상품 링크 + 판정 -->
        <div class="flex flex-col gap-1">
            <label :for="`${uid}-url`" class="text-sm font-medium">{{ t('hotdeal.urlLabel') }}</label>
            <InputText
                :id="`${uid}-url`"
                :model-value="modelValue.productUrl"
                type="url"
                inputmode="url"
                autocomplete="off"
                :maxlength="UtilHotdeal.URL_MAX + 50"
                :placeholder="t('hotdeal.urlPlaceholder')"
                :invalid="Boolean(errors.productUrl) || memberAffiliateOff"
                :disabled="locked"
                :aria-describedby="`${uid}-url-msg`"
                fluid
                data-testid="hotdeal-url"
                data-cache-area="hotdeal.rules"
                @update:model-value="patch({ productUrl: String($event ?? '') })"
                @keydown.enter.prevent
            />
            <div :id="`${uid}-url-msg`" aria-live="polite" class="flex flex-col gap-1 text-xs leading-relaxed">
                <p v-if="errors.productUrl" role="alert" class="m-0 text-red-600 dark:text-red-400" data-testid="hotdeal-url-error">{{ errors.productUrl }}</p>
                <!-- 사이트 제휴 방식(D5)인데 개인 제휴 링크 — 등록 전에도 바로 알린다(등록하면 같은 문구가 칸 오류로) -->
                <p v-else-if="memberAffiliateOff" role="alert" class="m-0 text-red-600 dark:text-red-400" data-testid="hotdeal-url-member-affiliate-off">{{ t('hotdeal.urlMemberAffiliateOff') }}</p>
                <p v-if="check.status === 'checking'" class="m-0 flex items-center gap-1.5 text-muted-color"><i class="pi pi-spin pi-spinner text-[0.6875rem]" aria-hidden="true" />{{ t('hotdeal.checking') }}</p>
                <p v-else-if="check.status === 'error'" class="m-0 flex flex-wrap items-center gap-1.5 text-muted-color" data-testid="hotdeal-check-error">
                    <!-- 까닭을 아는 실패(분당 한도·쓰기 자격·칸 오류 — `useDealLinkCheck`)는 그 문구, 모르면 「확인하지 못했어요」 -->
                    <i class="pi pi-exclamation-circle text-[0.6875rem]" aria-hidden="true" />{{ check.error || t('hotdeal.checkFailed') }}
                    <button type="button" :class="LINK_BUTTON" @click="actions.retryCheck()">{{ t('hotdeal.retry') }}</button>
                </p>
                <p v-else-if="result" class="m-0 flex flex-wrap items-center gap-1.5" data-testid="hotdeal-check">
                    <Tag :value="result.host" severity="secondary" class="text-[0.6875rem]" data-testid="hotdeal-host" />
                    <span v-if="result.finalHost && result.finalHost !== result.host" class="inline-flex items-center gap-1 text-muted-color" data-testid="hotdeal-final-host">
                        <i class="pi pi-arrow-right text-[0.625rem]" aria-hidden="true" />{{ t('hotdeal.finalHost', { host: result.finalHost }) }}
                    </span>
                    <Tag v-if="result.common" :value="t('hotdeal.commonMall')" severity="info" class="text-[0.6875rem]" />
                    <span v-if="affiliateLocked" class="inline-flex items-center gap-1 font-medium text-sky-700 dark:text-sky-300" data-testid="hotdeal-affiliate-auto">
                        <i class="pi pi-info-circle text-[0.6875rem]" aria-hidden="true" />{{ t('hotdeal.affiliateAuto') }}
                    </span>
                    <!-- 사이트 제휴 몰(D5) — 단정형 안내만(조건부 표현 없음) -->
                    <span v-else-if="siteMode && result.siteAffiliate && !memberAffiliateOff" class="inline-flex items-center gap-1 font-medium text-sky-700 dark:text-sky-300" data-testid="hotdeal-site-affiliate">
                        <i class="pi pi-info-circle text-[0.6875rem]" aria-hidden="true" />{{ t('hotdeal.siteAffiliateConvert') }}
                    </span>
                </p>
                <p v-else-if="!errors.productUrl" class="m-0 text-muted-color">{{ t('hotdeal.urlHelp') }}</p>
            </div>
        </div>

        <!-- 2 이미지 · 쇼핑몰 이름(링크로 자동 채움 — 고칠 수 있음) -->
        <div class="grid gap-3 sm:grid-cols-[7rem_1fr]">
            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">{{ t('hotdeal.imageLabel') }}</span>
                <div class="relative grid aspect-square w-28 place-items-center overflow-hidden rounded-lg border border-surface bg-surface-50 text-center dark:bg-surface-800/60" data-testid="hotdeal-image">
                    <img v-if="modelValue.image" :src="modelValue.image.url" :alt="t('hotdeal.imageLabel')" class="size-full object-cover" />
                    <span v-else-if="uploading || (check.status === 'checking' && !modelValue.imageTouched)" class="px-2 text-xs text-muted-color">
                        <i class="pi pi-spin pi-spinner mb-1 block" aria-hidden="true" />{{ uploading ? t('hotdeal.imageUploading') : t('hotdeal.imageFetching') }}
                    </span>
                    <span v-else-if="ogStatus === 'FAILED' && !modelValue.imageTouched" class="px-2 text-xs text-muted-color" data-testid="hotdeal-og-failed">
                        <i class="pi pi-exclamation-triangle mb-1 block" aria-hidden="true" />{{ t('hotdeal.imageFailed') }}
                        <button type="button" :class="LINK_BUTTON" class="mt-0.5 block w-full" :disabled="locked" @click="actions.retryCheck()">{{ t('hotdeal.retry') }}</button>
                    </span>
                    <span v-else-if="ogStatus === 'NONE' && !modelValue.imageTouched" class="px-2 text-xs text-muted-color" data-testid="hotdeal-og-none">
                        <i class="pi pi-image mb-1 block" aria-hidden="true" />{{ t('hotdeal.imageNone') }}
                    </span>
                    <span v-else-if="ogStatus === 'OFF' && !modelValue.imageTouched" class="px-2 text-xs text-muted-color" data-testid="hotdeal-og-off">
                        <i class="pi pi-image mb-1 block" aria-hidden="true" />{{ t('hotdeal.imageOff') }}
                    </span>
                    <span v-else class="px-2 text-xs text-muted-color"><i class="pi pi-image mb-1 block" aria-hidden="true" />{{ t('hotdeal.imageEmpty') }}</span>
                </div>
                <p v-if="modelValue.image" class="m-0 text-[0.6875rem] text-muted-color">{{ modelValue.image.source === 'OG' ? t('hotdeal.imageFromOg') : t('hotdeal.imageUploaded') }}</p>
                <div class="flex flex-wrap gap-x-2 gap-y-0.5">
                    <button type="button" :class="LINK_BUTTON" :disabled="locked || uploading" data-testid="hotdeal-image-upload" @click="fileInput?.click()">
                        {{ modelValue.image ? t('hotdeal.imageReplace') : t('hotdeal.imageUpload') }}
                    </button>
                    <button v-if="modelValue.image" type="button" :class="LINK_BUTTON" :disabled="locked" data-testid="hotdeal-image-remove" @click="patch({ image: null, imageTouched: true })">{{ t('hotdeal.imageRemove') }}</button>
                </div>
                <input ref="fileInput" type="file" accept="image/*" class="sr-only" tabindex="-1" aria-hidden="true" @change="onFile" />
                <p v-if="uploadError || errors.image" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.image || uploadError }}</p>
            </div>

            <div class="flex min-w-0 flex-col gap-3">
                <div class="flex flex-col gap-1">
                    <div class="flex items-baseline justify-between gap-2">
                        <label :for="`${uid}-store`" class="text-sm font-medium">{{ t('hotdeal.storeLabel') }}</label>
                        <span class="text-xs tabular-nums text-muted-color">{{ modelValue.storeName.trim().length }} / {{ UtilHotdeal.STORE_NAME_MAX }}</span>
                    </div>
                    <InputText
                        :id="`${uid}-store`"
                        :model-value="modelValue.storeName"
                        :maxlength="UtilHotdeal.STORE_NAME_MAX"
                        autocomplete="off"
                        :invalid="Boolean(errors.storeName)"
                        :disabled="locked"
                        fluid
                        data-testid="hotdeal-store"
                        @update:model-value="patch({ storeName: String($event ?? ''), storeNameTouched: true })"
                        @keydown.enter.prevent
                    />
                    <p v-if="errors.storeName" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.storeName }}</p>
                    <p v-else-if="!modelValue.storeNameTouched && modelValue.storeName" class="m-0 text-xs text-muted-color">{{ t('hotdeal.storeAuto') }}</p>
                </div>

                <p v-if="ogTitle && !locked" class="m-0 flex flex-wrap items-baseline gap-x-2 rounded-md bg-surface-50 px-2.5 py-2 text-xs dark:bg-surface-800/60" data-testid="hotdeal-title-suggest">
                    <span class="min-w-0 break-words text-muted-color">{{ t('hotdeal.titleSuggest', { title: ogTitle }) }}</span>
                    <button type="button" :class="LINK_BUTTON" @click="$emit('useTitle', ogTitle)">{{ t('hotdeal.titleUse') }}</button>
                </p>

                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ ogStatus === 'OFF' ? t('hotdeal.imageHelpOff') : t('hotdeal.imageHelp') }}</p>
            </div>
        </div>

        <!-- 3 같은 딜 안내(그래도 등록 가능) -->
        <Message v-if="sameDeals.length" severity="info" :closable="false" size="small" data-testid="hotdeal-same">
            <div class="flex flex-col gap-1">
                <p class="m-0 flex flex-wrap items-center gap-x-2">
                    <b>{{ t('hotdeal.sameTitle', { n: sameDeals.length }) }}</b>
                    <span>{{ t('hotdeal.sameText') }}</span>
                    <button type="button" :class="LINK_BUTTON" :aria-expanded="sameOpen" @click="sameOpen = !sameOpen">{{ sameOpen ? t('hotdeal.sameHide') : t('hotdeal.sameShow') }}</button>
                </p>
                <template v-if="sameOpen">
                    <ul class="m-0 flex list-none flex-col gap-1 p-0">
                        <li v-for="item in sameDeals" :key="item.postId" class="flex min-w-0 flex-wrap items-baseline gap-x-2" data-testid="hotdeal-same-row">
                            <NuxtLink :to="`/posts/${item.postId}`" target="_blank" class="min-w-0 max-w-full truncate underline-offset-2 hover:underline">{{ item.title }}</NuxtLink>
                            <span class="shrink-0 font-semibold tabular-nums">{{ UtilHotdealMessages.won(t, item.dealPrice) }}</span>
                            <span v-if="UtilHotdealMessages.priceDiff(t, item.dealPrice, modelValue.dealPrice)" class="shrink-0 text-xs font-medium" :class="diffTone(item.dealPrice)" data-testid="hotdeal-same-diff">
                                {{ UtilHotdealMessages.priceDiff(t, item.dealPrice, modelValue.dealPrice) }}
                            </span>
                            <span class="shrink-0 opacity-80">{{ time.listLabel(item.createdAt) }}</span>
                        </li>
                    </ul>
                    <p class="m-0 text-xs opacity-90">{{ t('hotdeal.sameCommentHint') }}</p>
                </template>
            </div>
        </Message>

        <!-- 4 가격 -->
        <div class="grid gap-3 sm:grid-cols-2">
            <div class="flex flex-col gap-1">
                <label :for="`${uid}-deal`" class="text-sm font-medium">{{ t('hotdeal.dealPriceLabel') }}</label>
                <InputNumber
                    :input-id="`${uid}-deal`"
                    :model-value="modelValue.dealPrice"
                    :min="0"
                    :max-fraction-digits="0"
                    :suffix="wonSuffix"
                    :invalid="Boolean(errors.dealPrice)"
                    :disabled="locked"
                    fluid
                    :aria-describedby="`${uid}-deal-help`"
                    data-testid="hotdeal-deal-price"
                    @update:model-value="patch({ dealPrice: numberOrNull($event) })"
                />
                <p v-if="errors.dealPrice" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.dealPrice }}</p>
                <p :id="`${uid}-deal-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('hotdeal.dealPriceHelp') }}</p>
            </div>
            <div class="flex flex-col gap-1">
                <label :for="`${uid}-normal`" class="text-sm font-medium">
                    {{ t('hotdeal.normalPriceLabel') }}<small class="ml-1 text-xs font-normal text-muted-color">{{ t('hotdeal.priceOptional') }}</small>
                </label>
                <InputNumber
                    :input-id="`${uid}-normal`"
                    :model-value="modelValue.normalPrice"
                    :min="0"
                    :max-fraction-digits="0"
                    :suffix="wonSuffix"
                    :invalid="Boolean(errors.normalPrice)"
                    :disabled="locked"
                    fluid
                    data-testid="hotdeal-normal-price"
                    @update:model-value="patch({ normalPrice: numberOrNull($event) })"
                />
                <p v-if="errors.normalPrice" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.normalPrice }}</p>
                <p v-else-if="discount !== null" class="m-0 text-xs font-semibold text-red-600 dark:text-red-400" data-testid="hotdeal-discount-preview">{{ t('hotdeal.discountPreview', { n: discount }) }}</p>
            </div>
        </div>
        <!-- 할인율이 경고 문턱 이상 — 경고 + 가격 기준 필수(막지는 않는다) -->
        <Message v-if="discountWarn && !locked" severity="warn" :closable="false" size="small" data-testid="hotdeal-discount-warn">
            {{ t('hotdeal.discountWarn', { n: rules.discountWarnPercent }) }}
        </Message>

        <!-- 5 배송비 -->
        <div class="flex flex-col gap-1">
            <span :id="`${uid}-ship`" class="text-sm font-medium">{{ t('hotdeal.shippingLabel') }}</span>
            <div class="flex flex-wrap items-center gap-2">
                <SelectButton
                    :model-value="modelValue.shippingMode"
                    :options="shippingOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                    :disabled="locked"
                    size="small"
                    :aria-labelledby="`${uid}-ship`"
                    data-testid="hotdeal-shipping"
                    @update:model-value="patch({ shippingMode: $event ?? modelValue.shippingMode })"
                />
                <InputNumber
                    v-if="modelValue.shippingMode === 'PAID'"
                    :model-value="modelValue.shippingFee"
                    :min="0"
                    :max-fraction-digits="0"
                    :suffix="wonSuffix"
                    :invalid="Boolean(errors.shippingFee)"
                    :disabled="locked"
                    :aria-label="t('hotdeal.shippingFeeAria')"
                    size="small"
                    class="w-36"
                    data-testid="hotdeal-shipping-fee"
                    @update:model-value="patch({ shippingFee: numberOrNull($event) })"
                />
            </div>
            <p v-if="errors.shippingFee" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.shippingFee }}</p>
        </div>

        <!-- 6 마감(선택) -->
        <div class="flex flex-col gap-1">
            <div class="flex items-baseline justify-between gap-2">
                <label :for="`${uid}-ends`" class="text-sm font-medium">{{ t('hotdeal.endsLabel') }}</label>
                <button v-if="modelValue.endsAt && !locked" type="button" :class="LINK_BUTTON" data-testid="hotdeal-ends-clear" @click="patch({ endsAt: '' })">{{ t('hotdeal.endsClear') }}</button>
            </div>
            <AppDateTimePicker
                :model-value="modelValue.endsAt"
                :input-id="`${uid}-ends`"
                :min="range.min"
                :max="range.max"
                :disabled="locked"
                :invalid="Boolean(errors.endsAt)"
                :placeholder="t('hotdeal.endsNone')"
                :described-by="`${uid}-ends-help`"
                class="max-w-xs"
                data-testid="hotdeal-ends"
                @update:model-value="patch({ endsAt: $event })"
            />
            <p v-if="errors.endsAt" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.endsAt }}</p>
            <p :id="`${uid}-ends-help`" class="m-0 text-xs text-muted-color">
                {{ t('hotdeal.endsHelp', { days: UtilHotdeal.ENDS_MAX_DAYS }) }}
                <!-- 마감 없는 딜의 기간 종료 — 설정이 꺼졌으면(0일) 말하지 않는다 -->
                <template v-if="!modelValue.endsAt && rules.autoExpireDays > 0"> {{ t('hotdeal.endsAutoExpire', { days: rules.autoExpireDays }) }}</template>
            </p>
        </div>

        <!-- 7 재고 -->
        <div class="flex flex-col gap-1">
            <span :id="`${uid}-stock`" class="text-sm font-medium">{{ t('hotdeal.stockLabel') }}</span>
            <SelectButton
                :model-value="modelValue.stockState"
                :options="stockOptions"
                option-label="label"
                option-value="value"
                :allow-empty="false"
                :disabled="locked"
                size="small"
                :aria-labelledby="`${uid}-stock`"
                class="flex-wrap"
                data-testid="hotdeal-stock"
                @update:model-value="patch({ stockState: $event ?? modelValue.stockState })"
            />
            <p v-if="modelValue.stockState === 'SOLD_OUT' && !locked" class="m-0 text-xs text-amber-700 dark:text-amber-300" data-testid="hotdeal-stock-soldout">{{ t('hotdeal.stockSoldOutNote') }}</p>
        </div>

        <!-- 8 제휴(제휴 도메인이면 켜고 잠금) — 사이트 제휴 방식(D5 `SITE`)이면 개인 제휴를 받지 않아 칸을 숨긴다 -->
        <div v-if="!siteMode" class="flex flex-col gap-1" data-testid="hotdeal-affiliate-field">
            <label class="inline-flex cursor-pointer items-center gap-2 text-sm" :class="affiliateLocked ? 'cursor-default' : ''">
                <Checkbox
                    :model-value="modelValue.affiliate || affiliateLocked"
                    binary
                    :input-id="`${uid}-aff`"
                    :disabled="locked || affiliateLocked"
                    :aria-describedby="`${uid}-aff-help`"
                    data-testid="hotdeal-affiliate"
                    @update:model-value="patch({ affiliate: Boolean($event) })"
                />
                <span>{{ t('hotdeal.affiliateLabel') }}</span>
                <i v-if="affiliateLocked" class="pi pi-lock text-xs text-muted-color" aria-hidden="true" />
            </label>
            <p :id="`${uid}-aff-help`" class="m-0 text-xs text-muted-color">{{ affiliateLocked ? t('hotdeal.affiliateLocked') : t('hotdeal.affiliateHelp') }}</p>
        </div>

        <!-- 9 가격 기준(선택지 + 메모) — 0원 딜·할인율 경고 문턱 이상이면 필수. 해외 직구면 「관부가세 포함」 -->
        <div class="flex flex-col gap-1.5" data-testid="hotdeal-basis">
            <span :id="`${uid}-basis-kind`" class="text-sm font-medium">
                {{ t('hotdeal.priceBasisLabel') }}<small class="ml-1 text-xs font-normal" :class="basisRequired ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ basisRequired ? t('hotdeal.basisRequiredMark') : t('hotdeal.priceOptional') }}</small>
            </span>
            <SelectButton
                :model-value="modelValue.priceBasisKind"
                :options="basisOptions"
                option-label="label"
                option-value="value"
                :allow-empty="true"
                :disabled="locked"
                :invalid="Boolean(errors.priceBasisKind)"
                size="small"
                :aria-labelledby="`${uid}-basis-kind`"
                class="flex-wrap"
                data-testid="hotdeal-basis-kind"
                @update:model-value="patch({ priceBasisKind: $event ?? null })"
            />
            <p v-if="errors.priceBasisKind" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400" data-testid="hotdeal-basis-error">{{ errors.priceBasisKind }}</p>
            <label v-if="modelValue.priceBasisKind === 'OVERSEAS'" class="inline-flex cursor-pointer items-center gap-2 text-sm">
                <Checkbox :model-value="modelValue.dutyIncluded" binary :input-id="`${uid}-duty`" :disabled="locked" data-testid="hotdeal-duty" @update:model-value="patch({ dutyIncluded: Boolean($event) })" />
                <span>{{ t('hotdeal.dutyIncluded') }}</span>
            </label>
            <InputText
                :id="`${uid}-basis`"
                :model-value="modelValue.priceBasis"
                :maxlength="UtilHotdeal.NOTE_MAX"
                :placeholder="t('hotdeal.priceBasisPlaceholder')"
                :invalid="Boolean(errors.priceBasis)"
                :disabled="locked"
                :aria-label="t('hotdeal.priceBasisMemoAria')"
                autocomplete="off"
                fluid
                @update:model-value="patch({ priceBasis: String($event ?? '') })"
                @keydown.enter.prevent
            />
            <p v-if="errors.priceBasis" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.priceBasis }}</p>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('hotdeal.priceBasisHelp') }}</p>
        </div>

        <!-- 10 쿠폰·적용 방법(선택) -->
        <div class="flex flex-col gap-1">
                <label :for="`${uid}-coupon`" class="text-sm font-medium">
                    {{ t('hotdeal.couponLabel') }}<small class="ml-1 text-xs font-normal text-muted-color">{{ t('hotdeal.priceOptional') }}</small>
                </label>
                <InputText
                    :id="`${uid}-coupon`"
                    :model-value="modelValue.couponNote"
                    :maxlength="UtilHotdeal.NOTE_MAX"
                    :placeholder="t('hotdeal.couponPlaceholder')"
                    :invalid="Boolean(errors.couponNote)"
                    :disabled="locked"
                    autocomplete="off"
                    fluid
                    @update:model-value="patch({ couponNote: String($event ?? '') })"
                    @keydown.enter.prevent
                />
                <p v-if="errors.couponNote" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.couponNote }}</p>
        </div>

        <p v-if="errors.deal" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.deal }}</p>
    </fieldset>
</template>

<script setup lang="ts">
import type { DealFieldActions, DealFieldErrors, DealFormValues, DealLinkCheckState, DealShippingMode } from '~/components/hotdeal/HotdealDealFields.types';
import type { AffiliateMode, DealPriceBasis, DealRules, DealStock } from '~/composables/hotdeal/api/useHotdealApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

interface Props {
    modelValue: DealFormValues;
    check: DealLinkCheckState;
    errors: DealFieldErrors;
    actions: DealFieldActions;
    /** 핫딜 설정 공개 칸(게시판 응답 `dealRules` — `UtilHotdeal.rulesOf`) — 할인율 경고 문턱·기간 종료 일수 안내 */
    rules: DealRules;
    /** 묶음 제목 「딜 정보」를 보인다(모바일 단계 입력은 단계 이름이 대신해 끈다) */
    legend?: boolean;
    disabled?: boolean;
    /** 종료된 딜 — 모든 칸을 막고 안내 */
    readonly?: boolean;
    /** 기간이 지난 딜 — 「마감을 고쳐 주세요」 */
    expired?: boolean;
    /** 고치기 — 같은 딜 안내에서 이 글을 뺀다 */
    excludePostId?: string;
    /** 게시판 응답의 제휴 링크 방식(D5 `BoardDetail.dealAffiliateMode`) — 판정 결과의 `affiliateMode` 가 오면 그 값이 우선 */
    affiliateMode?: AffiliateMode;
}

const props = withDefaults(defineProps<Props>(), { legend: true, disabled: false, readonly: false, expired: false, excludePostId: '', affiliateMode: 'MEMBER' });
const emit = defineEmits<{
    'update:modelValue': [value: DealFormValues];
    /** 「제목으로 쓰기」 — 링크의 상품 이름을 글 제목으로 */
    useTitle: [title: string];
}>();

const LINK_BUTTON = 'rounded px-0.5 text-xs font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50';

const { t } = useI18n();
const time = useDisplayTime();
const uid = useId();

const locked = computed(() => props.disabled || props.readonly);
/** 마감으로 고를 수 있는 범위 — 칸을 연 때 기준(정확한 판정은 화면 검사·서버) */
const range = UtilHotdeal.endsRange(Date.now());
const wonSuffix = computed(() => t('hotdeal.wonSuffix'));

const result = computed(() => (props.check.status === 'done' ? props.check.result : null));
const ogStatus = computed(() => result.value?.og.status ?? null);
const ogTitle = computed(() => (result.value && result.value.rule !== 'BLOCK' ? (result.value.og.title ?? '') : ''));
/** 사이트 제휴 방식(D5) — 「제휴 링크예요」 체크를 숨기고 개인 제휴 링크를 막는다 */
const siteMode = computed(() => UtilHotdeal.affiliateModeOf({ dealAffiliateMode: props.affiliateMode }, result.value) === 'SITE');
/** 사이트 제휴 방식인데 개인 제휴 링크(등록 불가) */
const memberAffiliateOff = computed(() => UtilHotdeal.memberAffiliateOff(result.value, props.modelValue.productUrl));
const affiliateLocked = computed(() => !siteMode.value && result.value?.rule === 'AFFILIATE');
const sameDeals = computed(() => (result.value?.sameDeals ?? []).filter((s) => s.postId !== props.excludePostId));
const sameOpen = ref(false);
const discount = computed(() => UtilHotdeal.discountRate(props.modelValue.normalPrice, props.modelValue.dealPrice));

const shippingOptions = computed(() =>
    UtilHotdeal.SHIPPING_MODES.map((value: DealShippingMode) => ({ value, label: t(value === 'FREE' ? 'hotdeal.shipFree' : value === 'UNKNOWN' ? 'hotdeal.shipUnknown' : 'hotdeal.shipPaid') })),
);
const stockOptions = computed(() => UtilHotdeal.STOCKS.map((value: DealStock) => ({ value, label: UtilHotdealMessages.stock(t, value) })));
const basisOptions = computed(() => UtilHotdeal.PRICE_BASES.map((value: DealPriceBasis) => ({ value, label: UtilHotdealMessages.priceBasis(t, value) })));
/** 할인율이 경고 문턱 이상 */
const discountWarn = computed(() => UtilHotdeal.discountWarn(props.modelValue, props.rules));
/** 가격 기준이 필수인가(0원 딜 · 할인율 경고) — 「필수」 표시 */
const basisRequired = computed(() => props.modelValue.dealPrice === 0 || discountWarn.value);

/** 같은 딜 줄의 가격 차 색 — 그 글이 더 싸면 강조 */
function diffTone(other: number): string {
    const base = props.modelValue.dealPrice;
    if (base === null || other === base) return 'opacity-80';
    return other < base ? 'text-red-700 dark:text-red-300' : 'opacity-80';
}

function patch(change: Partial<DealFormValues>): void {
    emit('update:modelValue', { ...props.modelValue, ...change });
}

function numberOrNull(value: unknown): number | null {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/* ── 이미지 직접 올리기 ── */
const fileInput = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const uploadError = ref('');

async function onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || uploading.value) return;
    uploading.value = true;
    uploadError.value = '';
    try {
        const image = await props.actions.upload(file);
        patch({ image, imageTouched: true });
    } catch (e) {
        uploadError.value = e instanceof ApiError ? e.message || t('hotdeal.imageUploadFailed') : UtilBoardMessages.of(t, e);
    } finally {
        uploading.value = false;
    }
}
</script>
