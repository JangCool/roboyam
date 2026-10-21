<template>
    <!-- 소셜 가입·로그인이 화면 주소로 돌려보낸 결과: 이미 이메일로 가입한 주소 / 연결 실패·취소 -->
    <StatusPanel
        v-if="notice?.kind === 'email_exists'"
        icon="pi pi-info-circle"
        :title="t('login.emailExistsTitle')"
    >
        <p>{{ t('login.emailExistsBody', { provider: notice.provider, site: siteLabel }) }}</p>
        <Message severity="info" :closable="false" class="mt-3 text-left" size="small">
            <!-- 굵은 글씨가 문장 가운데 들어가므로 문장 하나를 키로 두고 굵은 부분만 자리(slot)로 넘긴다. 한국어 조사는 한국어 문장만 쓴다 -->
            <i18n-t keypath="login.emailExistsHint" scope="global" tag="span">
                <template #where>
                    <b>{{ t('login.emailExistsWhere') }}</b>
                </template>
                <template #provider>{{ notice.provider }}</template>
                <template #providerEul>{{ UtilProvider.params(notice.provider).providerEul }}</template>
                <template #providerRo>{{ UtilProvider.params(notice.provider).providerRo }}</template>
            </i18n-t>
        </Message>
        <template #actions>
            <Button :label="t('login.loginWithEmail')" fluid @click="dismissNotice" />
            <Button as="router-link" to="/signup" :label="t('login.signupOther')" variant="outlined" severity="secondary" fluid />
            <p class="text-xs text-muted-color">{{ t('login.noAutoMerge') }}</p>
        </template>
    </StatusPanel>

    <StatusPanel
        v-else-if="notice?.kind === 'social_failed'"
        icon="pi pi-times-circle"
        tone="error"
        :title="t('login.socialFailedTitle', { provider: notice.provider })"
    >
        {{ t('login.socialFailedBody') }}
        <template #actions>
            <SocialLoginButtons mode="continue" solid />
            <Button :label="t('login.loginWithEmail')" variant="outlined" severity="secondary" fluid @click="dismissNotice" />
            <NuxtLink to="/" class="mt-1 text-sm text-primary hover:underline max-md:py-1.5">{{ t('login.browseWithout') }}</NuxtLink>
        </template>
    </StatusPanel>

    <template v-else>
        <h1 class="mb-4 text-center text-lg font-semibold">{{ t('common.login') }}</h1>

        <!-- 다른 화면에서 세션이 끊겨 돌아온 경우 -->
        <Message v-if="route.query.expired === '1'" severity="warn" :closable="false" class="mb-4" size="small">
            <b>{{ t('login.expiredTitle') }}</b> {{ t('login.expiredBody') }}
        </Message>
        <Message v-if="notice?.kind === 'social_expired'" severity="warn" :closable="false" class="mb-4" size="small">
            {{ t('login.socialExpired') }}
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
                        autocomplete="email"
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

        <p v-if="locked" class="mt-3 text-center text-sm text-muted-color">
            {{ t('login.lockedHelpLead') }}<br />
            <i18n-t keypath="login.lockedHelpAction" scope="global" tag="span">
                <template #reset>
                    <NuxtLink to="/password/forgot" class="text-primary hover:underline max-md:py-1.5">{{ t('login.passwordReset') }}</NuxtLink>
                </template>
            </i18n-t>
        </p>

        <p class="mt-4 text-center text-sm text-muted-color">
            <!-- 글 안에 놓인 링크라 위아래 안쪽 여백(py-1.5)으로 눌리는 면만 키운다 — 글줄 높이는 그대로다(모바일 최소 32px, D-14) -->
            <NuxtLink to="/password/forgot" class="text-primary hover:underline max-md:py-1.5">{{ t('login.forgotPassword') }}</NuxtLink>
            <span aria-hidden="true"> · </span>
            <NuxtLink to="/signup" class="text-primary hover:underline max-md:py-1.5">{{ t('common.signup') }}</NuxtLink>
        </p>

        <div class="mt-5">
            <SocialLoginButtons mode="continue" :divider="t('login.socialDivider')" :disabled="busy" />
        </div>

        <p class="mt-5 text-center text-sm">
            <NuxtLink to="/" class="text-primary hover:underline max-md:py-1.5">{{ t('login.browseWithout') }}</NuxtLink>
            <br />
            <small class="text-muted-color">{{ t('login.guestNote') }}</small>
        </p>
    </template>
</template>

<script setup lang="ts">
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilProvider from '~/utils/util-provider';
import UtilSanction from '~/utils/util-sanction';
import UtilTime from '#layers/display-time/utils/util-time';
import UtilValidation from '~/utils/util-validation';

definePageMeta({ layout: 'auth', middleware: 'guest' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('common.login'), description: () => t('login.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const route = useRoute();
const { login, restriction } = useAuth();

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

/** `?error=` 로 돌아온 소셜 결과 (docs/api-auth.md 소셜 로그인 흐름) */
const notice = computed(() => {
    const code = route.query.error;
    const provider = UtilProvider.name(t, typeof route.query.provider === 'string' ? route.query.provider : null);
    if (code === 'email_exists') return { kind: 'email_exists' as const, provider };
    if (code === 'social_failed') return { kind: 'social_failed' as const, provider };
    // 화면이 붙이는 값 — 소셜 가입 정보(서버 세션)가 만료돼 /signup/social 에서 돌려보낸 경우
    if (code === 'social_expired') return { kind: 'social_expired' as const, provider };
    return null;
});

/** 안내를 닫고 이메일 로그인 폼으로 (주소의 error·provider 는 지운다, next 는 남긴다) */
async function dismissNotice() {
    const next = route.query.next;
    await navigateTo({ path: '/login', query: typeof next === 'string' ? { next } : undefined }, { replace: true });
}

async function submit() {
    if (busy.value) return;
    error.value = null;
    fieldErrors.email = undefined;
    fieldErrors.password = undefined;

    const emailProblem = UtilValidation.email(email.value);
    if (emailProblem) {
        fieldErrors.email = UtilAuthMessages.emailProblem(t, emailProblem);
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
        // 성공하면 화면을 떠난다 — 버튼을 다시 열지 않는다.
        // 정지·영구 정지 중이거나 확인하지 않은 경고가 있으면 제재 안내(`/sanction`)를 먼저 보이고, 원래 가려던 곳(`next`)은 그 화면이 이어서 보낸다(7-2)
        await navigateTo(UtilSanction.afterLoginPath(restriction.value, route.query.next), { replace: true });
    } catch (e) {
        submitting.value = false;
        const failure = e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAuthMessages.fallback(t, 0, 'NETWORK') });
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
