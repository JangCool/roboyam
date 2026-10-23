<template>
    <!--
        관리자 로그인 — ① 이메일·비밀번호(실제 서버) → (2차 인증이 켜져 있으면) 같은 카드 안에서 ② 단계로 바뀐다(`docs/domain/commerce.md` 13-3).
          · VERIFY: 6자리 코드(또는 복구 코드) + 「이 기기에서 N일 동안 다시 묻지 않기」. 틀리면 남은 횟수, 여러 번 틀리면 잠금 카운트다운, 대기(5분)가 지나면 ① 로 돌아가 안내.
          · GRACE: 미등록·유예 중 — 이미 로그인됐다. [지금 등록](→ ENROLL) / [나중에 하기](→ 가려던 곳).
          · ENROLL: 유예가 끝나 강제 등록(2차 대기 세션) — QR·확인 코드 → 복구 코드 → 로그인 완료.
        VERIFY·ENROLL 동안에는 로그인 상태(`user`)를 채우지 않는다 — 2단계가 끝난 응답을 `adopt` 한다. 「다른 계정으로 로그인」은 대기 세션을 버리고 ① 로.
        대기 중에 새로고침하거나(다른 화면이면 `/login?next=` 로 온다) 비밀번호 재설정이 대기로 끝나 오면, `me` 가 알려 준 단계(`useAdminAuth().pendingMfa`)로 이어 간다.
        등록 주소는 세션이 정한다 — ENROLL(대기 세션)은 강제 등록, GRACE 의 [지금 등록](이미 로그인)은 자발 등록.
    -->
    <h1 class="text-center text-lg font-semibold">{{ heading.title }}</h1>
    <p class="mb-4 mt-2 text-center text-sm text-muted-color">{{ heading.subtitle }}</p>

    <!-- 세션이 끝나 돌아온 경우(다른 화면에서 401 UNAUTHENTICATED) -->
    <Message v-if="step === 'password' && route.query.expired === '1' && !stageNotice" severity="warn" :closable="false" class="mb-4" size="small">
        <b>{{ t('login.expiredTitle') }}</b> {{ t('login.expiredBody') }}
    </Message>
    <!-- 2단계에서 처음으로 돌아온 이유(대기 만료 등) — 서버 문구 그대로 -->
    <Message v-if="step === 'password' && stageNotice" severity="warn" :closable="false" class="mb-4" size="small" role="status">{{ stageNotice }}</Message>

    <!-- 시도 제한: 잠금 안내 + 남은 시간(비밀번호 · 2차 인증 코드 모두) -->
    <template v-if="locked && (step === 'password' || step === 'verify')">
        <Message severity="warn" :closable="false" size="small">
            <b>{{ step === 'verify' ? t('mfa.lockedTitle') : t('login.lockedTitle') }}</b> {{ step === 'verify' ? t('mfa.lockedBody') : t('login.lockedBody') }}
        </Message>
        <div class="my-3 text-center">
            <small class="text-muted-color">{{ t('login.lockedUntil') }}</small>
            <p class="text-2xl font-semibold tabular-nums" role="timer" :aria-label="t('login.remainingTime', { time: UtilTime.mmss(lock.remaining.value) })">
                {{ UtilTime.mmss(lock.remaining.value) }}
            </p>
        </div>
    </template>
    <Message v-else-if="error && step === 'password'" severity="error" :closable="false" size="small">{{ error.message }}</Message>

    <!-- ① 이메일·비밀번호 -->
    <template v-if="step === 'password'">
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

    <!-- ② VERIFY — 코드 물음 -->
    <template v-else-if="step === 'verify'">
        <form class="mt-2 flex flex-col gap-4" novalidate @submit.prevent="submitVerify">
            <MfaCodeInput
                ref="codeInput"
                :key="useRecovery ? 'recovery' : 'otp'"
                v-model="code"
                :mode="useRecovery ? 'recovery' : 'otp'"
                :label="useRecovery ? t('mfa.recoveryCodeLabel') : t('mfa.otpLabel')"
                :help="useRecovery ? t('mfa.recoveryCodeHelp') : t('mfa.codeHelp')"
                :error="locked ? '' : codeError"
                :disabled="verifying || locked"
                @complete="submitVerify"
            />

            <div class="flex items-center gap-2">
                <Checkbox v-model="rememberDevice" :input-id="rememberId" binary :disabled="verifying || locked" />
                <label :for="rememberId" class="text-sm">{{ t('mfa.rememberDevice', { days: mfa?.trustDays ?? 30 }) }}</label>
            </div>

            <Button type="submit" fluid size="large" :label="verifying ? t('mfa.verifying') : t('mfa.verify')" :loading="verifying" :disabled="locked || !codeReady" />
        </form>

        <div class="mt-4 flex flex-col items-center gap-2 text-sm">
            <button type="button" class="text-primary hover:underline" :disabled="verifying" @click="toggleRecovery">
                {{ useRecovery ? t('mfa.useOtp') : t('mfa.useRecovery') }}
            </button>
            <button type="button" class="text-muted-color hover:underline" :disabled="verifying" @click="switchAccount">{{ t('mfa.otherAccount') }}</button>
        </div>
    </template>

    <!-- ② GRACE — 유예 중 등록 안내(이미 로그인됨) -->
    <template v-else-if="step === 'grace'">
        <Message severity="warn" :closable="false" size="small">
            <b>{{ t('mfa.graceTitle') }}</b>
            <span class="mt-0.5 block">{{ t('mfa.graceBody', { date: graceDate, days: graceDays }) }}</span>
        </Message>
        <div class="mt-4 flex flex-col gap-2">
            <Button type="button" fluid size="large" :label="t('mfa.enrollNow')" icon="pi pi-shield" @click="beginEnroll" />
            <Button type="button" fluid :label="t('mfa.later')" severity="secondary" variant="text" @click="goNext" />
        </div>
    </template>

    <!-- ② ENROLL — 등록(강제 또는 유예 중 자발) -->
    <template v-else>
        <Message v-if="forcedEnroll" severity="warn" :closable="false" size="small" class="mb-4">{{ t('mfa.forcedBody') }}</Message>
        <MfaEnrollPanel
            :enrollment="enroll.enrollment.value"
            :recovery-codes="enroll.recoveryCodes.value"
            :starting="enroll.starting.value"
            :start-error="enroll.startError.value"
            :confirming="enroll.confirming.value"
            :code-error="enroll.codeError.value"
            :lock-seconds="enroll.lock.remaining.value"
            :account="enroll.account.value || email"
            :finish-label="t('mfa.finishLogin')"
            fluid
            @confirm="confirmEnroll"
            @retry="beginEnroll"
            @finish="finishEnroll"
        />
        <div v-if="!enroll.recoveryCodes.value" class="mt-4 flex flex-col items-center gap-2 text-sm">
            <button v-if="!forcedEnroll" type="button" class="text-primary hover:underline" @click="goNext">{{ t('mfa.later') }}</button>
            <button type="button" class="text-muted-color hover:underline" @click="switchAccount">{{ t('mfa.otherAccount') }}</button>
        </div>
    </template>
</template>

<script setup lang="ts">
import type { AdminLoginMfa } from '~/composables/auth/api/useAdminAuthApi.types';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';
import UtilMfa from '~/utils/util-mfa';
import UtilMfaMessages from '~/utils/util-mfa-messages';
import UtilRedirect from '~/utils/util-redirect';
import UtilTime from '#layers/display-time/utils/util-time';
import UtilValidation from '~/utils/util-validation';

// public: 로그인 없이 볼 수 있는 화면(`auth.global` 이 건너뛴다) · guest: 이미 로그인했으면 `/`(또는 next)로 보낸다
definePageMeta({ layout: 'auth', middleware: 'guest', public: true });
const { t } = useI18n();
useSeoMeta({ title: () => t('login.seoTitle') });

const route = useRoute();
const { authenticate, adopt, logout, pendingMfa, clearPendingMfa } = useAdminAuth();
const mfaApi = useMfaApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();

/** 지금 단계 — ① 비밀번호 · ② 코드 물음 · 유예 안내 · 등록 */
type Step = 'password' | 'verify' | 'grace' | 'enroll';
const step = ref<Step>('password');
/** 로그인 응답의 2차 인증 단계(① 을 통과했을 때) */
const mfa = ref<AdminLoginMfa | null>(null);
/** ② 에서 ① 로 돌아온 이유(서버 문구) */
const stageNotice = ref('');
/** 등록 흐름 — ENROLL 은 2차 대기 세션의 강제 등록, GRACE 에서 [지금 등록]은 로그인 세션의 자발 등록 */
const enroll = useMfaEnrollment(() => (mfa.value?.stage === 'ENROLL' ? 'pending' : 'session'));

const email = ref('');
const password = ref('');
const submitting = ref(false);
const error = ref<ApiError | null>(null);
const fieldErrors = reactive<{ email?: string; password?: string }>({});

const emailInput = ref<{ $el: HTMLInputElement } | null>(null);
const passwordField = ref<{ focus: () => void } | null>(null);

/** 시도 제한 — 잠금이 풀리면 오류를 걷고 입력칸(비밀번호 · 코드)으로 돌아온다 */
const lock = useCountdown(undefined, () => {
    error.value = null;
    codeError.value = '';
    void nextTick(() => (step.value === 'verify' ? codeInput.value?.focus() : passwordField.value?.focus()));
});
const locked = computed(() => lock.active.value);
const busy = computed(() => submitting.value || locked.value);
/** 이메일·비밀번호 중 어느 쪽이 틀렸는지 알려 주지 않는다 — 둘 다 붉게만 */
const credentialsInvalid = computed(() => error.value?.code === 'INVALID_CREDENTIALS');

/** 카드 제목 — 단계마다 */
const heading = computed(() => {
    switch (step.value) {
        case 'verify':
            return { title: t('mfa.verifyTitle'), subtitle: useRecovery.value ? t('mfa.verifyRecoverySubtitle') : t('mfa.verifySubtitle') };
        case 'grace':
            return { title: t('mfa.graceHeading'), subtitle: t('mfa.graceSubtitle') };
        case 'enroll':
            return { title: t('mfa.enrollTitle'), subtitle: t('mfa.enrollSubtitle') };
        default:
            return { title: t('login.title'), subtitle: t('login.subtitle') };
    }
});

async function submit() {
    if (busy.value) return;
    error.value = null;
    stageNotice.value = '';
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
        const res = await authenticate({ email: email.value.trim(), password: password.value });
        password.value = '';
        if (!res.mfa) {
            // 2차 인증 없음 — 지금처럼 바로 들어간다. 원래 가려던 곳(`next`)이 있으면 그리로, 없으면 `/`
            adopt(res);
            await goNext();
            return;
        }
        mfa.value = res.mfa;
        submitting.value = false;
        if (res.mfa.stage === 'GRACE') {
            adopt(res);
            step.value = 'grace';
        } else if (res.mfa.stage === 'VERIFY') {
            enterVerify();
        } else {
            step.value = 'enroll';
            await beginEnroll();
        }
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

/** 2차 대기 중에 이 화면이 열렸으면(새로고침 · 비밀번호 재설정 뒤) 그 단계로 이어 간다 */
onMounted(() => {
    const pending = pendingMfa.value;
    if (!pending) return;
    mfa.value = pending;
    if (pending.stage === 'VERIFY') enterVerify();
    else void beginEnroll();
});

/** 원래 가려던 곳(`next`)으로 — 없으면 `/` */
async function goNext(): Promise<void> {
    await navigateTo(UtilRedirect.safeNext(route.query.next), { replace: true });
}

/** 처음(①)으로 — 대기 만료처럼 서버가 처음부터를 요구할 때. 이유(서버 문구)를 위에 남긴다 */
function backToStart(notice: string): void {
    resetStage();
    stageNotice.value = notice;
    void nextTick(() => passwordField.value?.focus());
}

function resetStage(): void {
    clearPendingMfa();
    step.value = 'password';
    mfa.value = null;
    code.value = '';
    codeError.value = '';
    useRecovery.value = false;
    rememberDevice.value = false;
    lock.stop();
    enroll.reset();
}

/** 「다른 계정으로 로그인」 — 대기 세션(VERIFY·강제 ENROLL)은 버리고, 이미 로그인한 경우(GRACE 에서 등록 중)는 로그아웃한다. 실패해도 ① 로 돌아간다 */
async function switchAccount(): Promise<void> {
    const loggedIn = mfa.value?.stage === 'GRACE';
    try {
        if (loggedIn) await logout();
        else await mfaApi.cancel();
    } catch {
        // 서버가 실패해도 화면은 처음으로 — 대기 세션은 5분 뒤 서버가 버린다
    }
    resetStage();
    stageNotice.value = '';
    email.value = '';
    password.value = '';
    void nextTick(() => emailInput.value?.$el.focus());
}

/* ── VERIFY ── */

const code = ref('');
const codeError = ref('');
const useRecovery = ref(false);
const rememberDevice = ref(false);
const verifying = ref(false);
const rememberId = useId();
const codeInput = ref<{ focus: () => void } | null>(null);
const codeReady = computed(() => (useRecovery.value ? UtilMfa.isRecoveryCode(code.value) : UtilMfa.isOtp(code.value)));

function enterVerify(): void {
    step.value = 'verify';
    code.value = '';
    codeError.value = '';
    void nextTick(() => codeInput.value?.focus());
}

function toggleRecovery(): void {
    useRecovery.value = !useRecovery.value;
    code.value = '';
    codeError.value = '';
    void nextTick(() => codeInput.value?.focus());
}

async function submitVerify(): Promise<void> {
    if (verifying.value || locked.value || !codeReady.value) return;
    verifying.value = true;
    codeError.value = '';
    try {
        const body = useRecovery.value ? { recoveryCode: code.value, rememberDevice: rememberDevice.value } : { code: code.value, rememberDevice: rememberDevice.value };
        const res = await mfaApi.verify(body);
        code.value = '';
        adopt(res);
        if (res.recoveryCodesLeft !== undefined) {
            const notice = UtilMfaMessages.recoveryLeft(t, res.recoveryCodesLeft);
            toast.show(notice.text, { life: notice.low ? 8000 : 5000 });
        }
        await goNext();
    } catch (e) {
        verifying.value = false;
        const failure = toApiError(e);
        code.value = '';
        if (failure.code === 'MFA_PENDING_EXPIRED') return backToStart(failure.message);
        if (failure.code === 'MFA_LOCKED') {
            lock.start(failure.retryAfterSeconds ?? UtilMfa.LOCK_MINUTES * 60);
            return;
        }
        codeError.value = UtilMfaMessages.codeError(t, failure);
        await nextTick();
        codeInput.value?.focus();
    }
}

/* ── GRACE · ENROLL ── */

/** 유예 마감 날짜·남은 날 */
const graceDate = computed(() => (mfa.value?.graceEndsAt ? time.value.date(mfa.value.graceEndsAt) : ''));
const graceDays = computed(() => UtilMfa.daysLeft(mfa.value?.graceEndsAt ?? null));
/** 유예가 끝나 등록해야만 들어갈 수 있다(대기 세션) */
const forcedEnroll = computed(() => mfa.value?.stage === 'ENROLL');

async function beginEnroll(): Promise<void> {
    step.value = 'enroll';
    const failure = await enroll.start();
    if (failure?.code === 'MFA_PENDING_EXPIRED') backToStart(failure.message);
}

async function confirmEnroll(value: string): Promise<void> {
    const failure = await enroll.confirm(value);
    if (failure?.code === 'MFA_PENDING_EXPIRED') backToStart(failure.message);
}

/** 복구 코드를 저장했다 — 강제 등록이면 서버가 준 로그인 결과를 받아들이고, 가려던 곳으로 */
async function finishEnroll(): Promise<void> {
    const me = enroll.me.value;
    if (me) adopt(me);
    enroll.reset();
    toast.success(t('mfa.enrolledToast'));
    await goNext();
}
</script>
