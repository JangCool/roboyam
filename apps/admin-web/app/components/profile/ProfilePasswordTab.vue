<template>
    <!--
        내 정보 > 비밀번호 — 현재 비밀번호·새 비밀번호·확인 + 규칙 체크 목록. 구획 하나이고 저장 단위도 하나라서 [되돌리기]·[바꾸기] 줄은 그 구획 맨 아래에 둔다(`forms.md` 2-4).
        성공하면 토스트 + 구획 안에 「다른 기기에서는 로그아웃돼요」 안내를 남긴다. 실패는 서버 문구를 그대로 — 현재 비밀번호가 틀리면(`INVALID_CURRENT_PASSWORD`) 그 칸 아래, 새 비밀번호 규칙 위반(`fields.newPassword`)은 새 비밀번호 칸 아래.
        입력한 비밀번호는 이 컴포넌트 메모리에만 있고 제출이 끝나면 비운다(`forms.md` 2-1).
    -->
    <form novalidate @submit.prevent="submit">
        <AppGadget :title="t('profile.passwordTitle')">
            <div class="flex flex-col gap-4">
                <p class="m-0 text-xs text-muted-color">{{ t('profile.passwordIntro') }}</p>

                <!-- 바꾼 뒤의 안내 — 다시 고치기 시작하면 걷는다 -->
                <Message v-if="changed" severity="success" :closable="false" size="small" role="status">
                    <b>{{ t('profile.passwordChanged') }}</b> {{ t('profile.passwordLogoutNote') }}
                </Message>
                <!-- 저장 실패(칸에 붙지 않는 오류) — 서버 문구 그대로 -->
                <Message v-if="formError" severity="error" :closable="false" size="small">{{ formError }}</Message>

                <div class="flex max-w-md flex-col gap-4">
                    <PasswordField ref="currentField" v-model="current" name="current-password" autocomplete="current-password" :label="t('account.currentPasswordLabel')" :disabled="saving" :error="fieldErrors.current" />
                    <NewPasswordFields ref="newPassword" v-model:password="password" v-model:confirm="confirm" :disabled="saving" :password-error="fieldErrors.password" :confirm-error="fieldErrors.confirm" />
                </div>

                <div class="flex flex-wrap items-center justify-end gap-2">
                    <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || !dirty" @click="reset" />
                    <Button type="submit" :label="t('profile.passwordChange')" icon="pi pi-check" :loading="saving" :disabled="!dirty" />
                </div>
            </div>
        </AppGadget>
    </form>
</template>

<script setup lang="ts">
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilValidation from '~/utils/util-validation';

const { t } = useI18n();
const account = useAdminAccount();
const toast = useAppToast();

// 입력한 비밀번호는 이 화면 메모리에만 둔다
const current = ref('');
const password = ref('');
const confirm = ref('');
const saving = ref(false);
const changed = ref(false);
const formError = ref('');
const fieldErrors = reactive<{ current?: string; password?: string; confirm?: string }>({});

const currentField = ref<{ focus: () => void } | null>(null);
const newPassword = ref<{ focusPassword: () => void; focusConfirm: () => void } | null>(null);

/** 입력한 것이 있는가 — [바꾸기]·[되돌리기]가 이것을 본다 */
const dirty = computed(() => current.value !== '' || password.value !== '' || confirm.value !== '');

// 고치기 시작하면 그 칸의 오류와 지난 성공 안내를 걷는다(비우는 것은 걷지 않는다 — 실패 뒤에 번호를 비워도 오류가 남아야 한다)
watch(current, (value) => {
    if (value) {
        fieldErrors.current = undefined;
        changed.value = false;
    }
});
watch(password, (value) => {
    if (value) {
        fieldErrors.password = undefined;
        changed.value = false;
    }
});
watch(confirm, (value) => {
    if (value) {
        fieldErrors.confirm = undefined;
        changed.value = false;
    }
});

function clearErrors() {
    formError.value = '';
    fieldErrors.current = undefined;
    fieldErrors.password = undefined;
    fieldErrors.confirm = undefined;
}

function clearInputs() {
    current.value = '';
    password.value = '';
    confirm.value = '';
}

function reset() {
    clearInputs();
    clearErrors();
}

async function submit() {
    if (saving.value || !dirty.value) return;
    clearErrors();

    // 화면이 미리 알 수 있는 문제 — 서버에 보내지 않고 그 칸에서 알린다(판정은 서버이고 서버가 거절하면 서버 문구를 그대로 보인다)
    if (!current.value) fieldErrors.current = t('profile.currentPasswordRequired');
    const passwordProblem = UtilValidation.passwordProblem(password.value);
    if (passwordProblem) fieldErrors.password = UtilAccountMessages.passwordProblem(t, passwordProblem);
    if (!confirm.value) fieldErrors.confirm = t('account.confirmRequired');
    else if (confirm.value !== password.value) fieldErrors.confirm = t('account.confirmMismatch');

    if (fieldErrors.current) return void currentField.value?.focus();
    if (fieldErrors.password) return void newPassword.value?.focusPassword();
    if (fieldErrors.confirm) return void newPassword.value?.focusConfirm();

    saving.value = true;
    try {
        await account.changePassword({ currentPassword: current.value, newPassword: password.value });
        clearInputs();
        changed.value = true;
        toast.success(t('profile.passwordChanged'));
    } catch (e) {
        const failure = account.asApiError(e);
        if (failure.code === 'INVALID_CURRENT_PASSWORD') {
            // 현재 비밀번호 칸의 문제다 — 서버 문구를 그대로 칸 아래에. 틀린 값은 비운다
            fieldErrors.current = failure.message;
            current.value = '';
            await nextTick();
            currentField.value?.focus();
        } else if (failure.code === 'VALIDATION' && (failure.fields?.currentPassword || failure.fields?.newPassword)) {
            fieldErrors.current = failure.fields.currentPassword;
            fieldErrors.password = failure.fields.newPassword;
            await nextTick();
            if (fieldErrors.current) currentField.value?.focus();
            else newPassword.value?.focusPassword();
        } else {
            formError.value = failure.message;
        }
    } finally {
        saving.value = false;
    }
}
</script>
