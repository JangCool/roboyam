<template>
    <!--
        품목 관리의 「아바타」 탭(결정 14, 계약 「1-2 화면 목업」 아바타 탭) — 유료 306종을 카테고리별로 보고 묶어서 바꾼다. community `ShopAvatarPanel` 을 로보얌 규약으로 옮겼다(가격 범위는 1~100,000).
        위에서 아래로:
          ① 카테고리 요약 표(전체 · 판매 중 · 중단 · 가격 범위 — 무료 두 카테고리는 「무료」, 고르기 체크 없음). 이름을 누르면 아래 격자를 그 카테고리로 거른다.
          ② 묶음 변경(`pointshop.manage` 일 때만): 범위(유료 전체 / 고른 카테고리 / 격자에서 고른 아바타) · 새 가격 + [가격 바꾸기] · [판매 켜기] [판매 끄기]. 범위에 드는 수를 미리 보인다.
             카테고리를 체크하면 범위가 「고른 카테고리」로, 격자에서 고르면 「고른 아바타」로 저절로 옮긴다. 확인창·요청·토스트는 부모 몫이다(`price`·`sale` 이벤트).
          ③ 격자: 썸네일 · 이름 · 가격 · 판매 여부 · 판 수. 거르기(카테고리 · 판매 상태), 고르기 체크(보이는 것 모두 고르기 · 지우기 — 거르기를 바꿔도 고른 것은 남는다). 칸을 누르면 한 줄 편집 서랍(`edit`).
        권한이 없으면 ② 와 체크가 없고 안내 한 줄(읽기 전용 — 편집 서랍도 읽기 전용). 서버를 부르지 않는다(`components.md`).
        서버가 거절한 묶음 칸(`400 fields.price`)은 가격 칸 아래, 나머지(`scope|categories|itemIds|active`)는 묶음 구획 아래 한 줄에 서버 문구 그대로.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <div v-if="loading && !summary" class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="status" :aria-label="t('common.loading')">
            <Skeleton width="12rem" height="1rem" />
            <Skeleton v-for="n in 5" :key="n" height="2rem" />
        </div>

        <div v-else-if="error && !summary" class="rounded-xl border border-(--line) bg-(--p-content-background)">
            <ErrorState :code="error.status === 403 ? '403' : '!'" :title="error.status === 403 ? t('pointShop.forbidden') : t('pointShop.avatar.loadFailed')" :description="error.message" :retry="error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        </div>

        <template v-else-if="summary">
            <!-- ① 카테고리 요약 -->
            <AppGadget :title="t('pointShop.avatar.summaryTitle')">
                <div class="flex flex-col gap-3" :class="loading ? 'opacity-70' : ''" :aria-busy="loading">
                    <p class="m-0 text-sm font-medium tabular-nums">{{ t('pointShop.avatar.totals', totalsArgs) }}</p>
                    <div class="overflow-x-auto rounded-lg border border-(--line)">
                        <table class="w-full min-w-[34rem] border-collapse text-[13px]">
                            <thead>
                                <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                                    <th v-if="canManage" scope="col" class="w-10 px-3 py-2 font-medium"><span class="sr-only">{{ t('pointShop.avatar.col.pick') }}</span></th>
                                    <th scope="col" class="px-3 py-2 font-medium">{{ t('pointShop.avatar.col.category') }}</th>
                                    <th scope="col" class="px-3 py-2 text-right font-medium">{{ t('pointShop.avatar.col.total') }}</th>
                                    <th scope="col" class="px-3 py-2 text-right font-medium">{{ t('pointShop.avatar.col.forSale') }}</th>
                                    <th scope="col" class="px-3 py-2 text-right font-medium">{{ t('pointShop.avatar.col.stopped') }}</th>
                                    <th scope="col" class="px-3 py-2 text-right font-medium">{{ t('pointShop.avatar.col.price') }}</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="row in summary.categories" :key="row.category" class="border-b border-(--line) last:border-b-0" :class="row.free ? 'text-muted-color' : ''">
                                    <td v-if="canManage" class="px-3 py-1.5">
                                        <Checkbox v-if="!row.free" :model-value="pickedCategories.includes(row.category)" binary :input-id="`${uid}-cat-${row.category}`" :disabled="busy" :aria-label="t('pointShop.avatar.pickCategory', { name: categoryName(row.category) })" @update:model-value="(v: boolean) => toggleCategory(row.category, v)" />
                                    </td>
                                    <td class="px-3 py-1.5">
                                        <button v-if="!row.free" type="button" class="cursor-pointer text-left hover:underline focus-visible:outline-2 focus-visible:outline-primary" :class="gridCategory === row.category ? 'font-semibold text-primary' : ''" @click="gridCategory = row.category">
                                            {{ categoryName(row.category) }}
                                        </button>
                                        <span v-else>{{ categoryName(row.category) }}</span>
                                    </td>
                                    <td class="px-3 py-1.5 text-right tabular-nums">{{ row.total }}</td>
                                    <td class="px-3 py-1.5 text-right tabular-nums">{{ row.free ? '—' : row.forSale }}</td>
                                    <td class="px-3 py-1.5 text-right tabular-nums" :class="!row.free && row.stopped > 0 ? 'text-amber-700 dark:text-amber-400' : ''">{{ row.free ? '—' : row.stopped }}</td>
                                    <td class="px-3 py-1.5 text-right tabular-nums">{{ row.free ? t('pointShop.avatar.free') : priceRange(row.minPrice, row.maxPrice) }}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.avatar.noteFree') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.avatar.noteOwned') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.avatar.noteFixed') }}</span></li>
                    </ul>
                </div>
            </AppGadget>

            <!-- ② 묶음 변경 -->
            <AppGadget v-if="canManage" :title="t('pointShop.avatar.bulkTitle')">
                <form class="flex flex-col gap-4" novalidate @submit.prevent="applyPrice">
                    <fieldset class="m-0 min-w-0 border-0 p-0">
                        <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('pointShop.avatar.scopeLabel') }}</legend>
                        <div class="flex flex-wrap gap-2">
                            <label
                                v-for="option in scopeOptions"
                                :key="option.value"
                                class="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm"
                                :class="[scope === option.value ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', option.disabled || busy ? 'cursor-not-allowed opacity-60' : 'cursor-pointer']"
                            >
                                <RadioButton v-model="scope" :value="option.value" :name="`${uid}-scope`" :disabled="option.disabled || busy" />
                                {{ option.label }}
                            </label>
                        </div>
                    </fieldset>

                    <div class="flex flex-wrap items-end gap-x-4 gap-y-3">
                        <LabeledField :label="t('pointShop.avatar.newPrice')" :hint="t('pointShop.range', { min: UtilPointShop.PRICE.min.toLocaleString(locale), max: UtilPointShop.PRICE.max.toLocaleString(locale) })" :error="priceError">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex items-center gap-2">
                                    <InputText :id="id" v-model="priceText" name="price" inputmode="numeric" autocomplete="off" placeholder="1000" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                                    <span class="text-sm text-muted-color">P</span>
                                    <Button type="submit" :label="t('pointShop.avatar.applyPrice')" icon="pi pi-tag" size="small" :loading="busy && pending === 'price'" :disabled="busy || matched === 0" />
                                </div>
                            </template>
                        </LabeledField>
                        <div class="flex flex-wrap items-center gap-2 pb-6">
                            <Button type="button" :label="t('pointShop.avatar.saleOn')" icon="pi pi-play" size="small" severity="secondary" variant="outlined" :loading="busy && pending === 'on'" :disabled="busy || matched === 0" @click="applySale(true)" />
                            <Button type="button" :label="t('pointShop.avatar.saleOff')" icon="pi pi-pause" size="small" severity="secondary" variant="outlined" :loading="busy && pending === 'off'" :disabled="busy || matched === 0" @click="applySale(false)" />
                        </div>
                    </div>

                    <p class="m-0 text-xs text-muted-color" role="status">{{ t('pointShop.avatar.matchedNote', { count: matched }) }}</p>
                    <Message v-if="otherBulkError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ otherBulkError }}</Message>
                </form>
            </AppGadget>
            <p v-else class="m-0 text-xs text-muted-color">{{ t('pointShop.avatar.readOnly') }}</p>

            <!-- ③ 격자 -->
            <AppGadget :title="t('pointShop.avatar.gridTitle', { count: visible.length })">
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-end gap-3">
                        <div class="flex flex-col gap-1.5">
                            <label :for="`${uid}-grid-cat`" class="text-xs font-medium text-muted-color">{{ t('pointShop.avatar.filterCategory') }}</label>
                            <Select v-model="gridCategory" :input-id="`${uid}-grid-cat`" :options="categoryOptions" option-label="label" option-value="value" class="w-48" />
                        </div>
                        <div class="flex flex-col gap-1.5">
                            <span :id="`${uid}-grid-sale`" class="text-xs font-medium text-muted-color">{{ t('pointShop.avatar.filterSale') }}</span>
                            <FilterOptionGroup v-model="gridSale" :options="saleOptions" :labelledby="`${uid}-grid-sale`" />
                        </div>
                    </div>

                    <div v-if="canManage" class="flex flex-wrap items-center gap-2 text-xs">
                        <span class="font-medium tabular-nums">{{ t('pointShop.avatar.selected', { count: picked.size }) }}</span>
                        <Button type="button" :label="t('pointShop.avatar.pickVisible')" size="small" severity="secondary" variant="text" :disabled="busy || visible.length === 0" @click="pickVisible" />
                        <Button type="button" :label="t('pointShop.avatar.clearPicked')" size="small" severity="secondary" variant="text" :disabled="busy || picked.size === 0" @click="clearPicked" />
                    </div>

                    <EmptyState v-if="visible.length === 0" icon="pi pi-filter-slash" :title="t('pointShop.avatar.gridEmpty')" />
                    <ul v-else class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(9.5rem,1fr))] gap-2 p-0" :aria-label="t('pointShop.avatar.gridTitle', { count: visible.length })">
                        <li v-for="avatar in visible" :key="avatar.id" class="relative">
                            <div class="flex h-full flex-col items-center gap-1.5 rounded-lg border px-2 pb-2 pt-3 text-center" :class="picked.has(avatar.id) ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)'">
                                <Checkbox v-if="canManage" :model-value="picked.has(avatar.id)" binary class="absolute left-2 top-2" :disabled="busy" :aria-label="t('pointShop.avatar.pickOne', { name: avatar.name })" @update:model-value="(v: boolean) => togglePicked(avatar.id, v)" />
                                <button type="button" class="flex w-full cursor-pointer flex-col items-center gap-1.5 rounded focus-visible:outline-2 focus-visible:outline-primary" :aria-label="t('pointShop.avatar.editOne', { name: avatar.name })" @click="emit('edit', avatar)">
                                    <PointShopAvatarThumb v-if="avatar.avatarId" :avatar-id="avatar.avatarId" size="md" :class="avatar.active ? '' : 'opacity-50'" />
                                    <span class="w-full truncate text-[13px] font-medium" :title="avatar.name">{{ avatar.name }}</span>
                                </button>
                                <span class="text-[12px] tabular-nums">{{ t('pointShop.pricePoint', { price: avatar.pricePoint.toLocaleString(locale) }) }}</span>
                                <div class="flex flex-wrap items-center justify-center gap-1">
                                    <Tag :value="avatar.active ? t('pointShop.avatar.onSale') : t('pointShop.avatar.stopped')" :severity="avatar.active ? 'success' : 'secondary'" />
                                    <span class="text-[11px] tabular-nums text-muted-color">{{ t('pointShop.sold', { count: avatar.soldCount.toLocaleString(locale) }) }}</span>
                                </div>
                            </div>
                        </li>
                    </ul>
                </div>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PointShopAdminItem, PointShopAvatarScope, PointShopAvatarSummary, PointShopAvatarTarget } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

interface Props {
    /** 카테고리 요약 — 못 받았으면 null */
    summary?: PointShopAvatarSummary | null;
    /** 아바타 품목(유료 306) */
    items?: PointShopAdminItem[];
    loading?: boolean;
    /** 요약·목록을 못 읽었다 */
    error?: ApiError | null;
    /** 묶음 변경 권한(`pointshop.manage`) */
    canManage?: boolean;
    /** 묶음 변경 요청 중 */
    busy?: boolean;
    /** 칸에 붙지 않는 묶음 변경 오류(서버 문구 그대로) */
    bulkError?: string;
    /** 서버가 묶음 칸마다 준 오류(`fields.scope|categories|itemIds|price|active`) */
    bulkFieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { summary: null, items: () => [], loading: false, error: null, canManage: false, busy: false, bulkError: '', bulkFieldErrors: () => ({}) });
const emit = defineEmits<{
    retry: [];
    edit: [item: PointShopAdminItem];
    price: [target: PointShopAvatarTarget, price: number, matched: number];
    sale: [target: PointShopAvatarTarget, active: boolean, matched: number];
}>();

const { t, locale } = useI18n();
const uid = useId();

function categoryName(id: string): string {
    return UtilPointShopMessages.avatarCategory(t, id);
}
function priceRange(min: number | null, max: number | null): string {
    return UtilPointShopMessages.priceRange(t, min, max, locale.value);
}

const totalsArgs = computed(() => {
    const totals = props.summary?.totals;
    return { total: totals?.total ?? 0, free: totals?.freeCount ?? 0, paid: totals?.paidCount ?? 0, forSale: totals?.forSale ?? 0, stopped: totals?.stopped ?? 0 };
});

// ── 고르기 ─
const pickedCategories = ref<string[]>([]);
const picked = ref<Set<string>>(new Set());
const scope = ref<PointShopAvatarScope>('ALL');
/** 지금 누른 묶음 단추 — 그 단추에만 도는 표시 */
const pending = ref<'price' | 'on' | 'off' | null>(null);
watch(
    () => props.busy,
    (busy) => {
        if (!busy) pending.value = null;
    },
);

function toggleCategory(id: string, on: boolean): void {
    const next = new Set(pickedCategories.value);
    if (on) next.add(id);
    else next.delete(id);
    // 요약 표 차례를 지킨다
    pickedCategories.value = (props.summary?.categories ?? []).map((c) => c.category).filter((c) => next.has(c));
    if (on) scope.value = 'CATEGORIES';
    else if (!pickedCategories.value.length && scope.value === 'CATEGORIES') scope.value = 'ALL';
}

function togglePicked(id: string, on: boolean): void {
    const next = new Set(picked.value);
    if (on) next.add(id);
    else next.delete(id);
    picked.value = next;
    if (on) scope.value = 'ITEMS';
    else if (!next.size && scope.value === 'ITEMS') scope.value = 'ALL';
}

function pickVisible(): void {
    const next = new Set(picked.value);
    for (const avatar of visible.value) next.add(avatar.id);
    picked.value = next;
    if (next.size) scope.value = 'ITEMS';
}

function clearPicked(): void {
    picked.value = new Set();
    if (scope.value === 'ITEMS') scope.value = 'ALL';
}

/** 목록이 다시 오면 없어진 품목은 고른 것에서 뺀다 */
watch(
    () => props.items,
    (list) => {
        const ids = new Set(list.map((it) => it.id));
        const next = new Set([...picked.value].filter((id) => ids.has(id)));
        if (next.size !== picked.value.size) picked.value = next;
    },
);

const inCategories = computed(() => props.items.filter((it) => it.category !== null && pickedCategories.value.includes(it.category)).length);
const matched = computed(() => {
    if (scope.value === 'CATEGORIES') return inCategories.value;
    if (scope.value === 'ITEMS') return picked.value.size;
    return props.items.length;
});
const scopeOptions = computed(() => [
    { value: 'ALL' as const, label: t('pointShop.avatar.scope.ALL', { count: props.items.length }), disabled: props.items.length === 0 },
    { value: 'CATEGORIES' as const, label: t('pointShop.avatar.scope.CATEGORIES', { count: pickedCategories.value.length, items: inCategories.value }), disabled: pickedCategories.value.length === 0 },
    { value: 'ITEMS' as const, label: t('pointShop.avatar.scope.ITEMS', { count: picked.value.size }), disabled: picked.value.size === 0 },
]);

/** 범위 칸 — 그 범위의 칸만 싣는다(다른 범위의 칸을 같이 보내면 서버가 거절한다) */
const target = computed<PointShopAvatarTarget>(() => {
    if (scope.value === 'CATEGORIES') return { scope: 'CATEGORIES', categories: [...pickedCategories.value] };
    if (scope.value === 'ITEMS') return { scope: 'ITEMS', itemIds: [...picked.value] };
    return { scope: 'ALL' };
});

// ── 묶음 가격·판매 ─
const priceText = ref('');
const priceTried = ref(false);
/** 서버 가격 문구를 숨겼다 — 칸을 고쳤다 */
const priceDismissed = ref(false);
watch(priceText, () => (priceDismissed.value = true));
watch(
    () => props.bulkFieldErrors,
    () => (priceDismissed.value = false),
);
const priceProblem = computed(() => UtilPointShop.rangeProblem(priceText.value, UtilPointShop.PRICE.min, UtilPointShop.PRICE.max));
const priceError = computed(() => {
    const problem = priceProblem.value;
    if (problem && (priceTried.value || problem !== 'REQUIRED')) return UtilPointShopMessages.itemProblem(t, 'pricePoint', problem, locale.value);
    return priceDismissed.value ? '' : (props.bulkFieldErrors.price ?? '');
});
const otherBulkError = computed(() =>
    [
        props.bulkError,
        ...Object.entries(props.bulkFieldErrors)
            .filter(([key]) => key !== 'price')
            .map(([, message]) => message),
    ]
        .filter(Boolean)
        .join(' '),
);

function applyPrice(): void {
    priceTried.value = true;
    const price = UtilPointShop.parseInt(priceText.value);
    if (props.busy || priceProblem.value || price === null || matched.value === 0) return;
    pending.value = 'price';
    emit('price', target.value, price, matched.value);
}

function applySale(active: boolean): void {
    if (props.busy || matched.value === 0) return;
    pending.value = active ? 'on' : 'off';
    emit('sale', target.value, active, matched.value);
}

// ── 격자 거르기 ─
const gridCategory = ref('');
const gridSale = ref<string>('');
const categoryOptions = computed(() => [
    { value: '', label: t('common.all') },
    ...(props.summary?.categories ?? []).filter((c) => !c.free).map((c) => ({ value: c.category, label: categoryName(c.category) })),
]);
const saleOptions = computed(() => [
    { value: '', label: t('common.all') },
    { value: 'ON', label: t('pointShop.avatar.onSale') },
    { value: 'OFF', label: t('pointShop.avatar.stopped') },
]);
const visible = computed(() =>
    props.items.filter((it) => (!gridCategory.value || it.category === gridCategory.value) && (!gridSale.value || (gridSale.value === 'ON' ? it.active : !it.active))),
);
</script>
