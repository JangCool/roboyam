<template>
    <!--
        익명 게시판(`board.authorMode === 'ANONYMOUS'` — anonymous-board-contract) 안내 — 글쓰기 화면 위 · 새 댓글 입력 위.
        「글과 댓글은 익명으로 올라가요 · 닉네임·등급·아바타가 보이지 않아요 · 금칙어·제재 규칙은 같고 법적 요청이 있으면 작성자가 확인될 수 있어요」. 닫지 않는다.
        `compact` 는 댓글 입력 위(작은 글자·좁은 여백).
    -->
    <p
        class="m-0 flex items-start gap-2 rounded-lg bg-primary-50 text-primary-700 dark:bg-primary-950 dark:text-primary-300"
        :class="compact ? 'mb-3 px-3 py-2 text-xs leading-relaxed' : 'mb-4 px-3.5 py-3 text-sm leading-relaxed'"
        role="note"
        data-testid="anonymous-board-notice"
    >
        <AnonymousAuthorIcon class="mt-0.5" />
        <span class="min-w-0 flex-1">{{ t('anonymous.notice') }}</span>
    </p>
</template>

<script setup lang="ts">
interface Props {
    /** 댓글 입력 위 — 작은 글자 */
    compact?: boolean;
}

withDefaults(defineProps<Props>(), { compact: false });

const { t } = useI18n();
</script>
