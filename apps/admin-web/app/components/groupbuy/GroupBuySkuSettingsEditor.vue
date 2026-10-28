<template>
    <!--
        공구 「단품」 구획 본문(사용자 결정 2026-10-28 — 단품은 상품 하위 아이템, 상품 화면에서만 등록). 고른 상품의 단품을 **읽기 전용 이름**으로 보이고
        이 공구의 설정만 넣는다: 추가금 · 최대 수량(비우면 공구 최대만) · 공급가(가격 권한 — 모집 뒤에도) · 판매 여부. 옵션 이름·값·단품 줄 더하기는 여기 없다(상품 화면 링크).
        일괄 넣기: 모든 단품에 추가금·최대 수량·공급가·판매 여부를 한 번에. 모집 뒤(`locked`)에는 추가금·최대 수량·판매 여부가 잠기고 확정·결제 중이 보인다(최대 수량은 수량 화면에서).
        판매가 < 공급가면 줄에 「역마진」. 서버 오류는 `errors.skus`.
    -->
    <div class="flex flex-col gap-3">
        <p class="m-0 text-xs leading-relaxed text-muted-color">
            {{ locked ? t('groupBuy.skuLockedHelp') : t('groupBuy.skuSettingsHelp') }}
            <NuxtLink v-if="productId" :to="`/group-buy-products/${encodeURIComponent(productId)}`" class="ml-1 text-primary hover:underline">{{ t('groupBuy.skuEditOnProduct') }}</NuxtLink>
        </p>
        <p v-if="errors.skus" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.skus }}</p>
        <Message v-if="!mains.length || !productId" severity="secondary" :closable="false" size="small">{{ t('groupBuy.skuPickProductFirst') }}</Message>

        <template v-else>
            <!-- 일괄 넣기 -->
            <div v-if="!ro || supplyEditable" class="flex flex-wrap items-end gap-2 rounded-md bg-surface-50 px-3 py-2 dark:bg-surface-900">
                <span class="w-full text-xs font-medium">{{ t('groupBuy.bulkAllSkus') }}</span>
                <div v-if="!ro" class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-extra`" class="text-xs">{{ t('groupBuy.optionExtra') }}</label>
                    <InputText :id="`${baseId}-bulk-extra`" v-model="bulkExtra" inputmode="numeric" size="small" :placeholder="t('groupBuy.bulkKeep')" class="w-28 text-right tabular-nums" />
                </div>
                <div v-if="!ro" class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-max`" class="text-xs">{{ t('groupBuy.optionMax') }}</label>
                    <InputText :id="`${baseId}-bulk-max`" v-model="bulkMax" inputmode="numeric" size="small" :placeholder="t('groupBuy.bulkKeep')" class="w-28 text-right tabular-nums" />
                </div>
                <div v-if="showSupply && supplyEditable" class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-supply`" class="text-xs">{{ t('groupBuy.supplyPrice') }}</label>
                    <InputText :id="`${baseId}-bulk-supply`" v-model="bulkSupply" inputmode="numeric" size="small" :placeholder="t('groupBuy.bulkKeep')" class="w-28 text-right tabular-nums" />
                </div>
                <div v-if="!ro" class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-sale`" class="text-xs">{{ t('groupBuy.skuOnSale') }}</label>
                    <Select :input-id="`${baseId}-bulk-sale`" v-model="bulkSale" :options="bulkSaleOptions" option-label="label" option-value="value" size="small" class="w-32" />
                </div>
                <Button type="button" :label="t('groupBuy.bulkApply')" size="small" severity="secondary" :disabled="!bulkReady" @click="applyBulk" />
            </div>

            <div class="overflow-x-auto">
                <table class="w-full min-w-[38rem] border-collapse text-[13px]">
                    <thead>
                        <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                            <th class="py-1.5 pr-2 font-normal">{{ t('groupBuy.skuName') }}</th>
                            <th class="w-28 py-1.5 pr-2 text-right font-normal">{{ t('groupBuy.skuPrice') }}</th>
                            <th class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionExtra') }}</th>
                            <th class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionMax') }}</th>
                            <th v-if="showSupply" class="w-32 py-1.5 pr-2 font-normal">{{ t('groupBuy.supplyPrice') }}</th>
                            <th class="w-16 py-1.5 pr-2 font-normal">{{ t('groupBuy.skuOnSale') }}</th>
                            <th v-if="locked" class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionSold') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(row, k) in mains" :key="row.key" class="border-b border-(--line) align-top last:border-b-0" :class="row.onSale ? '' : 'text-muted-color'">
                            <td class="py-1.5 pr-2">
                                <span :class="row.onSale ? 'font-medium' : 'line-through'">{{ row.name || productName }}</span>
                                <Tag v-if="!row.onSale" :value="t('groupBuy.skuOffSale')" severity="secondary" class="ml-1" />
                            </td>
                            <td class="py-1.5 pr-2 text-right tabular-nums">{{ priceOf(row) }}</td>
                            <td class="py-1.5 pr-2">
                                <InputText :model-value="row.extraPrice" :aria-label="t('groupBuy.comboExtraFor', { name: row.name || productName })" inputmode="numeric" :readonly="ro" :invalid="!numberOk(row.extraPrice, false)" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(k, { extraPrice: v ?? '' })" />
                            </td>
                            <td class="py-1.5 pr-2">
                                <InputText :model-value="row.maxQty" :aria-label="t('groupBuy.comboMaxFor', { name: row.name || productName })" inputmode="numeric" :placeholder="t('groupBuy.optionUnlimited')" :readonly="ro" :invalid="!numberOk(row.maxQty, true)" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(k, { maxQty: v ?? '' })" />
                            </td>
                            <td v-if="showSupply" class="py-1.5 pr-2">
                                <InputText :model-value="row.supplyPrice" :aria-label="t('groupBuy.comboSupplyFor', { name: row.name || productName })" inputmode="numeric" :placeholder="t('groupBuy.supplyRequired')" :readonly="disabled || !supplyEditable" :invalid="!numberOk(row.supplyPrice, true)" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(k, { supplyPrice: v ?? '' })" />
                                <span v-if="marginLoss(row)" class="mt-0.5 block text-[11px] text-red-600 dark:text-red-400">{{ t('groupBuy.marginLoss') }}</span>
                            </td>
                            <td class="py-1.5 pr-2">
                                <ToggleSwitch :model-value="row.onSale" :disabled="ro" :aria-label="t('groupBuy.comboSaleFor', { name: row.name || productName })" @update:model-value="(v: boolean) => patch(k, { onSale: v })" />
                            </td>
                            <td v-if="locked" class="py-1.5 pr-2 text-xs tabular-nums text-muted-color">{{ t('groupBuy.optionSoldLine', { current: row.currentQty, reserved: row.reservedQty }) }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <p v-if="!mains.some((m) => m.onSale)" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('groupBuy.comboNoneOnSale') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyMainSkuRow } from '~/components/groupbuy/GroupBuySkuEditor.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const mains = defineModel<GroupBuyMainSkuRow[]>('mains', { required: true });

interface Props {
    /** 고른 상품(없으면 「상품을 먼저 골라 주세요」) */
    productId: string | null;
    /** 단품 이름이 빈 단품 하나(옵션 없는 상품)일 때 보일 이름 */
    productName?: string;
    /** 공구가 글자(판매가 미리 보기) */
    groupPrice?: string;
    /** 모집 뒤 — 추가금·최대 수량·판매 여부 잠김 */
    locked?: boolean;
    disabled?: boolean;
    showSupply?: boolean;
    /** 공급가를 고칠 수 있는가(모집 뒤에도 — 결정 89) */
    supplyEditable?: boolean;
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { productName: '', groupPrice: '', locked: false, disabled: false, showSupply: false, supplyEditable: false, errors: () => ({}) });

const { t, locale } = useI18n();
const baseId = useId();

const ro = computed(() => props.locked || props.disabled);

function priceOf(row: GroupBuyMainSkuRow): string {
    const base = UtilGroupBuy.int(props.groupPrice);
    const extra = UtilGroupBuy.int(row.extraPrice);
    return base === null || extra === null ? '—' : t('groupBuy.wonAmount', { amount: UtilGroupBuy.won(base + extra, locale.value) });
}

function numberOk(text: string, blankOk: boolean): boolean {
    return (blankOk && text.trim() === '') || UtilGroupBuy.int(text) !== null;
}

function marginLoss(row: GroupBuyMainSkuRow): boolean {
    const base = UtilGroupBuy.int(props.groupPrice);
    const extra = UtilGroupBuy.int(row.extraPrice);
    const supply = UtilGroupBuy.int(row.supplyPrice);
    return base !== null && extra !== null && supply !== null && base + extra < supply;
}

function patch(k: number, change: Partial<GroupBuyMainSkuRow>): void {
    mains.value = mains.value.map((m, x) => (x === k ? { ...m, ...change } : m));
}

// ── 일괄 넣기(모든 단품) ──
const bulkExtra = ref('');
const bulkMax = ref('');
const bulkSupply = ref('');
const bulkSale = ref<'KEEP' | 'ON' | 'OFF'>('KEEP');
const bulkSaleOptions = computed(() => (['KEEP', 'ON', 'OFF'] as const).map((v) => ({ value: v, label: t(`groupBuy.bulkSale.${v}`) })));
const bulkReady = computed(() => bulkExtra.value.trim() !== '' || bulkMax.value.trim() !== '' || bulkSupply.value.trim() !== '' || bulkSale.value !== 'KEEP');

function applyBulk(): void {
    if (!bulkReady.value) return;
    mains.value = mains.value.map((m) => ({
        ...m,
        extraPrice: !ro.value && bulkExtra.value.trim() !== '' ? bulkExtra.value.trim() : m.extraPrice,
        maxQty: !ro.value && bulkMax.value.trim() !== '' ? bulkMax.value.trim() : m.maxQty,
        supplyPrice: props.supplyEditable && bulkSupply.value.trim() !== '' ? bulkSupply.value.trim() : m.supplyPrice,
        onSale: ro.value || bulkSale.value === 'KEEP' ? m.onSale : bulkSale.value === 'ON',
    }));
    bulkExtra.value = '';
    bulkMax.value = '';
    bulkSupply.value = '';
    bulkSale.value = 'KEEP';
}
</script>
