<template>
    <!-- 발송 안내 — 가입 여부와 관계없이 항상 같은 문구 (계정이 있는지 알려 주지 않는다) -->
    <StatusPanel v-if="sent" icon="pi pi-envelope" :title="t('password.sentTitle')" data-testid="forgot-sent">
        {{ t('password.sentText') }}
        <template #actions>
            <Button
                type="button"
                variant="outlined"
                severity="secondary"
                fluid
                size="large"
                :loading="submitting"
                :disabled="cooling"
                :label="cooling ? t('password.resendCooling', { time: UtilTime.mss(remaining) }) : t('password.resend')"
                data-testid="forgot-resend"
                @click="send"
            />
            <p v-if="error" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ error }}</p>
            <NuxtLink to="/login" class="text-sm text-primary hover:underline max-md:py-1.5">{{ t('password.backToLogin') }}</NuxtLink>
            <small class="text-xs text-muted-color">{{ t('password.sameNotice') }}</small>
        </template>
    </StatusPanel>

    <template v-else>
        <h1 class="text-lg font-semibold">{{ t('password.forgotTitle') }}</h1>
        <p class="mt-1 text-sm text-muted-color">{{ t('password.forgotSubtitle') }}</p>

        <form class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="send">
            <Message v-if="error" severity="error" :closable="false" size="small" data-testid="forgot-error">{{ error }}</Message>

            <LabeledField :label="t('login.emailLabel')" :error="emailError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        v-model="email"
                        type="email"
                        name="email"
                        autocomplete="email"
                        inputmode="email"
                        autocapitalize="off"
                        spellcheck="false"
                        placeholder="name@example.com"
                        :disabled="submitting"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        data-testid="forgot-email"
                        @blur="touched = true"
                    />
                </template>
            </LabeledField>

            <Button
                type="submit"
                :label="submitting ? t('password.sending') : t('password.forgotSubmit')"
                fluid
                size="large"
                :loading="submitting"
                :disabled="Boolean(UtilValidation.email(email.trim()))"
                data-testid="forgot-submit"
            />
        </form>

        <p class="mt-4 text-center text-sm">
            <NuxtLink to="/login" class="text-primary hover:underline max-md:py-1.5">{{ t('password.backToLogin') }}</NuxtLink>
        </p>
        <p class="mt-3 text-center text-xs leading-relaxed text-muted-color">{{ t('password.socialNote') }}</p>
    </template>
</template>

<script setup lang="ts">
import UtilAccount from '~/utils/util-account';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilTime from '#layers/display-time/utils/util-time';
import UtilValidation from '~/utils/util-validation';

/**
 * 비밀번호 찾기 `/password/forgot` — 원본(community) `pages/password/forgot.vue` 를 옮겼다. 이메일 입력 → 발송 안내.
 * 서버는 가입 여부와 무관하게 항상 202 를 준다(형식 오류만 400) — 화면도 어떤 주소든 같은 안내를 보여 준다(계정이 있는지 알려 주지 않는다).
 * 같은 주소로 60초 안에 다시 요청하면 서버가 조용히 무시하므로 재발송 단추도 60초 쿨다운을 둔다.
 */
definePageMeta({ layout: 'auth', middleware: 'guest' });
const { t } = useI18n();
useSeoMeta({ title: () => t('login.forgotPassword'), description: () => t('password.forgotSeoDescription'), robots: 'noindex' });

const api = useAuthApi();

const email = ref('');
const touched = ref(false);
const submitting = ref(false);
const sent = ref(false);
const error = ref('');
const serverEmailError = ref('');

const cooldown = useCountdown();
const cooling = computed(() => cooldown.active.value);
const remaining = cooldown.remaining;

watch(email, () => (serverEmailError.value = ''));

const emailError = computed(() => {
    if (serverEmailError.value) return serverEmailError.value;
    const problem = UtilValidation.email(email.value.trim());
    return touched.value && email.value && problem ? UtilAuthMessages.emailProblem(t, problem) : undefined;
});

async function send() {
    if (submitting.value || cooling.value) return;
    const problem = UtilValidation.email(email.value.trim());
    if (problem) {
        touched.value = true;
        return;
    }
    submitting.value = true;
    error.value = '';
    try {
        await api.forgotPassword(email.value.trim());
        sent.value = true;
        cooldown.start(UtilAccount.DEFAULT_RESEND_SECONDS);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'VALIDATION' && e.field('email')) {
            serverEmailError.value = e.field('email') ?? '';
            sent.value = false;
        } else {
            error.value = UtilAccountMessages.of(t, e);
        }
    } finally {
        submitting.value = false;
    }
}
</script>
