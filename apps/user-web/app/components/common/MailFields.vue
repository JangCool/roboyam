<template>
    <!-- 메일 서버 입력 — 서버 주소 · 보안 방식 · 포트 · 아이디 · 비밀번호 · 보내는 주소. 설치 마법사 4단계가 쓴다(운영자 설정 화면은 관리자 앱 `admin/` 에 같은 컴포넌트의 사본이 있다) -->
    <div class="flex flex-col gap-4">
        <LabeledField :label="t('mail.hostLabel')" :error="errors?.host">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.host"
                    type="text"
                    name="mailHost"
                    inputmode="url"
                    autocomplete="off"
                    autocapitalize="off"
                    spellcheck="false"
                    placeholder="smtp.example.com"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="mail-host"
                />
            </template>
        </LabeledField>

        <fieldset class="m-0 min-w-0 border-0 p-0" :aria-describedby="errors?.security ? securityErrorId : undefined" data-testid="mail-security">
            <legend class="mb-1.5 text-sm font-medium">{{ t('mail.securityLabel') }}</legend>
            <div class="flex flex-col gap-1.5 sm:flex-row sm:gap-4">
                <div v-for="option in UtilMail.SECURITIES" :key="option" class="flex items-center gap-2">
                    <RadioButton
                        :model-value="model.security"
                        :value="option"
                        :input-id="`${uid}-${option}`"
                        :name="`${uid}-security`"
                        :disabled="disabled"
                        :data-testid="`mail-security-${option}`"
                        @update:model-value="selectSecurity"
                    />
                    <label :for="`${uid}-${option}`" class="cursor-pointer text-sm">{{ UtilMailMessages.securityOption(t, option) }}</label>
                </div>
            </div>
            <p v-if="errors?.security" :id="securityErrorId" role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ errors.security }}</p>
        </fieldset>

        <LabeledField :label="t('mail.portLabel')" :hint="`${UtilMail.PORT_MIN}~${UtilMail.PORT_MAX}`" :error="errors?.port">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.port"
                    type="text"
                    name="mailPort"
                    inputmode="numeric"
                    autocomplete="off"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    class="w-32"
                    data-testid="mail-port"
                />
            </template>
        </LabeledField>

        <LabeledField :label="t('mail.usernameLabel')" :hint="t('mail.optionalHint')" :help="credentialsLocked ? undefined : t('mail.usernameHelp')" :error="errors?.username">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.username"
                    type="text"
                    name="mailUsername"
                    autocomplete="off"
                    autocapitalize="off"
                    spellcheck="false"
                    :disabled="disabled || credentialsLocked"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="mail-username"
                />
            </template>
        </LabeledField>

        <!-- 비밀번호는 서버가 주지 않는다 — 늘 빈 칸에서 시작하고, 저장에 성공하면 곧바로 비운다. 값을 보여 주는 칸 밖의 표시는 없다 -->
        <PasswordField
            v-model="model.password"
            :label="t('mail.passwordLabel')"
            :hint="t('mail.optionalHint')"
            name="mailPassword"
            autocomplete="off"
            :placeholder="UtilMailMessages.passwordPlaceholder(t, passwordRegistered)"
            :help="credentialsLocked ? undefined : UtilMailMessages.passwordHelp(t, passwordRegistered)"
            :error="errors?.password"
            :disabled="disabled || credentialsLocked"
        />

        <LabeledField :label="t('mail.fromLabel')" :hint="t('mail.fromHint')" :error="errors?.from">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.from"
                    type="email"
                    name="mailFrom"
                    inputmode="email"
                    autocomplete="off"
                    autocapitalize="off"
                    spellcheck="false"
                    placeholder="no-reply@example.com"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="mail-from"
                />
            </template>
        </LabeledField>
    </div>
</template>

<script setup lang="ts">
import type { MailFieldErrors } from '~/utils/util-mail';
import type { MailSecurity } from '~/composables/install/api/useInstallApi.types';
import UtilMail, { type MailForm } from '~/utils/util-mail';
import UtilMailMessages from '~/utils/util-mail-messages';

/**
 * 메일 서버 입력칸(`UtilMail` 규칙). 값은 부모의 객체를 그대로 고친다(`v-model`) — 서버는 부르지 않는다.
 *
 * - 보안 방식을 **사용자가 바꾸면** 포트가 비어 있거나 다른 방식의 기본 포트(25·587·465) 그대로일 때만 새 방식의 기본 포트로 바꾼다. 직접 적은 포트는 그대로 둔다.
 *   (값을 서버에서 받아 채울 때는 이 처리가 돌지 않는다 — 사용자 동작에만 반응한다.)
 * - 비밀번호는 `type=password` 이고 `autocomplete=off` — 브라우저·비밀번호 관리자가 계정 비밀번호로 오해해 저장하지 않게 한다.
 * - `credentialsLocked` 면 아이디·비밀번호 칸을 잠근다(서버에 암호화 키가 없어 저장할 수 없을 때).
 */
const model = defineModel<MailForm>({ required: true });

const { t } = useI18n();

interface Props {
    errors?: MailFieldErrors;
    disabled?: boolean;
    /** 아이디·비밀번호를 받을 수 없다(암호화 키 없음) */
    credentialsLocked?: boolean;
    /** 비밀번호가 이미 등록돼 있다(설정 화면) — 비워 두면 기존 값을 유지한다 */
    passwordRegistered?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    errors: () => ({}),
    disabled: false,
    credentialsLocked: false,
    passwordRegistered: false,
});

const uid = useId();
const securityErrorId = `${uid}-security-err`;

function selectSecurity(next: MailSecurity) {
    model.value.port = UtilMail.portAfterSecurityChange(model.value.port, next);
    model.value.security = next;
}
</script>
