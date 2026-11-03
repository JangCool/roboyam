<template>
    <!-- 탈퇴 진행 중 전역 안내 띠 — 헤더 바로 아래(정지 안내 `RestrictionBanner` 옆). 로그인 정보의 `withdrawalScheduled` 가 true 일 때만 보이고 탈퇴 화면으로 이어 준다.
         기능을 막는 것은 서버다(새 활동은 정지와 같은 길 `403 ACCOUNT_RESTRICTED` 로 거절하고 화면이 그 오류를 안내한다) -->
    <section
        v-if="withdrawalScheduled && !onWithdrawPage"
        class="border-b border-blue-200 bg-blue-50 text-blue-950 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-100"
        :aria-label="t('withdrawal.bannerLabel')"
        data-testid="withdrawal-banner"
    >
        <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2 text-sm">
            <p class="m-0 min-w-0 flex-1 leading-relaxed" data-testid="withdrawal-banner-text">
                <b>{{ t('withdrawal.bannerTitle') }}</b> {{ t('withdrawal.bannerText') }}
            </p>
            <NuxtLink :to="WITHDRAW_PATH" class="inline-flex min-h-11 shrink-0 items-center text-xs underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="withdrawal-banner-link">
                {{ t('withdrawal.bannerLink') }}
            </NuxtLink>
        </div>
    </section>
</template>

<script setup lang="ts">
/**
 * 탈퇴 진행 중 전역 안내 띠(member-withdrawal-contract 「API」 — `withdrawalScheduled`) — `layouts/default.vue` 의 헤더 아래에 한 번 놓는다.
 * 「탈퇴가 진행 중이에요 · 남은 일이 끝나면 자동으로 완료돼요 · 탈퇴 화면 보기/취소」. 탈퇴 화면에서는 같은 내용을 크게 보이므로 접는다.
 */
const WITHDRAW_PATH = '/settings/account/withdraw';

const { t } = useI18n();
const { withdrawalScheduled } = useAuth();
const route = useRoute();

const onWithdrawPage = computed(() => route.path === WITHDRAW_PATH);
</script>
