<template>
    <h1 class="text-center text-lg font-semibold">{{ t('login.title') }}</h1>
    <p class="mb-4 mt-1 text-center text-sm text-muted-color">{{ t('login.subtitle') }}</p>

    <!-- 세션이 끝나 돌아온 경우(다른 화면에서 401 UNAUTHENTICATED) -->
    <Message v-if="route.query.expired === '1'" severity="warn" :closable="false" class="mb-4" size="small">
        <b>{{ t('login.expiredTitle') }}</b> {{ t('login.expiredBody') }}
    </Message>

    <!-- 시도 제한: 잠금 안내 + 남은 시간 -->
    <template v-if="locked">
        <Message severity="warn" :closable="false" size="small">
            <b>{{ t('login.lockedTitle') }}</b> {{ t('login.lockedBody') }}
        </Message>
        <div class="my-3 text-center">
            <small class="text-muted-color">{{ t('login.lockedUntil') }}</small>
            <p class="text-2xl font-semibold tabular-nums" role="timer" :aria-label="t('login.remainingTime', { time: UtilTime.mmss(lock.remaining.value) })">
                {{ UtilTime.mmss(lock.remaining.value) }}
            </p>
        </div>
    </template>
    <Message v-else-if="error" severity="error" :closable="false" size="small">{{ error.message }}</Message>

    <form class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="submit">
        <LabeledField :label="t('login.emailLabel')" :error="fieldErrors.email">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    ref="emailInput"
                    v-model="email"
                    type="email"
                    name="email"
                    autocomplete="username"
                    inputmode="email"
                    autocapitalize="off"
                    spellcheck="false"
                    placeholder="name@example.com"
                    :disabled="busy"
                    :invalid="invalid || credentialsInvalid"
                    :aria-describedby="describedBy"
                    fluid
                />
            </template>
        </LabeledField>

        <PasswordField
            ref="passwordField"
            v-model="password"
            autocomplete="current-password"
            :disabled="busy"
            :error="fieldErrors.password"
            :force-invalid="credentialsInvalid"
        />

        <p v-if="error?.code === 'INVALID_CREDENTIALS'" class="-mt-2 text-xs text-muted-color">
            {{ t('login.lockWarning') }}
        </p>

        <Button
            type="submit"
            fluid
            size="large"
            :label="locked ? t('login.lockedButton') : submitting ? t('login.submitting') : t('common.login')"
            :loading="submitting"
            :disabled="locked"
        />
        <p v-if="submitting" class="-mt-2 text-center text-xs text-muted-color" role="status">{{ t('login.submitWait') }}</p>
    </form>

    <p class="mt-4 text-center text-sm">
        <NuxtLink to="/forgot-password" class="text-primary hover:underline">{{ t('login.forgotLink') }}</NuxtLink>
    </p>
</template>

<script setup lang="ts">
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';
import UtilRedirect from '~/utils/util-redirect';
import UtilTime from '#layers/display-time/utils/util-time';
import UtilValidation from '~/utils/util-validation';

// public: 로그인 없이 볼 수 있는 화면(`auth.global` 이 건너뛴다) · guest: 이미 로그인했으면 `/`(또는 next)로 보낸다
definePageMeta({ layout: 'auth', middleware: 'guest', public: true });
const { t } = useI18n();
useSeoMeta({ title: () => t('login.seoTitle') });

const route = useRoute();
const { login } = useAdminAuth();

const email = ref('');
const password = ref('');
const submitting = ref(false);
const error = ref<ApiError | null>(null);
const fieldErrors = reactive<{ email?: string; password?: string }>({});

const emailInput = ref<{ $el: HTMLInputElement } | null>(null);
const passwordField = ref<{ focus: () => void } | null>(null);

/** 시도 제한 — 잠금이 풀리면 오류를 걷고 비밀번호칸으로 돌아온다 */
const lock = useCountdown(undefined, () => {
    error.value = null;
    void nextTick(() => passwordField.value?.focus());
});
const locked = computed(() => lock.active.value);
const busy = computed(() => submitting.value || locked.value);
/** 이메일·비밀번호 중 어느 쪽이 틀렸는지 알려 주지 않는다 — 둘 다 붉게만 */
const credentialsInvalid = computed(() => error.value?.code === 'INVALID_CREDENTIALS');

async function submit() {
    if (busy.value) return;
    error.value = null;
    fieldErrors.email = undefined;
    fieldErrors.password = undefined;

    const emailProblem = UtilValidation.email(email.value);
    if (emailProblem) {
        fieldErrors.email = UtilAdminAuthMessages.emailProblem(t, emailProblem);
        emailInput.value?.$el.focus();
        return;
    }
    if (!password.value) {
        fieldErrors.password = t('login.passwordRequired');
        passwordField.value?.focus();
        return;
    }

    submitting.value = true;
    try {
        await login({ email: email.value.trim(), password: password.value });
        // 성공하면 화면을 떠난다 — 버튼을 다시 열지 않는다. 원래 가려던 곳(`next`)이 있으면 그리로, 없으면 `/`
        password.value = '';
        await navigateTo(UtilRedirect.safeNext(route.query.next), { replace: true });
    } catch (e) {
        submitting.value = false;
        const failure = e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAdminAuthMessages.fallback(t, 0, 'NETWORK') });
        if (failure.code === 'LOGIN_LOCKED') {
            password.value = '';
            lock.start(failure.retryAfterSeconds ?? 600);
            error.value = failure;
        } else if (failure.code === 'VALIDATION' && failure.fields) {
            fieldErrors.email = failure.fields.email;
            fieldErrors.password = failure.fields.password;
            if (!fieldErrors.email && !fieldErrors.password) error.value = failure;
        } else {
            error.value = failure;
            await nextTick();
            passwordField.value?.focus();
        }
    }
}
</script>
