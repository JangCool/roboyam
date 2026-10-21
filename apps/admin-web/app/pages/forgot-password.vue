<template>
    <!--
        비밀번호 재설정 `/forgot-password` — 로그인 전 화면. hamonica3 의 비밀번호 찾기(`find-password.vue`)와 같은 **한 화면 두 걸음**이다:
          ① 이메일을 넣으면 승인번호(숫자 6자리, 10분)가 메일로 간다  ② 승인번호와 새 비밀번호를 **함께** 넣어 한 번에 보낸다(승인번호만 먼저 확인받는 단계가 없다).
        **가입 여부를 알려 주지 않는다** — 어떤 이메일이든 「보냈어요」라고만 말한다(서버도 같은 답). [승인번호 다시 받기]는 60초 카운트다운 뒤에 켜진다.
        성공하면 서버가 곧바로 로그인시킨다(200 + 로그인된 `me`) — 로그인 화면으로 보내지 않고 그 응답으로 로그인 상태를 갱신한 뒤 홈(`/`)으로 간다. 이미 로그인한 사람이 열어도 막지 않는다(`guest` 미들웨어 없음).
        칸에 붙지 않는 오류(`PASSWORD_RESET_NOT_ALLOWED` 403 · 서버 오류 · 이메일 형식 …)는 폼 위쪽에, 승인번호 오류(`CERT_CODE_*`)는 승인번호 칸 아래에 서버 문구 그대로 보인다.
    -->
    <h1 class="text-center text-lg font-semibold">{{ t('account.forgotTitle') }}</h1>
    <p class="mb-4 mt-2 text-center text-sm text-muted-color">{{ step === 'email' ? t('account.forgotEmailSubtitle') : t('account.forgotCodeSubtitle') }}</p>

    <!-- ① 이메일 -->
    <form v-if="step === 'email'" class="flex flex-col gap-4" novalidate @submit.prevent="onRequest">
        <Message v-if="error" severity="error" :closable="false" size="small">{{ error.message }}</Message>

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
                    :disabled="submitting"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                />
            </template>
        </LabeledField>

        <Button
            type="submit"
            fluid
            size="large"
            :label="submitting ? t('account.sending') : cooldown.active.value ? t('account.resendWait', { seconds: cooldown.remaining.value }) : t('account.sendCode')"
            :loading="submitting"
            :disabled="cooldown.active.value"
        />
        <p v-if="submitting" class="-mt-2 text-center text-xs text-muted-color" role="status">{{ t('login.submitWait') }}</p>
    </form>

    <!-- ② 승인번호 + 새 비밀번호 -->
    <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="onReset">
        <!-- 어디로 보냈는지 — 오타를 여기서 알아채는 경우가 흔하다 -->
        <div class="rounded-md bg-surface-100 px-3 py-2.5 text-xs leading-relaxed dark:bg-surface-800" role="status">
            <i18n-t keypath="account.codeSentTo" tag="span">
                <template #email>
                    <span class="break-all font-medium">{{ sentEmail }}</span>
                </template>
            </i18n-t>
            <br />
            {{ t('account.checkSpam') }}
        </div>

        <Message v-if="error" severity="error" :closable="false" size="small">{{ error.message }}</Message>

        <LabeledField :label="t('account.codeLabel')" :help="t('account.codeRule', { n: code.length, minutes: code.minutes })" :error="fieldErrors.certCode">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    ref="codeInput"
                    v-model="certCode"
                    name="certCode"
                    class="text-center text-lg tracking-[0.4em] tabular-nums"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    :maxlength="code.length"
                    placeholder="······"
                    :disabled="submitting"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                />
            </template>
        </LabeledField>

        <NewPasswordFields ref="newPassword" v-model:password="password" v-model:confirm="confirm" :disabled="submitting" :password-error="fieldErrors.password" :confirm-error="fieldErrors.confirm" />

        <Button type="submit" fluid size="large" :label="submitting ? t('account.resetting') : t('account.resetSubmit')" :loading="submitting" />
        <p v-if="submitting" class="-mt-2 text-center text-xs text-muted-color" role="status">{{ t('login.submitWait') }}</p>

        <div class="flex items-center justify-between gap-3 text-xs">
            <button type="button" class="cursor-pointer border-0 bg-transparent p-0 text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed" :disabled="submitting" @click="backToEmail">{{ t('account.reenter') }}</button>
            <button type="button" class="cursor-pointer border-0 bg-transparent p-0 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:text-muted-color disabled:no-underline" :disabled="submitting || cooldown.active.value" @click="onResend">
                {{ cooldown.active.value ? t('account.resendWait', { seconds: cooldown.remaining.value }) : t('account.resendCode') }}
            </button>
        </div>
    </form>

    <p class="mt-4 text-center text-sm">
        <NuxtLink to="/login" class="text-primary hover:underline">{{ t('account.backToLogin') }}</NuxtLink>
    </p>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import UtilAccount from '~/utils/util-account';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';
import UtilValidation from '~/utils/util-validation';

// public: 로그인 없이 볼 수 있는 화면(`auth.global` 이 건너뛴다). 로그인한 사람도 막지 않는다
definePageMeta({ layout: 'auth', public: true });
const { t } = useI18n();
useSeoMeta({ title: () => t('account.forgotSeoTitle') });

const account = useAdminAccount();
const { adopt } = useAdminAuth();
const toast = useAppToast();

const step = ref<'email' | 'reset'>('email');
const email = ref('');
/** 승인번호를 보낸 이메일 — ②단계의 모든 요청(다시 받기·재설정)이 이 값을 쓴다 */
const sentEmail = ref('');
/** 승인번호 규칙 — 서버가 요청 응답으로 알려 준다(기본값은 응답 전의 자리 채우기) */
const code = reactive({ length: UtilAccount.CERT_CODE_LENGTH, minutes: 10 });

// 입력한 값(승인번호·비밀번호)은 이 화면 메모리에만 둔다
const certCode = ref('');
const password = ref('');
const confirm = ref('');

const submitting = ref(false);
/** 칸에 붙지 않는 오류(서버 문구 그대로) */
const error = ref<ApiError | null>(null);
const fieldErrors = reactive<{ email?: string; certCode?: string; password?: string; confirm?: string }>({});

const emailInput = ref<{ $el: HTMLInputElement } | null>(null);
const codeInput = ref<{ $el: HTMLInputElement } | null>(null);
const newPassword = ref<{ focusPassword: () => void; focusConfirm: () => void } | null>(null);

/** [승인번호 다시 받기] 쿨다운 — 서버가 준 초(`cooldownSeconds`·429 의 `retryAfterSeconds`)를 센다 */
const cooldown = useCountdown();

watch(email, () => (fieldErrors.email = undefined));
// 승인번호를 코드로 비울 때(오류 뒤)는 오류를 남긴다 — 사용자가 고치기 시작할 때만 걷는다
watch(certCode, (value) => {
    if (value) fieldErrors.certCode = undefined;
});
watch(password, () => (fieldErrors.password = undefined));
watch(confirm, () => (fieldErrors.confirm = undefined));

function clearErrors() {
    error.value = null;
    fieldErrors.email = undefined;
    fieldErrors.certCode = undefined;
    fieldErrors.password = undefined;
    fieldErrors.confirm = undefined;
}

/** 승인번호 요청 한 번 — 성공하면 쿨다운을 시작한다. 실패는 `error` 로 둔다(429 는 남은 시간도 쿨다운으로 센다) */
async function sendCode(address: string): Promise<boolean> {
    submitting.value = true;
    try {
        const res = await account.requestPasswordResetCode(address);
        code.length = res.codeLength;
        code.minutes = res.expiresInMinutes;
        cooldown.start(res.cooldownSeconds);
        return true;
    } catch (e) {
        const failure = account.asApiError(e);
        if (failure.status === 429 && failure.retryAfterSeconds) cooldown.start(failure.retryAfterSeconds);
        error.value = failure;
        return false;
    } finally {
        submitting.value = false;
    }
}

/** ① 이메일을 보낸다 — 가입된 이메일인지와 무관하게 성공하면 늘 같은 안내 */
async function onRequest() {
    if (submitting.value || cooldown.active.value) return;
    clearErrors();

    const problem = UtilValidation.email(email.value);
    if (problem) {
        fieldErrors.email = UtilAdminAuthMessages.emailProblem(t, problem);
        emailInput.value?.$el.focus();
        return;
    }

    const address = email.value.trim();
    if (await sendCode(address)) {
        sentEmail.value = address;
        step.value = 'reset';
        toast.success(`${t('account.codeSent')}. ${t('account.codeSentIfExists')}`, 5000);
        await nextTick();
        codeInput.value?.$el.focus();
    } else {
        await nextTick();
        emailInput.value?.$el.focus();
    }
}

/** ② 승인번호와 새 비밀번호를 한 번에 보낸다 */
async function onReset() {
    if (submitting.value) return;
    clearErrors();

    // 화면이 미리 알 수 있는 문제 — 서버에 보내지 않고 그 칸에서 알린다(판정은 서버이고 서버가 거절하면 서버 문구를 그대로 보인다)
    if (!UtilAccount.isCertCode(certCode.value.trim(), code.length)) fieldErrors.certCode = t('account.codeDigits', { n: code.length });
    const passwordProblem = UtilValidation.passwordProblem(password.value);
    if (passwordProblem) fieldErrors.password = UtilAccountMessages.passwordProblem(t, passwordProblem);
    if (!confirm.value) fieldErrors.confirm = t('account.confirmRequired');
    else if (confirm.value !== password.value) fieldErrors.confirm = t('account.confirmMismatch');

    if (fieldErrors.certCode) return void codeInput.value?.$el.focus();
    if (fieldErrors.password) return void newPassword.value?.focusPassword();
    if (fieldErrors.confirm) return void newPassword.value?.focusConfirm();

    submitting.value = true;
    try {
        const me = await account.resetPassword({ email: sentEmail.value, certCode: certCode.value.trim(), newPassword: password.value });
        // 성공 — 입력한 값은 바로 비운다
        certCode.value = '';
        password.value = '';
        confirm.value = '';
        // 서버가 곧바로 로그인시켰다 — 그 응답으로 로그인 상태를 갱신하고(로그인 성공과 같은 처리) 홈으로. 토스트는 화면을 옮겨도 이어서 보인다
        adopt(me);
        toast.success(t('account.resetDone'), 6000);
        await navigateTo('/', { replace: true });
    } catch (e) {
        const failure = account.asApiError(e);
        if (UtilAccount.isCertCodeError(failure.code)) {
            // 승인번호 오류 — 서버 문구를 승인번호 칸 아래에. 더는 쓸 수 없는 번호면(만료·소진 …) 비우고 다시 받게 한다
            fieldErrors.certCode = failure.message;
            if (UtilAccount.needsNewCertCode(failure.code)) certCode.value = '';
            await nextTick();
            codeInput.value?.$el.focus();
        } else if (failure.code === 'VALIDATION' && (failure.fields?.certCode || failure.fields?.newPassword)) {
            // 승인번호 형식(숫자 6자리가 아님)은 CERT_CODE_INVALID 가 아니라 `fields.certCode` 로 온다
            fieldErrors.certCode = failure.fields.certCode;
            fieldErrors.password = failure.fields.newPassword;
            await nextTick();
            if (fieldErrors.certCode) codeInput.value?.$el.focus();
            else newPassword.value?.focusPassword();
        } else if (failure.code === 'VALIDATION' && failure.fields?.email) {
            // 이메일 칸은 ①단계에 있어 여기서 붙일 곳이 없다 — 서버 문구를 폼 위쪽에
            error.value = new ApiError({ status: failure.status, code: failure.code, message: failure.fields.email });
        } else {
            // 칸에 붙지 않는 오류 — PASSWORD_RESET_NOT_ALLOWED(403, 승인번호는 맞는데 계정이 막힘) · 서버 오류 · 네트워크 … 서버 문구 그대로 폼 위쪽에
            error.value = failure;
        }
    } finally {
        submitting.value = false;
    }
}

/** 승인번호 다시 받기 — 새 번호가 가고 먼저 받은 번호는 못 쓰므로 입력해 둔 번호는 비운다 */
async function onResend() {
    if (submitting.value || cooldown.active.value) return;
    clearErrors();
    if (await sendCode(sentEmail.value)) {
        certCode.value = '';
        toast.success(t('account.codeResent'));
        await nextTick();
        codeInput.value?.$el.focus();
    }
}

/** ← 다시 입력 — 이메일을 잘못 적었을 때 쓴다. 입력한 승인번호·비밀번호는 비운다. 쿨다운은 이전 이메일의 것이라 끈다(너무 잦은 요청은 서버가 429 로 막는다) */
async function backToEmail() {
    step.value = 'email';
    cooldown.stop();
    clearErrors();
    certCode.value = '';
    password.value = '';
    confirm.value = '';
    await nextTick();
    emailInput.value?.$el.focus();
}
</script>
