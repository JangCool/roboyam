<template>
    <!--
        소모임 설정의 구획 「운영 한도」(community `GroupPolicyRuleSection`) — 소제목 셋(가입·정원 · 지금 뜨는 소모임 · 일정) 아래 숫자 여덟 칸 + 미리보기 + 마지막 저장 줄 + 구획 저장 줄.
        최소 정원 > 최대 정원은 최대 칸의 문제(서버 `fields.capacityMax` 와 같다). 이미 만들어진 소모임·일정은 바뀌지 않는다(다음 요청부터).
        서버 호출은 `useGroupSettingsApi`, 상태는 `useGroupRuleSection`.
    -->
    <AppGadget :title="t('group.policy.title')" anchor="group-policy-rule" :changes="s.changeCount.value" :invalid="s.errorCount.value">
        <template #title-extra>
            <GroupTag v-if="pill" :value="pill" :severity="s.errorCount.value ? 'danger' : s.changeCount.value ? 'warn' : 'secondary'" />
        </template>

        <div v-if="s.loading.value && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="5rem" />
            <Skeleton height="5rem" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('group.ruleLoadFailed')" :description="s.loadError.value?.message || t('group.ruleLoadFailedBody')" retry :retrying="s.loading.value" @retry="s.load" />

        <div v-else class="flex flex-col gap-5">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('group.policy.lead') }}</p>

            <div v-for="group in GROUPS" :key="group.key" role="group" :aria-labelledby="`${uid}-${group.key}`" class="flex flex-col gap-3">
                <h3 :id="`${uid}-${group.key}`" class="text-sm font-medium">{{ t(`group.policy.group.${group.key}`) }}</h3>
                <div class="grid gap-x-6 gap-y-4 md:grid-cols-2">
                    <GroupRuleField
                        v-for="field in group.fields"
                        :key="field.key"
                        :label="t(`group.policy.label.${field.key}`)"
                        :hint="UtilGroupMessages.range(t, saved.limits[field.key].min, saved.limits[field.key].max, field.unit, saved.defaults[field.key])"
                        :help="t(`group.policy.help.${field.key}`)"
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
            </div>

            <!-- 미리보기 — 입력이 틀리면 저장값으로 흐리게 -->
            <div class="flex flex-col gap-2">
                <div class="flex flex-wrap items-center gap-2">
                    <b class="text-sm font-medium">{{ t('group.policy.previewLabel') }}</b>
                    <GroupTag :value="t('group.previewPill')" />
                </div>
                <ul class="m-0 flex list-none flex-col gap-1 rounded-lg border border-(--line) bg-surface-50 px-3 py-2.5 text-[13px] leading-relaxed dark:bg-surface-800" :class="previewStale ? 'opacity-60' : ''">
                    <li>{{ UtilGroupMessages.policyJoin(t, v('reapplyDays'), v('capacityMin'), v('capacityMax')) }}</li>
                    <li>{{ t('group.policy.previewTrending', { days: v('trendingActiveDays'), size: v('trendingSize') }) }}</li>
                    <li>{{ t('group.policy.previewEvent', { upcoming: v('eventMaxUpcoming'), ahead: v('eventMaxDaysAhead'), length: v('eventMaxLengthDays') }) }}</li>
                </ul>
            </div>

            <p class="m-0 text-xs text-muted-color">{{ saved.fromDefaults ? t('group.ruleFromDefaults') : saved.updatedAt ? UtilGroupMessages.updatedLine(t, saved.updatedBy, time.full(saved.updatedAt)) : '' }}</p>
            <Message v-if="s.saveError.value" severity="error" variant="simple" size="small" role="alert">{{ s.saveError.value }}</Message>

            <GroupSaveRow v-if="canEdit" :note="UtilGroupMessages.note(t, s.changeCount.value, s.saving.value, s.justSaved.value)" :dirty="s.changeCount.value > 0" :saving="s.saving.value" :disabled="!s.canSave.value" :error-count="s.errorCount.value" @save="save" @revert="s.revert" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { GroupPolicyRuleResponse, GroupPolicyRuleValues } from '~/composables/group/api/useGroupSettingsApi.types';
import type { GroupRuleUnit } from '~/utils/util-group-messages';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupRule from '~/utils/util-group-rule';

type PolicyKey = keyof GroupPolicyRuleValues;
const GROUPS: readonly { key: 'JOIN' | 'TRENDING' | 'EVENT'; fields: { key: PolicyKey; unit: GroupRuleUnit }[] }[] = [
    {
        key: 'JOIN',
        fields: [
            { key: 'reapplyDays', unit: 'day' },
            { key: 'capacityMin', unit: 'people' },
            { key: 'capacityMax', unit: 'people' },
        ],
    },
    {
        key: 'TRENDING',
        fields: [
            { key: 'trendingSize', unit: 'places' },
            { key: 'trendingActiveDays', unit: 'day' },
        ],
    },
    {
        key: 'EVENT',
        fields: [
            { key: 'eventMaxUpcoming', unit: 'events' },
            { key: 'eventMaxDaysAhead', unit: 'day' },
            { key: 'eventMaxLengthDays', unit: 'day' },
        ],
    },
];

interface Props {
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ changes: [count: number]; saved: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const api = useGroupSettingsApi();
const uid = useId();

const s = useGroupRuleSection<GroupPolicyRuleValues, GroupPolicyRuleResponse>({
    load: api.policyRules,
    save: api.savePolicyRules,
    ranges: (res) => ({ ...res.limits }),
    orders: [{ low: 'capacityMin', high: 'capacityMax', on: 'capacityMax' }],
});
const saved = s.saved;
const form = s.form;

const editable = computed(() => props.canEdit && !s.saving.value);
const pill = computed(() => (saved.value ? UtilGroupMessages.pill(t, saved.value.fromDefaults, s.changeCount.value, s.errorCount.value) : ''));

onMounted(() => void s.load());
watch(s.changeCount, (n) => emit('changes', n));

function errorOf(key: string, unit: GroupRuleUnit): string {
    const problem = s.problems.value[key];
    return problem ? UtilGroupMessages.problem(t, problem, unit, 'group.policy.orderProblem') : s.serverError(key);
}

const previewStale = computed(() => Object.keys(s.problems.value).length > 0);

/** 미리보기에 쓸 값 — 입력이 맞으면 입력값, 틀리면 저장값 */
function v(key: PolicyKey): number {
    const res = saved.value;
    if (!res) return 0;
    return previewStale.value ? res.values[key] : (UtilGroupRule.numberOf(form.value[key]) ?? res.values[key]);
}

async function save(): Promise<void> {
    if (await s.save()) {
        toast.success(t('group.ruleSaved', { name: t('group.policy.title') }));
        emit('saved');
    }
}
</script>
