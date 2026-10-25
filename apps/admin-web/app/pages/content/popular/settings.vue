<template>
    <!--
        인기글 설정 — 인기글 순위를 정하는 점수식(가중치·시간 감쇠)과 조건(최소 추천·댓글·조회, 한 사람 최대 글 수, 운영진 전용 게시판 빼기)과 이번 주 시작 요일 11칸(계약 `docs/features/popular-highlight-contract.md`).
        community 관리자 앱의 인기글 설정(`home-settings` 13칸)에서 홈 개수 두 칸을 뺐다(로보얌 홈은 아직 없다). 문구는 community `api-popular.md` 9절의 권고를 따른다.
        **저장은 한 번에**(11칸 모두, PUT 한 번) — [저장]·[되돌리기]·상태 글은 `<form>` 맨 끝의 떠 있는 줄(`StickySaveBar`, `forms.md` 2-4). 숫자 칸은 `InputText` + `inputmode=decimal`(`InputNumber` 는 범위 밖 값을 말없이 고친다).
        범위·소수 자리 검사는 서버가 준 `limits`(`UtilPopular`) — 하나라도 틀리면 [저장]이 꺼진다. 서버가 거절한 칸(`400 VALIDATION` `fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 저장 줄 안에.
        [기본값으로] 는 서버가 준 `defaults` 로 칸을 채울 뿐 저장하지 않는다(저장 줄로 저장한다).
        저장하면 서버가 순위 다시 계산을 요청한다 — 응답 직후엔 아직 옛 설정의 순위라서 `recomputePending` 이 true 인 동안 「다시 계산 중」을 보이고 잠시 뒤(4초) 미리보기만 다시 읽는다(최대 8번, 고치던 칸은 건드리지 않는다).
        권한: 보기 `popular.read`(메뉴), 저장 `popular.settings` — 없으면 칸이 `readonly` 이고 저장 줄 대신 읽기 전용 안내. 저장하지 않은 변경이 있는 채 떠나면 확인을 묻는다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('popular.settingsTitle')" :subtitle="t('popular.settingsSubtitle')">
            <template #actions>
                <Button v-if="canEdit && saved" type="button" :label="t('popular.resetDefaults')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" :disabled="saving || !differsFromDefaults" @click="applyDefaults" />
                <NuxtLink v-if="can('popular.read')" to="/content/popular" class="inline-flex items-center gap-1.5 text-sm text-primary hover:underline">
                    <i class="pi pi-chart-line text-xs" aria-hidden="true" />{{ t('popular.openRanking') }}
                </NuxtLink>
            </template>
        </PageTitle>

        <AppGadget v-if="loading && !saved" :title="t('popular.sectionWeights')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3.5rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!saved" :title="t('popular.settingsTitle')">
            <ErrorState :title="t('popular.settingsLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else-if="form" class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <p class="m-0 text-xs text-muted-color">
                {{ saved.updatedAt ? t('popular.lastUpdated', { at: time.full(saved.updatedAt), by: saved.updatedBy?.name ?? '—' }) : t('popular.neverUpdated') }}
            </p>

            <AppGadget v-for="section in SECTIONS" :key="section.anchor" :title="t(section.titleKey)" :anchor="section.anchor" :changes="sectionChanges(section)" :invalid="sectionInvalid(section)">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t(section.leadKey) }}</p>

                <div v-if="section.fields.length" class="grid gap-x-4 gap-y-3 sm:grid-cols-[repeat(3,minmax(0,15rem))]">
                    <LabeledField
                        v-for="f in section.fields"
                        :key="f.field"
                        :label="t(`popular.field.${f.field}`)"
                        :hint="UtilPopularMessages.range(t, saved.limits[f.field], locale)"
                        :help="t(`popular.help.${f.field}`)"
                        :error="errorOf(f.field)"
                    >
                        <template #default="{ id, describedBy, invalid }">
                            <div class="flex items-center gap-2">
                                <InputText
                                    :id="id"
                                    :model-value="form[f.field]"
                                    :name="f.field"
                                    :inputmode="saved.limits[f.field].decimals > 0 ? 'decimal' : 'numeric'"
                                    autocomplete="off"
                                    :readonly="!canEdit || saving"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    :aria-required="true"
                                    class="w-32 text-right tabular-nums"
                                    @update:model-value="(value: string | undefined) => onInput(f.field, value ?? '')"
                                />
                                <span v-if="f.unit" class="text-sm text-muted-color">{{ t(`popular.unit.${f.unit}`) }}</span>
                            </div>
                        </template>
                    </LabeledField>
                </div>

                <!-- 조건 구획 — 운영진 전용 게시판 빼기(켬/끔) -->
                <div v-if="section.anchor === 'popular-conditions'" class="mt-4 flex flex-col gap-1">
                    <div class="flex items-center gap-3">
                        <ToggleSwitch :model-value="form.excludeStaffBoards" :input-id="staffToggleId" :disabled="!canEdit || saving" @update:model-value="(v: boolean) => onToggle(v)" />
                        <label :for="staffToggleId" class="text-sm font-medium">{{ t('popular.field.excludeStaffBoards') }}</label>
                    </div>
                    <p class="m-0 text-xs leading-relaxed" :class="serverErrors.excludeStaffBoards ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ serverErrors.excludeStaffBoards || t('popular.help.excludeStaffBoards') }}</p>
                </div>

                <!-- 기간 구획 — 이번 주 시작 요일 -->
                <div v-if="section.anchor === 'popular-period'" class="flex flex-col gap-1.5" role="group" :aria-labelledby="weekLabelId">
                    <span :id="weekLabelId" class="text-sm font-medium">{{ t('popular.field.weekStart') }}</span>
                    <SelectButton
                        :model-value="form.weekStart"
                        :options="weekOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        :disabled="!canEdit || saving"
                        class="self-start"
                        @update:model-value="(v: PopularWeekStart) => onWeekStart(v)"
                    />
                    <p class="m-0 text-xs leading-relaxed" :class="serverErrors.weekStart ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ serverErrors.weekStart || t('popular.help.weekStart') }}</p>
                </div>
            </AppGadget>

            <!-- 지금 실시간 순위 상위 10개 — 점수는 계산 시점, 추천·댓글·조회는 지금 값. 저장 뒤 다시 계산되면 바뀐다 -->
            <AppGadget :title="t('popular.previewTitle')">
                <template #title-extra>
                    <Tag v-if="preview.recomputePending" :value="t('popular.recomputing')" icon="pi pi-spin pi-spinner" severity="info" />
                </template>
                <template #actions>
                    <Button type="button" icon="pi pi-refresh" :label="t('popular.refresh')" size="small" severity="secondary" variant="text" :loading="previewLoading" @click="refreshPreview()" />
                </template>

                <p class="mb-3 text-xs leading-relaxed text-muted-color">
                    {{ UtilPopularMessages.computedAt(t, time, preview.computedAt) }} · {{ t('popular.previewLead') }}
                </p>
                <Message v-if="preview.recomputePending" severity="info" size="small" :closable="false" class="mb-3">{{ t('popular.recomputeNotice') }}</Message>

                <EmptyState v-if="preview.current.length === 0" icon="pi pi-chart-line" :title="t('popular.previewEmpty')" :description="t('popular.previewEmptyHint')" />
                <div v-else class="overflow-x-auto">
                    <table class="w-full min-w-[40rem] text-[13px]">
                        <thead>
                            <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                                <th class="w-12 py-2 pr-2 font-medium">{{ t('popular.colRank') }}</th>
                                <th class="py-2 pr-2 font-medium">{{ t('popular.colPost') }}</th>
                                <th class="w-20 py-2 pr-2 text-right font-medium">{{ t('popular.colScore') }}</th>
                                <th class="w-36 py-2 pr-2 text-right font-medium">{{ t('popular.colIngredients') }}</th>
                                <th class="w-28 py-2 font-medium">{{ t('popular.colCreatedAt') }}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="row in preview.current" :key="row.postId" class="border-b border-(--line) last:border-0">
                                <td class="py-2 pr-2 font-semibold tabular-nums">{{ row.rank }}</td>
                                <td class="py-2 pr-2">
                                    <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                        <span class="min-w-0 break-words">{{ row.title }}</span>
                                        <Tag v-if="row.fill" :value="t(`popular.fill.${row.fill}`)" severity="secondary" class="text-[11px]!" />
                                    </div>
                                    <span class="text-xs text-muted-color">{{ localizedName(row.boardName, row.boardNameEn) }}</span>
                                </td>
                                <td class="py-2 pr-2 text-right font-medium tabular-nums">{{ UtilPopular.score(row.score, locale) }}</td>
                                <td class="py-2 pr-2 text-right text-xs tabular-nums text-muted-color">{{ t('popular.ingredients', { likes: row.likeCount, comments: row.commentCount, views: row.viewCount }) }}</td>
                                <td class="py-2 text-xs tabular-nums text-muted-color">{{ time.listLabel(row.createdAt) }}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </AppGadget>

            <StickySaveBar v-if="canEdit" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changed.length" :error-count="errorCount" :error="formError" @revert="revert" />
            <p v-else class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('popular.settingsReadOnly') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PopularNumberField, PopularPreviewItem, PopularSettingField, PopularSettingsView, PopularWeekStart } from '~/composables/content/api/usePopularApi.types';
import UtilPopular, { type PopularSettingsForm } from '~/utils/util-popular';
import UtilPopularMessages from '~/utils/util-popular-messages';

/** 숫자 칸 하나 — 단위 글자 키(`popular.unit.*`), 없으면 단위 없음 */
interface SettingField {
    field: PopularNumberField;
    unit: 'hours' | 'likes' | 'comments' | 'views' | 'posts' | null;
}
/** 구획 = 우측 구역 내비 한 항목. 켬/끔·요일 칸은 구획 안에 따로 그린다(`extra`) */
interface SettingSection {
    anchor: string;
    titleKey: string;
    leadKey: string;
    fields: SettingField[];
    extra: PopularSettingField[];
}

const SECTIONS: SettingSection[] = [
    {
        anchor: 'popular-weights',
        titleKey: 'popular.sectionWeights',
        leadKey: 'popular.leadWeights',
        fields: [
            { field: 'likeWeight', unit: null },
            { field: 'commentWeight', unit: null },
            { field: 'viewWeight', unit: null },
        ],
        extra: [],
    },
    {
        anchor: 'popular-decay',
        titleKey: 'popular.sectionDecay',
        leadKey: 'popular.leadDecay',
        fields: [
            { field: 'gravity', unit: null },
            { field: 'ageOffsetHours', unit: 'hours' },
        ],
        extra: [],
    },
    {
        anchor: 'popular-conditions',
        titleKey: 'popular.sectionConditions',
        leadKey: 'popular.leadConditions',
        fields: [
            { field: 'minLikes', unit: 'likes' },
            { field: 'minComments', unit: 'comments' },
            { field: 'minViews', unit: 'views' },
            { field: 'maxPerAuthor', unit: 'posts' },
        ],
        extra: ['excludeStaffBoards'],
    },
    {
        anchor: 'popular-period',
        titleKey: 'popular.sectionPeriod',
        leadKey: 'popular.leadPeriod',
        fields: [],
        extra: ['weekStart'],
    },
];

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('popular.settingsTitle') });

const api = usePopularApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const staffToggleId = useId();
const weekLabelId = useId();

/** 저장 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canEdit = computed(() => can('popular.settings'));

/** 서버가 준 값(저장된 값). `null` = 아직 못 받았다 */
const saved = ref<PopularSettingsView | null>(null);
const form = ref<PopularSettingsForm | null>(null);
/** 미리보기(실시간 상위 10개) — 다시 계산을 기다리며 이것만 다시 읽는다(고치던 칸은 그대로) */
const preview = ref<{ current: PopularPreviewItem[]; computedAt: string | null; recomputePending: boolean }>({ current: [], computedAt: null, recomputePending: false });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const previewLoading = ref(false);
const saving = ref(false);
const formError = ref('');
/** 서버가 거절한 칸별 문구 — 그 칸을 고치면 지운다 */
const serverErrors = ref<Partial<Record<PopularSettingField, string>>>({});

const weekOptions = computed(() => (saved.value?.weekStarts ?? ['MONDAY', 'SUNDAY']).map((value) => ({ value, label: t(`popular.weekStart.${value}`) })));

const problems = computed(() => (form.value && saved.value ? UtilPopular.validate(form.value, saved.value.limits) : {}));
const changed = computed(() => (form.value && saved.value ? UtilPopular.changedFields(form.value, saved.value.settings) : []));
const dirty = computed(() => changed.value.length > 0);
const differsFromDefaults = computed(() => Boolean(form.value && saved.value && UtilPopular.differsFrom(form.value, saved.value.defaults)));
const errorCount = computed(() => UtilPopular.FIELDS.filter((field) => problems.value[field as PopularNumberField] || serverErrors.value[field]).length);
/** 서버가 거절한 칸은 저장을 막지 않는다 — 고치지 않고 다시 보내 볼 수 있어야 하고 서버가 다시 판정한다 */
const canSave = computed(() => canEdit.value && dirty.value && Object.keys(problems.value).length === 0);

function fieldsOf(section: SettingSection): PopularSettingField[] {
    return [...section.fields.map((f) => f.field), ...section.extra];
}
function sectionChanges(section: SettingSection): number {
    return fieldsOf(section).filter((field) => changed.value.includes(field)).length;
}
function sectionInvalid(section: SettingSection): number {
    return fieldsOf(section).filter((field) => problems.value[field as PopularNumberField] || serverErrors.value[field]).length;
}

/** 칸 아래 오류 — 화면이 미리 검사한 문제가 먼저, 없으면 서버가 거절한 문구 */
function errorOf(field: PopularNumberField): string {
    const problem = problems.value[field];
    if (problem && saved.value) return UtilPopularMessages.problem(t, problem, saved.value.limits[field], locale.value);
    return serverErrors.value[field] ?? '';
}

function clearServerError(field: PopularSettingField): void {
    if (!serverErrors.value[field]) return;
    const { [field]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

function onInput(field: PopularNumberField, value: string): void {
    if (!form.value) return;
    form.value = { ...form.value, [field]: value };
    clearServerError(field);
    // 세 가중치가 모두 0 이라는 서버 거절은 likeWeight 에 붙는다 — 다른 가중치를 고쳐도 지운다
    if (field === 'commentWeight' || field === 'viewWeight') clearServerError('likeWeight');
}

function onToggle(value: boolean): void {
    if (!form.value) return;
    form.value = { ...form.value, excludeStaffBoards: value };
    clearServerError('excludeStaffBoards');
}

function onWeekStart(value: PopularWeekStart): void {
    if (!form.value) return;
    form.value = { ...form.value, weekStart: value };
    clearServerError('weekStart');
}

onMounted(load);

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.settings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: PopularSettingsView): void {
    saved.value = view;
    form.value = UtilPopular.formOf(view.settings);
    serverErrors.value = {};
    formError.value = '';
    applyPreview(view);
}

function applyPreview(view: PopularSettingsView): void {
    preview.value = { current: view.current, computedAt: view.computedAt, recomputePending: view.recomputePending };
    schedulePoll();
}

function revert(): void {
    if (!saved.value) return;
    form.value = UtilPopular.formOf(saved.value.settings);
    serverErrors.value = {};
    formError.value = '';
}

/** 기본값으로 칸을 채운다 — 저장은 저장 줄로 */
function applyDefaults(): void {
    if (!saved.value) return;
    form.value = UtilPopular.formOf(saved.value.defaults);
    serverErrors.value = {};
    formError.value = '';
}

async function save(): Promise<void> {
    if (saving.value || !canSave.value || !form.value) return;
    const body = UtilPopular.toRequest(form.value);
    if (!body) return;
    formError.value = '';
    serverErrors.value = {};
    saving.value = true;
    try {
        const view = await api.saveSettings(body);
        pollCount = 0;
        applySaved(view);
        toast.success(view.recomputeRequested ? t('popular.savedRecompute') : t('popular.saved'));
    } catch (e) {
        const rejected = UtilPopular.fieldErrorsOf(e);
        if (rejected) {
            serverErrors.value = rejected.fields;
            formError.value = [Object.keys(rejected.fields).length ? t('popular.saveFieldsRejected') : '', rejected.rest].filter(Boolean).join(' ');
        } else {
            formError.value = `${t('popular.saveFailed')} — ${toApiError(e).message}`;
        }
    } finally {
        saving.value = false;
    }
}

/* ── 다시 계산 기다리기 — 미리보기만 다시 읽는다 ── */
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let pollCount = 0;

function schedulePoll(): void {
    if (pollTimer) clearTimeout(pollTimer);
    pollTimer = null;
    if (!preview.value.recomputePending || pollCount >= UtilPopular.RECOMPUTE_POLL_MAX) return;
    pollTimer = setTimeout(() => {
        pollCount += 1;
        void refreshPreview(true);
    }, UtilPopular.RECOMPUTE_POLL_MS);
}

/** 미리보기 다시 읽기 — [새로고침]·다시 계산 기다리기. 실패는 단추로 눌렀을 때만 알린다 */
async function refreshPreview(silent = false): Promise<void> {
    if (previewLoading.value) return;
    if (!silent) pollCount = 0;
    previewLoading.value = true;
    try {
        const view = await api.settings();
        if (saved.value) saved.value = { ...saved.value, current: view.current, computedAt: view.computedAt, recomputePending: view.recomputePending };
        applyPreview(view);
    } catch (e) {
        if (!silent) toast.error(toApiError(e).message, 8000);
    } finally {
        previewLoading.value = false;
    }
}

onBeforeUnmount(() => {
    if (pollTimer) clearTimeout(pollTimer);
});

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('popular.leaveTitle'), message: t('popular.leaveMessage'), confirmLabel: t('popular.leaveConfirm'), cancelLabel: t('popular.leaveCancel'), tone: 'danger' });
});
</script>
