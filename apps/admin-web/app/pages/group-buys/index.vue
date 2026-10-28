<template>
    <!--
        공구 › 공구(`/group-buys`) — 운영자가 파는 실물 공동구매 목록(계약 commerce-groupbuy 「화면 목록 (2b-1)」).
        상태 탭(건수 — 서버 `counts`, 검색어를 적용한 건수) · 검색(제목·상품명·공구 번호) · 표(공구·상태·방식·수량·공구가·기간·주문 수). 줄을 누르면 상세·편집(`/group-buys/{id}`).
        탭은 주소 `?status=` 로 남는다(대시보드 링크). 권한: 화면 `commerce.product.read`, 「새 공구」 `commerce.product.manage`(없으면 단추를 숨긴다 — 서버가 막는다).
        서버 호출은 `useGroupBuyAdminApi`(지금 목업 — `?mock=gb-list-fail`·`gb-list-empty`·`read-forbidden`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuy.listTitle')" :subtitle="t('groupBuy.listSubtitle')">
            <template #actions>
                <Button v-if="canManage" as="router-link" to="/group-buys/new" :label="t('groupBuy.newGroupBuy')" icon="pi pi-plus" size="small" />
            </template>
        </PageTitle>

        <form class="flex flex-wrap items-center gap-2" role="search" :aria-label="t('groupBuy.searchLabel')" @submit.prevent="search">
            <label class="sr-only" :for="qId">{{ t('groupBuy.searchLabel') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('groupBuy.searchPlaceholder')" class="w-full max-w-sm" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <Tabs :value="status" scrollable class="min-w-0" @update:value="(v: string | number) => selectStatus(String(v))">
            <TabList :aria-label="t('groupBuy.statusTabs')">
                <Tab v-for="tab in tabs" :key="tab.value" :value="tab.value">
                    <span class="inline-flex items-center gap-1.5">
                        {{ tab.label }}
                        <span class="text-[11px] font-normal tabular-nums text-muted-color">{{ tab.count }}</span>
                    </span>
                </Tab>
            </TabList>
        </Tabs>

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
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('groupBuy.forbidden') : t('groupBuy.listLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || status" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-shopping-bag" :title="t('groupBuy.listEmpty')" :description="t('groupBuy.listEmptyHint')">
                        <Button v-if="canManage" as="router-link" to="/group-buys/new" :label="t('groupBuy.newGroupBuy')" icon="pi pi-plus" size="small" />
                    </EmptyState>
                </template>

                <Column :header="t('groupBuy.col.groupBuy')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 items-center gap-3">
                            <img v-if="data.thumbnailUrl" :src="data.thumbnailUrl" alt="" class="size-10 shrink-0 rounded-md border border-(--line) object-cover" />
                            <span v-else class="grid size-10 shrink-0 place-items-center rounded-md bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true"><i class="pi pi-image" /></span>
                            <div class="min-w-0">
                                <div class="flex min-w-0 items-center gap-1.5">
                                    <span class="truncate text-[13px] font-medium">{{ data.title }}</span>
                                    <Tag v-if="data.roundNo > 1" :value="t('groupBuy.roundTag', { n: data.roundNo })" severity="secondary" />
                                </div>
                                <div class="truncate text-[11px] text-muted-color">{{ data.productName }}</div>
                            </div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.status')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag :value="t(`groupBuy.status.${data.status}`)" :severity="UtilGroupBuy.statusSeverity(data.status)" />
                            <Tag v-if="data.closeReason" :value="t(`groupBuy.closeReason.${data.closeReason}`)" :severity="UtilGroupBuy.closeReasonSeverity(data.closeReason)" />
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.mode')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="text-[12px]">{{ data.minQty > 0 ? t('groupBuy.modeThreshold', { n: data.minQty }) : t('groupBuy.modeFirstCome') }}</div>
                        <div class="text-[11px] text-muted-color">{{ t(`groupBuy.fulfillment.${data.fulfillment}`) }}</div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.qty')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="text-[13px] tabular-nums">{{ t('groupBuy.qtyLine', { current: data.currentQty, max: data.maxQty }) }}</div>
                        <div class="flex flex-wrap items-center gap-1 text-[11px] text-muted-color">
                            <span v-if="data.reservedQty > 0">{{ t('groupBuy.reservedLine', { n: data.reservedQty }) }}</span>
                            <Tag v-if="data.status === 'RECRUITING' && UtilGroupBuy.lowStock(data.maxQty, data.currentQty, data.reservedQty)" :value="t('groupBuy.lowStock')" severity="warn" />
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.price')" style="min-width: 7rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div class="text-right text-[13px] tabular-nums">{{ t('groupBuy.won', { amount: UtilGroupBuy.won(data.groupPrice, locale) }) }}</div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.period')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <div class="text-[12px] tabular-nums">{{ data.startAt ? time.shortDateTime(data.startAt) : '—' }}</div>
                        <div class="text-[12px] tabular-nums text-muted-color">~ {{ data.endAt ? time.shortDateTime(data.endAt) : '—' }}</div>
                        <div v-if="data.extendedUntil" class="text-[11px] text-orange-600 dark:text-orange-400">{{ t('groupBuy.extendedUntil', { at: time.clock(data.extendedUntil) }) }}</div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.orders')" style="min-width: 5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div class="text-right text-[13px] tabular-nums">{{ data.orderCount }}</div>
                    </template>
                </Column>
            </DataTable>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyRow, GroupBuyStatus } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import { GROUP_BUY_STATUSES } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('groupBuy.listTitle') });

const api = useGroupBuyAdminApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const canManage = computed(() => can('commerce.product.manage'));
const qId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const status = ref<string>(typeof route.query.status === 'string' && (GROUP_BUY_STATUSES as readonly string[]).includes(route.query.status) ? route.query.status : '');
const q = ref('');
const applied = ref<{ q: string }>({ q: '' });
const rows = ref<GroupBuyRow[]>([]);
const counts = ref<Record<GroupBuyStatus, number> | null>(null);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const tabs = computed(() => {
    const c = counts.value;
    const all = c ? GROUP_BUY_STATUSES.reduce((sum, s) => sum + c[s], 0) : null;
    return [{ value: '', label: t('common.all'), count: all ?? '—' }, ...GROUP_BUY_STATUSES.map((s) => ({ value: s, label: t(`groupBuy.status.${s}`), count: c ? c[s] : '—' }))];
});

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ status: (status.value || undefined) as GroupBuyStatus | undefined, q: applied.value.q || undefined, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
        counts.value = found.counts;
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
    applied.value = { q: q.value.trim() };
    first.value = 0;
    void reload();
}

function selectStatus(value: string): void {
    status.value = value;
    first.value = 0;
    void router.replace({ query: { ...route.query, status: value || undefined } });
    void reload();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

function onRowClick(event: DataTableRowClickEvent): void {
    void router.push(`/group-buys/${encodeURIComponent((event.data as GroupBuyRow).id)}`);
}

onMounted(reload);
</script>
