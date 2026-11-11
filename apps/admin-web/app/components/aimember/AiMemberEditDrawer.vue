<template>
    <!--
        AI 회원 계정 서랍 — 고치기(`mode="edit"`, `/ai-members?account=ID`, 목록의 줄을 누르면 연다)와 만들기(`mode="create"`, 목록의 [AI 계정 만들기])를 같이 한다.
        열리는 쪽은 화면 설정의 「서랍 위치」(`prefs.drawerPosition`)를 따른다(소모임·회원 상세 서랍과 같다).
        구획 여섯: ⓪ 계정(만들기면 닉네임 · 아바타 고르기 `AiAvatarPicker`) ① 제공사 · 모델(모델은 서버 options — 모델 목록의 「사용」인 모델만, 가격·기본 같이) ② 페르소나(성별·나이대·성격·말투·관심사·공개 소개)
          ③ 관계(배우자/연인 — 상대 AI 계정) ④ 활동량 · 시간대(오전·오후 창, 하루 글 2~3) ⑤ 쓸 수 있는 곳(게시판 고르기 — 항상 막힌 게시판은 비활성 + 이유, AI 전용 소모임).
        고치기는 서랍 하나가 저장 하나다 — 저장 줄은 서랍 맨 아래(`StickySaveBar`). 만들기는 아래 [취소]·[만들기]. 저장하지 않은 채 닫으면 묻는다.
        아바타는 바꿨을 때만 `avatarId` 를 싣는다(null 이면 서버가 그대로 둔다).
        머리(닉네임·상태)는 목록 줄(`summary`)로 먼저 그리고, 상세·고르기 목록은 부르는 쪽이 받아 내려 준다(서버를 부르지 않는다 — components.md).
        서버 칸 오류(`serverErrors`)는 그 칸 아래에, 칸에 붙지 않는 것은 저장 줄 안에.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '46rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="creating" class="flex min-w-0 flex-1 items-center gap-2">
                <i class="pi pi-user-plus text-muted-color" aria-hidden="true" />
                <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ t('aiMember.createTitle') }}</h2>
            </div>
            <div v-else-if="head" class="flex min-w-0 flex-1 items-center gap-2">
                <PointShopAvatarThumb v-if="head.avatarId" :avatar-id="head.avatarId" size="sm" />
                <UserAvatar v-else :name="head.member.nickname" />
                <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ head.member.nickname }}</h2>
                <Tag :value="UtilAiMemberMessages.status(t, head.restingReason, head.provider)" :severity="UtilAiMember.restingSeverity(head.restingReason)" class="ai-member-tag text-[11px]!" />
            </div>
        </template>

        <DrawerSummary v-if="!creating && head" :label="t('drawerSummary.label')" :items="summaryItems">
            <template #item-model>
                <AiModelTag :provider="head.provider" :model="head.model" />
            </template>
            <template #note>{{ t('aiMember.drawerNote') }}</template>
        </DrawerSummary>

        <ErrorState v-if="error && !creating":title="t('aiMember.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="(!creating && !detail) || !options || !form" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="40%" height="1.25rem" />
            <Skeleton v-for="n in 8" :key="n" height="1rem" :width="n % 2 ? '85%' : '65%'" />
        </div>

        <form v-else class="flex min-w-0 flex-col gap-6" novalidate @submit.prevent="submit">
            <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                <i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.readOnly') }}
            </p>

            <p v-if="creating" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.createHelp') }}</p>

            <fieldset :disabled="!canManage || saving" class="m-0 flex min-w-0 flex-col gap-6 border-0 p-0">
                <!-- ⓪ 계정 — 닉네임(만들기만) · 아바타 -->
                <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-account`">
                    <h3 :id="`${titleId}-account`" class="text-sm font-semibold">{{ t('aiMember.sec.account') }}</h3>
                    <LabeledField v-if="creating" :label="t('aiMember.field.nickname')" :hint="t('aiMember.nicknameHint', { min: UtilValidation.NICKNAME_MIN, max: UtilValidation.NICKNAME_MAX })" :help="t('aiMember.help.nickname')" :error="nicknameError">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.nickname" class="max-w-xs" fluid :maxlength="UtilValidation.NICKNAME_MAX" autocomplete="off" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('aiMember.field.avatar')" :help="t('aiMember.help.avatar')" :error="fieldError('avatarId')">
                        <template #default="{ describedBy }">
                            <AiAvatarPicker v-model="form.avatarId" :choices="avatars" :loading="avatarsLoading" :unavailable="avatarsUnavailable" :disabled="!canManage || saving" :described-by="describedBy" @open="emit('load-avatars')" />
                        </template>
                    </LabeledField>
                </section>

                <!-- ① 제공사 · 모델 -->
                <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-model`">
                    <h3 :id="`${titleId}-model`" class="text-sm font-semibold">{{ t('aiMember.sec.model') }}</h3>
                    <LabeledField :label="t('aiMember.field.provider')" :help="t('aiMember.help.provider')" :error="fieldError('provider')">
                        <template #default="{ describedBy }">
                            <SelectButton :model-value="form.provider" :options="providerOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-describedby="describedBy" :aria-label="t('aiMember.field.provider')" @update:model-value="setProvider" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('aiMember.field.model')" :help="t('aiMember.help.model')" :error="fieldError('model')">
                        <template #default="{ id, describedBy, invalid }">
                            <Select
                                v-model="form.model"
                                :input-id="id"
                                :options="modelOptions"
                                option-label="label"
                                option-value="value"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                :placeholder="modelOptions.length ? undefined : t('aiMember.noModels')"
                                class="max-w-md"
                                fluid
                            />
                        </template>
                    </LabeledField>
                    <!-- 지금 모델이 모델 목록에서 꺼졌거나 빠졌다(쉬는 이유 MODEL_UNAVAILABLE) — 다시 골라야 돈다 -->
                    <p v-if="modelMissing" class="m-0 flex gap-1.5 text-xs text-amber-800 dark:text-amber-300"><i class="pi pi-exclamation-triangle mt-0.5 text-[10px]" aria-hidden="true" />{{ t('aiMember.modelMissing', { model: initial?.model ?? '' }) }}</p>
                    <!-- 모델 이력(읽기 전용) — 접어 두고 버튼으로 펼친다. 한 줄뿐이면(지금 모델만) 버튼을 숨긴다 -->
                    <div v-if="!creating && history.length > 1" class="flex flex-col gap-2">
                        <Button type="button" :icon="historyOpen ? 'pi pi-chevron-up' : 'pi pi-history'" :label="t('aiMember.modelHistory', { n: history.length })" size="small" severity="secondary" variant="text" class="self-start" :aria-expanded="historyOpen" :aria-controls="historyId" @click="historyOpen = !historyOpen" />
                        <ol v-show="historyOpen" :id="historyId" class="m-0 flex list-none flex-col gap-1 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                            <li v-for="(row, i) in history" :key="`${row.since}-${row.model}`" class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                                <span class="tabular-nums text-muted-color">{{ i === 0 ? t('aiMember.historySince', { from: time.date(row.since) }) : t('aiMember.historyRange', { from: time.date(row.since), to: time.date(history[i - 1]!.since) }) }}</span>
                                <span aria-hidden="true" class="text-muted-color">·</span>
                                <AiModelTag :provider="row.provider" :model="row.model" />
                                <Tag v-if="i === 0" :value="t('aiMember.historyNow')" severity="success" class="ai-member-tag text-[11px]!" />
                                <span class="text-xs text-muted-color">{{ row.changedBy ? t('aiMember.historyBy', { name: row.changedBy.operatorName }) : t('aiMember.historyBySystem') }}</span>
                            </li>
                        </ol>
                    </div>
                </section>

                <!-- ② 페르소나 -->
                <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-persona`">
                    <h3 :id="`${titleId}-persona`" class="text-sm font-semibold">{{ t('aiMember.sec.persona') }}</h3>
                    <div class="grid gap-3 sm:grid-cols-2">
                        <LabeledField :label="t('aiMember.field.gender')">
                            <template #default="{ id }">
                                <Select v-model="form.persona.gender" :input-id="id" :options="genderOptions" option-label="label" option-value="value" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('aiMember.field.ageBand')">
                            <template #default="{ id }">
                                <Select v-model="form.persona.ageBand" :input-id="id" :options="ageOptions" option-label="label" option-value="value" fluid />
                            </template>
                        </LabeledField>
                    </div>
                    <LabeledField :label="t('aiMember.field.personality')" :hint="t('aiMember.hintMax', { max: UtilAiMember.PERSONALITY_MAX })" :help="t('aiMember.help.personality')" :error="fieldError('persona.personality')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.persona.personality" rows="2" auto-resize fluid :maxlength="UtilAiMember.PERSONALITY_MAX" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('aiMember.field.tone')" :hint="t('aiMember.hintMax', { max: UtilAiMember.TONE_MAX })" :error="fieldError('persona.tone')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.persona.tone" fluid :maxlength="UtilAiMember.TONE_MAX" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('aiMember.field.interests')" :hint="t('aiMember.hintMax', { max: UtilAiMember.INTERESTS_MAX })" :help="t('aiMember.help.interests')" :error="fieldError('persona.interests')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.persona.interests" rows="3" auto-resize fluid :maxlength="UtilAiMember.INTERESTS_MAX" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('aiMember.field.bio')" :hint="t('aiMember.hintMax', { max: UtilAiMember.BIO_MAX })" :help="t('aiMember.help.bio')" :error="fieldError('bio')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.bio" rows="2" auto-resize fluid :maxlength="UtilAiMember.BIO_MAX" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </section>

                <!-- ③ 관계 -->
                <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-relation`">
                    <h3 :id="`${titleId}-relation`" class="text-sm font-semibold">{{ t('aiMember.sec.relation') }}</h3>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.help.relation') }}</p>
                    <div class="grid gap-3 sm:grid-cols-2">
                        <LabeledField :label="t('aiMember.field.relationKind')">
                            <template #default="{ id }">
                                <Select :model-value="relationKind" :input-id="id" :options="relationOptions" option-label="label" option-value="value" fluid @update:model-value="setRelationKind" />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('aiMember.field.partner')" :error="fieldError('relation')">
                            <template #default="{ id, describedBy, invalid }">
                                <Select
                                    :model-value="form.relation?.partnerAiMemberId ?? null"
                                    :input-id="id"
                                    :options="partnerOptions"
                                    option-label="label"
                                    option-value="value"
                                    :placeholder="t('aiMember.partnerPlaceholder')"
                                    :disabled="!form.relation"
                                    :invalid="invalid"
                                    :aria-describedby="describedBy"
                                    fluid
                                    @update:model-value="setPartner"
                                />
                            </template>
                        </LabeledField>
                    </div>
                </section>

                <!-- ④ 활동량 · 시간대 -->
                <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-activity`">
                    <h3 :id="`${titleId}-activity`" class="text-sm font-semibold">{{ t('aiMember.sec.activity') }}</h3>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.help.windows') }}</p>
                    <div class="grid gap-3 sm:grid-cols-2">
                        <LabeledField v-for="slot in WINDOW_SLOTS" :key="slot" :label="t(`aiMember.field.${slot}`)" :error="windowError(slot)">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex items-center gap-2">
                                    <InputText :id="id" v-model="form.windows[slot].start" type="time" class="w-32" :invalid="invalid" :aria-describedby="describedBy" />
                                    <span class="text-muted-color" aria-hidden="true">{{ t('aiMember.windowTo') }}</span>
                                    <InputText v-model="form.windows[slot].end" type="time" class="w-32" :invalid="invalid" :aria-label="`${t(`aiMember.field.${slot}`)} ${t('aiMember.windowTo')}`" :aria-describedby="describedBy" />
                                </div>
                            </template>
                        </LabeledField>
                    </div>
                    <LabeledField :label="t('aiMember.field.dailyPosts')" :help="t('aiMember.help.dailyPosts')" :error="fieldError('dailyPosts')">
                        <template #default="{ describedBy }">
                            <SelectButton v-model="form.dailyPosts" :options="dailyOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('aiMember.field.dailyPosts')" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </section>

                <!-- ⑤ 쓸 수 있는 곳 -->
                <section class="flex flex-col gap-3" :aria-labelledby="`${titleId}-places`">
                    <h3 :id="`${titleId}-places`" class="text-sm font-semibold">{{ t('aiMember.sec.places') }}</h3>
                    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                        <legend class="mb-1 text-sm font-medium">{{ t('aiMember.field.boards') }}</legend>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.help.boards') }}</p>
                        <div class="grid gap-x-4 gap-y-2 sm:grid-cols-3">
                            <label v-for="board in openBoards" :key="board.slug" class="flex items-center gap-2 text-sm">
                                <Checkbox v-model="form.boards" :value="board.slug" />
                                <span>{{ board.name }}</span>
                            </label>
                        </div>
                        <p v-if="fieldError('boards')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('boards') }}</p>
                        <!-- 항상 막힌 게시판 — 고를 수 없게 비활성 + 이유(정책 7) -->
                        <div class="mt-1 rounded-lg border border-dashed border-(--line) px-3 py-2">
                            <p class="m-0 mb-1.5 text-xs font-medium text-muted-color">{{ t('aiMember.blockedTitle') }}</p>
                            <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                                <li v-for="board in blockedBoards" :key="board.slug" class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm text-muted-color">
                                    <Checkbox :model-value="false" binary disabled :aria-label="board.name" />
                                    <span>{{ board.name }}</span>
                                    <span class="text-xs"><i class="pi pi-ban mr-1 text-[10px]" aria-hidden="true" />{{ t(`aiMember.blockedReason.${board.blockedReason}`) }}</span>
                                </li>
                            </ul>
                        </div>
                    </fieldset>
                    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                        <legend class="mb-1 text-sm font-medium">{{ t('aiMember.field.groups') }}</legend>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.help.groups') }}</p>
                        <p v-if="options.groups.length === 0" class="m-0 text-sm text-muted-color">{{ t('aiMember.noGroups') }}</p>
                        <div v-else class="flex flex-wrap gap-x-4 gap-y-2">
                            <label v-for="group in options.groups" :key="group.id" class="flex items-center gap-2 text-sm">
                                <Checkbox v-model="form.groups" :value="group.id" />
                                <span>{{ group.name }}</span>
                            </label>
                        </div>
                    </fieldset>
                </section>
            </fieldset>

            <p v-if="!creating && detail?.updatedAt" class="m-0 text-xs text-muted-color tabular-nums">{{ t('aiMember.updatedBy', { at: time.full(detail.updatedAt), name: detail.updatedBy ?? '—' }) }}</p>

            <div v-if="creating && canManage" class="flex flex-col gap-2 border-t border-(--line) pt-3">
                <Message v-if="formError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ formError }}</Message>
                <div class="flex flex-wrap justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="saving" @click="open = false" />
                    <Button type="submit" icon="pi pi-check" :label="t('aiMember.createSubmit')" size="small" :loading="saving" :disabled="saving || hasWindowProblem" />
                </div>
            </div>
            <StickySaveBar v-else-if="canManage":dirty="changes > 0" :count="changes" :saving="saving" :disabled="hasWindowProblem" :error-count="errorCount" :error="formError" revertable submit sticky-on-mobile @revert="reset" />
        </form>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AiAvatarChoice } from '~/components/aimember/AiAvatarPicker.types';
import {
    AI_AGE_BANDS,
    AI_GENDERS,
    AI_PROVIDERS,
    AI_RELATION_KINDS,
    type AiMemberCreateRequest,
    type AiMemberDetail,
    type AiMemberFormOptions,
    type AiMemberRow,
    type AiMemberSaveRequest,
    type AiProvider,
    type AiRelationKind,
} from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAiMember from '~/utils/util-ai-member';
import UtilAiMemberMessages from '~/utils/util-ai-member-messages';
import UtilProfileMessages from '~/utils/util-profile-messages';
import UtilValidation from '~/utils/util-validation';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 고치기(기본) · 만들기 — 만들기는 `detail` 없이 `options` 로 빈 칸을 만든다 */
    mode?: 'edit' | 'create';
    /** 목록 줄 — 상세가 오기 전 머리 */
    summary?: AiMemberRow | null;
    detail?: AiMemberDetail | null;
    options?: AiMemberFormOptions | null;
    loading?: boolean;
    error?: ApiError | null;
    saving?: boolean;
    /** 서버가 거절한 칸(`fields`) — 새 객체가 올 때마다 칸 아래에 보인다 */
    serverErrors?: Record<string, string>;
    formError?: string;
    canManage?: boolean;
    /** 아바타 고를 거리 — 처음 고르기를 열 때(`load-avatars`) 부르는 쪽이 받는다 */
    avatars?: AiAvatarChoice[] | null;
    avatarsLoading?: boolean;
    /** 아바타 목록을 받을 수 없다(포인트 상점 조회 권한 없음 등) */
    avatarsUnavailable?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    mode: 'edit',
    summary: null,
    detail: null,
    options: null,
    loading: false,
    error: null,
    saving: false,
    serverErrors: () => ({}),
    formError: '',
    canManage: false,
    avatars: null,
    avatarsLoading: false,
    avatarsUnavailable: false,
});

const emit = defineEmits<{ save: [body: AiMemberSaveRequest]; create: [body: AiMemberCreateRequest]; retry: []; 'dirty-change': [dirty: boolean]; 'load-avatars': [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const WINDOW_SLOTS = ['morning', 'afternoon'] as const;

const creating = computed(() => props.mode === 'create');
const head = computed(() => props.detail ?? props.summary);

/** 편집 칸 — 고치기는 상세가 (다시) 오면 그 값으로, 만들기는 고르기 목록이 오면 빈 칸으로 한 번 채운다 */
const form = ref<AiMemberCreateRequest | null>(null);
const initial = ref<AiMemberCreateRequest | null>(null);
/** 지금 칸이 어느 쪽 값으로 만들어졌는가 — 만들기 칸은 고르기 목록을 다시 받아도 지우지 않는다 */
let formFor: 'edit' | 'create' | null = null;
const clone = (v: AiMemberCreateRequest) => JSON.parse(JSON.stringify(v)) as AiMemberCreateRequest;
// 서버 값이 바뀌면(열기·저장 뒤) 편집 칸을 그 값으로 다시 만든다 — 늦게 도착하는 데이터로 만드는 폼(forms.md 2-1)
watch(
    () => (props.mode === 'create' ? props.options : props.detail),
    () => {
        if (props.mode === 'create') {
            if ((formFor === 'create' && form.value) || !props.options) return;
            formFor = 'create';
            initial.value = UtilAiMember.blankForm(props.options);
            form.value = clone(initial.value);
            return;
        }
        formFor = 'edit';
        initial.value = props.detail ? UtilAiMember.formOf(props.detail) : null;
        form.value = initial.value ? clone(initial.value) : null;
    },
    { immediate: true },
);

function reset(): void {
    if (initial.value) form.value = clone(initial.value);
}

/** 모델 이력 — 접어 두고 버튼으로 펼친다 */
const historyOpen = ref(false);
const historyId = useId();
const history = computed(() => props.detail?.modelHistory ?? []);

const changes = computed(() => (form.value && initial.value ? UtilAiMember.changes(form.value, initial.value) : 0));
watch(
    () => changes.value > 0,
    (dirty) => emit('dirty-change', dirty),
);

const windowProblems = computed(() => (form.value ? UtilAiMember.windowProblems(form.value.windows) : { morning: false, afternoon: false }));
const hasWindowProblem = computed(() => windowProblems.value.morning || windowProblems.value.afternoon);

function fieldError(key: string): string {
    return props.serverErrors[key] ?? '';
}

/** 만들기 — [만들기]를 누른 뒤에만 화면이 미리 본 닉네임 문제를 보인다(서버 문구가 오면 그것) */
const triedCreate = ref(false);
const nicknameProblem = computed(() => (form.value ? UtilValidation.nickname(form.value.nickname.trim()) : null));
const nicknameError = computed(() => fieldError('nickname') || (triedCreate.value && nicknameProblem.value ? UtilProfileMessages.nicknameProblem(t, nicknameProblem.value) : ''));

function windowError(slot: 'morning' | 'afternoon'): string {
    return fieldError(`windows.${slot}`) || (windowProblems.value[slot] ? t('aiMember.windowInvalid') : '');
}

const errorCount = computed(() => Object.keys(props.serverErrors).length + (windowProblems.value.morning ? 1 : 0) + (windowProblems.value.afternoon ? 1 : 0));

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const h = head.value;
    if (!h) return [];
    return [
        { key: 'model', label: t('aiMember.col.model') },
        { key: 'relation', label: t('aiMember.col.relation'), value: UtilAiMemberMessages.relation(t, h.relation) },
        { key: 'today', label: t('aiMember.col.today'), value: t('aiMember.todayLine', { posts: h.today.posts, comments: h.today.comments }) },
    ];
});

/* ── 고르기 목록 ── */

const providerOptions = computed(() => AI_PROVIDERS.map((value) => ({ value, label: t(`aiMember.provider.${value}`) })));
/** 고를 수 있는 모델 — 모델 목록에서 「사용」인 것만(가격·무료·기본을 같이 보인다) */
const modelOptions = computed(() =>
    (form.value && props.options ? (props.options.models[form.value.provider] ?? []) : []).map((m) => ({ value: m.modelId, label: UtilAiMemberMessages.modelOption(t, m) })),
);
const genderOptions = computed(() => AI_GENDERS.map((value) => ({ value, label: t(`aiMember.gender.${value}`) })));
const ageOptions = computed(() => AI_AGE_BANDS.map((value) => ({ value, label: t(`aiMember.ageBand.${value}`) })));
const dailyOptions = computed(() => UtilAiMember.DAILY_POSTS.map((value) => ({ value, label: t('aiMember.dailyLine', { n: value }) })));
const relationOptions = computed(() => [{ value: 'NONE', label: t('aiMember.relationNone') }, ...AI_RELATION_KINDS.map((value) => ({ value, label: t(`aiMember.relationKind.${value}`) }))]);
const partnerOptions = computed(() => (props.options?.accounts ?? []).filter((a) => a.aiMemberId !== props.detail?.id).map((a) => ({ value: a.aiMemberId, label: a.nickname })));
const openBoards = computed(() => (props.options?.boards ?? []).filter((b) => !b.blockedReason));
const blockedBoards = computed(() => (props.options?.boards ?? []).filter((b) => b.blockedReason));
const relationKind = computed(() => form.value?.relation?.kind ?? 'NONE');
/** 지금 모델이 고르기 목록(모델 목록의 「사용」)에 없다 — 고치기에서 처음 값 그대로일 때만 */
const modelMissing = computed(
    () => !creating.value && Boolean(form.value && initial.value && form.value.model === initial.value.model && !modelOptions.value.some((o) => o.value === form.value?.model)),
);

/** 제공사를 바꾸면 모델은 그 제공사의 기본 모델로 */
function setProvider(provider: AiProvider): void {
    if (!form.value || provider === form.value.provider) return;
    form.value.provider = provider;
    form.value.model = UtilAiMember.defaultModel(props.options, provider);
}

function setRelationKind(kind: AiRelationKind | 'NONE'): void {
    if (!form.value) return;
    if (kind === 'NONE') form.value.relation = null;
    else form.value.relation = { kind, partnerAiMemberId: form.value.relation?.partnerAiMemberId ?? partnerOptions.value[0]?.value ?? '' };
}

function setPartner(partnerAiMemberId: string): void {
    if (form.value?.relation) form.value.relation = { ...form.value.relation, partnerAiMemberId };
}

function submit(): void {
    if (!form.value || !props.canManage || props.saving || hasWindowProblem.value) return;
    const f = form.value;
    if (creating.value) {
        triedCreate.value = true;
        if (nicknameProblem.value) return;
    } else if (changes.value === 0) {
        return;
    }
    // 서버에 넘기는 본문은 반응형이 아닌 평범한 객체로(_frontend-core 2-3)
    const body: AiMemberSaveRequest = {
        provider: f.provider,
        model: f.model,
        persona: { ...toRaw(f.persona) },
        bio: f.bio.trim(),
        relation: f.relation ? { ...toRaw(f.relation) } : null,
        windows: { morning: { ...toRaw(f.windows.morning) }, afternoon: { ...toRaw(f.windows.afternoon) } },
        dailyPosts: f.dailyPosts,
        boards: f.boards.map((slug) => slug),
        groups: f.groups.map((id) => id),
        // 아바타는 바꿨을 때만 — null 이면 서버가 그대로 둔다
        avatarId: f.avatarId !== initial.value?.avatarId ? f.avatarId : null,
    };
    if (creating.value) emit('create', { ...body, nickname: f.nickname.trim() });
    else emit('save', body);
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.ai-member-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
