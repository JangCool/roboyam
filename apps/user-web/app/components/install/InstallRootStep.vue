<template>
    <div class="flex flex-col gap-4">
        <LabeledField :label="t('install.emailLabel')" :error="fieldErrors.email">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.email"
                    type="email"
                    name="rootEmail"
                    autocomplete="email"
                    inputmode="email"
                    autocapitalize="off"
                    spellcheck="false"
                    placeholder="admin@example.com"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="root-email"
                />
            </template>
        </LabeledField>

        <LabeledField
            :label="t('install.nicknameLabel')"
            :hint="t('install.nicknameHint', { min: UtilValidation.NICKNAME_MIN, max: UtilValidation.NICKNAME_MAX })"
            :help="t('install.nicknameHelp')"
            :error="fieldErrors.nickname"
        >
            <template #aside>
                <small class="text-xs text-muted-color" aria-hidden="true">{{ model.nickname.length }}/{{ UtilValidation.NICKNAME_MAX }}</small>
            </template>
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.nickname"
                    type="text"
                    name="rootNickname"
                    autocomplete="nickname"
                    autocapitalize="off"
                    spellcheck="false"
                    :maxlength="UtilValidation.NICKNAME_MAX"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="root-nickname"
                />
            </template>
        </LabeledField>

        <PasswordField v-model="model.password" :label="t('install.passwordLabel')" name="rootPassword" autocomplete="new-password" :error="fieldErrors.password" :disabled="disabled">
            <template #under><PasswordRules :password="model.password" class="mt-1.5" /></template>
        </PasswordField>

        <PasswordField
            v-model="model.passwordConfirm"
            :label="t('install.passwordConfirmLabel')"
            name="rootPasswordConfirm"
            autocomplete="new-password"
            :error="confirmError"
            :success="confirmState === 'match' ? t('install.passwordMatch') : undefined"
            :disabled="disabled"
        />
    </div>
</template>

<script setup lang="ts">
import UtilInstall, { type InstallFieldErrors, type RootForm } from '~/utils/util-install';
import UtilValidation from '~/utils/util-validation';

/**
 * 2단계 — 루트 계정(첫 운영자): 이메일·닉네임·비밀번호·비밀번호 확인. 규칙은 `docs/api-auth.md` 의 "입력 규칙"과 같다(`UtilValidation`).
 * 닉네임은 이름이 겹치는지·예약어인지 **묻지 않는다**(설치 전에는 `nickname-available` 이 막혀 있고, 운영자 같은 이름을 쓸 수 있다) — 중복은 설치할 때 서버가 본다.
 * 값은 부모의 객체를 그대로 고친다(`v-model`) — 서버는 부르지 않는다. 비밀번호는 이 화면 밖으로 나가지 않는다.
 */
const model = defineModel<RootForm>({ required: true });

const { t } = useI18n();

interface Props {
    /** 칸 경로(`root.email` …) → 오류 문구 */
    errors: InstallFieldErrors;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    disabled: false,
});

/** 이 단계 칸의 문구 — 칸 이름(`email`·`nickname`·`password`·`passwordConfirm`) → 문구 */
const fieldErrors = computed(() => UtilInstall.sliceErrors(props.errors, 'root'));

/** 확인 칸은 입력하는 동안 같은지 바로 알려 준다(가입 화면과 같다) */
const confirmState = computed<'idle' | 'match' | 'mismatch'>(() => {
    if (!model.value.passwordConfirm) return 'idle';
    return model.value.passwordConfirm === model.value.password ? 'match' : 'mismatch';
});
const confirmError = computed(() => fieldErrors.value.passwordConfirm ?? (confirmState.value === 'mismatch' ? t('install.passwordMismatch') : undefined));
</script>
