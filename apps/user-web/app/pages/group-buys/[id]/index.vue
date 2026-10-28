<template>
    <div class="mx-auto flex max-w-4xl flex-col gap-4" data-testid="group-buy-detail">
        <NuxtLink :to="UtilGroupBuy.LIST_PATH" class="inline-flex w-fit items-center gap-1 text-sm text-muted-color hover:underline">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" /> {{ term('GROUP_BUY') }}
        </NuxtLink>

        <div v-if="status === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')" data-testid="group-buy-loading">
            <div class="h-72 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-6 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('groupBuy.notFound')" :description="loadError" data-testid="group-buy-notfound">
            <Button as="router-link" :to="UtilGroupBuy.LIST_PATH" :label="t('groupBuy.backToList')" severity="secondary" variant="outlined" />
        </ErrorState>
        <ErrorState v-else-if="status === 'error' || !gb" :title="t('groupBuy.detailFailed')" :description="loadError" retry data-testid="group-buy-error" @retry="load(false)" />

        <template v-else>
            <div class="grid gap-4 md:grid-cols-2">
                <!-- 그림 -->
                <section :aria-label="t('groupBuy.images')" class="flex flex-col gap-2">
                    <div class="overflow-hidden rounded-xl bg-surface-100 dark:bg-surface-800">
                        <img v-if="shownImage" :src="shownImage.url" :alt="shownImage.alt" class="aspect-[4/3] w-full object-cover" />
                        <div v-else class="grid aspect-[4/3] place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-box text-3xl" /></div>
                    </div>
                    <div v-if="gb.images.length > 1" class="flex gap-2 overflow-x-auto">
                        <button
                            v-for="(image, index) in gb.images"
                            :key="image.url"
                            type="button"
                            class="size-16 shrink-0 overflow-hidden rounded-lg border-2 focus-visible:outline-2 focus-visible:outline-primary"
                            :class="index === imageIndex ? 'border-primary' : 'border-transparent'"
                            :aria-label="t('groupBuy.imageN', { n: index + 1 })"
                            :aria-pressed="index === imageIndex"
                            @click="imageIndex = index"
                        >
                            <img :src="image.url" alt="" class="size-full object-cover" />
                        </button>
                    </div>
                </section>

                <!-- 요약 · 가격 · 고르기 · 단추 -->
                <section :aria-labelledby="titleId" class="flex min-w-0 flex-col gap-3">
                    <div class="flex flex-wrap items-center gap-1">
                        <PointShopTag v-for="tag in tags" :key="tag.key" :tone="tag.tone">{{ tag.text }}</PointShopTag>
                    </div>
                    <h1 :id="titleId" class="m-0 text-xl font-bold leading-snug">{{ gb.title }}</h1>
                    <div>
                        <p class="m-0 flex flex-wrap items-baseline gap-x-2">
                            <span class="text-2xl font-bold tabular-nums text-primary">{{ gb.priceFrom ? t('groupBuy.priceFrom', { price: UtilGroupBuyMessages.won(t, gb.groupPrice) }) : UtilGroupBuyMessages.won(t, gb.groupPrice) }}</span>
                            <s v-if="gb.normalPrice" class="text-sm tabular-nums text-muted-color"><span class="sr-only">{{ t('groupBuy.normalPrice') }}</span>{{ UtilGroupBuyMessages.won(t, gb.normalPrice) }}</s>
                        </p>
                        <p v-if="gb.normalPrice && gb.normalPriceBasis" class="m-0 mt-0.5 text-xs text-muted-color" data-testid="group-buy-normal-basis">
                            {{ t('groupBuy.normalPriceBasisShort', { basis: t(`groupBuy.normalPriceBasis.${gb.normalPriceBasis}`) }) }}
                        </p>
                    </div>
                    <p v-if="gb.summary" class="m-0 whitespace-pre-line text-sm leading-relaxed">{{ gb.summary }}</p>

                    <!-- 지금 상태 한 줄 -->
                    <Message v-if="phaseNotice" :severity="phaseNotice.severity" size="small" :closable="false" role="status" data-testid="group-buy-phase-notice">{{ phaseNotice.text }}</Message>

                    <!-- 성사형 진행률 · 남은 수량(본 상품만 센다 — 결정 88) -->
                    <div v-if="gb.phase !== 'UPCOMING'" class="flex flex-col gap-1.5 text-sm">
                        <template v-if="gb.minQty > 0">
                            <ProgressBar :value="UtilGroupBuy.progress(gb.currentQty, gb.minQty) ?? 0" :show-value="false" class="h-2" :aria-label="t('groupBuy.progressLabel')" />
                            <span class="tabular-nums text-muted-color">{{ t('groupBuy.progress', { current: gb.currentQty, min: gb.minQty }) }}</span>
                        </template>
                        <span v-if="gb.status === 'RECRUITING'" class="tabular-nums" :class="UtilGroupBuy.lowStock(gb.availability.remaining, gb.maxQty) ? 'font-semibold text-orange-700 dark:text-orange-400' : 'text-muted-color'">
                            {{ t('groupBuy.remaining', { count: UtilGroupBuy.amount(gb.availability.remaining), total: UtilGroupBuy.amount(gb.maxQty) }) }}
                        </span>
                        <span class="text-muted-color">{{ UtilGroupBuyMessages.shipPlan(t, time, gb) }}</span>
                    </div>

                    <!-- 발송 지연 공지(결정 52) -->
                    <Message v-if="gb.delayNotice" severity="warn" size="small" :closable="false" data-testid="group-buy-delay">
                        <p class="m-0 font-semibold">{{ t('groupBuy.delayTitle', { date: time.serviceDate(gb.delayNotice.newPlannedAt) }) }}</p>
                        <p class="m-0 mt-0.5">{{ gb.delayNotice.reason }}</p>
                        <p v-if="gb.delayNotice.previousPlannedAt" class="m-0 mt-0.5 text-xs">{{ t('groupBuy.delayPrevious', { date: time.serviceDate(gb.delayNotice.previousPlannedAt) }) }}</p>
                    </Message>

                    <!-- 옵션 선택 — 축마다 선택 칸(Select, 일반 쇼핑몰처럼). 마지막 축(축 없는 단품 여럿이면 그 목록)·추가 구성상품에서 고르면 「선택한 상품」에 담고 칸을 비운다(결정 88) -->
                    <section v-if="canPick && pickSelects.length > 0" :aria-labelledby="pickId" class="flex flex-col gap-2.5" data-testid="group-buy-pick">
                        <h2 :id="pickId" class="m-0 text-sm font-semibold">{{ t('groupBuy.pickTitle') }}</h2>
                        <div v-for="sel in pickSelects" :key="sel.key" class="flex flex-col gap-1">
                            <label v-if="sel.label" :for="`${pickId}-${sel.key}`" class="text-xs font-medium text-muted-color">{{ sel.label }}</label>
                            <Select
                                :key="sel.adds ? `${sel.key}-${pickRound}` : sel.key"
                                :input-id="`${pickId}-${sel.key}`"
                                :model-value="sel.value"
                                :options="sel.options"
                                option-label="label"
                                option-value="key"
                                :option-disabled="isOff"
                                :placeholder="sel.placeholder"
                                :disabled="sel.disabled"
                                :aria-labelledby="sel.label ? undefined : pickId"
                                fluid
                                :data-testid="`group-buy-select-${sel.key}`"
                                @update:model-value="sel.pick"
                            >
                                <template #option="{ option }">
                                    <span class="flex w-full items-center gap-3 text-sm">
                                        <span class="min-w-0 flex-1">
                                            <span class="font-medium">{{ option.label }}</span>
                                            <span v-if="option.sku && option.state !== 'OFF'" class="ml-1.5 tabular-nums text-muted-color">{{ UtilGroupBuyMessages.won(t, option.sku.unitPrice) }}</span>
                                        </span>
                                        <span class="shrink-0 text-xs tabular-nums" :class="stateClass(option)">{{ stateText(option) }}</span>
                                    </span>
                                </template>
                            </Select>
                            <p v-if="sel.help" class="m-0 text-xs text-muted-color">{{ sel.help }}</p>
                        </div>
                        <!-- 품절 단품을 고르면 담지 않고 안내 · 재입고 알림 -->
                        <div v-if="soldOutPick" class="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-surface px-3 py-2 text-sm" role="status" data-testid="group-buy-soldout-pick">
                            <span class="min-w-0 flex-1">
                                <span class="font-medium">{{ soldOutPick.sku!.name }}</span>
                                <span class="ml-1.5 text-muted-color">{{ t(`groupBuy.stockHelp.${soldOutPick.state}`) }}</span>
                            </span>
                            <Button
                                v-if="gb.skuWatches.watchable.includes(soldOutPick.sku!.skuId)"
                                type="button"
                                size="small"
                                variant="text"
                                :icon="watchingSku(soldOutPick.sku!.skuId) ? 'pi pi-bell-slash' : 'pi pi-bell'"
                                :label="watchingSku(soldOutPick.sku!.skuId) ? t('groupBuy.watchOff') : t('groupBuy.watchRestockShort')"
                                :aria-pressed="watchingSku(soldOutPick.sku!.skuId)"
                                :aria-label="t('groupBuy.watchSkuLabel', { name: soldOutPick.sku!.name })"
                                :loading="watchBusy === `sku-${soldOutPick.sku!.skuId}`"
                                @click="toggleWatch('RESTOCK', soldOutPick.sku!.skuId, watchingSku(soldOutPick.sku!.skuId))"
                            />
                        </div>
                    </section>

                    <!-- 고를 수 없을 때(예정·종료 등) — 상품별 남은 수량(축이나 추가 구성상품이 있을 때) -->
                    <section v-if="listRows.length > 0" :aria-labelledby="`${pickId}-list`" class="flex flex-col gap-1.5" data-testid="group-buy-skus-list">
                        <h2 :id="`${pickId}-list`" class="m-0 text-sm font-semibold">{{ t('groupBuy.skusTitle') }}</h2>
                        <ul class="m-0 flex list-none flex-col divide-y divide-surface rounded-lg border border-surface p-0">
                            <li v-for="row in listRows" :key="row.key" class="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2 text-sm" :class="row.state === 'OFF' ? 'text-muted-color' : ''">
                                <span class="min-w-0 flex-1">
                                    <span class="inline-flex flex-wrap items-center gap-1.5">
                                        <PointShopTag v-if="row.sku?.kind === 'ADDON'" tone="neutral">{{ t('groupBuy.addonTag') }}</PointShopTag>
                                        <span class="font-medium">{{ row.label }}</span>
                                    </span>
                                    <span v-if="row.sku && row.state !== 'OFF'" class="ml-1.5 tabular-nums text-muted-color">{{ UtilGroupBuyMessages.won(t, row.sku.unitPrice) }}</span>
                                </span>
                                <span class="text-xs tabular-nums" :class="stateClass(row)">{{ stateText(row) }}</span>
                                <Button
                                    v-if="row.sku && gb.skuWatches.watchable.includes(row.sku.skuId)"
                                    type="button"
                                    size="small"
                                    variant="text"
                                    :icon="watchingSku(row.sku.skuId) ? 'pi pi-bell-slash' : 'pi pi-bell'"
                                    :label="watchingSku(row.sku.skuId) ? t('groupBuy.watchOff') : t('groupBuy.watchRestockShort')"
                                    :aria-pressed="watchingSku(row.sku.skuId)"
                                    :aria-label="t('groupBuy.watchSkuLabel', { name: row.sku.name })"
                                    :loading="watchBusy === `sku-${row.sku.skuId}`"
                                    @click="toggleWatch('RESTOCK', row.sku.skuId, watchingSku(row.sku.skuId))"
                                />
                            </li>
                        </ul>
                    </section>
                    <p v-if="!canPick && axes.length === 0 && addonSkus.length === 0 && gb.stockState !== 'AVAILABLE' && gb.status === 'RECRUITING'" class="m-0 text-sm text-muted-color">{{ t(`groupBuy.stockHelp.${gb.stockState}`) }}</p>

                    <!-- 선택한 상품 · 서버 견적 -->
                    <section v-if="canPick" :aria-labelledby="pickedId" class="flex flex-col gap-2 rounded-lg border border-surface bg-surface-50 p-3 dark:bg-surface-800/50" data-testid="group-buy-picked">
                        <h2 :id="pickedId" class="m-0 text-sm font-semibold">{{ t('groupBuy.pickedTitle') }}</h2>
                        <p v-if="lines.length === 0" class="m-0 text-sm text-muted-color">{{ t('groupBuy.pickedEmpty') }}</p>
                        <GroupBuyPickedLines v-else :lines="pickedLines" editable @qty="setQty" @remove="removeLine" />
                        <p v-if="capNote" class="m-0 text-xs text-orange-700 dark:text-orange-400" role="status">{{ capNote }}</p>
                        <GroupBuyQuoteSummary class="border-t border-surface pt-2" :quote="quote" :ready="hasMain" :busy="quoteBusy" :error="quoteError" />
                    </section>

                    <!-- 구매 단추 — 서버의 `actions.order`·`purchase.reason` 만 보고 정한다(본 상품 줄이 있어야 누른다) -->
                    <div class="flex flex-col gap-2" data-testid="group-buy-purchase">
                        <Button v-if="gb.actions.order" type="button" :label="t('groupBuy.orderNow')" icon="pi pi-shopping-cart" size="large" :disabled="!hasMain" data-testid="group-buy-order" @click="goOrder" />
                        <Button v-else-if="reason === 'LOGIN_REQUIRED'" type="button" :label="t('groupBuy.loginToOrder')" icon="pi pi-sign-in" size="large" data-testid="group-buy-login" @click="goLogin" />
                        <Button v-else-if="reason === 'IDENTITY_REQUIRED'" type="button" :label="t('groupBuy.verifyToOrder')" icon="pi pi-id-card" size="large" :loading="gateBusy" data-testid="group-buy-verify" @click="verifyThenOrder" />
                        <Button v-else type="button" :label="t('groupBuy.orderNow')" icon="pi pi-shopping-cart" size="large" disabled data-testid="group-buy-order-disabled" />
                        <p v-if="gb.actions.order && !hasMain" class="m-0 text-sm text-muted-color" data-testid="group-buy-pick-first">{{ t('groupBuy.pickFirst') }}</p>
                        <p v-if="reason && reason !== 'LOGIN_REQUIRED'" class="m-0 text-sm text-muted-color" role="status" data-testid="group-buy-reason">
                            {{ t(`groupBuy.reason.${reason}`) }}
                            <NuxtLink v-if="reason === 'ALREADY_JOINED' && gb.myOrderNo" :to="UtilGroupBuy.myOrderPath(gb.myOrderNo)" class="ml-1 font-medium text-primary hover:underline">{{ t('groupBuy.seeMyOrder') }}</NuxtLink>
                        </p>

                        <!-- 알림 신청 -->
                        <div v-if="gb.actions.watchRestock || gb.actions.watchClosingSoon || gb.actions.watchNextRound" class="flex flex-wrap gap-2">
                            <Button
                                v-if="gb.actions.watchRestock"
                                type="button"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :icon="gb.watches.RESTOCK ? 'pi pi-bell-slash' : 'pi pi-bell'"
                                :label="gb.watches.RESTOCK ? t('groupBuy.watchRestockOff') : t('groupBuy.watchRestock')"
                                :aria-pressed="gb.watches.RESTOCK"
                                :loading="watchBusy === 'RESTOCK'"
                                data-testid="group-buy-watch-restock"
                                @click="toggleWatch('RESTOCK', null, gb.watches.RESTOCK)"
                            />
                            <Button
                                v-if="gb.actions.watchClosingSoon"
                                type="button"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :icon="gb.watches.CLOSING_SOON ? 'pi pi-bell-slash' : 'pi pi-clock'"
                                :label="gb.watches.CLOSING_SOON ? t('groupBuy.watchClosingOff') : t('groupBuy.watchClosing')"
                                :aria-pressed="gb.watches.CLOSING_SOON"
                                :loading="watchBusy === 'CLOSING_SOON'"
                                data-testid="group-buy-watch-closing"
                                @click="toggleWatch('CLOSING_SOON', null, gb.watches.CLOSING_SOON)"
                            />
                            <Button
                                v-if="gb.actions.watchNextRound"
                                type="button"
                                size="small"
                                :severity="gb.watches.NEXT_ROUND ? 'secondary' : undefined"
                                :variant="gb.watches.NEXT_ROUND ? 'outlined' : undefined"
                                :icon="gb.watches.NEXT_ROUND ? 'pi pi-bell-slash' : 'pi pi-bell'"
                                :label="gb.watches.NEXT_ROUND ? t('groupBuy.watchNextRoundOff') : t('groupBuy.watchNextRound')"
                                :aria-pressed="gb.watches.NEXT_ROUND"
                                :loading="watchBusy === 'NEXT_ROUND'"
                                data-testid="group-buy-watch-next"
                                @click="toggleWatch('NEXT_ROUND', null, gb.watches.NEXT_ROUND)"
                            />
                        </div>
                        <Message v-if="watchError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ watchError }}</Message>
                    </div>
                </section>
            </div>

            <GroupBuySalesTerms :gb="gb" />
            <GroupBuyShippingNotice :shipping="gb.shipping" :withdrawal-restriction="gb.withdrawalRestriction" />

            <!-- 상세 설명(서버가 정화한 HTML) · 사양 -->
            <section :aria-labelledby="descId" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                <h2 :id="descId" class="m-0 mb-2 text-sm font-semibold">{{ t('groupBuy.descriptionTitle') }}</h2>
                <div class="rich-text text-[0.9375rem]" data-testid="group-buy-description" v-html="gb.descriptionHtml" />
                <template v-if="gb.spec">
                    <h3 class="m-0 mb-1 mt-4 text-sm font-semibold">{{ t('groupBuy.specTitle') }}</h3>
                    <p class="m-0 whitespace-pre-line text-sm leading-relaxed">{{ gb.spec }}</p>
                </template>
            </section>

            <!-- 상품정보제공고시(결정 87·88) — 넓은 폭은 그 자리에 펼치고, 모바일은 단추로 시트를 연다 -->
            <section v-if="gb.notices.length > 0" :aria-labelledby="noticesId" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-buy-product-info">
                <h2 :id="noticesId" class="m-0 mb-2 text-sm font-semibold">{{ t('groupBuy.productInfoTitle') }}</h2>
                <GroupBuyProductNotices v-if="desktop" :notices="gb.notices" :skus="saleSkus" />
                <template v-else>
                    <p class="m-0 mb-3 text-sm text-muted-color">{{ t('groupBuy.productInfoHelp') }}</p>
                    <Button
                        type="button"
                        :label="t('groupBuy.productInfoOpen')"
                        icon="pi pi-list"
                        severity="secondary"
                        variant="outlined"
                        size="small"
                        aria-haspopup="dialog"
                        data-testid="group-buy-product-info-open"
                        @click="noticesOpen = true"
                    />
                    <AppSheet v-model:visible="noticesOpen" :title="t('groupBuy.productInfoTitle')" wide>
                        <div class="px-5 pt-3">
                            <GroupBuyProductNotices :notices="gb.notices" :skus="saleSkus" heading-tag="h4" />
                        </div>
                    </AppSheet>
                </template>
            </section>

            <CommerceBusinessInfo v-if="shownBusiness" :business="shownBusiness" variant="panel" />
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyDetail, GroupBuyOrderLine, GroupBuyQuote, GroupBuySku, GroupBuyWatchKind } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { GroupBuyPickedLine } from '~/components/groupbuy/GroupBuyPickedLines.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy, { type GroupBuyValueState } from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 공동구매 상세 `/group-buys/{id}` — 로그인 없이 볼 수 있다. 계약 commerce-groupbuy 화면 목록.
 * - 그림·요약·가격(정상가 기준 — 결정 58)·지금 상태(예정 · 마감 연장 · 집계 중 · 종료 사유)·진행률·남은 수량·발송 예정(결정 51)·발송 지연 공지(결정 52).
 * - **상품 고르기는 이 화면에서**(결정 88 — 일반 쇼핑몰처럼): 축마다 선택 칸(Select)을 차례로 고르고, 마지막 축 칸에서 단품(가격·남은 수량·다 팔림/잠시 구매 불가/판매 안 함)을 고르면 「선택한 상품」에 담고 칸을 비운다.
 *   품절 단품을 고르면 담지 않고 안내·재입고 알림. 이미 담긴 단품이면 수량 +1. 줄마다 수량 ± · 빼기(상한은 단품 남은 수량 · 1인 한도(본 상품만) — 편의, 최종은 서버). 축이 없으면 본 상품 한 줄(수량만)이 처음부터 담긴다.
 * - 「추가 구성상품」: 본 상품 줄이 있어야 담는다(단독 구매 불가). 본 상품 줄을 모두 빼려 하면 「추가 구성상품도 뺄까요?」 — 예면 함께 빼고, 아니면 그대로 둔다.
 * - 합계는 서버 견적(줄을 바꾸면 300ms 뒤 다시). 고른 목록은 주소 `?items=skuId:qty,…` 에도 적어 새로고침·로그인·주문서 왕복에도 남는다.
 * - 구매 단추는 서버의 `actions.order`·`purchase.reason` 으로만: 구매하기 · 로그인하고 · 본인인증하고(`useIdentityGate` — 용도 GROUP_BUY) · 막는 안내(+ 이미 주문했으면 그 주문으로).
 * - 알림 신청: 단품 단위 수량 풀림(단품 줄의 종) · 공구 단위 수량 풀림 · 마감 임박 · 다음 차수(결정 17). 로그인하지 않았으면 로그인으로 보낸다.
 * - 판매조건 요약(결정 57)·배송비·반품비 금액 고지(10-8)·청약철회 제한(결정 55)·상세 설명(정화된 HTML)·사양·사업자 정보(판매 스위치 켜짐일 때 — 결정 18·56). Q&A 는 2b-6.
 * - 상품정보제공고시(결정 87·88): 양식마다 품목·적용 단품·항목 표(`GroupBuyProductNotices`), 단품 칩으로 그 단품의 양식을 펼친다. 모바일은 「상품정보제공고시 보기」 → 시트.
 */
const { t } = useI18n();
const { term } = useSiteTerms();
const { user } = useAuth();
const route = useRoute();
const router = useRouter();
const time = useDisplayTime();
const api = useGroupBuyApi();
const gate = useIdentityGate();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const commerce = useCommerceInfo();
const { shownBusiness } = commerce;
const titleId = useId();
const descId = useId();
const noticesId = useId();
const pickId = useId();
const pickedId = useId();
const desktop = useIsDesktop();
/** 모바일 — 상품정보제공고시 시트 */
const noticesOpen = ref(false);

const id = computed(() => String(route.params.id));
const gb = ref<GroupBuyDetail | null>(null);
const status = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const loadError = ref('');
const imageIndex = ref(0);
let seq = 0;

useSeoMeta({ title: () => gb.value?.title ?? term('GROUP_BUY') });

async function load(quiet: boolean): Promise<void> {
    const mine = ++seq;
    if (!quiet) status.value = 'loading';
    loadError.value = '';
    try {
        const detail = await api.get(id.value);
        if (mine !== seq) return;
        gb.value = detail;
        if (imageIndex.value >= detail.images.length) imageIndex.value = 0;
        restoreLines(detail);
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = UtilGroupBuyMessages.error(t, e);
        status.value = e instanceof ApiError && e.status === 404 ? 'notfound' : 'error';
    }
}

watch([id, () => user.value?.id ?? null], () => void load(false), { immediate: true });
onMounted(() => void commerce.ensureLoaded());
onBeforeUnmount(() => seq++);

const shownImage = computed(() => gb.value?.images[imageIndex.value] ?? null);
const reason = computed(() => gb.value?.purchase.reason ?? null);

type Tone = 'neutral' | 'primary' | 'success' | 'warn' | 'danger';
const tags = computed(() => {
    const g = gb.value;
    if (!g) return [];
    const out: { key: string; text: string; tone: Tone }[] = [];
    if (g.roundNo > 1) out.push({ key: 'round', text: t('groupBuy.roundNoGroupBuy', { n: g.roundNo }), tone: 'primary' });
    if (g.addedQty > 0) out.push({ key: 'added', text: t('groupBuy.addedQty', { count: g.addedQty }), tone: 'success' });
    out.push({ key: 'kind', text: g.minQty > 0 ? t('groupBuy.kindMin') : t('groupBuy.kindFirst'), tone: 'neutral' });
    out.push({ key: 'fulfillment', text: t(`groupBuy.fulfillment.${g.fulfillment}`), tone: 'neutral' });
    if (g.status === 'RECRUITING') {
        if (g.stockState !== 'AVAILABLE') out.push({ key: 'stock', text: t(`groupBuy.stock.${g.stockState}`), tone: g.stockState === 'SOLD_OUT' ? 'danger' : 'warn' });
        else if (UtilGroupBuy.lowStock(g.availability.remaining, g.maxQty)) out.push({ key: 'low', text: t('groupBuy.remainingFew', { count: g.availability.remaining }), tone: 'warn' });
        if (!g.extendedUntil && g.closingSoon) out.push({ key: 'soon', text: t('groupBuy.closingSoon'), tone: 'danger' });
    }
    return out;
});

/** 지금 상태 한 줄 — 예정 · 연장 중 · 모집 중 마감 시각 · 집계 중 · 종료(사유) */
const phaseNotice = computed<{ text: string; severity: 'info' | 'warn' | 'secondary' | 'success' } | null>(() => {
    const g = gb.value;
    if (!g) return null;
    if (g.phase === 'UPCOMING') return { text: t('groupBuy.upcomingNotice', { at: time.value.shortDateTime(g.startAt) }), severity: 'info' };
    if (g.status === 'RECRUITING' && g.extendedUntil) return { text: t('groupBuy.extendedNotice', { at: time.value.clock(g.extendedUntil) }), severity: 'warn' };
    if (g.status === 'RECRUITING') return { text: t('groupBuy.endsAt', { at: time.value.shortDateTime(g.endAt) }), severity: 'secondary' };
    if (g.status === 'CLOSING') return { text: t('groupBuy.judgingNotice'), severity: 'info' };
    if (g.closeReason === 'SOLD_OUT') return { text: t('groupBuy.soldOutNotice'), severity: 'success' };
    if (g.closeReason) return { text: t(`groupBuy.closeReasonNotice.${g.closeReason}`), severity: 'secondary' };
    return { text: t(`groupBuy.status.${g.status}`), severity: 'secondary' };
});

/* ── 축 · 단품(서버 `availability`) ── */

const axes = computed(() => gb.value?.availability.axes ?? []);
const skus = computed(() => gb.value?.availability.skus ?? []);
const addonSkus = computed(() => skus.value.filter((s) => s.kind === 'ADDON'));
/** 옵션(축) 없이 이름으로 가리는 본 상품 단품이 여럿인가(예 쌀 10kg·20kg — 2b-2 6125b751) */
const plainMains = computed(() => (axes.value.length === 0 ? skus.value.filter((s) => s.kind === 'MAIN') : []));
/** 고시 칩·적용 범위 — 판매하는 단품만 */
const saleSkus = computed(() => skus.value.filter((s) => s.onSale));
const lastAxis = computed(() => axes.value.length - 1);
/** 고를 수 있나 — 모집 중이고, 주문할 수 있거나 로그인·본인인증만 남았을 때(고른 뒤 그 길로 이어 간다) */
const canPick = computed(() => {
    const g = gb.value;
    if (!g || g.status !== 'RECRUITING') return false;
    return g.actions.order || reason.value === 'LOGIN_REQUIRED' || reason.value === 'IDENTITY_REQUIRED';
});

/** 앞 축에서 고른 값(축 차례, 고르지 않으면 null) — 마지막 축은 값을 들고 있지 않고, 고르면 바로 담는다 */
const chosen = ref<(string | null)[]>([]);
/** 담는 선택 칸을 비우는 차례 — 고를 때마다 늘려 칸을 다시 그린다(같은 값 다시 고르기도 받는다) */
const pickRound = ref(0);
/** 고른 품절 단품 — 담지 않고 안내·재입고 알림을 보인다 */
const soldOutPick = ref<SkuRow | null>(null);

function axisOpen(axisIndex: number): boolean {
    return axisIndex === 0 || chosen.value[axisIndex - 1] !== null;
}

/** 앞 축 값을 고른다 — 뒤 축 고른 값은 지운다 */
function choose(axisIndex: number, valueId: string | null): void {
    const next = chosen.value.slice();
    next[axisIndex] = valueId;
    for (let i = axisIndex + 1; i < next.length; i++) next[i] = null;
    chosen.value = next;
    soldOutPick.value = null;
}

interface SkuRow {
    key: string;
    sku: GroupBuySku | null;
    label: string;
    state: GroupBuyValueState;
}

function rowOf(sku: GroupBuySku, label = sku.name): SkuRow {
    const state: GroupBuyValueState = !sku.onSale ? 'OFF' : UtilGroupBuy.buyable(sku) ? 'AVAILABLE' : sku.temporarilyUnavailable ? 'TEMP_SOLD_OUT' : 'SOLD_OUT';
    return { key: `sku-${sku.skuId}`, sku, label, state };
}

function isOff(row: SkuRow): boolean {
    return row.state === 'OFF';
}

/** 선택 칸 오른쪽 글자 — 판매 안 함 · 품절 · (단품이면) 남은 수량 */
function stateText(row: SkuRow): string {
    if (row.state === 'OFF') return t('groupBuy.pickValueOff');
    if (row.state !== 'AVAILABLE') return t(`groupBuy.stock.${row.state}`);
    if (!row.sku || gb.value?.status !== 'RECRUITING') return '';
    return row.sku.remaining === null ? t('groupBuy.skuNoLimit') : t('groupBuy.skuRemaining', { count: row.sku.remaining });
}

function stateClass(row: SkuRow): string {
    if (row.state === 'SOLD_OUT') return 'font-semibold text-red-700 dark:text-red-400';
    if (row.state === 'TEMP_SOLD_OUT') return 'font-semibold text-orange-700 dark:text-orange-400';
    const g = gb.value;
    if (row.state === 'AVAILABLE' && row.sku?.remaining != null && g && UtilGroupBuy.lowStock(row.sku.remaining, g.maxQty)) return 'font-semibold text-orange-700 dark:text-orange-400';
    return 'text-muted-color';
}

/** 담는 칸에서 고름 — 살 수 있으면 담고(이미 있으면 +1) 칸을 비운다, 품절이면 안내만 */
function pickRow(rows: SkuRow[], key: string | null): void {
    const row = rows.find((r) => r.key === key);
    pickRound.value++;
    if (!row?.sku || row.state === 'OFF') return;
    if (row.state !== 'AVAILABLE') {
        soldOutPick.value = row;
        return;
    }
    soldOutPick.value = null;
    addLine(row.sku);
    if (row.sku.kind === 'MAIN') chosen.value = axes.value.map(() => null);
}

interface PickSelect {
    key: string;
    label: string;
    options: SkuRow[];
    value: string | null;
    placeholder: string;
    disabled: boolean;
    help: string;
    /** 고르면 담는 칸인가(고른 값을 들고 있지 않는다) */
    adds: boolean;
    pick: (key: string | null) => void;
}

/** 선택 칸들 — 앞 축(값을 든다) · 마지막 축 또는 축 없는 단품 여럿(담는다) · 추가 구성상품(담는다) */
const pickSelects = computed<PickSelect[]>(() => {
    if (!canPick.value) return [];
    const out: PickSelect[] = [];
    axes.value.forEach((axis, axisIndex) => {
        const open = axisOpen(axisIndex);
        const placeholder = open ? t('groupBuy.pickPlaceholder', { name: axis.name }) : t('groupBuy.pickPrevFirst', { name: axes.value[axisIndex - 1]?.name ?? '' });
        if (axisIndex < lastAxis.value) {
            const options = axis.values.map((v): SkuRow => ({ key: v.valueId, sku: null, label: v.value, state: UtilGroupBuy.valueState(skus.value, chosen.value, axisIndex, v.valueId) }));
            out.push({ key: `axis-${axis.axisId}`, label: axis.name, options, value: chosen.value[axisIndex] ?? null, placeholder, disabled: !open, help: '', adds: false, pick: (k) => choose(axisIndex, k) });
            return;
        }
        const prefix = chosen.value.slice(0, lastAxis.value);
        const options = prefix.some((v) => v === null)
            ? []
            : axis.values.map((v): SkuRow => {
                  const sku = UtilGroupBuy.comboSku(skus.value, [...(prefix as string[]), v.valueId]);
                  return sku ? rowOf(sku, v.value) : { key: `value-${v.valueId}`, sku: null, label: v.value, state: 'OFF' };
              });
        out.push({ key: `axis-${axis.axisId}`, label: axis.name, options, value: null, placeholder, disabled: !open, help: '', adds: true, pick: (k) => pickRow(options, k) });
    });
    // 옵션 없는 단품 여럿(예 쌀 10kg·20kg) — 한 칸에서 고른다
    if (plainMains.value.length > 1) {
        const options = plainMains.value.filter((s) => s.onSale).map((s) => rowOf(s));
        out.push({ key: 'main', label: '', options, value: null, placeholder: t('groupBuy.pickItemPlaceholder'), disabled: false, help: '', adds: true, pick: (k) => pickRow(options, k) });
    }
    if (addonSkus.value.length > 0) {
        const options = addonSkus.value.filter((s) => s.onSale).map((s) => rowOf(s));
        const help = hasMain.value ? t('groupBuy.addonsHelp') : `${t('groupBuy.addonsHelp')} ${t('groupBuy.addonsNeedMain')}`;
        out.push({ key: 'addons', label: t('groupBuy.addonsTitle'), options, value: null, placeholder: t('groupBuy.pickPlaceholder', { name: t('groupBuy.addonsTitle') }), disabled: !hasMain.value, help, adds: true, pick: (k) => pickRow(options, k) });
    }
    return out;
});

/** 고를 수 없을 때 — 상품별 남은 수량(축이나 추가 구성상품, 이름으로 가리는 단품 여럿이 있을 때) */
const listRows = computed<SkuRow[]>(() => {
    if (!gb.value || canPick.value) return [];
    if (axes.value.length === 0 && addonSkus.value.length === 0 && plainMains.value.length <= 1) return [];
    return saleSkus.value.map((s) => rowOf(s));
});

/* ── 선택한 상품(주소 `?items=` 와 같이 간다) ── */

const lines = ref<GroupBuyOrderLine[]>([]);
const capNote = ref('');
const hasMain = computed(() => UtilGroupBuy.hasMain(lines.value, skus.value));

function skuOf(skuId: string): GroupBuySku | undefined {
    return skus.value.find((s) => s.skuId === skuId);
}

function qtyOf(skuId: string): number {
    return lines.value.find((l) => l.skuId === skuId)?.qty ?? 0;
}

function capOf(sku: GroupBuySku): number {
    return UtilGroupBuy.lineCap(sku, lines.value, skus.value, gb.value?.maxQtyPerUser ?? null);
}

/** 상한에 걸린 까닭 한 줄 — 1인 한도(본 상품 합)면 한도, 아니면 단품 남은 수량 */
function capMessage(sku: GroupBuySku): string {
    const limit = gb.value?.maxQtyPerUser ?? null;
    if (sku.kind === 'MAIN' && limit !== null) {
        const mains = lines.value.filter((l) => skuOf(l.skuId)?.kind === 'MAIN').reduce((n, l) => n + l.qty, 0);
        if (mains >= limit) return t('groupBuy.lineCapLimit', { count: limit });
    }
    return t('groupBuy.lineCapStock', { name: sku.name });
}

/** 주소의 고른 목록을 되살린다 — 없는 단품·지금 담을 수 없는 단품은 빼고, 수량은 상한으로 줄이고, 본 상품이 없으면 추가 구성상품도 뺀다. 축이 없으면 본 상품 한 줄(수량 1)부터 */
function restoreLines(detail: GroupBuyDetail): void {
    const all = detail.availability.skus;
    chosen.value = detail.availability.axes.map(() => null);
    soldOutPick.value = null;
    capNote.value = '';
    if (detail.status !== 'RECRUITING') {
        lines.value = [];
        return;
    }
    const out: GroupBuyOrderLine[] = [];
    for (const line of UtilGroupBuy.parseItems(route.query[UtilGroupBuy.ITEMS_QUERY]) ?? []) {
        const sku = all.find((s) => s.skuId === line.skuId);
        if (!sku || !UtilGroupBuy.buyable(sku)) continue;
        const qty = Math.min(line.qty, UtilGroupBuy.lineCap(sku, out, all, detail.maxQtyPerUser));
        if (qty >= 1) out.push({ skuId: sku.skuId, qty });
    }
    let next = UtilGroupBuy.hasMain(out, all) ? out : [];
    const only = all.filter((s) => s.kind === 'MAIN');
    if (next.length === 0 && detail.availability.axes.length === 0 && only.length === 1 && UtilGroupBuy.buyable(only[0]!)) next = [{ skuId: only[0]!.skuId, qty: 1 }];
    lines.value = next;
}

/** 담기 — 이미 있으면 +1(상한까지) */
function addLine(sku: GroupBuySku): void {
    if (sku.kind === 'ADDON' && !hasMain.value) return;
    const cap = capOf(sku);
    const current = qtyOf(sku.skuId);
    if (current >= cap) {
        capNote.value = capMessage(sku);
        return;
    }
    capNote.value = '';
    if (current > 0) lines.value = lines.value.map((l) => (l.skuId === sku.skuId ? { ...l, qty: l.qty + 1 } : l));
    else lines.value = [...lines.value, { skuId: sku.skuId, qty: 1 }];
}

function setQty(skuId: string, qty: number): void {
    const sku = skuOf(skuId);
    if (!sku) return;
    const cap = Math.max(1, capOf(sku));
    capNote.value = qty > cap ? capMessage(sku) : '';
    lines.value = lines.value.map((l) => (l.skuId === skuId ? { ...l, qty: Math.min(qty, cap) } : l));
}

/** 빼기 — 마지막 본 상품 줄을 빼면 추가 구성상품도 함께 뺄지 묻는다(아니면 그대로 둔다) */
async function removeLine(skuId: string): Promise<void> {
    const sku = skuOf(skuId);
    const otherMain = lines.value.some((l) => l.skuId !== skuId && skuOf(l.skuId)?.kind === 'MAIN');
    const addons = lines.value.filter((l) => skuOf(l.skuId)?.kind === 'ADDON');
    capNote.value = '';
    if (sku?.kind === 'MAIN' && !otherMain && addons.length > 0) {
        const ok = await confirm({ title: t('groupBuy.removeAddonsTitle'), message: t('groupBuy.removeAddonsText'), confirmLabel: t('groupBuy.removeAddonsConfirm'), tone: 'danger' });
        if (ok) lines.value = [];
        return;
    }
    lines.value = lines.value.filter((l) => l.skuId !== skuId);
}

const pickedLines = computed<GroupBuyPickedLine[]>(() =>
    lines.value.flatMap((l) => {
        const sku = skuOf(l.skuId);
        if (!sku) return [];
        // 수량이 바뀌어 견적을 기다리는 동안은 지난 견적의 줄 금액을 흐리게 둔다(「—」로 바꿨다 되돌리면 깜빡인다)
        const quoted = quote.value?.items.find((q) => q.skuId === l.skuId) ?? null;
        const pending = quoted !== null && quoted.qty !== l.qty;
        return [{ skuId: sku.skuId, name: sku.name, kind: sku.kind, unitPrice: sku.unitPrice, qty: l.qty, max: capOf(sku), amount: quoted?.itemAmount ?? null, pending, removable: axes.value.length > 0 || sku.kind === 'ADDON' }];
    }),
);

/** 주소 쿼리 — 고른 목록(+ 개발용 `?mock=` 스위치는 그대로) */
function itemsQuery(): Record<string, string> {
    const out: Record<string, string> = {};
    const mock = route.query.mock;
    if (typeof mock === 'string' && mock) out.mock = mock;
    const items = UtilGroupBuy.formatItems(lines.value);
    if (items) out[UtilGroupBuy.ITEMS_QUERY] = items;
    return out;
}

/* ── 견적(서버) — 줄을 바꾸면 300ms 뒤 ── */

const quote = ref<GroupBuyQuote | null>(null);
const quoteBusy = ref(false);
const quoteError = ref('');
let quoteSeq = 0;
let quoteTimer: ReturnType<typeof setTimeout> | null = null;

async function requote(): Promise<void> {
    const g = gb.value;
    if (!g || !hasMain.value) return;
    const mine = ++quoteSeq;
    quoteBusy.value = true;
    quoteError.value = '';
    try {
        const res = await api.quote(g.id, { items: lines.value.map((l) => ({ ...l })) });
        if (mine === quoteSeq) quote.value = res;
    } catch (e) {
        if (mine !== quoteSeq) return;
        quote.value = null;
        quoteError.value = e instanceof ApiError && e.status === 400 && e.fields ? (e.fields.items ?? Object.values(e.fields)[0] ?? e.message) : UtilGroupBuyMessages.error(t, e);
    } finally {
        if (mine === quoteSeq) quoteBusy.value = false;
    }
}

watch(
    () => UtilGroupBuy.formatItems(lines.value),
    (items) => {
        // 주소에도 적는다(새로고침·로그인·주문서 왕복 대비)
        if ((route.query[UtilGroupBuy.ITEMS_QUERY] ?? '') !== items) {
            const query = { ...route.query };
            if (items) query[UtilGroupBuy.ITEMS_QUERY] = items;
            else delete query[UtilGroupBuy.ITEMS_QUERY];
            void router.replace({ query });
        }
        if (quoteTimer) clearTimeout(quoteTimer);
        quoteSeq++;
        if (!hasMain.value) {
            quoteBusy.value = false;
            quote.value = null;
            quoteError.value = '';
            return;
        }
        // 기다림을 바로 표시하고 결과가 올 때까지 이어 둔다 — 300ms 사이에 false→true 로 다시 바뀌며 깜빡이지 않게
        quoteBusy.value = true;
        quoteTimer = setTimeout(() => void requote(), 300);
    },
);
onBeforeUnmount(() => {
    if (quoteTimer) clearTimeout(quoteTimer);
    quoteSeq++;
});

/* ── 구매 ── */

function goOrder(): void {
    if (gb.value && hasMain.value) void router.push({ path: UtilGroupBuy.orderPath(gb.value.id), query: itemsQuery() });
}

/** 로그인 뒤 — 고른 것이 있으면 주문서로(고른 목록과 함께), 없으면 이 화면으로 */
function goLogin(): void {
    const g = gb.value;
    if (!g) return;
    const next = hasMain.value ? router.resolve({ path: UtilGroupBuy.orderPath(g.id), query: itemsQuery() }).fullPath : route.fullPath;
    void navigateTo(UtilRedirect.toLogin(next));
}

const gateBusy = ref(false);
async function verifyThenOrder(): Promise<void> {
    if (gateBusy.value) return;
    gateBusy.value = true;
    try {
        if (!(await gate.ensure({ purpose: 'GROUP_BUY' }))) return;
        if (hasMain.value) goOrder();
        else void load(true);
    } finally {
        gateBusy.value = false;
    }
}

/* ── 알림 신청 ── */

const watchBusy = ref<string | null>(null);
const watchError = ref('');

function watchingSku(skuId: string): boolean {
    return gb.value?.skuWatches.watching.includes(skuId) ?? false;
}

async function toggleWatch(kind: GroupBuyWatchKind, skuId: string | null, on: boolean): Promise<void> {
    const g = gb.value;
    if (!g || watchBusy.value) return;
    if (!user.value) {
        void navigateTo(UtilRedirect.toLogin(route.fullPath));
        return;
    }
    watchBusy.value = skuId !== null ? `sku-${skuId}` : kind;
    watchError.value = '';
    try {
        if (on) await api.unwatch(g.id, { kind, skuId });
        else await api.watch(g.id, { kind, skuId });
        if (skuId !== null) {
            const w = g.skuWatches;
            w.watching = on ? w.watching.filter((s) => s !== skuId) : [...w.watching, skuId];
        } else g.watches[kind] = !on;
        toast.success(on ? t('groupBuy.watchRemoved') : t(`groupBuy.watchAdded.${kind}`));
    } catch (e) {
        watchError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        watchBusy.value = null;
    }
}
</script>
