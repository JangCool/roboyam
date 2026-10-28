<template>
    <!--
        상품 「단품(옵션)」 구획 본문(사용자 결정 2026-10-28 — 단품 목록이 정본, 옵션 이름은 코드로 고르고 값은 자유 입력 + 자동완성, 상품 코드·단품 코드 따로).
        · 단품 = 실제로 파는 아이템 한 줄(단품 코드 · 이름 · 판매 여부 · 기본 공급가). 파는 것만 등록한다. 단품이 하나면 옵션 없는 상품.
        · 옵션 종류(최대 3 — 「공구 › 옵션 종류」에서 관리하는 코드, 예 색상·사이즈)를 고르면 단품마다 그 값을 적는다. 값 칸은 그 종류로 이미 쓴 값을 자동완성으로 권한다.
          회원 상세는 옵션 종류 차례로 고른다(앞을 고르면 없는 조합은 회색). 옵션이 없어도 이름으로 가리는 단품을 여럿 둘 수 있다(예 쌀 10kg·20kg — 한 목록에서 고른다).
        · 상품 ID · 단품 ID 는 서버가 자동으로 붙인다(운영자가 넣지 않는다). 단품은 상품 ID 와 단품 ID 를 따로 가진다 — 단품 ID 에 상품 ID 를 붙이지 않는다(서버 열 `product_id` · `id`). 표에는 단품 ID 를 읽기 전용으로, 새 줄은 「저장하면 자동」.
        · 이름을 비우면 옵션 값으로 「블랙 / M」. 같은 이름 · 같은 값 묶음 · 같은 코드 두 줄은 막는다.
        · 모집 중 공구가 쓰면(`locked`) 옵션 종류·단품 추가/삭제·이름·값이 잠긴다(서버 2b-2 LOCKED_FIELD `options`). 판매 여부·기본 공급가는 언제든 고친다.
        기본 공급가 칸은 가격 권한(`show-supply`)이 있을 때만. 서버 오류는 `errors.optionTypeCodes`·`errors.skus`.
    -->
    <div class="flex flex-col gap-3">
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ locked ? t('groupBuy.pskuLockedHelp') : t('groupBuy.pskuHelp') }}</p>

        <!-- 옵션 종류(코드) -->
        <div class="flex flex-col gap-1.5">
            <span class="text-[13px] font-medium">{{ t('groupBuy.pskuOptionNames', { max: UtilGroupBuy.SKU.axisMax }) }}</span>
            <div class="flex flex-wrap items-center gap-2">
                <span v-for="(code, i) in optionTypes" :key="code" class="inline-flex items-center gap-0.5 rounded-md border border-(--line) py-0.5 pl-2 pr-0.5 text-[13px]">
                    {{ typeName(code) }}<span class="ml-1 text-[11px] text-muted-color">{{ code }}</span>
                    <Button v-if="!ro" type="button" icon="pi pi-times" size="small" severity="danger" variant="text" class="size-7!" :aria-label="t('groupBuy.pskuOptionRemove', { name: typeName(code) })" @click="removeOption(i)" />
                    <span v-else class="w-1" />
                </span>
                <Select
                    v-if="!ro && optionTypes.length < UtilGroupBuy.SKU.axisMax"
                    :model-value="null"
                    :options="typeChoices"
                    option-label="label"
                    option-value="value"
                    :placeholder="t('groupBuy.pskuOptionAdd')"
                    :aria-label="t('groupBuy.pskuOptionAdd')"
                    size="small"
                    class="w-44"
                    @update:model-value="(v: string) => addOption(v)"
                />
                <span v-if="!optionTypes.length" class="text-xs text-muted-color">{{ t('groupBuy.pskuOptionNone') }}</span>
            </div>
            <NuxtLink to="/group-buy-option-types" class="w-fit text-[11px] text-primary hover:underline">{{ t('groupBuy.pskuManageTypes') }}</NuxtLink>
            <p v-if="errors.optionTypeCodes" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.optionTypeCodes }}</p>
        </div>

        <!-- 단품 표 -->
        <p v-if="errors.skus" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.skus }}</p>
        <div class="overflow-x-auto">
            <table class="w-full min-w-[40rem] border-collapse text-[13px]">
                <thead>
                    <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                        <th class="w-24 py-1.5 pr-2 font-normal">{{ t('groupBuy.pskuId') }}</th>
                        <th v-for="code in optionTypes" :key="`h-${code}`" class="w-32 py-1.5 pr-2 font-normal">{{ typeName(code) }}</th>
                        <th class="py-1.5 pr-2 font-normal">{{ t('groupBuy.pskuName') }}</th>
                        <th v-if="showSupply" class="w-32 py-1.5 pr-2 font-normal">{{ t('groupBuy.baseSupplyPrice') }}</th>
                        <th class="w-16 py-1.5 pr-2 font-normal">{{ t('groupBuy.skuOnSale') }}</th>
                        <th v-if="!ro" class="w-24 py-1.5 font-normal"><span class="sr-only">{{ t('groupBuy.pskuRowActions') }}</span></th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="(row, k) in model" :key="row.key" class="border-b border-(--line) align-top last:border-b-0" :class="row.onSale ? '' : 'text-muted-color'">
                        <td class="py-1.5 pr-2 font-mono text-xs tabular-nums" :class="row.id ? '' : 'text-muted-color'">{{ row.id ?? t('groupBuy.pskuIdAuto') }}</td>
                        <td v-for="(code, i) in optionTypes" :key="`v-${code}`" class="py-1.5 pr-2">
                            <AutoComplete
                                :model-value="row.values[i] ?? ''"
                                :suggestions="suggestions"
                                dropdown
                                :input-id="`${baseId}-v-${row.key}-${i}`"
                                :aria-label="t('groupBuy.pskuValueFor', { option: typeName(code), n: k + 1 })"
                                :maxlength="UtilGroupBuy.SKU.valueNameMax"
                                :disabled="ro"
                                :invalid="!(row.values[i] ?? '').trim()"
                                size="small"
                                fluid
                                @complete="(e: { query: string }) => suggest(code, e.query, i)"
                                @update:model-value="(v: string) => setValue(k, i, typeof v === 'string' ? v : '')"
                            />
                        </td>
                        <td class="py-1.5 pr-2">
                            <InputText :model-value="row.name" :aria-label="t('groupBuy.pskuNameFor', { n: k + 1 })" maxlength="100" :placeholder="autoName(row) || t('groupBuy.pskuNamePlaceholder')" :readonly="ro" :invalid="!!rowError(k)" size="small" class="w-full" @update:model-value="(v: string | undefined) => patch(k, { name: v ?? '' })" />
                            <span v-if="rowError(k)" class="mt-0.5 block text-[11px] text-red-600 dark:text-red-400">{{ rowError(k) }}</span>
                        </td>
                        <td v-if="showSupply" class="py-1.5 pr-2">
                            <InputText :model-value="row.supplyPrice" :aria-label="t('groupBuy.comboSupplyFor', { name: displayName(row) || k + 1 })" inputmode="numeric" :placeholder="t('groupBuy.supplyOptional')" :readonly="disabled || !supplyEditable" :invalid="!!row.supplyPrice.trim() && UtilGroupBuy.int(row.supplyPrice) === null" size="small" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(k, { supplyPrice: v ?? '' })" />
                        </td>
                        <td class="py-1.5 pr-2">
                            <ToggleSwitch :model-value="row.onSale" :disabled="disabled" :aria-label="t('groupBuy.comboSaleFor', { name: displayName(row) || k + 1 })" @update:model-value="(v: boolean) => patch(k, { onSale: v })" />
                        </td>
                        <td v-if="!ro" class="py-1.5">
                            <div class="flex items-center">
                                <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" class="size-7!" :aria-label="t('groupBuy.pskuMoveUp', { n: k + 1 })" :disabled="k === 0" @click="move(k, -1)" />
                                <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" class="size-7!" :aria-label="t('groupBuy.pskuDuplicate', { n: k + 1 })" @click="duplicate(k)" />
                                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" class="size-7!" :aria-label="t('groupBuy.pskuRemove', { n: k + 1 })" :disabled="model.length <= 1" @click="remove(k)" />
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <div class="flex flex-wrap items-center gap-2">
            <Button v-if="!ro" type="button" :label="t('groupBuy.pskuAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="model.length >= UtilGroupBuy.SKU.comboMax" @click="add" />
            <span class="text-xs text-muted-color">{{ t('groupBuy.pskuCount', { n: model.length, on: model.filter((r) => r.onSale).length }) }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyProductSkuRow } from '~/components/groupbuy/GroupBuySkuEditor.types';
import type { GroupBuyOptionType } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyProductSkuRow[]>({ required: true });
/** 고른 옵션 종류 코드(차례) */
const optionTypes = defineModel<string[]>('optionTypes', { required: true });

interface Props {
    /** 옵션 종류 목록(이름 · 숨김 · 이미 쓴 값) */
    types?: GroupBuyOptionType[];
    /** 모집 중 공구가 쓰는 상품 — 옵션 종류·단품 구성 잠금 */
    locked?: boolean;
    disabled?: boolean;
    showSupply?: boolean;
    supplyEditable?: boolean;
    /** 서버 칸 오류(`optionTypeCodes` · `skus`) */
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { types: () => [], locked: false, disabled: false, showSupply: false, supplyEditable: false, errors: () => ({}) });

const { t } = useI18n();
const baseId = useId();
let seq = 0;

const ro = computed(() => props.locked || props.disabled);

function typeName(code: string): string {
    return props.types.find((x) => x.code === code)?.name ?? code;
}

/** 더할 수 있는 옵션 종류 — 숨긴 것·이미 고른 것은 뺀다 */
const typeChoices = computed(() => props.types.filter((x) => !x.hidden && !optionTypes.value.includes(x.code)).map((x) => ({ value: x.code, label: x.name })));

/** 값 자동완성 — 그 종류로 이미 쓴 값 + 이 상품의 다른 줄 값, 친 글자가 든 것 */
const suggestions = ref<string[]>([]);
function suggest(code: string, query: string, i: number): void {
    const used = props.types.find((x) => x.code === code)?.usedValues ?? [];
    const here = model.value.map((r) => (r.values[i] ?? '').trim()).filter(Boolean);
    const q = query.trim().toLowerCase();
    suggestions.value = [...new Set([...here, ...used])].filter((v) => !q || v.toLowerCase().includes(q)).slice(0, 20);
}

/** 옵션 값으로 만든 이름(「블랙 / M」) — 옵션이 없으면 빈 글자 */
function autoName(row: GroupBuyProductSkuRow): string {
    return optionTypes.value.length ? UtilGroupBuy.skuMainName(optionTypes.value.map((_, i) => row.values[i] ?? '')) : '';
}

function displayName(row: GroupBuyProductSkuRow): string {
    return row.name.trim() || autoName(row);
}

/** 줄 — 이름 없음 · 이름 겹침 · 옵션 값 묶음 겹침(판정은 서버) */
function rowError(k: number): string {
    const row = model.value[k];
    if (!row) return '';
    if (!displayName(row)) return t('groupBuy.pskuNameBlank');
    if (UtilGroupBuy.repeatedNames(model.value.map(displayName)).includes(k)) return t('groupBuy.pskuNameRepeated');
    if (optionTypes.value.length && UtilGroupBuy.repeatedNames(model.value.map((r) => optionTypes.value.map((_, i) => (r.values[i] ?? '').trim()).join('\u0001'))).includes(k)) return t('groupBuy.pskuValuesRepeated');
    return '';
}

function patch(k: number, change: Partial<GroupBuyProductSkuRow>): void {
    model.value = model.value.map((r, x) => (x === k ? { ...r, ...change } : r));
}

function setValue(k: number, i: number, value: string): void {
    const row = model.value[k];
    if (!row) return;
    patch(k, { values: optionTypes.value.map((_, x) => (x === i ? value : (row.values[x] ?? ''))) });
}

function nextKey(): string {
    seq += 1;
    return `psku-${Date.now()}-${seq}`;
}

function add(): void {
    model.value = [...model.value, { key: nextKey(), id: null, name: '', values: optionTypes.value.map(() => ''), onSale: true, supplyPrice: '' }];
}

/** 줄 복사 — 옵션 값 하나만 바꿔 넣기 쉽게(이름은 비운다) */
function duplicate(k: number): void {
    const row = model.value[k];
    if (!row) return;
    const copy = { ...row, key: nextKey(), id: null, name: '', values: [...row.values] };
    model.value = [...model.value.slice(0, k + 1), copy, ...model.value.slice(k + 1)];
}

function remove(k: number): void {
    if (model.value.length <= 1) return;
    model.value = model.value.filter((_, x) => x !== k);
}

function move(k: number, delta: number): void {
    const to = k + delta;
    if (to < 0 || to >= model.value.length) return;
    const next = [...model.value];
    [next[k], next[to]] = [next[to]!, next[k]!];
    model.value = next;
}

function addOption(code: string): void {
    if (!code || optionTypes.value.length >= UtilGroupBuy.SKU.axisMax || optionTypes.value.includes(code)) return;
    optionTypes.value = [...optionTypes.value, code];
    model.value = model.value.map((r) => ({ ...r, values: [...r.values, ''] }));
}

/** 옵션 종류를 빼면 그 열의 값도 뺀다 */
function removeOption(i: number): void {
    optionTypes.value = optionTypes.value.filter((_, x) => x !== i);
    model.value = model.value.map((r) => ({ ...r, values: r.values.filter((_, x) => x !== i) }));
}
</script>
