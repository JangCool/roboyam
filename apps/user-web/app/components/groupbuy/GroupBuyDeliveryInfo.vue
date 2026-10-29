<template>
    <!--
        공구 상세 「배송/교환/반품 안내」 구역의 표(11번가·네이버 쇼핑 방식) — 배송(배송비·방법·발송 예정) · 교환·반품(신청 방법·기한·반품 배송비·하자·오배송·청약철회 제한).
        금액 문장은 서버가 준 배송비 사본으로 만든다(`UtilGroupBuyMessages.shippingLines` — 주문서의 `GroupBuyShippingNotice` 와 같은 문장). 반품 비용은 처음부터 금액까지 보인다(commerce.md 10-8).
        판매자(사업자) 정보는 부르는 쪽이 이 표 아래에 둔다.
    -->
    <div class="flex flex-col gap-4" data-testid="group-buy-delivery-info">
        <table v-for="group in groups" :key="group.key" class="w-full border-collapse overflow-hidden rounded-lg border border-surface text-sm">
            <caption class="pb-1.5 text-left text-sm font-semibold">{{ group.title }}</caption>
            <tbody>
                <tr v-for="row in group.rows" :key="row.key" class="border-b border-surface last:border-b-0">
                    <th scope="row" class="w-28 bg-surface-50 px-3 py-2.5 text-left align-top font-medium text-muted-color sm:w-36 dark:bg-surface-800/60">{{ row.label }}</th>
                    <td class="px-3 py-2.5 align-top leading-relaxed [overflow-wrap:anywhere]" :class="row.tone">
                        <template v-if="Array.isArray(row.value)">
                            <p v-for="(line, i) in row.value" :key="i" class="m-0" :class="i > 0 ? 'mt-1' : ''">{{ line }}</p>
                        </template>
                        <span v-else class="whitespace-pre-line">{{ row.value }}</span>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyDetail } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

const props = defineProps<{ gb: GroupBuyDetail }>();
const { t } = useI18n();
const time = useDisplayTime();

interface Row {
    key: string;
    label: string;
    value: string | string[];
    tone?: string;
}

const groups = computed(() => {
    const g = props.gb;
    // shippingLines: [배송비, 반품 배송비, (무료배송 부분 반품·왕복/편도), 직접 보내기, 하자·오배송] — 표 칸으로 나눠 담는다
    const lines = UtilGroupBuyMessages.shippingLines(t, g.shipping);
    const shippingFee = lines[0] ?? '';
    const defect = lines[lines.length - 1] ?? '';
    const returnFee = lines.slice(1, -1);
    const shipping: Row[] = [
        { key: 'fee', label: t('groupBuy.rowShippingFee'), value: shippingFee },
        { key: 'method', label: t('groupBuy.rowShippingMethod'), value: t('groupBuy.rowShippingMethodValue') },
        { key: 'plan', label: t('groupBuy.rowShipPlan'), value: UtilGroupBuyMessages.shipPlan(t, time.value, g) || '—' },
    ];
    const returns: Row[] = [
        { key: 'how', label: t('groupBuy.rowReturnHow'), value: t('groupBuy.rowReturnHowValue') },
        { key: 'period', label: t('groupBuy.rowReturnPeriod'), value: t('groupBuy.noticeWindow') },
        { key: 'fee', label: t('groupBuy.rowReturnFee'), value: returnFee },
        { key: 'defect', label: t('groupBuy.rowDefect'), value: defect },
        g.withdrawalRestriction
            ? { key: 'withdrawal', label: t('groupBuy.rowWithdrawal'), value: g.withdrawalRestriction, tone: 'bg-amber-50 text-amber-950 dark:bg-amber-950/40 dark:text-amber-100' }
            : { key: 'withdrawal', label: t('groupBuy.rowWithdrawal'), value: t('groupBuy.rowWithdrawalNone') },
    ];
    return [
        { key: 'shipping', title: t('groupBuy.deliveryShippingHead'), rows: shipping },
        { key: 'returns', title: t('groupBuy.deliveryReturnHead'), rows: returns },
    ];
});
</script>
