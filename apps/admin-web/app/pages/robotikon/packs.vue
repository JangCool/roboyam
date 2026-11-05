<template>
    <!--
        로보티콘 › 팩 관리(`/robotikon/packs`) — 상점에 나온(또는 심사 중·반려된) 로보티콘 팩 전체를 보고 판매를 멈추거나 다시 판다. 운영 팩(로보얌콘 …)은 여기서 직접 만들고 고친다.
        실제 API(S1 — `useEmoticonAdminApi`) · 가격 바꾸기(S2 — `POST …/packs/{id}/price`, 판매 중 작가 팩만). 오류: 사유 `fields.reason` → 404 → `409 EMOTICON_PACK_NOT_ON_SALE`(토스트 + 다시 읽기) → `fields.price`(대화상자).
        거르기: 출처(전체 · 운영 · 작가) · 상태(전체 · 판매 중 · 판매 중단 · 심사 중 · 반려) — 바꾸면 바로 다시 읽는다. 찾기(팩 이름 · 작가 닉네임 · 태그)는 [검색]을 눌러야 간다.
        `?creator=<회원 번호>`(회원 상세의 「로보티콘 작가」 링크)로 들어오면 그 작가의 팩만 — 위에 지울 수 있는 칩으로 보인다. 연 팩은 `?id=`.
        한 줄: 대표 · 이름(새 판 심사 중이면 딱지) · 출처 · 작가 · 개수 · 받은 수 · 작가 보상 합계(운영 팩은 「보상 없음」) · 상태. 줄을 누르면 서랍(`EmoticonPackDrawer`).
        판매 중단: `EmoticonReasonDialog`(자주 쓰는 사유 + 직접 입력 — 작가에게 보인다) → 토스트 → 서랍 갱신 → 목록 다시 읽기. 다시 판매: 확인창 → 같은 흐름.
        운영 팩: [운영 팩 만들기] → 빈 편집 서랍 · 서랍의 [고치기] → 이름·설명·태그·로보티콘 이름표·차례·빼기 · [로보티콘 올리기](한 장씩 올려 서랍 편집에 붙인다) → 저장.
        권한(후보 — 서버 `Permission` 에 아직 없다): 조회 `emoticon.read`(메뉴) · 판매 중단·다시 판매·운영 팩 만들기·고치기 `emoticon.review`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('emoticon.packsTitle')" :subtitle="t('emoticon.packsSubtitle')">
            <template v-if="canManage" #actions>
                <Button type="button" :label="t('emoticon.official.createButton')" icon="pi pi-plus" size="small" @click="openCreate" />
            </template>
        </PageTitle>

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('emoticon.filter.label')" @submit.prevent="search">
            <FilterRow :label="t('emoticon.filter.source')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="source" :options="sourceOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <FilterRow :label="t('emoticon.filter.status')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="status" :options="statusOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <FilterRow :label="t('emoticon.filter.q')" :label-for="qId">
                <div class="flex w-full flex-wrap items-center gap-2">
                    <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('emoticon.filter.qPlaceholder')" class="w-full max-w-sm" />
                    <Button type="submit" :label="t('common.search')" size="small" :loading="loading" />
                </div>
            </FilterRow>
            <div v-if="creator" class="flex flex-wrap items-center gap-2 border-t border-(--line) pt-3 text-[13px]">
                <span class="text-muted-color">{{ t('emoticon.filter.creator') }}</span>
                <Chip :label="creatorLabel" removable @remove="clearCreator" />
            </div>
        </form>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="rows" data-key="id" :loading="loading" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable row-hover class="min-w-0 cursor-pointer" @row-click="(e: { data: EmoticonPackRow }) => openRow(e.data)">
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('emoticon.forbidden') : t('emoticon.packsLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="loading" @retry="load" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="hasFilter" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Button type="button" :label="t('emoticon.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearAll" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-face-smile" :title="t('emoticon.packsEmpty')" />
                </template>

                <Column :header="t('emoticon.col.cover')" style="width: 4.5rem">
                    <template #body="{ data }">
                        <EmoticonPackCover :src="data.coverUrl" :alt="data.name" />
                    </template>
                </Column>

                <Column :header="t('emoticon.col.name')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-col items-start gap-0.5">
                            <button type="button" class="block max-w-full cursor-pointer truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop="openRow(data)">
                                {{ data.name }}
                            </button>
                            <Tag v-if="data.pendingUpdate" :value="t('emoticon.pendingUpdateTag')" icon="pi pi-hourglass" severity="info" />
                        </div>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.source')" style="min-width: 5.5rem">
                    <template #body="{ data }">
                        <Tag :value="t(`emoticon.source.${data.source}`)" :severity="data.source === 'OFFICIAL' ? 'contrast' : 'secondary'" />
                    </template>
                </Column>

                <Column :header="t('emoticon.col.creator')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <template v-if="data.creator">
                            <span v-if="data.creator.withdrawn" class="text-[13px] text-muted-color">{{ data.creator.nickname }}</span>
                            <NuxtLink v-else :to="UtilEmoticon.memberLink(data.creator.memberNo)" class="text-[13px] text-primary hover:underline" :title="t('emoticon.openMember')" @click.stop>{{ data.creator.nickname }}</NuxtLink>
                        </template>
                        <span v-else class="text-[13px] text-muted-color">{{ t('emoticon.officialCreator') }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.count')" style="min-width: 4.5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums">{{ data.itemCount }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.price')" style="min-width: 6rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span v-if="data.price > 0" class="block text-right text-[13px] tabular-nums">{{ t('emoticon.wonValue', { amount: data.price.toLocaleString(locale) }) }}</span>
                        <span v-else class="block text-right text-[12px] text-muted-color">{{ t('emoticon.free') }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.acquire')" style="min-width: 6rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums">{{ data.acquireCount.toLocaleString(locale) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.reward')" style="min-width: 7rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span v-if="data.rewardTotal === null" class="block text-right text-[12px] text-muted-color">{{ t('emoticon.noReward') }}</span>
                        <span v-else class="block text-right text-[13px] tabular-nums">{{ t('emoticon.pointValue', { amount: data.rewardTotal.toLocaleString(locale) }) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.sales')" style="min-width: 8rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div v-if="data.salesCount !== null && data.salesAmount !== null" class="text-right">
                            <div class="text-[13px] tabular-nums">{{ t('emoticon.wonValue', { amount: data.salesAmount.toLocaleString(locale) }) }}</div>
                            <div class="text-[11px] tabular-nums text-muted-color">{{ t('emoticon.salesCountValue', { count: data.salesCount.toLocaleString(locale) }) }}</div>
                        </div>
                        <span v-else class="block text-right text-[12px] text-muted-color">—</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.status')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <Tag :value="t(`emoticon.packStatus.${data.status}`)" :severity="UtilEmoticon.packStatusSeverity(data.status)" />
                    </template>
                </Column>
            </DataTable>
        </div>

        <EmoticonPackDrawer
            ref="drawerRef"
            v-model:visible="drawerOpen"
            :summary="selected"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :busy="working"
            :can-manage="canManage"
            :creating="creating"
            :saving="saving"
            :uploading="uploading"
            :save-error="saveError"
            :upload-error="uploadError"
            :cover-uploading="coverUploading"
            :cover-error="coverError"
            :item-min="packRules.itemMin"
            :item-max="packRules.itemMax"
            :field-errors="saveFieldErrors"
            @retry="loadDetail"
            @suspend="openSuspend"
            @resume="resume"
            @price="openPrice"
            @upload="upload"
            @upload-cover="uploadCover"
            @save="saveOfficial"
        />

        <EmoticonPriceDialog
            v-if="detail"
            v-model:visible="priceOpen"
            mode="change"
            :title="t('emoticon.priceTitle', { name: detail.name })"
            :current-price="detail.price"
            :tiers="priceTiers"
            :paid-sales-enabled="paidSalesEnabled"
            :paid-blockers="pricePaidBlockers"
            :busy="working"
            :error="priceError"
            :field-errors="priceFieldErrors"
            @submit="submitPrice"
        />

        <EmoticonReasonDialog
            v-model:visible="suspendOpen"
            :title="t('emoticon.suspendTitle', { name: detail?.name ?? '' })"
            :description="t('emoticon.suspendBody')"
            :confirm-label="t('emoticon.actSuspend')"
            :presets="suspendPresets"
            :busy="working"
            :error="suspendError"
            :field-error="suspendFieldError"
            @submit="submitSuspend"
        />
    </div>
</template>

<script setup lang="ts">
import type { EmoticonReasonPreset } from '~/components/emoticon/EmoticonReasonDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonCoverView, EmoticonItemView, EmoticonOfficialPackSaveRequest, EmoticonPackDetail, EmoticonPackRow, EmoticonPackSource, EmoticonPackStatus } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import { EMOTICON_PACK_LIST_STATUSES, EMOTICON_PACK_SOURCES } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('emoticon.packsTitle') });

const api = useEmoticonAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
const route = useRoute();
const router = useRouter();
const qId = useId();

/** 판매 중단·다시 판매·운영 팩 만들기·고치기 권한(후보) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('emoticon.review'));

const sourceOptions = computed(() => [{ value: '', label: t('common.all') }, ...EMOTICON_PACK_SOURCES.map((value) => ({ value, label: t(`emoticon.source.${value}`) }))]);
const statusOptions = computed(() => [{ value: '', label: t('common.all') }, ...EMOTICON_PACK_LIST_STATUSES.map((value) => ({ value, label: t(`emoticon.packStatus.${value}`) }))]);

const source = ref('');
const status = ref('');
const q = ref('');
/** 마지막으로 검색한 찾기 글자 */
const appliedQ = ref('');
const creator = ref<string>(typeof route.query.creator === 'string' ? route.query.creator : '');

const rows = ref<EmoticonPackRow[]>([]);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const hasFilter = computed(() => Boolean(source.value || status.value || appliedQ.value || creator.value));

/** 작가 칩 글자 — 목록에서 그 작가의 닉네임을 찾고, 없으면 회원 번호 */
const creatorLabel = computed(() => {
    const found = rows.value.find((r) => r.creator?.memberNo === creator.value)?.creator;
    return found ? t('emoticon.filter.creatorChip', { name: found.nickname, no: creator.value }) : t('emoticon.filter.creatorChipNo', { no: creator.value });
});

let ticket = 0;

async function load(): Promise<void> {
    const mine = ++ticket;
    loading.value = true;
    loadError.value = null;
    try {
        const found = await api.packs({
            source: (source.value || undefined) as EmoticonPackSource | undefined,
            status: (status.value || undefined) as EmoticonPackStatus | undefined,
            q: appliedQ.value || undefined,
            creator: creator.value || undefined,
        });
        if (mine !== ticket) return;
        rows.value = found.items;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) loading.value = false;
    }
}

function search(): void {
    appliedQ.value = q.value.trim();
    void load();
}

function clearCreator(): void {
    creator.value = '';
    void router.replace({ query: { ...route.query, creator: undefined } });
    void load();
}

function clearAll(): void {
    source.value = '';
    status.value = '';
    q.value = '';
    appliedQ.value = '';
    clearCreator();
}

watch([source, status], () => void load());

// ── 서랍 ─
const drawerRef = ref<{ addItems: (items: EmoticonItemView[]) => void; setCover: (cover: EmoticonCoverView) => void } | null>(null);
const drawerOpen = ref(false);
const creating = ref(false);
const selected = ref<EmoticonPackRow | null>(null);
const selectedId = ref<string | null>(null);
const detail = ref<EmoticonPackDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
let detailTicket = 0;

async function loadDetail(): Promise<void> {
    const id = selectedId.value;
    if (!id) return;
    const mine = ++detailTicket;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const found = await api.pack(id);
        if (mine !== detailTicket) return;
        detail.value = found;
    } catch (e) {
        if (mine !== detailTicket) return;
        detailError.value = toApiError(e);
    } finally {
        if (mine === detailTicket) detailLoading.value = false;
    }
}

function resetEdit(): void {
    saveError.value = '';
    uploadError.value = '';
    saveFieldErrors.value = {};
    coverError.value = '';
}

function openId(id: string, row: EmoticonPackRow | null): void {
    creating.value = false;
    selectedId.value = id;
    selected.value = row;
    detail.value = null;
    resetEdit();
    drawerOpen.value = true;
    if (route.query.id !== id) void router.replace({ query: { ...route.query, id } });
    void loadDetail();
}

function openRow(row: EmoticonPackRow): void {
    openId(row.id, row);
}

function openCreate(): void {
    creating.value = true;
    selectedId.value = null;
    selected.value = null;
    detail.value = null;
    detailError.value = null;
    resetEdit();
    drawerOpen.value = true;
}

watch(drawerOpen, (visible) => {
    if (!visible && route.query.id) void router.replace({ query: { ...route.query, id: undefined } });
});

onMounted(() => {
    void load();
    void loadRules();
    if (typeof route.query.id === 'string' && route.query.id) openId(route.query.id, null);
});

// ── 판매 중단 · 다시 판매 ─
const working = ref(false);
const suspendOpen = ref(false);
const suspendError = ref('');
const suspendFieldError = ref('');
const suspendPresets = computed<EmoticonReasonPreset[]>(() =>
    UtilEmoticon.SUSPEND_PRESETS.map((code) => ({ code, label: t(`emoticon.suspendPreset.${code}`), text: t(`emoticon.suspendPresetText.${code}`) })),
);

function openSuspend(): void {
    if (!detail.value || working.value || !canManage.value) return;
    suspendError.value = '';
    suspendFieldError.value = '';
    suspendOpen.value = true;
}

/** 상태가 이미 바뀐 팩(409)·없는 팩(404) — 토스트 후 다시 읽는다 */
function handleStale(e: ApiError): boolean {
    if (e.status !== 404 && e.status !== 409) return false;
    toast.show(e.message, { life: 6000 });
    void loadDetail();
    void load();
    return true;
}

async function submitSuspend(reason: string): Promise<void> {
    const target = detail.value;
    if (!target || working.value) return;
    working.value = true;
    suspendError.value = '';
    suspendFieldError.value = '';
    try {
        detail.value = await api.suspend(target.id, reason);
        suspendOpen.value = false;
        toast.success(t('emoticon.suspended', { name: target.name }));
        void load();
    } catch (e) {
        const error = toApiError(e);
        if (handleStale(error)) suspendOpen.value = false;
        else if (error.field('reason')) suspendFieldError.value = error.field('reason')!;
        else suspendError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
    } finally {
        working.value = false;
    }
}

// ── 가격 바꾸기(판매 중 유료 작가 팩) — 설정의 가격 단계를 처음 열 때 받는다 ─
const priceOpen = ref(false);
const priceTiers = ref<number[]>([]);
const paidSalesEnabled = ref(true);
const priceError = ref('');
const priceFieldErrors = ref<Record<string, string>>({});
/** 무료 → 유료로 못 가는 까닭(작가의 상품화 계약 동의 · 정산 정보) */
const pricePaidBlockers = computed(() => {
    const ready = detail.value?.paidReady;
    if (!ready) return [];
    const out: string[] = [];
    if (!ready.contractAgreed) out.push(t('emoticon.price.blockContract'));
    if (!ready.payoutRegistered) out.push(t('emoticon.price.blockPayout'));
    return out;
});

async function openPrice(): Promise<void> {
    if (!detail.value || working.value || !canManage.value) return;
    priceError.value = '';
    priceFieldErrors.value = {};
    try {
        const settings = await api.settings();
        priceTiers.value = settings.priceTiers;
        paidSalesEnabled.value = settings.paidSalesEnabled;
    } catch (e) {
        toast.error(`${t('emoticon.price.tiersLoadFailed')} — ${toApiError(e).message}`, 8000);
        return;
    }
    priceOpen.value = true;
}

async function submitPrice(body: { price: number; reason: string }): Promise<void> {
    const target = detail.value;
    if (!target || working.value) return;
    working.value = true;
    priceError.value = '';
    priceFieldErrors.value = {};
    try {
        detail.value = await api.changePrice(target.id, body);
        priceOpen.value = false;
        toast.success(body.price > 0 ? t('emoticon.priceChanged', { name: target.name, price: body.price.toLocaleString(locale.value) }) : t('emoticon.priceChangedFree', { name: target.name }));
        void load();
    } catch (e) {
        const error = toApiError(e);
        if (handleStale(error)) priceOpen.value = false;
        else if (error.code === 'VALIDATION' && error.fields) priceFieldErrors.value = error.fields;
        else priceError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
    } finally {
        working.value = false;
    }
}

async function resume(): Promise<void> {
    const target = detail.value;
    if (!target || working.value || !canManage.value) return;
    const ok = await confirm({ title: t('emoticon.resumeTitle', { name: target.name }), message: t('emoticon.resumeBody'), confirmLabel: t('emoticon.actResume'), cancelLabel: t('common.cancel') });
    if (!ok) return;
    working.value = true;
    try {
        detail.value = await api.resume(target.id);
        toast.success(t('emoticon.resumed', { name: target.name }));
        void load();
    } catch (e) {
        const error = toApiError(e);
        if (!handleStale(error)) toast.error(`${t('emoticon.actionFailed')} — ${error.message}`, 8000);
    } finally {
        working.value = false;
    }
}

// ── 운영 팩 만들기·고치기 ─
const saving = ref(false);
const uploading = ref(false);
const saveError = ref('');
const uploadError = ref('');
const saveFieldErrors = ref<Record<string, string>>({});

/** 팩 규칙(설정의 팩당 개수) — 운영 팩 편집이 쓴다. 못 받으면 서버 기본값(8~40)으로 두고 저장 때 서버가 판정한다 */
const packRules = reactive({ itemMin: 8, itemMax: 40 });
async function loadRules(): Promise<void> {
    try {
        const settings = await api.settings();
        packRules.itemMin = settings.itemMin;
        packRules.itemMax = settings.itemMax;
    } catch {
        // 편집 화면의 안내 값일 뿐 — 판정은 서버(저장 때 `fields.items`)
    }
}

/** 대표 이미지 올리기 — 서버가 파일을 읽어 검사(200×200 PNG · 1MB). 거절 문구는 그대로 */
const coverUploading = ref(false);
const coverError = ref('');
async function uploadCover(file: File): Promise<void> {
    if (coverUploading.value) return;
    coverUploading.value = true;
    coverError.value = '';
    try {
        drawerRef.value?.setCover(await api.uploadCover(file));
    } catch (e) {
        const error = toApiError(e);
        // 서버는 `fields.cover` 에 이유 코드를 준다(문구는 `message`) — 이유 코드로 화면 문구를 고른다
        coverError.value = UtilEmoticonMessages.uploadError(t, error, 'cover');
    } finally {
        coverUploading.value = false;
    }
}

/** 한 장씩 차례로 올린다 — 실패한 것은 이름과 서버 문구를 한 줄로 모으고, 된 것만 서랍 편집에 붙인다 */
async function upload(files: File[]): Promise<void> {
    if (uploading.value) return;
    uploading.value = true;
    uploadError.value = '';
    const done: EmoticonItemView[] = [];
    const failed: string[] = [];
    try {
        for (const file of files) {
            try {
                done.push(await api.upload(file));
            } catch (e) {
                const error = toApiError(e);
                failed.push(`${file.name}: ${UtilEmoticonMessages.uploadError(t, error, 'file')}`);
            }
        }
        if (done.length) drawerRef.value?.addItems(done);
        if (failed.length) uploadError.value = `${t('emoticon.official.uploadFailed', { count: failed.length })} — ${failed.join(' · ')}`;
    } finally {
        uploading.value = false;
    }
}

async function saveOfficial(body: EmoticonOfficialPackSaveRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    saveError.value = '';
    saveFieldErrors.value = {};
    const wasCreating = creating.value;
    try {
        const saved = wasCreating ? await api.createOfficial(body) : await api.saveOfficial(selectedId.value!, body);
        toast.success(wasCreating ? t('emoticon.official.created', { name: saved.name }) : t('emoticon.official.saved', { name: saved.name }));
        creating.value = false;
        selectedId.value = saved.id;
        detail.value = saved;
        if (route.query.id !== saved.id) void router.replace({ query: { ...route.query, id: saved.id } });
        void load();
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'VALIDATION' && error.fields && Object.keys(error.fields).length) {
            saveFieldErrors.value = error.fields;
            saveError.value = t('emoticon.saveFieldsRejected');
        } else {
            saveError.value = `${t('emoticon.saveFailed')} — ${error.message}`;
        }
    } finally {
        saving.value = false;
    }
}

// 다른 화면에서 `?creator=` 로 다시 들어오면(같은 화면 재사용) 거르기를 맞춘다
watch(
    () => route.query.creator,
    (value) => {
        const next = typeof value === 'string' ? value : '';
        if (next === creator.value) return;
        creator.value = next;
        void load();
    },
);
</script>
