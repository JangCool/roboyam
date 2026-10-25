<template>
    <StatusPanel v-if="view === 'checking'" icon="pi pi-spin pi-spinner" :title="t('password.checkingTitle')" data-testid="reset-checking">{{ t('common.wait') }}</StatusPanel>

    <!-- 링크 만료 / 이미 쓴 링크 (같은 화면) -->
    <StatusPanel v-else-if="view === 'expired'" icon="pi pi-exclamation-circle" tone="warn" :title="t('password.expiredTitle')" data-testid="reset-expired">
        {{ t('password.expiredText') }}
        <template #actions>
            <Button as="router-link" to="/password/forgot" :label="t('password.getNewMail')" fluid size="large" />
            <NuxtLink to="/login" class="text-sm text-primary hover:underline max-md:py-1.5">{{ t('password.backToLogin') }}</NuxtLink>
            <small class="text-xs text-muted-color">{{ t('password.usedNote') }}</small>
        </template>
    </StatusPanel>

    <StatusPanel v-else-if="view === 'error'" icon="pi pi-exclamation-triangle" tone="error" :title="t('password.checkErrorTitle')" data-testid="reset-error">
        {{ failure }}
        <template #actions>
            <Button :label="t('common.retry')" fluid size="large" @click="check" />
        </template>
    </StatusPanel>

    <!-- 완료 -->
    <StatusPanel v-else-if="view === 'done'" icon="pi pi-check" tone="success" :title="t('password.doneTitle')" data-testid="reset-done">
        {{ t('password.doneText') }}
        <template #actions>
            <Button as="router-link" to="/login" :label="t('password.toLogin')" fluid size="large" />
        </template>
    </StatusPanel>

    <!-- 새 비밀번호 입력 -->
    <template v-else>
        <h1 class="text-lg font-semibold">{{ t('password.resetTitle') }}</h1>

        <form class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message v-if="error" severity="error" :closable="false" size="small" data-testid="reset-form-error">{{ error }}</Message>

            <PasswordField
                v-model="password"
                :label="t('password.newLabel')"
                name="newPassword"
                autocomplete="new-password"
                :disabled="submitting"
                :error="passwordError"
            >
                <template #under><PasswordRules :password="password" class="mt-1.5" /></template>
            </PasswordField>

            <PasswordField
                v-model="passwordConfirm"
                :label="t('password.newConfirmLabel')"
                name="newPasswordConfirm"
                autocomplete="new-password"
                :disabled="submitting"
                :error="confirmState === 'mismatch' ? t('password.mismatch') : undefined"
                :success="confirmState === 'match' ? t('password.match') : undefined"
            />

            <Button
                type="submit"
                :label="submitting ? t('password.changing') : t('password.submit')"
                fluid
                size="large"
                :loading="submitting"
                :disabled="!ready"
                data-testid="reset-submit"
            />
        </form>
    </template>
</template>

<script setup lang="ts">
import UtilAccount from '~/utils/util-account';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilValidation from '~/utils/util-validation';

/**
 * 비밀번호 재설정 `/password/reset?token=` — 원본(community) `pages/password/reset.vue` 를 옮겼다. 메일 링크가 아직 유효한지 먼저 확인하고(`reset/check`), 새 비밀번호를 받는다.
 * 성공하면 그 계정의 **모든 세션이 끝난다**(다른 기기 로그아웃) — 이 브라우저의 로그인 상태도 새로 받는다. 만료·이미 쓴 링크는 같은 안내를 보여 준다. 토큰이 없으면 만료와 같다.
 */
definePageMeta({ layout: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('password.resetSeoTitle'), description: () => t('password.resetSeoDescription'), robots: 'noindex' });

type View = 'checking' | 'form' | 'done' | 'expired' | 'error';

const route = useRoute();
const api = useAuthApi();
const { refresh } = useAuth();

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''));

const view = ref<View>('checking');
const failure = ref('');

/* ── 입력칸 값 — 비밀번호는 여기에만 있다 ── */
const password = ref('');
const passwordConfirm = ref('');
const passwordError = ref('');
const error = ref('');
const submitting = ref(false);

watch(password, () => (passwordError.value = ''));

const confirmState = computed(() => UtilAccount.confirmState(password.value, passwordConfirm.value));
const ready = computed(() => UtilValidation.password(password.value).valid && confirmState.value === 'match' && !passwordError.value);

async function check() {
    if (!token.value) {
        view.value = 'expired';
        return;
    }
    view.value = 'checking';
    try {
        await api.checkResetToken(token.value);
        view.value = 'form';
    } catch (e) {
        if (e instanceof ApiError && (e.code === 'TOKEN_EXPIRED' || e.code === 'TOKEN_INVALID')) {
            view.value = 'expired';
        } else {
            failure.value = UtilAccountMessages.of(t, e);
            view.value = 'error';
        }
    }
}

async function submit() {
    if (submitting.value || !ready.value) return;
    submitting.value = true;
    error.value = '';
    passwordError.value = '';
    try {
        await api.resetPassword({ token: token.value, newPassword: password.value });
        password.value = '';
        passwordConfirm.value = '';
        try {
            await refresh(); // 이 브라우저의 세션도 끝났다 — 로그인 상태를 비운다
        } catch {
            /* 다음에 다시 받는다 */
        }
        view.value = 'done';
    } catch (e) {
        if (e instanceof ApiError && (e.code === 'TOKEN_EXPIRED' || e.code === 'TOKEN_INVALID')) {
            view.value = 'expired';
        } else if (e instanceof ApiError && e.code === 'SAME_PASSWORD') {
            passwordError.value = e.message;
        } else if (e instanceof ApiError && e.code === 'VALIDATION' && e.field('newPassword')) {
            passwordError.value = e.field('newPassword') ?? '';
        } else {
            error.value = UtilAccountMessages.of(t, e);
        }
    } finally {
        submitting.value = false;
    }
}

onMounted(check);
</script>
