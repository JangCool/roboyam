<template>
    <!--
        새 팀 만들기 — hamonica3 `TeamFormDialog` 와 같다. **고치기는 여기서 하지 않는다**: 오른쪽 패널에서 목록과 함께 보며 직접 고친다(모달로 고치면 목록이 가려져 옆 팀 이름을 견주지 못한다).
        차례를 묻지 않는다 — 서버가 맨 뒤에 붙이고, 옮기는 것은 목록의 위·아래 화살표다.
        입력은 화면에서 먼저 검사한다(`UtilTeam` — 서버가 같은 규칙으로 다시 본다). 서버 호출은 부르는 쪽(페이지)이 하고 여기는 `submit` 으로 값을 내보낸다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('team.dialogCreate')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <LabeledField :label="t('team.fieldName')" :hint="t('team.nameHint', { max: UtilTeam.NAME_MAX })" :error="nameError || fieldErrors.teamName">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.teamName" name="teamName" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('team.fieldDescription')" :hint="t('team.descHint', { max: UtilTeam.DESC_MAX })" :error="descError || fieldErrors.description">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="form.description" name="description" rows="3" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <div class="flex items-start justify-between gap-4">
                <div class="min-w-0">
                    <label :for="enabledId" class="text-sm font-medium">{{ t('team.fieldStatus') }}</label>
                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ form.enabled ? t('team.statusOnHint') : t('team.statusOffHint') }}</p>
                </div>
                <ToggleSwitch v-model="form.enabled" class="shrink-0" :input-id="enabledId" :disabled="busy" />
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('team.create')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { TeamSaveRequest } from '~/composables/team/api/useTeamApi.types';
import UtilTeam from '~/utils/util-team';
import UtilTeamMessages from '~/utils/util-team-messages';

interface Props {
    visible: boolean;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 칸마다 준 오류(`fields`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [payload: TeamSaveRequest] }>();

const { t } = useI18n();
const formId = useId();
const enabledId = useId();

const form = reactive({ teamName: '', description: '', enabled: true });
const submitted = ref(false);

/** 열 때마다 빈 칸으로 시작한다 — 지난번에 적다 만 것이 남아 있으면 잘못 만들기 쉽다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        submitted.value = false;
        form.teamName = '';
        form.description = '';
        form.enabled = true;
    },
    { immediate: true },
);

const nameError = computed(() => {
    const problem = submitted.value ? UtilTeam.nameProblem(form.teamName) : null;
    return problem ? UtilTeamMessages.problem(t, problem) : '';
});
const descError = computed(() => {
    const problem = submitted.value ? UtilTeam.descProblem(form.description) : null;
    return problem ? UtilTeamMessages.problem(t, problem) : '';
});

function submit(): void {
    submitted.value = true;
    if (UtilTeam.nameProblem(form.teamName) || UtilTeam.descProblem(form.description)) return;
    emit('submit', { teamName: form.teamName.trim(), description: form.description.trim() || null, enabled: form.enabled });
}
</script>
