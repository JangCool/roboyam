<template>
    <!--
        오픈소스 라이선스(`/settings/oss-licenses`) — 설정 메뉴 「운영·약관」 묶음, 약관 다음. 계약 `docs/features/oss-licenses-contract.md`(사용자 확정 2026-11-10).
        공개 화면 `/licenses` 에 보일 것: 자동 수집 목록(정적 파일 — 빌드 때 모은다, 여기서는 읽기만) + 안내 문구 + 직접 추가 항목(이름순).
        구획 셋: ① 안내 문구(여러 줄, 비우면 기본 문구 — 구획 안 저장 줄 `SaveBar`) ② 직접 추가 항목(표 · 추가·고치기(대화상자)·숨기기·지우기 — 줄마다 바로 저장)
          ③ 자동 수집 목록(개수·마지막으로 모은 날·찾기 — 읽기 전용).
        권한: 보기 `site.read`(메뉴), 바꾸기 `site.manage` — 없으면 저장 줄·처리 단추가 없고 안내 한 줄. 사라진 항목(`404 OSS_NOTICE_ENTRY_NOT_FOUND`)은 서버 문구를 토스트로 보이고 다시 받는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('ossLicense.title')" :subtitle="t('ossLicense.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('ossLicense.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            </template>
        </PageTitle>

        <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i class="pi pi-lock" aria-hidden="true" />{{ t('ossLicense.readOnlyHint') }}
        </p>

        <div v-if="busy && !view" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 3" :key="n" height="6rem" />
        </div>
        <ErrorState v-else-if="loadError && !view" :title="t('ossLicense.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />

        <template v-else-if="view">
            <!-- ① 안내 문구 -->
            <AppGadget :title="t('ossLicense.introTitle')">
                <div class="flex flex-col gap-3">
                    <LabeledField :label="t('ossLicense.introLabel')" :hint="t('ossLicense.counter', { n: UtilOssLicense.length(introDraft), max: UtilOssLicense.INTRO_MAX })" :help="t('ossLicense.introHelp')" :error="introError">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea
                                :id="id"
                                v-model="introDraft"
                                name="intro"
                                rows="3"
                                fluid
                                auto-resize
                                :placeholder="defaultIntro"
                                :readonly="!canManage"
                                :disabled="introSaving"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                            />
                        </template>
                    </LabeledField>
                    <SaveBar v-if="canManage" :dirty="introDirty" :saving="introSaving" :disabled="introTooLong" :error-count="introTooLong ? 1 : 0" :error="introFormError" @save="saveIntro" @revert="revertIntro" />
                </div>
            </AppGadget>

            <!-- ② 직접 추가 항목 -->
            <AppGadget :title="t('ossLicense.entriesTitle')">
                <template #title-extra>
                    <span class="text-xs tabular-nums text-muted-color">{{ view.entries.length }}</span>
                </template>
                <div class="flex flex-col gap-3">
                    <div class="flex flex-wrap items-start justify-between gap-2">
                        <p class="m-0 max-w-prose text-xs leading-relaxed text-muted-color">{{ t('ossLicense.entriesHint') }}</p>
                        <Button v-if="canManage" type="button" :label="t('ossLicense.add')" icon="pi pi-plus" size="small" @click="openCreate" />
                    </div>
                    <DataTable :value="view.entries" data-key="id" :size="prefs.density === 'compact' ? 'small' : undefined" class="min-w-0" data-testid="oss-entry-table">
                        <template #empty>
                            <EmptyState icon="pi pi-code" :title="t('ossLicense.entriesEmpty')" :description="t('ossLicense.entriesEmptyHint')" />
                        </template>
                        <Column :header="t('ossLicense.col.name')">
                            <template #body="{ data }">
                                <span class="break-words font-medium [overflow-wrap:anywhere]">{{ data.name }}</span>
                                <Tag v-if="data.hidden" :value="t('ossLicense.hiddenTag')" severity="secondary" class="ml-1.5 text-[11px]!" />
                                <a v-if="UtilOssLicense.safeUrl(data.sourceUrl)" :href="UtilOssLicense.safeUrl(data.sourceUrl)!" target="_blank" rel="noopener" class="mt-0.5 block truncate text-xs text-primary hover:underline">{{ data.sourceUrl }}</a>
                            </template>
                        </Column>
                        <Column :header="t('ossLicense.col.version')" style="width: 8rem">
                            <template #body="{ data }">
                                <span class="tabular-nums text-muted-color">{{ data.version ?? '—' }}</span>
                            </template>
                        </Column>
                        <Column :header="t('ossLicense.col.license')" style="width: 12rem">
                            <template #body="{ data }">
                                <span class="break-words">{{ data.license }}</span>
                            </template>
                        </Column>
                        <Column :header="t('ossLicense.col.category')" style="width: 9rem">
                            <template #body="{ data }">{{ t(`ossLicense.category.${data.category}`) }}</template>
                        </Column>
                        <Column :header="t('ossLicense.col.shown')" style="width: 9rem">
                            <template #body="{ data }">
                                <ToggleSwitch
                                    :model-value="!data.hidden"
                                    :disabled="!canManage || togglingId === data.id"
                                    :aria-label="`${data.name} — ${t('ossLicense.col.shown')}`"
                                    @update:model-value="(v: boolean) => setHidden(data, !v)"
                                />
                            </template>
                        </Column>
                        <Column v-if="canManage" :header="t('ossLicense.col.actions')" style="width: 10rem">
                            <template #body="{ data }">
                                <div class="flex flex-wrap gap-1">
                                    <Button type="button" :label="t('common.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="text" @click="openEdit(data)" />
                                    <Button type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="text" :loading="removingId === data.id" @click="remove(data)" />
                                </div>
                            </template>
                        </Column>
                    </DataTable>
                </div>
            </AppGadget>

            <!-- ③ 자동 수집 목록(읽기 전용) -->
            <AppGadget :title="t('ossLicense.collectedTitle')">
                <template #title-extra>
                    <span v-if="collected" class="text-xs tabular-nums text-muted-color">{{ t('ossLicense.collectedSummary', { n: collected.items.length, date: time.serviceDate(collected.collectedAt) }) }}</span>
                </template>
                <div class="flex flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ossLicense.collectedHelp') }}</p>
                    <Message v-if="collectedError" severity="warn" size="small" :closable="false">{{ collectedError }}</Message>
                    <template v-else-if="collected">
                        <IconField class="w-full sm:w-72">
                            <InputIcon class="pi pi-search" />
                            <InputText v-model="query" type="search" size="small" :placeholder="t('ossLicense.searchPlaceholder')" :aria-label="t('ossLicense.searchLabel')" fluid />
                        </IconField>
                        <DataTable :value="collectedRows" data-key="id" paginator :rows="20" :size="prefs.density === 'compact' ? 'small' : undefined" class="min-w-0" data-testid="oss-collected-table">
                            <template #empty>
                                <EmptyState icon="pi pi-search" :title="t('ossLicense.collectedEmpty')" />
                            </template>
                            <Column :header="t('ossLicense.col.name')">
                                <template #body="{ data }">
                                    <span class="break-all [overflow-wrap:anywhere]">{{ data.name }}</span>
                                    <a v-if="UtilOssLicense.safeUrl(data.repository)" :href="UtilOssLicense.safeUrl(data.repository)!" target="_blank" rel="noopener" class="ml-1.5 text-xs text-primary hover:underline">{{ t('ossLicense.repository') }}</a>
                                </template>
                            </Column>
                            <Column :header="t('ossLicense.col.version')" style="width: 9rem">
                                <template #body="{ data }"><span class="tabular-nums text-muted-color">{{ data.version }}</span></template>
                            </Column>
                            <Column :header="t('ossLicense.col.license')" style="width: 14rem">
                                <template #body="{ data }"><span class="break-words">{{ data.license }}</span></template>
                            </Column>
                            <Column :header="t('ossLicense.col.usedIn')" style="width: 11rem">
                                <template #body="{ data }">
                                    <span class="text-xs">{{ usedInLabel(data) }}</span>
                                </template>
                            </Column>
                        </DataTable>
                    </template>
                </div>
            </AppGadget>
        </template>

        <OssNoticeEntryFormDialog v-model:visible="formOpen" :item="editing" :saving="saving" :server-errors="formFieldErrors" :form-error="formError" @save="save" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { OssCollectedFile, OssCollectedItem, OssNoticeAdminView, OssNoticeEntry, OssNoticeEntrySaveRequest } from '~/composables/osslicense/api/useOssLicenseAdminApi.types';
import UtilOssLicense from '~/utils/util-oss-license';

const { t } = useI18n();
useSeoMeta({ title: () => t('ossLicense.title') });

const api = useOssLicenseAdminApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

/** 바꾸기 권한(약관 옆 운영 주체와 같은 `site.manage`) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('site.manage'));
/** 이미 바뀐 항목(다른 운영자가 지웠다) — 다시 받는다 */
const STALE_CODES: ReadonlySet<string> = new Set(['OSS_NOTICE_ENTRY_NOT_FOUND']);

const view = ref<OssNoticeAdminView | null>(null);
const collected = ref<OssCollectedFile | null>(null);
const collectedError = ref('');
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

let request = 0;
/** 설정(서버)과 자동 수집 목록(정적 파일)을 함께 — 정적 파일만 못 받으면 그 구획에 안내만 */
async function reload(): Promise<void> {
    const mine = ++request;
    busy.value = true;
    loadError.value = null;
    const [settings, file] = await Promise.allSettled([api.get(), api.collected()]);
    if (mine !== request) return;
    if (settings.status === 'fulfilled') {
        applyView(settings.value);
    } else {
        loadError.value = errorOf(settings.reason);
        if (view.value) toast.error(loadError.value.message, 8000);
    }
    if (file.status === 'fulfilled') {
        collected.value = file.value;
        collectedError.value = '';
    } else {
        collectedError.value = t('ossLicense.collectedLoadFailed');
    }
    busy.value = false;
}
onMounted(() => void reload());

/** 받은 설정을 화면에 — 안내 문구 입력은 고치는 중이 아니면(또는 방금 저장했으면) 저장한 값으로 맞춘다 */
function applyView(next: OssNoticeAdminView, resetIntro = !introDirty.value): void {
    view.value = next;
    introSaved.value = next.intro ?? '';
    if (resetIntro) introDraft.value = next.intro ?? '';
}

/* ── ① 안내 문구 ── */
const introSaved = ref('');
const introDraft = ref('');
const introSaving = ref(false);
const introFormError = ref('');
const introServerError = ref('');
const defaultIntro = computed(() => t('ossLicense.introDefault', { site: '{사이트}' }));
const introDirty = computed(() => introDraft.value.trim() !== introSaved.value.trim());
const introTooLong = computed(() => UtilOssLicense.length(introDraft.value.trim()) > UtilOssLicense.INTRO_MAX);
const introError = computed(() => (introTooLong.value ? t('ossLicense.problem.tooLong', { max: UtilOssLicense.INTRO_MAX }) : introServerError.value));

async function saveIntro(): Promise<void> {
    if (introSaving.value || !introDirty.value || introTooLong.value) return;
    introSaving.value = true;
    introFormError.value = '';
    introServerError.value = '';
    try {
        const text = introDraft.value.trim();
        applyView(await api.saveIntro(text ? introDraft.value : null), true);
        toast.success(t('ossLicense.introSaved'));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields?.intro) introServerError.value = error.fields.intro;
        else introFormError.value = error.message;
    } finally {
        introSaving.value = false;
    }
}

function revertIntro(): void {
    introDraft.value = introSaved.value;
    introServerError.value = '';
    introFormError.value = '';
}

onBeforeRouteLeave(async () => {
    if (!introDirty.value) return true;
    return confirm({ title: t('ossLicense.leaveTitle'), message: t('ossLicense.leaveMessage'), confirmLabel: t('ossLicense.leaveConfirm'), tone: 'danger' });
});

/* ── ② 직접 추가 항목 ── */
const togglingId = ref<string | null>(null);
const removingId = ref<string | null>(null);

/** 처리 실패 — 이미 바뀐 항목이면 다시 받는다 */
function fail(e: unknown): void {
    const error = errorOf(e);
    toast.error(error.fields?.hidden ?? error.message, 8000);
    if (STALE_CODES.has(error.code)) void reload();
}

/** 한 줄 바꾼 결과를 목록에 — 이름순은 다시 받을 때 서버가 맞춘다 */
function replaceEntry(entry: OssNoticeEntry): void {
    if (!view.value) return;
    view.value = { ...view.value, entries: view.value.entries.map((row) => (row.id === entry.id ? entry : row)) };
}

async function setHidden(entry: OssNoticeEntry, hidden: boolean): Promise<void> {
    if (togglingId.value) return;
    togglingId.value = entry.id;
    try {
        replaceEntry(await api.setHidden(entry.id, hidden));
        toast.success(hidden ? t('ossLicense.hidden') : t('ossLicense.shown'));
    } catch (e) {
        fail(e);
    } finally {
        togglingId.value = null;
    }
}

async function remove(entry: OssNoticeEntry): Promise<void> {
    const ok = await confirm({ title: t('ossLicense.deleteTitle'), message: t('ossLicense.deleteMessage', { name: entry.name }), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    removingId.value = entry.id;
    try {
        await api.remove(entry.id);
        toast.success(t('ossLicense.deleted'));
        await reload();
    } catch (e) {
        fail(e);
    } finally {
        removingId.value = null;
    }
}

const formOpen = ref(false);
const editing = ref<OssNoticeEntry | null>(null);
const saving = ref(false);
const formFieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const KNOWN_FIELDS = ['name', 'version', 'license', 'copyright', 'licenseText', 'sourceUrl', 'category'];

function openCreate(): void {
    editing.value = null;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

function openEdit(entry: OssNoticeEntry): void {
    editing.value = entry;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

async function save(body: OssNoticeEntrySaveRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    formFieldErrors.value = {};
    formError.value = '';
    const target = editing.value;
    try {
        if (target) await api.update(target.id, body);
        else await api.create(body);
        formOpen.value = false;
        toast.success(target ? t('ossLicense.updated') : t('ossLicense.created'));
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

/* ── ③ 자동 수집 목록 ── */
const query = ref('');
const collectedRows = computed(() => UtilOssLicense.searchCollected(collected.value?.items ?? [], query.value));

function usedInLabel(item: OssCollectedItem): string {
    const parts = item.usedIn.map((u) => t(`ossLicense.usedIn.${u}`));
    if (item.category === 'fontIcon') parts.push(t('ossLicense.fontIcon'));
    return parts.join(' · ');
}
</script>
