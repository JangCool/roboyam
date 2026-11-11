<template>
    <!--
        AI 회원 — 프롬프트(`/ai-members/prompts`). 계약 「뉴스 글·프롬프트 관리 (2026-11-11)」. 서버 `useAiMemberAdminApi`(표 ai_prompt_settings — V190).
        저장 단위는 하나(한 번에 PUT — `StickySaveBar`):
          ① 지시문 일곱 칸(공통 · 새 글 · 뉴스 글 · 첫 댓글 · 답글 · 강아지 소리 · 고양이 소리 — 동물 소리 두 칸은 글 방식 DOG·CAT 계정의 새 글·첫 댓글·답글 지시를 대신한다, V191) — 칸마다 「기본값으로 되돌리기」, 기본값과 같으면 서버가 기본값(NULL)으로 둔다.
             자리표시 칩({페르소나}·{게시판}·{모델})을 누르면 마지막으로 고르던 칸의 커서 자리에 넣는다. 출력 형식·인용·최근 제목은 서버가 늘 붙인다(여기서 못 고친다).
          ② 고정 규칙 — 읽기 전용 회색 목록(서버 `fixedRules` — 시스템 맨 뒤에 늘 붙는다).
          ③ 분위기 — 확률(%) + 목록(켜기·이름·지시문·지우기, 추가). 생성 한 번마다 확률로 켜진 것 중 하나.
          ④ 뉴스 글 — 비율(%) · 말머리 키워드 · 최근 일수. 사이트 회원이 쓴 글만 소재(외부 뉴스·검색 API 없음).
          ⑤ 글 모양 — 문단 자동 나누기 · 한 문단 문장 수.
          ⑥ 미리 보기 — 계정 · 종류 · 분위기를 고르면 **저장 전 화면 값**으로 실제 보낼 시스템·사용자 메시지를 보인다(제공사 호출 없음, 읽기 권한).
             동물 소리 계정을 고르면 그 계정의 동물 소리 지시가 들어가고 분위기는 빠진다(뉴스 글을 고르면 새 글로 보인다 — 안내 한 줄).
        권한: 보기·미리 보기 `ai-member.read` · 저장 `ai-member.manage`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('aiMember.promptsTitle')" :subtitle="t('aiMember.promptsSubtitle')" />
        <AiMemberNav :pending="pendingTotal" />

        <div v-if="busy && !settings" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 4" :key="n" height="7rem" />
        </div>
        <ErrorState v-else-if="loadError && !settings" :title="t('aiMember.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />

        <template v-else-if="settings && form">
            <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                <i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.readOnly') }}
            </p>
            <p v-if="settings.updatedAt" class="m-0 text-xs tabular-nums text-muted-color">{{ t('aiMember.promptUpdated', { at: time.full(settings.updatedAt), name: settings.updatedBy ?? '—' }) }}</p>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <!-- ① 지시문 -->
                <AppGadget :title="t('aiMember.promptTextsTitle')">
                    <div class="flex flex-col gap-4">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.promptTextsHelp') }}</p>
                        <div class="flex flex-wrap items-center gap-1.5">
                            <span class="text-xs font-medium text-muted-color">{{ t('aiMember.placeholdersLabel') }}</span>
                            <Button
                                v-for="ph in settings.placeholders"
                                :key="ph"
                                type="button"
                                :label="ph"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                class="px-2! py-0.5! text-[12px]!"
                                :title="t(`aiMember.placeholderHelp.${placeholderKey(ph)}`)"
                                :disabled="!canManage || saving"
                                @mousedown.prevent
                                @click="insertPlaceholder(ph)"
                            />
                        </div>
                        <ul class="m-0 flex list-none flex-col gap-0.5 p-0 text-[12px] text-muted-color">
                            <li v-for="ph in settings.placeholders" :key="ph"><code>{{ ph }}</code> — {{ t(`aiMember.placeholderHelp.${placeholderKey(ph)}`) }}</li>
                        </ul>

                        <fieldset :disabled="!canManage || saving" class="m-0 flex flex-col gap-5 border-0 p-0">
                            <div v-for="key in AI_PROMPT_TEXT_KEYS" :key="key" class="flex flex-col gap-1.5">
                                <div class="flex flex-wrap items-center gap-2">
                                    <Tag
                                        :value="isDefaultText(key) ? t('aiMember.promptDefaultInUse') : t('aiMember.promptCustom')"
                                        :severity="isDefaultText(key) ? 'secondary' : 'info'"
                                        class="ai-member-tag text-[11px]!"
                                    />
                                    <Button
                                        v-if="canManage && !isDefaultText(key)"
                                        type="button"
                                        class="ml-auto"
                                        icon="pi pi-undo"
                                        :label="t('aiMember.promptResetDefault')"
                                        size="small"
                                        severity="secondary"
                                        variant="text"
                                        @click="resetText(key)"
                                    />
                                </div>
                                <LabeledField
                                    :label="t(`aiMember.promptText.${key}`)"
                                    :hint="t('aiMember.promptLength', { n: form[key].length.toLocaleString(), max: maxOf(key).toLocaleString() })"
                                    :help="t(`aiMember.promptTextHelp.${key}`)"
                                    :error="fieldErrors[key]"
                                >
                                    <template #default="{ id, describedBy, invalid }">
                                        <Textarea
                                            :id="id"
                                            v-model="form[key]"
                                            :rows="key === 'common' ? 12 : 4"
                                            auto-resize
                                            fluid
                                            spellcheck="false"
                                            class="font-mono text-[13px]!"
                                            :maxlength="maxOf(key)"
                                            :invalid="invalid || form[key].length > maxOf(key)"
                                            :aria-describedby="describedBy"
                                            @focus="(e: FocusEvent) => remember(key, e)"
                                        />
                                    </template>
                                </LabeledField>
                            </div>
                        </fieldset>
                    </div>
                </AppGadget>

                <!-- ② 고정 규칙(읽기 전용) -->
                <AppGadget :title="t('aiMember.fixedRulesTitle')">
                    <div class="rounded-lg bg-surface-50 px-3 py-2.5 dark:bg-surface-900">
                        <p class="m-0 mb-1.5 text-xs font-medium text-muted-color">{{ t('aiMember.fixedRulesHelp') }}</p>
                        <ol class="m-0 flex flex-col gap-1 pl-5 text-[13px] text-muted-color">
                            <li v-for="(rule, i) in settings.fixedRules" :key="i">{{ rule }}</li>
                        </ol>
                    </div>
                </AppGadget>

                <!-- ③ 분위기 -->
                <AppGadget :title="t('aiMember.moodsTitle')">
                    <template #actions>
                        <Button v-if="canManage && !isDefaultMoods" type="button" icon="pi pi-undo" :label="t('aiMember.promptResetDefault')" size="small" severity="secondary" variant="text" :disabled="saving" @click="resetMoods" />
                    </template>
                    <fieldset :disabled="!canManage || saving" class="m-0 flex flex-col gap-4 border-0 p-0">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.moodsHelp') }}</p>
                        <LabeledField :label="t('aiMember.moodChance')" :hint="t('aiMember.percentHint', { n: settings.defaults.moodChance })" :error="fieldErrors.moodChance">
                            <template #default="{ id, describedBy, invalid }">
                                <InputNumber v-model="form.moodChance" :input-id="id" :min="0" :max="100" suffix="%" :invalid="invalid" :aria-describedby="describedBy" class="max-w-[8rem]" />
                            </template>
                        </LabeledField>
                        <p v-if="fieldErrors.moods" class="m-0 text-xs text-red-700 dark:text-red-400" role="alert">{{ fieldErrors.moods }}</p>
                        <ul class="m-0 flex list-none flex-col gap-2 p-0">
                            <li v-for="(mood, i) in form.moods" :key="i" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5 sm:flex-row sm:items-start">
                                <div class="flex items-center gap-2 sm:w-44 sm:shrink-0">
                                    <ToggleSwitch v-model="mood.enabled" :aria-label="t('aiMember.moodEnabledLabel', { name: mood.name || i + 1 })" />
                                    <InputText
                                        v-model="mood.name"
                                        class="min-w-0 flex-1"
                                        size="small"
                                        :maxlength="settings.limits.moodNameMax"
                                        :placeholder="t('aiMember.moodName')"
                                        :aria-label="t('aiMember.moodName')"
                                        :invalid="Boolean(fieldErrors[`moods[${i}].name`])"
                                    />
                                </div>
                                <div class="flex min-w-0 flex-1 flex-col gap-1">
                                    <Textarea
                                        v-model="mood.prompt"
                                        rows="2"
                                        auto-resize
                                        fluid
                                        class="text-[13px]!"
                                        :maxlength="settings.limits.moodPromptMax"
                                        :placeholder="t('aiMember.moodPrompt')"
                                        :aria-label="t('aiMember.moodPrompt')"
                                        :invalid="Boolean(fieldErrors[`moods[${i}].prompt`])"
                                    />
                                    <small v-if="fieldErrors[`moods[${i}].name`] || fieldErrors[`moods[${i}].prompt`]" class="text-[11px] text-red-700 dark:text-red-400">
                                        {{ [fieldErrors[`moods[${i}].name`], fieldErrors[`moods[${i}].prompt`]].filter(Boolean).join(' ') }}
                                    </small>
                                </div>
                                <Button v-if="canManage" type="button" icon="pi pi-trash" :aria-label="t('aiMember.moodDelete', { name: mood.name || i + 1 })" size="small" severity="danger" variant="text" class="self-end sm:self-start" @click="removeMood(i)" />
                            </li>
                        </ul>
                        <p v-if="form.moods.length === 0" class="m-0 text-sm text-muted-color">{{ t('aiMember.moodsEmpty') }}</p>
                        <Button
                            v-if="canManage"
                            type="button"
                            icon="pi pi-plus"
                            :label="t('aiMember.moodAdd')"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            class="self-start"
                            :disabled="form.moods.length >= settings.limits.moodsMax"
                            @click="addMood"
                        />
                    </fieldset>
                </AppGadget>

                <!-- ④ 뉴스 글 -->
                <AppGadget :title="t('aiMember.newsTitle')">
                    <fieldset :disabled="!canManage || saving" class="m-0 flex flex-col gap-4 border-0 p-0">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.newsHelp') }}</p>
                        <div class="flex flex-wrap items-start gap-4">
                            <LabeledField :label="t('aiMember.newsRatio')" :hint="t('aiMember.percentHint', { n: settings.defaults.newsRatio })" :error="fieldErrors.newsRatio">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputNumber v-model="form.newsRatio" :input-id="id" :min="0" :max="100" suffix="%" :invalid="invalid" :aria-describedby="describedBy" class="max-w-[8rem]" />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('aiMember.newsKeyword')" :hint="t('aiMember.defaultHint', { value: settings.defaults.newsKeyword })" :error="fieldErrors.newsKeyword">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" v-model="form.newsKeyword" :maxlength="settings.limits.newsKeywordMax" :invalid="invalid" :aria-describedby="describedBy" class="w-40" />
                                </template>
                            </LabeledField>
                            <LabeledField
                                :label="t('aiMember.newsDays')"
                                :hint="t('aiMember.rangeHint', { min: settings.limits.newsDaysMin, max: settings.limits.newsDaysMax, n: settings.defaults.newsDays })"
                                :error="fieldErrors.newsDays"
                            >
                                <template #default="{ id, describedBy, invalid }">
                                    <InputNumber v-model="form.newsDays" :input-id="id" :min="settings.limits.newsDaysMin" :max="settings.limits.newsDaysMax" :suffix="t('aiMember.daysSuffix')" :invalid="invalid" :aria-describedby="describedBy" class="max-w-[8rem]" />
                                </template>
                            </LabeledField>
                        </div>
                        <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed text-muted-color">
                            <li>{{ t('aiMember.newsRuleSource', { keyword: form.newsKeyword || settings.defaults.newsKeyword }) }}</li>
                            <li>{{ t('aiMember.newsRuleExclude') }}</li>
                            <li>{{ t('aiMember.newsRuleReference') }}</li>
                            <li>{{ t('aiMember.newsRuleNoExternal') }}</li>
                        </ul>
                    </fieldset>
                </AppGadget>

                <!-- ⑤ 글 모양 -->
                <AppGadget :title="t('aiMember.shapeTitle')">
                    <fieldset :disabled="!canManage || saving" class="m-0 flex flex-col gap-4 border-0 p-0">
                        <div class="flex flex-wrap items-start justify-between gap-3">
                            <div class="min-w-0 flex-1 basis-64">
                                <label :for="ids.paragraph" class="text-sm font-medium">{{ t('aiMember.paragraphAuto') }}</label>
                                <p class="m-0 mt-1 text-xs leading-relaxed text-muted-color">{{ t('aiMember.paragraphAutoHelp') }}</p>
                            </div>
                            <ToggleSwitch v-model="form.paragraphAuto" :input-id="ids.paragraph" />
                        </div>
                        <LabeledField
                            :label="t('aiMember.paragraphSentences')"
                            :hint="t('aiMember.rangeHint', { min: settings.limits.sentencesMin, max: settings.limits.sentencesMax, n: settings.defaults.paragraphSentences })"
                            :error="fieldErrors.paragraphSentences"
                        >
                            <template #default="{ id, describedBy, invalid }">
                                <InputNumber
                                    v-model="form.paragraphSentences"
                                    :input-id="id"
                                    :min="settings.limits.sentencesMin"
                                    :max="settings.limits.sentencesMax"
                                    :disabled="!form.paragraphAuto"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    class="max-w-[8rem]"
                                />
                            </template>
                        </LabeledField>
                    </fieldset>
                </AppGadget>

                <StickySaveBar v-if="canManage" :dirty="changes > 0" :count="changes" :saving="saving" :error-count="errorCount" :error="formError" revertable submit @revert="revert" />
            </form>

            <!-- ⑥ 미리 보기 — 저장 전 화면 값 -->
            <AppGadget :title="t('aiMember.previewTitle')">
                <div class="flex min-w-0 flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.previewHelp') }}</p>
                    <div class="flex flex-wrap items-end gap-3">
                        <div class="flex flex-col gap-1">
                            <label :for="ids.previewAccount" class="text-sm font-medium">{{ t('aiMember.previewAccount') }}</label>
                            <Select v-model="preview.aiMemberId" :input-id="ids.previewAccount" :options="accountOptions" option-label="label" option-value="value" :placeholder="t('aiMember.previewAccountPlaceholder')" class="w-48" />
                        </div>
                        <div class="flex flex-col gap-1">
                            <span :id="ids.previewKind" class="text-sm font-medium">{{ t('aiMember.previewKind') }}</span>
                            <SelectButton v-model="preview.kind" :options="kindOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="ids.previewKind" />
                        </div>
                        <div class="flex flex-col gap-1">
                            <label :for="ids.previewMood" class="text-sm font-medium">{{ t('aiMember.previewMood') }}</label>
                            <Select v-model="preview.mood" :input-id="ids.previewMood" :options="moodOptions" option-label="label" option-value="value" class="w-40" />
                        </div>
                        <Button type="button" icon="pi pi-eye" :label="t('aiMember.previewRun')" size="small" :loading="previewing" :disabled="!preview.aiMemberId" @click="runPreview" />
                    </div>
                    <p v-if="accountOptions.length === 0" class="m-0 text-sm text-muted-color">{{ t('aiMember.previewNoAccounts') }}</p>
                    <Message v-if="previewError" severity="error" size="small" :closable="false">{{ previewError }}</Message>

                    <template v-if="previewResult">
                        <div class="flex flex-wrap items-center gap-2 text-xs text-muted-color">
                            <Tag :value="t(`aiMember.promptKind.${previewResult.kind}`)" severity="secondary" class="ai-member-tag text-[11px]!" />
                            <Tag v-if="previewResult.voice !== 'NORMAL'" :value="t(`aiMember.voice.${previewResult.voice}`)" severity="info" class="ai-member-tag text-[11px]!" />
                            <span>{{ t('aiMember.previewPlace', { place: previewResult.place ?? t('aiMember.previewPlaceNone') }) }}</span>
                            <span>· {{ previewResult.mood ? t('aiMember.previewMoodUsed', { name: previewResult.mood }) : t('aiMember.previewMoodNone') }}</span>
                        </div>
                        <Message v-if="previewResult.voice !== 'NORMAL'" severity="info" size="small" :closable="false">{{ t(previewResult.kind === 'NEWS' ? 'aiMember.previewAnimalNews' : 'aiMember.previewAnimal') }}</Message>
                        <Message v-if="previewResult.sourceMissing" severity="warn" size="small" :closable="false">{{ t('aiMember.previewSourceMissing', { keyword: form.newsKeyword, days: form.newsDays }) }}</Message>
                        <p v-if="previewResult.source" class="m-0 text-xs text-muted-color">{{ t('aiMember.previewSource', { title: previewResult.source.title, id: previewResult.source.postId }) }}</p>
                        <p v-if="previewResult.sample" class="m-0 text-xs text-muted-color">{{ t('aiMember.previewSample') }}</p>
                        <div class="flex min-w-0 flex-col gap-1">
                            <p class="m-0 text-sm font-medium">{{ t('aiMember.previewSystem') }}</p>
                            <pre class="m-0 max-h-[28rem] overflow-auto rounded-lg border border-(--line) bg-surface-50 p-3 text-[12px] leading-relaxed whitespace-pre-wrap [overflow-wrap:anywhere] dark:bg-surface-900">{{ previewResult.system }}</pre>
                        </div>
                        <div v-if="previewResult.user !== null" class="flex min-w-0 flex-col gap-1">
                            <p class="m-0 text-sm font-medium">{{ t('aiMember.previewUser') }}</p>
                            <pre class="m-0 max-h-[28rem] overflow-auto rounded-lg border border-(--line) bg-surface-50 p-3 text-[12px] leading-relaxed whitespace-pre-wrap [overflow-wrap:anywhere] dark:bg-surface-900">{{ previewResult.user }}</pre>
                        </div>
                    </template>
                </div>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import {
    AI_PROMPT_KINDS,
    AI_PROMPT_TEXT_KEYS,
    type AiMemberListView,
    type AiPromptKind,
    type AiPromptMood,
    type AiPromptPreview,
    type AiPromptSaveRequest,
    type AiPromptSettings,
    type AiPromptTextKey,
    type AiPromptValues,
} from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAiMember from '~/utils/util-ai-member';
import UtilAiMemberMessages from '~/utils/util-ai-member-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('aiMember.promptsTitle') });

const api = useAiMemberAdminApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const ids = { paragraph: useId(), previewAccount: useId(), previewKind: useId(), previewMood: useId() };

/** 저장 — 서버 `ai-member.manage`(보기·미리 보기는 `ai-member.read`) */
const canManage = computed(() => can(UtilAiMember.PERMISSION_MANAGE));

const settings = ref<AiPromptSettings | null>(null);
const accounts = ref<AiMemberListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const pendingTotal = computed(() => (accounts.value ? accounts.value.items.reduce((sum, row) => sum + row.pendingDrafts, 0) : null));
const accountOptions = computed(() => (accounts.value?.items ?? []).map((row) => ({ value: row.id, label: row.member.nickname })));

/* ── 한 번에 저장하는 칸 ── */
const form = ref<AiPromptValues | null>(null);
const initial = ref<AiPromptValues | null>(null);
const saving = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');

function copyValues(v: AiPromptValues): AiPromptValues {
    return { ...v, moods: v.moods.map((m) => ({ ...m })) };
}

function fill(s: AiPromptSettings): void {
    settings.value = s;
    initial.value = copyValues(s.values);
    form.value = copyValues(s.values);
}

function revert(): void {
    if (initial.value) form.value = copyValues(initial.value);
    fieldErrors.value = {};
    formError.value = '';
}

function sameText(a: string, b: string): boolean {
    return a.replace(/\r\n?/g, '\n').trim() === b.replace(/\r\n?/g, '\n').trim();
}

function sameMoods(a: AiPromptMood[], b: AiPromptMood[]): boolean {
    return a.length === b.length && a.every((m, i) => m.name.trim() === b[i]!.name.trim() && m.prompt.trim() === b[i]!.prompt.trim() && m.enabled === b[i]!.enabled);
}

function maxOf(key: AiPromptTextKey): number {
    const limits = settings.value?.limits;
    if (!limits) return 0;
    return key === 'common' ? limits.commonMax : limits.taskMax;
}

/** 칸이 비었거나 기본값과 같다 — 서버도 같은 규칙으로 기본값(NULL)으로 둔다 */
function isDefaultText(key: AiPromptTextKey): boolean {
    if (!form.value || !settings.value) return true;
    return form.value[key].trim() === '' || sameText(form.value[key], settings.value.defaults[key]);
}

const isDefaultMoods = computed(() => (form.value && settings.value ? sameMoods(form.value.moods, settings.value.defaults.moods) : true));

function resetText(key: AiPromptTextKey): void {
    if (form.value && settings.value) form.value[key] = settings.value.defaults[key];
}

function resetMoods(): void {
    if (form.value && settings.value) form.value.moods = settings.value.defaults.moods.map((m) => ({ ...m }));
}

function addMood(): void {
    form.value?.moods.push({ name: '', prompt: '', enabled: true });
}

function removeMood(index: number): void {
    form.value?.moods.splice(index, 1);
    // 차례가 바뀌어 칸 오류가 엉뚱한 줄에 붙지 않게 분위기 칸 오류만 지운다
    fieldErrors.value = Object.fromEntries(Object.entries(fieldErrors.value).filter(([key]) => !key.startsWith('moods[')));
    if (preview.mood !== null && preview.mood >= (form.value?.moods.length ?? 0)) preview.mood = null;
}

const changes = computed(() => {
    const a = form.value;
    const b = initial.value;
    if (!a || !b) return 0;
    let n = 0;
    for (const key of AI_PROMPT_TEXT_KEYS) if (!sameText(a[key], b[key])) n++;
    if (!sameMoods(a.moods, b.moods)) n++;
    if (a.moodChance !== b.moodChance) n++;
    if (a.newsRatio !== b.newsRatio) n++;
    if (a.newsKeyword.trim() !== b.newsKeyword.trim()) n++;
    if (a.newsDays !== b.newsDays) n++;
    if (a.paragraphAuto !== b.paragraphAuto) n++;
    if (a.paragraphSentences !== b.paragraphSentences) n++;
    return n;
});

const errorCount = computed(() => Object.keys(fieldErrors.value).length);

/** 서버에 넘기는 본문 — 평범한 객체로. 분위기가 기본 목록과 같으면 null(기본값) */
function requestOf(f: AiPromptValues): AiPromptSaveRequest {
    const raw = toRaw(f);
    return { ...raw, moods: isDefaultMoods.value ? null : raw.moods.map((m) => ({ ...m })) };
}

let seq = 0;
async function reload(): Promise<void> {
    const mine = ++seq;
    busy.value = true;
    loadError.value = null;
    try {
        const [s, list] = await Promise.all([api.prompts(), api.list()]);
        if (mine !== seq) return;
        fill(s);
        accounts.value = list;
        if (!preview.aiMemberId && list.items.length > 0) preview.aiMemberId = list.items[0]!.id;
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e);
    } finally {
        if (mine === seq) busy.value = false;
    }
}

onMounted(() => void reload());

/** 서버 칸 오류 — 칸에 붙는 것은 칸에, 나머지는 저장 줄 한 줄로 */
const FIELD_KEYS = new Set<string>([...AI_PROMPT_TEXT_KEYS, 'moodChance', 'moods', 'newsRatio', 'newsKeyword', 'newsDays', 'paragraphSentences']);

async function save(): Promise<void> {
    const f = form.value;
    if (!f || saving.value || changes.value === 0) return;
    saving.value = true;
    fieldErrors.value = {};
    formError.value = '';
    try {
        fill(await api.savePrompts(requestOf(f)));
        toast.success(t('aiMember.promptsSaved'));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) {
            fieldErrors.value = { ...error.fields };
            formError.value = Object.entries(error.fields)
                .filter(([key]) => !FIELD_KEYS.has(key) && !key.startsWith('moods['))
                .map(([, message]) => message)
                .join(' ');
        } else {
            formError.value = UtilAiMemberMessages.error(t, error);
        }
    } finally {
        saving.value = false;
    }
}

/* ── 자리표시 칩 — 마지막으로 고르던 칸의 커서 자리에 넣는다 ── */
const lastFocus = shallowRef<{ key: AiPromptTextKey; el: HTMLTextAreaElement } | null>(null);

function remember(key: AiPromptTextKey, event: FocusEvent): void {
    if (event.target instanceof HTMLTextAreaElement) lastFocus.value = { key, el: event.target };
}

function placeholderKey(ph: string): string {
    return ph === '{페르소나}' ? 'persona' : ph === '{게시판}' ? 'place' : ph === '{모델}' ? 'model' : 'other';
}

function insertPlaceholder(ph: string): void {
    const f = form.value;
    if (!f) return;
    const target = lastFocus.value;
    const key: AiPromptTextKey = target?.key ?? 'common';
    const text = f[key];
    const start = target?.el.selectionStart ?? text.length;
    const end = target?.el.selectionEnd ?? text.length;
    f[key] = text.slice(0, start) + ph + text.slice(end);
    // 넣은 뒤 커서를 자리표시 뒤로
    void nextTick(() => {
        const el = lastFocus.value?.el;
        if (el && lastFocus.value?.key === key) {
            el.focus();
            el.setSelectionRange(start + ph.length, start + ph.length);
        }
    });
}

/* ── 미리 보기 — 저장 전 화면 값으로(제공사 호출 없음) ── */
const preview = reactive<{ aiMemberId: string; kind: AiPromptKind; mood: number | null }>({ aiMemberId: '', kind: 'POST', mood: null });
const previewResult = ref<AiPromptPreview | null>(null);
const previewing = ref(false);
const previewError = ref('');

const kindOptions = computed(() => AI_PROMPT_KINDS.map((value) => ({ value, label: t(`aiMember.promptKind.${value}`) })));
const moodOptions = computed(() => [
    { value: null as number | null, label: t('aiMember.previewMoodOff') },
    ...(form.value?.moods ?? []).map((m, i) => ({ value: i as number | null, label: (m.name.trim() || t('aiMember.moodUnnamed', { n: i + 1 })) + (m.enabled ? '' : ` (${t('aiMember.moodOff')})`) })),
]);

async function runPreview(): Promise<void> {
    const f = form.value;
    if (!f || previewing.value || !preview.aiMemberId) return;
    previewing.value = true;
    previewError.value = '';
    try {
        previewResult.value = await api.previewPrompt({ aiMemberId: preview.aiMemberId, kind: preview.kind, mood: preview.mood, settings: requestOf(f) });
    } catch (e) {
        const error = errorOf(e);
        previewResult.value = null;
        previewError.value = error.fields && Object.keys(error.fields).length ? Object.values(error.fields).join(' ') : UtilAiMemberMessages.error(t, error);
    } finally {
        previewing.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (changes.value === 0) return true;
    return confirm({ title: t('aiMember.leaveTitle'), message: t('aiMember.leaveMessage'), confirmLabel: t('aiMember.leaveConfirm'), tone: 'danger' });
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.ai-member-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
