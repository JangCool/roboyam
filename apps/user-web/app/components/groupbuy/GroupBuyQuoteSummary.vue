<template>
    <!--
        서버 견적 합계(상품 금액 · 배송비 · 결제할 금액 · 무료배송까지 남은 금액). 공구 상세(「선택한 상품」 아래)와 주문서가 같이 쓴다.
        화면은 더하지 않는다 — 값은 `GroupBuyQuote` 그대로. 바깥 구획·제목은 부르는 쪽이 둔다.
    -->
    <div aria-live="polite" data-testid="group-buy-quote">
        <p v-if="!ready" class="m-0 text-sm text-muted-color">{{ t('groupBuy.quotePending') }}</p>
        <p v-else-if="busy && !quote" class="m-0 text-sm text-muted-color" role="status">{{ t('groupBuy.quoting') }}</p>
        <Message v-else-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        <dl v-else-if="quote" class="m-0 grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1 text-sm transition-opacity" :class="busy ? 'opacity-60' : ''">
            <dt class="text-muted-color">{{ t('groupBuy.itemsAmount') }}</dt>
            <dd class="m-0 text-right tabular-nums">{{ UtilGroupBuyMessages.won(t, quote.itemsAmount) }}</dd>
            <dt class="text-muted-color">{{ t('groupBuy.shippingFee') }}</dt>
            <dd class="m-0 text-right tabular-nums">{{ quote.shippingFee === 0 ? t('groupBuy.freeShipping') : UtilGroupBuyMessages.won(t, quote.shippingFee) }}</dd>
            <dt class="border-t border-surface pt-1 font-semibold">{{ t('groupBuy.totalAmount') }}</dt>
            <dd class="m-0 border-t border-surface pt-1 text-right text-lg font-bold tabular-nums text-primary" data-testid="group-buy-total">{{ UtilGroupBuyMessages.won(t, quote.totalAmount) }}</dd>
        </dl>
        <p v-if="ready && quote && quote.freeShippingRemaining !== null && !error" class="m-0 mt-2 text-xs text-muted-color">{{ t('groupBuy.freeShippingRemaining', { amount: UtilGroupBuyMessages.won(t, quote.freeShippingRemaining) }) }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyQuote } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

interface Props {
    quote: GroupBuyQuote | null;
    /** 고른 줄이 견적을 받을 만큼 찼나(본 상품 줄이 있다) — 아니면 「상품을 고르면 금액을 알려 드려요」 */
    ready: boolean;
    busy?: boolean;
    error?: string;
}

withDefaults(defineProps<Props>(), {
    busy: false,
    error: '',
});

const { t } = useI18n();
</script>
