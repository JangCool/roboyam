<template>
    <!--
        소모임 정보 수정 `/groups/{id}/manage/edit` (모임장 고정) — 이름·소개·가입 방식·정원·오프라인. 분류는 읽기 전용(만든 뒤에 바꿀 수 없다).
        `PUT /api/groups/{id}` 에 모든 칸을 보내고, 응답(바뀐 상세)으로 화면을 맞춘다. 정원은 지금 멤버 수보다 작게 줄일 수 없고 범위는 상세의 `limits`(없으면 5~200).
        저장하지 않은 변경이 있는데 다른 화면으로 가면 묻는다.
    -->
    <GroupManageShell
        :title="t('group.editTitle')"
        :state="state"
        :error-message="errorMessage"
        :group-id="id"
        :back-to="UtilGroup.managePath(id)"
        :back-label="t('group.manageTitle')"
        :blocked-reason="group?.blockedReason ?? null"
        @retry="load()"
    >
        <form v-if="group" class="flex flex-col gap-5" novalidate data-testid="group-edit-form" @submit.prevent="save">
            <LabeledField :label="t('group.fieldName')" :hint="t('group.nameHint', { min: UtilGroup.NAME_MIN, max: UtilGroup.NAME_MAX })" :error="fieldError('name')">
                <template #aside>
                    <small class="text-xs tabular-nums" :class="nameLength > UtilGroup.NAME_MAX ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: nameLength, max: UtilGroup.NAME_MAX }) }}</small>
                </template>
                <template #default="{ id: fieldId, describedBy, invalid }">
                    <InputText :id="fieldId" v-model="form.name" name="name" autocomplete="off" fluid :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" data-testid="group-edit-name" />
                </template>
            </LabeledField>

            <LabeledField :label="t('group.fieldDescription')" :hint="t('group.descriptionHint', { max: UtilGroup.DESCRIPTION_MAX })" :error="fieldError('description')">
                <template #aside>
                    <small class="text-xs tabular-nums" :class="descriptionLength > UtilGroup.DESCRIPTION_MAX ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: descriptionLength, max: UtilGroup.DESCRIPTION_MAX }) }}</small>
                </template>
                <template #default="{ id: fieldId, describedBy, invalid }">
                    <Textarea :id="fieldId" v-model="form.description" name="description" rows="5" fluid class="resize-none" :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" data-testid="group-edit-description" />
                </template>
            </LabeledField>

            <!-- 분류 — 읽기 전용. 게시판이 분류에서 빠졌으면 「분류 없음」 -->
            <div class="flex flex-col gap-1.5">
                <span :id="`${uid}-category`" class="text-sm font-medium">{{ t('group.fieldCategory') }}</span>
                <div class="flex flex-wrap items-center gap-2" role="group" :aria-labelledby="`${uid}-category`">
                    <GroupTag v-if="group.category" tone="neutral">{{ group.category.name }}</GroupTag>
                    <span v-else class="text-sm text-muted-color">{{ t('group.categoryNone') }}</span>
                    <small class="text-xs text-muted-color">{{ t('group.categoryLocked') }}</small>
                </div>
            </div>

            <GroupOptionCards v-model="form.joinType" :legend="t('group.fieldJoinType')" :options="joinTypeOptions" :disabled="saving" :error="fieldError('joinType')" testid="group-edit-join" />

            <LabeledField :label="t('group.fieldCapacity')" :hint="t('group.capacityHint', { min: range.min, max: range.max })" :help="t('group.capacityEditHelp', { n: group.memberCount, min: range.min, max: range.max })" :error="fieldError('capacity')">
                <template #default="{ id: fieldId, describedBy, invalid }">
                    <div class="flex items-center gap-2">
                        <InputText :id="fieldId" v-model="form.capacity" name="capacity" inputmode="numeric" autocomplete="off" class="w-28" :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" data-testid="group-edit-capacity" />
                        <span class="text-sm text-muted-color">{{ t('group.capacityUnit') }}</span>
                    </div>
                </template>
            </LabeledField>

            <div class="space-y-2">
                <GroupOptionCards
                    :model-value="form.offline ? 'YES' : 'NO'"
                    :legend="t('group.fieldOffline')"
                    :options="offlineOptions"
                    :disabled="saving"
                    :error="fieldError('offline')"
                    testid="group-edit-offline"
                    @update:model-value="(value: string) => (form.offline = value === 'YES')"
                />
                <GroupNotice v-if="form.offline" tone="warn" :title="t('group.offlineBoxTitle')">{{ t('group.offlineEditText') }}</GroupNotice>
            </div>

            <div class="flex flex-col gap-2">
                <Message v-if="saveSummary" severity="error" variant="simple" data-testid="group-edit-summary">{{ saveSummary }}</Message>
                <p v-if="changed" role="status" class="text-xs text-amber-700 dark:text-amber-400">{{ t('common.saveBarDirty') }}</p>
                <Button type="submit" :label="t('common.save')" fluid :loading="saving" :disabled="!canSave" data-testid="group-edit-save" />
            </div>
        </form>
    </GroupManageShell>
</template>

<script setup lang="ts">
import type { GroupOption } from '~/components/group/GroupOptionCards.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup, { type GroupEditField, type GroupEditForm } from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('group.editTitle'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const uid = useId();
const api = useGroupApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { state, errorMessage, group, load, apply, block } = useGroupManage(id, 'LEADER');

const form = reactive<GroupEditForm>({ name: '', description: '', joinType: 'OPEN', capacity: '', offline: false });
const saving = ref(false);
const serverErrors = ref<Partial<Record<GroupEditField, string>>>({});
const saveSummary = ref('');

// 소모임 정보가 처음 오면 폼을 채운다(저장 뒤에는 응답으로 다시 채운다)
let filled = false;
watch(
    group,
    (g) => {
        if (!g || filled) return;
        Object.assign(form, UtilGroup.editFormOf(g));
        filled = true;
    },
    { immediate: true },
);

const joinTypeOptions = computed<GroupOption[]>(() => [
    { value: 'OPEN', label: t('group.joinOpen'), hint: t('group.joinOpenEditHint') },
    { value: 'APPROVAL', label: t('group.joinApproval'), hint: form.offline ? t('group.joinApprovalOfflineHint') : t('group.joinApprovalEditHint') },
]);
const offlineOptions = computed<GroupOption[]>(() => [
    { value: 'NO', label: t('common.none'), hint: t('group.offlineNoEditHint') },
    { value: 'YES', label: t('group.offlineYes'), hint: t('group.offlineYesHint') },
]);

const nameLength = computed(() => UtilGroup.length(UtilGroup.normalizeName(form.name)));
const descriptionLength = computed(() => UtilGroup.trimmedLength(form.description));
const range = computed(() => UtilGroup.capacityRange(group.value?.limits));
const problems = computed(() => (group.value ? UtilGroup.editProblems(form, group.value.memberCount, range.value) : {}));
const changed = computed(() => (group.value ? UtilGroup.isEditChanged(form, group.value) : false));
const canSave = computed(() => group.value !== null && !group.value.blockedReason && changed.value && Object.keys(problems.value).length === 0 && !saving.value);

const FIELD_LABEL: Record<GroupEditField, string> = {
    name: 'group.fieldName',
    description: 'group.fieldDescription',
    joinType: 'group.fieldJoinType',
    capacity: 'group.fieldCapacity',
    offline: 'group.fieldOffline',
};

function fieldError(field: GroupEditField): string {
    const problem = problems.value[field];
    if (problem) {
        const length = field === 'name' ? nameLength.value : field === 'description' ? descriptionLength.value : undefined;
        return UtilGroupMessages.problem(t, field, problem, { range: range.value, memberCount: group.value?.memberCount ?? 0, length });
    }
    return serverErrors.value[field] ?? '';
}

// 칸을 고치면 그 칸의 서버 오류와 폼 위 한 줄을 걷는다
for (const field of UtilGroup.EDIT_FIELDS) {
    watch(
        () => form[field],
        () => {
            if (serverErrors.value[field] !== undefined) {
                const rest = { ...serverErrors.value };
                delete rest[field];
                serverErrors.value = rest;
            }
            saveSummary.value = '';
        },
    );
}

async function save() {
    if (!group.value || !canSave.value) return;
    const request = UtilGroup.toUpdateRequest(form);
    if (!request) return;
    saving.value = true;
    serverErrors.value = {};
    saveSummary.value = '';
    try {
        const detail = await api.update(id, request);
        apply(detail);
        if (group.value) Object.assign(form, UtilGroup.editFormOf(group.value));
        toast.success(t('common.saved'), 4000);
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_NAME_TAKEN') && e instanceof ApiError) {
            serverErrors.value = { name: e.message };
            return;
        }
        const fields = UtilGroup.serverFields(e, UtilGroup.EDIT_FIELDS);
        const keys = Object.keys(fields) as GroupEditField[];
        if (keys.length) {
            serverErrors.value = fields;
            saveSummary.value = UtilGroupMessages.serverFieldsSummary(
                t,
                keys.map((k) => t(FIELD_LABEL[k])),
            );
            return;
        }
        const blocked = UtilGroup.blockedReasonOf(e);
        if (blocked) block(blocked);
        if (UtilGroup.isCode(e, 'GROUP_LEADER_ONLY')) {
            toast.error(UtilBoardMessages.of(t, e), 8000);
            filled = false;
            await load();
            return;
        }
        toast.error(UtilGroupMessages.failed(t, 'group.saveFailed', e), 8000);
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있으면 떠나기 전에 묻는다
onBeforeRouteLeave(async () => {
    if (!changed.value || saving.value) return true;
    return await confirm({ title: t('group.leaveUnsavedTitle'), message: t('group.leaveUnsavedMessage'), confirmLabel: t('group.leaveUnsavedConfirm'), tone: 'danger' });
});
</script>
