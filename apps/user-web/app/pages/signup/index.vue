<template>
    <div>
        <!-- 진행 표시 (이메일 가입 1~3단계) -->
        <div v-if="stepNo" class="mb-4">
            <div class="flex items-center justify-between text-xs text-muted-color">
                <span>{{ t('signup.stepCount', { step: stepNo, label: stepLabel }) }}</span>
                <span>{{ t('signup.emailSignup') }}</span>
            </div>
            <ProgressBar :value="(stepNo / 3) * 100" :show-value="false" :aria-label="t('signup.progressLabel')" style="height: 6px" class="mt-2" />
        </div>

        <button
            v-if="canGoBack"
            type="button"
            class="mb-2 -ml-1 inline-flex items-center gap-1 rounded px-1 py-0.5 text-sm text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary max-md:py-1.5"
            data-testid="signup-back"
            @click="back"
        >
            <i class="pi pi-angle-left" aria-hidden="true" /> {{ t('common.prev') }}
        </button>

        <h1 ref="heading" tabindex="-1" class="text-lg font-semibold">{{ title }}</h1>
        <p v-if="subtitle" class="mt-1 text-sm text-muted-color">{{ subtitle }}</p>

        <!-- 0. 방식 선택 (소셜 로그인이 하나라도 켜져 있을 때만) -->
        <div v-if="step === 'method'" class="mt-4 flex flex-col gap-3">
            <Button :label="t('signup.withEmail')" icon="pi pi-at" fluid size="large" data-testid="signup-with-email" @click="step = 'account'" />
            <SocialLoginButtons mode="signup" :divider="t('signup.socialDivider')" />
            <p class="text-center text-sm text-muted-color">
                {{ t('signup.haveAccount') }} <NuxtLink to="/login" class="text-primary hover:underline max-md:py-1.5">{{ t('common.login') }}</NuxtLink>
            </p>
            <p class="text-center text-xs text-muted-color">{{ t('signup.ageLead') }}</p>
        </div>

        <!-- 1/3 계정 정보 -->
        <form v-else-if="step === 'account'" class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="toNickname">
            <LabeledField
                :label="t('login.emailLabel')"
                :error="emailError"
                :success="emailState === 'available' ? t('signup.emailAvailable') : undefined"
                :help="emailHelp"
            >
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
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        data-testid="signup-email"
                        @blur="emailTouched = true"
                    />
                </template>
                <template v-if="emailState === 'taken' && !emailServerError" #error>
                    <i18n-t keypath="signup.emailTakenHelp" scope="global" tag="span">
                        <template #login>
                            <NuxtLink to="/login" class="underline">{{ t('common.login') }}</NuxtLink>
                        </template>
                        <template #forgot>
                            <NuxtLink to="/password/forgot" class="underline">{{ t('login.forgotPassword') }}</NuxtLink>
                        </template>
                    </i18n-t>
                </template>
            </LabeledField>

            <PasswordField v-model="password" autocomplete="new-password" :error="passwordError">
                <template #under><PasswordRules :password="password" class="mt-1.5" /></template>
            </PasswordField>

            <PasswordField
                v-model="passwordConfirm"
                :label="t('password.confirmLabel')"
                name="passwordConfirm"
                autocomplete="new-password"
                :error="confirmState === 'mismatch' ? t('password.mismatch') : undefined"
                :success="confirmState === 'match' ? t('password.match') : undefined"
            />

            <Button type="submit" :label="t('common.next')" fluid size="large" :disabled="!accountReady" data-testid="signup-account-next" />
        </form>

        <!-- 2/3 닉네임 -->
        <form v-else-if="step === 'nickname'" class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="toTerms">
            <LabeledField
                :label="t('signup.nicknameLabel')"
                :hint="t('signup.nicknameHint', { min: UtilValidation.NICKNAME_MIN, max: UtilValidation.NICKNAME_MAX })"
                :help="nicknameHelp"
                :success="nicknameSuccess"
                :error="nicknameError"
            >
                <template #aside>
                    <small class="text-xs text-muted-color" aria-hidden="true">{{ nickname.length }}/{{ UtilValidation.NICKNAME_MAX }}</small>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        v-model="nickname"
                        name="nickname"
                        autocomplete="nickname"
                        autocapitalize="off"
                        spellcheck="false"
                        :maxlength="UtilValidation.NICKNAME_MAX"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        data-testid="signup-nickname"
                        @beforeinput="onNicknameBeforeInput"
                        @input="onNicknameInput"
                        @blur="nicknameOverflow = false"
                    />
                </template>
            </LabeledField>

            <div class="rounded-lg bg-surface-100 px-3 py-2.5 text-sm dark:bg-surface-800">
                <small class="block text-xs text-muted-color">{{ t('signup.previewLabel') }}</small>
                <p class="mt-1 flex items-center gap-1.5">
                    <GradeBadge />
                    <span class="break-all">{{ nickname || t('signup.nicknameLabel') }}</span>
                </p>
            </div>

            <Message severity="secondary" :closable="false" size="small">{{ t('signup.nicknameNote') }}</Message>

            <Button type="submit" :label="t('common.next')" fluid size="large" :disabled="!nicknameReady" data-testid="signup-nickname-next" />
        </form>

        <!-- 3/3 약관 동의 -->
        <form v-else class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message v-if="error" severity="error" :closable="false" size="small" data-testid="signup-error">{{ error }}</Message>

            <TermsAgreement v-model="agreements" :errors="termsErrors" :disabled="submitting" />

            <Message severity="secondary" :closable="false" size="small">{{ t('signup.noRealName') }}</Message>

            <Button
                type="submit"
                :label="submitting ? t('signup.submitting') : t('signup.submitEmail')"
                fluid
                size="large"
                :loading="submitting"
                :disabled="!allAgreed"
                data-testid="signup-submit"
            />
        </form>

        <p v-if="step === 'account'" class="mt-4 text-center text-sm text-muted-color">
            {{ t('signup.haveAccount') }} <NuxtLink to="/login" class="text-primary hover:underline max-md:py-1.5">{{ t('common.login') }}</NuxtLink>
        </p>
    </div>
</template>

<script setup lang="ts">
import type { Agreements, EmailAvailability, NicknameAvailability } from '~/composables/auth/api/useAuthApi.types';
import UtilAccount from '~/utils/util-account';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilValidation from '~/utils/util-validation';

/**
 * 이메일 가입 `/signup` — 원본(community) `pages/signup/index.vue` 를 옮겼다. 한 페이지 안에서 단계가 바뀐다: (방식 선택) → 1/3 계정 정보 → 2/3 닉네임 → 3/3 약관 동의.
 * 소셜 로그인이 하나도 켜져 있지 않으면 방식 선택을 건너뛰고 이메일 가입부터 시작한다(`GET /api/auth/providers` — 실제).
 * 서버 규칙은 community `docs/api-auth.md` 「입력 규칙」과 같고 화면이 미리 알려 준다(판정은 서버). 약관은 소셜 가입과 같은 `TermsAgreement`.
 * 가입이 끝나면 인증 메일 안내(`/verify-email`)로 간다 — 재발송 60초 제한을 이어서 센다(`useEmailResend().markSent`).
 * 가입 성공(`201`)은 실제 로그인 세션을 만든다 — 응답 me 로 로그인 상태를 담는다(`useAuth.signup`).
 * community 대비 뺀 것: 사업자 전용 계정 가입 링크(로보얌에 사업자 가입 화면이 없다), "본인인증도 없어요" 문구(본인인증은 다른 세션 범위), 닉네임 30일 주기 문구(정책 미확정).
 */
definePageMeta({ layout: 'auth', middleware: 'guest' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('signup.title'), description: () => t('signup.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

type Step = 'method' | 'account' | 'nickname' | 'terms';

const STEP_NO: Partial<Record<Step, 1 | 2 | 3>> = { account: 1, nickname: 2, terms: 3 };

const api = useAuthApi();
const { signup } = useAuth();
const { markSent } = useEmailResend();
const { ensure: ensureProviders } = useSocialProviders();

const providers = await ensureProviders();
const hasSocial = providers.length > 0;

const step = ref<Step>(hasSocial ? 'method' : 'account');
const stepNo = computed(() => STEP_NO[step.value]);
const canGoBack = computed(() => step.value === 'nickname' || step.value === 'terms' || (step.value === 'account' && hasSocial));

const title = computed(() => {
    switch (step.value) {
        case 'method':
            return t('signup.methodTitle');
        case 'account':
            return t('signup.accountTitle');
        case 'nickname':
            return t('signup.nicknameTitle');
        default:
            return t('signup.termsTitle', { site: siteLabel.value });
    }
});
const subtitle = computed(() => {
    if (step.value === 'method') return t('signup.methodSubtitle');
    if (step.value === 'account') return t('signup.accountSubtitle');
    if (step.value === 'nickname') return t('signup.nicknameSubtitle');
    return '';
});
const stepLabel = computed(() => {
    if (step.value === 'account') return t('signup.stepAccount');
    if (step.value === 'nickname') return t('signup.stepNickname');
    return t('signup.stepTerms');
});

// 화면이 바뀌면 제목으로 초점을 옮겨 스크린리더가 새 단계를 읽게 한다
const heading = ref<HTMLElement | null>(null);
watch(step, async () => {
    await nextTick();
    heading.value?.focus({ preventScroll: true });
    window.scrollTo({ top: 0 });
});

function back() {
    step.value = step.value === 'terms' ? 'nickname' : step.value === 'nickname' ? 'account' : 'method';
}

// ── 1/3 계정 정보 ──────────────────────────────────────────────
const email = ref('');
const password = ref('');
const passwordConfirm = ref('');
const emailTouched = ref(false);
const emailServerError = ref('');
const passwordError = ref('');

const emailCheck = useDebouncedCheck<EmailAvailability>((value, signal) => api.emailAvailable(value, signal));

watch(email, (value) => {
    emailServerError.value = '';
    const trimmed = value.trim();
    if (!trimmed || UtilValidation.email(trimmed)) emailCheck.reset();
    else emailCheck.schedule(trimmed);
});
watch(password, () => (passwordError.value = ''));

const emailProblem = computed(() => (email.value ? UtilValidation.email(email.value.trim()) : null));
const emailState = computed<'idle' | 'invalid' | 'checking' | 'available' | 'taken' | 'error'>(() => {
    if (!email.value.trim()) return 'idle';
    if (emailProblem.value) return 'invalid';
    if (emailCheck.failed.value) return 'error';
    if (emailCheck.pending.value || !emailCheck.result.value) return 'checking';
    return emailCheck.result.value.available ? 'available' : 'taken';
});
const emailError = computed(() => {
    if (emailServerError.value) return emailServerError.value;
    if (emailState.value === 'taken') return t('signup.emailTaken'); // 링크가 붙은 문구는 #error 슬롯이 그린다
    if (emailState.value === 'invalid' && emailTouched.value && emailProblem.value) return UtilAuthMessages.emailProblem(t, emailProblem.value);
    return undefined;
});
const emailHelp = computed(() => {
    if (emailState.value === 'checking') return t('signup.checking');
    if (emailState.value === 'error') return t('signup.emailCheckFailed');
    return undefined;
});

const confirmState = computed(() => UtilAccount.confirmState(password.value, passwordConfirm.value));

// 확인 요청이 실패했어도(error) 넘어갈 수 있다 — 마지막 제출에서 서버가 한 번 더 확인한다
const accountReady = computed(
    () =>
        !emailProblem.value &&
        !!email.value.trim() &&
        !emailServerError.value &&
        (emailState.value === 'available' || emailState.value === 'error') &&
        UtilValidation.password(password.value).valid &&
        confirmState.value === 'match',
);

function toNickname() {
    if (accountReady.value) step.value = 'nickname';
}

// ── 2/3 닉네임 ─────────────────────────────────────────────────
const nickname = ref('');
const nicknameServerError = ref('');
const nicknameOverflow = ref(false);
const nicknameCheck = useDebouncedCheck<NicknameAvailability>((value, signal) => api.nicknameAvailable(value, signal));

watch(nickname, (value) => {
    nicknameServerError.value = '';
    if (UtilValidation.nickname(value)) nicknameCheck.reset();
    else nicknameCheck.schedule(value);
});

const nicknameProblem = computed(() => UtilValidation.nickname(nickname.value));
const nicknameState = computed<'idle' | 'invalid' | 'checking' | 'available' | 'unavailable' | 'error'>(() => {
    if (!nickname.value) return 'idle';
    if (nicknameProblem.value) return 'invalid';
    if (nicknameCheck.failed.value) return 'error';
    if (nicknameCheck.pending.value || !nicknameCheck.result.value) return 'checking';
    return nicknameCheck.result.value.available ? 'available' : 'unavailable';
});
const nicknameError = computed(() => {
    if (nicknameServerError.value) return nicknameServerError.value;
    if (nicknameState.value === 'invalid' && nicknameProblem.value) return UtilAuthMessages.nicknameProblem(t, nicknameProblem.value, nickname.value.length);
    if (nicknameState.value === 'unavailable') return UtilAuthMessages.nicknameReason(t, nicknameCheck.result.value?.reason);
    if (nicknameState.value === 'error') return t('signup.nicknameCheckFailed');
    return undefined;
});
// 12자 제한 안내(overflow)가 떠 있는 동안에는 그것이 먼저 보인다
const nicknameSuccess = computed(() =>
    nicknameState.value === 'available' && !nicknameServerError.value && !nicknameOverflow.value ? t('signup.nicknameAvailable') : undefined,
);
const nicknameHelp = computed(() => {
    if (nicknameOverflow.value) return t('signup.nicknameOverflow', { max: UtilValidation.NICKNAME_MAX });
    if (nicknameState.value === 'checking') return t('signup.checking');
    return t('signup.nicknameHelp');
});

// 확인 요청이 실패했어도(error) 넘어갈 수 있다 — 마지막 제출에서 서버가 한 번 더 확인한다
const nicknameReady = computed(() => !nicknameServerError.value && (nicknameState.value === 'available' || nicknameState.value === 'error'));

/** 이미 12자인데 더 넣으려 하면 알려 준다(브라우저가 `maxlength` 로 막기 전에 이벤트가 온다) */
function onNicknameBeforeInput(event: Event) {
    const input = event as InputEvent;
    if (input.inputType !== 'insertText' && input.inputType !== 'insertFromPaste') return;
    const el = input.target as HTMLInputElement;
    const selected = (el.selectionEnd ?? 0) - (el.selectionStart ?? 0);
    const incoming = input.data?.length ?? 1;
    if (el.value.length - selected + incoming > UtilValidation.NICKNAME_MAX) nicknameOverflow.value = true;
}

function onNicknameInput(event: Event) {
    if ((event.target as HTMLInputElement).value.length < UtilValidation.NICKNAME_MAX) nicknameOverflow.value = false;
}

function toTerms() {
    if (nicknameReady.value) step.value = 'terms';
}

// ── 3/3 약관 동의 + 제출 ────────────────────────────────────────
const agreements = ref<Agreements>({ agreeTerms: false, agreePrivacy: false, ageConfirmed: false });
const termsErrors = ref<Partial<Record<keyof Agreements, string>>>({});
const allAgreed = computed(() => agreements.value.agreeTerms && agreements.value.agreePrivacy && agreements.value.ageConfirmed);

const submitting = ref(false);
const error = ref('');

async function submit() {
    if (submitting.value || !allAgreed.value) return;
    submitting.value = true;
    error.value = '';
    termsErrors.value = {};
    try {
        await signup({ email: email.value.trim(), password: password.value, nickname: nickname.value, ...agreements.value });
        // 비밀번호는 화면 메모리에만 — 성공하면 비운다
        password.value = '';
        passwordConfirm.value = '';
        markSent(); // 가입 직후 인증 메일이 나갔다 — 재발송 쿨다운 60초를 이어서 센다
        await navigateTo('/verify-email', { replace: true });
    } catch (e) {
        submitting.value = false;
        showSubmitError(e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAuthMessages.fallback(t, 0, 'NETWORK') }));
    }
}

/** 서버가 거절한 까닭이 있는 단계로 되돌린다 — 문구는 서버가 준 것 그대로 */
function showSubmitError(e: ApiError) {
    if (e.code === 'EMAIL_TAKEN') {
        emailServerError.value = e.message;
        step.value = 'account';
    } else if (e.code === 'NICKNAME_TAKEN') {
        nicknameServerError.value = e.message;
        step.value = 'nickname';
    } else if (e.code === 'VALIDATION' && e.fields) {
        // 서버는 위반을 한꺼번에 준다 — 모든 칸에 붙여 두고 가장 앞 단계로 돌아간다(뒤 단계 칸의 문구는 그 칸을 고칠 때까지 남는다)
        const f = e.fields;
        emailServerError.value = f.email ?? '';
        passwordError.value = f.password ?? '';
        nicknameServerError.value = f.nickname ?? '';
        termsErrors.value = { agreeTerms: f.agreeTerms, agreePrivacy: f.agreePrivacy, ageConfirmed: f.ageConfirmed };
        if (f.email || f.password) step.value = 'account';
        else if (f.nickname) step.value = 'nickname';
        else error.value = e.message;
    } else {
        error.value = e.message;
    }
}
</script>
