<template>
    <!--
        포인트 상점 › 품목 관리(`/point-shop/items`) — 포인트로 사는 디지털 아이템(닉네임 변경권 · 닉네임 색 · 프로필 테두리 · 프로필 아바타)을 만들고 고친다(commerce.md 7절, 계약 commerce-stage1 B절 · 「1-2 화면 목업」).
        실제 서버(`usePointShopAdminApi` → `server/admin` `pointshop.controller`)를 부른다.
        권한: 조회 `pointshop.read`(메뉴), 만들기·고치기 `pointshop.manage`(없으면 [품목 추가]가 없고 서랍이 읽기 전용). 권한 코드는 0-5 의 `shop.*` 를 1-2 에서 바꾼 이름이다(`util-permission-catalog.ts`).
        위에서 아래로: 종류 거르기(바꾸면 바로 다시 읽는다 — 품목 수가 적어 쪽 나누기 없음) · 안내 두 줄(산 때 값 · 장식은 지금 값) · 표(순서 · 미리보기 조각 · 이름/코드 · 종류 · 사용 방식 · 가격 · 기간 · 재고(남은/판 수) · 판매 상태 · 사용 여부).
        줄을 누르면(또는 이름 단추) 편집 서랍(`PointShopItemDrawer`, 오른쪽 — 왼쪽 입력·오른쪽 미리보기). 판매 상태는 그릴 때 계산한다(`UtilPointShop.saleState` — 예정 · 끝 · 품절 · 판매 중).
        저장: 서랍이 올린 본문 → 만들기(POST)·고치기(PUT) → 토스트 → 서랍 닫기 → 목록 다시 읽기. 서버가 거절한 칸(400 `fields.<칸>`)은 서랍의 그 칸 아래, 나머지는 서랍 아래 한 줄.
        탭 둘(결정 14): 「품목」(위의 표 — `excludeKind=PROFILE_AVATAR`) · 「아바타」(`PointShopAvatarPanel` — 카테고리 요약 · 묶음 가격·판매 · 격자, 처음 열 때 `avatars/summary` + `kind=PROFILE_AVATAR` 를 받는다). 고른 탭은 `?tab=avatars` 로 남는다.
        아바타 묶음 변경: 패널이 범위·값을 올리면 확인창(「N개를 바꿔요 · 이미 산 사람의 가격은 그대로예요」) → `avatarPrice`·`avatarSale` → 결과 토스트(matched·changed) → 아바타 탭 다시 읽기. 거절 칸은 패널에 서버 문구 그대로.
        아바타 칸을 누르면 같은 편집 서랍이 아바타 모드(이름·영문명·가격·순서·사용 여부만)로 열린다. [품목 추가]는 「품목」 탭에만 있고 종류에 아바타가 없다.
        목록을 못 읽으면 표 안 `ErrorState`(403 은 「권한 없음」이고 다시 시도가 없다). 변경권 미리보기의 환불 기한은 설정(`settings`)에서 읽고, 못 읽으면 기한 없이 말한다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('pointShop.itemsTitle')" :subtitle="t('pointShop.itemsSubtitle')">
            <template v-if="canManage && tab === 'items'" #actions>
                <Button type="button" :label="t('pointShop.item.add')" icon="pi pi-plus" size="small" @click="openCreate" />
            </template>
        </PageTitle>

        <!-- 탭 — 「품목」(아바타 제외) · 「아바타」(유료 306, 묶음 변경). 고른 탭은 주소(`?tab=avatars`)에 남는다 -->
        <Tabs :value="tab" class="min-w-0" @update:value="(v: string | number) => selectTab(String(v))">
            <TabList :aria-label="t('pointShop.tab.label')">
                <Tab value="items">{{ t('pointShop.tab.items') }}</Tab>
                <Tab value="avatars">{{ t('pointShop.tab.avatars') }}</Tab>
            </TabList>
        </Tabs>

        <PointShopAvatarPanel
            v-if="tab === 'avatars'"
            :summary="avatarSummary"
            :items="avatarItems"
            :loading="avatarLoading"
            :error="avatarError"
            :can-manage="canManage"
            :busy="bulkBusy"
            :bulk-error="bulkError"
            :bulk-field-errors="bulkFieldErrors"
            @retry="loadAvatars"
            @edit="openEdit"
            @price="onAvatarPrice"
            @sale="onAvatarSale"
        />

        <template v-else>
        <div class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
            <FilterRow :label="t('pointShop.filter.kind')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="kindFilter" :options="kindOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <ul class="m-0 flex list-none flex-col gap-1.5 border-t border-(--line) p-0 pt-3 text-xs leading-relaxed text-muted-color">
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.itemsNoteSnapshot') }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointShop.itemsNoteDecoration') }}</span></li>
            </ul>
        </div>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="items" data-key="id" :loading="loading" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable row-hover class="min-w-0 cursor-pointer" @row-click="(e: { data: PointShopAdminItem }) => openEdit(e.data)">
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('pointShop.forbidden') : t('pointShop.itemsLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="loading" @retry="load" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="kindFilter" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Button type="button" :label="t('pointShop.clearFilter')" size="small" severity="secondary" variant="outlined" @click="kindFilter = ''" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-shopping-bag" :title="t('pointShop.itemsEmpty')" :description="canManage ? t('pointShop.itemsEmptyHint') : ''" />
                </template>

                <Column field="sortOrder" :header="t('pointShop.col.sortOrder')" style="width: 4.5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums text-muted-color">{{ data.sortOrder }}</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.preview')" style="width: 6rem">
                    <template #body="{ data }">
                        <span v-if="data.kind === 'NICKNAME_COLOR'" class="text-[13px] font-semibold" :style="{ color: UtilColor.cssPair(data.color) ?? undefined }">{{ sampleNickname }}</span>
                        <PointShopBorderPreview v-else-if="data.kind === 'PROFILE_BORDER'" :border-style="data.borderStyle" :initial="sampleInitial" size="sm" :label="t(`pointShop.border.${data.borderStyle}`)" />
                        <i v-else class="pi pi-ticket text-base text-muted-color" :aria-label="t(`pointShop.kind.${data.kind}`)" />
                    </template>
                </Column>

                <Column :header="t('pointShop.col.name')" style="min-width: 14rem">
                    <template #body="{ data }">
                        <div class="min-w-0">
                            <button type="button" class="block max-w-full cursor-pointer truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop="openEdit(data)">
                                {{ data.name }}
                            </button>
                            <code class="block truncate text-[11px] text-muted-color">{{ data.code }}</code>
                        </div>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.kind')" style="min-width: 7.5rem">
                    <template #body="{ data }">
                        <span class="text-[13px]">{{ t(`pointShop.kind.${data.kind}`) }}</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.usage')" style="min-width: 5.5rem">
                    <template #body="{ data }">
                        <span class="text-[13px]">{{ t(`pointShop.usage.${data.usageType}`) }}</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.price')" style="min-width: 6rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums">{{ t('pointShop.pricePoint', { price: data.pricePoint.toLocaleString(locale) }) }}</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.duration')" style="min-width: 4.5rem">
                    <template #body="{ data }">
                        <span v-if="data.durationDays !== null" class="text-[13px] tabular-nums">{{ t('pointShop.days', { days: data.durationDays }) }}</span>
                        <span v-else class="text-[13px] text-muted-color">—</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.stock')" style="min-width: 7.5rem">
                    <template #body="{ data }">
                        <span class="text-[13px] tabular-nums">
                            <template v-if="data.stock === null">{{ t('pointShop.unlimited') }}</template>
                            <template v-else>{{ t('pointShop.stockLeft', { count: data.stock.toLocaleString(locale) }) }}</template>
                            <span class="text-muted-color"> / {{ t('pointShop.sold', { count: data.soldCount.toLocaleString(locale) }) }}</span>
                        </span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.sale')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-col items-start gap-0.5">
                            <Tag :value="t(`pointShop.saleState.${UtilPointShop.saleState(data, now)}`)" :severity="UtilPointShop.saleSeverity(UtilPointShop.saleState(data, now))" />
                            <span v-if="data.saleStartAt || data.saleEndAt" class="text-[11px] tabular-nums text-muted-color">{{ periodText(data) }}</span>
                        </div>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.active')" style="min-width: 5.5rem">
                    <template #body="{ data }">
                        <Tag :value="data.active ? t('pointShop.active') : t('pointShop.inactive')" :severity="data.active ? 'success' : 'secondary'" />
                    </template>
                </Column>
            </DataTable>
        </div>
        </template>

        <PointShopItemDrawer v-model:visible="drawerOpen" :item="editing" :can-manage="canManage" :saving="saving" :error="saveError" :field-errors="saveFieldErrors" :refund-days="refundDays" @save="onSave" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    PointShopAdminItem,
    PointShopAvatarBulkResult,
    PointShopAvatarSummary,
    PointShopAvatarTarget,
    PointShopItemKind,
    PointShopItemSaveRequest,
} from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilColor from '~/utils/util-color';
import UtilPointShop from '~/utils/util-point-shop';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('pointShop.itemsTitle') });

const api = usePointShopAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
// ⚠ 통째로 받아 템플릿에서 `prefs.density` 로 읽는다(조밀 모드)
const { prefs } = useLayoutPrefs();

/** 만들기·고치기 권한(`util-permission-catalog.ts`) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('pointshop.manage'));

const kindOptions = computed(() => [{ value: '', label: t('common.all') }, ...UtilPointShop.CREATABLE_KINDS.map((kind) => ({ value: kind, label: t(`pointShop.kind.${kind}`) }))]);
const sampleNickname = computed(() => t('pointShop.preview.sampleNickname'));
const sampleInitial = computed(() => Array.from(sampleNickname.value)[0] ?? '');

const kindFilter = ref<string>('');
const items = ref<PointShopAdminItem[]>([]);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
/** 판매 상태를 계산하는 기준 시각 — 다시 읽을 때 새로 잡는다 */
const now = ref(Date.now());
/** 셀프 환불 기한(변경권 미리보기) — 못 읽었으면 null */
const refundDays = ref<number | null>(null);

/** 늦게 도착한 앞 요청의 응답은 버린다(번호표) */
let ticket = 0;

async function load(): Promise<void> {
    const mine = ++ticket;
    loading.value = true;
    loadError.value = null;
    try {
        // 아바타 306줄은 「아바타」 탭이 따로 받는다
        const found = await api.items({ kind: (kindFilter.value || undefined) as PointShopItemKind | undefined, excludeKind: 'PROFILE_AVATAR' });
        if (mine !== ticket) return;
        items.value = found.items;
        now.value = Date.now();
    } catch (e) {
        if (mine !== ticket) return;
        items.value = [];
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) loading.value = false;
    }
}

async function loadRefundDays(): Promise<void> {
    try {
        refundDays.value = (await api.settings()).refundDays;
    } catch {
        // 미리보기 설명 한 줄에만 쓴다 — 못 읽으면 기한 없이 말한다(화면 오류로 올리지 않는다)
        refundDays.value = null;
    }
}

watch(kindFilter, () => void load());
onMounted(() => {
    void load();
    void loadRefundDays();
});

// ── 탭 ─
const route = useRoute();
const router = useRouter();
type ItemsTab = 'items' | 'avatars';
const tab = ref<ItemsTab>(route.query.tab === 'avatars' ? 'avatars' : 'items');

function selectTab(value: string): void {
    const next: ItemsTab = value === 'avatars' ? 'avatars' : 'items';
    if (next === tab.value) return;
    tab.value = next;
    void router.replace({ query: { ...route.query, tab: next === 'avatars' ? 'avatars' : undefined } });
}

// ── 아바타 탭 — 처음 열 때 요약·목록을 받는다 ─
const avatarSummary = ref<PointShopAvatarSummary | null>(null);
const avatarItems = ref<PointShopAdminItem[]>([]);
const avatarLoading = ref(false);
const avatarError = ref<ApiError | null>(null);
let avatarTicket = 0;

async function loadAvatars(): Promise<void> {
    const mine = ++avatarTicket;
    avatarLoading.value = true;
    avatarError.value = null;
    try {
        const [summary, list] = await Promise.all([api.avatarSummary(), api.items({ kind: 'PROFILE_AVATAR' })]);
        if (mine !== avatarTicket) return;
        avatarSummary.value = summary;
        avatarItems.value = list.items;
    } catch (e) {
        if (mine !== avatarTicket) return;
        avatarError.value = toApiError(e);
        // 다시 읽기에 실패하면 받아 둔 값은 그대로 두고 토스트로만 알린다(첫 읽기면 패널이 오류 상태를 그린다)
        if (avatarSummary.value) toast.error(`${t('pointShop.avatar.loadFailed')} — ${avatarError.value.message}`, 8000);
    } finally {
        if (mine === avatarTicket) avatarLoading.value = false;
    }
}

watch(
    tab,
    (value) => {
        if (value === 'avatars' && !avatarSummary.value && !avatarLoading.value) void loadAvatars();
    },
    { immediate: true },
);

// ── 아바타 묶음 변경 — 확인창 → 요청 → 결과 토스트(matched·changed) → 다시 읽기 ─
const bulkBusy = ref(false);
const bulkError = ref('');
const bulkFieldErrors = ref<Record<string, string>>({});

async function runBulk(confirmTitle: string, matched: number, call: () => Promise<PointShopAvatarBulkResult>): Promise<void> {
    if (bulkBusy.value) return;
    const ok = await confirm({ title: confirmTitle, message: t('pointShop.avatar.confirmMessage', { count: matched.toLocaleString(locale.value) }), confirmLabel: t('pointShop.avatar.confirmApply'), cancelLabel: t('common.cancel') });
    if (!ok) return;
    bulkBusy.value = true;
    bulkError.value = '';
    bulkFieldErrors.value = {};
    try {
        const res = await call();
        toast.success(res.changed > 0 ? t('pointShop.avatar.done', { matched: res.matched.toLocaleString(locale.value), changed: res.changed.toLocaleString(locale.value) }) : t('pointShop.avatar.doneNone', { matched: res.matched.toLocaleString(locale.value) }));
        void loadAvatars();
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'VALIDATION' && error.fields && Object.keys(error.fields).length) bulkFieldErrors.value = error.fields;
        else bulkError.value = `${t('pointShop.avatar.failed')} — ${error.message}`;
    } finally {
        bulkBusy.value = false;
    }
}

function onAvatarPrice(target: PointShopAvatarTarget, price: number, matched: number): void {
    void runBulk(t('pointShop.avatar.confirmPriceTitle', { price: price.toLocaleString(locale.value) }), matched, () => api.avatarPrice({ ...target, price }));
}

function onAvatarSale(target: PointShopAvatarTarget, active: boolean, matched: number): void {
    void runBulk(active ? t('pointShop.avatar.confirmSaleOnTitle') : t('pointShop.avatar.confirmSaleOffTitle'), matched, () => api.avatarSale({ ...target, active }));
}

/** 판매 기간 한 줄 — 시작 ~ 끝(한쪽만 있으면 그쪽만) */
function periodText(item: PointShopAdminItem): string {
    const from = item.saleStartAt ? time.value.shortDateTime(item.saleStartAt) : '';
    const to = item.saleEndAt ? time.value.shortDateTime(item.saleEndAt) : '';
    return `${from} ~ ${to}`.trim();
}

// ── 편집 서랍 ─
const drawerOpen = ref(false);
const editing = ref<PointShopAdminItem | null>(null);
const saving = ref(false);
const saveError = ref('');
const saveFieldErrors = ref<Record<string, string>>({});

function openCreate(): void {
    editing.value = null;
    saveError.value = '';
    saveFieldErrors.value = {};
    drawerOpen.value = true;
}

function openEdit(item: PointShopAdminItem): void {
    editing.value = item;
    saveError.value = '';
    saveFieldErrors.value = {};
    drawerOpen.value = true;
}

async function onSave(body: PointShopItemSaveRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    saveError.value = '';
    saveFieldErrors.value = {};
    const creating = editing.value === null;
    try {
        const saved = creating ? await api.createItem(body) : await api.updateItem(editing.value!.id, body);
        toast.success(creating ? t('pointShop.item.created', { name: saved.name }) : t('pointShop.item.saved', { name: saved.name }));
        drawerOpen.value = false;
        // 아바타 한 줄을 고쳤으면 아바타 탭(요약·격자)을, 아니면 품목 표를 다시 읽는다
        if (saved.kind === 'PROFILE_AVATAR') void loadAvatars();
        else void load();
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'VALIDATION' && error.fields && Object.keys(error.fields).length) {
            saveFieldErrors.value = error.fields;
            saveError.value = t('pointShop.saveFieldsRejected');
        } else {
            saveError.value = `${t('pointShop.saveFailed')} — ${error.message}`;
        }
    } finally {
        saving.value = false;
    }
}
</script>
