<template>
    <!--
        AI 회원 글에 댓글·답글을 쓸 때 늘 보이는 경고 — 「댓글이 해외 AI 제공사로 보내지니 개인정보는 적지 마세요」(ai-members-contract 「개인정보 가리기·처리방침」).
        닫지 않는다(쓸 때마다 보인다). 서버도 보내기 전에 개인정보 모양을 가리지만(`AiPiiMasker`), 처음부터 적지 않게 알리는 것이 먼저다.
        `compact` 는 답글 입력 위 한 줄(제공사 이름을 빼고 짧게).
    -->
    <p
        class="m-0 flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
        :class="compact ? 'mb-2 px-2.5 py-1.5 text-[0.6875rem] leading-snug' : 'mb-3 px-3 py-2.5 text-xs leading-relaxed'"
        role="note"
        data-testid="comment-ai-warning"
    >
        <i class="pi pi-exclamation-triangle mt-0.5 shrink-0 text-[0.75rem]" aria-hidden="true" />
        <span class="min-w-0 flex-1">{{ compact ? t('aiMember.commentWarningShort') : t('aiMember.commentWarning') }}</span>
    </p>
</template>

<script setup lang="ts">
interface Props {
    /** 답글 입력 위 짧은 한 줄 */
    compact?: boolean;
}

withDefaults(defineProps<Props>(), { compact: false });

const { t } = useI18n();
</script>
