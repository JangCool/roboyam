<template>
    <!--
        공구 › 리뷰(`/group-buy-reviews`) — 계약 「리뷰 규칙」(2b-6, 사용자 확정 2026-10-29).
        · 상태 탭(건수): 공개 · 신고 있음 · 숨김 · 전체(작성자가 지운 것 포함). 별점 하나 · 사진 있음 · 답글 없음 · 찾기(상품·공구 · 닉네임 · 회원·주문 번호). 새 것이 위.
        · 한 줄: 작성 시각 · 상품·공구(차수) · 회원 · 별점 · 리뷰 앞부분(사진 수 · 신고 수) · 상태(답글 여부).
        · 줄을 누르면 상세 서랍(`?review=ID`): 공식 답글(하나, 수정 가능) · 숨기기(사유 코드만 — 낮은 별점은 사유가 아니다) · 숨김 풀기(사유 5~200자).
        권한: 화면 `commerce.order.read` · 답글·숨김 `commerce.order.manage`. 서버 호출은 `useGroupBuyReviewAdminApi`(서버 연결 2026-10-29).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuyReview.title')" :subtitle="t('groupBuyReview.subtitle')" />
        <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuyReview.lowRatingNote') }}</p>

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('groupBuyReview.search')" @submit.prevent="search">
            <label class="sr-only" :for="ratingId">{{ t('groupBuyReview.ratingFilter') }}</label>
            <Select v-model="rating" :input-id="ratingId" :options="ratingOptions" option-label="label" option-value="value" size="small" class="w-32" />
            <div class="flex items-center gap-1.5">
                <Checkbox v-model="photoOnly" binary :input-id="photoId" />
                <label :for="photoId" class="text-[13px]">{{ t('groupBuyReview.photoOnly') }}</label>
            </div>
            <div class="flex items-center gap-1.5">
                <Checkbox v-model="noReplyOnly" binary :input-id="noReplyId" />
                <label :for="noReplyId" class="text-[13px]">{{ t('groupBuyReview.noReplyOnly') }}</label>
            </div>
            <label class="sr-only" :for="qId">{{ t('groupBuyReview.filterQ') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('groupBuyReview.filterQPlaceholder')" class="w-full max-w-xs" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <Tabs :value="tab" scrollable class="min-w-0" @update:value="(v: string | number) => selectTab(String(v) as GroupBuyReviewTab)">
            <TabList :aria-label="t('groupBuyReview.tabsLabel')">
                <Tab v-for="item in tabs" :key="item.value" :value="item.value">
                    <span class="inline-flex items-center gap-1.5">{{ item.label }}<span v-if="item.count !== null" class="text-[11px] font-normal tabular-nums text-muted-color">{{ item.count }}</span></span>
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
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('groupBuyReview.forbidden') : t('groupBuyReview.loadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="filtered" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-star" :title="t('groupBuyReview.empty')" :description="t('groupBuyReview.emptyHint')" />
                </template>

                <Column :header="t('groupBuyReview.col.createdAt')" style="min-width: 8.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.shortDateTime(data.createdAt) }}</span>
                    </template>
                </Column>
                <Column :header="t('groupBuyReview.col.product')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <span class="block max-w-56 truncate text-[13px]">{{ data.product.name }}</span>
                        <span class="block max-w-56 truncate text-[11px] text-muted-color">{{ t('groupBuyReview.roundN', { n: data.groupBuy.roundNo }) }} · {{ data.skuName }}</span>
                    </template>
                </Column>
                <Column :header="t('groupBuyReview.col.member')" style="min-width: 7rem"><template #body="{ data }"><PaymentMemberLabel :member="data.member" /></template></Column>
                <Column :header="t('groupBuyReview.col.rating')" style="min-width: 7rem"><template #body="{ data }"><GroupBuyStarsLabel :value="data.rating" /></template></Column>
                <Column :header="t('groupBuyReview.col.review')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <div class="flex flex-col gap-1">
                            <span class="line-clamp-2 text-[13px] [overflow-wrap:anywhere]">{{ data.preview }}</span>
                            <div v-if="data.photoCount > 0 || data.reportCount > 0 || data.updatedAt" class="flex flex-wrap gap-1">
                                <Tag v-if="data.photoCount > 0" :value="t('groupBuyReview.photoN', { n: data.photoCount })" severity="secondary" icon="pi pi-image" />
                                <Tag v-if="data.reportCount > 0" :value="t('groupBuyReview.reports', { count: data.reportCount })" severity="danger" icon="pi pi-flag" />
                                <Tag v-if="data.updatedAt" :value="t('groupBuyReview.editedMark')" severity="secondary" />
                            </div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuyReview.col.status')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag v-if="data.deleted" :value="t('groupBuyReview.deleted')" severity="secondary" />
                            <Tag v-else-if="data.hidden" :value="t('groupBuyReview.hidden')" severity="secondary" icon="pi pi-eye-slash" />
                            <Tag v-else :value="t('groupBuyReview.visible')" severity="success" />
                            <span class="text-[11px]" :class="data.hasReply ? 'text-muted-color' : 'text-orange-700 dark:text-orange-400'">{{ data.hasReply ? t('groupBuyReview.replied') : t('groupBuyReview.noReply') }}</span>
                        </div>
                    </template>
                </Column>
            </DataTable>
        </div>

        <GroupBuyReviewDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :busy="working"
            :can-manage="can('commerce.order.manage')"
            :reply-error="replyError"
            :reply-field-error="replyFieldError"
            @retry="loadDetail"
            @reply="submitReply"
            @hide="openHide"
            @unhide="openUnhide"
        />

        <GroupBuyReviewHideDialog :key="hideRound" v-model:visible="hideOpen" :busy="working === 'hide'" :error="dialogError" :field-errors="dialogFields" @submit="submitHide" />
        <PaymentReasonDialog
            v-model:visible="unhideOpen"
            :title="t('groupBuyReview.unhideTitle')"
            :description="t('groupBuyReview.unhideBody')"
            :confirm-label="t('groupBuyReview.actUnhide')"
            :busy="working === 'hide'"
            :error="dialogError"
            :field-error="dialogFields.reason ?? ''"
            @submit="submitUnhide"
        />
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyReviewDetail, GroupBuyReviewHideRequest, GroupBuyReviewListResponse, GroupBuyReviewQuery, GroupBuyReviewRow, GroupBuyReviewTab } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';
import { GROUP_BUY_REVIEW_TABS } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';

const { t } = useI18n();
useSeoMeta({ title: () => t('groupBuyReview.title') });

const api = useGroupBuyReviewAdminApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const ratingId = useId();
const photoId = useId();
const noReplyId = useId();
const qId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const queryString = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '');
const tab = ref<GroupBuyReviewTab>((GROUP_BUY_REVIEW_TABS as readonly string[]).includes(queryString('tab')) ? (queryString('tab') as GroupBuyReviewTab) : 'VISIBLE');
const rating = ref<number | null>(null);
const photoOnly = ref(false);
const noReplyOnly = ref(false);
const q = ref('');
const ratingOptions = computed(() => [{ value: null, label: t('groupBuyReview.ratingAll') }, ...[5, 4, 3, 2, 1].map((n) => ({ value: n, label: t('groupBuyReview.ratingN', { n }) }))]);
const applied = ref<Omit<GroupBuyReviewQuery, 'page' | 'size' | 'tab'>>({});
const filtered = computed(() => !!(applied.value.rating || applied.value.photo || applied.value.noReply || applied.value.q) || tab.value !== 'VISIBLE');

const rows = ref<GroupBuyReviewRow[]>([]);
const counts = ref<GroupBuyReviewListResponse['counts'] | null>(null);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const tabs = computed(() => GROUP_BUY_REVIEW_TABS.map((value) => ({ value, label: t(`groupBuyReview.tab.${value}`), count: value === 'ALL' ? null : (counts.value?.[value] ?? '—') })));

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ ...applied.value, tab: tab.value, page: Math.floor(first.value / size.value), size: size.value });
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
    applied.value = { rating: rating.value ?? undefined, photo: photoOnly.value || undefined, noReply: noReplyOnly.value || undefined, q: q.value.trim() || undefined };
    first.value = 0;
    void reload();
}

function selectTab(value: GroupBuyReviewTab): void {
    tab.value = value;
    first.value = 0;
    void router.replace({ query: { ...route.query, tab: value } });
    void reload();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

// ── 상세 서랍 ──
const drawerOpen = ref(false);
const drawerId = ref<string | null>(null);
const drawerSummary = ref<GroupBuyReviewRow | null>(null);
const drawerDetail = ref<GroupBuyReviewDetail | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);

let dseq = 0;
async function loadDetail(): Promise<void> {
    const id = drawerId.value;
    if (!id) return;
    const mine = ++dseq;
    drawerLoading.value = true;
    drawerError.value = null;
    try {
        const found = await api.detail(id);
        if (mine === dseq) drawerDetail.value = found;
    } catch (e) {
        if (mine !== dseq) return;
        drawerDetail.value = null;
        drawerError.value = toApiError(e);
    } finally {
        if (mine === dseq) drawerLoading.value = false;
    }
}

function show(id: string, row: GroupBuyReviewRow | null): void {
    if (drawerId.value !== id) drawerDetail.value = null;
    drawerId.value = id;
    drawerSummary.value = row;
    replyError.value = '';
    replyFieldError.value = '';
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as GroupBuyReviewRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, review: row.id } });
}

// 주소의 `?review=` 과 서랍을 맞춘다(바로가기·뒤로 가기)
watch(
    () => route.query.review,
    (id) => {
        if (typeof id === 'string' && id) {
            if (id !== drawerId.value || !drawerOpen.value) show(id, rows.value.find((r) => r.id === id) ?? null);
        } else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (open) => {
    if (open || !route.query.review) return;
    const { review: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

// ── 동작 ──
const working = ref<'reply' | 'hide' | null>(null);
const replyError = ref('');
const replyFieldError = ref('');

async function submitReply(body: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    const isFirst = !d.answer;
    working.value = 'reply';
    replyError.value = '';
    replyFieldError.value = '';
    try {
        drawerDetail.value = await api.reply(d.id, body);
        toast.success(isFirst ? t('groupBuyReview.repliedDone') : t('groupBuyReview.replyUpdated'));
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields?.body) replyFieldError.value = failure.fields.body;
        else replyError.value = failure.message;
        if (failure.code === 'GROUP_BUY_REVIEW_DELETED') void loadDetail();
    } finally {
        working.value = null;
    }
}

const hideOpen = ref(false);
const hideRound = ref(0);
const unhideOpen = ref(false);
const dialogError = ref('');
const dialogFields = ref<Record<string, string>>({});

function resetDialog(): void {
    dialogError.value = '';
    dialogFields.value = {};
}

function openHide(): void {
    resetDialog();
    hideRound.value++;
    hideOpen.value = true;
}

function openUnhide(): void {
    resetDialog();
    unhideOpen.value = true;
}

async function runHide(call: () => Promise<GroupBuyReviewDetail>, done: string, close: () => void): Promise<void> {
    working.value = 'hide';
    resetDialog();
    try {
        drawerDetail.value = await call();
        toast.success(done);
        close();
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) dialogFields.value = failure.fields;
        else dialogError.value = failure.message;
    } finally {
        working.value = null;
    }
}

async function submitHide(body: GroupBuyReviewHideRequest): Promise<void> {
    const d = drawerDetail.value;
    if (d) await runHide(() => api.hide(d.id, body), t('groupBuyReview.hiddenDone'), () => (hideOpen.value = false));
}

async function submitUnhide(reason: string): Promise<void> {
    const d = drawerDetail.value;
    if (d) await runHide(() => api.unhide(d.id, reason), t('groupBuyReview.unhiddenDone'), () => (unhideOpen.value = false));
}

onMounted(() => void reload());
</script>
