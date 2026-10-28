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

                <!-- 요약 · 가격 · 단추 -->
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

                    <!-- 성사형 진행률 · 남은 수량 -->
                    <div v-if="gb.phase !== 'UPCOMING'" class="flex flex-col gap-1.5 text-sm">
                        <template v-if="gb.minQty > 0">
                            <ProgressBar :value="UtilGroupBuy.progress(gb.currentQty, gb.minQty) ?? 0" :show-value="false" class="h-2" :aria-label="t('groupBuy.progressLabel')" />
                            <span class="tabular-nums text-muted-color">{{ t('groupBuy.progress', { current: gb.currentQty, min: gb.minQty }) }}</span>
                        </template>
                        <span v-if="gb.status === 'RECRUITING'" class="tabular-nums" :class="UtilGroupBuy.lowStock(gb.remainingQty, gb.maxQty) ? 'font-semibold text-orange-700 dark:text-orange-400' : 'text-muted-color'">
                            {{ t('groupBuy.remaining', { count: UtilGroupBuy.amount(gb.remainingQty), total: UtilGroupBuy.amount(gb.maxQty) }) }}
                        </span>
                        <span class="text-muted-color">{{ UtilGroupBuyMessages.shipPlan(t, time, gb) }}</span>
                    </div>

                    <!-- 발송 지연 공지(결정 52) -->
                    <Message v-if="gb.delayNotice" severity="warn" size="small" :closable="false" data-testid="group-buy-delay">
                        <p class="m-0 font-semibold">{{ t('groupBuy.delayTitle', { date: time.serviceDate(gb.delayNotice.newPlannedAt) }) }}</p>
                        <p class="m-0 mt-0.5">{{ gb.delayNotice.reason }}</p>
                        <p v-if="gb.delayNotice.previousPlannedAt" class="m-0 mt-0.5 text-xs">{{ t('groupBuy.delayPrevious', { date: time.serviceDate(gb.delayNotice.previousPlannedAt) }) }}</p>
                    </Message>

                    <!-- 옵션별 남은 수량 -->
                    <div v-if="gb.options.length > 0" class="flex flex-col gap-1.5">
                        <h2 class="m-0 text-sm font-semibold">{{ t('groupBuy.optionsTitle') }}</h2>
                        <ul class="m-0 flex list-none flex-col divide-y divide-surface rounded-lg border border-surface p-0" data-testid="group-buy-options">
                            <li v-for="option in gb.options" :key="option.id" class="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2 text-sm">
                                <span class="min-w-0 flex-1">
                                    <span class="font-medium">{{ option.name }}</span>
                                    <span class="ml-1.5 tabular-nums text-muted-color">{{ UtilGroupBuyMessages.won(t, option.unitPrice) }}</span>
                                </span>
                                <span v-if="option.stockState === 'SOLD_OUT'" class="inline-flex items-center gap-1"><PointShopTag tone="danger">{{ t('groupBuy.stock.SOLD_OUT') }}</PointShopTag></span>
                                <span v-else-if="option.stockState === 'TEMP_SOLD_OUT'" class="inline-flex items-center gap-1"><PointShopTag tone="warn">{{ t('groupBuy.stock.TEMP_SOLD_OUT') }}</PointShopTag></span>
                                <span v-else-if="gb.status === 'RECRUITING'" class="tabular-nums" :class="UtilGroupBuy.lowStock(option.remainingQty, option.maxQty ?? gb.maxQty) ? 'font-semibold text-orange-700 dark:text-orange-400' : 'text-muted-color'">
                                    {{ t('groupBuy.optionRemaining', { count: option.remainingQty }) }}
                                </span>
                                <Button
                                    v-if="option.canWatchRestock"
                                    type="button"
                                    size="small"
                                    variant="text"
                                    :icon="option.watchingRestock ? 'pi pi-bell-slash' : 'pi pi-bell'"
                                    :label="option.watchingRestock ? t('groupBuy.watchOff') : t('groupBuy.watchRestockShort')"
                                    :aria-pressed="option.watchingRestock"
                                    :aria-label="t('groupBuy.watchOptionLabel', { name: option.name })"
                                    :loading="watchBusy === `opt-${option.id}`"
                                    @click="toggleWatch('RESTOCK', option.id, option.watchingRestock)"
                                />
                                <p v-if="option.stockState !== 'AVAILABLE'" class="m-0 w-full text-xs text-muted-color">{{ t(`groupBuy.stockHelp.${option.stockState}`) }}</p>
                            </li>
                        </ul>
                    </div>
                    <p v-else-if="gb.stockState !== 'AVAILABLE' && gb.status === 'RECRUITING'" class="m-0 text-sm text-muted-color">{{ t(`groupBuy.stockHelp.${gb.stockState}`) }}</p>

                    <!-- 주문 단추 — 서버의 `actions.order`·`purchase.reason` 만 보고 정한다 -->
                    <div class="flex flex-col gap-2" data-testid="group-buy-purchase">
                        <Button v-if="gb.actions.order" type="button" :label="t('groupBuy.orderNow')" icon="pi pi-shopping-cart" size="large" data-testid="group-buy-order" @click="goOrder" />
                        <Button v-else-if="reason === 'LOGIN_REQUIRED'" type="button" :label="t('groupBuy.loginToOrder')" icon="pi pi-sign-in" size="large" data-testid="group-buy-login" @click="goLogin" />
                        <Button v-else-if="reason === 'IDENTITY_REQUIRED'" type="button" :label="t('groupBuy.verifyToOrder')" icon="pi pi-id-card" size="large" :loading="gateBusy" data-testid="group-buy-verify" @click="verifyThenOrder" />
                        <Button v-else type="button" :label="t('groupBuy.orderNow')" icon="pi pi-shopping-cart" size="large" disabled data-testid="group-buy-order-disabled" />
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

            <!-- 상품정보제공고시(결정 87) — 넓은 폭은 그 자리에 펼치고, 모바일은 단추로 시트를 연다 -->
            <section v-if="gb.notices.length > 0" :aria-labelledby="noticesId" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-buy-product-info">
                <h2 :id="noticesId" class="m-0 mb-2 text-sm font-semibold">{{ t('groupBuy.productInfoTitle') }}</h2>
                <GroupBuyProductNotices v-if="desktop" :notices="gb.notices" :options="gb.options" />
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
                            <GroupBuyProductNotices :notices="gb.notices" :options="gb.options" heading-tag="h4" />
                        </div>
                    </AppSheet>
                </template>
            </section>

            <CommerceBusinessInfo v-if="shownBusiness" :business="shownBusiness" variant="panel" />
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyDetail, GroupBuyWatchKind } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 공동구매 상세 `/group-buys/{id}` — 로그인 없이 볼 수 있다. 계약 commerce-groupbuy 화면 목록.
 * - 그림·요약·가격(정상가 기준 — 결정 58)·지금 상태(예정 · 마감 연장 · 집계 중 · 종료 사유)·진행률·남은 수량·발송 예정(결정 51)·발송 지연 공지(결정 52).
 * - 옵션별 남은 수량과 **다 팔림 / 잠시 구매 불가(다른 분이 결제 중)** 구분, 옵션 단위 수량 풀림 알림(결정 84). 값은 서버가 계산한 것을 보이기만 한다.
 * - 주문 단추는 서버의 `actions.order`·`purchase.reason` 으로만: 주문하기 · 로그인하고 · 본인인증하고(`useIdentityGate` — 용도 GROUP_BUY) · 막는 안내(+ 이미 주문했으면 그 주문으로).
 * - 알림 신청: 수량 풀림(RESTOCK) · 마감 임박(CLOSING_SOON) · 다 팔려 끝난 공구의 다음 차수(NEXT_ROUND — 결정 17). 로그인하지 않았으면 로그인으로 보낸다.
 * - 판매조건 요약(결정 57)·배송비·반품비 금액 고지(10-8)·청약철회 제한(결정 55)·상세 설명(정화된 HTML)·사양·사업자 정보(판매 스위치 켜짐일 때 — 결정 18·56). Q&A 는 2b-6.
 * - 상품정보제공고시(결정 87): 양식마다 품목·적용 옵션·항목 표(`GroupBuyProductNotices`), 옵션 칩으로 그 옵션의 양식을 펼친다. 모바일은 「상품정보제공고시 보기」 → 시트.
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
const commerce = useCommerceInfo();
const { shownBusiness } = commerce;
const titleId = useId();
const descId = useId();
const noticesId = useId();
const desktop = useIsDesktop();
/** 모바일 — 상품정보제공고시 시트 */
const noticesOpen = ref(false);

const id = computed(() => Number(route.params.id));
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
        else if (UtilGroupBuy.lowStock(g.remainingQty, g.maxQty)) out.push({ key: 'low', text: t('groupBuy.remainingFew', { count: g.remainingQty }), tone: 'warn' });
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

/* ── 주문 ── */

function goOrder(): void {
    if (gb.value) void router.push(UtilGroupBuy.orderPath(gb.value.id));
}

function goLogin(): void {
    if (gb.value) void navigateTo(UtilRedirect.toLogin(UtilGroupBuy.orderPath(gb.value.id)));
}

const gateBusy = ref(false);
async function verifyThenOrder(): Promise<void> {
    if (gateBusy.value) return;
    gateBusy.value = true;
    try {
        if (await gate.ensure({ purpose: 'GROUP_BUY' })) goOrder();
    } finally {
        gateBusy.value = false;
    }
}

/* ── 알림 신청 ── */

const watchBusy = ref<string | null>(null);
const watchError = ref('');

async function toggleWatch(kind: GroupBuyWatchKind, optionId: number | null, on: boolean): Promise<void> {
    const g = gb.value;
    if (!g || watchBusy.value) return;
    if (!user.value) {
        void navigateTo(UtilRedirect.toLogin(route.fullPath));
        return;
    }
    watchBusy.value = optionId !== null ? `opt-${optionId}` : kind;
    watchError.value = '';
    try {
        if (on) await api.unwatch(g.id, { kind, optionId });
        else await api.watch(g.id, { kind, optionId });
        if (optionId !== null) {
            const option = g.options.find((o) => o.id === optionId);
            if (option) option.watchingRestock = !on;
        } else g.watches[kind] = !on;
        toast.success(on ? t('groupBuy.watchRemoved') : t(`groupBuy.watchAdded.${kind}`));
    } catch (e) {
        watchError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        watchBusy.value = null;
    }
}
</script>
