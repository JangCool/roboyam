<template>
    <!--
        소모임 설정의 구획 「개설 규칙」(community `GroupCreationRuleSection`) — ① 개설 방식(승인제 / 자유 개설 — 이름·설명·「기본」 딱지가 있는 고르기 카드) + 승인 대기 줄 ② 개설할 수 있는 등급(고르기 단추)
        ③ 한 사람이 모임장일 수 있는 소모임 수(1~10) ④ 개설 화면에서 이렇게 보여요(저장 전 값을 바로 반영) ⑤ 마지막 저장 줄 ⑥ 구획 저장 줄. 저장은 이 구획의 것이다.
        승인제 → 자유 개설로 바꾸는 저장은 확인을 거친다(대기 중인 신청은 그대로 남는다). 값 이름(승인제·등급 이름)은 서버가 준 `limits` 를 그대로 쓰고 설명 글만 화면이 갖는다.
        서버 호출은 `useGroupSettingsApi`(목업 — `?mock=creation-fail`), 상태는 `useGroupRuleSection`.
    -->
    <AppGadget :title="t('group.creation.title')" anchor="group-creation-rule" :changes="s.changeCount.value" :invalid="s.errorCount.value">
        <template #title-extra>
            <GroupTag v-if="pill" :value="pill" :severity="s.errorCount.value ? 'danger' : s.changeCount.value ? 'warn' : 'secondary'" />
        </template>

        <div v-if="s.loading.value && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="4rem" />
            <Skeleton height="2.25rem" width="min(28rem, 100%)" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('group.ruleLoadFailed')" :description="s.loadError.value?.message || t('group.ruleLoadFailedBody')" retry :retrying="s.loading.value" @retry="s.load" />

        <div v-else class="flex flex-col gap-5">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('group.creation.lead') }}</p>

            <!-- ① 개설 방식 -->
            <div class="flex flex-col gap-2">
                <div class="flex items-baseline justify-between gap-2">
                    <span :id="`${uid}-mode`" class="text-sm font-medium">{{ t('group.creation.modeLabel') }}</span>
                    <span class="flex items-center gap-2">
                        <GroupTag v-if="s.changedFields.value.includes('creationMode')" :value="t('group.ruleChanged')" severity="warn" />
                        <Button v-if="editable && s.differs('creationMode')" type="button" :label="t('group.ruleResetDefault')" size="small" variant="link" class="p-0! text-xs" @click="s.resetField('creationMode')" />
                    </span>
                </div>
                <div role="radiogroup" :aria-labelledby="`${uid}-mode`" class="grid gap-2 sm:grid-cols-2">
                    <label
                        v-for="option in saved.limits.creationModes"
                        :key="option.value"
                        class="flex items-start gap-3 rounded-lg border px-3 py-2.5 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary"
                        :class="[form.creationMode === option.value ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', editable ? 'cursor-pointer' : '']"
                    >
                        <RadioButton :model-value="form.creationMode" :value="option.value" :name="`${uid}-mode`" :disabled="!editable" class="mt-0.5 shrink-0" @update:model-value="(value: string) => s.set('creationMode', value)" />
                        <span class="min-w-0">
                            <span class="flex flex-wrap items-center gap-x-2">
                                <b class="text-sm font-medium">{{ option.label }}</b>
                                <GroupTag v-if="saved.defaults.creationMode === option.value" :value="t('group.creation.badgeDefault')" />
                            </span>
                            <small class="mt-0.5 block text-xs leading-relaxed text-muted-color">{{ t(`group.creation.modeText.${option.value}`) }}</small>
                        </span>
                    </label>
                </div>
                <p class="m-0 text-xs text-muted-color">
                    {{ saved.pendingRequestCount > 0 ? t('group.creation.pendingLine', { n: saved.pendingRequestCount }) : t('group.creation.pendingNone') }} ·
                    <NuxtLink :to="UtilGroup.REQUESTS_PATH" class="font-medium text-primary hover:underline">{{ t('group.creation.approvalLink') }} ›</NuxtLink>
                </p>
            </div>

            <!-- ② 개설할 수 있는 등급 -->
            <div class="flex flex-col gap-1.5">
                <div class="flex items-baseline justify-between gap-2">
                    <span :id="`${uid}-grade`" class="text-sm font-medium">
                        {{ t('group.creation.gradeLabel') }}
                        <small class="ml-1 text-xs font-normal text-muted-color">{{ t('group.ruleDefault', { value: t('group.creation.gradeOption', { label: gradeLabel(saved.defaults.minGrade) }) }) }}</small>
                    </span>
                    <span class="flex items-center gap-2">
                        <GroupTag v-if="s.changedFields.value.includes('minGrade')" :value="t('group.ruleChanged')" severity="warn" />
                        <Button v-if="editable && s.differs('minGrade')" type="button" :label="t('group.ruleResetDefault')" size="small" variant="link" class="p-0! text-xs" @click="s.resetField('minGrade')" />
                    </span>
                </div>
                <SelectButton :model-value="form.minGrade" :options="gradeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="!editable" :aria-labelledby="`${uid}-grade`" class="self-start" @update:model-value="(value: string) => s.set('minGrade', value)" />
                <p v-if="s.serverError('minGrade')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ s.serverError('minGrade') }}</p>
                <p v-else class="m-0 text-xs text-muted-color">{{ t('group.creation.gradeNote') }}</p>
            </div>

            <!-- ③ 한 사람이 모임장일 수 있는 소모임 -->
            <GroupRuleField
                :label="t('group.creation.maxLabel')"
                :hint="UtilGroupMessages.range(t, saved.limits.maxPerOwner.min, saved.limits.maxPerOwner.max, 'items', saved.defaults.maxPerOwner)"
                :help="t('group.creation.maxNote')"
                :error="errorOf('maxPerOwner')"
                :changed="s.changedFields.value.includes('maxPerOwner')"
                :differs="s.differs('maxPerOwner')"
                :editable="editable"
                @reset="s.resetField('maxPerOwner')"
            >
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" :model-value="String(form.maxPerOwner ?? '')" inputmode="numeric" autocomplete="off" class="w-20 text-right tabular-nums" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" @update:model-value="(v: string | undefined) => s.set('maxPerOwner', v ?? '')" />
                    <span class="text-sm text-muted-color">{{ t('group.creation.maxUnit') }}</span>
                </template>
            </GroupRuleField>

            <!-- ④ 미리보기 — 저장 전 값을 바로 반영한다 -->
            <div class="flex flex-col gap-2">
                <div class="flex flex-wrap items-center gap-2">
                    <b class="text-sm font-medium">{{ t('group.creation.previewLabel') }}</b>
                    <GroupTag :value="t('group.previewPill')" />
                </div>
                <dl class="m-0 flex flex-col gap-2 rounded-lg border border-(--line) bg-surface-50 px-3 py-2.5 dark:bg-surface-800">
                    <div v-for="row in preview" :key="row.key">
                        <dt class="text-xs font-semibold text-muted-color">{{ row.title }}</dt>
                        <dd class="m-0 mt-0.5 text-[13px] leading-relaxed">{{ row.text }}</dd>
                    </div>
                </dl>
            </div>

            <p class="m-0 text-xs text-muted-color">{{ saved.fromDefaults ? t('group.ruleFromDefaults') : saved.updatedAt ? UtilGroupMessages.updatedLine(t, saved.updatedBy, time.full(saved.updatedAt)) : '' }}</p>
            <Message v-if="s.saveError.value" severity="error" variant="simple" size="small" role="alert">{{ s.saveError.value }}</Message>

            <GroupSaveRow v-if="canEdit" :note="UtilGroupMessages.note(t, s.changeCount.value, s.saving.value, s.justSaved.value)" :dirty="s.changeCount.value > 0" :saving="s.saving.value" :disabled="!s.canSave.value" :error-count="s.errorCount.value" @save="save" @revert="s.revert" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { GroupCreationRuleResponse, GroupCreationRuleValues } from '~/composables/group/api/useGroupSettingsApi.types';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupRule from '~/utils/util-group-rule';

interface Props {
    /** 저장할 수 있다(`group.settings`) */
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ changes: [count: number]; saved: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const api = useGroupSettingsApi();
const uid = useId();

const s = useGroupRuleSection<GroupCreationRuleValues, GroupCreationRuleResponse>({
    load: api.creationRules,
    save: api.saveCreationRules,
    ranges: (res) => ({ maxPerOwner: res.limits.maxPerOwner }),
});
const saved = s.saved;
const form = s.form;

const editable = computed(() => props.canEdit && !s.saving.value);
const pill = computed(() => (saved.value ? UtilGroupMessages.pill(t, saved.value.fromDefaults, s.changeCount.value, s.errorCount.value) : ''));

onMounted(() => void s.load());
watch(s.changeCount, (n) => emit('changes', n));

function gradeLabel(value: string): string {
    return saved.value?.limits.grades.find((g) => g.value === value)?.label ?? value;
}

/** 등급 선택지 — 「새싹 이상」 … */
const gradeOptions = computed(() => (saved.value?.limits.grades ?? []).map((g) => ({ value: g.value, label: t('group.creation.gradeOption', { label: g.label }) })));

function errorOf(key: string): string {
    const problem = s.problems.value[key];
    return problem ? UtilGroupMessages.problem(t, problem, 'items', '') : s.serverError(key);
}

/** 미리보기 — 입력 중인 값으로. 등급이 가장 낮은 등급이면 「모든 등급」, 아니면 바로 아래 등급이 받을 안내를 보인다 */
const preview = computed(() => {
    const res = saved.value;
    if (!res) return [];
    const grades = res.limits.grades;
    const index = grades.findIndex((g) => g.value === form.value.minGrade);
    const lowest = index <= 0;
    const label = gradeLabel(String(form.value.minGrade));
    const max = UtilGroupRule.numberOf(form.value.maxPerOwner) ?? res.values.maxPerOwner;
    const rows = [
        { key: 'member', title: lowest ? t('group.creation.previewAllTitle') : t('group.creation.previewMemberTitle', { grade: label }), text: form.value.creationMode === 'OPEN' ? t('group.creation.previewOpen') : t('group.creation.previewApproval') },
        lowest ? { key: 'grade', title: t('group.creation.gradeLabel'), text: t('group.creation.previewNoGradeLimit') } : { key: 'grade', title: t('group.creation.previewBelowTitle', { grade: grades[index - 1]?.label ?? '' }), text: t('group.creation.previewGradeTooLow', { grade: label }) },
        { key: 'limit', title: t('group.creation.previewLimitTitle', { n: max }), text: t('group.creation.previewLimit', { n: max }) },
    ];
    return rows;
});

async function save(): Promise<void> {
    const res = saved.value;
    if (!res) return;
    // 승인제 → 자유 개설은 한 번 더 묻는다(대기 중인 신청은 그대로 남는다)
    if (res.values.creationMode === 'APPROVAL' && form.value.creationMode === 'OPEN') {
        const index = res.limits.grades.findIndex((g) => g.value === form.value.minGrade);
        const ok = await confirm({
            title: t('group.creation.confirmTitle'),
            message: UtilGroupMessages.creationConfirm(t, index <= 0 ? null : gradeLabel(String(form.value.minGrade)), res.pendingRequestCount),
            confirmLabel: t('group.creation.confirmOk'),
            cancelLabel: t('common.cancel'),
        });
        if (!ok) return;
    }
    if (await s.save()) {
        toast.success(t('group.ruleSaved', { name: t('group.creation.title') }));
        emit('saved');
    }
}
</script>
