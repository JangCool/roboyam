<template>
    <!--
        상품 하나의 사업자 화면 미리보기 — 상품 가격 카드(`AdPriceRuleRow`)의 가격 영역 안. 사업자 센터 신청 화면(user-web `business/ads/new.vue`·`business/deals/new.vue`)의 상품 카드를 본떠 그린다.
          노출 위치 도식(`AdPriceSlotDiagram` — 공개 화면 어디에 나오는지, 핫딜은 목록 속 자리)
          머리: 「사업자 화면 미리보기」 · 때 고르기(Select — 「지금」 + 그 상품 예약마다 「○월 ○일 00:00부터」, 예약이 있을 때만 — 예약 값으로 다시 그린다)
          (가격·위치·예약 안내 카드는 상품 카드 왼쪽 가격 영역과 겹쳐 뺐다 — 미리보기는 도식 · 신청 조건 한 줄 · 예상 금액)
          신청 조건 한 줄(「한 번에 최대 14일 · 시작일은 오늘부터 60일 뒤까지」) · 예상 금액(하루 단가 7일(최대 일수가 짧으면 그만큼) / 건당 1건 — 공급가·부가세·합계)
        서버는 부르지 않는다. ⚠ 사업자 화면 문구를 옮겨 적은 것이라 그쪽 문구가 바뀌면 같이 맞춘다.
    -->
    <section class="flex min-w-0 flex-col gap-2" :aria-labelledby="titleId" data-testid="ad-price-preview">
        <div class="flex flex-wrap items-center gap-2">
            <h5 :id="titleId" class="m-0 inline-flex min-w-0 flex-1 items-center gap-1.5 text-xs font-semibold text-muted-color">
                <i class="pi pi-eye text-[0.6875rem]" aria-hidden="true" />{{ t('ad.pricePreviewTitle') }}
            </h5>
            <Select
                v-if="momentOptions.length > 1"
                v-model="moment"
                :options="momentOptions"
                option-label="label"
                option-value="value"
                size="small"
                class="max-w-full"
                :aria-label="t('ad.pricePreviewMoment')"
                data-testid="ad-price-preview-moment"
            />
        </div>

        <!-- 노출 위치 도식 -->
        <AdPriceSlotDiagram :product="rule.product" />

        <!-- 신청 조건 — 사업자 화면 기간 고르기 안내 -->
        <p class="m-0 text-xs text-muted-color" data-testid="ad-price-preview-period">
            {{ rule.unit === 'POST' ? t('ad.pricePreviewPeriodPost', { lead: values.maxLeadDays }) : t('ad.pricePreviewPeriod', { max: values.maxDays, lead: values.maxLeadDays }) }}
        </p>

        <!-- 예상 금액 -->
        <dl class="m-0 grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1 rounded-md bg-(--p-content-background) px-3 py-2 text-[13px] tabular-nums" data-testid="ad-price-preview-amount">
            <dt class="min-w-0 text-muted-color">{{ rule.unit === 'POST' ? t('ad.pricePreviewSupplyPost') : t('ad.pricePreviewSupplyDays', { n: example.units }) }}</dt>
            <dd class="m-0 text-right">{{ won(example.amount.supply) }}</dd>
            <dt class="min-w-0 text-muted-color">{{ t('ad.pricePreviewVat') }}</dt>
            <dd class="m-0 text-right">{{ won(example.amount.vat) }}</dd>
            <dt class="min-w-0 border-t border-(--line) pt-1 font-medium">{{ t('ad.pricePreviewTotal') }}</dt>
            <dd class="m-0 border-t border-(--line) pt-1 text-right font-semibold text-primary">{{ won(example.amount.total) }}</dd>
        </dl>
    </section>
</template>

<script setup lang="ts">
import type { AdPriceRule, AdPriceValues } from '~/composables/ad/api/useAdApi.types';
import UtilAdPrice from '~/utils/util-ad-price';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    /** 미리 볼 상품(지금 값 · 예약들) */
    rule: AdPriceRule;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const time = useDisplayTime();
const titleId = useId();

const chain = computed(() => UtilAdPrice.chain(props.rule));

/** 「지금」 또는 예약 id */
const moment = ref<string>('NOW');
watch(chain, (items) => {
    if (moment.value !== 'NOW' && !items.some((i) => i.schedule.id === moment.value)) moment.value = 'NOW';
});

const momentOptions = computed(() => [{ value: 'NOW', label: t('ad.pricePreviewNow') }, ...chain.value.map((i) => ({ value: i.schedule.id, label: t('ad.priceFrom', { at: time.value.full(i.schedule.effectiveAt) }) }))]);

const values = computed<AdPriceValues>(() => chain.value.find((i) => i.schedule.id === moment.value)?.after ?? UtilAdPrice.valuesOf(props.rule.unit, props.rule));

const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

const example = computed(() => UtilAdPrice.example(props.rule.unit, values.value));
</script>
