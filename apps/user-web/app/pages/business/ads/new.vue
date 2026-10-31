<template>
    <!--
        광고 신청 `/business/ads/new` — 사전 심사 신청(★-1, 02 §7-6). Stepper 5단계:
        ① 종류 카드(어디에 나오는지 그림 · 남은 칸 요약 · 하루 단가, 받지 않는 종류는 흐리게) ② 기간(달력 — 날짜별 신청 가능/꽉 참, 최대 기간 안내)
        ③ 소재(배너 = 이미지·링크·대체 글 / 한 줄 = 문구·링크) + 광고 모습 미리보기(「광고」 표시) ④ 결제 방식(온라인/수동) + 예상 금액
        ⑤ 확인 · 광고 정책·환불 규정 동의 → [심사 신청]. 지금 결제하지 않는다 — 승인되면 결제 요청이 온다.
        승인되지 않았거나 재심사 중이면 막힘 안내(`BusinessGateNotice`). 판정(한도·기간·소재)은 서버가 다시 한다.
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="business-ad-new">
        <div class="flex items-center gap-2">
            <Button as="router-link" :to="UtilBusiness.CENTER_PATH" icon="pi pi-angle-left" severity="secondary" variant="text" rounded :aria-label="t('business.home.title')" />
            <h1 class="m-0 text-lg font-bold">{{ t('business.ad.title') }}</h1>
        </div>

        <div v-if="status === 'loading'" class="h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />
        <ErrorState v-else-if="status === 'error' || !profile" :title="t('business.home.loadFailed')" :description="loadError" retry @retry="load" />
        <BusinessGateNotice v-else-if="profile.status !== 'APPROVED' || profile.reReview" :status="profile.status" :re-review="profile.reReview" />

        <template v-else>
            <BusinessStepHeader :steps="stepNames" :current="step" />

            <form class="panel-card flex flex-col gap-4 p-4" novalidate @submit.prevent="next">
                <!-- ① 종류 -->
                <fieldset v-if="step === 1" class="m-0 flex flex-col gap-3 border-0 p-0">
                    <legend class="mb-3 text-base font-semibold">{{ t('business.ad.typeTitle') }}</legend>
                    <div class="grid gap-3 sm:grid-cols-2">
                        <label
                            v-for="p in products"
                            :key="p.type"
                            class="flex gap-3 rounded-lg border p-3 transition-colors has-[input:focus-visible]:outline-2 has-[input:focus-visible]:outline-primary"
                            :class="[
                                !p.accepting ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
                                adType === p.type ? 'border-primary bg-(--p-highlight-background)' : 'border-hairline hover:bg-(--p-content-hover-background)',
                            ]"
                            :data-testid="`business-ad-type-${p.type}`"
                        >
                            <RadioButton v-model="adType" :value="p.type" :input-id="`${uid}-type-${p.type}`" name="ad-type" :disabled="!p.accepting" class="sr-only" />
                            <!-- 어디에 나오는지 — 페이지 틀 속 자리 -->
                            <span class="relative h-16 w-20 shrink-0 rounded border border-hairline-strong bg-card" aria-hidden="true">
                                <span class="absolute inset-x-1.5 top-1.5 h-1 rounded-sm bg-surface-300 dark:bg-surface-600" />
                                <span class="absolute bottom-1.5 left-1.5 top-4 w-11 rounded-sm bg-surface-200 dark:bg-surface-700" />
                                <span class="absolute bg-primary/70" :class="SKETCH[p.type]" />
                            </span>
                            <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                                <span class="flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                                    {{ t(`business.adType.${p.type}`) }}
                                    <PointShopTag v-if="!p.accepting" tone="neutral">{{ t('business.ad.notAccepting') }}</PointShopTag>
                                </span>
                                <span class="text-xs text-muted-color">{{ t(`business.adTypeWhere.${p.type}`) }} · {{ t(`business.adTypeSize.${p.type}`) }}</span>
                                <span class="text-xs tabular-nums text-muted-color">{{ t('business.ad.openDays', { count: p.openDays }) }} · {{ t('business.ad.capacity', { count: p.capacity }) }}</span>
                                <span class="text-sm font-bold tabular-nums">{{ t('business.price.daily', { amount: UtilBusinessMessages.won(t, p.dailyPrice) }) }}</span>
                            </span>
                        </label>
                    </div>
                    <p v-if="errors.type" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errors.type }}</p>
                </fieldset>

                <!-- ② 기간 -->
                <section v-else-if="step === 2 && product" class="flex flex-col gap-3">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.ad.periodTitle') }}</h2>
                    <p class="m-0 text-xs text-muted-color">{{ t('business.ad.periodHelp', { max: product.maxDays, lead: product.maxLeadDays }) }}</p>
                    <div v-if="availStatus === 'loading'" class="h-80 max-w-sm animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                    <p v-else-if="availStatus === 'error'" class="m-0 flex flex-wrap items-center gap-2 text-sm text-muted-color" role="alert">
                        {{ t('business.ad.availabilityFailed') }}
                        <Button type="button" :label="t('common.retry')" size="small" variant="text" class="p-0!" @click="loadAvailability" />
                    </p>
                    <BusinessPeriodCalendar v-else v-model="period" :days="availability" :min="minDay" :max="maxDay" />
                    <p v-if="days > 0" class="m-0 text-sm font-semibold tabular-nums">{{ periodLabel }}</p>
                    <p v-if="errors.period" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errors.period }}</p>
                </section>

                <!-- ③ 소재 -->
                <section v-else-if="step === 3 && adType" class="flex flex-col gap-4">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.ad.creativeTitle') }}</h2>
                    <div class="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                        <div class="flex min-w-0 flex-col gap-4">
                            <LabeledField v-if="adType !== 'TEXT_LINE'" :label="t('business.ad.image')" :help="t('business.ad.imageHelp', { size: t(`business.adTypeSize.${adType}`) })" :error="errors.image">
                                <template #default="{ id, describedBy }">
                                    <BusinessFilePicker
                                        :file="image"
                                        :accept="UtilBusiness.IMAGE_ACCEPT"
                                        :choose-label="t('business.file.choose')"
                                        :busy="uploadBusy"
                                        :input-id="id"
                                        :described-by="describedBy"
                                        :type-error="t('business.file.typeError')"
                                        :size-error="t('business.file.sizeError')"
                                        @select="uploadImage"
                                        @remove="image = null"
                                    />
                                </template>
                            </LabeledField>
                            <LabeledField v-else :label="t('business.ad.text')" :hint="`${creative.text.length}/40`" :help="t('business.ad.textHelp')" :error="errors.text">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText v-model="creative.text" :id="id" :maxlength="40" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('business.ad.link')" :error="errors.link">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText v-model="creative.linkUrl" :id="id" type="url" inputmode="url" :placeholder="LINK_PLACEHOLDER" :maxlength="500" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                </template>
                            </LabeledField>
                            <LabeledField v-if="adType !== 'TEXT_LINE'" :label="t('business.ad.alt')" :hint="`${creative.altText.length}/80`" :help="t('business.ad.altHelp')" :error="errors.alt">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText v-model="creative.altText" :id="id" :maxlength="80" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                </template>
                            </LabeledField>
                        </div>
                        <BusinessAdPreview :ad-type="adType" :image-url="image?.previewUrl ?? null" :link-url="linkOk ? creative.linkUrl.trim() : ''" :alt-text="creative.altText" :text="creative.text" />
                    </div>
                </section>

                <!-- ④ 결제 방식 -->
                <section v-else-if="step === 4" class="flex flex-col gap-4">
                    <BusinessPaymentMethodPicker v-model="paymentMethod" />
                    <BusinessPriceSummary :amount="estimate" :breakdown="breakdown" />
                </section>

                <!-- ⑤ 확인·동의 -->
                <section v-else-if="adType && product" class="flex flex-col gap-4">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.ad.confirmTitle') }}</h2>
                    <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                        <dt class="text-muted-color">{{ t('business.detail.kind') }}</dt>
                        <dd class="m-0">{{ t(`business.adType.${adType}`) }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.period') }}</dt>
                        <dd class="m-0 tabular-nums">{{ periodLabel }}</dd>
                        <dt class="text-muted-color">{{ t('business.ad.link') }}</dt>
                        <dd class="m-0 break-all">{{ creative.linkUrl }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.paymentMethod') }}</dt>
                        <dd class="m-0">{{ t(`business.paymentMethod.${paymentMethod}`) }}</dd>
                    </dl>
                    <BusinessAdPreview :ad-type="adType" :image-url="image?.previewUrl ?? null" :link-url="creative.linkUrl.trim()" :alt-text="creative.altText" :text="creative.text" />
                    <BusinessPriceSummary :amount="estimate" :breakdown="breakdown" />
                    <BusinessAgreeList v-model="agreed" :items="agreeItems" :error="errors.agree" />
                    <p class="m-0 flex items-start gap-1.5 text-xs font-medium text-primary">
                        <i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('business.ad.noPayNow') }}
                    </p>
                </section>

                <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ formError }}</Message>

                <BusinessStepNav :step="step" :total="TOTAL" :can-prev="step > 1" :next-label="step === TOTAL ? t('business.ad.submit') : ''" :busy="submitBusy" @prev="step--" @next="next" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { BusinessAgreeItem } from '~/components/business/BusinessAgreeList.types';
import type { PeriodValue } from '~/components/business/BusinessPeriodCalendar.types';
import type { AdProduct, AdType, AvailabilityDay, BizPaymentMethod, BusinessFile, BusinessProfile } from '~/composables/business/api/useBusinessApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

/**
 * 광고 신청 `/business/ads/new` — 로그인 + 승인된 사업자. 사업자 센터 홈의 광고 카드가 온다. ⚠ 목업 단계(`useBusinessApi`).
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
useSeoMeta({ title: () => t('business.ad.title'), description: () => t('business.ad.seoDescription'), robots: 'noindex' });

const TOTAL = 5;
const LINK_PLACEHOLDER = 'https://';
const AD_POLICY_VERSION = '2026-10-30';
const REFUND_POLICY_VERSION = '2026-10-30';

/** 종류 카드 그림 — 페이지 틀 속 광고 자리(`absolute` 위치) */
const SKETCH: Record<AdType, string> = {
    WIDE: 'inset-x-1.5 top-3.5 h-3 rounded-sm',
    STRIP: 'left-1.5 right-1.5 top-8 h-1 rounded-sm',
    SQUARE: 'right-1.5 top-4 h-5 w-5 rounded-sm',
    SMALL: 'right-1.5 bottom-1.5 h-2 w-5 rounded-sm',
    TEXT_LINE: 'left-1.5 top-[0.9rem] h-0.5 w-11 rounded-full',
};

const uid = useId();
const api = useBusinessApi();
const toast = useAppToast();

const profile = ref<BusinessProfile | null>(null);
const products = ref<AdProduct[]>([]);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const step = ref(1);

const adType = ref<AdType | null>(null);
const period = ref<PeriodValue>({ start: '', end: '' });
const availability = ref<AvailabilityDay[]>([]);
const availStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
/** 남은 칸을 받아 둔 종류 — 종류를 바꾸면 다시 받는다 */
let availFor: AdType | null = null;

const image = ref<BusinessFile | null>(null);
const uploadBusy = ref(false);
const creative = reactive({ linkUrl: '', altText: '', text: '' });
const paymentMethod = ref<BizPaymentMethod>('ONLINE');
const agreed = ref<Record<string, boolean>>({});
const tried = ref<Record<number, boolean>>({});
const formError = ref('');
const submitBusy = ref(false);

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        const [p, list] = await Promise.all([api.profile(), api.adProducts()]);
        profile.value = p;
        products.value = list;
        status.value = 'ready';
    } catch (e) {
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}
onMounted(load);

const product = computed(() => products.value.find((p) => p.type === adType.value) ?? null);
const minDay = computed(() => UtilBusiness.addDays(UtilBusiness.todayYmd(), 1));
const maxDay = computed(() => UtilBusiness.addDays(UtilBusiness.todayYmd(), product.value?.maxLeadDays ?? 60));

async function loadAvailability(): Promise<void> {
    const type = adType.value;
    if (!type) return;
    availStatus.value = 'loading';
    try {
        availability.value = await api.availability(type, minDay.value, maxDay.value);
        availFor = type;
        availStatus.value = 'ready';
    } catch {
        availStatus.value = 'error';
    }
}

const stepNames = computed(() => (['type', 'period', 'creative', 'payment', 'confirm'] as const).map((k) => t(`business.ad.steps.${k}`)));

const days = computed(() => (period.value.start && period.value.end ? UtilBusiness.daysBetween(period.value.start, period.value.end) : 0));
const time = useDisplayTime();
const periodLabel = computed(() =>
    days.value > 0 ? UtilBusinessMessages.period(t, time.value.serviceDate(period.value.start), time.value.serviceDate(period.value.end), period.value.start, period.value.end) : '',
);
const estimate = computed(() => UtilBusiness.estimate(product.value?.dailyPrice ?? 0, days.value));
const breakdown = computed(() => (product.value ? [t('business.price.line', { days: days.value, amount: UtilBusinessMessages.won(t, product.value.dailyPrice) })] : []));
const linkOk = computed(() => UtilBusiness.isHttpUrl(creative.linkUrl));

/** 고른 기간 안에 꽉 찬 날이 있나 */
const periodHasFull = computed(() => {
    if (!days.value) return false;
    return availability.value.some((d) => d.date >= period.value.start && d.date <= period.value.end && d.remaining <= 0);
});

const agreeItems = computed<BusinessAgreeItem[]>(() => [
    { key: 'adPolicy', label: t('business.agree.adPolicy'), detail: t('business.agree.adPolicyDetail'), version: AD_POLICY_VERSION },
    { key: 'refundPolicy', label: t('business.agree.refundPolicy'), detail: t('business.agree.refundPolicyDetail'), version: REFUND_POLICY_VERSION },
]);

const errors = computed(() => {
    const out: Record<string, string> = {};
    if (tried.value[1] && (!adType.value || !product.value?.accepting)) out.type = t('business.ad.typeRequired');
    if (tried.value[2] || periodHasFull.value) {
        if (tried.value[2] && !days.value) out.period = t('business.ad.periodRequired');
        else if (product.value && days.value > product.value.maxDays) out.period = t('business.ad.periodTooLong', { max: product.value.maxDays });
        else if (periodHasFull.value) out.period = t('business.ad.periodFull');
    }
    if (tried.value[3]) {
        if (adType.value === 'TEXT_LINE') {
            if (!creative.text.trim()) out.text = t('business.ad.textRequired');
        } else {
            if (!image.value) out.image = t('business.ad.imageRequired');
            if (!creative.altText.trim()) out.alt = t('business.ad.altRequired');
        }
        if (!linkOk.value) out.link = t('business.ad.linkInvalid');
    }
    if (tried.value[5] && !agreeItems.value.every((i) => agreed.value[i.key])) out.agree = t('business.agree.required');
    return out;
});

const STEP_FIELDS: Record<number, string[]> = { 1: ['type'], 2: ['period'], 3: ['text', 'image', 'alt', 'link'], 4: [], 5: ['agree'] };

async function uploadImage(file: File): Promise<void> {
    uploadBusy.value = true;
    formError.value = '';
    try {
        image.value = await api.uploadFile(file);
    } catch (e) {
        formError.value = e instanceof Error ? e.message : t('common.failed');
    } finally {
        uploadBusy.value = false;
    }
}

async function next(): Promise<void> {
    if (submitBusy.value) return;
    tried.value = { ...tried.value, [step.value]: true };
    if (STEP_FIELDS[step.value]!.some((k) => errors.value[k])) return;
    formError.value = '';
    if (step.value === 1 && adType.value !== availFor) {
        // 종류를 바꿨으면 기간을 비우고 그 종류의 남은 칸을 받는다
        period.value = { start: '', end: '' };
        void loadAvailability();
    }
    if (step.value < TOTAL) {
        step.value++;
        return;
    }
    await submit();
}

async function submit(): Promise<void> {
    const type = adType.value;
    if (!type) return;
    submitBusy.value = true;
    try {
        const created = await api.submitAd({
            adType: type,
            startDate: period.value.start,
            endDate: period.value.end,
            imageFileId: type === 'TEXT_LINE' ? null : (image.value?.fileId ?? null),
            linkUrl: creative.linkUrl.trim(),
            altText: type === 'TEXT_LINE' ? null : creative.altText.trim(),
            text: type === 'TEXT_LINE' ? creative.text.trim() : null,
            paymentMethod: paymentMethod.value,
            agreedAdPolicyVersion: AD_POLICY_VERSION,
            agreedRefundPolicyVersion: REFUND_POLICY_VERSION,
        });
        toast.success(t('business.ad.submitted'));
        await navigateTo(UtilBusiness.applicationPath(created.id));
    } catch (e) {
        formError.value = e instanceof Error ? e.message : t('business.ad.failed');
        // 자리가 찼으면(409 BIZ_SLOT_FULL) 기간 단계로 돌아가 남은 칸을 다시 받는다
        if (e instanceof ApiError && e.code === 'BIZ_SLOT_FULL') {
            step.value = 2;
            void loadAvailability();
        }
    } finally {
        submitBusy.value = false;
    }
}
</script>
