<template>
    <!--
        공구 「추가 구성상품」 구획 본문(결정 88·89 — 고시 원칙 ⑦ 의 별개 상품, 단품 종류 ADDON). 줄마다 **다른 상품의 단품**을 고르고(이름·이미지·고시 양식은 그 상품 것) 이 공구의 가격 · 한도(비우면 한도 없음) · 공급가를 넣는다. 20개까지, 차례 · 빼기.
        회원은 본 상품과 함께만 담는다(단독 구매 불가). 공구 최대·성사·1인 한도는 본 상품(MAIN)만 세고 추가 구성상품은 자기 한도만 센다.
        같은 단품은 두 번 고르지 못한다. 모집 뒤(`locked`)에는 구성·가격이 잠긴다(읽기 전용 + 확정·결제 중) — 공급가만 고친다(`supply-editable`, 결정 89). 한도는 수량 화면에서.
        서버 오류는 `errors.addons`(구획 단위).
    -->
    <div class="flex flex-col gap-2">
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ locked ? t('groupBuy.addonLockedHelp') : t('groupBuy.addonHelp') }}</p>
        <p v-if="errors.addons" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.addons }}</p>
        <p v-if="!model.length" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.addonNone') }}</p>

        <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
            <li v-for="(row, index) in model" :key="row.key" class="flex flex-wrap items-start gap-3 rounded-lg border border-(--line) px-3 py-2">
                <img v-if="row.image" :src="row.image.thumbUrl" :alt="t('groupBuy.addonImageAlt', { name: row.name || index + 1 })" class="aspect-square w-16 shrink-0 rounded-md border border-(--line) object-cover" />
                <span v-else class="flex aspect-square w-16 shrink-0 items-center justify-center rounded-md border border-(--line) text-muted-color"><i class="pi pi-image" aria-hidden="true" /></span>

                <div class="grid min-w-0 flex-1 gap-2" :class="showSupply ? 'sm:grid-cols-[minmax(12rem,1fr)_7rem_6rem_7rem]' : 'sm:grid-cols-[minmax(12rem,1fr)_7rem_6rem]'">
                    <div class="flex flex-col gap-1">
                        <label :for="`${baseId}-src-${row.key}`" class="text-xs font-medium">{{ t('groupBuy.addonSource') }}</label>
                        <Select
                            v-if="!ro"
                            :input-id="`${baseId}-src-${row.key}`"
                            :model-value="row.productSkuId"
                            :options="choices(index)"
                            option-label="label"
                            option-value="value"
                            option-disabled="disabled"
                            filter
                            :placeholder="t('groupBuy.addonSourcePlaceholder')"
                            :invalid="!row.productSkuId"
                            fluid
                            @update:model-value="(v: string) => pickSource(index, v)"
                        />
                        <span v-else :id="`${baseId}-src-${row.key}`" class="text-[13px] font-medium">{{ row.name }}</span>
                        <NuxtLink v-if="productOf(row)" :to="`/group-buy-products/${encodeURIComponent(productOf(row)!)}`" class="text-[11px] text-primary hover:underline">{{ t('groupBuy.addonOpenProduct') }}</NuxtLink>
                    </div>
                    <div class="flex flex-col gap-1">
                        <label :for="`${baseId}-price-${row.key}`" class="text-xs font-medium">{{ t('groupBuy.addonPrice') }}</label>
                        <InputText :id="`${baseId}-price-${row.key}`" :model-value="row.price" inputmode="numeric" :readonly="ro" :invalid="!!row.price.trim() && UtilGroupBuy.int(row.price) === null" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(index, { price: v ?? '' })" />
                    </div>
                    <div class="flex flex-col gap-1">
                        <label :for="`${baseId}-max-${row.key}`" class="text-xs font-medium">{{ t('groupBuy.addonMax') }}</label>
                        <InputText :id="`${baseId}-max-${row.key}`" :model-value="row.maxQty" inputmode="numeric" :placeholder="t('groupBuy.optionUnlimited')" :readonly="ro" :invalid="!!row.maxQty.trim() && UtilGroupBuy.int(row.maxQty) === null" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(index, { maxQty: v ?? '' })" />
                    </div>
                    <div v-if="showSupply" class="flex flex-col gap-1">
                        <label :for="`${baseId}-supply-${row.key}`" class="text-xs font-medium">{{ t('groupBuy.supplyPrice') }}</label>
                        <InputText :id="`${baseId}-supply-${row.key}`" :model-value="row.supplyPrice" inputmode="numeric" :placeholder="t('groupBuy.supplyRequired')" :readonly="disabled || !supplyEditable" :invalid="!!row.supplyPrice.trim() && UtilGroupBuy.int(row.supplyPrice) === null" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(index, { supplyPrice: v ?? '' })" />
                        <span v-if="marginLoss(row)" class="text-[11px] text-red-600 dark:text-red-400">{{ t('groupBuy.marginLoss') }}</span>
                    </div>
                    <p v-if="locked" class="m-0 text-xs tabular-nums text-muted-color sm:col-span-4">{{ t('groupBuy.addonSoldLine', { current: row.currentQty, reserved: row.reservedQty }) }}</p>
                </div>

                <div v-if="!ro" class="flex items-center gap-1 self-center">
                    <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.addonMoveUp', { n: index + 1 })" :disabled="index === 0" @click="move(index, -1)" />
                    <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.addonMoveDown', { n: index + 1 })" :disabled="index >= model.length - 1" @click="move(index, 1)" />
                    <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.addonRemove', { n: index + 1 })" @click="remove(index)" />
                </div>
            </li>
        </ul>

        <div v-if="!ro">
            <Button type="button" :label="t('groupBuy.addonAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="model.length >= UtilGroupBuy.SKU.addonMax" @click="add" />
            <span v-if="model.length >= UtilGroupBuy.SKU.addonMax" class="ml-2 text-xs text-muted-color">{{ t('groupBuy.addonMaxReached', { max: UtilGroupBuy.SKU.addonMax }) }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyAddonRow } from '~/components/groupbuy/GroupBuySkuEditor.types';
import type { GroupBuyAddonSource } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyAddonRow[]>({ required: true });

interface Props {
    /** 고를 수 있는 다른 상품의 단품(서버 `addon-sources`) */
    sources?: GroupBuyAddonSource[];
    locked?: boolean;
    disabled?: boolean;
    /** 공급가 칸을 보이는가(가격 권한) · 고칠 수 있는가(모집 뒤에도) */
    showSupply?: boolean;
    supplyEditable?: boolean;
    /** 서버 칸 오류(`addons`) */
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { sources: () => [], locked: false, disabled: false, showSupply: false, supplyEditable: false, errors: () => ({}) });

const { t } = useI18n();
const baseId = useId();
let seq = 0;

const ro = computed(() => props.locked || props.disabled);

/** 고르기 목록 — 다른 줄이 고른 단품은 끈다 */
function choices(index: number): { value: string; label: string; disabled: boolean }[] {
    const taken = new Set(model.value.filter((_, i) => i !== index).map((r) => r.productSkuId));
    return props.sources.map((s) => ({ value: s.productSkuId, label: s.name, disabled: taken.has(s.productSkuId) }));
}

function productOf(row: GroupBuyAddonRow): string | null {
    return props.sources.find((s) => s.productSkuId === row.productSkuId)?.productId ?? null;
}

/** 단품을 고르면 이름·이미지는 그 상품 것, 공급가 칸이 비었으면 기본 공급가를 채운다 */
function pickSource(index: number, productSkuId: string): void {
    const src = props.sources.find((s) => s.productSkuId === productSkuId);
    const row = model.value[index];
    if (!src || !row) return;
    patch(index, {
        productSkuId,
        name: src.name,
        image: src.imageUrl ? { mediaId: `src-${productSkuId}`, url: src.imageUrl, thumbUrl: src.imageUrl } : null,
        supplyPrice: row.supplyPrice.trim() === '' && src.baseSupplyPrice !== null ? String(src.baseSupplyPrice) : row.supplyPrice,
    });
}

function marginLoss(row: GroupBuyAddonRow): boolean {
    const price = UtilGroupBuy.int(row.price);
    const supply = UtilGroupBuy.int(row.supplyPrice);
    return price !== null && supply !== null && price < supply;
}

function patch(index: number, change: Partial<GroupBuyAddonRow>): void {
    model.value = model.value.map((r, i) => (i === index ? { ...r, ...change } : r));
}

function add(): void {
    if (model.value.length >= UtilGroupBuy.SKU.addonMax) return;
    seq += 1;
    model.value = [...model.value, { key: `addon-${Date.now()}-${seq}`, id: null, productSkuId: null, name: '', price: '', maxQty: '', supplyPrice: '', image: null, currentQty: 0, reservedQty: 0 }];
}

function remove(index: number): void {
    model.value = model.value.filter((_, i) => i !== index);
}

function move(index: number, delta: number): void {
    const to = index + delta;
    if (to < 0 || to >= model.value.length) return;
    const next = [...model.value];
    [next[index], next[to]] = [next[to]!, next[index]!];
    model.value = next;
}
</script>
