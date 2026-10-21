<template>
    <!-- 이메일 인증 전 안내 — 로그인과 읽기만 되고 글·댓글·추천·쪽지는 인증 뒤에 할 수 있다 -->
    <section
        v-if="user && !user.emailVerified"
        class="border-b border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100"
        :aria-label="t('login.verifyLabel')"
    >
        <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 text-sm">
            <p class="min-w-56 flex-1 leading-relaxed">
                <b>{{ t('login.verifyPending') }}</b>
                {{ t('login.verifyBenefit') }}
                <template v-if="user.email">{{ t('login.verifySentTo', { email: user.email }) }}</template>
            </p>
            <div class="flex shrink-0 items-center gap-3">
                <NuxtLink to="/verify-email" class="text-xs underline">{{ t('login.verifyGuide') }}</NuxtLink>
                <Button
                    size="small"
                    severity="warn"
                    type="button"
                    :label="coolingDown ? t('login.resendCooling', { time: UtilTime.mss(cooldownRemaining) }) : t('login.resendLabel')"
                    :loading="sending"
                    :disabled="coolingDown"
                    @click="onResend"
                />
            </div>
            <p v-if="notice" class="w-full text-xs" role="status" aria-live="polite">{{ notice }}</p>
        </div>
    </section>
</template>

<script setup lang="ts">
import UtilTime from '#layers/display-time/utils/util-time';
import type { ResendOutcome } from '~/composables/auth/useEmailResend';

/** 이메일 인증 안내 배너 — 재발송 버튼과 쿨다운(60초) 카운트다운. 인증을 마치면(로그인 상태가 새로 오면) 사라진다. */
const { t } = useI18n();
const { user } = useAuth();
const { sending, errorMessage, cooldownRemaining, coolingDown, resend } = useEmailResend();

/** 마지막 재발송 결과 — 글자가 아니라 결과를 기억해 두고 그릴 때 문구를 고른다(언어를 바꾸면 따라 바뀐다) */
const outcome = ref<ResendOutcome | null>(null);
const notice = computed(() => {
    switch (outcome.value) {
        case 'sent':
            return t('login.resendSent');
        case 'cooldown':
            return t('error.RESEND_COOLDOWN');
        case 'already':
            return t('login.resendAlready');
        case 'error':
            return errorMessage.value;
        default:
            return '';
    }
});

async function onResend() {
    outcome.value = await resend();
}
</script>
