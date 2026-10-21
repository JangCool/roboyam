<template>
    <!--
        운영자 기본 정보 — 이름(필수) · 이메일(읽기 전용) · 역할(여러 개) · 계정 기간. hamonica3 `members.vue` 의 「기본 정보」 탭에서 프로필 칸(국가·전화·회사·설명)과 워크스페이스 참여를 뺀 것이다.
        저장은 부르는 쪽(페이지)이 한다 — 여기는 `save` 로 값을 내보낼 뿐이다. 최고 관리자(루트)·권한이 없는 운영자에게는 읽기 전용이다.
    -->
    <form ref="formEl" class="flex flex-col gap-4 pt-3" novalidate @submit.prevent="submit">
        <Message v-if="operator.isSuper" severity="info" size="small" variant="simple" :closable="false">{{ t('operator.superNotice') }}</Message>

        <LabeledField :label="t('operator.fieldName')" :error="nameProblem || fieldErrors.operatorName">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" v-model="form.operatorName" name="operatorName" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
            </template>
        </LabeledField>

        <!-- 읽기 전용 — 로그인 아이디라 바꿀 수 없다. 서버 값이 폼에 포함돼야 하므로 disabled 가 아니라 readonly -->
        <LabeledField :label="t('operator.fieldEmail')" :help="t('operator.emailHelp')">
            <template #default="{ id, describedBy }">
                <InputText :id="id" :model-value="operator.email" readonly :aria-describedby="describedBy" fluid />
            </template>
        </LabeledField>

        <LabeledField :label="t('operator.fieldRoles')" :help="rolesHelp" :error="fieldErrors.roleIds">
            <template #default="{ id, describedBy }">
                <p v-if="operator.isSuper" :id="id" class="text-sm text-muted-color">{{ t('operator.superNoRoles') }}</p>
                <AppMultiSelect
                    v-else
                    v-model="form.roleIds"
                    :options="UtilOperator.groupRoleOptions(roleOptions)"
                    option-group-label="label"
                    option-group-children="items"
                    option-label="roleName"
                    option-value="roleId"
                    option-disabled="disabled"
                    :input-id="id"
                    :aria-label="t('operator.fieldRoles')"
                    :placeholder="t('operator.rolesPlaceholder')"
                    :disabled="!rolesEditable"
                    :selected-items-label="t('operator.rolesSelectedCount', { count: '{0}' })"
                    :filter="roleOptions.length > 8"
                    :filter-placeholder="t('operator.rolesFilterPlaceholder')"
                    :empty-message="t('operator.rolesEmpty')"
                    :aria-describedby="describedBy"
                >
                    <template #option="{ option }">
                        <span class="pointer-events-auto flex min-w-0 items-center gap-2" v-tooltip.top="option.disabled ? t('operator.outOfScope') : undefined">
                            <span class="truncate">{{ option.roleName }}</span>
                            <span v-if="option.disabled" class="inline-flex shrink-0 items-center gap-1 text-xs text-muted-color">
                                <i class="pi pi-lock text-[10px]" aria-hidden="true" />{{ t('operator.outOfScope') }}
                            </span>
                        </span>
                    </template>
                </AppMultiSelect>
            </template>
        </LabeledField>

        <!-- 계정 기간 — 기간이 지나면 로그인이 막힌다. 「무기한」이 기본이다(대부분의 계정은 기간이 없다) -->
        <div role="group" :aria-labelledby="periodTitleId" class="flex flex-col gap-3 border-t border-(--line) pt-4">
            <h3 :id="periodTitleId" class="text-sm font-medium">{{ t('operator.periodTitle') }}</h3>
            <div class="flex items-center gap-2">
                <Checkbox v-model="unlimited" binary :input-id="unlimitedId" :disabled="!editable" />
                <label :for="unlimitedId" class="cursor-pointer text-sm">{{ t('operator.unlimited') }}</label>
            </div>

            <template v-if="!unlimited">
                <div class="grid gap-3 sm:grid-cols-2">
                    <div>
                        <label class="mb-1 block text-xs text-muted-color" :for="startId">{{ t('operator.periodStart') }}</label>
                        <AppDateTimePicker v-model="form.accountStartAt" :input-id="startId" :minute-step="30" :max="form.accountEndAt || undefined" :readonly="!editable" :show-zone="false" :placeholder="t('operator.periodOpen')" />
                    </div>
                    <div>
                        <label class="mb-1 block text-xs text-muted-color" :for="endId">{{ t('operator.periodEnd') }}</label>
                        <AppDateTimePicker v-model="form.accountEndAt" :input-id="endId" :minute-step="30" :min="form.accountStartAt || undefined" :readonly="!editable" :placeholder="t('operator.periodOpen')" />
                    </div>
                </div>
                <small class="text-xs leading-relaxed text-(--p-message-warn-simple-color)">{{ t('operator.periodWarn') }}</small>
            </template>
            <small v-if="periodProblem" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ periodProblem }}</small>
        </div>

        <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>

        <!-- 저장·초기화를 바깥(서랍 하단 고정 푸터)에 두면(`hideActions`) 이 폼에는 단추가 없다. Enter 로 저장되게 보이지 않는 제출 단추만 둔다 -->
        <button v-if="editable && hideActions" type="submit" class="hidden" tabindex="-1" aria-hidden="true" />
        <div v-if="editable && !hideActions" class="flex gap-2 border-t border-(--line) pt-4">
            <Button type="submit" :label="t('common.save')" size="small" :loading="saving" :disabled="!dirty" />
            <Button type="button" :label="t('common.reset')" size="small" severity="secondary" variant="text" :disabled="saving || !dirty" @click="fill" />
        </div>
        <p v-else-if="!operator.isSuper" class="text-xs text-muted-color">{{ t('operator.readOnlyNote') }}</p>
    </form>
</template>

<script setup lang="ts">
import type { OperatorRoleOption } from '~/components/operator/OperatorInfoForm.types';
import type { OperatorUpdateRequest, OperatorView } from '~/composables/operator/api/useOperatorApi.types';
import UtilOperator from '~/utils/util-operator';

interface Props {
    operator: OperatorView;
    roleOptions?: OperatorRoleOption[];
    /** 이름·기간을 고칠 수 있는가(`operator.manage`) */
    canManage?: boolean;
    /** 역할을 바꿀 수 있는가(`role.grant`) */
    canGrant?: boolean;
    saving?: boolean;
    /** 칸에 붙지 않는 저장 오류 */
    error?: string;
    /** 서버가 칸에 붙여 준 오류(`ApiError.fields`) */
    fieldErrors?: Record<string, string>;
    /** 저장·초기화 단추를 그리지 않는다 — 부모가 `submit()`·`reset()`(expose)을 부른다 */
    hideActions?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    roleOptions: () => [],
    canManage: false,
    canGrant: false,
    saving: false,
    error: '',
    fieldErrors: () => ({}),
    hideActions: false,
});
const emit = defineEmits<{ save: [payload: OperatorUpdateRequest]; 'update:dirty': [dirty: boolean] }>();

const { t } = useI18n();
const unlimitedId = useId();
const startId = useId();
const endId = useId();
const periodTitleId = useId();
const formEl = ref<HTMLFormElement | null>(null);

const form = reactive({ operatorName: '', roleIds: [] as string[], accountStartAt: '', accountEndAt: '' });
const unlimited = ref(true);
const submitted = ref(false);

/** 최고 관리자(루트)는 바꿀 수 없다 — 읽기 전용 */
const editable = computed(() => props.canManage && !props.operator.isSuper);
const rolesEditable = computed(() => editable.value && props.canGrant);
const rolesHelp = computed(() => (props.operator.isSuper ? '' : props.canGrant ? t('operator.rolesHelp') : t('operator.rolesNeedGrant')));

/** 서버 값을 폼으로 베낀다 — 운영자가 바뀌거나(다른 행) 다시 읽어 오면 다시 채운다 */
function fill(): void {
    form.operatorName = props.operator.operatorName;
    form.roleIds = props.operator.roles.map((r) => r.roleId);
    form.accountStartAt = props.operator.accountStartAt ?? '';
    form.accountEndAt = props.operator.accountEndAt ?? '';
    unlimited.value = !props.operator.accountStartAt && !props.operator.accountEndAt;
    submitted.value = false;
}
watch(() => props.operator, fill, { immediate: true });

// 「무기한」을 켜면 두 날짜를 비운다 — 무제한이라면서 날짜가 남아 있으면 저장할 때 무엇이 이기는지 알 수 없다
watch(unlimited, (on) => {
    if (on) {
        form.accountStartAt = '';
        form.accountEndAt = '';
    }
});

const nameProblem = computed(() => {
    if (!submitted.value) return '';
    const name = form.operatorName.trim();
    if (!name) return t('operator.nameRequired');
    return name.length > UtilOperator.NAME_MAX ? t('operator.nameTooLong', { max: UtilOperator.NAME_MAX }) : '';
});
const periodProblem = computed(() => (UtilOperator.isPeriodReversed(form.accountStartAt, form.accountEndAt) ? t('operator.periodReversed') : ''));

const dirty = computed(() => {
    const o = props.operator;
    const sameRoles = form.roleIds.length === o.roles.length && o.roles.every((r) => form.roleIds.includes(r.roleId));
    return form.operatorName !== o.operatorName || !sameRoles || form.accountStartAt !== (o.accountStartAt ?? '') || form.accountEndAt !== (o.accountEndAt ?? '');
});

function submit(): void {
    submitted.value = true;
    if (nameProblem.value || periodProblem.value) return;
    emit('save', {
        operatorName: form.operatorName.trim(),
        // 역할을 바꿀 수 없는 운영자는 역할을 보내지 않는다(서버가 그대로 둔다)
        ...(rolesEditable.value ? { roleIds: [...form.roleIds] } : {}),
        accountStartAt: unlimited.value ? null : form.accountStartAt || null,
        accountEndAt: unlimited.value ? null : form.accountEndAt || null,
    });
}

// 저장하지 않은 변경 여부를 부모(서랍의 닫기 보호·푸터 단추)에 알린다
watch(dirty, (value) => emit('update:dirty', value), { immediate: true });

/** 이름·역할 입력칸에 초점 — 서랍이 열릴 때 부른다 */
function focusFirst(): void {
    formEl.value?.querySelector<HTMLInputElement>('input[name=operatorName]')?.focus();
}

defineExpose({ submit, reset: fill, focusFirst, dirty });
</script>
