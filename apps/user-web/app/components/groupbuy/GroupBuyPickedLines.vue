<template>
    <!--
        「선택한 상품」 목록(결정 88). 공구 상세는 줄마다 수량 ± · 빼기(`editable`), 주문서는 확인만.
        줄 금액은 서버 견적 값(`amount`)만 보인다 — 화면은 더하지 않는다. 추가 구성상품 줄에는 「추가 구성」 딱지.
    -->
    <ul class="m-0 flex list-none flex-col divide-y divide-surface p-0" data-testid="group-buy-picked-lines">
        <li v-for="line in lines" :key="line.skuId" class="flex flex-wrap items-center gap-x-3 gap-y-2 py-2.5 text-sm" :data-testid="`group-buy-picked-${line.skuId}`">
            <span class="min-w-0 flex-1 basis-40">
                <span class="flex flex-wrap items-center gap-1.5">
                    <PointShopTag v-if="line.kind === 'ADDON'" tone="neutral">{{ t('groupBuy.addonTag') }}</PointShopTag>
                    <span class="font-medium">{{ line.name }}</span>
                </span>
                <span class="mt-0.5 block text-xs tabular-nums text-muted-color">{{ t('groupBuy.unitTimesQty', { price: UtilGroupBuyMessages.won(t, line.unitPrice), qty: line.qty }) }}</span>
            </span>
            <InputNumber
                v-if="editable"
                :model-value="line.qty"
                :min="1"
                :max="Math.max(1, line.max)"
                show-buttons
                button-layout="horizontal"
                size="small"
                :aria-label="t('groupBuy.lineQtyOf', { name: line.name })"
                :disabled="disabled"
                class="w-32"
                input-class="w-12 text-center"
                :data-testid="`group-buy-picked-qty-${line.skuId}`"
                @update:model-value="(value: number | null) => onQty(line.skuId, value)"
            />
            <span class="w-24 text-right font-semibold tabular-nums transition-opacity" :class="line.pending ? 'opacity-50' : ''" :aria-busy="line.pending || undefined">{{ line.amount === null ? '—' : UtilGroupBuyMessages.won(t, line.amount) }}</span>
            <Button
                v-if="editable && line.removable"
                type="button"
                icon="pi pi-times"
                severity="secondary"
                variant="text"
                size="small"
                :aria-label="t('groupBuy.lineRemove', { name: line.name })"
                :disabled="disabled"
                :data-testid="`group-buy-picked-remove-${line.skuId}`"
                @click="emit('remove', line.skuId)"
            />
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { GroupBuyPickedLine } from '~/components/groupbuy/GroupBuyPickedLines.types';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

interface Props {
    lines: GroupBuyPickedLine[];
    /** 수량 ± · 빼기(공구 상세) — 주문서는 false(확인만) */
    editable?: boolean;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), {
    editable: false,
    disabled: false,
});

const emit = defineEmits<{
    (e: 'qty', skuId: string, qty: number): void;
    (e: 'remove', skuId: string): void;
}>();

const { t } = useI18n();

/** 칸을 비우면(null) 무시한다 — 줄을 빼려면 「빼기」 */
function onQty(skuId: string, value: number | null): void {
    if (value !== null && value >= 1) emit('qty', skuId, value);
}
</script>
