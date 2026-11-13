<template>
    <!--
        여러 상품 가격 변경 — 「상품 가격」 탭 위 [여러 상품 가격 변경]에서 연다(`pages/ads/placements.vue`).
          상품 고르기(묶음별 전체 선택) → 단가 조정(비율 % 또는 정액 원 — 기준 = 각 상품의 지금 단가, 10원 단위 버림, 0원 아래 금지) → 적용 시각 하나 → 결과 표(상품 · 지금 → 새 단가) → [예약하기].
          고른 상품마다 예약 1건이 생긴다(단가만 바뀌고 나머지 칸은 그 시각 바로 앞 값 그대로 — 서버). 고른 상품 중 같은 시각 예약이 이미 있으면 칸 오류로 막는다.
        서버는 부르지 않는다 — 값은 `submit` 으로 올린다. 서버 칸 오류(`fields.effectiveAt`·`products`·`adjust.value`)는 그 칸 아래, 나머지는 아래 한 줄.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="t('ad.priceBulkTitle')" :style="{ width: 'min(44rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate data-testid="ad-price-bulk-dialog" @submit.prevent="submit">
            <!-- 상품 고르기 -->
            <fieldset class="m-0 flex flex-col gap-3 border-0 p-0" :aria-describedby="`${formId}-products-msg`">
                <legend class="mb-1 p-0 text-sm font-medium">{{ t('ad.priceBulkProducts') }}</legend>
                <div v-for="group in groups" :key="group.key" class="flex flex-col gap-2 rounded-lg border border-(--line) p-3">
                    <div class="flex items-center gap-2">
                        <Checkbox :model-value="allOf(group.key)" binary :input-id="`${formId}-all-${group.key}`" :disabled="busy" @update:model-value="(v: boolean) => toggleGroup(group.key, v)" />
                        <label :for="`${formId}-all-${group.key}`" class="text-sm font-medium">{{ t('ad.priceBulkGroupAll', { group: t(`ad.priceGroup.${group.key}`) }) }}</label>
                    </div>
                    <div class="grid gap-x-4 gap-y-2 sm:grid-cols-2">
                        <div v-for="rule in group.rules" :key="rule.product" class="flex items-center gap-2">
                            <Checkbox v-model="selected" :value="rule.product" :input-id="`${formId}-p-${rule.product}`" :disabled="busy" @update:model-value="emit('clear-error', 'products')" />
                            <label :for="`${formId}-p-${rule.product}`" class="text-sm">{{ t(UtilAdPrice.nameKey(rule.product)) }}</label>
                        </div>
                    </div>
                </div>
                <small :id="`${formId}-products-msg`" class="text-xs" :class="productsError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ productsError || t('ad.priceBulkProductsHelp') }}</small>
            </fieldset>

            <!-- 단가 조정 · 적용 시각 -->
            <div class="grid gap-x-6 gap-y-3 sm:grid-cols-2">
                <div class="flex min-w-0 flex-col gap-1.5">
                    <span :id="`${formId}-adjust`" class="text-sm font-medium">{{ t('ad.priceBulkAdjust') }}</span>
                    <div class="flex flex-wrap items-center gap-2">
                        <SelectButton v-model="kind" :options="kindOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${formId}-adjust`" :disabled="busy" />
                        <InputGroup class="w-40">
                            <InputNumber
                                v-model="value"
                                :min="kind === 'PERCENT' ? UtilAdPrice.BULK.percentMin : undefined"
                                :max="kind === 'PERCENT' ? UtilAdPrice.BULK.percentMax : undefined"
                                :max-fraction-digits="0"
                                :allow-empty="false"
                                :readonly="busy"
                                :invalid="!!valueError"
                                :aria-labelledby="`${formId}-adjust`"
                                :aria-describedby="`${formId}-adjust-msg`"
                                fluid
                                input-class="min-w-0 text-right tabular-nums"
                                data-testid="ad-price-bulk-value"
                                @update:model-value="emit('clear-error', 'adjust.value')"
                            />
                            <InputGroupAddon>{{ kind === 'PERCENT' ? '%' : t('ad.priceWonSuffix') }}</InputGroupAddon>
                        </InputGroup>
                    </div>
                    <small :id="`${formId}-adjust-msg`" class="text-xs" :class="valueError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ valueError || t('ad.priceBulkAdjustHelp') }}</small>
                </div>
                <LabeledField :label="t('ad.priceEffectiveAt')" :help="t('ad.priceEffectiveAtHelp')" :error="effectiveAtError">
                    <template #default="{ id, describedBy, invalid }">
                        <AppDateTimePicker
                            v-model="effectiveAt"
                            :input-id="id"
                            :min="minAt"
                            :minute-step="UtilAdPrice.EFFECTIVE_MINUTE_STEP"
                            :readonly="busy"
                            :invalid="invalid"
                            :described-by="describedBy"
                            :show-button-bar="false"
                            data-testid="ad-price-bulk-effective-at"
                        />
                    </template>
                </LabeledField>
            </div>

            <!-- 결과 미리보기 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${formId}-result`">
                <h3 :id="`${formId}-result`" class="m-0 text-xs font-medium">{{ t('ad.priceBulkResult', { n: resultRows.length }) }}</h3>
                <p v-if="!resultRows.length" class="m-0 text-xs text-muted-color">{{ t('ad.priceBulkResultEmpty') }}</p>
                <table v-else class="w-full border-collapse text-[13px] tabular-nums">
                    <thead>
                        <tr class="text-[0.6875rem] text-muted-color">
                            <th scope="col" class="p-0 pb-1 text-left font-normal">{{ t('ad.priceBulkResultProduct') }}</th>
                            <th scope="col" class="p-0 pb-1 pl-3 text-right font-normal">{{ t('ad.priceDialogNow') }}</th>
                            <th scope="col" class="p-0 pb-1 pl-3 text-right font-normal">{{ t('ad.priceDialogNew') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in resultRows" :key="row.product" class="border-t border-(--line)">
                            <th scope="row" class="p-0 py-1 text-left font-normal">
                                {{ row.name }}
                                <span v-if="row.duplicate" class="block text-xs text-red-600 dark:text-red-400">{{ t('ad.priceEffectiveAtDuplicate') }}</span>
                            </th>
                            <td class="p-0 py-1 pl-3 text-right text-muted-color">{{ won(row.now) }}</td>
                            <td class="p-0 py-1 pl-3 text-right font-semibold" :class="row.next === 0 ? 'text-amber-700 dark:text-amber-400' : ''">{{ won(row.next) }}</td>
                        </tr>
                    </tbody>
                </table>
                <p class="m-0 text-xs text-muted-color">{{ t('ad.priceDialogWhich') }}</p>
            </section>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('ad.priceBulkSubmit', { n: selected.length })" :loading="busy" data-testid="ad-price-bulk-submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdPriceAdjustKind, AdPriceBulkScheduleRequest, AdPriceProduct, AdPriceRule } from '~/composables/ad/api/useAdApi.types';
import UtilAdPrice, { type AdPriceGroup } from '~/utils/util-ad-price';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    visible: boolean;
    /** 요금표 줄(지금 값 · 예약들) */
    rules?: AdPriceRule[];
    busy?: boolean;
    /** 칸에 안 붙는 서버 오류 */
    error?: string;
    /** 서버 칸 오류 — `effectiveAt`·`products`·`adjust.value` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { rules: () => [], busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: AdPriceBulkScheduleRequest]; 'clear-error': [key: string] }>();

const { t, locale } = useI18n();
const formId = useId();

const selected = ref<AdPriceProduct[]>([]);
const kind = ref<AdPriceAdjustKind>('PERCENT');
const value = ref<number | null>(10);
const effectiveAt = ref('');
const submitted = ref(false);
const minAt = ref(new Date().toISOString());

// 열 때마다 처음 값
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        selected.value = [];
        kind.value = 'PERCENT';
        value.value = 10;
        effectiveAt.value = UtilAdPrice.defaultEffectiveAt();
        submitted.value = false;
        minAt.value = new Date().toISOString();
    },
);

watch(effectiveAt, () => emit('clear-error', 'effectiveAt'));

const kindOptions = computed(() => [
    { value: 'PERCENT' as const, label: t('ad.priceBulkKind.PERCENT') },
    { value: 'AMOUNT' as const, label: t('ad.priceBulkKind.AMOUNT') },
]);

/** 묶음 둘 — 서버가 준 상품만 */
const groups = computed(() =>
    UtilAdPrice.GROUPS.map((group) => ({ key: group.key, rules: group.products.flatMap((p) => props.rules.filter((r) => r.product === p)) })).filter((group) => group.rules.length > 0),
);

function allOf(group: AdPriceGroup): boolean {
    const products = groups.value.find((g) => g.key === group)?.rules.map((r) => r.product) ?? [];
    return products.length > 0 && products.every((p) => selected.value.includes(p));
}

function toggleGroup(group: AdPriceGroup, on: boolean): void {
    const products = groups.value.find((g) => g.key === group)?.rules.map((r) => r.product) ?? [];
    selected.value = on ? [...new Set([...selected.value, ...products])] : selected.value.filter((p) => !products.includes(p));
    emit('clear-error', 'products');
}

const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

/** 결과 표 — 화면 차례, 기준 = 지금 단가 */
const resultRows = computed(() =>
    UtilAdPrice.PRODUCTS.filter((p) => selected.value.includes(p)).flatMap((product) => {
        const rule = props.rules.find((r) => r.product === product);
        if (!rule) return [];
        return [{ product, name: t(UtilAdPrice.nameKey(product)), now: rule.unitPrice, next: UtilAdPrice.adjustPrice(rule.unitPrice, kind.value, value.value ?? 0), duplicate: UtilAdPrice.hasScheduleAt(rule, effectiveAt.value) }];
    }),
);

const productsError = computed(() => props.fieldErrors.products ?? (submitted.value && !selected.value.length ? t('ad.priceBulkProductsRequired') : ''));
const valueError = computed(() => props.fieldErrors['adjust.value'] ?? props.fieldErrors['adjust.kind'] ?? (submitted.value && !value.value ? t('ad.priceBulkValueRequired') : ''));
const effectiveAtError = computed(() => {
    if (props.fieldErrors.effectiveAt) return props.fieldErrors.effectiveAt;
    const problem = UtilAdPrice.effectiveAtProblem(effectiveAt.value);
    if (problem === 'past') return t('ad.priceEffectiveAtPast');
    if (problem === 'required') return submitted.value ? t('ad.priceEffectiveAtRequired') : '';
    return resultRows.value.some((row) => row.duplicate) ? t('ad.priceBulkDuplicate') : '';
});

function submit(): void {
    submitted.value = true;
    if (props.busy || productsError.value || valueError.value || effectiveAtError.value || !value.value) return;
    emit('submit', { effectiveAt: effectiveAt.value, products: resultRows.value.map((row) => row.product), adjust: { kind: kind.value, value: Math.round(value.value) } });
}
</script>
