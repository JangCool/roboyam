<template>
    <!--
        사업자 › 사업자 심사(`/businesses`) — 01-business.md 7-1 A·B, 00-overview.md ★-4 B1~B3·H2.
        · 상태 탭(심사 중·승인·반려·정지·탈퇴·전체, 건수) · 서비스(핫딜/광고) · 찾기(사업자명·번호·신청자). 심사 중은 오래된 것이 위.
        · 한 줄: 사업자명 · 가린 등록번호 · 신원 상태 · 서비스 · 신청자 · 신청일 · 국세청 조회 결과. 평문 칸은 목록에 없다.
        · 줄을 누르면 상세 서랍(`?id=`): 승인(계속사업자만 — 조회 불가는 사유) · 반려(코드+글) · 정지(영향 건수와 처리 선택) · 평문 보기(사유 → 10분) · 등록증 열람.
        권한: 화면 `business.read` · 처리 `business.manage` · 평문 `business.reveal`(SENSITIVE, 서버 `Permission` 에 추가 예정).
        서버 호출은 `useBusinessApi`(지금 목업 — `?mock=biz-list-fail`·`biz-empty`·`nts-down`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('business.title')" :subtitle="t('business.subtitle')" />

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('business.search')" @submit.prevent="search">
            <div class="flex items-center gap-2">
                <span :id="serviceLabelId" class="text-[13px] text-muted-color">{{ t('business.serviceLabel') }}</span>
                <FilterOptionGroup v-model="service" :options="serviceOptions" :labelledby="serviceLabelId" />
            </div>
            <label class="sr-only" :for="qId">{{ t('business.search') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('business.searchPlaceholder')" class="w-full max-w-xs" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <Tabs :value="status" scrollable class="min-w-0" @update:value="(v: string | number) => selectStatus(String(v))">
            <TabList :aria-label="t('business.statusTabs')">
                <Tab v-for="tab in tabs" :key="tab.value" :value="tab.value">
                    <span class="inline-flex items-center gap-1.5">{{ tab.label }}<Badge v-if="tab.value === 'PENDING' && Number(tab.count) > 0" :value="tab.count" severity="info" size="small" /><span v-else class="text-[11px] font-normal tabular-nums text-muted-color">{{ tab.count }}</span></span>
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
                    <ErrorState v-if="loadError" :title="t('business.loadFailed')" :description="loadError.message" :retry="true" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || applied.service" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-check-circle" :title="t(`business.empty.${status}`)" />
                </template>

                <Column :header="t('business.col.name')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="block truncate text-[13px] font-medium">{{ data.name }}</span>
                        <code class="text-[11px] text-muted-color">{{ data.maskedRegNo }}</code>
                    </template>
                </Column>
                <Column :header="t('business.col.status')" style="min-width: 6rem">
                    <template #body="{ data }"><Tag :value="t(`business.status.${data.status}`)" :severity="UtilBusiness.statusSeverity(data.status)" /></template>
                </Column>
                <Column :header="t('business.col.services')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex flex-wrap gap-1">
                            <Tag v-for="s in (data as BusinessRow).services.filter((x) => x.status !== 'OFF')" :key="s.service" :value="`${t(`business.serviceShort.${s.service}`)} · ${t(`business.serviceStatus.${s.status}`)}`" :severity="UtilBusiness.serviceSeverity(s.status)" />
                            <span v-if="!(data as BusinessRow).services.some((x) => x.status !== 'OFF')" class="text-[12px] text-muted-color">—</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('business.col.applicant')" style="min-width: 7rem">
                    <template #body="{ data }"><span class="text-[13px]">{{ data.applicant.nickname }}</span></template>
                </Column>
                <Column :header="t('business.col.appliedAt')" style="min-width: 8rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ time.shortDateTime(data.appliedAt) }}</span></template>
                </Column>
                <Column :header="t('business.col.nts')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <Tag :value="t(`business.nts.${data.nts.status}`)" :severity="UtilBusiness.ntsSeverity(data.nts.status)" :icon="UtilBusiness.ntsIcon(data.nts.status)" />
                    </template>
                </Column>
            </DataTable>
        </div>

        <BusinessDetailDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :busy="working !== null"
            :can-manage="can('business.manage')"
            :can-reveal="can('business.reveal')"
            :reveal="revealed"
            @retry="loadDetail"
            @approve="approve"
            @reject="openDialog('reject')"
            @suspend="openDialog('suspend')"
            @recheck="recheck"
            @reveal="openDialog('reveal')"
            @hide="revealed = null"
            @open-cert="openCert"
        />

        <BizRejectDialog
            v-model:visible="dialogs.reject"
            :title="t('business.rejectTitle')"
            :description="t('business.rejectBody')"
            :confirm-label="t('business.actReject')"
            :codes="rejectCodes"
            :busy="working === 'reject'"
            :error="dialogError"
            :field-errors="dialogFields"
            @submit="submitReject"
        />
        <BusinessSuspendDialog v-if="drawerDetail" v-model:visible="dialogs.suspend" :name="drawerDetail.name" :impact="drawerDetail.impact" :busy="working === 'suspend'" :error="dialogError" @submit="submitSuspend" />
        <BizReasonDialog
            v-model:visible="dialogs.reveal"
            :title="t('business.revealTitle')"
            :description="t('business.revealBody')"
            :confirm-label="t('business.reveal')"
            :busy="working === 'reveal'"
            :error="dialogError"
            @submit="submitReveal"
        />
        <BizReasonDialog
            v-model:visible="dialogs.approveOverride"
            :title="t('business.approveOverrideTitle')"
            :description="t('business.approveOverrideBody')"
            :confirm-label="t('business.actApprove')"
            :busy="working === 'approve'"
            :error="dialogError"
            :field-error="dialogFields.ntsOverrideReason ?? ''"
            @submit="(reason: string) => submitApprove(reason)"
        />

        <Dialog v-model:visible="certOpen" modal :draggable="false" :header="t('business.certTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
            <div class="flex flex-col items-center gap-2">
                <img v-if="certUrl" :src="certUrl" :alt="t('business.certTitle')" class="max-h-[70vh] w-auto rounded border border-(--line)" />
                <p class="m-0 text-xs text-muted-color">{{ t('business.certViewLog') }}</p>
            </div>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    BusinessDetail,
    BusinessQuery,
    BusinessRejectCode,
    BusinessReveal,
    BusinessRow,
    BusinessService,
    BusinessStatus,
    BusinessSuspendRequest,
} from '~/composables/business/api/useBusinessApi.types';
import { BUSINESS_REJECT_CODES, BUSINESS_SERVICES, BUSINESS_STATUSES } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

type Work = 'approve' | 'reject' | 'suspend' | 'reveal' | 'recheck' | 'cert';

const { t } = useI18n();
useSeoMeta({ title: () => t('business.title') });

const api = useBusinessApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const route = useRoute();
const router = useRouter();

const serviceLabelId = useId();
const qId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const queryString = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '');
const status = ref<string>((BUSINESS_STATUSES as readonly string[]).includes(queryString('status')) || queryString('status') === 'ALL' ? queryString('status') : 'PENDING');
const service = ref<string>('');
const q = ref('');
const serviceOptions = computed(() => [{ value: '', label: t('common.all') }, ...BUSINESS_SERVICES.map((s) => ({ value: s, label: t(`business.serviceShort.${s}`) }))]);
const rejectCodes = computed(() => BUSINESS_REJECT_CODES.map((c) => ({ value: c, label: t(`business.rejectCode.${c}`) })));
const applied = ref<{ service?: BusinessService; q?: string }>({});

const rows = ref<BusinessRow[]>([]);
const counts = ref<Record<BusinessStatus, number> | null>(null);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const tabs = computed(() => {
    const c = counts.value;
    const all = c ? BUSINESS_STATUSES.reduce((sum, s) => sum + c[s], 0) : '—';
    return [...BUSINESS_STATUSES.map((s) => ({ value: s as string, label: t(`business.status.${s}`), count: c ? c[s] : '—' })), { value: 'ALL', label: t('common.all'), count: all }];
});

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const query: BusinessQuery = { ...applied.value, status: status.value === 'ALL' ? undefined : (status.value as BusinessStatus), page: Math.floor(first.value / size.value), size: size.value };
        const found = await api.list(query);
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
    applied.value = { service: (service.value || undefined) as BusinessService | undefined, q: q.value.trim() || undefined };
    first.value = 0;
    void reload();
}

function selectStatus(value: string): void {
    status.value = value;
    first.value = 0;
    void router.replace({ query: { ...route.query, status: value } });
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
const drawerSummary = ref<BusinessRow | null>(null);
const drawerDetail = ref<BusinessDetail | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);
/** 지금 보이는 평문 — 서랍 대상이 바뀌거나 닫히면 지운다 */
const revealed = ref<BusinessReveal | null>(null);

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

function show(id: string, row: BusinessRow | null): void {
    if (drawerId.value !== id) {
        drawerDetail.value = null;
        revealed.value = null;
    }
    drawerId.value = id;
    drawerSummary.value = row;
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as BusinessRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, id: row.id } });
}

watch(
    () => route.query.id,
    (id) => {
        if (typeof id === 'string' && id) {
            if (id !== drawerId.value || !drawerOpen.value) show(id, rows.value.find((r) => r.id === id) ?? null);
        } else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (open) => {
    if (open) return;
    revealed.value = null;
    if (!route.query.id) return;
    const { id: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

// ── 동작 ──
const working = ref<Work | null>(null);
const dialogError = ref('');
const dialogFields = ref<Record<string, string>>({});
const dialogs = reactive({ reject: false, suspend: false, reveal: false, approveOverride: false });

function openDialog(name: keyof typeof dialogs): void {
    dialogError.value = '';
    dialogFields.value = {};
    dialogs[name] = true;
}

/** 동작 하나 — 성공하면 서랍·목록을 새로. 409 는 「이미 바뀌었어요」 + 다시 읽기 */
async function run(work: Work, call: () => Promise<BusinessDetail>, done: string, dialog?: keyof typeof dialogs): Promise<void> {
    working.value = work;
    dialogError.value = '';
    dialogFields.value = {};
    try {
        drawerDetail.value = await call();
        toast.success(done);
        if (dialog) dialogs[dialog] = false;
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.status === 409) void loadDetail();
        if (!dialog) {
            toast.error(failure.message, 8000);
            return;
        }
        if (failure.code === 'VALIDATION' && failure.fields) dialogFields.value = failure.fields;
        else dialogError.value = failure.message;
    } finally {
        working.value = null;
    }
}

async function approve(): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    const rule = UtilBusiness.approvability(d.nts.status);
    if (rule === 'LOCKED') return;
    if (rule === 'NEEDS_REASON') {
        openDialog('approveOverride');
        return;
    }
    const services = d.services.filter((s) => s.status === 'PENDING').map((s) => t(`business.service.${s.service}`));
    const ok = await confirm({ title: t('business.approveTitle', { name: d.name }), message: services.length ? t('business.approveBody', { services: services.join(', ') }) : t('business.approveBodyNoService'), confirmLabel: t('business.actApprove') });
    if (ok) await submitApprove();
}

async function submitApprove(reason?: string): Promise<void> {
    const d = drawerDetail.value;
    if (d) await run('approve', () => api.approve(d.id, reason ? { ntsOverrideReason: reason } : {}), t('business.approved'), reason ? 'approveOverride' : undefined);
}

async function submitReject(body: { code: BusinessRejectCode; text: string }): Promise<void> {
    const d = drawerDetail.value;
    if (d) await run('reject', () => api.reject(d.id, body), t('business.rejected'), 'reject');
}

async function submitSuspend(body: BusinessSuspendRequest): Promise<void> {
    const d = drawerDetail.value;
    if (d) await run('suspend', () => api.suspend(d.id, body), t('business.suspended'), 'suspend');
}

async function recheck(): Promise<void> {
    const d = drawerDetail.value;
    if (d) await run('recheck', () => api.recheckNts(d.id), t('business.ntsRechecked'));
}

async function submitReveal(reason: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    working.value = 'reveal';
    dialogError.value = '';
    try {
        revealed.value = await api.reveal(d.id, reason);
        dialogs.reveal = false;
        void loadDetail();
    } catch (e) {
        dialogError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

const certOpen = ref(false);
const certUrl = ref('');
async function openCert(): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    working.value = 'cert';
    try {
        certUrl.value = (await api.certificate(d.id)).url;
        certOpen.value = true;
        void loadDetail();
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        working.value = null;
    }
}

onMounted(() => void reload());
</script>
