<template>
    <!--
        공구 「옵션」 구획 본문(결정 88) — 옵션 축 0~3개(이름 · 값 여러 개, 차례 바꾸기 · 지우기) → 축 값 조합마다 MAIN 단품 한 줄이 생기는 조합 표.
        · 조합 표: 이름 미리 보기(「블랙 / M」 — 서버가 같은 규칙으로 만든다) · 판매가(공구가 + 추가금) · 추가금 · 한도(비우면 공구 최대만) · 판매 여부(필요 없는 조합은 끈다).
          일괄 입력 도우미로 전체 또는 축 값 하나(예 「사이즈: XL」)에 든 조합에 추가금·한도를 한 번에 넣는다.
        · 축을 고치면 조합을 다시 만든다 — 값 조합이 같은 줄은 입력을 살리고, 새 조합은 그 조합을 품은 이전 줄의 추가금·판매 여부를 이어받는다.
        · 축이 없으면 「단품 하나(공구 전체)」 — 공구가·공구 최대로 판다.
        · 모집 뒤(`locked`)에는 축·값·조합 구성과 추가금·판매 여부가 잠긴다(읽기 전용 + 확정·결제 중). 한도는 수량 화면에서 단품마다 추가 입고·줄이기.
        · 결정 89: `mode="PRODUCT"`(상품 화면)는 축·조합 구성 + 기본 공급가 · 판매 여부만, 공구 화면은 축이 상품에서 오므로 `axes-locked` 로 축을 잠그고 조합마다 공구 설정(추가금·한도·판매 여부·공급가)만 고친다.
          공급가 칸은 가격 권한(`show-supply`)이 있을 때만 보이고, 모집 뒤에도 고친다(`supply-editable`). 판매가 < 공급가면 줄에 「역마진」.
        서버 오류는 `errors` 의 `axes` · `skus` 키(구획 단위 — 서버 2b-2)로 받고, 빈 값·이름 겹침·숫자 모양은 화면이 줄마다 미리 알린다. 서버 호출 없음.
    -->
    <div class="flex flex-col gap-3">
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ locked ? t('groupBuy.skuLockedHelp') : axesLocked ? t('groupBuy.axisFromProduct') : t('groupBuy.axisHelp', { max: UtilGroupBuy.SKU.axisMax }) }}</p>
        <p v-if="errors.axes" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.axes }}</p>

        <!-- 축 -->
        <section v-for="(axis, i) in axes" :key="axis.key" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-3" :aria-label="t('groupBuy.axisN', { n: i + 1 })">
            <div class="flex flex-wrap items-start gap-2">
                <div class="flex flex-col gap-1">
                    <label :for="`${baseId}-axis-${axis.key}`" class="text-[13px] font-medium">{{ t('groupBuy.axisN', { n: i + 1 }) }}</label>
                    <InputText
                        :id="`${baseId}-axis-${axis.key}`"
                        :model-value="axis.name"
                        :maxlength="UtilGroupBuy.SKU.axisNameMax"
                        :placeholder="t('groupBuy.axisNamePlaceholder')"
                        :readonly="axesRo"
                        :invalid="!!axisNameError(i)"
                        class="w-48"
                        @update:model-value="(v: string | undefined) => patchAxis(i, { name: v ?? '' })"
                    />
                </div>
                <div v-if="!axesRo" class="ml-auto flex items-center gap-1">
                    <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.axisMoveUp', { n: i + 1 })" :disabled="i === 0" @click="moveAxis(i, -1)" />
                    <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.axisMoveDown', { n: i + 1 })" :disabled="i >= axes.length - 1" @click="moveAxis(i, 1)" />
                    <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.axisRemove', { n: i + 1 })" @click="removeAxis(i)" />
                </div>
            </div>
            <p v-if="axisNameError(i)" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ axisNameError(i) }}</p>

            <!-- 값 -->
            <div class="flex flex-col gap-1.5">
                <span class="text-xs text-muted-color">{{ t('groupBuy.axisValues', { n: axis.values.length, max: UtilGroupBuy.SKU.valueMax }) }}</span>
                <ul v-if="axis.values.length" class="m-0 flex list-none flex-wrap gap-1.5 p-0">
                    <li v-for="(value, j) in axis.values" :key="value.key" class="inline-flex items-center gap-0.5 rounded-md border px-1 py-0.5" :class="valueError(i, j) ? 'border-red-400 dark:border-red-700' : 'border-(--line)'">
                        <InputText
                            :model-value="value.name"
                            :maxlength="UtilGroupBuy.SKU.valueNameMax"
                            :aria-label="t('groupBuy.axisValueN', { axis: axisLabel(i), n: j + 1 })"
                            :readonly="axesRo"
                            :invalid="!!valueError(i, j)"
                            size="small"
                            class="w-24"
                            @update:model-value="(v: string | undefined) => patchValue(i, j, v ?? '')"
                        />
                        <template v-if="!axesRo">
                            <Button type="button" icon="pi pi-angle-left" size="small" severity="secondary" variant="text" class="size-7!" :aria-label="t('groupBuy.axisValueMoveUp', { name: value.name || j + 1 })" :disabled="j === 0" @click="moveValue(i, j, -1)" />
                            <Button type="button" icon="pi pi-times" size="small" severity="danger" variant="text" class="size-7!" :aria-label="t('groupBuy.axisValueRemove', { name: value.name || j + 1 })" @click="removeValue(i, j)" />
                        </template>
                    </li>
                </ul>
                <p v-else class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('groupBuy.axisValuesEmpty') }}</p>
                <p v-for="j in valueErrorIndexes(i)" :key="`ve-${j}`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('groupBuy.axisValueErrorLine', { name: axis.values[j]?.name || j + 1, message: valueError(i, j) }) }}</p>
                <div v-if="!ro && axis.values.length < UtilGroupBuy.SKU.valueMax" class="flex flex-wrap items-center gap-2">
                    <InputText
                        :model-value="pending[axis.key] ?? ''"
                        :placeholder="t('groupBuy.axisValueAddPlaceholder')"
                        :aria-label="t('groupBuy.axisValueAddFor', { axis: axisLabel(i) })"
                        size="small"
                        class="w-full sm:w-72"
                        @update:model-value="(v: string | undefined) => (pending = { ...pending, [axis.key]: v ?? '' })"
                        @keydown.enter.prevent="addValues(i)"
                    />
                    <Button type="button" :label="t('groupBuy.axisValueAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="!(pending[axis.key] ?? '').trim()" @click="addValues(i)" />
                </div>
            </div>
        </section>

        <div v-if="!axesRo">
            <Button type="button" :label="t('groupBuy.axisAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="axes.length >= UtilGroupBuy.SKU.axisMax" @click="addAxis" />
            <span v-if="axes.length >= UtilGroupBuy.SKU.axisMax" class="ml-2 text-xs text-muted-color">{{ t('groupBuy.axisMaxReached', { max: UtilGroupBuy.SKU.axisMax }) }}</span>
        </div>

        <!-- 축 없음: 단품 하나 -->
        <Message v-if="!comboAxes.length" severity="secondary" :closable="false" size="small">{{ t('groupBuy.skuSingleHelp') }}</Message>

        <!-- 조합 표 -->
        <template v-else>
            <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span class="text-[13px] font-semibold">{{ t('groupBuy.comboTitle') }}</span>
                <span class="text-xs text-muted-color">{{ t('groupBuy.comboCount', { n: mains.length, on: onSaleCount }) }}</span>
                <span v-if="mains.length > UtilGroupBuy.SKU.comboMax" class="text-xs text-red-600 dark:text-red-400">{{ t('groupBuy.comboTooMany', { max: UtilGroupBuy.SKU.comboMax }) }}</span>
            </div>
            <p v-if="errors.skus" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.skus }}</p>
            <p v-else-if="!onSaleCount" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('groupBuy.comboNoneOnSale') }}</p>

            <!-- 일괄 입력 도우미 -->
            <div v-if="!ro" class="flex flex-wrap items-end gap-2 rounded-md bg-surface-50 px-3 py-2 dark:bg-surface-900">
                <div class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-target`" class="text-xs font-medium">{{ t('groupBuy.bulkTarget') }}</label>
                    <Select :input-id="`${baseId}-bulk-target`" v-model="bulkTarget" :options="bulkTargets" option-label="label" option-value="value" size="small" class="w-48" />
                </div>
                <div v-if="!product" class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-extra`" class="text-xs font-medium">{{ t('groupBuy.optionExtra') }}</label>
                    <InputText :id="`${baseId}-bulk-extra`" v-model="bulkExtra" inputmode="numeric" size="small" :placeholder="t('groupBuy.bulkKeep')" class="w-28 text-right tabular-nums" />
                </div>
                <div v-if="!product" class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-max`" class="text-xs font-medium">{{ t('groupBuy.optionMax') }}</label>
                    <InputText :id="`${baseId}-bulk-max`" v-model="bulkMax" inputmode="numeric" size="small" :placeholder="t('groupBuy.bulkKeep')" class="w-28 text-right tabular-nums" />
                </div>
                <div v-if="showSupply" class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-supply`" class="text-xs font-medium">{{ supplyLabel }}</label>
                    <InputText :id="`${baseId}-bulk-supply`" v-model="bulkSupply" inputmode="numeric" size="small" :placeholder="t('groupBuy.bulkKeep')" class="w-28 text-right tabular-nums" />
                </div>
                <div class="flex flex-col gap-1">
                    <label :for="`${baseId}-bulk-sale`" class="text-xs font-medium">{{ t('groupBuy.skuOnSale') }}</label>
                    <Select :input-id="`${baseId}-bulk-sale`" v-model="bulkSale" :options="bulkSaleOptions" option-label="label" option-value="value" size="small" class="w-32" />
                </div>
                <Button type="button" :label="t('groupBuy.bulkApply')" size="small" severity="secondary" :disabled="!bulkReady" @click="applyBulk" />
                <p class="m-0 w-full text-[11px] text-muted-color">{{ t('groupBuy.bulkHelp') }}</p>
            </div>

            <div class="overflow-x-auto">
                <table class="w-full min-w-[38rem] border-collapse text-[13px]">
                    <thead>
                        <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                            <th class="py-1.5 pr-2 font-normal">{{ t('groupBuy.comboName') }}</th>
                            <template v-if="!product">
                                <th class="w-28 py-1.5 pr-2 text-right font-normal">{{ t('groupBuy.skuPrice') }}</th>
                                <th class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionExtra') }}</th>
                                <th class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionMax') }}</th>
                            </template>
                            <th v-if="showSupply" class="w-32 py-1.5 pr-2 font-normal">{{ supplyLabel }}</th>
                            <th class="w-16 py-1.5 pr-2 font-normal">{{ t('groupBuy.skuOnSale') }}</th>
                            <th v-if="locked && !product" class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionSold') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(row, k) in mains" :key="row.key" class="border-b border-(--line) align-top last:border-b-0" :class="row.onSale ? '' : 'text-muted-color'">
                            <td class="py-1.5 pr-2">
                                <span :class="row.onSale ? 'font-medium' : 'line-through'">{{ nameOf(row) || t('groupBuy.comboNameBlank') }}</span>
                                <Tag v-if="!row.onSale" :value="t('groupBuy.skuOffSale')" severity="secondary" class="ml-1" />
                            </td>
                            <template v-if="!product">
                            <td class="py-1.5 pr-2 text-right tabular-nums">{{ priceOf(row) }}</td>
                            <td class="py-1.5 pr-2">
                                <InputText :model-value="row.extraPrice" :aria-label="t('groupBuy.comboExtraFor', { name: nameOf(row) || k + 1 })" inputmode="numeric" :readonly="ro" :invalid="!numberOk(row.extraPrice, false)" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patchMain(k, { extraPrice: v ?? '' })" />
                            </td>
                            <td class="py-1.5 pr-2">
                                <InputText :model-value="row.maxQty" :aria-label="t('groupBuy.comboMaxFor', { name: nameOf(row) || k + 1 })" inputmode="numeric" :placeholder="t('groupBuy.optionUnlimited')" :readonly="ro" :invalid="!numberOk(row.maxQty, true)" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patchMain(k, { maxQty: v ?? '' })" />
                            </td>
                            </template>
                            <td v-if="showSupply" class="py-1.5 pr-2">
                                <InputText :model-value="row.supplyPrice" :aria-label="t('groupBuy.comboSupplyFor', { name: nameOf(row) || k + 1 })" inputmode="numeric" :placeholder="product ? t('groupBuy.supplyOptional') : t('groupBuy.supplyRequired')" :readonly="supplyRo" :invalid="!numberOk(row.supplyPrice, true)" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patchMain(k, { supplyPrice: v ?? '' })" />
                                <span v-if="!product && marginLoss(row)" class="mt-0.5 block text-[11px] text-red-600 dark:text-red-400">{{ t('groupBuy.marginLoss') }}</span>
                            </td>
                            <td class="py-1.5 pr-2">
                                <ToggleSwitch :model-value="row.onSale" :disabled="ro" :aria-label="t('groupBuy.comboSaleFor', { name: nameOf(row) || k + 1 })" @update:model-value="(v: boolean) => patchMain(k, { onSale: v })" />
                            </td>
                            <td v-if="locked && !product" class="py-1.5 pr-2 text-xs tabular-nums text-muted-color">{{ t('groupBuy.optionSoldLine', { current: row.currentQty, reserved: row.reservedQty }) }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <p class="m-0 text-xs text-muted-color">{{ product ? t('groupBuy.productComboHelp') : t('groupBuy.comboHelp') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyAxisRow, GroupBuyMainSkuRow } from '~/components/groupbuy/GroupBuySkuEditor.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const axes = defineModel<GroupBuyAxisRow[]>('axes', { required: true });
const mains = defineModel<GroupBuyMainSkuRow[]>('mains', { required: true });

interface Props {
    /** 공구가 글자(판매가 미리 보기 — 비었거나 숫자가 아니면 「—」) */
    groupPrice?: string;
    /** 모집 뒤 — 구성 잠김 */
    locked?: boolean;
    disabled?: boolean;
    /** 서버 칸 오류(`axes` · `skus`) */
    errors?: Record<string, string>;
    /** `PRODUCT` = 상품 화면(축·조합 구성 + 기본 공급가 · 판매 여부만) — 결정 89 */
    mode?: 'GROUP_BUY' | 'PRODUCT';
    /** 공구 화면 — 축은 상품에서 와서 고치지 않는다(조합 설정만) */
    axesLocked?: boolean;
    /** 공급가 칸을 보이는가(가격 권한) */
    showSupply?: boolean;
    /** 공급가를 고칠 수 있는가(모집 뒤에도 — 결정 89) */
    supplyEditable?: boolean;
}

const props = withDefaults(defineProps<Props>(), { groupPrice: '', locked: false, disabled: false, errors: () => ({}), mode: 'GROUP_BUY', axesLocked: false, showSupply: false, supplyEditable: false });

const { t, locale } = useI18n();
const baseId = useId();
let seq = 0;

const ro = computed(() => props.locked || props.disabled);
const product = computed(() => props.mode === 'PRODUCT');
const axesRo = computed(() => ro.value || props.axesLocked);
const supplyRo = computed(() => props.disabled || !props.supplyEditable);
const supplyLabel = computed(() => (product.value ? t('groupBuy.baseSupplyPrice') : t('groupBuy.supplyPrice')));

/** 판매가 < 공급가(역마진) — 서버 확인 전 미리 보기 */
function marginLoss(row: GroupBuyMainSkuRow): boolean {
    const base = UtilGroupBuy.int(props.groupPrice);
    const extra = UtilGroupBuy.int(row.extraPrice);
    const supply = UtilGroupBuy.int(row.supplyPrice);
    return base !== null && extra !== null && supply !== null && base + extra < supply;
}

/** 조합에 드는 축 — 값이 하나 이상인 것(값이 빈 축은 아직 조합을 만들지 않는다) */
const comboAxes = computed(() => axes.value.filter((a) => a.values.length > 0));
const onSaleCount = computed(() => mains.value.filter((m) => m.onSale).length);

/** 값 키 → 이름 */
const valueName = computed(() => new Map(axes.value.flatMap((a) => a.values.map((v) => [v.key, v.name] as const))));

function nameOf(row: GroupBuyMainSkuRow): string {
    return UtilGroupBuy.skuMainName(row.valueKeys.map((k) => valueName.value.get(k) ?? ''));
}

function priceOf(row: GroupBuyMainSkuRow): string {
    const base = UtilGroupBuy.int(props.groupPrice);
    const extra = UtilGroupBuy.int(row.extraPrice);
    return base === null || extra === null ? '—' : t('groupBuy.wonAmount', { amount: UtilGroupBuy.won(base + extra, locale.value) });
}

function axisLabel(i: number): string {
    return axes.value[i]?.name.trim() || t('groupBuy.axisN', { n: i + 1 });
}

/** 숫자 칸 모양 — 0 이상 정수(한도는 비워도 된다). 판정은 서버 */
function numberOk(text: string, blankOk: boolean): boolean {
    return (blankOk && text.trim() === '') || UtilGroupBuy.int(text) !== null;
}

function axisNameError(i: number): string {
    if (!axes.value[i]?.name.trim()) return t('groupBuy.axisNameBlank');
    return UtilGroupBuy.repeatedNames(axes.value.map((a) => a.name)).includes(i) ? t('groupBuy.axisNameRepeated') : '';
}

function valueError(i: number, j: number): string {
    const axis = axes.value[i];
    if (!axis) return '';
    if (!axis.values[j]?.name.trim()) return t('groupBuy.axisValueBlank');
    return UtilGroupBuy.repeatedNames(axis.values.map((v) => v.name)).includes(j) ? t('groupBuy.axisValueRepeated') : '';
}

function valueErrorIndexes(i: number): number[] {
    return (axes.value[i]?.values ?? []).flatMap((_, j) => (valueError(i, j) ? [j] : []));
}

function nextKey(prefix: string): string {
    seq += 1;
    return `${prefix}-${Date.now()}-${seq}`;
}

/** 축이 바뀐 뒤 조합 줄을 다시 만든다 — 같은 조합은 그대로, 새 조합은 그 조합을 품은 이전 줄의 추가금·판매 여부를 잇는다 */
function regenerate(nextAxes: GroupBuyAxisRow[]): GroupBuyMainSkuRow[] {
    const active = nextAxes.filter((a) => a.values.length > 0);
    const before = new Map(mains.value.map((m) => [m.key, m]));
    return UtilGroupBuy.skuCombinations(active.map((a) => a.values.map((v) => v.key))).map((valueKeys) => {
        const key = `main:${valueKeys.join('|')}`;
        const same = before.get(key);
        if (same) return same;
        const parent = mains.value.find((m) => m.valueKeys.length > 0 && m.valueKeys.every((k) => valueKeys.includes(k)));
        return { key, id: null, valueKeys, extraPrice: parent?.extraPrice ?? '0', maxQty: '', onSale: parent?.onSale ?? true, supplyPrice: parent?.supplyPrice ?? '', currentQty: 0, reservedQty: 0 };
    });
}

function setAxes(next: GroupBuyAxisRow[]): void {
    const nextMains = regenerate(next);
    axes.value = next;
    mains.value = nextMains;
}

function patchAxis(i: number, change: Partial<GroupBuyAxisRow>): void {
    // 이름만 바뀌면 조합은 그대로(키가 값 키라서)
    axes.value = axes.value.map((a, x) => (x === i ? { ...a, ...change } : a));
}

function addAxis(): void {
    if (axes.value.length >= UtilGroupBuy.SKU.axisMax) return;
    setAxes([...axes.value, { key: nextKey('axis'), id: null, name: '', values: [] }]);
}

function removeAxis(i: number): void {
    setAxes(axes.value.filter((_, x) => x !== i));
}

function moveAxis(i: number, delta: number): void {
    const to = i + delta;
    if (to < 0 || to >= axes.value.length) return;
    const next = [...axes.value];
    [next[i], next[to]] = [next[to]!, next[i]!];
    setAxes(next);
}

// 값 더하기 입력(축마다)
const pending = ref<Record<string, string>>({});

function addValues(i: number): void {
    const axis = axes.value[i];
    if (!axis) return;
    const room = UtilGroupBuy.SKU.valueMax - axis.values.length;
    // 쉼표로 나눠 여러 개 — 빈 것·이미 있는 이름(공백 무시)은 건너뛴다
    const seen = new Set(axis.values.map((v) => UtilGroupBuy.noticeNameKey(v.name)));
    const names: string[] = [];
    for (const part of (pending.value[axis.key] ?? '').split(',')) {
        const name = part.trim().slice(0, UtilGroupBuy.SKU.valueNameMax);
        const key = UtilGroupBuy.noticeNameKey(name);
        if (!key || seen.has(key) || names.length >= room) continue;
        seen.add(key);
        names.push(name);
    }
    if (!names.length) return;
    setAxes(axes.value.map((a, x) => (x === i ? { ...a, values: [...a.values, ...names.map((name) => ({ key: nextKey('value'), id: null, name }))] } : a)));
    pending.value = { ...pending.value, [axis.key]: '' };
}

function patchValue(i: number, j: number, name: string): void {
    axes.value = axes.value.map((a, x) => (x === i ? { ...a, values: a.values.map((v, y) => (y === j ? { ...v, name } : v)) } : a));
}

function removeValue(i: number, j: number): void {
    setAxes(axes.value.map((a, x) => (x === i ? { ...a, values: a.values.filter((_, y) => y !== j) } : a)));
}

function moveValue(i: number, j: number, delta: number): void {
    const axis = axes.value[i];
    const to = j + delta;
    if (!axis || to < 0 || to >= axis.values.length) return;
    const values = [...axis.values];
    [values[j], values[to]] = [values[to]!, values[j]!];
    // 조합 표 차례도 값 차례를 따른다(줄은 키로 살린다)
    setAxes(axes.value.map((a, x) => (x === i ? { ...a, values } : a)));
}

function patchMain(k: number, change: Partial<GroupBuyMainSkuRow>): void {
    mains.value = mains.value.map((m, x) => (x === k ? { ...m, ...change } : m));
}

// ── 일괄 입력 ──
const ALL = '*';
const bulkTarget = ref<string>(ALL);
const bulkExtra = ref('');
const bulkMax = ref('');
const bulkSupply = ref('');
const bulkSale = ref<'KEEP' | 'ON' | 'OFF'>('KEEP');

const bulkTargets = computed(() => [
    { value: ALL, label: t('groupBuy.bulkAll') },
    ...comboAxes.value.flatMap((a, i) => a.values.map((v) => ({ value: v.key, label: t('groupBuy.bulkValue', { axis: a.name.trim() || t('groupBuy.axisN', { n: i + 1 }), value: v.name.trim() || '—' }) }))),
]);
const bulkSaleOptions = computed(() => (['KEEP', 'ON', 'OFF'] as const).map((v) => ({ value: v, label: t(`groupBuy.bulkSale.${v}`) })));
const bulkReady = computed(() => bulkExtra.value.trim() !== '' || bulkMax.value.trim() !== '' || bulkSupply.value.trim() !== '' || bulkSale.value !== 'KEEP');

// 고른 값이 지워지면 전체로 돌린다
watch(bulkTargets, (list) => {
    if (!list.some((o) => o.value === bulkTarget.value)) bulkTarget.value = ALL;
});

function applyBulk(): void {
    if (!bulkReady.value) return;
    const target = bulkTarget.value;
    mains.value = mains.value.map((m) => {
        if (target !== ALL && !m.valueKeys.includes(target)) return m;
        return {
            ...m,
            extraPrice: bulkExtra.value.trim() !== '' ? bulkExtra.value.trim() : m.extraPrice,
            maxQty: bulkMax.value.trim() !== '' ? bulkMax.value.trim() : m.maxQty,
            supplyPrice: bulkSupply.value.trim() !== '' ? bulkSupply.value.trim() : m.supplyPrice,
            onSale: bulkSale.value === 'KEEP' ? m.onSale : bulkSale.value === 'ON',
        };
    });
    bulkExtra.value = '';
    bulkMax.value = '';
    bulkSupply.value = '';
    bulkSale.value = 'KEEP';
}
</script>
