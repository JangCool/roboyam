<template>
    <!--
        공구 › 상품(`/group-buy-products`) — 같은 상품을 차수·조건을 바꿔 반복해 파는 상품 목록(결정 89). 상품 = 이름·설명·이미지·카테고리·고시 양식·옵션 축/단품·기본 공급가.
        거르기: 카테고리(위 단을 고르면 아래 단 포함) · 검색(상품 이름). 줄을 누르면 상품 편집(`/group-buy-products/{id}`). 「새 상품」은 `commerce.product.manage`.
        서버 호출은 `useGroupBuyProductApi`(지금 목업 — `?mock=products-fail`·`products-empty`·`read-forbidden`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuy.productListTitle')" :subtitle="t('groupBuy.productListSubtitle')">
            <template #actions>
                <Button v-if="canManage" as="router-link" to="/group-buy-products/new" :label="t('groupBuy.newProduct')" icon="pi pi-plus" size="small" />
            </template>
        </PageTitle>

        <form class="flex flex-wrap items-center gap-2" role="search" :aria-label="t('groupBuy.productSearchLabel')" @submit.prevent="search">
            <label class="sr-only" :for="catId">{{ t('groupBuy.productCategoryFilter') }}</label>
            <Select :input-id="catId" v-model="categoryId" :options="categoryFilter" option-label="label" option-value="value" show-clear :placeholder="t('groupBuy.productCategoryAll')" class="w-56" @update:model-value="search" />
            <label class="sr-only" :for="qId">{{ t('groupBuy.productSearchLabel') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('groupBuy.productSearchPlaceholder')" class="w-full max-w-xs" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="rows"
                data-key="id"
                lazy
                paginator
                :first="first"
                :rows="size"
                :total-records="total"
                :rows-per-page-options="[20, 50, 100]"
                :loading="busy"
                scrollable
                :row-class="() => 'cursor-pointer'"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                @page="onPage"
                @row-click="onRowClick"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('groupBuy.forbidden') : t('groupBuy.productListLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || applied.categoryId" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-tag" :title="t('groupBuy.productListEmpty')" :description="t('groupBuy.productListEmptyHint')" />
                </template>

                <Column :header="t('groupBuy.col.product')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 items-center gap-3">
                            <img v-if="data.thumbUrl" :src="data.thumbUrl" alt="" class="size-10 shrink-0 rounded-md border border-(--line) object-cover" />
                            <span v-else class="grid size-10 shrink-0 place-items-center rounded-md bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-image" /></span>
                            <span class="truncate text-[13px] font-medium">{{ data.name }}</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.category')" style="min-width: 10rem">
                    <template #body="{ data }"><span class="text-[12px]">{{ data.categoryPath }}</span></template>
                </Column>
                <Column :header="t('groupBuy.col.skus')" style="min-width: 5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }"><div class="text-right text-[13px] tabular-nums">{{ data.skuCount }}</div></template>
                </Column>
                <Column :header="t('groupBuy.col.uses')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ t('groupBuy.productUses', { n: data.groupBuyCount }) }}</span>
                        <Tag v-if="data.recruitingCount" :value="t('groupBuy.productRecruiting', { n: data.recruitingCount })" severity="info" class="ml-1" />
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.updatedAt')" style="min-width: 9rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums text-muted-color">{{ time.shortDateTime(data.updatedAt) }}</span></template>
                </Column>
            </DataTable>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyCategory, GroupBuyProductRow } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const { t } = useI18n();
useSeoMeta({ title: () => t('groupBuy.productListTitle') });

const api = useGroupBuyProductApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const router = useRouter();

const canManage = computed(() => can('commerce.product.manage'));
const qId = useId();
const catId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const categories = ref<GroupBuyCategory[]>([]);
/** 거르기 — 위 단(아래 단 포함)과 아래 단 모두 */
const categoryFilter = computed(() => UtilGroupBuy.categoryChoices(categories.value, true, t('groupBuy.categoryHiddenMark')));

const q = ref('');
const categoryId = ref<string | null>(null);
const applied = ref<{ q: string; categoryId: string | null }>({ q: '', categoryId: null });
const rows = ref<GroupBuyProductRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ q: applied.value.q || undefined, categoryId: applied.value.categoryId ?? undefined, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        total.value = 0;
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function search(): void {
    applied.value = { q: q.value.trim(), categoryId: categoryId.value };
    first.value = 0;
    void reload();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

function onRowClick(event: DataTableRowClickEvent): void {
    void router.push(`/group-buy-products/${encodeURIComponent((event.data as GroupBuyProductRow).id)}`);
}

onMounted(async () => {
    void reload();
    try {
        categories.value = await api.categories();
    } catch {
        // 거르기만 빠진다 — 목록은 그대로
    }
});
</script>
