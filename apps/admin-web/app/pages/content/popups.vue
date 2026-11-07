<template>
    <!--
        팝업 관리 — 공개 사이트에 들어오면 화면 가운데 뜨는 이벤트·프로모션 창(레이어 팝업). 계약 `docs/features/site-popup-contract.md`(화면 확정 2026-11-07).
          메뉴 줄 아래 한 줄 공지인 「공지 띠」(설정 > 일반)와 다른 기능이다.
        목록: 순서(↑↓ 한 칸씩 — 전체 ID 를 새 차례로 보낸다, 공개 사이트에서 넘기는 차례) · 썸네일(이미지 / 텍스트 첫 줄) · 상태 딱지(SHOWING·SCHEDULED·EXPIRED·OFF) · 종류 딱지 · 이름
          · 보일 곳 · 기기 · 닫기 줄 선택지 · 노출 기간 · 바꾼 사람 · 노출 켜기/끄기 · [미리보기] · [고치기] · [지우기](확인).
        [미리보기](제목 옆): 지금 노출 중인 팝업을 공개 사이트처럼 넘겨 본다(PC·모바일 — 그 기기에 뜨는 것만, 보일 곳은 「홈 화면」 기준). 줄의 [미리보기]는 그 팝업 한 장.
        만들기·고치기는 대화상자(`SitePopupFormDialog` — 입력하는 대로 미리보기).
        권한(공지 띠와 같다): 보기 `site.read`(메뉴), 바꾸기 `site.manage` — 없으면 처리 단추가 없고 안내 한 줄.
        사라진 팝업(`404 SITE_POPUP_NOT_FOUND`)·순서 집합 불일치(`fields.ids`)는 서버 문구를 토스트로 보이고 목록을 다시 받는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('popup.title')" :subtitle="t('popup.subtitle')">
            <template #actions>
                <Button v-if="canManage" type="button" :label="t('popup.add')" icon="pi pi-plus" size="small" @click="openCreate" />
                <Button type="button" :label="t('popup.previewShowing')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" :disabled="!list" @click="openPreview(null)" />
                <Button type="button" icon="pi pi-refresh" :label="t('popup.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            </template>
        </PageTitle>

        <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i class="pi pi-lock" aria-hidden="true" />{{ t('popup.readOnlyHint') }}
        </p>

        <AppGadget :title="t('popup.listTitle')">
            <template #title-extra>
                <span v-if="list" class="text-xs tabular-nums text-muted-color">{{ t('popup.countLine', { showing: list.showingCount, total: list.total }) }}</span>
            </template>

            <div v-if="busy && !list" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="5.5rem" />
            </div>
            <ErrorState v-else-if="loadError && !list" :title="t('popup.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
            <template v-else-if="list">
                <EmptyState v-if="list.total === 0" icon="pi pi-window-maximize" :title="t('popup.empty')" :description="t('popup.emptyHint')">
                    <Button v-if="canManage" type="button" :label="t('popup.add')" icon="pi pi-plus" size="small" @click="openCreate" />
                </EmptyState>
                <template v-else>
                    <Message v-if="list.showingCount === 0" severity="warn" size="small" :closable="false" class="mb-3">{{ t('popup.noneShowing') }}</Message>
                    <Message v-else-if="list.showingCount > UtilSitePopup.SHOWING_SOFT_MAX" severity="info" size="small" :closable="false" class="mb-3">{{ t('popup.tooMany', { n: list.showingCount }) }}</Message>
                    <!-- 여러 창 배치(사이트 전체 하나) — 바꾸면 바로 저장 -->
                    <div class="mb-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2">
                        <span :id="layoutLabelId" class="text-sm font-medium">{{ t('popup.layoutLabel') }}</span>
                        <SelectButton
                            :model-value="list.layout"
                            :options="layoutOptions"
                            option-label="label"
                            option-value="value"
                            :allow-empty="false"
                            size="small"
                            :aria-labelledby="layoutLabelId"
                            :disabled="!canManage || layoutSaving"
                            @update:model-value="setLayout"
                        >
                            <template #option="{ option }">
                                <i :class="option.icon" class="text-xs" aria-hidden="true" />
                                <span>{{ option.label }}</span>
                            </template>
                        </SelectButton>
                        <span class="text-xs leading-relaxed text-muted-color">{{ t('popup.layoutHelp') }}</span>
                    </div>
                    <p class="m-0 mb-2 text-xs leading-relaxed text-muted-color">{{ t('popup.orderHint') }}</p>
                    <ol class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-busy="busy || ordering">
                        <li v-for="(item, index) in list.items" :key="item.id" class="flex flex-col gap-3 py-3 sm:flex-row sm:items-start" data-testid="site-popup-row">
                            <!-- 순서 -->
                            <div class="flex shrink-0 items-center gap-1 sm:flex-col">
                                <span class="w-7 text-center text-sm font-semibold tabular-nums">{{ item.order }}</span>
                                <template v-if="canManage">
                                    <Button type="button" icon="pi pi-angle-up" size="small" severity="secondary" variant="text" :aria-label="t('popup.moveUp', { name: item.name })" :disabled="index === 0 || ordering" @click="move(item, -1)" />
                                    <Button type="button" icon="pi pi-angle-down" size="small" severity="secondary" variant="text" :aria-label="t('popup.moveDown', { name: item.name })" :disabled="index === list.items.length - 1 || ordering" @click="move(item, 1)" />
                                </template>
                            </div>

                            <!-- 썸네일 -->
                            <button
                                type="button"
                                class="flex aspect-[4/5] w-20 shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-lg border border-(--line) bg-surface-100 p-0 focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-800"
                                :aria-label="t('popup.previewOne', { name: item.name })"
                                @click="openPreview(item)"
                            >
                                <img v-if="item.kind === 'IMAGE' && item.imageUrl" :src="item.imageUrl" alt="" class="size-full object-cover" loading="lazy" />
                                <span v-else-if="item.kind === 'TEXT'" class="flex size-full flex-col gap-1 overflow-hidden bg-white p-2 text-left dark:bg-surface-900">
                                    <span class="line-clamp-2 text-[10px] font-bold leading-tight text-color">{{ item.heading || item.body }}</span>
                                    <span class="line-clamp-4 text-[9px] leading-tight text-muted-color">{{ item.heading ? item.body : '' }}</span>
                                </span>
                                <i v-else class="pi pi-image text-muted-color" aria-hidden="true" />
                            </button>

                            <!-- 내용 -->
                            <div class="flex min-w-0 flex-1 flex-col gap-1">
                                <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                    <Tag :value="t(`popup.status.${item.status}`)" :icon="UtilSitePopup.statusIcon(item.status)" :severity="UtilSitePopup.statusSeverity(item.status)" class="text-[11px]!" />
                                    <Tag :value="t(`popup.kind.${item.kind}`)" :icon="UtilSitePopup.kindIcon(item.kind)" severity="secondary" class="text-[11px]!" />
                                    <Tag :value="t('popup.windowNo', { n: item.windowNo })" icon="pi pi-clone" severity="secondary" class="text-[11px]!" />
                                    <span class="min-w-0 break-words text-sm font-semibold [overflow-wrap:anywhere]">{{ item.name }}</span>
                                </div>
                                <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-color">
                                    <span><i class="pi pi-map-marker mr-1 text-[10px]" aria-hidden="true" />{{ t(`popup.target.${item.target}`) }}</span>
                                    <span class="inline-flex items-center gap-1">
                                        <i v-for="device in item.devices" :key="device" :class="UtilSitePopup.deviceIcon(device)" class="text-[10px]" aria-hidden="true" />{{ UtilSitePopupMessages.devices(t, item) }}
                                    </span>
                                    <span><i class="pi pi-times-circle mr-1 text-[10px]" aria-hidden="true" />{{ t(`popup.hideChoice.${item.hideOption}`) }}</span>
                                </p>
                                <p v-if="item.link" class="m-0 min-w-0 truncate text-xs text-muted-color"><i class="pi pi-link mr-1 text-[10px]" aria-hidden="true" />{{ item.link }}</p>
                                <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs tabular-nums text-muted-color">
                                    <span><i class="pi pi-calendar mr-1 text-[10px]" aria-hidden="true" />{{ UtilSitePopupMessages.period(t, time, item) }}</span>
                                    <span v-if="item.updatedBy">{{ t('popup.updatedBy', { name: item.updatedBy.name ?? '—', at: time.listLabel(item.updatedAt) }) }}</span>
                                </p>
                            </div>

                            <!-- 동작 -->
                            <div class="flex shrink-0 flex-wrap items-center gap-1 sm:flex-col sm:items-end">
                                <div v-if="canManage" class="flex items-center gap-2">
                                    <label :for="`popup-visible-${item.id}`" class="text-xs text-muted-color">{{ t('popup.visibleShort') }}</label>
                                    <ToggleSwitch :model-value="item.visible" :input-id="`popup-visible-${item.id}`" :disabled="togglingId === item.id" @update:model-value="(v: boolean) => setVisible(item, v)" />
                                </div>
                                <div class="flex flex-wrap gap-1">
                                    <Button type="button" :label="t('popup.preview')" icon="pi pi-eye" size="small" severity="secondary" variant="text" @click="openPreview(item)" />
                                    <Button v-if="canManage" type="button" :label="t('common.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="text" @click="openEdit(item)" />
                                    <Button v-if="canManage" type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="text" :loading="removingId === item.id" @click="remove(item)" />
                                </div>
                            </div>
                        </li>
                    </ol>
                </template>
            </template>
        </AppGadget>

        <SitePopupFormDialog v-model:visible="formOpen" :item="editing" :upload-image="api.uploadImage" :saving="saving" :server-errors="formFieldErrors" :form-error="formError" @save="save" />

        <!-- 미리보기 — 한 장(줄) 또는 지금 노출 중인 전부(제목 옆) -->
        <Dialog v-model:visible="previewOpen" modal :draggable="false" :header="previewTarget ? t('popup.previewOneTitle', { name: previewTarget.name }) : t('popup.previewShowingTitle')" :style="{ width: 'min(84rem, calc(100vw - 2rem))' }">
            <div class="flex flex-col gap-3">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ previewTarget ? t('popup.previewOneHelp') : t('popup.previewShowingHelp') }}</p>
                    <SelectButton v-model="previewDevice" :options="deviceOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('popup.previewDevice')" />
                </div>
                <SitePopupPreviewFrame :popups="previewPopups" :device="previewDevice" :empty-text="previewEmpty" :layout="list?.layout ?? 'HORIZONTAL'" />
            </div>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { SitePopupDevice, SitePopupItem, SitePopupLayout, SitePopupListView, SitePopupSaveRequest } from '~/composables/popup/api/useSitePopupApi.types';
import UtilSitePopup from '~/utils/util-site-popup';
import UtilSitePopupMessages from '~/utils/util-site-popup-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('popup.title') });

const api = useSitePopupApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();

/** 바꾸기 권한(공지 띠와 같은 `site.manage`) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('site.manage'));

/** 이미 바뀐 팝업(다른 운영자가 지웠다) — 목록을 다시 받는다 */
const STALE_CODES: ReadonlySet<string> = new Set(['SITE_POPUP_NOT_FOUND']);

const list = ref<SitePopupListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const ordering = ref(false);
const togglingId = ref<string | null>(null);
const removingId = ref<string | null>(null);

onMounted(() => void reload());

let request = 0;
async function reload(): Promise<void> {
    const mine = ++request;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list();
        if (mine !== request) return;
        list.value = found;
    } catch (e) {
        if (mine !== request) return;
        loadError.value = errorOf(e);
        if (list.value) toast.error(loadError.value.message, 8000);
    } finally {
        if (mine === request) busy.value = false;
    }
}

/** 처리 실패 — 이미 바뀐 팝업이면 목록을 다시 받는다 */
function fail(e: unknown): void {
    const error = errorOf(e);
    toast.error(error.fields?.ids ?? error.fields?.visible ?? error.fields?.layout ?? error.message, 8000);
    if (STALE_CODES.has(error.code) || error.fields?.ids) void reload();
}

async function move(item: SitePopupItem, delta: -1 | 1): Promise<void> {
    if (!list.value || ordering.value) return;
    const next = UtilSitePopup.moved(
        list.value.items.map((row) => row.id),
        item.id,
        delta,
    );
    if (!next) return;
    ordering.value = true;
    try {
        list.value = await api.reorder(next);
    } catch (e) {
        fail(e);
    } finally {
        ordering.value = false;
    }
}

/* ── 여러 창 배치 ── */
const layoutLabelId = useId();
const layoutSaving = ref(false);
const layoutOptions = computed(() => UtilSitePopup.LAYOUTS.map((value) => ({ value, label: t(`popup.layout.${value}`), icon: value === 'HORIZONTAL' ? 'pi pi-arrows-h' : 'pi pi-arrows-v' })));

async function setLayout(layout: SitePopupLayout): Promise<void> {
    if (!list.value || layoutSaving.value || layout === list.value.layout) return;
    layoutSaving.value = true;
    try {
        list.value = await api.setLayout(layout);
        toast.success(t('popup.layoutSaved'));
    } catch (e) {
        fail(e);
    } finally {
        layoutSaving.value = false;
    }
}

async function setVisible(item: SitePopupItem, visible: boolean): Promise<void> {
    if (togglingId.value) return;
    togglingId.value = item.id;
    try {
        await api.setVisible(item.id, visible);
        toast.success(visible ? t('popup.shown') : t('popup.hidden'));
        await reload();
    } catch (e) {
        fail(e);
    } finally {
        togglingId.value = null;
    }
}

async function remove(item: SitePopupItem): Promise<void> {
    const ok = await confirm({ title: t('popup.deleteTitle'), message: t('popup.deleteMessage', { name: item.name }), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    removingId.value = item.id;
    try {
        await api.remove(item.id);
        toast.success(t('popup.deleted'));
        await reload();
    } catch (e) {
        fail(e);
    } finally {
        removingId.value = null;
    }
}

/* ── 만들기·고치기 ── */
const formOpen = ref(false);
const editing = ref<SitePopupItem | null>(null);
const saving = ref(false);
const formFieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const KNOWN_FIELDS = ['name', 'imageFileId', 'imageAlt', 'link', 'heading', 'body', 'buttonLabel', 'devices', 'showUntil'];

function openCreate(): void {
    editing.value = null;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

function openEdit(item: SitePopupItem): void {
    editing.value = item;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

async function save(body: SitePopupSaveRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    formFieldErrors.value = {};
    formError.value = '';
    const target = editing.value;
    try {
        if (target) await api.update(target.id, body);
        else await api.create(body);
        formOpen.value = false;
        toast.success(target ? t('popup.updated') : t('popup.created'));
        await reload();
    } catch (e) {
        const error = errorOf(e);
        if (STALE_CODES.has(error.code)) {
            formOpen.value = false;
            fail(error);
        } else if (error.fields && Object.keys(error.fields).length) {
            formFieldErrors.value = { ...error.fields };
            formError.value = Object.entries(error.fields)
                .filter(([key]) => !KNOWN_FIELDS.includes(key))
                .map(([, message]) => message)
                .join(' ');
        } else {
            formError.value = error.message;
        }
    } finally {
        saving.value = false;
    }
}

/* ── 미리보기 ── */
const previewOpen = ref(false);
/** 한 장 미리보기의 팝업 — null 이면 지금 노출 중인 전부 */
const previewTarget = ref<SitePopupItem | null>(null);
const previewDevice = ref<SitePopupDevice>('PC');
const deviceOptions = computed(() => UtilSitePopup.DEVICES.map((value) => ({ value, label: t(`popup.device.${value}`) })));

/** 그 기기에 뜨는 것만 — 모두 보기는 노출 중(SHOWING)인 것을 목록 차례로(홈 화면 기준이라 보일 곳은 가리지 않는다) */
const previewPopups = computed(() => {
    const rows = previewTarget.value ? [previewTarget.value] : (list.value?.items ?? []).filter((item) => item.status === 'SHOWING');
    return rows.filter((item) => item.devices.includes(previewDevice.value)).map((item) => UtilSitePopup.previewOf(item));
});
const previewEmpty = computed(() => (previewTarget.value ? t('popup.previewDeviceOff', { device: t(`popup.device.${previewDevice.value}`) }) : t('popup.previewNoneShowing')));

function openPreview(item: SitePopupItem | null): void {
    previewTarget.value = item;
    if (item && !item.devices.includes(previewDevice.value)) previewDevice.value = item.devices[0] ?? 'PC';
    previewOpen.value = true;
}
</script>
