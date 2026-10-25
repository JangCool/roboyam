<template>
    <!--
        소모임 설정의 구획 「공개 범위」(community `GroupVisibilityRuleSection`) — ① 공개 범위 기능 켜기 스위치 + 상태 글 + 사용 현황 + 매니저 안내 알림 줄 ② 공개 요청 규칙 숫자 다섯(쿨다운·재요청 대기·거절 한도·하루 요청 수·글자 수)
        ③ 모임장·회원 화면 미리보기 ④ 마지막 저장 줄 ⑤ 구획 저장 줄. 켜기·끄기 저장은 확인을 거친다 — 켜짐으로 저장하면 같은 요청에서 기존 매니저 안내 알림이 한 번 나간다(이미 보냈으면 다시 보내지 않는다).
        서버 호출은 `useGroupSettingsApi`(목업 — `?mock=visibility-fail`), 상태는 `useGroupRuleSection`.
    -->
    <AppGadget :title="t('group.visibilityRule.title')" anchor="group-visibility-rule" :changes="s.changeCount.value" :invalid="s.errorCount.value">
        <template #title-extra>
            <GroupTag v-if="pill" :value="pill" :severity="s.errorCount.value ? 'danger' : s.changeCount.value ? 'warn' : 'secondary'" />
        </template>

        <div v-if="s.loading.value && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="3rem" />
            <Skeleton height="6rem" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('group.ruleLoadFailed')" :description="s.loadError.value?.message || t('group.ruleLoadFailedBody')" retry :retrying="s.loading.value" @retry="s.load" />

        <div v-else class="flex flex-col gap-5">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('group.visibilityRule.lead') }}</p>

            <!-- ① 켜기 -->
            <div class="flex flex-col gap-1.5">
                <div class="flex items-baseline justify-between gap-2">
                    <label :for="`${uid}-switch`" class="text-sm font-medium">
                        {{ t('group.visibilityRule.switchLabel') }}
                        <small class="ml-1 text-xs font-normal text-muted-color">{{ saved.defaults.visibilityEnabled ? t('group.defaultOn') : t('group.defaultOff') }}</small>
                    </label>
                    <span class="flex items-center gap-2">
                        <GroupTag v-if="s.changedFields.value.includes('visibilityEnabled')" :value="t('group.ruleChanged')" severity="warn" />
                        <Button v-if="editable && s.differs('visibilityEnabled')" type="button" :label="t('group.ruleResetDefault')" size="small" variant="link" class="p-0! text-xs" @click="s.resetField('visibilityEnabled')" />
                    </span>
                </div>
                <div class="flex items-start gap-3">
                    <ToggleSwitch :model-value="form.visibilityEnabled === true" :input-id="`${uid}-switch`" :disabled="!editable" :aria-describedby="`${uid}-switch-state`" @update:model-value="(value: boolean) => s.set('visibilityEnabled', value)" />
                    <span :id="`${uid}-switch-state`" class="text-[13px] leading-relaxed">{{ form.visibilityEnabled ? t('group.visibilityRule.switchOn') : t('group.visibilityRule.switchOff') }}</span>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ UtilGroupMessages.visibilityUsage(t, saved.usage.publicGroupCount, saved.usage.privateGroupCount, saved.usage.pendingRequestCount) }}</p>
                <p class="m-0 text-xs text-muted-color">{{ UtilGroupMessages.visibilityNotice(t, saved.usage.managerNoticeSentAt ? time.full(saved.usage.managerNoticeSentAt) : null, saved.usage.managerNoticeTargetCount) }}</p>
            </div>

            <!-- ② 공개 요청 규칙 숫자 -->
            <div class="grid gap-x-6 gap-y-4 md:grid-cols-2">
                <GroupRuleField
                    v-for="field in NUMBERS"
                    :key="field.key"
                    :label="t(`group.visibilityRule.numbers.${field.key}.label`)"
                    :hint="UtilGroupMessages.range(t, saved.limits[field.key].min, saved.limits[field.key].max, field.unit, saved.defaults[field.key])"
                    :help="t(`group.visibilityRule.numbers.${field.key}.note`)"
                    :error="errorOf(field.key, field.unit)"
                    :changed="s.changedFields.value.includes(field.key)"
                    :differs="s.differs(field.key)"
                    :editable="editable"
                    @reset="s.resetField(field.key)"
                >
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" :model-value="String(form[field.key] ?? '')" inputmode="numeric" autocomplete="off" class="w-20 text-right tabular-nums" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" @update:model-value="(v: string | undefined) => s.set(field.key, v ?? '')" />
                        <span class="text-sm text-muted-color">{{ t(`group.unit.${field.unit}`) }}</span>
                    </template>
                </GroupRuleField>
            </div>

            <!-- ③ 미리보기 -->
            <div class="flex flex-col gap-2">
                <div class="flex flex-wrap items-center gap-2">
                    <b class="text-sm font-medium">{{ t('group.visibilityRule.previewLabel') }}</b>
                    <GroupTag :value="t('group.previewPill')" />
                </div>
                <div class="rounded-lg border border-(--line) bg-surface-50 px-3 py-2.5 text-[13px] leading-relaxed dark:bg-surface-800" :class="previewStale ? 'opacity-60' : ''">
                    <p class="m-0">{{ form.visibilityEnabled ? t('group.visibilityRule.previewOn') : t('group.visibilityRule.previewOff') }}</p>
                    <p v-if="form.visibilityEnabled" class="m-0 mt-1">{{ previewRules }}</p>
                </div>
            </div>

            <p class="m-0 text-xs text-muted-color">{{ saved.fromDefaults ? t('group.ruleFromDefaults') : saved.updatedAt ? UtilGroupMessages.updatedLine(t, saved.updatedBy, time.full(saved.updatedAt)) : '' }}</p>
            <Message v-if="s.saveError.value" severity="error" variant="simple" size="small" role="alert">{{ s.saveError.value }}</Message>

            <GroupSaveRow v-if="canEdit" :note="UtilGroupMessages.note(t, s.changeCount.value, s.saving.value, s.justSaved.value)" :dirty="s.changeCount.value > 0" :saving="s.saving.value" :disabled="!s.canSave.value" :error-count="s.errorCount.value" @save="save" @revert="s.revert" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { GroupVisibilityRuleResponse, GroupVisibilityRuleValues } from '~/composables/group/api/useGroupSettingsApi.types';
import type { GroupRuleUnit } from '~/utils/util-group-messages';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupRule from '~/utils/util-group-rule';

type NumberKey = Exclude<keyof GroupVisibilityRuleValues, 'visibilityEnabled'>;
const NUMBERS: readonly { key: NumberKey; unit: GroupRuleUnit }[] = [
    { key: 'cooldownDays', unit: 'day' },
    { key: 'retryHours', unit: 'hour' },
    { key: 'rejectLimit', unit: 'times' },
    { key: 'dailyLimit', unit: 'count' },
    { key: 'textMax', unit: 'chars' },
];

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

const s = useGroupRuleSection<GroupVisibilityRuleValues, GroupVisibilityRuleResponse>({
    load: api.visibilityRules,
    save: api.saveVisibilityRules,
    ranges: (res) => ({ ...res.limits }),
});
const saved = s.saved;
const form = s.form;

const editable = computed(() => props.canEdit && !s.saving.value);
const pill = computed(() => (saved.value ? UtilGroupMessages.pill(t, saved.value.fromDefaults, s.changeCount.value, s.errorCount.value) : ''));

onMounted(() => void s.load());
watch(s.changeCount, (n) => emit('changes', n));

function errorOf(key: string, unit: GroupRuleUnit): string {
    const problem = s.problems.value[key];
    return problem ? UtilGroupMessages.problem(t, problem, unit, '') : s.serverError(key);
}

/** 미리보기 — 입력이 틀리면 저장값으로 흐리게 */
const previewStale = computed(() => Object.keys(s.problems.value).length > 0);
const previewRules = computed(() => {
    const res = saved.value;
    if (!res) return '';
    const num = (key: NumberKey) => (previewStale.value ? res.values[key] : (UtilGroupRule.numberOf(form.value[key]) ?? res.values[key]));
    return UtilGroupMessages.visibilityRules(t, { cooldownDays: num('cooldownDays'), retryHours: num('retryHours'), rejectLimit: num('rejectLimit'), dailyLimit: num('dailyLimit'), textMax: num('textMax') });
});

async function save(): Promise<void> {
    const res = saved.value;
    if (!res) return;
    const was = res.values.visibilityEnabled;
    const now = form.value.visibilityEnabled === true;
    if (!was && now) {
        const ok = await confirm({ title: t('group.visibilityRule.confirmOnTitle'), message: UtilGroupMessages.visibilityConfirmOn(t, res.usage.managerNoticeTargetCount, res.usage.managerNoticeSentAt !== null), confirmLabel: t('group.visibilityRule.confirmOnOk'), cancelLabel: t('common.cancel') });
        if (!ok) return;
    } else if (was && !now) {
        const ok = await confirm({ title: t('group.visibilityRule.confirmOffTitle'), message: UtilGroupMessages.visibilityConfirmOff(t, res.usage.publicGroupCount, res.usage.pendingRequestCount), confirmLabel: t('group.visibilityRule.confirmOffOk'), cancelLabel: t('common.cancel'), tone: 'danger' });
        if (!ok) return;
    }
    const result = await s.save();
    if (!result) return;
    toast.success(t('group.ruleSaved', { name: t('group.visibilityRule.title') }));
    if (result.noticeSent > 0) toast.show(t('group.visibilityRule.noticeSentToast', { n: result.noticeSent }), { life: 5000 });
    emit('saved');
}
</script>
