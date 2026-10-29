<template>
    <!-- 설정 묶음(`pages/settings.vue`) 안의 한 화면 — 넓은 화면은 왼쪽 메뉴, 좁은 화면은 위에 「‹ 계정·로그인 연결」 -->
    <div>
        <!-- 결과 화면(보냈어요·소셜 가입자)은 제목(h1)을 StatusPanel 이 그린다 -->
        <h1 v-if="view === 'loading' || view === 'error' || view === 'form'" class="mb-4 text-lg font-bold">{{ t('account.emailChange') }}</h1>

        <!-- 로딩 -->
        <div v-if="view === 'loading'" class="panel-card p-4" role="status" :aria-label="t('account.accountLoading')" data-testid="email-loading">
            <div class="space-y-4">
                <div v-for="n in 3" :key="n" class="h-10 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>

        <!-- 불러오기 실패 -->
        <ErrorState v-else-if="view === 'error'" :title="t('account.accountLoadFailed')" :description="loadError" retry data-testid="email-error" @retry="load" />

        <!-- 소셜 가입자 — 이메일을 바꿀 수 없다 -->
        <div v-else-if="view === 'social'" class="panel-card p-4">
            <StatusPanel icon="pi pi-info-circle" :title="t('password.socialAccountTitle')" data-testid="email-social">
                {{ t('account.noEmailChangeReason') }}.
                <template #actions>
                    <Button as="router-link" to="/settings/account" :label="t('account.backToAccount')" variant="outlined" severity="secondary" fluid size="large" />
                </template>
            </StatusPanel>
        </div>

        <!-- 인증 메일을 보냈다 — 링크를 누를 때까지 지금 이메일이 그대로다. 다시 보내려면 비밀번호를 한 번 더 받는다 -->
        <div v-else-if="view === 'sent'" class="panel-card p-4">
            <StatusPanel icon="pi pi-envelope" :title="t('account.sentTitle')" data-testid="email-sent">
                <b class="break-all text-color" data-testid="email-sent-to">{{ sentTo }}</b>
                <br />
                {{ t('account.sentText') }}
                <template #actions>
                    <form class="flex flex-col gap-3 text-left" novalidate :aria-label="t('account.emailResend')" @submit.prevent="submit">
                        <!-- 다시 보내지 못했다 -->
                        <Message v-if="formError" severity="error" :closable="false" size="small" data-testid="email-form-error">
                            <b>{{ t('account.sendFailed') }}</b> {{ formError }}
                        </Message>
                        <PasswordField
                            ref="passwordField"
                            v-model="password"
                            :label="t('password.currentLabel')"
                            name="password"
                            autocomplete="current-password"
                            :help="t('account.resendPasswordHelp')"
                            :disabled="busy"
                            :error="passwordError"
                        />
                        <Button type="submit" variant="outlined" severity="secondary" fluid size="large" :label="submitLabel" :loading="submitting" :disabled="!canSubmit" data-testid="email-resend" />
                    </form>
                    <small class="text-xs leading-relaxed text-muted-color">{{ t('account.checkSpam') }} {{ t('account.wrongEmailNote') }}</small>
                    <Button type="button" :label="t('account.retype')" variant="text" fluid data-testid="email-retype" @click="retype" />
                    <Button as="router-link" to="/settings/account" :label="t('account.backToAccount')" variant="text" severity="secondary" fluid />
                </template>
            </StatusPanel>
        </div>

        <!-- 입력 -->
        <form v-else class="flex flex-col gap-4 panel-card p-4" novalidate :aria-label="t('account.emailChange')" @submit.prevent="submit">
            <!-- 이미 보낸 변경 요청이 있다 — 새로 보내면 이전 링크는 쓸 수 없다 -->
            <Message v-if="account?.pendingEmail" severity="info" :closable="false" size="small" data-testid="email-pending">
                {{ t('account.pendingNote', { email: account.pendingEmail }) }}
            </Message>
            <!-- 보내지 못했다 — 입력한 내용은 그대로 남아 있다 -->
            <Message v-if="formError" severity="error" :closable="false" size="small" data-testid="email-form-error">
                <b>{{ t('account.sendFailed') }}</b> {{ formError }}
            </Message>

            <LabeledField :label="t('account.currentEmail')">
                <template #default="{ id }">
                    <InputText :id="id" :model-value="account?.email ?? ''" name="currentEmail" readonly autocomplete="off" fluid class="opacity-70" data-testid="email-current" />
                </template>
            </LabeledField>

            <LabeledField :label="t('account.newEmail')" :help="t('account.newEmailHelp')" :error="emailError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        ref="emailInput"
                        v-model="newEmail"
                        type="email"
                        name="newEmail"
                        autocomplete="off"
                        inputmode="email"
                        autocapitalize="off"
                        spellcheck="false"
                        placeholder="name@example.com"
                        :disabled="busy"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        data-testid="email-new"
                        fluid
                    />
                </template>
            </LabeledField>

            <!-- 본인 확인 — 서버 계약이 요구한다(community docs/api-account.md). 왜 묻는지 함께 적는다 -->
            <PasswordField
                ref="passwordField"
                v-model="password"
                :label="t('password.currentLabel')"
                name="password"
                autocomplete="current-password"
                :help="t('account.passwordHelp')"
                :disabled="busy"
                :error="passwordError"
            />

            <Button type="submit" fluid size="large" :label="submitLabel" :loading="submitting" :disabled="!canSubmit" data-testid="email-submit" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { AccountView } from '~/composables/account/api/useAccountApi.types';
import UtilAccount from '~/utils/util-account';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilTime from '#layers/display-time/utils/util-time';
import UtilValidation from '~/utils/util-validation';

/**
 * 이메일 변경 `/settings/account/email` — 원본(community) `pages/settings/account/email.vue` 를 설정 묶음 안으로 옮겼다. 로그인 필요(`auth` 미들웨어, 서버도 401).
 *
 * 현재 이메일(읽기 전용) · 새 이메일(형식은 `UtilValidation` 으로 미리 검사) · 현재 비밀번호(본인 확인) → 「인증 메일 보내기」.
 * `202` 면 결과 화면: 새 주소로 인증 메일을 보냈고, **링크를 누를 때까지 지금 이메일이 그대로**다. 60초 동안은 다시 보낼 수 없다(`useCountdown`).
 * 다시 보내는 것은 같은 요청을 한 번 더 보내는 것이라 **비밀번호를 다시 받는다** — 비밀번호는 입력칸 값(이 화면의 `password` 하나)에만 있고 성공·잠금 때 비운다.
 * 오류는 서버 문구를 그대로 쓰고 코드로 갈라 자리에 붙인다 —
 *  - `400 VALIDATION`(`fields.newEmail`)·`409 EMAIL_TAKEN` → 새 이메일 칸 아래 · `401 CURRENT_PASSWORD_WRONG`·`429 LOGIN_LOCKED` → 비밀번호 칸 아래(잠금 동안은 입력·단추 잠금)
 *  - `429 RESEND_COOLDOWN` → 남은 시간을 단추에 · `409 EMAIL_CHANGE_NOT_ALLOWED`(소셜 가입자) → 폼 대신 안내 · 그 밖의 실패 → 「인증 메일을 보내지 못했어요」 + 「다시 시도」
 * 변경 대기 이메일(`pendingEmail`)은 서버가 이미 가린 값(`m***@…`)이라 그대로 보인다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('account.emailChange'), description: () => t('account.emailSeoDescription', { site: siteLabel.value }), robots: 'noindex' });

const api = useAccountApi();

type View = 'loading' | 'error' | 'form' | 'social' | 'sent';
const view = ref<View>('loading');
const loadError = ref('');
const account = ref<AccountView | null>(null);

/* ── 입력칸 값 — 비밀번호는 여기에만 있다 ── */
const newEmail = ref('');
const password = ref('');
/** 인증 메일을 보낸 새 주소(결과 화면에 보이고, 다시 보낼 때 쓴다) */
const sentTo = ref('');

const submitting = ref(false);
/** 새 이메일 칸 아래 · 비밀번호 칸 아래 · 칸에 못 붙이는 오류(발송 실패). 그 칸을 다시 고치면 지운다 */
const emailError = ref('');
const passwordError = ref('');
const formError = ref('');

const emailInput = ref<{ $el: HTMLInputElement } | null>(null);
const passwordField = ref<{ focus: () => void } | null>(null);

/** 인증 메일을 다시 보낼 수 있을 때까지(60초) */
const cooldown = useCountdown();
/** 비밀번호를 여러 번 틀려 잠겼다 — 잠금이 풀리면 오류를 걷고 비밀번호 칸으로 돌아온다 */
const lock = useCountdown(undefined, () => {
    passwordError.value = '';
    void nextTick(() => passwordField.value?.focus());
});
const cooling = computed(() => cooldown.active.value);
const locked = computed(() => lock.active.value);
const busy = computed(() => submitting.value || locked.value);
// 비밀번호가 틀렸다는 답을 받은 뒤에는 칸을 고칠 때까지 막는다 — 같은 값으로 다시 눌러 틀린 횟수(잠금)만 늘리지 않게
const canSubmit = computed(() => (view.value === 'form' || view.value === 'sent') && !submitting.value && !cooling.value && !locked.value && !passwordError.value);

const submitLabel = computed(() => {
    if (submitting.value) return t('account.sending');
    if (cooling.value) return t('account.resendWait', { time: UtilTime.mss(cooldown.remaining.value) });
    if (formError.value) return t('common.retry');
    return view.value === 'sent' ? t('account.emailResend') : t('account.emailSend');
});

watch(newEmail, () => {
    emailError.value = '';
    formError.value = '';
});
watch(password, () => {
    passwordError.value = '';
    formError.value = '';
});

let loadSeq = 0;
/** 비밀번호가 있는 계정인지(`hasPassword`)와 현재 이메일·변경 대기 중인 이메일을 먼저 안다 — 소셜 가입자에게는 폼 대신 안내를 보인다 */
async function load() {
    const mine = ++loadSeq;
    view.value = 'loading';
    loadError.value = '';
    try {
        const value = await api.account();
        if (mine !== loadSeq) return;
        account.value = value;
        view.value = value.hasPassword ? 'form' : 'social';
    } catch (e) {
        if (mine !== loadSeq) return;
        loadError.value = UtilAccountMessages.of(t, e);
        view.value = 'error';
    }
}
void load();
onBeforeUnmount(() => loadSeq++);

/** 변경 대기 중인 이메일만 새로 받는다 — 방금 보낸 요청이 입력 화면의 안내에 반영되게. 못 받아도 있던 내용을 그대로 둔다 */
async function refreshAccount() {
    const mine = ++loadSeq;
    try {
        const value = await api.account();
        if (mine === loadSeq) account.value = value;
    } catch {
        /* 안내만 낡은 채로 남는다 */
    }
}

async function submit() {
    if (!canSubmit.value) return;
    emailError.value = '';
    passwordError.value = '';
    formError.value = '';

    const target = (view.value === 'sent' ? sentTo.value : newEmail.value).trim();
    if (view.value === 'form') {
        const problem = UtilValidation.email(target);
        if (problem) {
            emailError.value = UtilAuthMessages.emailProblem(t, problem);
            emailInput.value?.$el.focus();
            return;
        }
    }
    if (!password.value) {
        passwordError.value = t('password.currentRequired');
        passwordField.value?.focus();
        return;
    }

    submitting.value = true;
    try {
        await api.requestEmailChange({ newEmail: target, password: password.value });
        password.value = ''; // 성공하면 칸을 비운다 — 다시 보낼 때 새로 받는다
        sentTo.value = target;
        view.value = 'sent';
        cooldown.start(UtilAccount.DEFAULT_RESEND_SECONDS);
    } catch (e) {
        await onFailed(e);
    } finally {
        submitting.value = false;
    }
}

/** 입력 화면으로 돌아가 새 이메일 칸에 초점을 둔다(결과 화면에서 같은 주소가 막혔을 때·「다른 이메일로 다시 입력하기」) */
async function backToForm() {
    view.value = 'form';
    await nextTick();
    emailInput.value?.$el.focus();
}

async function retype() {
    void refreshAccount();
    await backToForm();
}

/** 서버 오류를 코드로 갈라 자리에 붙인다 — 문구는 서버가 준 것 그대로 */
async function onFailed(e: unknown) {
    if (!(e instanceof ApiError)) {
        formError.value = UtilAccountMessages.of(t, e);
        return;
    }
    switch (e.code) {
        case 'EMAIL_TAKEN':
            await backToForm();
            emailError.value = e.message;
            return;
        case 'VALIDATION':
            // 서버는 `fields.newEmail`·`fields.password` 를 한꺼번에 준다 — 둘 다 칸에 붙인다
            if (e.field('newEmail')) await backToForm();
            emailError.value = e.field('newEmail') ?? '';
            passwordError.value = e.field('password') ?? '';
            if (!emailError.value && !passwordError.value) formError.value = e.message;
            return;
        case 'CURRENT_PASSWORD_WRONG':
            passwordError.value = e.message;
            await nextTick();
            passwordField.value?.focus();
            return;
        case 'LOGIN_LOCKED':
            password.value = '';
            await nextTick(); // 위 비움을 지켜보는 쪽이 오류를 지운 뒤에 붙인다
            passwordError.value = e.message;
            lock.start(UtilAccount.waitSeconds(e.retryAfterSeconds));
            return;
        case 'RESEND_COOLDOWN':
            cooldown.start(UtilAccount.waitSeconds(e.retryAfterSeconds, UtilAccount.DEFAULT_RESEND_SECONDS));
            return;
        case 'EMAIL_CHANGE_NOT_ALLOWED':
            password.value = '';
            view.value = 'social';
            return;
        default:
            formError.value = e.message;
    }
}
</script>
