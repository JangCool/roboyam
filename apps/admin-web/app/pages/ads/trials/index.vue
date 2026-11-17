<template>
    <!--
        광고 › 체험단(`/ads/trials?status=&q=`, trial-group-contract 「관리자 화면」·「구현 결정」 10). 위에 두 탭(모집 · 확인할 일 — `TrialAdminTabs`).
        거르기: 상태(전체 + 6 — 서버 `status`) · 찾기(제목·광고주 — 받은 목록 안에서). 한 줄(서버 `TrialRow`): 상태 · 제목(→ 모집 상세) · 상품·사용기 게시판 · 광고주 · 형태 · 보내는 쪽 · 신청 / 인원(뽑은 인원) · 신청 기간 · 금액(선결제 = 저장 때 · 후불 = 뽑기 뒤 확정) · 광고 결제 상태(`paymentStatus` — V242) + 결제 방식(`paymentTiming` — V244).
        [모집 만들기] → 서랍(`TrialEditDrawer`). 저장하면 상세로 간다. 권한: 화면·만들기 `trial.manage`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <TrialAdminTabs active="list">
            <template #actions>
                <Button v-if="canManage" type="button" :label="t('trial.create')" icon="pi pi-plus" size="small" @click="openCreate" />
            </template>
        </TrialAdminTabs>

        <form class="flex flex-wrap items-center gap-2" role="search" :aria-label="t('trial.searchLabel')" @submit.prevent="search">
            <Select :model-value="status" :options="statusOptions" option-label="label" option-value="value" size="small" class="w-40" :aria-label="t('trial.statusLabel')" @update:model-value="onStatus" />
            <InputText v-model="q" type="search" size="small" maxlength="100" :placeholder="t('trial.searchPlaceholder')" class="w-full max-w-xs" :aria-label="t('trial.searchLabel')" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="loading" />
        </form>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="shownRows" data-key="id" :loading="loading" scrollable row-hover :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('trial.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-box" :title="t('trial.empty')">
                        <Button v-if="canManage" type="button" :label="t('trial.create')" icon="pi pi-plus" size="small" @click="openCreate" />
                    </EmptyState>
                </template>
                <Column :header="t('trial.col.status')" style="min-width: 6rem">
                    <template #body="{ data }"><Tag :value="t(`trial.status.${data.status}`)" :severity="statusSeverity(data.status)" /></template>
                </Column>
                <Column :header="t('trial.col.title')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <NuxtLink :to="UtilTrial.detailPath(data.id)" class="line-clamp-1 text-[13px] font-medium text-(--p-primary-color) hover:underline" @click.stop>{{ data.title }}</NuxtLink>
                        <span class="text-[11px] text-muted-color">{{ data.productName }} · {{ data.reviewBoard.name }}</span>
                    </template>
                </Column>
                <Column :header="t('trial.col.advertiser')" style="min-width: 8rem">
                    <template #body="{ data }"><span class="text-[13px]">{{ data.advertiser.name }}</span></template>
                </Column>
                <Column :header="t('trial.col.form')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <span class="flex flex-col text-[12px]">
                            <span>{{ t(`trial.form.${data.form}`) }}</span>
                            <span class="text-[11px] text-muted-color">{{ t(`trial.shipper.${data.shipper}`) }}</span>
                        </span>
                    </template>
                </Column>
                <Column :header="t('trial.col.people')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ t('trial.peopleLine', { applied: data.applicantCount, total: data.recruitCount }) }}</span>
                        <span v-if="data.selectedCount !== null" class="block text-[11px] text-muted-color">{{ t('trial.selectedLine', { n: data.selectedCount }) }}</span>
                    </template>
                </Column>
                <Column :header="t('trial.col.period')" style="min-width: 10rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ time.date(data.applyStartAt) }} ~ {{ time.date(data.applyEndAt) }}</span></template>
                </Column>
                <Column :header="t('trial.col.amount')" style="min-width: 6rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ data.amount === null ? '—' : data.amount === 0 ? t('trial.free') : won(data.amount) }}</span></template>
                </Column>
                <Column :header="t('trial.col.payment')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <Tag v-if="data.paymentStatus" :value="t(`trial.paymentStatus.${data.paymentStatus}`)" :severity="UtilTrial.paymentSeverity(data.paymentStatus)" class="text-[11px]!" />
                        <span v-if="data.paymentTiming" class="block text-[11px] text-muted-color">{{ t(`trial.paymentTiming.${data.paymentTiming}`) }}</span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <TrialEditDrawer v-model:open="drawerOpen" :options="formOptions.options.value" :can-manage="canManage" :saving="saving" :error="saveError" :field-errors="saveFieldErrors" :upload-cover="uploadCover" @save="save" />
    </div>
</template>

<script setup lang="ts">
import type { AdminTrialRequest, AdminTrialRow, TrialStatus } from '~/composables/trial/api/useTrialApi.types';
import UtilTrial from '~/utils/util-trial';
import UtilTrialMessages from '~/utils/util-trial-messages';

const ALL = '__all__';

const { t, locale } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useTrialApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();

/** 만들기 — 서버 `POST /api/admin/trials` 와 같게 `trial.manage` 또는 `ad.manage` */
const canManage = computed(() => can('trial.manage') || can('ad.manage'));
const status = computed(() => (typeof route.query.status === 'string' && (UtilTrial.STATUSES as readonly string[]).includes(route.query.status) ? route.query.status : ALL));
const q = ref(typeof route.query.q === 'string' ? route.query.q : '');
const statusOptions = computed(() => [{ value: ALL, label: t('trial.allStatus') }, ...UtilTrial.STATUSES.map((s) => ({ value: s as string, label: t(`trial.status.${s}`) }))]);

const rows = ref<AdminTrialRow[]>([]);
const loading = ref(true);
const loadError = ref('');
const won = (n: number) => UtilTrialMessages.won(t, n, locale.value);
/** 찾기(제목·광고주) — 서버는 상태로만 거르므로 받은 목록 안에서 */
const shownRows = computed(() => {
    const text = (typeof route.query.q === 'string' ? route.query.q : '').trim();
    return text ? rows.value.filter((r) => r.title.includes(text) || r.advertiser.name.includes(text) || r.productName.includes(text)) : rows.value;
});

let seq = 0;
async function load(): Promise<void> {
    const mine = ++seq;
    loading.value = true;
    loadError.value = '';
    try {
        const res = await api.list(status.value === ALL ? '' : (status.value as TrialStatus));
        if (mine !== seq) return;
        rows.value = res;
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e).message;
        rows.value = [];
    } finally {
        if (mine === seq) loading.value = false;
    }
}
watch(status, () => void load(), { immediate: true });

// 알림 링크(`/ads/trials?id=` — 서버 `TRIAL_STOCK_KEEP_DUE`)는 그 모집 상세로
if (typeof route.query.id === 'string' && route.query.id) void router.replace(UtilTrial.detailPath(route.query.id));

function query(change: { status?: string; q?: string }) {
    const s = change.status ?? status.value;
    const text = (change.q ?? q.value).trim();
    return { path: UtilTrial.PATHS.list, query: { ...(s !== ALL ? { status: s } : {}), ...(text ? { q: text } : {}) } };
}
function onStatus(v: string | null): void {
    void router.replace(query({ status: v ?? ALL }));
}
function search(): void {
    void router.replace(query({}));
}

/** 줄을 누르면 모집 상세 */
function onRowClick(e: { data: AdminTrialRow }): void {
    void router.push(UtilTrial.detailPath(e.data.id));
}

function statusSeverity(s: TrialStatus): 'success' | 'info' | 'warn' | 'secondary' | 'danger' {
    if (s === 'RECRUITING') return 'success';
    if (s === 'SELECTING') return 'warn';
    if (s === 'RUNNING') return 'info';
    if (s === 'CANCELLED') return 'danger';
    return 'secondary';
}

/* ── 만들기 ── */
const drawerOpen = ref(false);
const formOptions = useTrialFormOptions();
const saving = ref(false);
const saveError = ref('');
const saveFieldErrors = ref<Record<string, string>>({});

async function openCreate(): Promise<void> {
    saveError.value = '';
    saveFieldErrors.value = {};
    drawerOpen.value = true;
    await formOptions.ensure();
}

/** 대표 그림 올리기 — 서랍이 키·주소를 받는다 */
async function uploadCover(file: File): Promise<{ key: string; url: string }> {
    const uploaded = await api.uploadCover(file);
    return { key: uploaded.key ?? '', url: uploaded.url };
}

async function save(body: AdminTrialRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    saveError.value = '';
    saveFieldErrors.value = {};
    try {
        const created = await api.create(body);
        drawerOpen.value = false;
        toast.success(t('trial.created'));
        await router.push(UtilTrial.detailPath(created.id));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) saveFieldErrors.value = { ...error.fields };
        else saveError.value = error.message;
    } finally {
        saving.value = false;
    }
}

useSeoMeta({ title: () => t('trial.title') });
</script>
