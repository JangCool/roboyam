<template>
    <!--
        운영자 등록 — hamonica3 `InviteDialog` 처럼 하나의 다이얼로그에서 두 방식을 고른다(정책 5번의 ① 만들거나 초대).
          · 직접 추가: 이름·이메일·역할로 계정을 바로 만든다(승인된 상태, 임시 비밀번호는 이메일로 안내 — 방식은 미확정).
          · 초대: 이메일 여러 개를 목록에 쌓아 초대 링크 메일을 보낸다. 받은 사람이 링크에서 직접 등록한다. 한 줄씩 따로 보내고, 실패한 줄은 이유를 달고 남는다.
        보낸 초대는 목록의 「초대 대기」 탭에서 보고 취소한다(유효기간 기본 72시간 — 가정). 신청 승인·반려(②)는 목록의 신규 탭에서 한다. 서버 호출은 부르는 쪽(페이지)이 하고 여기는 `submit` 으로 값을 내보낸다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('operator.dialogTitle')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div>
                <span :id="modeLabelId" class="mb-1 block text-xs text-muted-color">{{ t('operator.modeLabel') }}</span>
                <SelectButton v-model="mode" :options="modeOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="modeLabelId" :disabled="busy" />
                <p class="mt-1.5 text-xs leading-relaxed text-muted-color">{{ mode === 'CREATE' ? t('operator.createHint') : t('operator.inviteHint', { hours: UtilOperator.INVITE_VALID_HOURS }) }}</p>
            </div>

            <template v-if="mode === 'CREATE'">
                <LabeledField :label="t('operator.fieldName')" :error="fieldErrors.operatorName">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.operatorName" name="operatorName" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                    </template>
                </LabeledField>
                <LabeledField :label="t('operator.fieldEmail')" :error="fieldErrors.email">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.email" name="email" type="email" autocomplete="off" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                    </template>
                </LabeledField>
            </template>

            <template v-else>
                <div>
                    <label class="mb-1.5 block text-sm font-medium" :for="inviteEmailId">{{ t('operator.inviteEmails') }}</label>
                    <div class="flex gap-2">
                        <InputText :id="inviteEmailId" v-model="emailInput" type="email" autocomplete="off" class="min-w-0 flex-1" placeholder="name@example.com" :disabled="busy" :invalid="!!rowError" :aria-describedby="rowError ? rowErrorId : undefined" @keydown.enter.prevent="addRow" />
                        <Button type="button" :label="t('common.add')" severity="secondary" :disabled="busy" @click="addRow" />
                    </div>
                    <p v-if="rowError" :id="rowErrorId" role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ rowError }}</p>
                </div>

                <div>
                    <div class="mb-1.5 text-sm font-medium">
                        {{ t('operator.inviteList') }}<span v-if="rows.length" class="ml-1 text-xs font-normal text-muted-color tabular-nums">({{ rows.length }})</span>
                    </div>
                    <div class="max-h-56 overflow-y-auto rounded-lg border border-(--line)">
                        <EmptyState v-if="!rows.length" icon="pi pi-envelope" :title="t('operator.inviteEmpty')" :description="t('operator.inviteEmptyHint')" />
                        <ul v-else class="m-0 list-none p-0">
                            <li v-for="row in rows" :key="row.email" class="flex items-start gap-2 border-b border-(--line) px-3 py-2 last:border-b-0">
                                <i class="pi pi-envelope mt-0.5 shrink-0 text-sm text-muted-color" aria-hidden="true" />
                                <div class="min-w-0 flex-1">
                                    <div class="truncate text-sm" :title="row.email">{{ row.email }}</div>
                                    <!-- 실패한 줄은 이유를 달고 남는다 — 고쳐서 다시 보낼 수 있어야 한다 -->
                                    <div v-if="row.error" role="alert" class="text-xs leading-relaxed text-red-600 dark:text-red-400">{{ row.error }}</div>
                                </div>
                                <Button type="button" icon="pi pi-times" severity="secondary" variant="text" rounded size="small" :aria-label="t('operator.removeEmail', { email: row.email })" :disabled="busy" @click="removeRow(row.email)" />
                            </li>
                        </ul>
                    </div>
                </div>
            </template>

            <LabeledField :label="t('operator.fieldRoles')" :help="canGrant ? t('operator.rolesHelp') : t('operator.rolesNeedGrant')">
                <template #default="{ id, describedBy }">
                    <AppMultiSelect
                        v-model="roleIds"
                        :options="UtilOperator.groupRoleOptions(roleOptions)"
                    option-group-label="label"
                    option-group-children="items"
                        option-label="roleName"
                        option-value="roleId"
                        option-disabled="disabled"
                        :input-id="id"
                        :aria-label="t('operator.fieldRoles')"
                        :placeholder="t('operator.rolesPlaceholder')"
                        :disabled="busy || !canGrant"
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

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="mode === 'CREATE' ? t('common.add') : t('operator.sendInvites')" :loading="busy" :disabled="!canSubmit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { OperatorCreateMode, OperatorCreateSubmit } from '~/components/operator/OperatorCreateDialog.types';
import type { OperatorRoleOption } from '~/components/operator/OperatorInfoForm.types';
import UtilOperator from '~/utils/util-operator';

interface Props {
    visible: boolean;
    /** 열 때 먼저 보일 방식 */
    initialMode?: OperatorCreateMode;
    roleOptions?: OperatorRoleOption[];
    /** 역할을 줄 수 있는가(`role.grant`) */
    canGrant?: boolean;
    busy?: boolean;
    /** 칸에 붙지 않는 오류 */
    error?: string;
    fieldErrors?: Record<string, string>;
    /** 초대에서 보내지 못한 주소 → 이유. 이 목록에 없는 줄은 보냈으니 목록에서 뺀다 */
    failures?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
    initialMode: 'CREATE',
    roleOptions: () => [],
    canGrant: false,
    busy: false,
    error: '',
    fieldErrors: () => ({}),
    failures: () => ({}),
});
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [payload: OperatorCreateSubmit] }>();

const { t } = useI18n();
const formId = useId();
const modeLabelId = useId();
const inviteEmailId = useId();
const rowErrorId = useId();

const mode = ref<OperatorCreateMode>('CREATE');
const form = reactive({ operatorName: '', email: '' });
const roleIds = ref<string[]>([]);
const emailInput = ref('');
const rowError = ref('');
const rows = ref<{ email: string; error?: string }[]>([]);

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const modeOptions = computed(() => [
    { label: t('operator.modeCreate'), value: 'CREATE' as const },
    { label: t('operator.modeInvite'), value: 'INVITE' as const },
]);

/** 열 때마다 비운다 — 지난번에 실패해 남은 줄이 다음에 딸려 나오면 안 된다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        mode.value = props.initialMode;
        form.operatorName = '';
        form.email = '';
        roleIds.value = [];
        emailInput.value = '';
        rowError.value = '';
        rows.value = [];
    },
    { immediate: true },
);

/** 보낸 결과가 오면 성공한 줄은 빼고 실패한 줄에 이유를 단다 */
watch(
    () => props.failures,
    (failures) => {
        rows.value = rows.value.filter((row) => row.email in failures).map((row) => ({ email: row.email, error: failures[row.email] }));
    },
);

const canSubmit = computed(() => (mode.value === 'CREATE' ? !!form.operatorName.trim() && !!form.email.trim() : rows.value.length > 0));

function addRow(): void {
    rowError.value = '';
    const email = emailInput.value.trim().toLowerCase();
    if (!email) return;
    if (!UtilOperator.isEmail(email)) {
        rowError.value = t('operator.invalidEmail');
        return;
    }
    if (rows.value.some((row) => row.email === email)) {
        rowError.value = t('operator.alreadyInList');
        return;
    }
    rows.value = [...rows.value, { email }];
    emailInput.value = '';
}

function removeRow(email: string): void {
    rows.value = rows.value.filter((row) => row.email !== email);
}

function submit(): void {
    if (!canSubmit.value) return;
    if (mode.value === 'CREATE') {
        emit('submit', { mode: 'CREATE', operatorName: form.operatorName.trim(), email: form.email.trim().toLowerCase(), roleIds: [...roleIds.value] });
    } else {
        emit('submit', { mode: 'INVITE', emails: rows.value.map((row) => row.email), roleIds: [...roleIds.value] });
    }
}
</script>
