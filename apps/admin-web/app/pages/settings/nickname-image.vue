<template>
    <!--
        닉네임 이미지 신청 조건(`/settings/nickname-image`, 설정 메뉴 › 회원 › 닉네임 이미지) — 회원이 닉네임 이미지를 신청할 수 있는 조건을 정한다(사용자 결정 2026-10-24).
        방식 둘: 등급 기준(최소 등급 하나 + 이메일 인증, 늘 필요) · 사용자 정의(최소 등급(없음 가능) · 가입 후 일수 · 글·댓글 수 · 보유 포인트 · 이메일 인증 필요 여부 — 모두 채워야 신청, 0 = 그 조건 없음).
        위쪽 요약 한 줄은 저장된 조건이고, 고친 값이 있으면 「저장하면」 줄이 함께 보인다. 고른 등급이 지워졌으면(`gradeMissing`) 경고와 함께 다시 고르게 한다.
        **저장은 한 번에**(PUT) — 떠 있는 저장 줄(`StickySaveBar`, `forms.md` 2-4). 칸은 `InputText` + `inputmode=numeric`(포인트 정책과 같은 이유). 범위는 서버와 같고(`UtilNicknameImage.LIMITS`) 판정은 서버다.
        서버가 거절한 칸(`400 VALIDATION fields.<칸>`)은 그 칸 아래에 서버 문구 그대로. 권한 `nickname-image.manage` 가 없으면 저장 줄 대신 읽기 전용 안내, 칸은 `readonly`·고르개는 잠금.
        이미 적용 중인 이미지는 조건이 바뀌어도 그대로다(안내 한 줄). 저장하지 않은 변경이 있는 채 떠나면 확인을 묻는다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('nicknameImage.settingsTitle')" :subtitle="t('nicknameImage.settingsSubtitle')" />

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !saved" :title="t('nicknameImage.settingsSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <!-- 읽기 실패 -->
        <AppGadget v-else-if="!saved" :title="t('nicknameImage.settingsTitle')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('nicknameImage.settingsLoadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <p class="m-0 text-xs text-muted-color">
                {{ saved.updatedAt ? t('nicknameImage.settingsLastUpdated', { at: time.full(saved.updatedAt), by: saved.updatedBy?.name ?? '—' }) : t('nicknameImage.settingsNeverUpdated') }}
            </p>

            <AppGadget :title="t('nicknameImage.settingsSection')" anchor="nickname-image-requirements" :changes="changed.length" :invalid="errorCount">
                <div class="flex flex-col gap-5">
                    <!-- 요약 — 저장된 조건, 고쳤으면 저장했을 때의 조건도 -->
                    <div class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-4 py-3 text-sm">
                        <p class="m-0 flex flex-wrap items-baseline gap-x-2">
                            <span class="text-xs text-muted-color">{{ t('nicknameImage.summaryNow') }}</span>
                            <span class="font-medium">{{ savedSummary }}</span>
                        </p>
                        <p v-if="dirty" class="m-0 flex flex-wrap items-baseline gap-x-2">
                            <span class="text-xs text-muted-color">{{ t('nicknameImage.summaryAfter') }}</span>
                            <span class="font-medium text-primary">{{ formSummary }}</span>
                        </p>
                    </div>

                    <Message v-if="saved.gradeMissing" severity="warn" :closable="false" size="small">{{ t('nicknameImage.settingsGradeMissing') }}</Message>

                    <!-- 방식 -->
                    <div class="flex flex-col gap-1.5" role="group" :aria-labelledby="modeLabelId">
                        <span :id="modeLabelId" class="text-sm font-medium">{{ t('nicknameImage.settingsModeLabel') }}</span>
                        <SelectButton
                            :model-value="form.mode"
                            :options="modeOptions"
                            option-label="label"
                            option-value="value"
                            :allow-empty="false"
                            :disabled="!canEdit || saving"
                            :aria-labelledby="modeLabelId"
                            class="self-start"
                            @update:model-value="onMode"
                        />
                        <small class="text-xs leading-relaxed text-muted-color">{{ form.mode === 'GRADE' ? t('nicknameImage.settingsModeGradeHelp') : t('nicknameImage.settingsModeCustomHelp') }}</small>
                        <small v-if="serverErrors.mode" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ serverErrors.mode }}</small>
                    </div>

                    <!-- 최소 등급 — 등급 기준이면 필수, 사용자 정의면 「등급 조건 없음」도 고를 수 있다 -->
                    <div class="grid gap-x-4 gap-y-4 sm:grid-cols-[repeat(3,minmax(0,15rem))]">
                        <LabeledField :label="t('nicknameImage.settingsGradeLabel')" :help="form.mode === 'GRADE' ? t('nicknameImage.settingsGradeHelpGrade') : t('nicknameImage.settingsGradeHelpCustom')" :error="errorOf('gradeKey')">
                            <template #default="{ id, describedBy, invalid }">
                                <Select
                                    :model-value="form.gradeKey"
                                    :input-id="id"
                                    :options="gradeOptions"
                                    option-label="label"
                                    option-value="value"
                                    :placeholder="gradePlaceholder"
                                    :disabled="!canEdit || saving"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    class="w-full"
                                    @update:model-value="(value: string) => onField('gradeKey', value ?? '')"
                                />
                            </template>
                        </LabeledField>
                    </div>

                    <!-- 사용자 정의 조건 -->
                    <template v-if="form.mode === 'CUSTOM'">
                        <div class="grid gap-x-4 gap-y-4 sm:grid-cols-[repeat(3,minmax(0,15rem))]">
                            <LabeledField
                                v-for="f in NUMBER_FIELDS"
                                :key="f.field"
                                :label="t(f.labelKey)"
                                :hint="UtilNicknameImageMessages.range(t, f.field, locale)"
                                :help="t('nicknameImage.settingsZeroOff')"
                                :error="errorOf(f.field)"
                            >
                                <template #default="{ id, describedBy, invalid }">
                                    <div class="flex items-center gap-2">
                                        <InputText
                                            :id="id"
                                            :model-value="form[f.field]"
                                            :name="f.field"
                                            inputmode="numeric"
                                            autocomplete="off"
                                            :readonly="!canEdit || saving"
                                            :invalid="invalid"
                                            :aria-describedby="describedBy"
                                            :aria-required="true"
                                            class="w-32 text-right tabular-nums"
                                            @update:model-value="(value: string | undefined) => onField(f.field, value ?? '')"
                                        />
                                        <span class="text-sm text-muted-color">{{ t(f.unitKey) }}</span>
                                    </div>
                                </template>
                            </LabeledField>
                        </div>

                        <div class="flex items-start gap-3">
                            <ToggleSwitch
                                :model-value="form.requireEmailVerified"
                                :input-id="emailId"
                                :disabled="!canEdit || saving"
                                :aria-describedby="`${emailId}-help`"
                                @update:model-value="(value: boolean) => onField('requireEmailVerified', value)"
                            />
                            <div class="flex flex-col gap-0.5">
                                <label :for="emailId" class="text-sm font-medium">{{ t('nicknameImage.settingsEmailLabel') }}</label>
                                <small :id="`${emailId}-help`" class="text-xs text-muted-color">{{ t('nicknameImage.settingsEmailHelp') }}</small>
                                <small v-if="serverErrors.requireEmailVerified" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ serverErrors.requireEmailVerified }}</small>
                            </div>
                        </div>
                    </template>

                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('nicknameImage.settingsNoteApplied') }}</span></li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('nicknameImage.settingsNoteSuspended') }}</span></li>
                    </ul>
                </div>
            </AppGadget>

            <StickySaveBar v-if="canEdit" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changed.length" :error-count="errorCount" :error="formError" @revert="revert" />
            <p v-else class="m-0 text-xs text-muted-color">{{ t('nicknameImage.settingsReadOnly') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { NicknameImageSettingsField, NicknameImageSettingsMode, NicknameImageSettingsView } from '~/composables/member/api/useNicknameImageApi.types';
import UtilNicknameImage, { type NicknameImageNumberField, type NicknameImageSettingsForm } from '~/utils/util-nickname-image';
import UtilNicknameImageMessages from '~/utils/util-nickname-image-messages';

/** 사용자 정의의 숫자 칸 — 이름·단위 키 */
const NUMBER_FIELDS: readonly { field: NicknameImageNumberField; labelKey: string; unitKey: string }[] = [
    { field: 'minDays', labelKey: 'nicknameImage.settingsDaysLabel', unitKey: 'nicknameImage.unitDays' },
    { field: 'minActivity', labelKey: 'nicknameImage.settingsActivityLabel', unitKey: 'nicknameImage.unitCount' },
    { field: 'minPoints', labelKey: 'nicknameImage.settingsPointsLabel', unitKey: 'nicknameImage.unitPoints' },
];

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('nicknameImage.settingsTitle') });

const api = useNicknameImageApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();

/** 조건을 바꿀 수 있나(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canEdit = computed(() => can('nickname-image.manage'));

const modeLabelId = useId();
const emailId = useId();

/** 서버가 준 값. `null` = 아직 못 받았다 */
const saved = ref<NicknameImageSettingsView | null>(null);
const form = ref<NicknameImageSettingsForm>({ mode: 'GRADE', gradeKey: '', minDays: '0', minActivity: '0', minPoints: '0', requireEmailVerified: true });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
/** 칸에 붙지 않는 저장 오류 */
const formError = ref('');
/** 서버가 거절한 칸별 문구 — 그 칸을 고치면 지운다 */
const serverErrors = ref<Partial<Record<NicknameImageSettingsField, string>>>({});

const grades = computed(() => saved.value?.grades ?? []);

const modeOptions = computed(() => UtilNicknameImage.MODES.map((mode) => ({ value: mode, label: mode === 'GRADE' ? t('nicknameImage.modeGrade') : t('nicknameImage.modeCustom') })));

/** 등급 고르개 — 「베테랑 이상」 꼴, 사용자 정의면 맨 앞에 「등급 조건 없음」(값 `''`) */
const gradeOptions = computed(() => {
    const items = grades.value.map((grade) => ({ value: grade.key, label: UtilNicknameImageMessages.gradeAtLeast(t, grade.label) }));
    return form.value.mode === 'CUSTOM' ? [{ value: '', label: t('nicknameImage.settingsNoGrade') }, ...items] : items;
});
/** 고른 값이 목록에 없을 때 — 지워진 등급이면 그렇다고, 아니면 고르라고 */
const gradePlaceholder = computed(() => (form.value.gradeKey ? t('nicknameImage.summaryGradeMissing') : t('nicknameImage.settingsGradePlaceholder')));

const problems = computed(() => UtilNicknameImage.settingsProblems(form.value, grades.value));
const changed = computed(() => (saved.value ? UtilNicknameImage.changedSettings(form.value, saved.value) : []));
const dirty = computed(() => changed.value.length > 0);
const errorCount = computed(() => UtilNicknameImage.SETTINGS_FIELDS.filter((field) => problems.value[field] || serverErrors.value[field]).length);
/** 서버가 거절한 칸은 저장을 막지 않는다 — 다시 보내 볼 수 있어야 하고, 서버가 다시 판정한다 */
const canSave = computed(() => canEdit.value && dirty.value && Object.keys(problems.value).length === 0);

const savedSummary = computed(() => (saved.value ? UtilNicknameImageMessages.summary(t, saved.value, grades.value, locale.value) : ''));
const formSummary = computed(() => (saved.value ? UtilNicknameImageMessages.summary(t, UtilNicknameImage.toSettingsRequest(form.value, saved.value), grades.value, locale.value) : ''));

/** 칸 아래 오류 — 화면이 미리 검사한 문제가 먼저, 없으면 서버가 거절한 문구 */
function errorOf(field: NicknameImageNumberField | 'gradeKey'): string {
    const problem = problems.value[field];
    if (problem) return UtilNicknameImageMessages.problem(t, problem, field, locale.value);
    return serverErrors.value[field] ?? '';
}

function clearServer(field: NicknameImageSettingsField): void {
    if (!serverErrors.value[field]) return;
    const { [field]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

function onField<K extends keyof NicknameImageSettingsForm>(field: K, value: NicknameImageSettingsForm[K]): void {
    form.value = { ...form.value, [field]: value };
    clearServer(field);
}

/** 방식을 바꾼다 — 등급 기준으로 가면 「등급 조건 없음」은 고를 수 없어 빈 채로 두고 다시 고르게 한다 */
function onMode(mode: NicknameImageSettingsMode): void {
    if (!mode) return;
    onField('mode', mode);
}

onMounted(load);

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.loadSettings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: NicknameImageSettingsView): void {
    saved.value = view;
    form.value = UtilNicknameImage.settingsFormOf(view);
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (saving.value || !canSave.value || !saved.value) return;
    const body = UtilNicknameImage.toSettingsRequest(form.value, saved.value);
    formError.value = '';
    serverErrors.value = {};
    saving.value = true;
    try {
        applySaved(await api.saveSettings(body));
        toast.success(t('nicknameImage.settingsSaved'));
    } catch (e) {
        const rejected = UtilNicknameImage.settingsFieldErrorsOf(e);
        if (rejected) {
            serverErrors.value = rejected.fields;
            formError.value = [Object.keys(rejected.fields).length ? t('nicknameImage.settingsFieldsRejected') : '', rejected.rest].filter(Boolean).join(' ');
        } else {
            formError.value = `${t('nicknameImage.settingsSaveFailed')} — ${toApiError(e).message}`;
        }
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('nicknameImage.leaveTitle'), message: t('nicknameImage.leaveMessage'), confirmLabel: t('nicknameImage.leaveConfirm'), cancelLabel: t('nicknameImage.leaveCancel'), tone: 'danger' });
});
</script>
