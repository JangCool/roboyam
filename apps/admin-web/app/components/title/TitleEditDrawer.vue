<template>
    <!--
        칭호 만들기·고치기 서랍(오른쪽, member-title-contract 「관리자 칭호 만들기·고치기 서랍」) — 칭호 관리 표의 [칭호 만들기] 또는 줄의 [고치기]로 연다.
        칸: 키(만들 때만) · 이름(최대 6자 — 등급 이름과 겹치면 서버가 거절) · 영문 이름 · 그림(시안의 10종) · 칩 색 · 받는 방법 · 심사하는 사람(신청형 — 운영자만 / 운영자 + 연결 게시판 매니저) ·
        조건(등급 이상 · 글 수 · 인기글 진입 · 출석 · 신고 숨김 없음 — 모두 채우면 받는다) · 단계(없음 / 동·은·금 문턱) · 혜택 · 휴면 · 켜짐 · 차례. 오른쪽(넓은 화면)은 칩·도감 크기 미리보기.
        키는 서버가 정한다(고칠 때 읽기 전용으로만 보인다). 기본 칭호는 받는 방법을 바꿀 수 없다(서버 409 `TITLE_BUILTIN_LOCKED`). 자동 칭호는 「저장 뒤 기존 활동으로 소급 지급」(`retroactive`, 기본 켬).
        검사는 `UtilTitle.problems`(서버와 같은 규칙 — 판정은 서버). 빈 칸은 [저장]을 한 번 누른 뒤에 탓한다. 서버가 거절한 칸(`400 fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 아래 한 줄.
        권한(`title.manage`)이 없으면 칸이 모두 읽기 전용이고 저장 단추 대신 안내. 서버를 부르지 않는다 — 값은 `save` 로 올리고 저장·토스트·닫기는 부모 몫이다.
    -->
    <Drawer :visible="open" position="right" :aria-labelledby="titleId" :style="{ width: '56rem', maxWidth: '100vw' }" @update:visible="onVisible">
        <template #header>
            <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ creating ? t('memberTitle.drawer.createTitle') : t('memberTitle.drawer.editTitle', { name: title?.name ?? '' }) }}</h2>
        </template>

        <form :id="formId" class="grid min-w-0 gap-6 pb-4 lg:grid-cols-[minmax(0,1fr)_16rem]" novalidate @submit.prevent="submit">
            <div class="flex min-w-0 flex-col gap-5">
                <!-- 이름 -->
                <section class="grid gap-3 sm:grid-cols-2">
                    <!-- 키는 서버가 정한다 — 고칠 때만 읽기 전용으로 보인다 -->
                    <LabeledField v-if="title" :label="t('memberTitle.drawer.key')" :help="t('memberTitle.drawer.keyFixed')">
                        <template #default="{ id, describedBy }">
                            <InputText :id="id" :model-value="title.key" readonly class="w-full font-mono" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('memberTitle.drawer.name')" :hint="t('memberTitle.drawer.nameHint', { n: nameLength, max: UtilTitle.NAME_MAX })" :error="errorOf('name')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.name" :readonly="readonly" maxlength="12" class="w-full" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('memberTitle.drawer.nameEn')" :help="t('memberTitle.drawer.nameEnHint')" :error="errorOf('nameEn')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" :model-value="form.nameEn ?? ''" :readonly="readonly" maxlength="30" class="w-full" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" @update:model-value="(v) => (form.nameEn = v || null)" />
                        </template>
                    </LabeledField>
                </section>

                <!-- 그림 -->
                <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-icon`">
                    <h3 :id="`${titleId}-icon`" class="text-sm font-semibold">{{ t('memberTitle.drawer.icon') }}</h3>
                    <div role="radiogroup" :aria-labelledby="`${titleId}-icon`" class="grid grid-cols-5 gap-2 sm:grid-cols-10">
                        <label
                            v-for="icon in UtilTitleIcon.ICONS"
                            :key="icon"
                            class="flex cursor-pointer flex-col items-center gap-1 rounded-lg border p-2"
                            :class="[form.icon === icon ? 'border-(--p-primary-color) bg-primary-50 dark:bg-primary-950' : 'border-(--line)', readonly ? 'cursor-not-allowed opacity-60' : '']"
                        >
                            <RadioButton v-model="form.icon" :value="icon" :name="`${formId}-icon`" :disabled="readonly" class="sr-only" />
                            <TitleIcon :icon="icon" :tier="UtilTitleIcon.isTiered(icon) ? 'GOLD' : null" :scale="3" />
                            <span class="text-[10px] text-muted-color">{{ t(`memberTitle.icon.${icon}`) }}</span>
                        </label>
                    </div>
                </section>

                <!-- 칩 색 · 받는 방법 -->
                <section class="grid gap-3 sm:grid-cols-2">
                    <div class="flex flex-col gap-2">
                        <span :id="`${titleId}-chip`" class="text-sm font-semibold">{{ t('memberTitle.drawer.chip') }}</span>
                        <SelectButton v-model="form.chip" :options="chipOptions" option-label="label" option-value="value" :allow-empty="false" :disabled="readonly" size="small" :aria-labelledby="`${titleId}-chip`" />
                    </div>
                    <div class="flex flex-col gap-2">
                        <span :id="`${titleId}-method`" class="text-sm font-semibold">{{ t('memberTitle.drawer.method') }}</span>
                        <SelectButton :model-value="form.method" :options="methodOptions" option-label="label" option-value="value" :allow-empty="false" :disabled="readonly || builtin" size="small" :aria-labelledby="`${titleId}-method`" @update:model-value="pickMethod" />
                        <span v-if="builtin" class="text-[11px] text-muted-color">{{ t('memberTitle.drawer.builtinMethod') }}</span>
                    </div>
                </section>

                <!-- 심사하는 사람(신청형) -->
                <section v-if="form.method === 'APPLY'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-reviewer`">
                    <h3 :id="`${titleId}-reviewer`" class="text-sm font-semibold">{{ t('memberTitle.drawer.reviewer') }}</h3>
                    <div role="radiogroup" :aria-labelledby="`${titleId}-reviewer`" class="flex flex-wrap gap-4">
                        <label v-for="r in REVIEWERS" :key="r" class="flex items-center gap-2 text-sm">
                            <RadioButton v-model="form.reviewer" :value="r" :name="`${formId}-reviewer`" :disabled="readonly" />{{ t(`memberTitle.reviewer.${r}`) }}
                        </label>
                    </div>
                    <LabeledField v-if="form.reviewer === 'OPERATOR_AND_BOARD_MANAGER'" :label="t('memberTitle.drawer.board')" :help="t('memberTitle.drawer.boardHint')" :error="errorOf('boardId')">
                        <template #default="{ id, describedBy, invalid }">
                            <Select :input-id="id" v-model="form.boardId" :options="boards" option-label="name" option-value="id" :placeholder="t('memberTitle.drawer.boardPick')" :disabled="readonly" filter class="w-full max-w-sm" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </section>

                <!-- 조건 -->
                <section v-if="form.method !== 'AWARD'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-cond`">
                    <h3 :id="`${titleId}-cond`" class="text-sm font-semibold">{{ t('memberTitle.drawer.conditions') }}</h3>
                    <p class="m-0 text-xs text-muted-color">{{ t('memberTitle.drawer.conditionsHint') }}</p>
                    <ul v-if="form.conditions.length" class="m-0 flex list-none flex-col gap-2 p-0">
                        <li v-for="(c, i) in form.conditions" :key="i" class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) px-3 py-2">
                            <span class="min-w-28 text-[13px] font-medium">{{ t(`memberTitle.condType.${c.type}`) }}</span>
                            <Select v-if="c.type === 'GRADE_AT_LEAST'" v-model="c.gradeKey" :options="grades" option-label="label" option-value="key" :placeholder="t('memberTitle.drawer.gradePick')" :disabled="readonly" size="small" class="w-40" :aria-label="t('memberTitle.condType.GRADE_AT_LEAST')" />
                            <Select v-if="c.type === 'POSTS'" v-model="c.boardId" :options="boardOptions" option-label="name" option-value="id" :disabled="readonly" size="small" filter class="w-48" :aria-label="t('memberTitle.drawer.board')" />
                            <InputNumber
                                v-if="UtilTitle.counted(c.type)"
                                v-model="c.value"
                                :min="1"
                                :max="100000"
                                :disabled="readonly"
                                size="small"
                                input-class="w-20"
                                :invalid="Boolean(errorOf(`conditions[${i}].value`))"
                                :aria-label="t('memberTitle.drawer.conditionValue')"
                            />
                            <span v-if="UtilTitle.counted(c.type)" class="text-xs text-muted-color">{{ t(`memberTitle.condUnit.${c.type}`) }}</span>
                            <Button type="button" icon="pi pi-times" severity="secondary" variant="text" size="small" rounded :disabled="readonly" class="ml-auto" :aria-label="t('memberTitle.drawer.conditionRemove')" @click="form.conditions.splice(i, 1)" />
                            <small v-if="errorOf(`conditions[${i}].value`)" class="basis-full text-xs text-red-600 dark:text-red-400">{{ errorOf(`conditions[${i}].value`) }}</small>
                        </li>
                    </ul>
                    <Select :model-value="null" :options="addableConditions" option-label="label" option-value="value" :placeholder="t('memberTitle.drawer.conditionAdd')" :disabled="readonly || addableConditions.length === 0" size="small" class="w-56" @update:model-value="addCondition" />
                </section>

                <!-- 단계 -->
                <section v-if="form.method !== 'AWARD'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-tier`">
                    <h3 :id="`${titleId}-tier`" class="text-sm font-semibold">{{ t('memberTitle.drawer.tiers') }}</h3>
                    <div class="flex flex-wrap items-center gap-3">
                        <Select :model-value="form.tierMetric" :options="metricOptions" option-label="label" option-value="value" :disabled="readonly" size="small" class="w-56" :aria-labelledby="`${titleId}-tier`" @update:model-value="pickMetric" />
                        <template v-if="form.tierMetric">
                            <label v-for="tier in TIER_FIELDS" :key="tier.field" class="flex items-center gap-1.5 text-[13px]">
                                <TitleIcon :icon="form.icon" :tier="tier.tier" :scale="2" />
                                <span>{{ t(`memberTitle.tier.${tier.tier}`) }}</span>
                                <InputNumber v-model="form[tier.field]" :min="1" :max="100000" :disabled="readonly" size="small" input-class="w-20" :invalid="Boolean(errorOf('tiers'))" />
                            </label>
                        </template>
                    </div>
                    <small v-if="errorOf('tiers')" class="text-xs text-red-600 dark:text-red-400">{{ errorOf('tiers') }}</small>
                </section>

                <!-- 혜택 · 휴면 · 켜짐 · 차례 -->
                <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-benefit`">
                    <h3 :id="`${titleId}-benefit`" class="text-sm font-semibold">{{ t('memberTitle.drawer.benefits') }}</h3>
                    <label v-for="b in BENEFITS" :key="b" class="flex items-center gap-2 text-sm">
                        <Checkbox v-model="form.benefits[b]" binary :disabled="readonly" />{{ t(`memberTitle.benefit.${b}`) }}
                    </label>
                </section>
                <section class="flex flex-col gap-2">
                    <label class="flex items-start gap-2 text-sm">
                        <Checkbox v-model="form.dormantHide" binary :disabled="readonly" class="mt-0.5" />
                        <span class="flex flex-col gap-0.5">{{ t('memberTitle.drawer.dormant') }}<span class="text-[11px] text-muted-color">{{ t('memberTitle.drawer.dormantHint') }}</span></span>
                    </label>
                    <label class="flex items-center gap-2 text-sm"><ToggleSwitch v-model="form.enabled" :disabled="readonly" />{{ t('memberTitle.drawer.enabled') }}</label>
                    <label class="flex items-center gap-2 text-sm">
                        {{ t('memberTitle.drawer.sortOrder') }}
                        <InputNumber v-model="form.sortOrder" :min="1" :max="999" :disabled="readonly" size="small" input-class="w-16" />
                    </label>
                </section>

                <!-- 소급 지급(자동 칭호만) — 저장 뒤 그 칭호만 자동 판정을 한 번 돌린다 -->
                <label v-if="form.method === 'AUTO'" class="flex items-start gap-2 text-sm">
                    <Checkbox v-model="form.retroactive" binary :disabled="readonly" class="mt-0.5" />
                    <span class="flex flex-col gap-0.5">{{ t('memberTitle.drawer.retroactive') }}<span class="text-[11px] text-muted-color">{{ t('memberTitle.drawer.retroNotice') }}</span></span>
                </label>
                <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
            </div>

            <!-- 미리보기 -->
            <aside class="flex flex-col gap-3 rounded-xl border border-(--line) p-4 lg:sticky lg:top-0 lg:self-start" :aria-label="t('memberTitle.drawer.preview')">
                <span class="text-xs font-semibold text-muted-color">{{ t('memberTitle.drawer.preview') }}</span>
                <div class="flex justify-center py-2"><TitleIcon :icon="form.icon" :tier="form.tierMetric ? 'GOLD' : null" :label="form.name || t('memberTitle.drawer.name')" :scale="4" /></div>
                <TitleChip :title="previewLook" :scale="1" />
                <TitleChip :title="previewLook" :scale="2" />
            </aside>
        </form>

        <template #footer>
            <div class="flex w-full flex-wrap items-center justify-end gap-2">
                <span v-if="readonly" class="mr-auto text-xs text-muted-color">{{ t('memberTitle.readonlyNotice') }}</span>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="onVisible(false)" />
                <Button v-if="!readonly" type="submit" :form="formId" :label="t('common.save')" icon="pi pi-check" :loading="saving" data-testid="title-drawer-save" />
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import UtilTitleIcon from '#layers/member-title/utils/util-title-icon';
import type { AdminTitle, AdminTitleRequest, TitleConditionType, TitleLook, TitleMethod, TitleMetric, TitleReviewer } from '~/composables/title/api/useTitleApi.types';
import UtilTitle, { type TitleForm } from '~/utils/util-title';
import UtilTitleMessages from '~/utils/util-title-messages';

/** 서랍이 열려 있는가 */
const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 고칠 칭호 — null 이면 새로 만든다 */
    title: AdminTitle | null;
    /** 등급 이름 목록(조건 「등급 이상」) */
    grades?: { key: string; label: string }[];
    /** 게시판 목록(연결 게시판 · 조건 「글 수」) */
    boards?: { id: string; name: string }[];
    /** 새 칭호의 차례(맨 뒤) */
    nextSortOrder?: number;
    canManage?: boolean;
    saving?: boolean;
    /** 칸에 붙지 않는 서버 오류 */
    error?: string;
    /** 서버가 거절한 칸(`fields`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { grades: () => [], boards: () => [], nextSortOrder: 1, canManage: false, saving: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ save: [body: AdminTitleRequest, key: string | null] }>();

const REVIEWERS: readonly TitleReviewer[] = ['OPERATOR', 'OPERATOR_AND_BOARD_MANAGER'];
const BENEFITS = ['trialPriority', 'magazineCandidate', 'avatarFrame'] as const;
const TIER_FIELDS = [
    { field: 'tierBronze', tier: 'BRONZE' },
    { field: 'tierSilver', tier: 'SILVER' },
    { field: 'tierGold', tier: 'GOLD' },
] as const;
const METRICS: readonly TitleMetric[] = ['POPULAR_DAYS', 'ATTENDANCE_DAYS', 'POSTS', 'TRIAL_KEPT'];

const { t } = useI18n();
const { confirm } = useConfirmDialog();
const titleId = useId();
const formId = useId();

const creating = computed(() => props.title === null);
const builtin = computed(() => props.title?.builtin === true);
const readonly = computed(() => !props.canManage);
const form = ref<TitleForm>(UtilTitle.emptyForm(1));
/** 연 때의 값 — 저장하지 않은 변경 판정 */
let initial = '';
const tried = ref(false);

watch(
    () => [open.value, props.title] as const,
    ([isOpen]) => {
        if (!isOpen) return;
        form.value = props.title ? UtilTitle.formOf(props.title) : UtilTitle.emptyForm(props.nextSortOrder);
        initial = JSON.stringify(form.value);
        tried.value = false;
    },
    { immediate: true },
);

const nameLength = computed(() => Array.from(form.value.name.trim()).length);
const problems = computed(() => UtilTitle.problems(form.value));
/** 칸 아래 문구 — 서버가 거절한 것이 먼저, 그다음 화면 검사(빈 칸은 [저장]을 누른 뒤에만) */
function errorOf(field: string): string {
    const server = props.fieldErrors[field];
    if (server) return server;
    const problem = problems.value[field];
    if (!problem) return '';
    if (!tried.value && (problem === 'NAME_REQUIRED' || problem === 'TIERS_REQUIRED')) return '';
    return UtilTitleMessages.problem(t, problem);
}

const chipOptions = computed(() => [
    { value: 'PURPLE', label: t('memberTitle.chip.PURPLE') },
    { value: 'GREEN', label: t('memberTitle.chip.GREEN') },
]);
const methodOptions = computed(() => UtilTitle.METHODS.map((value) => ({ value, label: t(`memberTitle.method.${value}`) })));
const metricOptions = computed(() => [{ value: null, label: t('memberTitle.drawer.tierNone') }, ...METRICS.map((value) => ({ value, label: t(`memberTitle.metric.${value}`) }))]);
/** 조건 「글 수」의 게시판 — 「모든 게시판」 + 목록 */
const boardOptions = computed(() => [{ id: null as string | null, name: t('memberTitle.drawer.allBoards') }, ...props.boards]);
/** 아직 넣지 않은 조건 종류 */
const addableConditions = computed(() =>
    UtilTitle.CONDITION_TYPES.filter((type) => !form.value.conditions.some((c) => c.type === type)).map((value) => ({ value, label: t(`memberTitle.condType.${value}`) })),
);
const previewLook = computed<TitleLook>(() => ({ key: props.title?.key ?? 'preview', label: form.value.name || t('memberTitle.drawer.name'), labelEn: form.value.nameEn, icon: form.value.icon, tier: form.value.tierMetric ? 'GOLD' : null, chip: form.value.chip }));

function pickMethod(method: TitleMethod | null): void {
    if (!method) return;
    form.value.method = method;
    // 받는 방법을 바꾸면 기본 칩 색도 따라간다(자동 = 초록, 수여·심사 = 보라) — 운영자가 다시 바꿀 수 있다
    form.value.chip = UtilTitle.chipOf(method);
}

function pickMetric(metric: TitleMetric | null): void {
    form.value.tierMetric = metric;
}

function addCondition(type: TitleConditionType | null): void {
    if (type) form.value.conditions.push(UtilTitle.emptyCondition(type));
}

function submit(): void {
    tried.value = true;
    if (readonly.value || Object.keys(problems.value).length > 0) return;
    emit('save', UtilTitle.toRequest(form.value), props.title?.key ?? null);
}

/** 닫기 — 저장하지 않은 변경이 있으면 묻는다 */
async function onVisible(value: boolean): Promise<void> {
    if (value) {
        open.value = true;
        return;
    }
    if (props.saving) return;
    if (!readonly.value && JSON.stringify(form.value) !== initial) {
        const ok = await confirm({ title: t('memberTitle.drawer.discardTitle'), message: t('memberTitle.drawer.discardText'), confirmLabel: t('memberTitle.drawer.discard'), tone: 'danger' });
        if (!ok) return;
    }
    open.value = false;
}
</script>
