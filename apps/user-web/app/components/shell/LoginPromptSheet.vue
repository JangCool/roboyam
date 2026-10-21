<template>
    <!-- 비회원이 글쓰기·댓글을 시도했을 때 — 읽기는 그대로 되고 쓰기만 로그인이 필요하다 (design 01-shell 7) -->
    <AppSheet v-model:visible="open" :title="copy.title" :description="copy.description">
        <div class="mt-4 flex flex-col gap-2 px-5">
            <div class="flex gap-2">
                <Button type="button" :label="t('common.login')" class="flex-1" @click="goLogin" />
                <Button type="button" :label="t('common.signup')" severity="secondary" variant="outlined" class="flex-1" @click="goSignup" />
            </div>
            <Button type="button" :label="t('prompt.later')" variant="text" severity="secondary" @click="close" />
            <p class="text-center text-xs text-muted-color">{{ t('prompt.ageNotice') }}</p>
        </div>
    </AppSheet>
</template>

<script setup lang="ts">
import type { LoginPromptAction } from '~/composables/auth/useLoginPrompt';
import UtilRedirect from '~/utils/util-redirect';

const { t } = useI18n();
const { state, close } = useLoginPrompt();

const open = computed({
    get: () => state.value.open,
    set: (visible: boolean) => {
        if (!visible) close();
    },
});

/**
 * 종류별 제목·설명 — 문구는 `i18n/locales/{ko,en}/prompt.ts` 의 `prompt.{종류}Title`·`prompt.{종류}Description`(종류는 `LoginPromptAction`).
 * 한도 수치(7일·20통·5명)는 시안 문구 그대로다 — 비회원은 서버 설정값(내 쪽지 상태의 rules·quota)을 읽을 수 없다.
 * `view` 는 댓글 지연 노출(COM-003) — 비회원에게 아직 가려진 댓글 자리를 눌렀다. 시간 숫자는 알리지 않는다.
 */
const copy = computed(() => {
    const action: LoginPromptAction = state.value.action;
    return { title: t(`prompt.${action}Title`), description: t(`prompt.${action}Description`) };
});

async function goLogin() {
    const next = state.value.next;
    close();
    await navigateTo(UtilRedirect.toLogin(next));
}

async function goSignup() {
    close();
    await navigateTo('/signup');
}
</script>
