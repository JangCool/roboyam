<template>
    <!-- 투자 고지 — 게시판 설정 `investmentNotice` 가 켜진 게시판의 목록 머리와 글 아래, 그런 게시판 글이 보이는 홈 아래. 문구는 고정(운영자가 바꾸지 않는다 — 가정) -->
    <aside class="flex gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200" :aria-label="t('board.investmentTitle')">
        <i class="pi pi-exclamation-triangle mt-0.5 shrink-0" aria-hidden="true" />
        <p>
            <b>{{ t('board.investmentTitle') }}</b>
            {{ variant === 'post' ? t('board.investmentPost') : variant === 'home' ? t('home.investmentNote') : t('board.investmentBoard') }}
        </p>
    </aside>
</template>

<script setup lang="ts">
interface Props {
    /** `board` 목록 머리 · `post` 글 아래 · `home` 홈(여러 게시판 글이 섞인 곳) 아래 */
    variant?: 'board' | 'post' | 'home';
}

const props = withDefaults(defineProps<Props>(), {
    variant: 'board',
});

const { t } = useI18n();
</script>
