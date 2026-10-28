<template>
    <!--
        배송비·반품비 금액 고지 + 청약철회 제한 안내 — 공구 상세와 주문서가 같이 쓴다(commerce.md 10-8 끝: 상세·주문서·반품 요청 화면에 반품 비용을 금액까지, 결정 55·59 — 처음부터 보인다).
        문장은 서버가 준 배송비 사본으로 만든다(`UtilGroupBuyMessages.shippingLines`). 접지 않는다.
    -->
    <section :aria-labelledby="headingId" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-buy-shipping-notice">
        <h2 :id="headingId" class="m-0 mb-2 text-sm font-semibold">{{ t('groupBuy.noticeTitle') }}</h2>
        <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-sm leading-relaxed">
            <li v-for="(line, index) in lines" :key="index">{{ line }}</li>
        </ul>
        <div v-if="withdrawalRestriction" class="mt-3 rounded-lg bg-amber-50 p-3 text-sm leading-relaxed text-amber-950 dark:bg-amber-950/40 dark:text-amber-100" data-testid="group-buy-withdrawal-restriction">
            <p class="m-0 font-semibold">{{ t('groupBuy.withdrawalTitle') }}</p>
            <p class="m-0 mt-1 whitespace-pre-line">{{ withdrawalRestriction }}</p>
        </div>
        <p class="m-0 mt-3 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.noticeWindow') }}</p>
    </section>
</template>

<script setup lang="ts">
import type { GroupBuyShipping } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

interface Props {
    shipping: GroupBuyShipping;
    /** 청약철회 제한 안내(운영자 문구) — 없으면 null */
    withdrawalRestriction?: string | null;
}

const props = withDefaults(defineProps<Props>(), { withdrawalRestriction: null });
const { t } = useI18n();
const headingId = useId();
const lines = computed(() => UtilGroupBuyMessages.shippingLines(t, props.shipping));
</script>
