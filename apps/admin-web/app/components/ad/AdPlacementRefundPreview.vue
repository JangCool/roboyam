<template>
    <!--
        ⑤ 중도 해지 수수료 미리보기 — 저장 전 비율로 예시 계산(남은 일할 100,000원 → 수수료 → 환불 예상액, 서버 `BizPricing.stop` 의 수수료 식).
        다른 탭처럼 도식 대신 세 줄 계산표를 그린다(`AdPlacementFrame` 안). 조건 안내: 신청 건에 고정 · 수수료가 붙지 않는 환불.
    -->
    <AdPlacementFrame :caption="caption" :notes="notes" :unsaved="unsaved">
        <dl class="m-0 grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1.5 rounded-lg bg-surface-100 px-3 py-2.5 text-[13px] tabular-nums dark:bg-surface-950" data-testid="ad-placement-refund-example">
            <dt class="min-w-0 text-muted-color">{{ t('ad.previewRefundBase') }}</dt>
            <dd class="m-0 text-right">{{ won(base) }}</dd>
            <dt class="min-w-0 text-muted-color">{{ t('ad.previewRefundFee', { rate }) }}</dt>
            <dd class="m-0 text-right">− {{ won(example.fee) }}</dd>
            <dt class="min-w-0 border-t border-(--line) pt-1.5 font-medium">{{ t('ad.previewRefundAmount') }}</dt>
            <dd class="m-0 border-t border-(--line) pt-1.5 text-right font-semibold text-primary">{{ won(example.amount) }}</dd>
        </dl>
    </AdPlacementFrame>
</template>

<script setup lang="ts">
import UtilAd from '~/utils/util-ad';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    /** 저장 전 수수료율(%) */
    rate: number;
    unsaved?: boolean;
}

const props = withDefaults(defineProps<Props>(), { unsaved: false });
const { t, locale } = useI18n();

const base = UtilAd.STOP_FEE_RATE.example;
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);
const example = computed(() => UtilAd.stopFeeExample(base, props.rate));

const caption = computed(() =>
    props.rate > 0
        ? t('ad.previewRefundCaption', { base: won(base), fee: won(example.value.fee), amount: won(example.value.amount) })
        : t('ad.previewRefundCaptionNoFee', { base: won(base), amount: won(example.value.amount) }),
);
const notes = computed(() => [t('ad.previewRefundFixed'), t('ad.previewRefundNoFee')]);
</script>
