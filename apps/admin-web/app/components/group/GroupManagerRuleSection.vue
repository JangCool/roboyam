<template>
    <!--
        소모임 설정의 구획 「매니저」(community `GroupManagerRuleSection`) — ① 매니저 기능 켜기 스위치 + 상태 글 + 사용 현황 ② 새 소모임의 기본 인원 ③ 모임장이 정할 수 있는 최대 인원 + 영향 줄
        ④ 모임장 화면 미리보기(입력이 틀리면 마지막으로 맞았던 값으로 흐리게) ⑤ 마지막 저장 줄 ⑥ 구획 저장 줄. 기본 > 최대는 기본 칸의 문제(자동으로 맞추지 않는다).
        끄기·켜기·최대 줄이기(그보다 많이 정해 둔 소모임이 있을 때)는 확인을 거친다. 서버 호출은 `useGroupSettingsApi`(목업 — `?mock=manager-fail`), 상태는 `useGroupRuleSection`.
    -->
    <AppGadget :title="t('group.manager.title')" anchor="group-manager-rule" :changes="s.changeCount.value" :invalid="s.errorCount.value">
        <template #title-extra>
            <GroupTag v-if="pill" :value="pill" :severity="s.errorCount.value ? 'danger' : s.changeCount.value ? 'warn' : 'secondary'" />
        </template>

        <div v-if="s.loading.value && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="3rem" />
            <Skeleton height="2.25rem" width="min(28rem, 100%)" />
            <Skeleton height="2.25rem" width="min(28rem, 100%)" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('group.ruleLoadFailed')" :description="s.loadError.value?.message || t('group.ruleLoadFailedBody')" retry :retrying="s.loading.value" @retry="s.load" />

        <div v-else class="flex flex-col gap-5">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('group.manager.lead') }}</p>

            <!-- ① 켜기 -->
            <div class="flex flex-col gap-1.5">
                <div class="flex items-baseline justify-between gap-2">
                    <label :for="`${uid}-switch`" class="text-sm font-medium">
                        {{ t('group.manager.switchLabel') }}
                        <small class="ml-1 text-xs font-normal text-muted-color">{{ saved.defaults.managersEnabled ? t('group.defaultOn') : t('group.defaultOff') }}</small>
                    </label>
                    <span class="flex items-center gap-2">
                        <GroupTag v-if="s.changedFields.value.includes('managersEnabled')" :value="t('group.ruleChanged')" severity="warn" />
                        <Button v-if="editable && s.differs('managersEnabled')" type="button" :label="t('group.ruleResetDefault')" size="small" variant="link" class="p-0! text-xs" @click="s.resetField('managersEnabled')" />
                    </span>
                </div>
                <div class="flex items-start gap-3">
                    <ToggleSwitch :model-value="form.managersEnabled === true" :input-id="`${uid}-switch`" :disabled="!editable" :aria-describedby="`${uid}-switch-state`" @update:model-value="(value: boolean) => s.set('managersEnabled', value)" />
                    <span :id="`${uid}-switch-state`" class="text-[13px] leading-relaxed">{{ form.managersEnabled ? t('group.manager.switchOn') : t('group.manager.switchOff') }}</span>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ UtilGroupMessages.managerUsage(t, saved.usage.managerCount, saved.usage.groupCount) }}</p>
            </div>

            <!-- ② 기본 인원 · ③ 최대 인원 -->
            <div class="grid gap-x-6 gap-y-4 md:grid-cols-2">
                <GroupRuleField
                    v-for="key in NUMBER_KEYS"
                    :key="key"
                    :label="t(`group.manager.${key === 'limitDefault' ? 'defaultLabel' : 'maxLabel'}`)"
                    :hint="UtilGroupMessages.range(t, saved.limits[key].min, saved.limits[key].max, 'people', saved.defaults[key])"
                    :help="t(`group.manager.${key === 'limitDefault' ? 'defaultNote' : 'maxNote'}`)"
                    :error="errorOf(key)"
                    :changed="s.changedFields.value.includes(key)"
                    :differs="s.differs(key)"
                    :editable="editable"
                    @reset="s.resetField(key)"
                >
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" :model-value="String(form[key] ?? '')" inputmode="numeric" autocomplete="off" class="w-20 text-right tabular-nums" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" @update:model-value="(v: string | undefined) => s.set(key, v ?? '')" />
                        <span class="text-sm text-muted-color">{{ t('group.unit.people') }}</span>
                    </template>
                </GroupRuleField>
            </div>
            <p v-if="shrinkGroups > 0" class="m-0 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:border-amber-500/60 dark:bg-amber-950/40 dark:text-amber-200" role="status">
                {{ t('group.manager.shrinkLine', { n: shrinkGroups }) }}
            </p>

            <!-- ④ 미리보기 -->
            <div class="flex flex-col gap-2">
                <div class="flex flex-wrap items-center gap-2">
                    <b class="text-sm font-medium">{{ t('group.manager.previewLabel') }}</b>
                    <GroupTag :value="t('group.previewPill')" />
                </div>
                <p class="m-0 rounded-lg border border-(--line) bg-surface-50 px-3 py-2.5 text-[13px] leading-relaxed dark:bg-surface-800" :class="previewStale ? 'opacity-60' : ''">{{ previewText }}</p>
            </div>

            <p class="m-0 text-xs text-muted-color">{{ saved.fromDefaults ? t('group.ruleFromDefaults') : saved.updatedAt ? UtilGroupMessages.updatedLine(t, saved.updatedBy, time.full(saved.updatedAt)) : '' }}</p>
            <Message v-if="s.saveError.value" severity="error" variant="simple" size="small" role="alert">{{ s.saveError.value }}</Message>

            <GroupSaveRow v-if="canEdit" :note="UtilGroupMessages.note(t, s.changeCount.value, s.saving.value, s.justSaved.value)" :dirty="s.changeCount.value > 0" :saving="s.saving.value" :disabled="!s.canSave.value" :error-count="s.errorCount.value" @save="save" @revert="s.revert" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { GroupManagerRuleResponse, GroupManagerRuleValues } from '~/composables/group/api/useGroupSettingsApi.types';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupRule from '~/utils/util-group-rule';

const NUMBER_KEYS = ['limitDefault', 'limitMax'] as const;

interface Props {
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

const s = useGroupRuleSection<GroupManagerRuleValues, GroupManagerRuleResponse>({
    load: api.managerRules,
    save: api.saveManagerRules,
    ranges: (res) => ({ limitDefault: res.limits.limitDefault, limitMax: res.limits.limitMax }),
    orders: [{ low: 'limitDefault', high: 'limitMax', on: 'limitDefault' }],
});
const saved = s.saved;
const form = s.form;

const editable = computed(() => props.canEdit && !s.saving.value);
const pill = computed(() => (saved.value ? UtilGroupMessages.pill(t, saved.value.fromDefaults, s.changeCount.value, s.errorCount.value) : ''));

onMounted(() => void s.load());
watch(s.changeCount, (n) => emit('changes', n));

function errorOf(key: string): string {
    const problem = s.problems.value[key];
    return problem ? UtilGroupMessages.problem(t, problem, 'people', 'group.manager.orderProblem') : s.serverError(key);
}

/** 최대를 저장값보다 줄였고, 그보다 많이 정해 둔 소모임이 있으면 그 수 */
const shrinkGroups = computed(() => {
    const res = saved.value;
    if (!res) return 0;
    const max = UtilGroupRule.numberOf(form.value.limitMax);
    if (max === null || max >= res.values.limitMax) return 0;
    return UtilGroupRule.groupsAbove(res.usage.limitCounts, form.value.limitMax);
});

/** 미리보기 — 입력이 틀리면 마지막으로 맞았던 값(저장값)으로 흐리게 */
const previewStale = computed(() => Object.keys(s.problems.value).length > 0);
const previewText = computed(() => {
    const res = saved.value;
    if (!res) return '';
    if (!form.value.managersEnabled) return t('group.manager.previewOff');
    const base = previewStale.value ? res.values.limitDefault : (UtilGroupRule.numberOf(form.value.limitDefault) ?? res.values.limitDefault);
    const max = previewStale.value ? res.values.limitMax : (UtilGroupRule.numberOf(form.value.limitMax) ?? res.values.limitMax);
    return t('group.manager.previewOn', { base, max });
});

async function save(): Promise<void> {
    const res = saved.value;
    if (!res) return;
    // 끄기 · 켜기 · 최대 줄이기는 한 번 더 묻는다
    const was = res.values.managersEnabled;
    const now = form.value.managersEnabled === true;
    if (was && !now) {
        const ok = await confirm({ title: t('group.manager.confirmOffTitle'), message: UtilGroupMessages.managerConfirmOff(t, res.usage.managerCount, res.usage.groupCount), confirmLabel: t('group.manager.confirmOffOk'), cancelLabel: t('common.cancel'), tone: 'danger' });
        if (!ok) return;
    } else if (!was && now) {
        const ok = await confirm({ title: t('group.manager.confirmOnTitle'), message: t('group.manager.confirmOnBody'), confirmLabel: t('group.manager.confirmOnOk'), cancelLabel: t('common.cancel') });
        if (!ok) return;
    }
    if (shrinkGroups.value > 0) {
        const max = UtilGroupRule.numberOf(form.value.limitMax) ?? 0;
        const ok = await confirm({ title: t('group.manager.confirmShrinkTitle', { max }), message: t('group.manager.confirmShrinkBody', { max, n: shrinkGroups.value }), confirmLabel: t('group.manager.confirmShrinkOk'), cancelLabel: t('common.cancel') });
        if (!ok) return;
    }
    if (await s.save()) {
        toast.success(t('group.ruleSaved', { name: t('group.manager.title') }));
        emit('saved');
    }
}
</script>
