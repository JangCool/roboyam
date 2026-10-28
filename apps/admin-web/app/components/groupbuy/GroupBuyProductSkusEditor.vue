<template>
    <!--
        상품 「단품」 구획 본문(사용자 결정 2026-10-28 — 단품 목록이 정본, 옵션 이름은 단품의 속성). 결정 88 의 「축·값 → 조합 자동 생성」을 대신한다.
        · 단품 = 실제로 파는 아이템 한 줄(이름 · 판매 여부 · 기본 공급가). 파는 것만 등록한다(조합을 만들고 끄지 않는다). 단품이 하나면 옵션 없는 상품.
        · 옵션 이름(선택, 최대 3 — 예 색상·사이즈)을 두면 단품마다 그 값을 적는다 → 회원 상세는 옵션 이름 차례로 고른다(앞 옵션을 고르면 없는 조합은 회색). 비우면 단품 목록 하나에서 고른다.
        · 이름은 옵션 값이 있으면 「블랙 / M」으로 채우고(고칠 수 있다), 없으면 직접 적는다. 같은 이름 · 같은 옵션 값 묶음 두 줄은 막는다.
        · 모집 중 공구가 쓰면(`locked`) 옵션 이름·단품 구성이 잠긴다 — 판매 여부·기본 공급가도 잠금(새 단품은 다음 공구부터, 서버 2b-2 LOCKED_FIELD `options`).
        기본 공급가 칸은 가격 권한(`show-supply`)이 있을 때만. 서버 오류는 `errors.optionNames`·`errors.skus`.
    -->
    <div class="flex flex-col gap-3">
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ locked ? t('groupBuy.pskuLockedHelp') : t('groupBuy.pskuHelp') }}</p>

        <!-- 옵션 이름(속성) -->
        <div class="flex flex-col gap-1.5">
            <span class="text-[13px] font-medium">{{ t('groupBuy.pskuOptionNames', { max: UtilGroupBuy.SKU.axisMax }) }}</span>
            <div class="flex flex-wrap items-center gap-2">
                <div v-for="(name, i) in optionNames" :key="`on-${i}`" class="inline-flex items-center gap-0.5">
                    <InputText :model-value="name" :aria-label="t('groupBuy.pskuOptionNameN', { n: i + 1 })" :maxlength="UtilGroupBuy.SKU.axisNameMax" :placeholder="t('groupBuy.axisNamePlaceholder')" :readonly="ro" :invalid="!!optionNameError(i)" size="small" class="w-32" @update:model-value="(v: string | undefined) => renameOption(i, v ?? '')" />
                    <Button v-if="!ro" type="button" icon="pi pi-times" size="small" severity="danger" variant="text" class="size-7!" :aria-label="t('groupBuy.pskuOptionRemove', { name: name || i + 1 })" @click="removeOption(i)" />
                </div>
                <Button v-if="!ro && optionNames.length < UtilGroupBuy.SKU.axisMax" type="button" :label="t('groupBuy.pskuOptionAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="addOption" />
                <span v-if="!optionNames.length" class="text-xs text-muted-color">{{ t('groupBuy.pskuOptionNone') }}</span>
            </div>
            <p v-for="i in optionNames.map((_, x) => x).filter((x) => optionNameError(x))" :key="`one-${i}`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ optionNameError(i) }}</p>
            <p v-if="errors.optionNames" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.optionNames }}</p>
        </div>

        <!-- 단품 표 -->
        <p v-if="errors.skus" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.skus }}</p>
        <div class="overflow-x-auto">
            <table class="w-full min-w-[34rem] border-collapse text-[13px]">
                <thead>
                    <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                        <th v-for="(name, i) in optionNames" :key="`h-${i}`" class="w-28 py-1.5 pr-2 font-normal">{{ name || t('groupBuy.axisN', { n: i + 1 }) }}</th>
                        <th class="py-1.5 pr-2 font-normal">{{ t('groupBuy.pskuName') }}</th>
                        <th v-if="showSupply" class="w-32 py-1.5 pr-2 font-normal">{{ t('groupBuy.baseSupplyPrice') }}</th>
                        <th class="w-16 py-1.5 pr-2 font-normal">{{ t('groupBuy.skuOnSale') }}</th>
                        <th v-if="!ro" class="w-24 py-1.5 font-normal"><span class="sr-only">{{ t('groupBuy.pskuRowActions') }}</span></th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="(row, k) in model" :key="row.key" class="border-b border-(--line) align-top last:border-b-0" :class="row.onSale ? '' : 'text-muted-color'">
                        <td v-for="(name, i) in optionNames" :key="`v-${i}`" class="py-1.5 pr-2">
                            <InputText :model-value="row.values[i] ?? ''" :aria-label="t('groupBuy.pskuValueFor', { option: name || i + 1, n: k + 1 })" :maxlength="UtilGroupBuy.SKU.valueNameMax" :readonly="ro" :invalid="!(row.values[i] ?? '').trim()" size="small" class="w-full" @update:model-value="(v: string | undefined) => setValue(k, i, v ?? '')" />
                        </td>
                        <td class="py-1.5 pr-2">
                            <InputText :model-value="row.name" :aria-label="t('groupBuy.pskuNameFor', { n: k + 1 })" maxlength="100" :placeholder="autoName(row) || t('groupBuy.pskuNamePlaceholder')" :readonly="ro" :invalid="!!rowError(k)" size="small" class="w-full" @update:model-value="(v: string | undefined) => patch(k, { name: v ?? '' })" />
                            <span v-if="rowError(k)" class="mt-0.5 block text-[11px] text-red-600 dark:text-red-400">{{ rowError(k) }}</span>
                        </td>
                        <td v-if="showSupply" class="py-1.5 pr-2">
                            <InputText :model-value="row.supplyPrice" :aria-label="t('groupBuy.comboSupplyFor', { name: displayName(row) || k + 1 })" inputmode="numeric" :placeholder="t('groupBuy.supplyOptional')" :readonly="ro || !supplyEditable" :invalid="!!row.supplyPrice.trim() && UtilGroupBuy.int(row.supplyPrice) === null" size="small" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(k, { supplyPrice: v ?? '' })" />
                        </td>
                        <td class="py-1.5 pr-2">
                            <ToggleSwitch :model-value="row.onSale" :disabled="ro" :aria-label="t('groupBuy.comboSaleFor', { name: displayName(row) || k + 1 })" @update:model-value="(v: boolean) => patch(k, { onSale: v })" />
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
        <div v-if="!ro" class="flex flex-wrap items-center gap-2">
            <Button type="button" :label="t('groupBuy.pskuAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="model.length >= UtilGroupBuy.SKU.comboMax" @click="add" />
            <span class="text-xs text-muted-color">{{ t('groupBuy.pskuCount', { n: model.length, on: model.filter((r) => r.onSale).length }) }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyProductSkuRow } from '~/components/groupbuy/GroupBuySkuEditor.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyProductSkuRow[]>({ required: true });
const optionNames = defineModel<string[]>('optionNames', { required: true });

interface Props {
    /** 모집 중 공구가 쓰는 상품 — 옵션 이름·단품 구성 잠금 */
    locked?: boolean;
    disabled?: boolean;
    showSupply?: boolean;
    supplyEditable?: boolean;
    /** 서버 칸 오류(`optionNames` · `skus`) */
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { locked: false, disabled: false, showSupply: false, supplyEditable: false, errors: () => ({}) });

const { t } = useI18n();
let seq = 0;

const ro = computed(() => props.locked || props.disabled);

/** 옵션 값으로 만든 이름(「블랙 / M」) — 값이 없으면 빈 글자 */
function autoName(row: GroupBuyProductSkuRow): string {
    return optionNames.value.length ? UtilGroupBuy.skuMainName(optionNames.value.map((_, i) => row.values[i] ?? '')) : '';
}

/** 보이는 이름 — 직접 적은 이름, 없으면 옵션 값 이름 */
function displayName(row: GroupBuyProductSkuRow): string {
    return row.name.trim() || autoName(row);
}

function optionNameError(i: number): string {
    if (!optionNames.value[i]?.trim()) return t('groupBuy.axisNameBlank');
    return UtilGroupBuy.repeatedNames(optionNames.value).includes(i) ? t('groupBuy.axisNameRepeated') : '';
}

/** 줄 — 이름 없음 · 이름 겹침 · 옵션 값 묶음 겹침(판정은 서버) */
function rowError(k: number): string {
    const row = model.value[k];
    if (!row) return '';
    if (!displayName(row)) return t('groupBuy.pskuNameBlank');
    if (UtilGroupBuy.repeatedNames(model.value.map(displayName)).includes(k)) return t('groupBuy.pskuNameRepeated');
    if (optionNames.value.length && UtilGroupBuy.repeatedNames(model.value.map((r) => optionNames.value.map((_, i) => (r.values[i] ?? '').trim()).join('\u0001'))).includes(k)) return t('groupBuy.pskuValuesRepeated');
    return '';
}

function patch(k: number, change: Partial<GroupBuyProductSkuRow>): void {
    model.value = model.value.map((r, x) => (x === k ? { ...r, ...change } : r));
}

function setValue(k: number, i: number, value: string): void {
    const row = model.value[k];
    if (!row) return;
    const values = optionNames.value.map((_, x) => (x === i ? value : (row.values[x] ?? '')));
    patch(k, { values });
}

function nextKey(): string {
    seq += 1;
    return `psku-${Date.now()}-${seq}`;
}

function add(): void {
    model.value = [...model.value, { key: nextKey(), id: null, name: '', values: optionNames.value.map(() => ''), onSale: true, supplyPrice: '' }];
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

function addOption(): void {
    if (optionNames.value.length >= UtilGroupBuy.SKU.axisMax) return;
    optionNames.value = [...optionNames.value, ''];
    model.value = model.value.map((r) => ({ ...r, values: [...r.values, ''] }));
}

function renameOption(i: number, name: string): void {
    optionNames.value = optionNames.value.map((n, x) => (x === i ? name : n));
}

/** 옵션 이름을 빼면 그 열의 값도 뺀다 */
function removeOption(i: number): void {
    optionNames.value = optionNames.value.filter((_, x) => x !== i);
    model.value = model.value.map((r) => ({ ...r, values: r.values.filter((_, x) => x !== i) }));
}
</script>
