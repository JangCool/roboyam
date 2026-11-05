<template>
    <div class="mx-auto flex max-w-3xl flex-col gap-4">
        <NuxtLink :to="UtilEmoticon.PATH" class="inline-flex w-fit items-center gap-1 rounded text-sm text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ t('emoticon.shopTitle') }}
        </NuxtLink>

        <header class="panel-card p-4">
            <h1 class="m-0 text-lg font-bold">{{ t('emoticon.guideTitle') }}</h1>
            <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('emoticon.guideIntro') }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
                <Button as="router-link" :to="UtilEmoticon.STUDIO_NEW_PATH" :label="t('emoticon.newPack')" icon="pi pi-plus" size="small" data-testid="emoticon-guide-start" />
                <Button as="router-link" :to="UtilEmoticon.STUDIO_PATH" :label="t('emoticon.studioTitle')" icon="pi pi-pen-to-square" size="small" severity="secondary" variant="outlined" />
            </div>
        </header>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('emoticon.loading')">
            <div v-for="n in 3" :key="n" class="h-40 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error' || !rules" :title="t('emoticon.loadFailed')" :description="loadError" retry @retry="load" />

        <template v-else>
            <!-- 1. 규격 표 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-spec`" data-testid="emoticon-guide-spec">
                <h2 :id="`${uid}-spec`" class="m-0 text-base font-bold">{{ t('emoticon.guideSpecTitle') }}</h2>
                <table class="mt-3 w-full border-collapse text-sm">
                    <tbody>
                        <tr v-for="row in specRows" :key="row.key" class="border-t border-surface first:border-t-0">
                            <th scope="row" class="w-32 py-2 pr-3 text-left align-top text-xs font-semibold text-muted-color sm:w-40">{{ row.label }}</th>
                            <td class="py-2 leading-relaxed">{{ row.value }}</td>
                        </tr>
                    </tbody>
                </table>
            </section>

            <!-- 2. 좋은 예 — 로보얌콘 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-good`">
                <h2 :id="`${uid}-good`" class="m-0 text-base font-bold">{{ t('emoticon.guideGoodTitle') }}</h2>
                <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('emoticon.guideGoodText') }}</p>
                <ul class="m-0 mt-3 grid list-none grid-cols-[repeat(auto-fill,minmax(6.5rem,1fr))] gap-2 p-0">
                    <li v-for="ex in examples" :key="ex.url" class="flex flex-col items-center gap-1 rounded-xl bg-surface-50 p-2 dark:bg-surface-950">
                        <img :src="ex.url" :alt="ex.label" width="100" height="100" class="size-[100px] object-contain" loading="lazy" />
                        <span class="text-xs text-muted-color">{{ ex.label }}</span>
                    </li>
                </ul>
            </section>

            <!-- 3. 보이는 크기 — 글·댓글 100px · 모바일 80px · 고르기 창 64px -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-size`">
                <h2 :id="`${uid}-size`" class="m-0 text-base font-bold">{{ t('emoticon.guideSizeTitle') }}</h2>
                <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('emoticon.guideSizeText') }}</p>
                <div class="mt-3 flex flex-wrap items-end gap-6">
                    <figure v-for="s in sizes" :key="s.px" class="m-0 flex flex-col items-center gap-1">
                        <img :src="sample.url" :alt="sample.label" :width="s.px" :height="s.px" :style="{ width: `${s.px}px`, height: `${s.px}px` }" class="rounded border border-dashed border-surface object-contain" />
                        <figcaption class="text-xs text-muted-color">{{ s.label }}</figcaption>
                    </figure>
                </div>
            </section>

            <!-- 4. 어두운 바탕 — 흰 테두리 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-dark`">
                <h2 :id="`${uid}-dark`" class="m-0 text-base font-bold">{{ t('emoticon.guideDarkTitle') }}</h2>
                <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('emoticon.guideDarkText') }}</p>
                <div class="mt-3 grid grid-cols-2 gap-2">
                    <div class="flex flex-col items-center gap-1 rounded-xl p-3" style="background: #ffffff">
                        <img :src="sample.url" :alt="sample.label" width="100" height="100" class="size-[100px] object-contain" loading="lazy" />
                        <span class="text-xs" style="color: #475569">{{ t('emoticon.bgLight') }}</span>
                    </div>
                    <div class="flex flex-col items-center gap-1 rounded-xl p-3" style="background: #0f172a">
                        <img :src="sample.url" :alt="sample.label" width="100" height="100" class="size-[100px] object-contain" loading="lazy" />
                        <span class="text-xs" style="color: #cbd5e1">{{ t('emoticon.bgDark') }}</span>
                    </div>
                </div>
            </section>

            <!-- 5. 만들 때 팁 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-tips`">
                <h2 :id="`${uid}-tips`" class="m-0 text-base font-bold">{{ t('emoticon.guideTipsTitle') }}</h2>
                <ul class="m-0 mt-2 flex flex-col gap-1.5 pl-5 text-sm leading-relaxed">
                    <li>{{ t('emoticon.guideTipAlpha') }}</li>
                    <li>{{ t('emoticon.guideTipFrames', { max: rules.maxFrames }) }}</li>
                    <li>{{ t('emoticon.guideTipBytes', { recommended: UtilEmoticonSpec.bytes(rules.recommendedBytes), max: UtilEmoticonSpec.bytes(rules.maxBytes) }) }}</li>
                    <li>{{ t('emoticon.guideTipPadding', { padding: rules.imagePadding, inner: rules.imageSize - rules.imagePadding * 2 }) }}</li>
                    <li>{{ t('emoticon.guideTipText') }}</li>
                </ul>
            </section>

            <!-- 6. 심사 기준 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-review`">
                <h2 :id="`${uid}-review`" class="m-0 text-base font-bold">{{ t('emoticon.guideReviewTitle') }}</h2>
                <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('emoticon.guideReviewText', { max: rules.maxPending }) }}</p>
                <ul class="m-0 mt-2 flex flex-col gap-1.5 pl-5 text-sm leading-relaxed">
                    <li>{{ t('emoticon.guideReviewCopyright') }}</li>
                    <li>{{ t('emoticon.guideReviewAdult') }}</li>
                    <li>{{ t('emoticon.guideReviewHate') }}</li>
                    <li>{{ t('emoticon.guideReviewTrademark') }}</li>
                    <li>{{ t('emoticon.guideReviewReadable') }}</li>
                </ul>
            </section>

            <!-- 7. 보상 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-reward`" data-testid="emoticon-guide-reward">
                <h2 :id="`${uid}-reward`" class="m-0 text-base font-bold">{{ t('emoticon.guideRewardTitle') }}</h2>
                <p class="m-0 mt-2 text-sm leading-relaxed">{{ t('emoticon.guideRewardText', { points: UtilPoint.points(rules.rewardPerAcquire), cap: UtilPoint.points(rules.rewardDailyCap) }) }}</p>
                <ul class="m-0 mt-2 flex flex-col gap-1.5 pl-5 text-sm leading-relaxed text-muted-color">
                    <li>{{ t('emoticon.guideRewardCount') }}</li>
                    <li>{{ t('emoticon.guideRewardOnce') }}</li>
                    <li>{{ t('emoticon.guideRewardKeep') }}</li>
                    <li>{{ t('emoticon.guideRewardOfficial') }}</li>
                </ul>
            </section>

            <!-- 8. 유료 판매 — 실결제, 작가에게 별도 정산(계약 「가격과 결제」·가정 1·3·4·5) -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-paid`" data-testid="emoticon-guide-paid">
                <h2 :id="`${uid}-paid`" class="m-0 text-base font-bold">{{ t('emoticon.guidePaidTitle') }}</h2>
                <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('emoticon.guidePaidText') }}</p>
                <table class="mt-3 w-full border-collapse text-sm">
                    <tbody>
                        <tr v-for="row in paidRows" :key="row.key" class="border-t border-surface first:border-t-0">
                            <th scope="row" class="w-32 py-2 pr-3 text-left align-top text-xs font-semibold text-muted-color sm:w-40">{{ row.label }}</th>
                            <td class="py-2 leading-relaxed">{{ row.value }}</td>
                        </tr>
                    </tbody>
                </table>
                <div class="mt-3 flex flex-wrap gap-2">
                    <Button as="router-link" :to="UtilEmoticon.PAYOUT_PATH" :label="t('emoticon.payoutTitle')" icon="pi pi-wallet" size="small" severity="secondary" variant="outlined" />
                </div>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonRules } from '~/composables/emoticon/api/useEmoticonApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';
import UtilEmoticonSpec from '~/utils/util-emoticon-spec';
import UtilEmoticonSettlement from '~/utils/util-emoticon-settlement';
import UtilPoint from '~/utils/util-point';

/**
 * 로보티콘 제작 가이드 `/robotikon/guide` — 비로그인도 본다. 규격 표·좋은 예(로보얌콘)·보이는 크기(100·80·64px)·어두운 바탕(흰 테두리)·팁(GIF 반투명 없음·프레임·용량)·
 * 심사 기준·무료 팩 보상(받을 때마다 적립 포인트, 하루 상한)·유료 판매 규칙(카카오 방식 — 사이트가 판매자·작가는 상품화 계약 · 가격 단계(운영자가 정함) ·
 * 부가세 제외 뒤 배분 + 한 건 예시(`UtilEmoticonSettlement`) · 월 정산 · 원천징수/세금계산서 · 정산 정보 · 구매자 환불 · 가격 변경 요청 — 계약 「가격과 결제」·가정 1·3·4·5).
 * 규격·보상 값은 서버 설정(`GET /api/emoticons/rules` — 가격·수수료·정산·환불 칸은 S2~S4 기본값)에서 읽는다 — 업로드 검사와 같은 값.
 */
const { t } = useI18n();
useSeoMeta({ title: () => t('emoticon.guideTitle'), description: () => t('emoticon.guideIntro') });

const uid = useId();
const api = useEmoticonApi();
const { siteLabel } = useSiteName();

const rules = ref<EmoticonRules | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');

async function load(): Promise<void> {
    status.value = 'loading';
    try {
        rules.value = await api.rules();
        status.value = 'ready';
    } catch (e) {
        loadError.value = UtilEmoticonMessages.error(t, e);
        status.value = 'error';
    }
}
onMounted(load);

const specRows = computed(() => {
    const r = rules.value;
    if (!r) return [];
    return [
        { key: 'size', label: t('emoticon.specRowSize'), value: t('emoticon.specRowSizeValue', { size: r.imageSize, inner: r.imageSize - r.imagePadding * 2, padding: r.imagePadding }) },
        { key: 'format', label: t('emoticon.specRowFormat'), value: t('emoticon.specRowFormatValue') },
        { key: 'frames', label: t('emoticon.specRowFrames'), value: t('emoticon.specRowFramesValue', { max: r.maxFrames }) },
        { key: 'bytes', label: t('emoticon.specRowBytes'), value: t('emoticon.specRowBytesValue', { max: UtilEmoticonSpec.bytes(r.maxBytes), recommended: UtilEmoticonSpec.bytes(r.recommendedBytes) }) },
        { key: 'count', label: t('emoticon.specRowCount'), value: t('emoticon.specRowCountValue', { min: r.minCount, max: r.maxCount }) },
        { key: 'cover', label: t('emoticon.specRowCover'), value: t('emoticon.specRowCoverValue', { size: r.imageSize, max: UtilEmoticonSpec.bytes(r.coverMaxBytes) }) },
        { key: 'name', label: t('emoticon.specRowName'), value: t('emoticon.specRowNameValue', { min: r.nameMin, max: r.nameMax }) },
        { key: 'label', label: t('emoticon.specRowLabel'), value: t('emoticon.specRowLabelValue', { min: r.labelMin, max: r.labelMax }) },
        { key: 'tags', label: t('emoticon.specRowTags'), value: t('emoticon.specRowTagsValue', { max: r.maxTags }) },
        { key: 'display', label: t('emoticon.specRowDisplay'), value: t('emoticon.specRowDisplayValue', { size: r.displaySize, mobile: r.displaySizeMobile, picker: r.pickerSize }) },
    ];
});

/** 유료 판매 규칙 표 — 설정 값에서 */
const paidRows = computed(() => {
    const r = rules.value;
    if (!r) return [];
    // 예시 — 가운데 가격 단계 한 건(개인 작가). 계산은 `UtilEmoticonSettlement` 한 곳
    const examplePrice = r.priceTiers[Math.floor(r.priceTiers.length / 2)] ?? 2500;
    const ex = UtilEmoticonSettlement.compute(examplePrice, 0, r, true);
    return [
        { key: 'seller', label: t('emoticon.paidRowSeller'), value: t('emoticon.paidRowSellerValue', { site: siteLabel.value }) },
        { key: 'price', label: t('emoticon.paidRowPrice'), value: t('emoticon.paidRowPriceValue', { tiers: UtilEmoticonMessages.tiers(t, r.priceTiers) }) },
        { key: 'fee', label: t('emoticon.paidRowFee'), value: t('emoticon.paidRowFeeValue', { vat: UtilEmoticonMessages.percent(r.vatRate), rate: UtilEmoticonMessages.percent(r.feeRate), keep: UtilEmoticonMessages.percent(1 - r.feeRate) }) },
        {
            key: 'example',
            label: t('emoticon.paidRowExample'),
            value: t('emoticon.paidRowExampleValue', {
                price: UtilEmoticonMessages.won(t, examplePrice),
                base: UtilEmoticonMessages.won(t, ex.base),
                share: UtilEmoticonMessages.won(t, ex.share),
                withholding: UtilEmoticonMessages.won(t, ex.withholding),
                payout: UtilEmoticonMessages.won(t, ex.payout),
            }),
        },
        { key: 'cycle', label: t('emoticon.paidRowCycle'), value: t('emoticon.paidRowCycleValue', { days: r.refundDays, day: r.settlementDay, min: UtilEmoticonMessages.won(t, r.settlementMin) }) },
        { key: 'tax', label: t('emoticon.paidRowTax'), value: t('emoticon.paidRowTaxValue', { rate: UtilEmoticonMessages.percent(r.withholdingRate) }) },
        { key: 'contract', label: t('emoticon.paidRowContract'), value: t('emoticon.paidRowContractValue') },
        { key: 'payout', label: t('emoticon.paidRowPayout'), value: t('emoticon.paidRowPayoutValue') },
        { key: 'refund', label: t('emoticon.paidRowRefund'), value: t('emoticon.paidRowRefundValue', { days: r.refundDays }) },
        { key: 'change', label: t('emoticon.paidRowChange'), value: t('emoticon.paidRowChangeValue') },
    ];
});

/** 좋은 예 — 로보얌콘 몇 개(공개 폴더의 운영 팩 그림) */
const examples = computed(() => [
    { url: '/emoticons/robotikon-m/01.gif', label: t('emoticon.exampleJoy') },
    { url: '/emoticons/robotikon-m/19.gif', label: t('emoticon.exampleThumbs') },
    { url: '/emoticons/robotikon-f/11.gif', label: t('emoticon.exampleLove') },
    { url: '/emoticons/robotikon-m/25.gif', label: t('emoticon.exampleLoading') },
    { url: '/emoticons/robotikon-f/18.gif', label: t('emoticon.exampleThanks') },
]);
const sample = computed(() => ({ url: '/emoticons/robotikon-m/19.gif', label: t('emoticon.exampleThumbs') }));
const sizes = computed(() => {
    const r = rules.value;
    return [
        { px: r?.displaySize ?? 100, label: t('emoticon.sizeDisplay', { px: r?.displaySize ?? 100 }) },
        { px: r?.displaySizeMobile ?? 80, label: t('emoticon.sizeMobile', { px: r?.displaySizeMobile ?? 80 }) },
        { px: r?.pickerSize ?? 64, label: t('emoticon.sizePicker', { px: r?.pickerSize ?? 64 }) },
    ];
});
</script>
