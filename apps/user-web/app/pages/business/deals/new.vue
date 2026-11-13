<template>
    <!--
        사업자 핫딜 신청 `/business/deals/new` — 사전 심사 신청(★-1, 03 §7-2). Stepper 5단계:
        ① 상품 링크 붙여넣기 → [확인](또는 칸을 벗어나면) → 쇼핑몰 자동 표시(고칠 수 있음)·제휴 표시 자동·단축 URL 차단
        ② 가격(정상가·딜가 → 할인율 미리보기)·배송비·마감·재고(재고 있음·얼마 안 남음·모름 — 회원 핫딜과 같은 값)
        ③ 게시판(하나면 자동으로 고르고 감춤)·말머리(게시판에 있으면 필수) · 상품 이미지 한 장(선택 — 광고 이미지 올리기 API, 없으면 기본 아이콘) · 제목·본문 + 딜 미리보기(이미지·말머리)
        ④ 노출 추가(없음 / 핫딜 게시판 상단 / 말머리 상단 — 고른 게시판·말머리 이름으로 안내, 말머리 없는 게시판이면 말머리 상단을 못 고름 — 고르면 노출 기간 달력)
        ⑤ 결제 방식 + 예상 금액 + 게시 정책 4줄(시작 시각에 게시 · 종료 요청 받음 · 끝나도 환불 없음 · 게시 뒤 고칠 수 없음) + 동의 → [심사 신청].
        「사업자 핫딜은 심사 뒤 결제하면 게시돼요」. 노출을 고르면 같은 신청 건에 묶어 한 번에 결제한다(목업 가정).
    -->
    <div class="content-card flex flex-col gap-4" data-testid="business-deal-new">
        <header class="flex items-center gap-2">
            <Button as="router-link" :to="UtilBusiness.CENTER_PATH" icon="pi pi-angle-left" severity="secondary" variant="text" rounded :aria-label="t('business.home.title')" />
            <h1 class="m-0 text-lg font-bold">{{ t('business.deal.title') }}</h1>
        </header>

        <div v-if="status === 'loading'" class="h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />
        <ErrorState v-else-if="status === 'error' || !profile || !dealProduct" :title="t('business.home.loadFailed')" :description="loadError" retry @retry="load" />
        <BusinessGateNotice v-else-if="profile.status !== 'APPROVED' || profile.reReview || serviceSuspension" :status="profile.status" :re-review="profile.reReview" :service-suspension="serviceSuspension" />

        <!-- 글 게시 받기 꺼짐(V121 — 게시 기능이 생길 때까지 닫음) — 직접 주소로 와도 폼 대신 안내 -->
        <section v-else-if="!dealProduct.accepting" class="panel-card flex flex-col gap-4 p-5" data-testid="business-deal-closed">
            <div class="flex items-start gap-3">
                <span class="grid size-11 shrink-0 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-clock text-lg" /></span>
                <div class="min-w-0">
                    <h2 class="m-0 text-base font-bold">{{ t('business.deal.closedTitle') }}</h2>
                    <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('business.deal.closedText') }}</p>
                </div>
            </div>
            <div>
                <Button as="router-link" :to="UtilBusiness.CENTER_PATH" :label="t('business.deal.closedBack')" icon="pi pi-angle-left" severity="secondary" variant="outlined" size="small" />
            </div>
        </section>

        <template v-else>
            <Message severity="info" size="small" :closable="false">{{ t('business.deal.intro') }}</Message>
            <!-- 게시 1건 값 예약 변경(⚠ 가정) — 글 값은 ⑤ 예상 금액에만 보여서 여기서 미리 알린다 -->
            <BusinessPriceChangeNote v-if="postNote" :note="postNote" />
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
                        <!-- 게시판·말머리 — 게시판이 하나면 자동으로 고르고 칸을 감춘다, 말머리가 없는 게시판이면 말머리 칸도 감춘다 -->
                        <div v-if="boards.length > 1 || boardPrefixes.length" class="grid gap-4 sm:grid-cols-2">
                            <LabeledField v-if="boards.length > 1" :label="t('business.deal.board')" :help="t('business.deal.boardHelp')" :error="errors.board">
                                <template #default="{ id, describedBy, invalid }">
                                    <Select
                                        :model-value="deal.boardId || null"
                                        :options="boards"
                                        option-label="name"
                                        option-value="id"
                                        :input-id="id"
                                        :placeholder="t('business.deal.boardPlaceholder')"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        fluid
                                        data-testid="business-deal-board"
                                        @update:model-value="pickBoard"
                                    />
                                </template>
                            </LabeledField>
                            <LabeledField v-if="boardPrefixes.length" :label="t('business.deal.prefixOptional')" :help="t('business.deal.prefixHelp')" :error="errors.prefix">
                                <template #default="{ id, describedBy, invalid }">
                                    <Select
                                        v-model="deal.prefixId"
                                        :options="boardPrefixes"
                                        option-label="name"
                                        option-value="id"
                                        :input-id="id"
                                        :placeholder="t('business.deal.prefixNone')"
                                        show-clear
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        fluid
                                        data-testid="business-deal-prefix"
                                    />
                                </template>
                            </LabeledField>
                        </div>
                        <LabeledField :label="t('business.deal.image')" :help="t('business.deal.imageHelp')">
                            <template #default="{ id, describedBy }">
                                <BusinessFilePicker
                                    :file="image"
                                    :accept="UtilBusiness.IMAGE_ACCEPT"
                                    :choose-label="t('business.file.choose')"
                                    :busy="imageBusy"
                                    :input-id="id"
                                    :described-by="describedBy"
                                    :type-error="t('business.file.typeError')"
                                    :size-error="t('business.file.sizeError')"
                                    @select="uploadImage"
                                    @remove="image = null"
                                />
                            </template>
                        </LabeledField>
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
                            <HotdealThumb :url="image?.previewUrl ?? null" class="aspect-square w-24" />
                            <p class="m-0 flex flex-wrap items-center gap-1">
                                <PointShopTag tone="primary">{{ t('business.badge') }}</PointShopTag>
                                <PointShopTag tone="neutral">{{ t('business.ad.adMark') }}</PointShopTag>
                                <PointShopTag v-if="linkResult?.affiliate" tone="warn">{{ t('business.deal.affiliateTag') }}</PointShopTag>
                                <PointShopTag v-if="pickedPrefix" tone="neutral">{{ pickedPrefix.name }}</PointShopTag>
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
                            class="flex items-start gap-3 rounded-lg border p-3.5 transition-colors"
                            :class="[
                                !option.accepting ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
                                deal.placement === option.value ? 'border-primary bg-(--p-highlight-background)' : 'border-hairline hover:bg-(--p-content-hover-background)',
                            ]"
                        >
                            <RadioButton
                                :model-value="deal.placement"
                                :value="option.value"
                                :input-id="`${uid}-pl-${option.value}`"
                                name="placement"
                                :disabled="!option.accepting"
                                class="mt-0.5"
                                @update:model-value="pickPlacement"
                            />
                            <span class="min-w-0 flex-1">
                                <span class="flex flex-wrap items-center justify-between gap-x-2 text-sm font-semibold">
                                    <span class="flex flex-wrap items-center gap-1.5">
                                        {{ t(`business.placement.${option.value}`) }}
                                        <PointShopTag v-if="option.closed" tone="neutral">{{ t('business.ad.notAccepting') }}</PointShopTag>
                                    </span>
                                    <span v-if="option.dailyPrice" class="text-xs font-semibold tabular-nums">{{ t('business.deal.placementPrice', { amount: UtilBusinessMessages.won(t, option.dailyPrice) }) }}</span>
                                </span>
                                <span class="mt-0.5 block text-xs text-muted-color">{{ option.help }}</span>
                                <BusinessPriceChangeNote v-if="option.note" :note="option.note" class="mt-1" />
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
                    <BusinessPaymentMethodPicker v-model="paymentMethod" :online-available="dealProduct.onlinePayment !== false" />
                    <BusinessPriceSummary :amount="estimate" :breakdown="breakdown" />
                    <BusinessPriceChangeNotice v-if="priceChange" :summary="priceChange" />
                    <!-- 게시 정책 — 동의 바로 위(사용자 확정 2026-11-02) -->
                    <section class="flex flex-col gap-2 rounded-lg border border-hairline p-3.5" :aria-labelledby="`${uid}-policy`" data-testid="business-deal-policy">
                        <h2 :id="`${uid}-policy`" class="m-0 text-sm font-semibold">{{ t('business.deal.policyTitle') }}</h2>
                        <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-sm leading-relaxed">
                            <li v-for="line in policyLines" :key="line.icon" class="flex items-start gap-2">
                                <i :class="[line.icon, 'mt-1 text-xs text-muted-color']" aria-hidden="true" />
                                <span class="min-w-0">{{ line.text }}</span>
                            </li>
                        </ul>
                    </section>
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
import type {
    AvailabilityDay,
    BizDeal,
    BizDealStock,
    BizPaymentMethod,
    BizScheduledPriceChange,
    BusinessFile,
    BusinessProfile,
    DealLinkCheck,
    DealPlacement,
    DealPlacementProduct,
    DealProduct,
} from '~/composables/business/api/useBusinessApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages, { type BizPriceChangeItem, type BizPriceChangeNote } from '~/utils/util-business-messages';

/**
 * 사업자 핫딜 신청 `/business/deals/new` — 로그인 + 승인된 사업자. 사업자 센터 홈의 핫딜 카드가 온다. ⚠ 목업 단계(`useBusinessApi`).
 * 요금표 예약 변경(`scheduledChange` — 계약 `ad-price-rules-contract.md`): 맨 위 안내 아래 글 1건 값, ④ 노출 자리 줄, ⑤ 예상 금액 아래 「지금 신청하면 지금 가격」.
 * 적용 시각이 지나면 안내만 숨긴다(견적은 서버가 신청 시각 요금표로 정한다).
 */
definePageMeta({ middleware: 'auth', meMenu: true });

const { t } = useI18n();
useSeoMeta({ title: () => t('business.deal.title'), description: () => t('business.deal.seoDescription'), robots: 'noindex' });

const TOTAL = 5;
const LEAD_DAYS = 30;
const AD_POLICY_VERSION = '2026-10-30';
/** 환불 규정 버전 — 서버 `BizApplicationMemberService.REFUND_POLICY_VERSION` 과 같아야 한다(다르면 400 `fields.agreedRefundPolicyVersion`). 2026-11-02: 진행 중 중단 수수료 */
const REFUND_POLICY_VERSION = '2026-11-02';

const uid = useId();
const api = useBusinessApi();
const toast = useAppToast();
const time = useDisplayTime();

const profile = ref<BusinessProfile | null>(null);
/** 신원은 승인인데 핫딜 서비스만 정지 — 막힘 안내(`services[]`, 서버는 409 `BUSINESS_SERVICE_SUSPENDED`) */
const serviceSuspension = computed(() => (profile.value ? UtilBusiness.serviceSuspension(profile.value, 'HOTDEAL') : null));
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
    boardId: '',
    prefixId: null,
    imageFileId: null,
});
const image = ref<BusinessFile | null>(null);
const imageBusy = ref(false);
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
        // 받는 게시판이 하나면 자동으로 고른다(칸은 감춘다)
        const list = product.boards ?? [];
        if (list.length === 1 && !deal.boardId) deal.boardId = list[0]!.id;
        status.value = 'ready';
    } catch (e) {
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}
onMounted(load);

/** 진행 중 중단의 중도 해지 수수료율 — 동의 문구에 넣는다(받지 못하면 비율 없는 문구) */
const stopFeeRate = ref<number | null>(null);
onMounted(async () => {
    try {
        stopFeeRate.value = (await api.refundPolicy()).stopFeeRate;
    } catch {
        stopFeeRate.value = null;
    }
});

/** 신원만 다시 읽는다(서비스 정지 409 뒤) — 못 읽으면 지금 화면을 그대로 둔다(서버 문구가 이미 폼 오류로 보인다) */
async function reloadProfile(): Promise<void> {
    try {
        profile.value = await api.profile();
    } catch {
        // 그대로 둔다
    }
}

async function reloadDealProduct(): Promise<void> {
    try {
        dealProduct.value = await api.dealProduct();
    } catch {
        // 다시 읽지 못하면 닫힌 것으로 본다 — 서버가 이미 받지 않는다고 답했다
        if (dealProduct.value) dealProduct.value = { ...dealProduct.value, accepting: false };
        return;
    }
    if (!dealProduct.value.accepting) return;
    if (deal.placement !== 'NONE' && !placement.value?.accepting) {
        pickPlacement('NONE');
        step.value = 4;
    }
}

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
const STOCKS: readonly BizDealStock[] = ['IN_STOCK', 'LOW', 'UNKNOWN'];
const stockOptions = computed(() => STOCKS.map((value) => ({ value, label: t(`business.deal.stockOption.${value}`) })));

/* ── 게시판·말머리·이미지 ── */
const boards = computed(() => dealProduct.value?.boards ?? []);
const pickedBoard = computed(() => boards.value.find((b) => b.id === deal.boardId) ?? null);
const boardPrefixes = computed(() => pickedBoard.value?.prefixes ?? []);
const pickedPrefix = computed(() => boardPrefixes.value.find((p) => p.id === deal.prefixId) ?? null);

/** 게시판을 바꾸면 말머리를 비우고, 말머리 없는 게시판이면 말머리 상단 노출도 거둔다 */
function pickBoard(id: string | null): void {
    if (!id || id === deal.boardId) return;
    deal.boardId = id;
    deal.prefixId = null;
    if (deal.placement === 'CATEGORY_TOP' && !boardPrefixes.value.length) pickPlacement('NONE');
}

async function uploadImage(file: File): Promise<void> {
    imageBusy.value = true;
    formError.value = '';
    try {
        image.value = await api.uploadFile(file);
    } catch (e) {
        formError.value = e instanceof Error ? e.message : t('common.failed');
    } finally {
        imageBusy.value = false;
    }
}
const shippingLabel = computed(() => {
    if (shippingMode.value === 'FREE') return `${t('business.deal.shipping')} ${t('business.deal.shippingFree')}`;
    if (shippingMode.value === 'UNKNOWN') return `${t('business.deal.shipping')} ${t('business.deal.shippingUnknown')}`;
    return `${t('business.deal.shipping')} ${UtilBusinessMessages.won(t, shippingFee.value ?? 0)}`;
});

/* ── 노출 ── */
/** 말머리 상단은 고른 말머리 목록 위에 나온다 — 게시판을 아는데(받는 게시판 목록이 있는데) 말머리가 없으면 고를 수 없다 */
const categoryBlocked = computed(() => pickedBoard.value !== null && !boardPrefixes.value.length);
/** 노출 자리 안내 — 고른 게시판·말머리 이름으로(모르면 일반 안내) */
function placementHelp(value: DealPlacement): string {
    const board = pickedBoard.value?.name;
    if (value === 'HOME_TOP' && board) return t('business.deal.placementHelpTop', { board });
    if (value === 'CATEGORY_TOP' && categoryBlocked.value) return t('business.deal.placementNeedPrefix');
    if (value === 'CATEGORY_TOP' && board && pickedPrefix.value) return t('business.deal.placementHelpCategory', { board, prefix: pickedPrefix.value.name });
    return t(`business.placementHelp.${value}`);
}

/* ── 요금 예약 변경 안내(`scheduledChange`) ── */
const now = useNowTicker();
/** 아직 적용 전인 예약 변경 — 글 1건은 `POST`, 노출 자리는 자리 값. 글자 하나로 묶어 1초마다 도는 「지금」이 바뀌어도 적용 시각이 지날 때만 아래 안내가 다시 계산된다 */
const pendingKeys = computed(() => {
    const p = dealProduct.value;
    if (!p) return '';
    const keys: string[] = [];
    if (UtilBusiness.pendingPriceChange(p.scheduledChange, now.value)) keys.push('POST');
    for (const pl of p.placements) if (UtilBusiness.pendingPriceChange(pl.scheduledChange, now.value)) keys.push(pl.placement);
    return keys.join(',');
});
function pendingChange(key: string, change: BizScheduledPriceChange | null | undefined): BizScheduledPriceChange | null {
    return change && pendingKeys.value.split(',').includes(key) ? change : null;
}
/** 글 1건 값 안내 — 글 받기가 꺼졌으면 폼 대신 「준비 중」이 보이므로 따로 막지 않는다 */
const postNote = computed(() => {
    const p = dealProduct.value;
    const change = p ? pendingChange('POST', p.scheduledChange) : null;
    return p && change ? UtilBusinessMessages.priceChangeNote(t, time.value.slot(change.effectiveAt), 'perPost', { price: p.postPrice }, change) : null;
});
/** 노출 자리 줄의 안내 — 고를 수 없는 자리(받기 꺼짐·말머리 없음)는 기존 안내가 먼저라 뺀다. 미리 신청 일수는 화면 고정값(`LEAD_DAYS`)이라 비교하지 않는다 */
function placementNote(p: DealPlacementProduct, selectable: boolean): BizPriceChangeNote | null {
    const change = selectable ? pendingChange(p.placement, p.scheduledChange) : null;
    return change ? UtilBusinessMessages.priceChangeNote(t, time.value.slot(change.effectiveAt), 'daily', { price: p.dailyPrice, maxDays: p.maxDays }, change) : null;
}
/** 예상 금액 옆 안내 — 글 1건과 고른 노출 자리 중 단가가 바뀔 예정인 것 */
const priceChange = computed(() => {
    const p = dealProduct.value;
    if (!p) return null;
    const items: BizPriceChangeItem[] = [];
    const postChange = pendingChange('POST', p.scheduledChange);
    if (postChange) items.push({ label: t('business.priceChange.postLabel'), unit: 'perPost', price: p.postPrice, change: postChange });
    const pl = placement.value;
    const plChange = pl && placementOptions.value.find((o) => o.value === pl.placement)?.accepting ? pendingChange(pl.placement, pl.scheduledChange) : null;
    if (pl && plChange) items.push({ label: t(`business.placement.${pl.placement}`), unit: 'daily', price: pl.dailyPrice, change: plChange });
    return UtilBusinessMessages.priceChangeSummary(t, items, (iso) => time.value.slot(iso));
});

const placementOptions = computed(() => [
    { value: 'NONE' as DealPlacement, dailyPrice: 0, accepting: true, closed: false, help: placementHelp('NONE'), note: null as BizPriceChangeNote | null },
    ...(dealProduct.value?.placements ?? []).map((p) => {
        /** 고를 수 있나(받기 켬 + 말머리 상단이면 말머리 있는 게시판) */
        const accepting = p.accepting && !(p.placement === 'CATEGORY_TOP' && categoryBlocked.value);
        return {
            value: p.placement as DealPlacement,
            dailyPrice: p.dailyPrice,
            accepting,
            /** 받기 꺼짐 — 「지금은 받지 않아요」 */
            closed: !p.accepting,
            help: placementHelp(p.placement),
            /** 요금 예약 변경 안내(⚠ 가정) */
            note: placementNote(p, accepting),
        };
    }),
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
    if (value === deal.placement || !placementOptions.value.find((o) => o.value === value)?.accepting) return;
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

/** 게시 정책 4줄 — 시작 시각에 게시 · 종료 요청 · 환불 없음 · 고칠 수 없음 */
const policyLines = computed(() => [
    { icon: 'pi pi-calendar', text: pickedBoard.value ? t('business.deal.policyPublish', { board: pickedBoard.value.name }) : t('business.deal.policyPublishAny') },
    { icon: 'pi pi-flag', text: t('business.deal.policyEndRequest') },
    { icon: 'pi pi-ban', text: t('business.deal.policyNoRefund') },
    { icon: 'pi pi-lock', text: t('business.deal.policyNoEdit') },
]);

const agreeItems = computed<BusinessAgreeItem[]>(() => [
    { key: 'adPolicy', label: t('business.agree.adPolicy'), detail: t('business.agree.adPolicyDetail'), version: AD_POLICY_VERSION },
    { key: 'refundPolicy', label: t('business.agree.refundPolicy'), detail: UtilBusinessMessages.refundPolicyDetail(t, stopFeeRate.value), version: REFUND_POLICY_VERSION },
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
    if (tried.value[3] && boards.value.length && !pickedBoard.value) out.board = t('business.deal.boardRequired');
    // 말머리는 선택(회원 글쓰기와 같음) — 말머리 상단 노출을 고른 경우만 필수(서버 `BizDealContent.board`)
    if (tried.value[3] && deal.placement === 'CATEGORY_TOP' && !pickedPrefix.value) out.prefix = t('business.deal.prefixRequired');
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

const STEP_FIELDS: Record<number, string[]> = { 1: ['link', 'shop'], 2: ['dealPrice'], 3: ['board', 'prefix', 'title', 'body'], 4: ['period'], 5: ['agree'] };

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
            boardId: pickedBoard.value?.id ?? '',
            prefixId: pickedPrefix.value?.id ?? null,
            imageFileId: image.value?.fileId ?? null,
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
        // 환불 규정이 그새 바뀌었다(400 `fields.agreedRefundPolicyVersion`) — 새 문구에 다시 동의하도록 새로 고침을 부탁한다
        if (e instanceof ApiError && e.field('agreedRefundPolicyVersion')) formError.value = t('business.agree.refundPolicyOutdated');
        // 서버 칸 오류면 그 칸이 있는 단계로(앞 단계가 먼저) — 문구는 서버 몫. 링크(형식·차단·단축 주소 — `deal.linkUrl`)는 ①의 링크 칸 아래에도 싣는다
        if (e instanceof ApiError) {
            const linkField = e.field('deal.linkUrl');
            if (linkField) {
                linkError.value = linkField;
                step.value = 1;
            } else if (e.field('deal.listPrice') || e.field('deal.stock')) step.value = 2;
            else if (e.field('deal.boardId') || e.field('deal.prefixId') || e.field('deal.imageFileId')) step.value = 3;
        }
        if (e instanceof ApiError && e.code === 'BIZ_SLOT_FULL') {
            step.value = 4;
            void loadAvailability();
        }
        // 받기가 꺼졌으면(409 BIZ_PRODUCT_CLOSED) 요금을 다시 읽는다 — 글 게시가 닫혔으면 폼 대신 「준비 중」 안내, 노출 자리만 닫혔으면 노출 단계로
        if (e instanceof ApiError && e.code === 'BIZ_PRODUCT_CLOSED') await reloadDealProduct();
        // 그새 핫딜 서비스가 정지됐다(409 BUSINESS_SERVICE_SUSPENDED) — 신원을 다시 읽어 폼 대신 막힘 안내(사유)를 보인다
        if (e instanceof ApiError && e.code === 'BUSINESS_SERVICE_SUSPENDED') await reloadProfile();
    } finally {
        submitBusy.value = false;
    }
}
</script>
