<template>
    <!-- 링크를 확인하는 중 -->
    <StatusPanel v-if="view === 'verifying'" icon="pi pi-spin pi-spinner" :title="t('verify.verifyingTitle')" data-testid="verify-verifying">{{ t('common.wait') }}</StatusPanel>

    <!-- 인증 완료 -->
    <StatusPanel v-else-if="view === 'done'" icon="pi pi-check" tone="success" :title="t('verify.doneTitle')" data-testid="verify-done">
        {{ t('verify.doneText') }}
        <template #actions>
            <Button :label="user ? t('verify.start') : t('verify.loginToStart')" fluid size="large" @click="start" />
            <small class="text-xs text-muted-color">{{ t('verify.otherDevice') }}</small>
        </template>
    </StatusPanel>

    <!-- 이메일 변경 완료 — 메일의 변경 링크(`changed`)를 눌렀다. 새 이메일은 인증된 상태로 바뀌었다 -->
    <StatusPanel v-else-if="view === 'changed'" icon="pi pi-check" tone="success" :title="t('verify.changedTitle')" data-testid="verify-changed">
        {{ t('verify.changedText') }}
        <template #actions>
            <Button v-if="user" as="router-link" to="/settings/account" :label="t('account.backToAccount')" fluid size="large" />
            <Button v-else :label="t('verify.loginToStart')" fluid size="large" @click="navigateTo(UtilRedirect.toLogin('/settings/account'))" />
        </template>
    </StatusPanel>

    <!-- 이메일 변경 실패 — 그 사이 다른 회원이 이 이메일로 가입하거나 변경했다(변경 링크에서만) -->
    <StatusPanel v-else-if="view === 'taken'" icon="pi pi-exclamation-circle" tone="warn" :title="t('verify.takenTitle')" data-testid="verify-taken">
        {{ t('verify.takenText') }}
        <template #actions>
            <Button v-if="user" as="router-link" to="/settings/account/email" :label="t('verify.retryOtherEmail')" fluid size="large" />
            <Button v-else :label="t('verify.loginAndRetry')" fluid size="large" @click="navigateTo(UtilRedirect.toLogin('/settings/account/email'))" />
        </template>
    </StatusPanel>

    <!-- 이미 인증됨(주소로 직접 들어왔거나 다른 곳에서 인증을 마쳤다) -->
    <StatusPanel v-else-if="view === 'already'" icon="pi pi-check" tone="success" :title="t('verify.alreadyTitle')" data-testid="verify-already">
        {{ t('verify.alreadyText') }}
        <template #actions>
            <Button :label="t('verify.home')" fluid size="large" @click="navigateTo('/')" />
        </template>
    </StatusPanel>

    <!-- 링크 만료 / 사용할 수 없는 링크 -->
    <StatusPanel
        v-else-if="view === 'expired' || view === 'invalid'"
        icon="pi pi-exclamation-circle"
        tone="warn"
        :title="view === 'expired' ? t('verify.expiredTitle') : t('verify.invalidTitle')"
        :data-testid="`verify-${view}`"
    >
        {{ view === 'expired' ? t('verify.expiredText') : t('verify.invalidText') }}
        <template #actions>
            <Button v-if="user" :label="t('verify.getNew')" fluid size="large" :loading="sending" :disabled="coolingDown" @click="resendAndShow" />
            <Button v-else :label="t('verify.loginAndGetNew')" fluid size="large" @click="navigateTo(UtilRedirect.toLogin('/verify-email'))" />
            <p v-if="notice" class="text-xs" :class="outcomeIsError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="outcomeIsError ? 'alert' : 'status'">
                {{ notice }}
            </p>
            <small class="text-xs text-muted-color">{{ t('verify.alreadyHint') }}</small>
        </template>
    </StatusPanel>

    <!-- 링크 확인 중 서버 오류(네트워크 등) -->
    <StatusPanel v-else-if="view === 'error'" icon="pi pi-exclamation-triangle" tone="error" :title="t('verify.errorTitle')" data-testid="verify-error">
        {{ failure }}
        <template #actions>
            <Button :label="t('common.retry')" fluid size="large" @click="verify" />
        </template>
    </StatusPanel>

    <!-- 인증 메일 발송 안내 (가입 직후, 또는 배너의 "안내 보기") -->
    <StatusPanel v-else icon="pi pi-envelope" :title="t('verify.sentTitle')" data-testid="verify-pending">
        <template v-if="user?.email">
            <i18n-t keypath="verify.sentTo" scope="global" tag="span">
                <template #email>
                    <b class="break-all text-color">{{ user.email }}</b>
                </template>
            </i18n-t>
            <br />
        </template>
        {{ t('verify.validHours') }}
        <template #actions>
            <Button
                type="button"
                variant="outlined"
                severity="secondary"
                fluid
                size="large"
                :loading="sending"
                :disabled="coolingDown"
                :label="coolingDown ? t('login.resendCooling', { time: UtilTime.mss(cooldownRemaining) }) : t('login.resendLabel')"
                data-testid="verify-resend"
                @click="resendAndShow"
            />
            <p v-if="notice" class="text-xs" :class="outcomeIsError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="outcomeIsError ? 'alert' : 'status'">
                {{ notice }}
            </p>
            <small class="text-xs leading-relaxed text-muted-color">{{ t('verify.spamNote') }}</small>
            <Message severity="secondary" :closable="false" size="small" class="text-left">{{ t('verify.readOnlyNote') }}</Message>
            <NuxtLink v-if="user" to="/settings/account/email" class="text-sm text-primary hover:underline max-md:py-1.5" data-testid="verify-change-email">{{ t('verify.changeEmail') }}</NuxtLink>
            <NuxtLink to="/" class="text-sm text-primary hover:underline max-md:py-1.5">{{ t('verify.browseHome') }}</NuxtLink>
        </template>
    </StatusPanel>
</template>

<script setup lang="ts">
import type { ResendOutcome } from '~/composables/auth/useEmailResend';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilRedirect from '~/utils/util-redirect';
import UtilTime from '#layers/display-time/utils/util-time';

/**
 * 이메일 인증 `/verify-email` — 원본(community) `pages/verify-email.vue` 를 옮겼다.
 * 주소에 `?token=` 이 있으면 메일 링크를 확인하고(로그인 없이 된다 — `auth-unless-token`), 없으면 로그인한 사람에게 "인증 메일을 보냈어요" 안내와 재발송(60초 쿨다운)을 보여 준다.
 * 같은 주소가 **이메일 변경 링크**(`/settings/account/email`)도 받는다 — 응답의 `changed` 가 true 면 "이메일을 바꿨어요", 변경 링크에서만 오는 `409 EMAIL_TAKEN` 은 "이메일을 바꾸지 못했어요".
 *
 * - 재발송은 **실제** `POST /api/auth/email/resend`(`useEmailResend` — 안내 배너와 같은 쿨다운). 링크 확인도 실제 `POST /api/auth/email/verify`(로그인 없이).
 * - 인증이 끝나면 로그인 상태를 새로 받는다(같은 브라우저면 배너가 사라진다). 다른 탭에서 인증하고 돌아오면(`visibilitychange`) 다시 받는다.
 */
definePageMeta({ layout: 'auth', middleware: 'auth-unless-token' });
const { t } = useI18n();
useSeoMeta({ title: () => t('verify.seoTitle'), description: () => t('verify.seoDescription'), robots: 'noindex' });

type View = 'verifying' | 'done' | 'changed' | 'taken' | 'expired' | 'invalid' | 'error' | 'pending' | 'already';

const route = useRoute();
const api = useAuthApi();
const { user, refresh } = useAuth();
const { sending, errorMessage, cooldownRemaining, coolingDown, resend } = useEmailResend();

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''));
const failure = ref('');
/** 마지막 재발송 결과 — 글자가 아니라 결과를 기억하고 그릴 때 문구를 고른다(언어를 바꾸면 따라 바뀐다) */
const resendOutcome = ref<ResendOutcome | null>(null);
const outcome = ref<View | null>(null);

const view = computed<View>(() => {
    if (outcome.value) return outcome.value;
    if (token.value) return 'verifying';
    return user.value?.emailVerified ? 'already' : 'pending';
});

const outcomeIsError = computed(() => resendOutcome.value === 'error');
const notice = computed(() => {
    switch (resendOutcome.value) {
        case 'sent':
            return t('login.resendSent');
        case 'cooldown':
            return t('error.RESEND_COOLDOWN');
        case 'error':
            return errorMessage.value;
        default:
            return '';
    }
});

async function verify() {
    outcome.value = 'verifying';
    try {
        const result = await api.verifyEmail(token.value);
        try {
            await refresh(); // 같은 브라우저에 로그인돼 있으면 안내 배너가 사라지고(변경 링크면 로그인 이메일이 새 주소로 바뀐다)
        } catch {
            /* 인증은 끝났다 — 상태는 다음에 받는다 */
        }
        outcome.value = result.changed ? 'changed' : 'done';
    } catch (e) {
        if (e instanceof ApiError && e.code === 'TOKEN_EXPIRED') outcome.value = 'expired';
        else if (e instanceof ApiError && e.code === 'TOKEN_INVALID') outcome.value = 'invalid';
        else if (e instanceof ApiError && e.code === 'EMAIL_TAKEN') outcome.value = 'taken';
        else {
            failure.value = UtilAccountMessages.of(t, e);
            outcome.value = 'error';
        }
    }
}

async function resendAndShow() {
    let result: ResendOutcome;
    try {
        result = await resend();
    } catch (e) {
        // ApiError 가 아닌 실패(네트워크 등) — `useEmailResend` 가 던진다
        errorMessage.value = UtilAccountMessages.of(t, e);
        result = 'error';
    }
    if (result === 'already') {
        resendOutcome.value = null;
        outcome.value = 'already';
        return;
    }
    resendOutcome.value = result;
    if (result === 'sent' && token.value) {
        // 새 메일을 보냈다 — 링크 결과 화면에서 안내 화면으로 돌아가 쿨다운을 보여 준다
        await navigateTo('/verify-email', { replace: true });
        outcome.value = null;
    }
}

async function start() {
    await navigateTo(user.value ? '/onboarding' : UtilRedirect.toLogin('/onboarding'));
}

// 다른 탭·기기에서 인증을 마치고 이 탭으로 돌아오면 상태를 새로 받는다
function onVisible() {
    if (document.visibilityState === 'visible' && view.value === 'pending') void refresh().catch(() => undefined);
}

onMounted(() => {
    document.addEventListener('visibilitychange', onVisible);
    if (token.value) void verify();
});
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisible));
</script>
