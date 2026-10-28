<template>
    <!--
        판매조건 요약(결정 57 — 모집 기간 · 총 수량 · 1인 한도 · 차수 · 성사 조건) + 발송 예정(결정 51) + 정상가 기준(결정 58). 공구 상세와 주문서가 같이 쓴다.
        「한정」은 시스템 문구로 쓰지 않고 「총 N개」만(결정 58). 값은 서버가 준 그대로.
    -->
    <section :aria-labelledby="headingId" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-buy-sales-terms">
        <h2 :id="headingId" class="m-0 mb-2 text-sm font-semibold">{{ t('groupBuy.termsTitle') }}</h2>
        <dl class="m-0 grid grid-cols-[minmax(0,7.5rem)_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
            <dt class="text-muted-color">{{ t('groupBuy.termsPeriod') }}</dt>
            <dd class="m-0 tabular-nums">{{ t('groupBuy.termsPeriodValue', { start: time.shortDateTime(gb.startAt), end: time.shortDateTime(gb.endAt) }) }}</dd>
            <dt class="text-muted-color">{{ t('groupBuy.termsTotal') }}</dt>
            <dd class="m-0 tabular-nums">
                {{ t('groupBuy.termsTotalValue', { count: UtilGroupBuy.amount(gb.maxQty) }) }}
                <span v-if="gb.addedQty > 0" class="text-muted-color">· {{ t('groupBuy.addedQty', { count: gb.addedQty }) }}</span>
            </dd>
            <dt class="text-muted-color">{{ t('groupBuy.termsLimit') }}</dt>
            <dd class="m-0">
                {{ gb.maxQtyPerUser === null ? t('groupBuy.termsLimitNone') : t('groupBuy.termsLimitValue', { count: gb.maxQtyPerUser }) }}
                <span v-if="gb.maxQtyPerUser !== null && gb.roundNo > 1" class="block text-xs text-muted-color">{{ gb.roundLimitShared ? t('groupBuy.termsLimitShared') : t('groupBuy.termsLimitPerRound') }}</span>
            </dd>
            <dt class="text-muted-color">{{ t('groupBuy.termsRound') }}</dt>
            <dd class="m-0">{{ t('groupBuy.roundNo', { n: gb.roundNo }) }}</dd>
            <dt class="text-muted-color">{{ t('groupBuy.termsCondition') }}</dt>
            <dd class="m-0">{{ gb.minQty > 0 ? t('groupBuy.termsConditionMin', { count: gb.minQty }) : t('groupBuy.termsConditionFirst') }}</dd>
            <dt class="text-muted-color">{{ t('groupBuy.termsShip') }}</dt>
            <dd class="m-0">{{ UtilGroupBuyMessages.shipPlan(t, time, gb) || '—' }}</dd>
            <template v-if="gb.normalPrice !== null && gb.normalPriceBasis">
                <dt class="text-muted-color">{{ t('groupBuy.termsNormalPrice') }}</dt>
                <dd class="m-0">{{ t('groupBuy.normalPriceBasisLine', { price: UtilGroupBuyMessages.won(t, gb.normalPrice), basis: t(`groupBuy.normalPriceBasis.${gb.normalPriceBasis}`) }) }}</dd>
            </template>
        </dl>
        <p class="m-0 mt-3 text-xs leading-relaxed text-muted-color">{{ t(`groupBuy.extensionHint.${gb.extensionMode}`, { minutes: gb.extensionMaxMinutes }) }}</p>
        <p v-if="gb.minQty > 0" class="m-0 mt-1 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.failRefundHint') }}</p>
    </section>
</template>

<script setup lang="ts">
import type { GroupBuyDetail } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

interface Props {
    gb: GroupBuyDetail;
}

withDefaults(defineProps<Props>(), {});
const { t } = useI18n();
const time = useDisplayTime();
const headingId = useId();
</script>
