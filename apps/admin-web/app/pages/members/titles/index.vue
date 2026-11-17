<template>
    <!--
        회원 › 회원 칭호 › 칭호(`/members/titles`, member-title-contract 「화면」·「작가 칭호 설정」). 서버 호출은 `useTitleApi`.
        · 맨 위 「작가 칭호 설정」 두 칸(이달의 작가 한 달 최대 인원 1~20 · 명예 작가가 되는 이달의 작가 횟수 1~24) — 구획 안 저장 줄(`SaveBar`, 이 구획만 저장).
          기준을 올려도 이미 받은 명예 작가는 그대로, 내리면 새 기준을 채운 회원에게 바로 소급(안내 한 줄).
        · 칭호 표: 그림·이름 칩 · 받는 방법 · 심사하는 사람 · 단계 · 가진 회원 수 · 켜짐 · 기본 칭호. 줄을 누르거나 [칭호 만들기]면 편집 서랍(`TitleEditDrawer`).
        권한: 화면 `title.read`(메뉴) · 만들기·고치기·설정 `title.manage`. 서랍의 등급·게시판 목록은 `grade.read`·`board.read` 가 있을 때만 받는다(없으면 키로 보인다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <TitleAdminTabs active="titles">
            <template v-if="canManage" #actions>
                <Button type="button" :label="t('memberTitle.create')" icon="pi pi-plus" size="small" data-testid="title-create" @click="openCreate" />
            </template>
        </TitleAdminTabs>

        <!-- 작가 칭호 설정 -->
        <AppGadget :title="t('memberTitle.settings.title')">
            <div v-if="settingsError && !settings" class="py-2">
                <ErrorState :title="t('memberTitle.settings.loadFailed')" :description="settingsError" retry @retry="loadSettings" />
            </div>
            <div v-else-if="!settings" class="grid gap-3 sm:grid-cols-2" aria-hidden="true">
                <Skeleton height="4rem" />
                <Skeleton height="4rem" />
            </div>
            <form v-else class="flex flex-col gap-3" novalidate @submit.prevent="saveSettings">
                <div class="grid gap-4 sm:grid-cols-2">
                    <LabeledField :label="t('memberTitle.settings.monthlyMax')" :help="t('memberTitle.settings.monthlyMaxHelp', UtilTitle.MONTHLY_MAX_RANGE)" :error="settingsFieldErrors.monthlyMax ?? ''">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber
                                v-model="settingsForm.monthlyMax"
                                :input-id="id"
                                :min="UtilTitle.MONTHLY_MAX_RANGE.min"
                                :max="UtilTitle.MONTHLY_MAX_RANGE.max"
                                show-buttons
                                :suffix="t('memberTitle.settings.peopleSuffix')"
                                :disabled="!canManage"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                input-class="w-24"
                            />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('memberTitle.settings.honoraryCount')" :help="t('memberTitle.settings.honoraryCountHelp', UtilTitle.HONORARY_RANGE)" :error="settingsFieldErrors.honoraryCount ?? ''">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber
                                v-model="settingsForm.honoraryCount"
                                :input-id="id"
                                :min="UtilTitle.HONORARY_RANGE.min"
                                :max="UtilTitle.HONORARY_RANGE.max"
                                show-buttons
                                :suffix="t('memberTitle.settings.timesSuffix')"
                                :disabled="!canManage"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                input-class="w-24"
                            />
                        </template>
                    </LabeledField>
                </div>
                <p class="m-0 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
                    <span>{{ t('memberTitle.settings.notice') }}</span>
                </p>
                <SaveBar v-if="canManage" :dirty="settingsDirty" :count="settingsChanged" :saving="settingsSaving" :error="settingsSaveError" @save="saveSettings" @revert="revertSettings" />
                <p v-else class="m-0 text-xs text-muted-color">{{ t('memberTitle.readonlyNotice') }}</p>
            </form>
        </AppGadget>

        <!-- 칭호 표 -->
        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="titles" data-key="key" :loading="loading" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable :row-class="() => 'cursor-pointer'" @row-click="(e) => openEdit(e.data)">
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('memberTitle.loadFailed')" :description="loadError" retry :retrying="loading" @retry="loadTitles" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-star" :title="t('memberTitle.empty')" />
                </template>
                <Column :header="t('memberTitle.col.title')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <div class="flex items-center gap-2">
                            <TitleChip :title="UtilTitle.lookOf(data)" />
                            <Tag v-if="data.builtin" :value="t('memberTitle.builtin')" severity="secondary" class="text-[11px]!" />
                            <Tag v-if="!data.enabled" :value="t('memberTitle.disabled')" severity="warn" class="text-[11px]!" />
                        </div>
                    </template>
                </Column>
                <Column :header="t('memberTitle.col.method')" style="min-width: 7rem">
                    <template #body="{ data }"><span class="text-[13px]">{{ t(`memberTitle.method.${data.method}`) }}</span></template>
                </Column>
                <Column :header="t('memberTitle.col.reviewer')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span v-if="data.method === 'APPLY'" class="text-[13px]">{{ t(`memberTitle.reviewer.${data.reviewer}`) }}<template v-if="data.reviewer === 'OPERATOR_AND_BOARD_MANAGER'"> · {{ boardNameOf(data) }}</template></span>
                        <span v-else class="text-[13px] text-muted-color">—</span>
                    </template>
                </Column>
                <Column :header="t('memberTitle.col.conditions')" style="min-width: 14rem">
                    <template #body="{ data }">
                        <ul v-if="data.conditions.length || data.tierMetric" class="m-0 flex list-none flex-col gap-0.5 p-0 text-[12px]">
                            <li v-for="(c, i) in data.conditions" :key="i">{{ UtilTitleMessages.condition(t, c, gradeName, boardName) }}</li>
                            <li v-if="data.tierMetric" class="text-muted-color">{{ t('memberTitle.tierLine', { metric: t(`memberTitle.metric.${data.tierMetric}`), bronze: thresholdOf(data, 'BRONZE'), silver: thresholdOf(data, 'SILVER'), gold: thresholdOf(data, 'GOLD') }) }}</li>
                        </ul>
                        <span v-else class="text-[12px] text-muted-color">—</span>
                    </template>
                </Column>
                <Column :header="t('memberTitle.col.holders')" style="min-width: 6rem">
                    <template #body="{ data }"><span class="text-[13px] tabular-nums">{{ data.holders.toLocaleString(locale) }}</span></template>
                </Column>
                <Column style="width: 4rem">
                    <template #body="{ data }">
                        <Button type="button" :icon="canManage ? 'pi pi-pencil' : 'pi pi-eye'" severity="secondary" variant="text" size="small" rounded :aria-label="canManage ? t('memberTitle.edit', { name: data.name }) : t('memberTitle.view', { name: data.name })" @click.stop="openEdit(data)" />
                    </template>
                </Column>
            </DataTable>
        </div>

        <TitleEditDrawer
            v-model:visible="drawerOpen"
            :title="editing"
            :grades="grades"
            :boards="boards"
            :next-sort-order="nextSortOrder"
            :can-manage="canManage"
            :saving="saving"
            :error="saveError"
            :field-errors="saveFieldErrors"
            @save="onSave"
        />
    </div>
</template>

<script setup lang="ts">
import type { AdminTitle, AdminTitleRequest, TitleSettings, TitleSettingsRequest } from '~/composables/title/api/useTitleApi.types';
import UtilTitle from '~/utils/util-title';
import UtilTitleMessages from '~/utils/util-title-messages';

const { t, locale } = useI18n();
const api = useTitleApi();
const gradeApi = useGradeApi();
const boardApi = useBoardApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();

const canManage = computed(() => can('title.manage'));

/* ── 작가 칭호 설정 ── */
const settings = ref<TitleSettings | null>(null);
const settingsForm = reactive<TitleSettingsRequest>({ monthlyMax: 3, honoraryCount: 3, reapplyDays: 30 });
const settingsError = ref('');
const settingsSaving = ref(false);
const settingsSaveError = ref('');
const settingsFieldErrors = ref<Record<string, string>>({});
const settingsChanged = computed(() => (settings.value ? Number(settings.value.monthlyMax !== settingsForm.monthlyMax) + Number(settings.value.honoraryCount !== settingsForm.honoraryCount) : 0));
const settingsDirty = computed(() => settingsChanged.value > 0);

function assignSettings(value: TitleSettings): void {
    settingsForm.monthlyMax = value.monthlyMax;
    settingsForm.honoraryCount = value.honoraryCount;
    settingsForm.reapplyDays = value.reapplyDays;
}

async function loadSettings(): Promise<void> {
    settingsError.value = '';
    try {
        const value = await api.settings();
        settings.value = value;
        assignSettings(value);
    } catch (e) {
        settingsError.value = errorOf(e).message;
    }
}

function revertSettings(): void {
    if (settings.value) assignSettings(settings.value);
    settingsSaveError.value = '';
    settingsFieldErrors.value = {};
}

async function saveSettings(): Promise<void> {
    if (!canManage.value || !settingsDirty.value || settingsSaving.value) return;
    settingsSaving.value = true;
    settingsSaveError.value = '';
    settingsFieldErrors.value = {};
    try {
        // 다시 신청까지 일수는 이 화면에 칸이 없어 받은 값을 그대로 돌려 보낸다
        const saved = await api.saveSettings({ monthlyMax: settingsForm.monthlyMax, honoraryCount: settingsForm.honoraryCount, reapplyDays: settingsForm.reapplyDays });
        settings.value = saved;
        assignSettings(saved);
        toast.success(saved.honoraryGranted > 0 ? t('memberTitle.settings.honoraryGranted', { count: saved.honoraryGranted }) : t('common.saved'));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields) settingsFieldErrors.value = { ...error.fields };
        settingsSaveError.value = error.fields ? '' : error.message;
    } finally {
        settingsSaving.value = false;
    }
}

/* ── 칭호 표 ── */
const titles = ref<AdminTitle[]>([]);
const loading = ref(true);
const loadError = ref('');

async function loadTitles(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        titles.value = await api.list();
    } catch (e) {
        loadError.value = errorOf(e).message;
    } finally {
        loading.value = false;
    }
}

/** 서랍의 등급·게시판 목록 — 권한이 있을 때만, 못 받아도 화면은 그대로(키로 보인다) */
const grades = ref<{ key: string; label: string }[]>([]);
const boards = ref<{ id: string; name: string }[]>([]);
async function loadOptions(): Promise<void> {
    const [gradeResult, boardResult] = await Promise.allSettled([can('grade.read') ? gradeApi.load() : Promise.reject(), can('board.read') ? boardApi.list() : Promise.reject()]);
    if (gradeResult.status === 'fulfilled') grades.value = gradeResult.value.grades.map((g) => ({ key: g.gradeKey, label: g.label }));
    if (boardResult.status === 'fulfilled') boards.value = boardResult.value.boards.map((b) => ({ id: b.id, name: localizedName(b.name, b.nameEn) }));
}

void loadSettings();
void loadTitles();
void loadOptions();

function gradeName(key: string): string {
    return grades.value.find((g) => g.key === key)?.label ?? key;
}
function boardName(id: string): string {
    return boards.value.find((b) => b.id === id)?.name ?? id;
}
/** 연결 게시판 이름 — 서버 `boardName` 이 먼저, 없으면 게시판 목록에서 */
function boardNameOf(title: AdminTitle): string {
    return title.boardName ?? (title.boardId ? boardName(title.boardId) : '—');
}
/** 단계 문턱 한 칸 */
function thresholdOf(title: AdminTitle, tier: 'BRONZE' | 'SILVER' | 'GOLD'): number | string {
    const value = tier === 'BRONZE' ? title.tierBronze : tier === 'SILVER' ? title.tierSilver : title.tierGold;
    return value ?? '—';
}

/* ── 편집 서랍 ── */
const drawerOpen = ref(false);
const editing = ref<AdminTitle | null>(null);
const saving = ref(false);
const saveError = ref('');
const saveFieldErrors = ref<Record<string, string>>({});
const nextSortOrder = computed(() => titles.value.reduce((max, x) => Math.max(max, x.sortOrder), 0) + 1);

function resetSaveState(): void {
    saveError.value = '';
    saveFieldErrors.value = {};
}
function openCreate(): void {
    editing.value = null;
    resetSaveState();
    drawerOpen.value = true;
}
function openEdit(title: AdminTitle): void {
    editing.value = title;
    resetSaveState();
    drawerOpen.value = true;
}

async function onSave(body: AdminTitleRequest, key: string | null): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    resetSaveState();
    try {
        if (key) await api.update(key, body);
        else await api.create(body);
        drawerOpen.value = false;
        toast.success(key ? t('memberTitle.saved', { name: body.name }) : t('memberTitle.created', { name: body.name }));
        await loadTitles();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields) saveFieldErrors.value = { ...error.fields };
        saveError.value = error.fields && Object.keys(error.fields).length ? '' : error.message;
    } finally {
        saving.value = false;
    }
}

useSeoMeta({ title: () => t('memberTitle.title') });
</script>
