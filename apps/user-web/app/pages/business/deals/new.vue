<template>
    <!--
        사업자 핫딜 신청 `/business/deals/new` — 사전 심사 신청(★-1, 03 §7-2). Stepper 5단계:
        ① 상품 링크 붙여넣기 → [확인](또는 칸을 벗어나면) → 쇼핑몰 자동 표시(고칠 수 있음)·제휴 표시 자동·단축 URL 차단
        ② 가격(정상가·딜가 → 할인율 미리보기)·배송비·마감·재고 ③ 제목·본문 + 딜 미리보기
        ④ 노출 추가(없음 / 홈 상단 / 말머리 상단 — 고르면 노출 기간 달력) ⑤ 결제 방식 + 예상 금액 + 동의 → [심사 신청].
        「사업자 핫딜은 심사 뒤 결제하면 게시돼요」. 노출을 고르면 같은 신청 건에 묶어 한 번에 결제한다(목업 가정).
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="business-deal-new">
        <div class="flex items-center gap-2">
            <Button as="router-link" :to="UtilBusiness.CENTER_PATH" icon="pi pi-angle-left" severity="secondary" variant="text" rounded :aria-label="t('business.home.title')" />
            <h1 class="m-0 text-lg font-bold">{{ t('business.deal.title') }}</h1>
        </div>

        <div v-if="status === 'loading'" class="h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />
        <ErrorState v-else-if="status === 'error' || !profile || !dealProduct" :title="t('business.home.loadFailed')" :description="loadError" retry @retry="load" />
        <BusinessGateNotice v-else-if="profile.status !== 'APPROVED' || profile.reReview" :status="profile.status" :re-review="profile.reReview" />

        <template v-else>
            <Message severity="info" size="small" :closable="false">{{ t('business.deal.intro') }}</Message>
            <BusinessStepHeader :steps="stepNames" :current="step" />

            <form class="panel-card flex flex-col gap-4 p-4" novalidate @submit.prevent="next">
                <!-- ① 상품 링크 -->
                <section v-if="step === 1" class="flex flex-col gap-4">
                    <LabeledField :label="t('business.deal.link')" :error="errors.link">
                        <template #default="{ id, describedBy, invalid }">
                            <div class="flex gap-2">
                                <InputText
                                    v-model="deal.linkUrl"
                                    :id="id"
                                    type="url"
                                    inputmode="url"
                                    :placeholder="t('business.deal.linkPlaceholder')"
                                    :maxlength="1000"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    class="min-w-0 flex-1"
                                    data-testid="business-deal-link"
                                    @input="linkError = ''"
                                    @blur="checkLink"
                                />
                                <Button type="button" :label="t('business.deal.linkCheck')" severity="secondary" variant="outlined" :loading="linkBusy" @click="checkLink" />
                            </div>
                        </template>
                    </LabeledField>
                    <div v-if="linkResult && linkChecked" class="flex flex-col gap-3">
                        <p class="m-0 flex flex-wrap items-center gap-1.5 text-sm">
                            <PointShopTag :tone="linkResult.blocked ? 'danger' : 'success'">{{ linkResult.host }}</PointShopTag>
                            <PointShopTag v-if="linkResult.affiliate" tone="warn">{{ t('business.deal.affiliateOn') }}</PointShopTag>
                        </p>
                        <LabeledField v-if="!linkResult.blocked" :label="t('business.deal.shopName')" :help="t('business.deal.shopNameHelp')" :error="errors.shop">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="deal.shopName" :id="id" :maxlength="40" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                </section>

                <!-- ② 가격 -->
                <section v-else-if="step === 2" class="flex flex-col gap-4">
                    <div class="grid gap-4 sm:grid-cols-2">
                        <LabeledField :label="t('business.deal.listPrice')">
                            <template #default="{ id, describedBy }">
                                <InputNumber v-model="deal.listPrice" :input-id="id" :min="0" :max="100_000_000" :suffix="t('payment.wonSuffix')" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('business.deal.dealPrice')" :error="errors.dealPrice" :success="discount !== null ? t('business.deal.discount', { rate: discount }) : ''">
                            <template #default="{ id, describedBy, invalid }">
                                <InputNumber v-model="deal.dealPrice" :input-id="id" :min="0" :max="100_000_000" :suffix="t('payment.wonSuffix')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                    <div class="flex flex-col gap-2">
                        <span :id="`${uid}-ship`" class="text-sm font-medium">{{ t('business.deal.shipping') }}</span>
                        <SelectButton v-model="shippingMode" :options="shippingOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${uid}-ship`" />
                        <LabeledField v-if="shippingMode === 'PAID'" :label="t('business.deal.shippingFee')">
                            <template #default="{ id, describedBy }">
                                <InputNumber v-model="shippingFee" :input-id="id" :min="0" :max="1_000_000" :suffix="t('payment.wonSuffix')" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                    <div class="grid gap-4 sm:grid-cols-2">
                        <LabeledField :label="t('business.deal.endsOn')" :help="t('business.deal.endsOnHelp')">
                            <template #default="{ id, describedBy }">
                                <AppDatePicker v-model="endsOn" :input-id="id" :min="UtilBusiness.todayYmd()" :described-by="describedBy" />
                            </template>
                        </LabeledField>
                        <div class="flex flex-col gap-2">
                            <span :id="`${uid}-stock`" class="text-sm font-medium">{{ t('business.deal.stock') }}</span>
                            <SelectButton v-model="deal.stock" :options="stockOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${uid}-stock`" />
                        </div>
                    </div>
                </section>

                <!-- ③ 내용 -->
                <section v-else-if="step === 3" class="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,18rem)]">
                    <div class="flex min-w-0 flex-col gap-4">
                        <LabeledField :label="t('business.deal.dealTitle')" :hint="t('business.deal.titleHelp')" :error="errors.title">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="title" :id="id" :maxlength="80" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('business.deal.body')" :help="t('business.deal.bodyHelp')" :error="errors.body">
                            <template #default="{ id, describedBy, invalid }">
                                <Textarea v-model="deal.body" :id="id" rows="6" auto-resize :maxlength="5000" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                    <!-- 딜 미리보기 -->
                    <figure class="m-0 flex flex-col gap-1.5">
                        <figcaption class="text-xs font-medium text-muted-color">{{ t('business.deal.previewTitle') }}</figcaption>
                        <div class="flex flex-col gap-1.5 rounded-lg border border-hairline p-3">
                            <p class="m-0 flex flex-wrap items-center gap-1">
                                <PointShopTag tone="primary">{{ t('business.badge') }}</PointShopTag>
                                <PointShopTag tone="neutral">{{ t('business.ad.adMark') }}</PointShopTag>
                                <PointShopTag v-if="linkResult?.affiliate" tone="warn">{{ t('business.deal.affiliateTag') }}</PointShopTag>
                            </p>
                            <p class="m-0 line-clamp-2 break-words text-sm font-semibold">{{ title || t('business.deal.dealTitle') }}</p>
                            <p class="m-0 flex flex-wrap items-baseline gap-x-1.5 tabular-nums">
                                <span v-if="discount !== null" class="text-sm font-bold text-red-600 dark:text-red-400">{{ discount }}%</span>
                                <span class="text-base font-bold">{{ UtilBusinessMessages.won(t, deal.dealPrice ?? 0) }}</span>
                                <span v-if="deal.listPrice" class="text-xs text-muted-color line-through">{{ UtilBusinessMessages.won(t, deal.listPrice) }}</span>
                            </p>
                            <p class="m-0 text-xs text-muted-color">{{ deal.shopName }} · {{ shippingLabel }}</p>
                        </div>
                    </figure>
                </section>

                <!-- ④ 노출 -->
                <section v-else-if="step === 4" class="flex flex-col gap-4">
                    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                        <legend class="mb-2 text-base font-semibold">{{ t('business.deal.placementTitle') }}</legend>
                        <label
                            v-for="option in placementOptions"
                            :key="option.value"
                            class="flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 transition-colors"
                            :class="deal.placement === option.value ? 'border-primary bg-(--p-highlight-background)' : 'border-hairline hover:bg-(--p-content-hover-background)'"
                        >
                            <RadioButton :model-value="deal.placement" :value="option.value" :input-id="`${uid}-pl-${option.value}`" name="placement" class="mt-0.5" @update:model-value="pickPlacement" />
                            <span class="min-w-0 flex-1">
                                <span class="flex flex-wrap items-center justify-between gap-x-2 text-sm font-semibold">
                                    {{ t(`business.placement.${option.value}`) }}
                                    <span v-if="option.dailyPrice" class="text-xs font-semibold tabular-nums">{{ t('business.deal.placementPrice', { amount: UtilBusinessMessages.won(t, option.dailyPrice) }) }}</span>
                                </span>
                                <span class="mt-0.5 block text-xs text-muted-color">{{ t(`business.placementHelp.${option.value}`) }}</span>
                            </span>
                        </label>
                    </fieldset>
                    <section v-if="deal.placement !== 'NONE' && placement" class="flex flex-col gap-2">
                        <h2 class="m-0 text-sm font-semibold">{{ t('business.deal.placementPeriod') }}</h2>
                        <p class="m-0 text-xs text-muted-color">{{ t('business.ad.periodHelp', { max: placement.maxDays, lead: LEAD_DAYS }) }}</p>
                        <div v-if="availStatus === 'loading'" class="h-80 max-w-sm animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                        <p v-else-if="availStatus === 'error'" class="m-0 flex flex-wrap items-center gap-2 text-sm text-muted-color" role="alert">
                            {{ t('business.ad.availabilityFailed') }}
                            <Button type="button" :label="t('common.retry')" size="small" variant="text" class="p-0!" @click="loadAvailability" />
                        </p>
                        <BusinessPeriodCalendar v-else v-model="period" :days="availability" :min="minDay" :max="maxDay" />
                        <p v-if="placementDays > 0" class="m-0 text-sm font-semibold tabular-nums">{{ periodLabel }}</p>
                        <p v-if="errors.period" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errors.period }}</p>
                    </section>
                </section>

                <!-- ⑤ 결제·동의 -->
                <section v-else class="flex flex-col gap-4">
                    <BusinessPaymentMethodPicker v-model="paymentMethod" />
                    <BusinessPriceSummary :amount="estimate" :breakdown="breakdown" />
                    <BusinessAgreeList v-model="agreed" :items="agreeItems" :error="errors.agree" />
                </section>

                <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ formError }}</Message>

                <BusinessStepNav :step="step" :total="TOTAL" :can-prev="step > 1" :next-label="step === TOTAL ? t('business.deal.submit') : ''" :busy="submitBusy" @prev="step--" @next="next" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { BusinessAgreeItem } from '~/components/business/BusinessAgreeList.types';
import type { PeriodValue } from '~/components/business/BusinessPeriodCalendar.types';
import type { AvailabilityDay, BizDeal, BizPaymentMethod, BusinessProfile, DealLinkCheck, DealPlacement, DealProduct } from '~/composables/business/api/useBusinessApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

/**
 * 사업자 핫딜 신청 `/business/deals/new` — 로그인 + 승인된 사업자. 사업자 센터 홈의 핫딜 카드가 온다. ⚠ 목업 단계(`useBusinessApi`).
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
useSeoMeta({ title: () => t('business.deal.title'), description: () => t('business.deal.seoDescription'), robots: 'noindex' });

const TOTAL = 5;
const LEAD_DAYS = 30;
const AD_POLICY_VERSION = '2026-10-30';
const REFUND_POLICY_VERSION = '2026-10-30';

const uid = useId();
const api = useBusinessApi();
const toast = useAppToast();
const time = useDisplayTime();

const profile = ref<BusinessProfile | null>(null);
const dealProduct = ref<DealProduct | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const step = ref(1);

const deal = reactive<Omit<BizDeal, 'listPrice' | 'dealPrice'> & { listPrice: number | null; dealPrice: number | null }>({
    linkUrl: '',
    shopName: '',
    listPrice: null,
    dealPrice: null,
    shippingFee: 0,
    endsOn: null,
    stock: 'UNKNOWN',
    body: '',
    placement: 'NONE',
});
const title = ref('');
const shippingMode = ref<'FREE' | 'UNKNOWN' | 'PAID'>('FREE');
const shippingFee = ref<number | null>(null);
const endsOn = ref('');

const linkResult = ref<DealLinkCheck | null>(null);
/** 확인한 링크 — 칸을 고치면 다시 확인해야 한다 */
const checkedUrl = ref('');
const linkBusy = ref(false);
const linkError = ref('');
const linkChecked = computed(() => checkedUrl.value !== '' && checkedUrl.value === deal.linkUrl.trim());

const period = ref<PeriodValue>({ start: '', end: '' });
const availability = ref<AvailabilityDay[]>([]);
const availStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');

const paymentMethod = ref<BizPaymentMethod>('ONLINE');
const agreed = ref<Record<string, boolean>>({});
const tried = ref<Record<number, boolean>>({});
const formError = ref('');
const submitBusy = ref(false);

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        const [p, product] = await Promise.all([api.profile(), api.dealProduct()]);
        profile.value = p;
        dealProduct.value = product;
        status.value = 'ready';
    } catch (e) {
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}
onMounted(load);

const stepNames = computed(() => (['link', 'price', 'content', 'placement', 'payment'] as const).map((k) => t(`business.deal.steps.${k}`)));

async function checkLink(): Promise<void> {
    const url = deal.linkUrl.trim();
    if (!url || linkBusy.value || url === checkedUrl.value) return;
    linkError.value = '';
    if (!UtilBusiness.isHttpUrl(url)) {
        linkError.value = t('business.ad.linkInvalid');
        return;
    }
    linkBusy.value = true;
    try {
        const result = await api.checkDealLink(url);
        linkResult.value = result;
        checkedUrl.value = url;
        if (result.blocked) linkError.value = result.blockedReason ?? '';
        else if (result.shopName) deal.shopName = result.shopName;
    } catch (e) {
        linkError.value = e instanceof ApiError ? (e.field('linkUrl') ?? e.message) : t('common.failed');
    } finally {
        linkBusy.value = false;
    }
}

const discount = computed(() => UtilBusiness.discountRate(deal.listPrice, deal.dealPrice));
const shippingOptions = computed(() => [
    { value: 'FREE', label: t('business.deal.shippingFree') },
    { value: 'UNKNOWN', label: t('business.deal.shippingUnknown') },
    { value: 'PAID', label: t('business.deal.shippingPaid') },
]);
const stockOptions = computed(() => (['PLENTY', 'LIMITED', 'UNKNOWN'] as const).map((value) => ({ value, label: t(`business.deal.stockOption.${value}`) })));
const shippingLabel = computed(() => {
    if (shippingMode.value === 'FREE') return `${t('business.deal.shipping')} ${t('business.deal.shippingFree')}`;
    if (shippingMode.value === 'UNKNOWN') return `${t('business.deal.shipping')} ${t('business.deal.shippingUnknown')}`;
    return `${t('business.deal.shipping')} ${UtilBusinessMessages.won(t, shippingFee.value ?? 0)}`;
});

/* ── 노출 ── */
const placementOptions = computed(() => [
    { value: 'NONE' as DealPlacement, dailyPrice: 0 },
    ...(dealProduct.value?.placements ?? []).map((p) => ({ value: p.placement as DealPlacement, dailyPrice: p.dailyPrice })),
]);
const placement = computed(() => dealProduct.value?.placements.find((p) => p.placement === deal.placement) ?? null);
const minDay = computed(() => UtilBusiness.addDays(UtilBusiness.todayYmd(), 1));
const maxDay = computed(() => UtilBusiness.addDays(UtilBusiness.todayYmd(), LEAD_DAYS));
const placementDays = computed(() => (deal.placement !== 'NONE' && period.value.start && period.value.end ? UtilBusiness.daysBetween(period.value.start, period.value.end) : 0));
const periodLabel = computed(() =>
    placementDays.value > 0 ? UtilBusinessMessages.period(t, time.value.serviceDate(period.value.start), time.value.serviceDate(period.value.end), period.value.start, period.value.end) : '',
);
const periodHasFull = computed(() => placementDays.value > 0 && availability.value.some((d) => d.date >= period.value.start && d.date <= period.value.end && d.remaining <= 0));

function pickPlacement(value: DealPlacement): void {
    if (value === deal.placement) return;
    deal.placement = value;
    period.value = { start: '', end: '' };
    availability.value = [];
    if (value !== 'NONE') void loadAvailability();
}

async function loadAvailability(): Promise<void> {
    const slot = deal.placement;
    if (slot === 'NONE') return;
    availStatus.value = 'loading';
    try {
        availability.value = await api.availability(slot, minDay.value, maxDay.value);
        availStatus.value = 'ready';
    } catch {
        availStatus.value = 'error';
    }
}

/* ── 금액 ── */
const estimate = computed(() => {
    const post = dealProduct.value?.postPrice ?? 0;
    const supply = post + (placement.value?.dailyPrice ?? 0) * placementDays.value;
    return UtilBusiness.estimate(supply, 1);
});
const breakdown = computed(() => {
    const lines = [t('business.price.perPost', { amount: UtilBusinessMessages.won(t, dealProduct.value?.postPrice ?? 0) })];
    if (placement.value && placementDays.value) {
        lines.push(`${t(`business.placement.${deal.placement}`)} · ${t('business.price.line', { days: placementDays.value, amount: UtilBusinessMessages.won(t, placement.value.dailyPrice) })}`);
    }
    return lines;
});

const agreeItems = computed<BusinessAgreeItem[]>(() => [
    { key: 'adPolicy', label: t('business.agree.adPolicy'), detail: t('business.agree.adPolicyDetail'), version: AD_POLICY_VERSION },
    { key: 'refundPolicy', label: t('business.agree.refundPolicy'), detail: t('business.agree.refundPolicyDetail'), version: REFUND_POLICY_VERSION },
]);

/* ── 검사 ── */
const errors = computed(() => {
    const out: Record<string, string> = {};
    if (linkError.value) out.link = linkError.value;
    else if (tried.value[1] && !deal.linkUrl.trim()) out.link = t('business.deal.linkRequired');
    else if (tried.value[1] && !linkChecked.value) out.link = t('business.deal.linkNeedCheck');
    if (tried.value[1] && linkChecked.value && !linkResult.value?.blocked && !deal.shopName.trim()) out.shop = t('business.deal.shopRequired');
    if (tried.value[2] && !deal.dealPrice) out.dealPrice = t('business.deal.dealPriceRequired');
    else if (deal.listPrice && deal.dealPrice && deal.dealPrice >= deal.listPrice) out.dealPrice = t('business.deal.priceInvalid');
    if (tried.value[3] && title.value.trim().length < 5) out.title = t('business.deal.titleRequired');
    if (tried.value[3] && !deal.body.trim()) out.body = t('business.deal.bodyRequired');
    if (deal.placement !== 'NONE') {
        if (tried.value[4] && !placementDays.value) out.period = t('business.deal.placementPeriodRequired');
        else if (placement.value && placementDays.value > placement.value.maxDays) out.period = t('business.ad.periodTooLong', { max: placement.value.maxDays });
        else if (periodHasFull.value) out.period = t('business.ad.periodFull');
    }
    if (tried.value[5] && !agreeItems.value.every((i) => agreed.value[i.key])) out.agree = t('business.agree.required');
    return out;
});

const STEP_FIELDS: Record<number, string[]> = { 1: ['link', 'shop'], 2: ['dealPrice'], 3: ['title', 'body'], 4: ['period'], 5: ['agree'] };

async function next(): Promise<void> {
    if (submitBusy.value) return;
    if (step.value === 1 && !linkChecked.value) await checkLink();
    tried.value = { ...tried.value, [step.value]: true };
    if (STEP_FIELDS[step.value]!.some((k) => errors.value[k])) return;
    formError.value = '';
    if (step.value < TOTAL) {
        step.value++;
        return;
    }
    await submit();
}

async function submit(): Promise<void> {
    submitBusy.value = true;
    try {
        const body: BizDeal = {
            ...toRaw(deal),
            linkUrl: deal.linkUrl.trim(),
            shopName: deal.shopName.trim(),
            dealPrice: deal.dealPrice ?? 0,
            shippingFee: shippingMode.value === 'FREE' ? 0 : shippingMode.value === 'UNKNOWN' ? null : (shippingFee.value ?? 0),
            endsOn: endsOn.value || null,
            body: deal.body.trim(),
        };
        const created = await api.submitDeal({
            title: title.value.trim(),
            deal: body,
            placementStartDate: deal.placement !== 'NONE' ? period.value.start : null,
            placementEndDate: deal.placement !== 'NONE' ? period.value.end : null,
            paymentMethod: paymentMethod.value,
            agreedAdPolicyVersion: AD_POLICY_VERSION,
            agreedRefundPolicyVersion: REFUND_POLICY_VERSION,
        });
        toast.success(t('business.deal.submitted'));
        await navigateTo(UtilBusiness.applicationPath(created.id));
    } catch (e) {
        formError.value = e instanceof Error ? e.message : t('business.deal.failed');
        if (e instanceof ApiError && e.code === 'BIZ_SLOT_FULL') {
            step.value = 4;
            void loadAvailability();
        }
    } finally {
        submitBusy.value = false;
    }
}
</script>
