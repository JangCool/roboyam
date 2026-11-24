<template>
    <!--
        운영자 회원 계정 「새 계정 만들기」 — 운영자가 사이트에서 쓸 회원 계정을 관리자가 바로 만들고 그 운영자에 잇는다(2026-11-24, `docs/features/operator-member-account-contract.md` 의 연결 정책 예외 길).
        회원 관리(`/members` 머리 단추 — 운영자를 고른다)와 운영자 서랍 「사이트 계정」 탭(운영자가 정해져 있다)이 연다. 흐름·서버 호출은 `useOperatorMemberCreate`, 여기는 `view` 로 그리고 이벤트로 알린다.
        칸: 연결할 운영자 · 이메일(운영자 이메일과 달라도 됨) · 닉네임(예약어 허용 — 형식·중복만) · 비밀번호(직접 입력 + 확인 / 「비밀번호 설정 메일 보내기」).
        만들면 결과 화면으로 바뀐다(닫지 않는다): 대기열에 넣은 메일(잠시 뒤 나간다) · 로그인 안내(DIRECT 는 인증 전에도 로그인, MAIL 은 비밀번호를 정한 뒤) · [회원 관리에서 보기]·[운영자 서랍 열기].
        비밀번호는 이 컴포넌트 메모리에만 있고 보내거나 닫으면 비운다(`forms.md` 2-1). 서버 칸 오류(`fields.email|nickname|passwordMode|password`, `EMAIL_TAKEN`·`REJOIN_BLOCKED` → 이메일 칸,
        `NICKNAME_TAKEN` → 닉네임 칸)는 칸 아래(그 칸을 고치면 숨긴다), 그 밖은 폼 아래에 서버 문구 그대로.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!view.busy" :header="view.result ? t('operatorMember.create.doneTitle') : t('operatorMember.create.title')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <!-- 결과 -->
        <div v-if="view.result" class="flex flex-col gap-4">
            <Message severity="success" size="small" variant="simple" :closable="false">
                {{ t('operatorMember.create.done', { nickname: view.result.nickname, operator: submittedOperator?.operatorName ?? '' }) }}
            </Message>
            <dl class="m-0 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1.5 text-[13px]">
                <dt class="text-muted-color">{{ t('operatorMember.create.fieldEmail') }}</dt>
                <dd class="m-0 min-w-0 truncate" :title="view.result.email">{{ view.result.email }}</dd>
                <dt class="text-muted-color">{{ t('operatorMember.fieldNickname') }}</dt>
                <dd class="m-0">{{ view.result.nickname }}</dd>
                <dt class="text-muted-color">{{ t('operatorMember.fieldUserId') }}</dt>
                <dd class="m-0"><code class="text-[12px] tabular-nums">{{ view.result.userId }}</code></dd>
                <dt class="text-muted-color">{{ t('operatorMember.create.fieldOperator') }}</dt>
                <dd class="m-0">{{ submittedOperator?.operatorName ?? '—' }}</dd>
            </dl>
            <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-[13px]">
                <li v-for="mail in view.result.mailsSent" :key="mail" class="flex items-start gap-2">
                    <i class="pi pi-envelope mt-0.5 text-muted-color" aria-hidden="true" />
                    <span>{{ UtilOperatorMemberMessages.mailSent(t, mail, view.result.email) }}</span>
                </li>
            </ul>
            <p class="m-0 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
                <span>{{ view.result.passwordMode === 'MAIL' ? t('operatorMember.create.doneLoginMail') : t('operatorMember.create.doneLoginDirect') }}</span>
            </p>
        </div>

        <!-- 입력 -->
        <form v-else :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('operatorMember.create.intro') }}</p>

            <!-- 연결할 운영자 -->
            <div v-if="view.fixed && fixedOperator" class="flex flex-col gap-1">
                <span class="text-sm font-medium">{{ t('operatorMember.create.fieldOperator') }}</span>
                <p class="m-0 text-sm">
                    {{ fixedOperator.operatorName }} <span class="text-xs text-muted-color">{{ fixedOperator.email }}</span>
                </p>
            </div>
            <LabeledField v-else :label="t('operatorMember.create.fieldOperator')" :help="t('operatorMember.create.operatorHelp')" :error="operatorError">
                <template #default="{ id, describedBy, invalid }">
                    <Skeleton v-if="view.operatorsLoading && !view.operators" height="2.5rem" />
                    <div v-else-if="view.operatorsError" class="flex flex-wrap items-center gap-2 text-xs">
                        <span role="alert" class="text-red-600 dark:text-red-400">{{ t('operatorMember.create.operatorsFailed') }} {{ view.operatorsError }}</span>
                        <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :loading="view.operatorsLoading" @click="emit('retry-operators')" />
                    </div>
                    <p v-else-if="noOperators" class="m-0 rounded-lg border border-(--line) px-3 py-2.5 text-xs leading-relaxed text-muted-color">{{ t('operatorMember.create.operatorsNone') }}</p>
                    <Select
                        v-else
                        v-model="operatorId"
                        :input-id="id"
                        :options="operatorOptions"
                        option-label="label"
                        option-value="value"
                        :placeholder="t('operatorMember.create.operatorPlaceholder')"
                        :filter="operatorOptions.length > 8"
                        :disabled="view.busy"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                    >
                        <template #option="{ option }">
                            <span class="flex min-w-0 flex-col">
                                <span class="truncate">{{ option.label }}</span>
                                <span class="truncate text-xs text-muted-color">{{ option.email }}</span>
                            </span>
                        </template>
                    </Select>
                </template>
            </LabeledField>

            <!-- 이메일 -->
            <div class="flex flex-col gap-1.5">
                <LabeledField :label="t('operatorMember.create.fieldEmail')" :help="t('operatorMember.create.emailHelp')" :error="emailError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="email" name="email" type="email" autocomplete="off" placeholder="name@example.com" :disabled="view.busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                    </template>
                </LabeledField>
                <div v-if="selectedOperator && email.trim().toLowerCase() !== selectedOperator.email.toLowerCase()">
                    <Button type="button" :label="t('operatorMember.create.useOperatorEmail', { email: selectedOperator.email })" icon="pi pi-at" size="small" severity="secondary" variant="text" class="-ml-2" :disabled="view.busy" @click="email = selectedOperator.email" />
                </div>
                <!-- 이미 가입한 이메일 — 새로 만들지 말고 운영자 서랍에서 그 회원을 검색해 연결하라는 안내(이메일이 운영자와 같든 다르든) -->
                <Message v-if="emailTakenHint" severity="secondary" size="small" variant="simple" :closable="false">
                    <span>{{ emailTakenHint }}</span>
                    <div v-if="showOperatorLink && selectedOperator" class="mt-1.5">
                        <Button type="button" :label="t('operatorMember.create.goLink')" icon="pi pi-link" size="small" severity="secondary" variant="outlined" @click="emit('open-operator', selectedOperator.operatorId)" />
                    </div>
                </Message>
            </div>

            <!-- 닉네임 -->
            <LabeledField :label="t('operatorMember.fieldNickname')" :hint="t('operatorMember.create.nicknameHint')" :help="t('operatorMember.create.nicknameHelp')" :error="nicknameError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="nickname" name="nickname" autocomplete="off" :disabled="view.busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                </template>
            </LabeledField>

            <!-- 비밀번호 -->
            <div class="flex flex-col gap-3">
                <div>
                    <span :id="modeLabelId" class="mb-1 block text-sm font-medium">{{ t('operatorMember.create.passwordModeLabel') }}</span>
                    <SelectButton
                        v-model="passwordMode"
                        :options="modeOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        :aria-labelledby="modeLabelId"
                        :aria-describedby="passwordModeError ? modeErrorId : undefined"
                        :invalid="!!passwordModeError"
                        :disabled="view.busy"
                    />
                    <p v-if="passwordModeError" :id="modeErrorId" role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ passwordModeError }}</p>
                </div>
                <NewPasswordFields
                    v-if="passwordMode === 'DIRECT'"
                    v-model:password="password"
                    v-model:confirm="confirm"
                    :label="t('operatorMember.create.passwordLabel')"
                    :password-error="passwordError"
                    :confirm-error="confirmError"
                    :disabled="view.busy"
                />
                <p v-else class="m-0 flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2.5 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-envelope mt-0.5" aria-hidden="true" />
                    <span>{{ t('operatorMember.create.passwordMailHint') }}</span>
                </p>
            </div>

            <!-- 만들면 일어나는 일 -->
            <ul class="m-0 flex list-none flex-col gap-1 rounded-lg bg-surface-50 px-3 py-2.5 text-xs leading-relaxed text-muted-color dark:bg-surface-900">
                <li class="flex items-start gap-2"><i class="pi pi-envelope mt-0.5" aria-hidden="true" /><span>{{ passwordMode === 'MAIL' ? t('operatorMember.create.noticeMailSetup') : t('operatorMember.create.noticeMailDirect') }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-link mt-0.5" aria-hidden="true" /><span>{{ t('operatorMember.create.noticeLink') }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-eye-slash mt-0.5" aria-hidden="true" /><span>{{ t('operatorMember.create.noticePrivate') }}</span></li>
            </ul>

            <Message v-if="view.error" severity="error" size="small" variant="simple" :closable="false">{{ view.error }}</Message>
        </form>

        <template #footer>
            <template v-if="view.result">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
                <Button v-if="showOperatorLink && submittedOperator" type="button" :label="t('operatorMember.create.openOperator')" icon="pi pi-id-card" severity="secondary" variant="outlined" @click="emit('open-operator', submittedOperator.operatorId)" />
                <Button v-if="canOpenMember" type="button" :label="t('operatorMember.openMember')" icon="pi pi-user" @click="emit('open-member', view.result.userId)" />
            </template>
            <template v-else>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="view.busy" @click="emit('update:visible', false)" />
                <Button type="submit" :form="formId" :label="t('operatorMember.create.submit')" icon="pi pi-user-plus" :loading="view.busy" :disabled="noOperators" />
            </template>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { OperatorMemberPasswordMode } from '~/composables/operator/api/useOperatorMemberAccountApi.types';
import type { OperatorMemberCreateOperator, OperatorMemberCreateSubmit, OperatorMemberCreateView } from '~/composables/operator/useOperatorMemberCreate.types';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilOperatorMemberMessages from '~/utils/util-operator-member-messages';
import UtilValidation from '~/utils/util-validation';

interface Props {
    visible: boolean;
    view: OperatorMemberCreateView;
    /** 결과 화면의 [회원 관리에서 보기] — 회원 조회 권한(`member.read`)이 있을 때 */
    canOpenMember?: boolean;
    /** [운영자 서랍 열기]·「운영자 서랍에서 연결」(이미 가입한 이메일일 때)을 보인다 — 운영자 서랍 밖(회원 관리)에서 열었을 때 */
    showOperatorLink?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canOpenMember: false, showOperatorLink: false });
const emit = defineEmits<{
    'update:visible': [visible: boolean];
    submit: [payload: OperatorMemberCreateSubmit];
    'retry-operators': [];
    'open-member': [userId: string];
    'open-operator': [operatorId: string];
}>();

const { t } = useI18n();
const formId = useId();
const modeLabelId = useId();
const modeErrorId = useId();

const operatorId = ref<string | null>(null);
const email = ref('');
const nickname = ref('');
const passwordMode = ref<OperatorMemberPasswordMode>('DIRECT');
const password = ref('');
const confirm = ref('');
const submitted = ref(false);
/** 보낸 운영자 — 결과 화면의 이름·[운영자 서랍 열기] */
const submittedOperatorId = ref<string | null>(null);
/** 서버 칸 오류를 숨길지 — 새 오류가 오면 모두 보이고, 칸을 고치면 그 칸만 숨긴다 */
const hidden = reactive<Record<string, boolean>>({});

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const modeOptions = computed(() => [
    { label: t('operatorMember.create.passwordDirect'), value: 'DIRECT' as const },
    { label: t('operatorMember.create.passwordMail'), value: 'MAIL' as const },
]);

const operatorOptions = computed(() =>
    (props.view.operators ?? []).map((o) => ({ value: o.operatorId, label: o.isSelf ? t('operatorMember.create.operatorSelf', { name: o.operatorName }) : o.operatorName, email: o.email })),
);
const noOperators = computed(() => !props.view.fixed && props.view.operators !== null && props.view.operators.length === 0);
const fixedOperator = computed<OperatorMemberCreateOperator | null>(() => (props.view.fixed ? (props.view.operators?.[0] ?? null) : null));
const selectedOperator = computed<OperatorMemberCreateOperator | null>(() => fixedOperator.value ?? props.view.operators?.find((o) => o.operatorId === operatorId.value) ?? null);
const submittedOperator = computed<OperatorMemberCreateOperator | null>(() => props.view.operators?.find((o) => o.operatorId === submittedOperatorId.value) ?? null);

/** 열 때마다 비운다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) {
            // 닫으면 비밀번호를 메모리에 남기지 않는다
            password.value = '';
            confirm.value = '';
            return;
        }
        operatorId.value = props.view.fixed ? (props.view.operators?.[0]?.operatorId ?? null) : props.view.defaultOperatorId;
        email.value = '';
        nickname.value = '';
        passwordMode.value = 'DIRECT';
        password.value = '';
        confirm.value = '';
        submitted.value = false;
        submittedOperatorId.value = null;
        for (const key of Object.keys(hidden)) delete hidden[key];
    },
    { immediate: true },
);
/** 운영자 목록이 늦게 오면(회원 관리에서 열 때) 기본 운영자를 골라 둔다 — 고른 운영자가 목록에서 빠졌으면(이미 이어짐) 다시 고른다 */
watch(
    () => props.view.operators,
    (list) => {
        if (props.view.fixed || !list) return;
        if (!list.some((o) => o.operatorId === operatorId.value)) operatorId.value = props.view.defaultOperatorId;
    },
);
/** 만들었으면 비밀번호를 비운다 */
watch(
    () => props.view.result,
    (result) => {
        if (!result) return;
        password.value = '';
        confirm.value = '';
    },
);
watch(
    () => props.view.fieldErrors,
    () => {
        for (const key of Object.keys(hidden)) delete hidden[key];
    },
);
watch(email, () => (hidden.email = true));
watch(nickname, () => (hidden.nickname = true));
watch(password, () => (hidden.password = true));
watch(passwordMode, () => {
    hidden.passwordMode = true;
    hidden.password = true;
});

function server(key: string): string {
    return hidden[key] ? '' : (props.view.fieldErrors[key] ?? '');
}

const operatorError = computed(() => (submitted.value && !selectedOperator.value && !noOperators.value ? t('operatorMember.create.operatorRequired') : ''));

const emailError = computed(() => {
    if (server('email')) return server('email');
    if (!submitted.value) return '';
    const problem = UtilValidation.email(email.value);
    return problem ? UtilOperatorMemberMessages.emailProblem(t, problem) : '';
});
/** 이미 가입한 이메일(`EMAIL_TAKEN`)이 칸에 보이는 동안의 안내 — 이미 있는 회원은 이메일이 달라도 「사이트 계정」 탭에서 검색해 이을 수 있다 */
const emailTakenHint = computed(() => (props.view.errorCode === 'EMAIL_TAKEN' && server('email') ? t('operatorMember.create.emailTaken') : ''));

const nicknameError = computed(() => {
    if (server('nickname')) return server('nickname');
    if (!submitted.value) return '';
    const problem = UtilValidation.nickname(nickname.value);
    return problem ? UtilOperatorMemberMessages.nicknameProblem(t, problem) : '';
});

/** 서버만 판정한다(`fields.passwordMode` — 화면은 두 값만 보낸다) */
const passwordModeError = computed(() => server('passwordMode'));

const passwordError = computed(() => {
    if (passwordMode.value !== 'DIRECT') return '';
    if (server('password')) return server('password');
    if (!submitted.value) return '';
    const problem = UtilValidation.passwordProblem(password.value);
    return problem ? UtilAccountMessages.passwordProblem(t, problem) : '';
});
const confirmError = computed(() => {
    if (passwordMode.value !== 'DIRECT' || !submitted.value) return '';
    if (!confirm.value) return t('operatorMember.create.confirmRequired');
    return confirm.value !== password.value ? t('account.confirmMismatch') : '';
});

function submit(): void {
    submitted.value = true;
    for (const key of Object.keys(hidden)) hidden[key] = true;
    const target = selectedOperator.value;
    if (!target || props.view.busy || emailError.value || nicknameError.value || passwordError.value || confirmError.value) return;
    submittedOperatorId.value = target.operatorId;
    emit('submit', {
        operatorId: target.operatorId,
        body: {
            email: email.value.trim().toLowerCase(),
            nickname: nickname.value,
            passwordMode: passwordMode.value,
            password: passwordMode.value === 'DIRECT' ? password.value : null,
        },
    });
}
</script>
